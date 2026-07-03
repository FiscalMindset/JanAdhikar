package com.janadhikar.llm

import com.janadhikar.input.AppLanguage
import com.janadhikar.memory.model.VerifiedCitation
import kotlinx.coroutines.withTimeoutOrNull
import java.io.Closeable
import java.io.File

/**
 * The Qwen 2.5 1.5B translator (llama.cpp). Same contract as [GemmaTranslator]
 * — it rewrites verbatim statute text into one plain directive and NOTHING else
 * — but a much stronger multilingual model, so Hindi answers are usable. Runs a
 * local GGUF fully offline. Non-streaming: [onDelta] fires once with the result.
 */
class LlamaTranslator private constructor(
    private val llama: LlamaBridge,
    val modelLabel: String,
) : Closeable {

    private val meaningLabel: String = "$modelLabel — plain meaning"

    suspend fun translate(citation: VerifiedCitation, output: AppLanguage): Directive =
        translate(citation, output, PromptContract.Style.NORMAL) {}

    suspend fun translate(
        citation: VerifiedCitation,
        output: AppLanguage,
        onDelta: (String) -> Unit,
    ): Directive = translate(citation, output, PromptContract.Style.NORMAL, onDelta)

    suspend fun translate(
        citation: VerifiedCitation,
        output: AppLanguage,
        style: PromptContract.Style,
        onDelta: (String) -> Unit,
    ): Directive {
        val verbatim = VerbatimStatuteText.from(citation, output)
        val prompt = PromptContract.buildChatML(verbatim, output, style)
        val startedAt = System.currentTimeMillis()
        val raw = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) { llama.generate(prompt, MAX_NEW_TOKENS) }
        val elapsed = System.currentTimeMillis() - startedAt

        return when (val verdict = raw?.let(OutputSanitizer::inspect)) {
            is OutputSanitizer.Verdict.Clean -> {
                onDelta(verdict.text)
                Directive(verdict.text, output, isVerbatimFallback = false, modelId = modelLabel,
                    generationMillis = elapsed, approxTokens = verdict.text.length / 4)
            }
            else -> Directive(verbatim.value, output, isVerbatimFallback = true)
        }
    }

    suspend fun synthesize(
        citations: List<VerifiedCitation>,
        output: AppLanguage,
        onDelta: (String) -> Unit,
    ): Directive? {
        if (citations.isEmpty()) return null
        val combined = VerbatimStatuteText.combined(citations, output)
        val prompt = PromptContract.buildSynthesisChatML(combined, output)
        val startedAt = System.currentTimeMillis()
        val raw = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) { llama.generate(prompt, 260) }
        val elapsed = System.currentTimeMillis() - startedAt
        return (raw?.let(OutputSanitizer::inspect) as? OutputSanitizer.Verdict.Clean)?.let {
            onDelta(it.text)
            Directive(it.text, output, isVerbatimFallback = false, modelId = modelLabel, generationMillis = elapsed)
        }
    }

    suspend fun define(phrase: String, output: AppLanguage, onDelta: (String) -> Unit): Directive {
        val lang = if (output == AppLanguage.HINDI) "आसान हिंदी" else "simple English"
        val clean = phrase.trim().take(120).replace("\"", "")
        // Free-text dictionary helper (NOT a grounded legal prompt) — built here,
        // deliberately outside PromptContract's grounded single-injection point.
        val prompt = "<|im_start|>system\nExplain what a word or phrase means in $lang, in one or two " +
            "short sentences a class-5 student understands. Give its everyday meaning; no extra facts." +
            "<|im_end|>\n<|im_start|>user\n\"$clean\"<|im_end|>\n<|im_start|>assistant\n"
        val raw = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) { llama.generate(prompt, 160) }
        val text = raw?.trim().orEmpty().ifBlank { "Sorry, I could not explain that word." }
        onDelta(text)
        return Directive(text, output, isVerbatimFallback = false, modelId = meaningLabel)
    }

    suspend fun warmUp() {
        withTimeoutOrNull(WARMUP_TIMEOUT_MS) {
            llama.generate("<|im_start|>user\nhi<|im_end|>\n<|im_start|>assistant\n", 4)
        }
    }

    override fun close() = llama.close()

    companion object {
        // Generous — a budget CPU needs a couple of minutes for a full rich
        // answer, and llama.cpp's native call can't be cancelled mid-generation
        // anyway; a short timeout only threw away a nearly-finished answer and
        // showed raw law instead. Better to wait and deliver the real answer.
        private const val INFERENCE_TIMEOUT_MS = 240_000L
        private const val WARMUP_TIMEOUT_MS = 40_000L
        private const val MAX_NEW_TOKENS = 300

        fun open(modelFile: File): LlamaTranslator? =
            LlamaBridge.open(modelFile)?.let { LlamaTranslator(it, LlamaBridge.modelIdForFile(modelFile.name)) }
    }
}
