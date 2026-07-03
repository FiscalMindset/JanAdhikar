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

    /**
     * Common Hindi function/question words written in the Latin script
     * (Hinglish). A citizen who types "article 15 kya hain" wants a Hindi
     * answer, not English — these markers catch that.
     */
    private val HINGLISH = setOf(
        "kya", "kyaa", "hain", "hai", "hai", "kaise", "kaisa", "kyu", "kyun", "kyon",
        "mera", "meri", "mere", "mujhe", "muje", "adhikar", "adhikaar", "kanoon", "kanun",
        "dhara", "anuchhed", "matlab", "batao", "bataye", "bataiye", "samjhao", "samjhaao",
        "kaun", "konsa", "kaunsa", "kitne", "kitna", "kitni", "hota", "hoti", "karna", "sakta",
        "nahi", "nahin", "gaya", "diya", "raha", "rahe", "wala", "wali", "koi", "aur",
        "girftar", "girftaar", "shikayat", "sazaa", "saza",
    )

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
     * Devanagari-dominant → Hindi. Latin script with Hinglish markers (kya,
     * hain, adhikar…) → Hindi too, so a Hinglish question gets a Hindi answer.
     * Otherwise English.
     */
    fun detectLanguage(text: String): AppLanguage {
        val devanagari = DEVANAGARI.findAll(text).count()
        val letters = text.count { !it.isWhitespace() }
        if (letters == 0) return AppLanguage.ENGLISH
        if (devanagari * 2 >= letters) return AppLanguage.HINDI

        val words = text.lowercase().split(WHITESPACE)
        val hinglishHits = words.count { it.trim('?', '.', ',', '!') in HINGLISH }
        return if (hinglishHits >= 1) AppLanguage.HINDI else AppLanguage.ENGLISH
    }
}
