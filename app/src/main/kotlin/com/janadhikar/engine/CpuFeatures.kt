package com.janadhikar.engine

import java.io.File

/**
 * Detects CPU features that the native (ggml) libraries were compiled to REQUIRE.
 * The native libs are built with ARMv8.2 dot-product kernels for speed; running
 * those instructions on a CPU that lacks them SIGILLs. So the app checks here
 * BEFORE loading llama.cpp/whisper and, on a CPU without dot-product, falls back
 * to the (separately built, MediaPipe) Gemma path instead of crashing.
 */
object CpuFeatures {

    val hasDotProd: Boolean by lazy { detect() }

    private fun detect(): Boolean = runCatching {
        val text = File("/proc/cpuinfo").readText().lowercase()
        // 'asimddp' is the /proc/cpuinfo flag for ARMv8.2 dot-product (SDOT/UDOT).
        "asimddp" in text
    }.getOrDefault(false)
}
