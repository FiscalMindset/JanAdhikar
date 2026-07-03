package com.janadhikar.engine

import com.google.common.truth.Truth.assertThat
import com.janadhikar.input.AppLanguage
import com.janadhikar.llm.Directive
import com.janadhikar.memory.model.VerifiedCitation
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ConversationStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private fun citation() = VerifiedCitation(
        1L, "Bharatiya Nyaya Sanhita, 2023", "भारतीय न्याय संहिता, 2023", "SECTION", "303",
        null, 113, "Theft.—Whoever…", "चोरी…", "a2023-45.pdf", "https://x/pdf", "2026-07-03",
    )

    @Test
    fun `saves and reloads completed turns`() {
        val file = tmp.newFile("conv.json")
        val turns = listOf(
            Turn(0, "what is theft", Answer.Grounded(
                Directive("Taking someone's property without permission.", AppLanguage.ENGLISH, false, "Gemma 3 1B", 4200),
                listOf(citation()), 0.9f, false, streaming = false,
            )),
            Turn(1, "gibberish query", Answer.NoStatute),
        )
        FileConversationStore(file).save(turns)

        // A FRESH store on the same file — simulates an app restart.
        val loaded = FileConversationStore(file).load()

        assertThat(loaded).hasSize(2)
        assertThat(loaded[0].query).isEqualTo("what is theft")
        val grounded = loaded[0].answer as Answer.Grounded
        assertThat(grounded.explanation.text).contains("property")
        assertThat(grounded.citations.first().sectionNumber).isEqualTo("303")
        assertThat(loaded[1].answer).isEqualTo(Answer.NoStatute)
    }

    @Test
    fun `does not persist a still-streaming or thinking turn`() {
        val file = tmp.newFile("conv2.json")
        val store = FileConversationStore(file)
        store.save(
            listOf(
                Turn(0, "pending", Answer.Thinking),
                Turn(1, "streaming", Answer.Grounded(
                    Directive("half", AppLanguage.ENGLISH, false), emptyList(), 0.9f, false, streaming = true,
                )),
            ),
        )
        assertThat(store.load()).isEmpty()
    }

    @Test
    fun `missing file loads empty`() {
        assertThat(FileConversationStore(java.io.File(tmp.root, "nope.json")).load()).isEmpty()
    }
}
