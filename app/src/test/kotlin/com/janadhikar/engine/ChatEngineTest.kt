package com.janadhikar.engine

import app.cash.turbine.test
import com.google.common.truth.Truth.assertThat
import com.janadhikar.input.AppLanguage
import com.janadhikar.llm.Directive
import com.janadhikar.memory.model.RetrievalResult
import com.janadhikar.memory.model.VerifiedCitation
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Test

class ChatEngineTest {

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
        sourceDocument = "BNSS.pdf",
        sourceUrl = "https://indiacode.nic.in/x",
        compilationDate = "2026-05-01",
    )

    private val match = RetrievalResult.Match(citation, 0.9f, emptyList(), false)

    private fun engine(
        scope: kotlinx.coroutines.CoroutineScope,
        retrieval: RetrievalResult = match,
    ) = ChatEngine(
        scope = scope,
        audioSource = { MutableSharedFlow() },
        transcriber = { "" },
        retrieve = { retrieval },
        translate = { _, lang, _ -> Directive("It means you can ask why you are arrested.", lang, false) },
        clock = { 0L },
    )

    @Test
    fun `a question appends a turn that resolves to a grounded answer`() = runTest {
        val engine = engine(backgroundScope)
        engine.ask("why am I being arrested")
        runCurrent()
        val answer = engine.conversation.value.single().answer as Answer.Grounded
        assertThat(answer.streaming).isFalse()
        assertThat(answer.citations.first().sectionNumber).isEqualTo("47")
        assertThat(answer.explanation.text).contains("ask why")
    }

    @Test
    fun `refused retrieval yields a NoStatute answer, not a crash`() = runTest {
        val engine = engine(backgroundScope, retrieval = RetrievalResult.NoVerifiedStatute)
        engine.conversation.test {
            skipItems(1) // empty
            engine.ask("something with no legal match at all")
            skipItems(1) // Thinking
            assertThat(awaitItem()[0].answer).isEqualTo(Answer.NoStatute)
        }
    }

    @Test
    fun `follow-up appends below instead of replacing`() = runTest {
        val engine = engine(backgroundScope)
        engine.ask("first question about arrest")
        engine.ask("second question about arrest")
        // both turns present; the second appended, the first was not replaced
        assertThat(engine.conversation.value).hasSize(2)
        assertThat(engine.conversation.value[0].query).contains("first")
        assertThat(engine.conversation.value[1].query).contains("second")
    }

    @Test
    fun `too-short question is refused without searching`() = runTest {
        val engine = engine(backgroundScope)
        engine.conversation.test {
            skipItems(1)
            engine.ask("hm")
            assertThat(awaitItem()[0].answer).isEqualTo(Answer.NoStatute)
        }
    }

    @Test
    fun `hindi question is answered in accurate english`() = runTest {
        // The bundled law text is English and the small on-device model garbles
        // Hindi legal translation, so every generated explanation is produced in
        // English (accuracy over language) — even for a Hindi/Hinglish question.
        var used: AppLanguage? = null
        val engine = ChatEngine(
            scope = backgroundScope,
            audioSource = { MutableSharedFlow() },
            transcriber = { "" },
            retrieve = { match },
            translate = { _, lang, _ -> used = lang; Directive("explanation", lang, false) },
            clock = { 0L },
        )
        engine.ask("पुलिस गिरफ्तारी का कारण नहीं बता रही")
        runCurrent()
        assertThat(used).isEqualTo(AppLanguage.ENGLISH)
    }
}
