package com.janadhikar.llm

/**
 * Last line of defence for Rule 1: even a correctly-prompted model can leak a
 * memorised citation. Any citation-shaped pattern in LLM output means the
 * output is DISCARDED and the UI falls back to the verbatim statute text —
 * which is always safe, because it came from the database.
 *
 * Deliberately over-broad: a false positive costs us a nicer sentence; a false
 * negative costs a citizen a fabricated law.
 */
object OutputSanitizer {

    private val CITATION_PATTERNS = listOf(
        Regex("""\bsec(?:tion)?s?\.?\s*\d""", RegexOption.IGNORE_CASE), // Section 47 / Sec. 47
        Regex("""धारा\s*[\d०-९]"""), //                                    धारा ४७
        Regex("""अनुच्छेद\s*[\d०-९]"""), //                                 अनुच्छेद 21
        Regex("""\barticle\s*\d""", RegexOption.IGNORE_CASE), //          Article 21
        Regex("""\bpage\s*(?:no\.?\s*)?\d""", RegexOption.IGNORE_CASE), // page 21
        Regex("""पृष्ठ\s*[\d०-९]"""),
        Regex("""\bact,?\s*(?:of\s*)?(?:18|19|20)\d{2}\b""", RegexOption.IGNORE_CASE), // Act, 2023
        Regex("""अधिनियम,?\s*(?:18|19|20)\d{2}"""),
        Regex("""संहिता,?\s*(?:18|19|20)\d{2}"""), //                       संहिता, 2023
        Regex("""\b(?:IPC|CrPC|BNS|BNSS|BSA)\b"""), //                    bare-act acronyms
        Regex("""\bclause\s*\(?\d""", RegexOption.IGNORE_CASE),
        Regex("""\bchapter\s*[IVXL\d]""", RegexOption.IGNORE_CASE),
    )

    sealed interface Verdict {
        data class Clean(val text: String) : Verdict

        /** [pattern] is for logs/tests only — never shown to the user. */
        data class CitationLeak(val pattern: String) : Verdict

        data object Unusable : Verdict
    }

    /** Prompt instructions the model sometimes echoes instead of answering. */
    private val INSTRUCTION_LEAK = Regex(
        """LEGAL TEXT|reading level|class-?5|short sentences|छोटे वाक्य|""" +
            """[0-9] से [0-9] (छोटे|सरल)|In simple words:|Key point:|केवल LEGAL""",
        RegexOption.IGNORE_CASE,
    )

    fun inspect(rawLlmOutput: String): Verdict {
        val text = rawLlmOutput.trim().removeSuffix("<end_of_turn>").trim()
        if (text.isBlank()) return Verdict.Unusable
        for (pattern in CITATION_PATTERNS) {
            if (pattern.containsMatchIn(text)) return Verdict.CitationLeak(pattern.pattern)
        }
        // Small models (esp. in Hindi) can spew repeated tokens ("6-7 6-7 6-7…")
        // or echo the prompt. That is worse than the raw law — discard it.
        if (INSTRUCTION_LEAK.containsMatchIn(text) || isRepetitiveGibberish(text)) return Verdict.Unusable
        return Verdict.Clean(text)
    }

    private fun isRepetitiveGibberish(text: String): Boolean {
        val words = text.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size < 6) return false
        // One token dominates the output → degenerate repetition.
        val topShare = words.groupingBy { it }.eachCount().values.max().toFloat() / words.size
        if (topShare > 0.30f) return true
        // The same token repeated 4+ times in a row.
        var run = 1
        for (i in 1 until words.size) {
            if (words[i] == words[i - 1]) { run++; if (run >= 4) return true } else run = 1
        }
        return false
    }
}
