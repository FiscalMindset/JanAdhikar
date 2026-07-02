package com.janadhikar.engine

import com.janadhikar.input.AppLanguage
import com.janadhikar.input.NormalizedQuery
import com.janadhikar.input.QueryNormalizer
import com.janadhikar.llm.Directive
import com.janadhikar.memory.model.RetrievalResult
import com.janadhikar.memory.model.VerifiedCitation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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

    private var captureJob: Job? = null
    private var pcmWindow = FloatArray(0)
    private var startedAt = 0L

    // ── Voice path ───────────────────────────────────────────────────────────

    fun startVoiceCapture() {
        if (_state.value !is IncidentState.Idle) return
        startedAt = clock()
        pcmWindow = FloatArray(0)
        _state.value = IncidentState.Active("", AgentPhase.LISTENING, 0L)

        captureJob = scope.launch {
            var samplesSinceDecode = 0
            try {
                audioSource.stream().collect { chunk ->
                    pcmWindow += chunk
                    samplesSinceDecode += chunk.size

                    if (elapsed() >= MAX_CAPTURE_MILLIS) {
                        stopAndResolve()
                        return@collect
                    }
                    if (samplesSinceDecode >= DECODE_EVERY_SAMPLES) {
                        samplesSinceDecode = 0
                        _state.value = IncidentState.Active(
                            transcript = currentTranscript(),
                            phase = AgentPhase.TRANSCRIBING,
                            elapsedMillis = elapsed(),
                        )
                        val text = transcriber.transcribe(pcmWindow)
                        // cancel() may have fired while whisper was decoding
                        if (_state.value is IncidentState.Active) {
                            _state.value = IncidentState.Active(text, AgentPhase.LISTENING, elapsed())
                        }
                    }
                }
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
        captureJob?.cancel()
        captureJob = null
        scope.launch {
            val finalTranscript = if (pcmWindow.isNotEmpty()) {
                _state.value = IncidentState.Active(active.transcript, AgentPhase.TRANSCRIBING, elapsed())
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
        scope.launch { resolve(rawText) }
    }

    // ── Shared resolution pipeline ───────────────────────────────────────────

    private suspend fun resolve(rawQuery: String) {
        val query = QueryNormalizer.normalize(rawQuery)
        if (query == null) {
            _state.value = IncidentState.NoStatute // nothing searchable → refuse (Rule 3)
            return
        }
        _state.value = IncidentState.Active(query.text, AgentPhase.SEARCHING, elapsed())

        when (val result = retrieve(query)) {
            is RetrievalResult.NoVerifiedStatute -> _state.value = IncidentState.NoStatute
            is RetrievalResult.Match -> {
                _state.value = IncidentState.Active(query.text, AgentPhase.TRANSLATING, elapsed())
                val language = preferredLanguage() ?: query.language
                val directive = translate(result.primary, language)
                _state.value = IncidentState.Shield(
                    directive = directive,
                    citation = result.primary,
                    related = result.related,
                    confidence = result.confidence,
                    redirectedFromSuperseded = result.redirectedFromSuperseded,
                )
            }
        }
    }

    /** Hard reset from any state. Discards the PCM window immediately (privacy). */
    fun cancel() {
        captureJob?.cancel()
        captureJob = null
        pcmWindow = FloatArray(0)
        _state.value = IncidentState.Idle
    }

    private fun currentTranscript(): String =
        (_state.value as? IncidentState.Active)?.transcript.orEmpty()

    private fun elapsed(): Long = clock() - startedAt

    companion object {
        /** Re-run whisper on the window every ~2 s of new audio. */
        private const val DECODE_EVERY_SAMPLES = 2 * 16_000

        /** Incident capture cap; past this we resolve with what we have. */
        const val MAX_CAPTURE_MILLIS = 60_000L
    }
}
