package com.janadhikar.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.janadhikar.input.AppLanguage
import com.janadhikar.memory.model.VerifiedCitation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File

/** What the Resolution screen renders as the directive, plus generation metadata. */
data class Directive(
    val text: String,
    val language: AppLanguage,
    /**
     * True when the LLM output was discarded (citation leak / empty / timeout)
     * and [text] is the verbatim statute text straight from the database.
     * The shield NEVER fails open — worst case, it shows the raw law.
     */
    val isVerbatimFallback: Boolean,
    /** Which engine produced [text] — an LLM id, or "verbatim (database)". */
    val modelId: String = VERBATIM_MODEL_ID,
    /** Wall-clock generation time in ms (0 for a verbatim passthrough). */
    val generationMillis: Long = 0,
    /** Approximate token count of [text] (~1 token per 4 chars). */
    val approxTokens: Int = text.length / 4,
) {
    companion object {
        const val VERBATIM_MODEL_ID = "verbatim (database)"
    }
}

/**
 * Gemma 3 (4-bit, LiteRT via MediaPipe GenAI) in its ONLY permitted role:
 * translating retrieved statute text into one plain directive.
 *
 * Pipeline per call: PromptContract (single injection point) → bounded
 * inference → OutputSanitizer → verbatim fallback if anything is off.
 */
class GemmaTranslator(
    context: Context,
    modelFile: File,
) : Closeable {

    private val llm: LlmInference = LlmInference.createFromOptions(
        context,
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            .setMaxTokens(MAX_TOKENS)
            .setMaxTopK(TOP_K)
            .build(),
    )

    suspend fun translate(citation: VerifiedCitation, output: AppLanguage): Directive =
        withContext(Dispatchers.Default) {
            val verbatim = VerbatimStatuteText.from(citation, output)
            val prompt = PromptContract.build(verbatim, output)

            val startedAt = System.currentTimeMillis()
            val raw = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                llm.generateResponse(prompt)
            }
            val elapsed = System.currentTimeMillis() - startedAt

            when (val verdict = raw?.let(OutputSanitizer::inspect)) {
                is OutputSanitizer.Verdict.Clean -> Directive(
                    text = verdict.text,
                    language = output,
                    isVerbatimFallback = false,
                    modelId = MODEL_ID,
                    generationMillis = elapsed,
                    approxTokens = verdict.text.length / 4,
                )
                // Leak, unusable output, or timeout → verbatim DB text. Always safe.
                else -> Directive(verbatim.value, output, isVerbatimFallback = true)
            }
        }

    override fun close() = llm.close()

    companion object {
        const val MODEL_ASSET = "models/gemma3-1b-it-int4.task"
        const val MODEL_ID = "Gemma 3 1B (4-bit, LiteRT)"

        /** 2–4 explanatory sentences; 256 tokens is comfortable headroom. */
        private const val MAX_TOKENS = 256
        private const val TOP_K = 40

        /** Past this we show verbatim text instead (explanation is longer now). */
        private const val INFERENCE_TIMEOUT_MS = 12_000L
    }
}
