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

    /** Blocking; call from a background dispatcher. Thread-confined: one caller at a time. */
    fun transcribe(pcm16k: FloatArray, lang: Lang): String {
        check(ctxPtr != 0L) { "WhisperBridge used after close()" }
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
