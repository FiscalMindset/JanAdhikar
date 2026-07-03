package com.janadhikar.llm

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import com.janadhikar.input.AppLanguage
import com.janadhikar.memory.model.VerifiedCitation
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val genMutex = Mutex()

    /** Which model actually loaded (1B or the bigger 4B), for the metadata. */
    val modelLabel: String = modelIdForFile(modelFile.name)
    private val meaningLabel: String = "$modelLabel — plain meaning"

    private val llm: LlmInference = LlmInference.createFromOptions(
        context,
        LlmInference.LlmInferenceOptions.builder()
            .setModelPath(modelFile.absolutePath)
            // TOTAL sequence budget (input prompt + output). The prompt embeds
            // the full verbatim statute text (often 400–500 tokens), so this
            // MUST be large — a small value made LiteRT abort (SIGABRT) because
            // the input alone overflowed the sequence.
            .setMaxTokens(MAX_SEQUENCE_TOKENS)
            .setMaxTopK(TOP_K)
            .build(),
    )

    suspend fun translate(citation: VerifiedCitation, output: AppLanguage): Directive =
        translate(citation, output, PromptContract.Style.NORMAL, onDelta = {})

    suspend fun translate(
        citation: VerifiedCitation,
        output: AppLanguage,
        onDelta: (String) -> Unit,
    ): Directive = translate(citation, output, PromptContract.Style.NORMAL, onDelta)

    /**
     * Streams the explanation token-by-token via [onDelta] (called on the main
     * flow of tokens as they arrive), then returns the final [Directive] with
     * the sanitized full text. If the model leaks a citation the streamed text
     * is discarded and the verbatim law is returned instead (always safe).
     */
    suspend fun translate(
        citation: VerifiedCitation,
        output: AppLanguage,
        style: PromptContract.Style,
        onDelta: (String) -> Unit,
    ): Directive {
        val verbatim = VerbatimStatuteText.from(citation, output)
        val prompt = PromptContract.build(verbatim, output, style)
        val startedAt = System.currentTimeMillis()

        val full = StringBuilder()
        // generateResponseAsync is single-flight per instance — serialize.
        val raw = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
            genMutex.withLock {
                suspendCancellableCoroutine<String> { cont ->
                    llm.generateResponseAsync(prompt) { partial, done ->
                        full.append(partial)
                        onDelta(partial)
                        if (done && cont.isActive) cont.resume(full.toString()) {}
                    }
                }
            }
        }
        val elapsed = System.currentTimeMillis() - startedAt

        return when (val verdict = raw?.let(OutputSanitizer::inspect)) {
            is OutputSanitizer.Verdict.Clean -> Directive(
                text = verdict.text,
                language = output,
                isVerbatimFallback = false,
                modelId = modelLabel,
                generationMillis = elapsed,
                approxTokens = verdict.text.length / 4,
            )
            // Leak, unusable output, or timeout → verbatim DB text. Always safe.
            else -> Directive(verbatim.value, output, isVerbatimFallback = true)
        }
    }

    /** Synthesise ONE answer from several provisions' real verbatim text (concepts). */
    suspend fun synthesize(
        citations: List<VerifiedCitation>,
        output: AppLanguage,
        onDelta: (String) -> Unit,
    ): Directive? {
        if (citations.isEmpty()) return null
        val combined = VerbatimStatuteText.combined(citations, output)
        val prompt = PromptContract.buildSynthesis(combined, output)
        val startedAt = System.currentTimeMillis()
        val full = StringBuilder()
        val raw = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
            genMutex.withLock {
                suspendCancellableCoroutine<String> { cont ->
                    llm.generateResponseAsync(prompt) { partial, done ->
                        full.append(partial); onDelta(partial)
                        if (done && cont.isActive) cont.resume(full.toString()) {}
                    }
                }
            }
        }
        val elapsed = System.currentTimeMillis() - startedAt
        return (raw?.let(OutputSanitizer::inspect) as? OutputSanitizer.Verdict.Clean)?.let {
            Directive(it.text, output, isVerbatimFallback = false, modelId = modelLabel, generationMillis = elapsed)
        }
    }

    /**
     * Plain-language meaning of a word/phrase the user selected (e.g. "criminal
     * force", "hefty"). This is a dictionary helper — a general definition, not
     * a statute — so it is clearly separate from grounded legal answers.
     */
    suspend fun define(phrase: String, output: AppLanguage, onDelta: (String) -> Unit): Directive {
        val lang = if (output == AppLanguage.HINDI) "Hindi" else "simple English"
        val clean = phrase.trim().take(120).replace("\"", "")
        val prompt = "<start_of_turn>user\nExplain what \"$clean\" means in $lang, in one or two " +
            "short sentences a class-5 student would understand. If it is a legal or official term, " +
            "give its everyday meaning. Do not add extra facts.<end_of_turn>\n<start_of_turn>model\n"
        val full = StringBuilder()
        val raw = withTimeoutOrNull(INFERENCE_TIMEOUT_MS) {
            genMutex.withLock {
                suspendCancellableCoroutine<String> { cont ->
                    llm.generateResponseAsync(prompt) { partial, done ->
                        full.append(partial)
                        onDelta(partial)
                        if (done && cont.isActive) cont.resume(full.toString()) {}
                    }
                }
            }
        }
        val text = raw?.trim().orEmpty().ifBlank { "Sorry, I could not explain that word." }
        return Directive(text, output, isVerbatimFallback = false, modelId = meaningLabel)
    }

    /**
     * Runs one tiny inference to build the LiteRT/XNNPACK weight cache up front,
     * off the background load path — so the user's FIRST real question does not
     * pay the cold-start cost (which is tens of seconds on a phone).
     */
    suspend fun warmUp() = withContext(Dispatchers.Default) {
        runCatching {
            withTimeoutOrNull(WARMUP_TIMEOUT_MS) {
                llm.generateResponse("<start_of_turn>user\nhi<end_of_turn>\n<start_of_turn>model\n")
            }
        }
        Unit
    }

    override fun close() = llm.close()

    companion object {
        const val MODEL_ASSET = "models/gemma3-1b-it-int4.task"
        const val MODEL_ASSET_4B = "models/gemma3-4b-it-int4.task"
        const val MODEL_ID = "Gemma 3 1B (4-bit, LiteRT)"

        /** Human label for whichever .task file loaded. */
        fun modelIdForFile(name: String): String = when {
            name.contains("4b", ignoreCase = true) -> "Gemma 3 4B (int4, LiteRT)"
            else -> "Gemma 3 1B (4-bit, LiteRT)"
        }

        /**
         * Total sequence length (prompt + generated). Must comfortably hold the
         * longest verbatim statute chunk plus the explanation. 1024 is safe for
         * Gemma 3 1B and our ~1800-char chunk cap.
         */
        private const val MAX_SEQUENCE_TOKENS = 2048
        private const val TOP_K = 40

        /**
         * Past this we give up and show verbatim text. Generous because the
         * answer STREAMS — the user watches tokens the whole time, so a long
         * budget is not a frozen wait, and it lets Gemma finish rather than
         * discarding a nearly-complete streamed answer.
         */
        private const val INFERENCE_TIMEOUT_MS = 45_000L

        /** Cold-start warm-up can be slow; bound it generously. */
        private const val WARMUP_TIMEOUT_MS = 40_000L
    }
}
