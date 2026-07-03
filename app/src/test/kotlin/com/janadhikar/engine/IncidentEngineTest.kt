package com.janadhikar.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.janadhikar.input.AppLanguage
import com.janadhikar.llm.Directive
import com.janadhikar.memory.model.RetrievalResult
import com.janadhikar.memory.model.VerifiedCitation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class IncidentEngineTest {

    private val citation = VerifiedCitation(
        chunkId = 1L,
        statuteName = "Bharatiya Nagarik Suraksha Sanhita, 2023",
        statuteNameHi = "भारतीय नागरिक सुरक्षा संहिता, 2023",
        unit = "SECTION",
        sectionNumber = "47",
        clause = null,
        pageNumber = 21,
        verbatimTextEn = "Grounds of arrest shall be communicated forthwith.",
        verbatimTextHi = "गिरफ्तारी के आधार तुरंत बताए जाएँगे।",
        sourceDocument = "BNSS_2023_Gazette.pdf",
        sourceUrl = "https://www.indiacode.nic.in/handle/123456789/21419",
        compilationDate = "2026-05-01",
    )

    private val match = RetrievalResult.Match(
        primary = citation,
        confidence = 0.91f,
        related = emptyList(),
        redirectedFromSuperseded = false,
    )

    private fun engine(
        scope: kotlinx.coroutines.CoroutineScope,
        audio: Flow<FloatArray> = MutableSharedFlow(),
        transcript: String = "police won't tell me why I'm being arrested",
        retrieval: RetrievalResult = match,
    ) = IncidentEngine(
        scope = scope,
        audioSource = { audio },
        transcriber = { transcript },
        retrieve = { retrieval },
        translate = { c, lang -> Directive("Demand the grounds of your arrest now.", lang, isVerbatimFallback = false) },
        clock = { 0L },
    )

    // ── Typed-text path (equal citizen) ──────────────────────────────────────

    @Test
    fun `typed query goes Idle → Searching → Translating → Shield`() = runTest {
        val engine = engine(backgroundScope)
        engine.state.test {
            assertThat(awaitItem()).isEqualTo(IncidentState.Idle)

            engine.submitTypedQuery("police won't tell me why I'm being arrested")

            val searching = awaitItem() as IncidentState.Active
            assertThat(searching.phase).isEqualTo(AgentPhase.SEARCHING)

            val translating = awaitItem() as IncidentState.Active
            assertThat(translating.phase).isEqualTo(AgentPhase.TRANSLATING)

            val shield = awaitItem() as IncidentState.Shield
            assertThat(shield.citation.sectionNumber).isEqualTo("47")
            assertThat(shield.directive.text).isEqualTo("Demand the grounds of your arrest now.")
        }
    }

    @Test
    fun `typed query active states are not voice sessions`() = runTest {
        // Regression: a typed query must never present as a mic session, or the
        // UI would start the FGS-microphone service and crash without
        // RECORD_AUDIO (Android 14+).
        val engine = engine(backgroundScope)
        engine.state.test {
            skipItems(1) // Idle
            engine.submitTypedQuery("police won't tell me why I'm being arrested")
            val searching = awaitItem() as IncidentState.Active
            assertThat(searching.isVoice).isFalse()
            val translating = awaitItem() as IncidentState.Active
            assertThat(translating.isVoice).isFalse()
            awaitItem() as IncidentState.Shield
        }
    }

    @Test
    fun `voice session active states are voice sessions`() = runTest {
        val engine = engine(backgroundScope)
        engine.state.test {
            skipItems(1) // Idle
            engine.startVoiceCapture()
            val listening = awaitItem() as IncidentState.Active
            assertThat(listening.isVoice).isTrue()
        }
    }

    @Test
    fun `refusal from retrieval resolves to NoStatute with no partial data`() = runTest {
        val engine = engine(backgroundScope, retrieval = RetrievalResult.NoVerifiedStatute)
        engine.state.test {
            skipItems(1) // Idle
            engine.submitTypedQuery("some question with no legal match")
            skipItems(1) // Searching
            assertThat(awaitItem()).isEqualTo(IncidentState.NoStatute)
        }
    }

    @Test
    fun `too-short query refuses instead of searching`() = runTest {
        val engine = engine(backgroundScope)
        engine.state.test {
            skipItems(1)
            engine.submitTypedQuery("hm")
            assertThat(awaitItem()).isEqualTo(IncidentState.NoStatute)
        }
    }

    @Test
    fun `hindi typed query resolves in hindi`() = runTest {
        var usedLanguage: AppLanguage? = null
        val engine = IncidentEngine(
            scope = backgroundScope,
            audioSource = { MutableSharedFlow() },
            transcriber = { "" },
            retrieve = { match },
            translate = { _, lang ->
                usedLanguage = lang
                Directive("अभी गिरफ्तारी का कारण पूछें।", lang, isVerbatimFallback = false)
            },
            clock = { 0L },
        )
        engine.state.test {
            skipItems(1)
            engine.submitTypedQuery("पुलिस गिरफ्तारी का कारण नहीं बता रही")
            skipItems(2) // Searching, Translating
            awaitItem() as IncidentState.Shield
            assertThat(usedLanguage).isEqualTo(AppLanguage.HINDI)
        }
    }

    // ── Voice path ───────────────────────────────────────────────────────────

    @Test
    fun `voice capture transitions to Active LISTENING and cancel returns to Idle`() = runTest {
        val engine = engine(backgroundScope)
        engine.state.test {
            assertThat(awaitItem()).isEqualTo(IncidentState.Idle)

            engine.startVoiceCapture()
            val active = awaitItem() as IncidentState.Active
            assertThat(active.phase).isEqualTo(AgentPhase.LISTENING)
            assertThat(active.transcript).isEmpty()

            engine.cancel()
            assertThat(awaitItem()).isEqualTo(IncidentState.Idle)
        }
    }

    @Test
    fun `stopAndResolve on voice runs final decode then resolves to Shield`() = runTest {
        val audio = MutableSharedFlow<FloatArray>()
        val engine = engine(backgroundScope, audio = audio)
        engine.state.test {
            skipItems(1) // Idle
            engine.startVoiceCapture()
            skipItems(1) // Active LISTENING
            runCurrent() // let the capture coroutine subscribe before emitting —
            //              a SharedFlow emission with zero subscribers is dropped
            audio.emit(FloatArray(1600)) // some audio arrived

            engine.stopAndResolve()

            // TRANSCRIBING (final decode) → SEARCHING → TRANSLATING → Shield
            val phases = mutableListOf<AgentPhase>()
            while (true) {
                when (val state = awaitItem()) {
                    is IncidentState.Active -> phases.add(state.phase)
                    is IncidentState.Shield -> {
                        assertThat(phases).containsAtLeast(
                            AgentPhase.TRANSCRIBING,
                            AgentPhase.SEARCHING,
                            AgentPhase.TRANSLATING,
                        ).inOrder()
                        return@test
                    }
                    else -> throw AssertionError("unexpected state $state")
                }
            }
        }
    }

    @Test
    fun `startVoiceCapture is a no-op outside Idle`() = runTest {
        val engine = engine(backgroundScope)
        engine.state.test {
            skipItems(1)
            engine.startVoiceCapture()
            skipItems(1) // Active
            engine.startVoiceCapture() // second trigger must not restart
            expectNoEvents()
        }
    }

    @Test
    fun `audio failure surfaces as Failure state, not a crash`() = runTest {
        val engine = IncidentEngine(
            scope = backgroundScope,
            audioSource = { throw IllegalStateException("AudioRecord failed to initialise") },
            transcriber = { "" },
            retrieve = { match },
            translate = { _, lang -> Directive("x", lang, false) },
            clock = { 0L },
        )
        engine.state.test {
            skipItems(1)
            engine.startVoiceCapture()
            skipItems(1) // Active LISTENING
            assertThat(awaitItem()).isInstanceOf(IncidentState.Failure::class.java)
        }
    }
}
