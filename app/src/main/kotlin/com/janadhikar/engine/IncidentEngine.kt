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
 * The orchestration state machine:
 *
 *   Idle ──startVoiceCapture()──▶ Active(LISTENING ⇄ TRANSCRIBING)
 *   Idle ──submitTypedQuery()───▶ Active(SEARCHING)          (text skips STT)
 *   Active ──stopAndResolve()──▶ SEARCHING ─▶ TRANSLATING ─▶ Shield | NoStatute
 *   any ──cancel()─────────────▶ Idle
 *
 * All collaborators are seams (interfaces/lambdas): the machine is fully
 * unit-testable with fakes, and the production wiring lives in JanadhikarApp.
 */
class IncidentEngine(
    private val scope: CoroutineScope,
    private val audioSource: AudioSource,
    private val transcriber: Transcriber,
    private val retrieve: suspend (NormalizedQuery) -> RetrievalResult,
    private val translate: suspend (VerifiedCitation, AppLanguage) -> Directive,
    private val clock: () -> Long,
    /** User's output-language preference; null = follow the query's language. */
    private val preferredLanguage: () -> AppLanguage? = { null },
) {

    fun interface AudioSource {
        fun stream(): Flow<FloatArray>
    }

    fun interface Transcriber {
        /** Re-decodes the whole accumulated window (see WhisperBridge docs). */
        suspend fun transcribe(pcm16k: FloatArray): String
    }

    private val _state = MutableStateFlow<IncidentState>(IncidentState.Idle)
    val state: StateFlow<IncidentState> = _state.asStateFlow()

    /** Recent resolved shields, newest first — the Trigger screen's history. */
    private val _history = MutableStateFlow<List<IncidentState.Shield>>(emptyList())
    val history: StateFlow<List<IncidentState.Shield>> = _history.asStateFlow()

    private var captureJob: Job? = null
    private var pcmWindow = FloatArray(0)
    private var startedAt = 0L

    /** True while the current session is a microphone session (see Active.isVoice). */
    private var sessionIsVoice = false

    // ── Voice path ───────────────────────────────────────────────────────────

    fun startVoiceCapture() {
        if (_state.value !is IncidentState.Idle) return
        startedAt = clock()
        pcmWindow = FloatArray(0)
        sessionIsVoice = true
        _state.value = IncidentState.Active("", AgentPhase.LISTENING, 0L, isVoice = true)

        captureJob = scope.launch {
            try {
                // Accumulate audio only; transcribe ONCE on stop. Re-decoding the
                // whole growing window every couple of seconds was O(n²) and made
                // voice sluggish for no benefit — the transcript is only needed at
                // resolution time. The elapsed clock still updates the timer.
                audioSource.stream().collect { chunk ->
                    pcmWindow += chunk
                    if (elapsed() >= MAX_CAPTURE_MILLIS) {
                        stopAndResolve()
                        return@collect
                    }
                    _state.value = IncidentState.Active("", AgentPhase.LISTENING, elapsed(), isVoice = true)
                }
            } catch (e: CancellationException) {
                // Normal teardown (stopAndResolve/cancel). MUST be rethrown, not
                // reported: on the JVM CancellationException IS-A
                // IllegalStateException and would otherwise be caught below.
                throw e
            } catch (e: SecurityException) {
                _state.value = IncidentState.Failure("mic permission: ${e.message}")
            } catch (e: IllegalStateException) {
                _state.value = IncidentState.Failure("audio pipeline: ${e.message}")
            }
        }
    }

    /** User tapped stop (or the capture cap fired): final decode, then resolve. */
    fun stopAndResolve() {
        val active = _state.value as? IncidentState.Active ?: return
        val job = captureJob
        captureJob = null
        scope.launch {
            // Wait for the streaming decode to fully stop before the final one —
            // whisper_context is single-threaded (see EdgeStack whisperMutex).
            job?.cancelAndJoin()
            val finalTranscript = if (pcmWindow.isNotEmpty()) {
                _state.value = IncidentState.Active(active.transcript, AgentPhase.TRANSCRIBING, elapsed(), isVoice = true)
                transcriber.transcribe(pcmWindow)
            } else {
                active.transcript
            }
            pcmWindow = FloatArray(0)
            resolve(finalTranscript)
        }
    }

    // ── Text path — equal citizen, just skips STT ───────────────────────────

    fun submitTypedQuery(rawText: String) {
        if (_state.value !is IncidentState.Idle) return
        startedAt = clock()
        sessionIsVoice = false
        scope.launch { resolve(rawText) }
    }

    // ── Shared resolution pipeline ───────────────────────────────────────────

    private suspend fun resolve(rawQuery: String) {
        val query = QueryNormalizer.normalize(rawQuery)
        if (query == null) {
            _state.value = IncidentState.NoStatute // nothing searchable → refuse (Rule 3)
            return
        }
        _state.value = IncidentState.Active(query.text, AgentPhase.SEARCHING, elapsed(), isVoice = sessionIsVoice)

        try {
            when (val result = retrieve(query)) {
                is RetrievalResult.NoVerifiedStatute -> _state.value = IncidentState.NoStatute
                is RetrievalResult.Match -> {
                    _state.value = IncidentState.Active(query.text, AgentPhase.TRANSLATING, elapsed(), isVoice = sessionIsVoice)
                    val language = preferredLanguage() ?: query.language
                    val directive = translate(result.primary, language)
                    val shield = IncidentState.Shield(
                        directive = directive,
                        citation = result.primary,
                        related = result.related,
                        confidence = result.confidence,
                        redirectedFromSuperseded = result.redirectedFromSuperseded,
                    )
                    _history.value = (listOf(shield) + _history.value).distinctBy {
                        it.citation.chunkId
                    }.take(MAX_HISTORY)
                    _state.value = shield
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Retrieval/translation must never crash the app — fail to the
            // governed refusal instead.
            _state.value = IncidentState.NoStatute
        }
    }

    /** Re-open a past result from history without re-running retrieval. */
    fun showFromHistory(shield: IncidentState.Shield) {
        _state.value = shield
    }

    /** Hard reset from any state. Discards the PCM window immediately (privacy). */
    fun cancel() {
        captureJob?.cancel()
        captureJob = null
        pcmWindow = FloatArray(0)
        _state.value = IncidentState.Idle
    }

    private fun elapsed(): Long = clock() - startedAt

    companion object {
        /** Incident capture cap; past this we resolve with what we have. */
        const val MAX_CAPTURE_MILLIS = 60_000L

        private const val MAX_HISTORY = 20
    }
}
