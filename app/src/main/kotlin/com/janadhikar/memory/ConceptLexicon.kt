package com.janadhikar.memory

/**
 * Broad, conceptual questions ("what are my fundamental rights") have no single
 * matching section — the answer is a *set* of provisions spread across the law.
 * Pure similarity search fails them (it returned a criminal self-defence
 * section for "fundamental rights"). This lexicon maps such concepts directly
 * to the authoritative provisions, so the answer is correct and complete.
 *
 * Each concept lists (statute-name-contains, section/article number) pairs, in
 * priority order — the first becomes the primary, the rest are shown alongside.
 */
object ConceptLexicon {

    data class Ref(val statuteContains: String, val number: String)

    private val CONCEPTS: List<Pair<Regex, List<Ref>>> = listOf(
        // Fundamental Rights — one representative article per category.
        regexOf("fundamental rights", "my rights", "basic rights", "constitutional rights",
            "मौलिक अधिकार", "मेरे अधिकार") to listOf(
            Ref("Constitution", "14"),  // Right to Equality
            Ref("Constitution", "19"),  // Right to Freedom
            Ref("Constitution", "21"),  // Life & personal liberty
            Ref("Constitution", "23"),  // Right against Exploitation
            Ref("Constitution", "25"),  // Freedom of Religion
            Ref("Constitution", "29"),  // Cultural & Educational Rights
            Ref("Constitution", "32"),  // Right to Constitutional Remedies
        ),
        regexOf("right to equality", "equality before law", "समानता का अधिकार") to listOf(
            Ref("Constitution", "14"), Ref("Constitution", "15"), Ref("Constitution", "16"),
        ),
        regexOf("right to freedom", "freedom of speech", "स्वतंत्रता का अधिकार") to listOf(
            Ref("Constitution", "19"), Ref("Constitution", "21"), Ref("Constitution", "22"),
        ),
        regexOf("against exploitation", "forced labour", "child labour", "human trafficking",
            "शोषण के विरुद्ध") to listOf(
            Ref("Constitution", "23"), Ref("Constitution", "24"),
        ),
        regexOf("freedom of religion", "religious freedom", "धर्म की स्वतंत्रता") to listOf(
            Ref("Constitution", "25"), Ref("Constitution", "26"), Ref("Constitution", "28"),
        ),
        regexOf("cultural and educational", "right to education", "minority rights",
            "शिक्षा का अधिकार") to listOf(
            Ref("Constitution", "29"), Ref("Constitution", "30"), Ref("Constitution", "21A"),
        ),
        regexOf("constitutional remedies", "writ", "habeas corpus", "संवैधानिक उपचार") to listOf(
            Ref("Constitution", "32"), Ref("Constitution", "226"),
        ),
        regexOf("fundamental duties", "मौलिक कर्तव्य") to listOf(
            Ref("Constitution", "51A"),
        ),
        regexOf("directive principles", "नीति निदेशक तत्व") to listOf(
            Ref("Constitution", "38"), Ref("Constitution", "39"), Ref("Constitution", "39A"), Ref("Constitution", "41"),
        ),
    )

    /** Returns the authoritative provisions for a conceptual query, or empty. */
    fun resolve(query: String): List<Ref> {
        val q = query.lowercase()
        return CONCEPTS.firstOrNull { it.first.containsMatchIn(q) }?.second ?: emptyList()
    }

    private fun regexOf(vararg phrases: String): Regex =
        Regex(phrases.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
}
