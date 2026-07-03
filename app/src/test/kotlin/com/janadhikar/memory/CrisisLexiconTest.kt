package com.janadhikar.memory

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CrisisLexiconTest {

    @Test
    fun `killed maps to murder`() {
        assertThat(CrisisLexicon.toFtsQuery("someone killed my brother")).contains("murder")
    }

    @Test
    fun `slapped and beat map to assault`() {
        assertThat(CrisisLexicon.toFtsQuery("police slapped me")).contains("assault")
        assertThat(CrisisLexicon.toFtsQuery("they beat me up")).contains("assault")
    }

    @Test
    fun `stolen maps to theft, robbed to robbery`() {
        assertThat(CrisisLexicon.toFtsQuery("my phone was stolen")).contains("theft")
        assertThat(CrisisLexicon.toFtsQuery("he robbed me")).contains("robbery")
    }

    @Test
    fun `threatened maps to criminal intimidation`() {
        assertThat(CrisisLexicon.toFtsQuery("he threatened to kill me"))
            .contains("criminal intimidation")
    }

    @Test
    fun `hindi crisis words are covered`() {
        assertThat(CrisisLexicon.toFtsQuery("पुलिस ने मुझे मारा")).isNotEmpty()
        assertThat(CrisisLexicon.toFtsQuery("मेरा फोन चोरी हो गया")).contains("theft")
    }

    @Test
    fun `query with no crisis word yields empty (vector-only path)`() {
        assertThat(CrisisLexicon.toFtsQuery("what are my fundamental rights")).isEmpty()
    }

    @Test
    fun `produced query is a valid FTS5 OR expression`() {
        val q = CrisisLexicon.toFtsQuery("he killed and robbed me")
        // fragments are parenthesised and OR-joined
        assertThat(q).contains(" OR ")
        assertThat(q).startsWith("(")
    }
}
