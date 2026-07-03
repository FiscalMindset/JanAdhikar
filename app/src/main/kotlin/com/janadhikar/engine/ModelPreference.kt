package com.janadhikar.engine

import android.content.Context

/**
 * Which answer model the user chose.
 *  - QWEN_SMALL (default): Qwen 2.5 0.5B — fast even on budget phones, small
 *    download (~350 MB), decent multilingual. Best first experience.
 *  - QWEN: Qwen 2.5 1.5B — best Hindi/quality, but slow on low-end CPUs.
 *  - GEMMA: Gemma 3 (MediaPipe) — hardware-accelerated; needs the .task file.
 * Changing it takes effect on the next app start (the model loads at launch).
 */
object ModelPreference {

    enum class Choice { QWEN_SMALL, QWEN, GEMMA }

    private const val PREFS = "janadhikar_prefs"
    private const val KEY = "answer_model"

    fun get(context: Context): Choice {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return runCatching { Choice.valueOf(v ?: "") }.getOrDefault(Choice.QWEN_SMALL)
    }

    fun set(context: Context, choice: Choice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, choice.name).apply()
    }

    fun isGemma(context: Context): Boolean = get(context) == Choice.GEMMA
}
