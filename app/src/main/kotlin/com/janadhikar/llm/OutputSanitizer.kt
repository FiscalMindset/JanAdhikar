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

    sealed interface Verdict {
        data class Clean(val text: String) : Verdict

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
        // Article/section references are now WELCOME — a rich legal answer cites
        // them (Article 21, 21A, Maneka Gandhi, etc.). We no longer discard the
        // answer for that; the exact verified law is still shown in the source
        // card, so the citizen can always check. We only reject genuine garbage:
        // small-model token repetition ("6-7 6-7 6-7…") or an echoed prompt.
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
