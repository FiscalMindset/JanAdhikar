package com.janadhikar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.janadhikar.engine.Answer
import com.janadhikar.engine.EdgeStack
import com.janadhikar.engine.Turn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end, on real hardware, over the REAL artifacts: SentencePiece →
 * LiteRT MiniLM embedder → sqlite-vec KNN + FTS5 keyword → graph → strict
 * extraction → conversation answer. Drives the same ChatEngine the app builds.
 */
@RunWith(AndroidJUnit4::class)
class FullPipelineTest {

    @Test
    fun groundsOfArrestQuery_answersWithBnssSection47() = runBlocking {
        val answer = ask("do I have the right to know the grounds of my arrest")
        val grounded = answer as Answer.Grounded
        val primary = grounded.citations.first()
        assertThat(primary.statuteName).contains("Nagarik Suraksha Sanhita")
        assertThat(primary.sectionNumber).isEqualTo("47")
    }

    @Test
    fun constitutionQuery_answersWithArticle() = runBlocking {
        val grounded = ask("do I have a right to equality before the law") as Answer.Grounded
        val primary = grounded.citations.first()
        assertThat(primary.statuteName).contains("Constitution of India")
        assertThat(primary.unit).isEqualTo("ARTICLE")
        assertThat(primary.sectionNumber).isEqualTo("14")
    }

    @Test
    fun crisisQueries_answerWithRealOffenceSections_notRefusal() = runBlocking {
        for (query in listOf(
            "police killed my brother",
            "police slapped me without any reason",
            "my phone was stolen",
            "someone raped my friend",
        )) {
            val answer = ask(query)
            assertThat(answer).isInstanceOf(Answer.Grounded::class.java)
            val primary = (answer as Answer.Grounded).citations.first()
            assertThat(primary.statuteName).contains("Nyaya Sanhita")
            assertThat(primary.verbatimTextEn).isNotEmpty()
        }
    }

    @Test
    fun nonsenseQuery_refuses() = runBlocking {
        assertThat(ask("my pizza is cold and I want a refund")).isEqualTo(Answer.NoStatute)
    }

    @Test
    fun followUpAppendsToConversation() = runBlocking {
        stack.engine.clear()
        stack.engine.ask("what is the punishment for murder")
        awaitAnswered(0)
        stack.engine.ask("what are my fundamental rights")
        awaitAnswered(1)
        // Both turns are present — a follow-up appends, it does not replace.
        assertThat(stack.engine.conversation.value).hasSize(2)
    }

    private suspend fun ask(query: String): Answer {
        stack.engine.clear()
        stack.engine.ask(query)
        return awaitAnswered(0).answer
    }

    private suspend fun awaitAnswered(index: Int): Turn = withTimeout(90_000) {
        stack.engine.conversation.first {
            it.size > index && it[index].answer !is Answer.Thinking
        }[index]
    }

    companion object {
        private lateinit var stack: EdgeStack

        @JvmStatic
        @BeforeClass
        fun warmUp() = runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            stack = EdgeStack.create(context)
        }

        @JvmStatic
        @AfterClass
        fun tearDown() {
            stack.close()
        }
    }
}
