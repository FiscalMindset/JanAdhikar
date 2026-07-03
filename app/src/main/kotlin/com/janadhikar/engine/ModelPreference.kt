package com.janadhikar.engine

import android.content.Context

/**
 * Which answer model the user chose. Default is Qwen (ungated, auto-downloads,
 * best multilingual). Gemma is an option for those who pushed its gated .task
 * or want MediaPipe's hardware acceleration (faster on low-end CPUs).
 * Changing it takes effect on the next app start (the model loads at launch).
 */
object ModelPreference {

    enum class Choice { QWEN, GEMMA }

    private const val PREFS = "janadhikar_prefs"
    private const val KEY = "answer_model"

    fun get(context: Context): Choice {
        val v = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)
        return if (v == Choice.GEMMA.name) Choice.GEMMA else Choice.QWEN
    }

    fun set(context: Context, choice: Choice) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, choice.name).apply()
    }

    fun isGemma(context: Context): Boolean = get(context) == Choice.GEMMA
}
