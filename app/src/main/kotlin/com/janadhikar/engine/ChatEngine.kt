package com.janadhikar.engine

import com.janadhikar.input.AppLanguage
import com.janadhikar.input.NormalizedQuery
import com.janadhikar.input.QueryNormalizer
import com.janadhikar.llm.Directive
import com.janadhikar.memory.model.RetrievalResult
import com.janadhikar.memory.model.VerifiedCitation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The conversational engine: an offline legal assistant grounded in the
 * corpus. Questions accumulate in a scrolling [conversation]; each answer is
 * the LLM's plain-language explanation plus the verified citations (facts from
 * the database, never the LLM — the zero-hallucination rule still holds).
 *
 * Voice capture is a transient overlay ([capture]); when the user finishes
 * speaking, the transcript becomes a question like any typed one.
 *
 * Collaborators are seams so the engine is unit-testable without a device.
 */
class ChatEngine(
    private val scope: CoroutineScope,
    private val audioSource: AudioSource,
    private val transcriber: Transcriber,
    private val retrieve: suspend (NormalizedQuery) -> RetrievalResult,
    private val translate: suspend (VerifiedCitation, AppLanguage) -> Directive,
    private val clock: () -> Long,
    private val preferredLanguage: () -> AppLanguage? = { null },
) {

    fun interface AudioSource { fun stream(): Flow<FloatArray> }
    fun interface Transcriber { suspend fun transcribe(pcm16k: FloatArray): String }

    private val _conversation = MutableStateFlow<List<Turn>>(emptyList())
    val conversation: StateFlow<List<Turn>> = _conversation.asStateFlow()

    private val _capture = MutableStateFlow<CaptureState>(CaptureState.Idle)
    val capture: StateFlow<CaptureState> = _capture.asStateFlow()

    private var nextId = 0L
    private var captureJob: Job? = null
    private var pcmWindow = FloatArray(0)
    private var startedAt = 0L

    /** True while a mic session is live (drives the foreground service). */
    val isRecording: Boolean get() = _capture.value is CaptureState.Recording

    // ── Ask a question (typed, or a finished transcript) ─────────────────────

    fun ask(rawQuery: String) {
        val query = QueryNormalizer.normalize(rawQuery)
        val id = nextId++
        if (query == null) {
            // Nothing searchable → a refusal turn using the raw text as the label.
            appendTurn(Turn(id, rawQuery.trim(), Answer.NoStatute))
            return
        }
        appendTurn(Turn(id, query.text, Answer.Thinking))
        scope.launch {
            val answer = try {
                when (val result = retrieve(query)) {
                    is RetrievalResult.NoVerifiedStatute -> Answer.NoStatute
                    is RetrievalResult.Match -> {
                        val language = preferredLanguage() ?: query.language
                        val explanation = translate(result.primary, language)
                        Answer.Grounded(
                            explanation = explanation,
                            citations = listOf(result.primary) + result.related,
                            confidence = result.confidence,
                            redirectedFromSuperseded = result.redirectedFromSuperseded,
                        )
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Answer.NoStatute // never crash a turn — refuse instead
            }
            updateTurn(id) { it.copy(answer = answer) }
        }
    }

    fun clear() {
        _conversation.value = emptyList()
    }

    // ── Voice capture overlay ────────────────────────────────────────────────

    fun startVoice() {
        if (_capture.value is CaptureState.Recording) return
        startedAt = clock()
        pcmWindow = FloatArray(0)
        _capture.value = CaptureState.Recording("", 0L)
        captureJob = scope.launch {
            var lastDecode = 0
            var live = ""
            try {
                audioSource.stream().collect { chunk ->
                    pcmWindow += chunk
                    if (elapsed() >= MAX_CAPTURE_MILLIS) { stopVoice(); return@collect }
                    if (pcmWindow.size - lastDecode >= LIVE_DECODE_SAMPLES) {
                        lastDecode = pcmWindow.size
                        live = transcriber.transcribe(pcmWindow.copyOf()).ifBlank { live }
                    }
                    if (_capture.value is CaptureState.Recording) {
                        _capture.value = CaptureState.Recording(live, elapsed())
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: SecurityException) {
                _capture.value = CaptureState.Idle
            } catch (e: IllegalStateException) {
                _capture.value = CaptureState.Idle
            }
        }
    }

    /** User finished speaking: final decode, then ask the transcript. */
    fun stopVoice() {
        if (_capture.value !is CaptureState.Recording) return
        val job = captureJob
        captureJob = null
        scope.launch {
            job?.cancelAndJoin()
            val transcript = if (pcmWindow.isNotEmpty()) transcriber.transcribe(pcmWindow) else ""
            pcmWindow = FloatArray(0)
            _capture.value = CaptureState.Idle
            if (transcript.isNotBlank()) ask(transcript)
        }
    }

    fun cancelVoice() {
        captureJob?.cancel()
        captureJob = null
        pcmWindow = FloatArray(0)
        _capture.value = CaptureState.Idle
    }

    // ── internals ────────────────────────────────────────────────────────────

    private fun appendTurn(turn: Turn) {
        _conversation.value = _conversation.value + turn
    }

    private fun updateTurn(id: Long, transform: (Turn) -> Turn) {
        _conversation.value = _conversation.value.map { if (it.id == id) transform(it) else it }
    }

    private fun elapsed(): Long = clock() - startedAt

    companion object {
        const val MAX_CAPTURE_MILLIS = 60_000L
        private const val LIVE_DECODE_SAMPLES = (2.5 * 16_000).toInt()
    }
}
