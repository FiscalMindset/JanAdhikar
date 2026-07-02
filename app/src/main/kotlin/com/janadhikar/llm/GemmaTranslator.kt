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

/** What the Resolution screen renders as the big yellow directive. */
data class Directive(
    val text: String,
    val language: AppLanguage,
    /**
     * True when the LLM output was discarded (citation leak / empty / timeout)
     * and [text] is the verbatim statute text straight from the database.
     * The shield NEVER fails open — worst case, it shows the raw law.
     */
    val isVerbatimFallback: Boolean,
)

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

            val raw = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
                llm.generateResponse(prompt)
            }

            when (val verdict = raw?.let(OutputSanitizer::inspect)) {
                is OutputSanitizer.Verdict.Clean ->
                    Directive(verdict.text, output, isVerbatimFallback = false)
                // Leak, unusable output, or timeout → verbatim DB text. Always safe.
                else -> Directive(verbatim.value, output, isVerbatimFallback = true)
            }
        }

    override fun close() = llm.close()

    companion object {
        const val MODEL_ASSET = "models/gemma3-1b-it-int4.task"

        /** Directive budget is ~25 words; 96 tokens is generous headroom. */
        private const val MAX_TOKENS = 96
        private const val TOP_K = 40

        /** Zero-latency promise: past this we show verbatim text instead. */
        private const val INFERENCE_TIMEOUT_MS = 4_000L
    }
}
