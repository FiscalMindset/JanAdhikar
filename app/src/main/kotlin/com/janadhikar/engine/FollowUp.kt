package com.janadhikar.engine

/**
 * A conversation isn't a series of unrelated searches. When the user says
 * "explain this in simple words", "what does that mean", or "give an example",
 * they mean the PREVIOUS answer — there is nothing new to retrieve. This
 * detects such follow-ups so the engine re-explains the last provision instead
 * of searching (and refusing).
 */
object FollowUp {

    // Back-reference to the previous answer, or a bare request to rephrase.
    private val FOLLOWUP = Regex(
        """\b(this|that|it|these|those|above|previous)\b""" +
            """|\b(simpl|in simple|simple words|easy words|easier|elaborate|in detail|more detail""" +
            """|tell me more|give (an )?example|for example|summari[sz]e|break ?it ?down""" +
            """|samjhao|samjha|aasan|aur batao|और बताओ|सरल|आसान|मतलब|उदाहरण)\b""",
        RegexOption.IGNORE_CASE,
    )

    // If they named a concrete provision, it's a NEW question, not a follow-up.
    private val HAS_REFERENCE = Regex("""\b(art(icle)?|sec(tion)?|s)\.?\s*\d""", RegexOption.IGNORE_CASE)

    private val WANTS_EXAMPLE = Regex("""example|उदाहरण""", RegexOption.IGNORE_CASE)
    private val WANTS_SIMPLER =
        Regex("""simpl|simple|easy|easier|aasan|saral|सरल|आसान|child|kid""", RegexOption.IGNORE_CASE)

    /** How to re-answer a follow-up: a distinctly different take, not a repeat. */
    enum class Intent { SIMPLER, EXAMPLE, REPHRASE }

    fun isFollowUp(rawQuery: String): Boolean =
        FOLLOWUP.containsMatchIn(rawQuery) && !HAS_REFERENCE.containsMatchIn(rawQuery)

    /** Classify a follow-up so the re-explanation actually differs. */
    fun classify(rawQuery: String): Intent = when {
        WANTS_EXAMPLE.containsMatchIn(rawQuery) -> Intent.EXAMPLE
        WANTS_SIMPLER.containsMatchIn(rawQuery) -> Intent.SIMPLER
        else -> Intent.REPHRASE
    }
}
