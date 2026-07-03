package com.janadhikar.stt

import java.io.Closeable
import java.io.File

/**
 * Kotlin face of whisper.cpp. Owns the native context lifecycle; no ggml
 * pointer escapes this class (CONTRIBUTING.md JNI rule).
 *
 * Streaming model: the caller accumulates 16 kHz mono float PCM and calls
 * [transcribe] on the growing window (~ every 2 s). whisper re-decodes the
 * window; the caller replaces its transcript wholesale. Simple, robust, and
 * good enough for < 60 s incident clips.
 */
class WhisperBridge private constructor(private var ctxPtr: Long) : Closeable {

    enum class Lang(val whisperCode: String) {
        AUTO("auto"),
        ENGLISH("en"),
        HINDI("hi"),
    }

    /**
     * Blocking; call from a background dispatcher. NOT thread-safe — the caller
     * must serialize calls (whisper_context has no internal locking; concurrent
     * decode segfaults). Buffers shorter than [MIN_SAMPLES] are ignored: feeding
     * whisper a near-empty window can crash inside ggml.
     */
    fun transcribe(pcm16k: FloatArray, lang: Lang): String {
        check(ctxPtr != 0L) { "WhisperBridge used after close()" }
        if (pcm16k.size < MIN_SAMPLES) return ""
        return nativeTranscribe(ctxPtr, pcm16k, lang.whisperCode).trim()
    }

    override fun close() {
        if (ctxPtr != 0L) {
            nativeRelease(ctxPtr)
            ctxPtr = 0L
        }
    }

    private external fun nativeRelease(ctxPtr: Long)

    private external fun nativeTranscribe(ctxPtr: Long, pcm: FloatArray, langCode: String): String

    companion object {
        const val SAMPLE_RATE_HZ = 16_000

        /** ~0.5 s. whisper needs a non-trivial window; below this it can segfault. */
        private const val MIN_SAMPLES = SAMPLE_RATE_HZ / 2

        init {
            System.loadLibrary("janadhikar_whisper")
        }

        @JvmStatic
        private external fun nativeInit(modelPath: String): Long

        /** [modelFile]: ggml multilingual weights (EN+HI) provisioned from assets. */
        fun open(modelFile: File): WhisperBridge {
            val ptr = nativeInit(modelFile.absolutePath)
            check(ptr != 0L) { "Failed to initialise whisper context from $modelFile" }
            return WhisperBridge(ptr)
        }
    }
}
