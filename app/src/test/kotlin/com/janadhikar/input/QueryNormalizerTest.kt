package com.janadhikar.input

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class QueryNormalizerTest {

    @Test
    fun `collapses whitespace and trims`() {
        val query = QueryNormalizer.normalize("  police   took my\n\nphone  ")
        assertThat(query?.text).isEqualTo("police took my phone")
    }

    @Test
    fun `strips whisper filler tokens`() {
        val query = QueryNormalizer.normalize("um police uh took my phone yaar")
        assertThat(query?.text).isEqualTo("police took my phone")
    }

    @Test
    fun `too-short input returns null so the engine can refuse`() {
        assertThat(QueryNormalizer.normalize("hm")).isNull()
        assertThat(QueryNormalizer.normalize("   ")).isNull()
        assertThat(QueryNormalizer.normalize("um uh hmm")).isNull() // all filler
    }

    @Test
    fun `devanagari query detects as hindi`() {
        val query = QueryNormalizer.normalize("पुलिस ने मेरा फोन ले लिया")
        assertThat(query?.language).isEqualTo(AppLanguage.HINDI)
    }

    @Test
    fun `english query detects as english`() {
        val query = QueryNormalizer.normalize("police took my phone without a warrant")
        assertThat(query?.language).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun `hinglish in latin script resolves to english output space`() {
        val query = QueryNormalizer.normalize("police ne mera phone le liya bina warrant ke")
        assertThat(query?.language).isEqualTo(AppLanguage.ENGLISH)
    }

    @Test
    fun `mixed script leans hindi when devanagari dominates`() {
        val query = QueryNormalizer.normalize("पुलिस ने मेरा phone ज़ब्त कर लिया है")
        assertThat(query?.language).isEqualTo(AppLanguage.HINDI)
    }
}
