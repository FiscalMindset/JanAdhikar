package com.janadhikar

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import com.janadhikar.engine.EdgeStack
import com.janadhikar.engine.IncidentState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.AfterClass
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * End-to-end, on real hardware, over the REAL artifacts bundled in the APK:
 * SentencePiece tokenizer → LiteRT MiniLM embedder → sqlite-vec KNN → graph →
 * strict metadata extraction → engine Shield state.
 *
 * This is the definitive "the offline engine actually works" proof — it drives
 * the same EdgeStack the app builds at launch, with no UI in the loop.
 */
@RunWith(AndroidJUnit4::class)
class FullPipelineTest {

    @Test
    fun groundsOfArrestQuery_resolvesToExactlyBnssSection47() = runBlocking {
        // A direct-phrasing query the offline eval confirms as a Precision@1
        // hit → the pipeline must land exactly on BNSS §47, "Person arrested
        // to be informed of grounds of arrest and of right to bail".
        val shield = submitAndAwaitResolution("do I have the right to know the grounds of my arrest")
        assertThat(shield).isInstanceOf(IncidentState.Shield::class.java)
        val citation = (shield as IncidentState.Shield).citation
        assertThat(citation.statuteName).contains("Nagarik Suraksha Sanhita")
        assertThat(citation.sectionNumber).isEqualTo("47")
        assertThat(citation.pageNumber).isGreaterThan(0)
        assertThat(citation.verbatimTextEn).ignoringCase().contains("grounds")
    }

    @Test
    fun colloquialArrestQuery_resolvesToAVerifiedBnssArrestProvision() = runBlocking {
        // Colloquial phrasing. Precision@1 over the corpus is 7/14 (see
        // eval_queries.py), so we do NOT pin the exact section — we assert the
        // guarantee that actually matters: the directive is backed by a REAL,
        // verified BNSS arrest-chapter provision, never a hallucination.
        val shield = submitAndAwaitResolution("police won't tell me why I am being arrested")
        assertThat(shield).isInstanceOf(IncidentState.Shield::class.java)
        val match = shield as IncidentState.Shield
        assertThat(match.citation.statuteName).contains("Nagarik Suraksha Sanhita")
        assertThat(match.confidence).isGreaterThan(0.34f) // governed threshold
        // The grounds-of-arrest provisions the eval deems correct for this
        // query (§47 or §35) now reach the user as primary or related, thanks
        // to surfacing high-confidence vector neighbours — not just the #1 hit.
        val allSections = (listOf(match.citation) + match.related).map { it.sectionNumber }
        assertThat(allSections).containsAnyOf("47", "35")
    }

    @Test
    fun hindiMurderQuery_resolvesToVerifiedBnsCitation() = runBlocking {
        val shield = submitAndAwaitResolution("हत्या की सजा क्या है")
        val citation = (shield as IncidentState.Shield).citation
        assertThat(citation.statuteName).contains("Nyaya Sanhita")
        assertThat(citation.sectionNumber).isEqualTo("103") // Punishment for murder
    }

    @Test
    fun nonsenseQuery_resolvesToGovernedRefusal() = runBlocking {
        val state = submitAndAwaitResolution("my pizza is cold and I want a refund")
        assertThat(state).isEqualTo(IncidentState.NoStatute)
    }

    private suspend fun submitAndAwaitResolution(query: String): IncidentState {
        stack.engine.cancel()
        stack.engine.submitTypedQuery(query)
        return withTimeout(20_000) {
            stack.engine.state.first { it is IncidentState.Shield || it is IncidentState.NoStatute }
        }
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
