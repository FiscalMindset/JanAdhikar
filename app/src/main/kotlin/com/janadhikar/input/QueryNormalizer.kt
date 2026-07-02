package com.janadhikar.input

/** UI + LLM output language. */
enum class AppLanguage { ENGLISH, HINDI }

/** A query ready for retrieval, whatever mode it arrived by. */
data class NormalizedQuery(val text: String, val language: AppLanguage)

/**
 * THE single entry point for every input mode (typed text, whisper transcript).
 * Speech and text are equal citizens: both become a [NormalizedQuery] here and
 * are indistinguishable downstream.
 */
object QueryNormalizer {

    /** Below this there is nothing to search for; the UI keeps listening. */
    private const val MIN_QUERY_CHARS = 6

    private val WHITESPACE = Regex("""\s+""")

    /** Devanagari block — used to detect Hindi/Hinglish queries. */
    private val DEVANAGARI = Regex("""[ऀ-ॿ]""")

    /** Filler tokens whisper commonly emits that carry no retrieval signal. */
    private val FILLERS = setOf(
        "um", "uh", "hmm", "haan", "अच्छा", "मतलब", "yaar", "यार", "like",
    )

    fun normalize(raw: String): NormalizedQuery? {
        val cleaned = raw
            .replace(WHITESPACE, " ")
            .trim()
            .split(" ")
            .filter { it.lowercase() !in FILLERS }
            .joinToString(" ")

        if (cleaned.length < MIN_QUERY_CHARS) return null

        return NormalizedQuery(text = cleaned, language = detectLanguage(cleaned))
    }

    /**
     * Devanagari-dominant → Hindi. Hinglish (Hindi in Latin script) resolves
     * to ENGLISH here, which is fine: the embedder is multilingual and the
     * output language is separately overridable by the user's UI preference.
     */
    fun detectLanguage(text: String): AppLanguage {
        val devanagari = DEVANAGARI.findAll(text).count()
        val letters = text.count { !it.isWhitespace() }
        if (letters == 0) return AppLanguage.ENGLISH
        return if (devanagari * 2 >= letters) AppLanguage.HINDI else AppLanguage.ENGLISH
    }
}
