package com.janadhikar.memory

/**
 * Direct citations — "what is article 15", "section 302", "BNS section 63" —
 * name the exact provision. Similarity search is the wrong tool for these; the
 * user wants THAT provision. This parses the reference so the retriever can look
 * it up directly.
 */
object DirectReference {

    /** unit is "ARTICLE" or "SECTION"; [statuteHint] narrows which act, if named. */
    data class Ref(val unit: String, val number: String, val statuteHint: String?)

    // Also match the Hinglish/Hindi names a citizen actually types: "anuchhed 21"
    // (अनुच्छेद = article) and "dhara 302" (धारा = section). Without these, a
    // Hinglish reference fell through to fuzzy search and drifted to the wrong
    // provision. "anuch[a-z]*" covers spelling variants (anuchhed, anucched…).
    private val ARTICLE =
        Regex("""(?:\b(?:art(?:icle)?|anuch[a-z]*)|अनुच्छेद)\.?\s*(\d+\s*[A-Za-z]?)\b""", RegexOption.IGNORE_CASE)
    private val SECTION =
        Regex("""(?:\b(?:sec(?:tion)?|s\.|dhaara|dhara)|धारा)\.?\s*(\d+\s*[A-Za-z]?)\b""", RegexOption.IGNORE_CASE)

    // Common ways citizens name each act (incl. the old codes they map to).
    private val STATUTE_HINTS = listOf(
        Regex("""constitution|संविधान""", RegexOption.IGNORE_CASE) to "Constitution",
        Regex("""\bbns\b|nyaya|न्याय|ipc|penal""", RegexOption.IGNORE_CASE) to "Nyaya",
        Regex("""\bbnss\b|nagarik|नागरिक|crpc|cr\.?p\.?c""", RegexOption.IGNORE_CASE) to "Nagarik",
        Regex("""\bbsa\b|sakshya|साक्ष्य|evidence""", RegexOption.IGNORE_CASE) to "Sakshya",
        Regex("""motor|vehicle|traffic|driving|मोटर""", RegexOption.IGNORE_CASE) to "Motor",
    )

    fun parse(query: String): Ref? {
        val hint = STATUTE_HINTS.firstOrNull { it.first.containsMatchIn(query) }?.second

        ARTICLE.find(query)?.let {
            // "article N" is unambiguous — the Constitution.
            return Ref("ARTICLE", it.groupValues[1].replace(" ", "").uppercase(), hint ?: "Constitution")
        }
        SECTION.find(query)?.let {
            return Ref("SECTION", it.groupValues[1].replace(" ", "").uppercase(), hint)
        }
        return null
    }
}
