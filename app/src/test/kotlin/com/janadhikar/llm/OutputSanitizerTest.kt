package com.janadhikar.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OutputSanitizerTest {

    @Test
    fun `plain directive passes clean`() {
        val verdict = OutputSanitizer.inspect(
            "Ask the officer to tell you the exact reason for your arrest right now.",
        )
        assertThat(verdict).isInstanceOf(OutputSanitizer.Verdict.Clean::class.java)
    }

    @Test
    fun `hindi directive passes clean`() {
        val verdict = OutputSanitizer.inspect("अधिकारी से अभी अपनी गिरफ्तारी का कारण पूछें।")
        assertThat(verdict).isInstanceOf(OutputSanitizer.Verdict.Clean::class.java)
    }

    @Test
    fun `gemma end-of-turn marker is stripped before inspection`() {
        val verdict = OutputSanitizer.inspect("Demand the grounds of arrest.<end_of_turn>")
        assertThat((verdict as OutputSanitizer.Verdict.Clean).text)
            .isEqualTo("Demand the grounds of arrest.")
    }

    // Rich legal answers now WELCOME citations, articles, and landmark cases —
    // that is the whole point of a helpful explanation. They pass clean (the
    // exact verified law is still shown separately in the source card).
    @Test
    fun `article and section references are allowed in rich answers`() {
        assertClean("Article 21 protects life and personal liberty; see also Article 21A.")
        assertClean("Under Section 47 the police must tell you the grounds of arrest.")
        assertClean("अनुच्छेद 21 जीवन और स्वतंत्रता की रक्षा करता है।")
        assertClean("The landmark Maneka Gandhi case expanded its scope.")
    }

    @Test
    fun `empty or whitespace output is unusable`() {
        assertThat(OutputSanitizer.inspect("")).isEqualTo(OutputSanitizer.Verdict.Unusable)
        assertThat(OutputSanitizer.inspect("   \n")).isEqualTo(OutputSanitizer.Verdict.Unusable)
    }

    private fun assertClean(output: String) {
        assertThat(OutputSanitizer.inspect(output)).isInstanceOf(OutputSanitizer.Verdict.Clean::class.java)
    }
}
