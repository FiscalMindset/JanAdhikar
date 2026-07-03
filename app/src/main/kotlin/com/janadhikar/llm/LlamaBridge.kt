package com.janadhikar.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * Thin JNI wrapper over llama.cpp, running a local GGUF chat model (Qwen 2.5
 * 1.5B) fully offline. Non-streaming: [generate] returns the whole completion.
 * The native context is not thread-safe, so calls are serialized.
 */
class LlamaBridge private constructor(private var handle: Long) : Closeable {

    private val mutex = Mutex()

    suspend fun generate(prompt: String, maxTokens: Int): String = withContext(Dispatchers.Default) {
        mutex.withLock {
            if (handle == 0L) "" else nativeGenerate(handle, prompt, maxTokens)
        }
    }

    override fun close() {
        if (handle != 0L) { nativeFree(handle); handle = 0L }
    }

    private external fun nativeGenerate(handle: Long, prompt: String, maxTokens: Int): String
    private external fun nativeFree(handle: Long)
    private external fun nativeLoad(modelPath: String, nCtx: Int, nThreads: Int): Long

    companion object {
        const val MODEL_ASSET = "models/qwen2.5-1.5b-instruct-q4_k_m.gguf"
        const val MODEL_ID = "Qwen 2.5 1.5B (Q4, llama.cpp)"
        private const val N_CTX = 2048

        init { System.loadLibrary("janadhikar_llama") }

        /** Loads the model; returns null if it fails (caller falls back). */
        fun open(modelFile: File): LlamaBridge? {
            val threads = Runtime.getRuntime().availableProcessors().coerceIn(2, 6)
            val stub = LlamaBridge(0L)
            val h = stub.nativeLoad(modelFile.absolutePath, N_CTX, threads)
            return if (h != 0L) LlamaBridge(h) else null
        }
    }
}
