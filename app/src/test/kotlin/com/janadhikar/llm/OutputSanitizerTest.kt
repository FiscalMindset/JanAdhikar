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

    // ── Leaks: the model reciting citations from parametric memory (Rule 1) ──

    @Test
    fun `leaked section number is caught`() {
        assertLeak("Under Section 47 you can demand the grounds of arrest.")
        assertLeak("As per sec. 50 of the code, ask for the grounds.")
    }

    @Test
    fun `leaked hindi dhara is caught`() {
        assertLeak("धारा 47 के अनुसार आप गिरफ्तारी का कारण पूछ सकते हैं।")
        assertLeak("धारा ४७ के तहत कारण पूछें।") // Devanagari numerals too
    }

    @Test
    fun `leaked article is caught`() {
        assertLeak("Article 22 protects you here.")
        assertLeak("अनुच्छेद 21 आपके साथ है।")
    }

    @Test
    fun `leaked page number is caught`() {
        assertLeak("See page 21 for details.")
        assertLeak("पृष्ठ २१ देखें।")
    }

    @Test
    fun `leaked act-with-year is caught`() {
        assertLeak("The Police Act, 1861 forbids this.")
        assertLeak("भारतीय न्याय संहिता, 2023 इसके विरुद्ध है।")
    }

    @Test
    fun `leaked bare-act acronym is caught`() {
        assertLeak("This is covered by the BNSS.")
        assertLeak("The IPC forbids this behaviour.")
    }

    @Test
    fun `leaked clause and chapter references are caught`() {
        assertLeak("Clause (1) applies to you.")
        assertLeak("Chapter V explains custody rules.")
    }

    @Test
    fun `empty or whitespace output is unusable`() {
        assertThat(OutputSanitizer.inspect("")).isEqualTo(OutputSanitizer.Verdict.Unusable)
        assertThat(OutputSanitizer.inspect("   \n")).isEqualTo(OutputSanitizer.Verdict.Unusable)
    }

    private fun assertLeak(output: String) {
        assertThat(OutputSanitizer.inspect(output))
            .isInstanceOf(OutputSanitizer.Verdict.CitationLeak::class.java)
    }
}
