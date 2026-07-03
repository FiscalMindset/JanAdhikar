package com.janadhikar.memory

/**
 * Broad, conceptual questions ("what are my fundamental rights") have no single
 * matching section — the answer is a *set* of provisions, and the citizen wants
 * an OVERVIEW of all of them, not one article. Pure similarity search fails
 * these (it returned a criminal self-defence section for "fundamental rights").
 *
 * Each concept carries:
 *   - an authoritative [overview] (the actual constitutional structure — this
 *     is established fact, not model output), shown as the answer directly, and
 *   - the [refs] (statute-name-contains + article/section number) whose verbatim
 *     cards appear below so the citizen can read the exact law.
 */
object ConceptLexicon {

    data class Ref(val statuteContains: String, val number: String)
    data class Concept(val overview: String, val refs: List<Ref>)

    private fun c(statute: String, vararg numbers: String) = numbers.map { Ref(statute, it) }

    private val CONCEPTS: List<Pair<Regex, Concept>> = listOf(
        regexOf("fundamental rights", "my rights", "basic rights", "constitutional rights",
            "मौलिक अधिकार", "मेरे अधिकार") to Concept(
            overview = """
                The Constitution of India guarantees you **six Fundamental Rights**:

                **1. Right to Equality** — everyone is equal before the law; no discrimination by religion, caste, sex, or birthplace *(Articles 14–18)*.
                **2. Right to Freedom** — freedom of speech, movement, and to live with dignity; protection when arrested *(Articles 19–22)*.
                **3. Right against Exploitation** — no forced labour, human trafficking, or child labour *(Articles 23–24)*.
                **4. Right to Freedom of Religion** — practise, profess, and spread any faith *(Articles 25–28)*.
                **5. Cultural and Educational Rights** — protect your language, script, and culture; minorities may run their own schools *(Articles 29–30)*.
                **6. Right to Constitutional Remedies** — go directly to the Supreme Court to enforce these rights *(Article 32)*.

                Tap any card below to read the exact words of the law.
            """.trimIndent(),
            refs = c("Constitution", "14", "19", "21", "23", "25", "29", "32"),
        ),
        regexOf("right to equality", "equality before law", "समानता का अधिकार") to Concept(
            overview = "**Right to Equality** means the State cannot deny anyone equality before the " +
                "law, and cannot discriminate on grounds of religion, race, caste, sex, or place of " +
                "birth. It also guarantees equal opportunity in public employment *(Articles 14–18)*.",
            refs = c("Constitution", "14", "15", "16"),
        ),
        regexOf("right to freedom", "freedom of speech", "स्वतंत्रता का अधिकार") to Concept(
            overview = "**Right to Freedom** protects your freedom of speech and expression, to move " +
                "and settle anywhere in India, and your life and personal liberty. It also gives you " +
                "protections when arrested or detained *(Articles 19–22)*.",
            refs = c("Constitution", "19", "21", "22"),
        ),
        regexOf("against exploitation", "forced labour", "child labour", "human trafficking",
            "शोषण के विरुद्ध") to Concept(
            overview = "**Right against Exploitation** bans human trafficking, forced ('begar') " +
                "labour, and the employment of children below 14 in factories or hazardous work " +
                "*(Articles 23–24)*.",
            refs = c("Constitution", "23", "24"),
        ),
        regexOf("freedom of religion", "religious freedom", "धर्म की स्वतंत्रता") to Concept(
            overview = "**Right to Freedom of Religion** lets everyone freely practise, profess, and " +
                "propagate their religion, and manage their own religious affairs *(Articles 25–28)*.",
            refs = c("Constitution", "25", "26", "28"),
        ),
        regexOf("cultural and educational", "right to education", "minority rights",
            "शिक्षा का अधिकार") to Concept(
            overview = "**Cultural and Educational Rights** let any community protect its language, " +
                "script, and culture, and allow minorities to set up and run their own educational " +
                "institutions *(Articles 29–30)*. Free education for children 6–14 is a right *(21A)*.",
            refs = c("Constitution", "29", "30", "21A"),
        ),
        regexOf("constitutional remedies", "writ", "habeas corpus", "संवैधानिक उपचार") to Concept(
            overview = "**Right to Constitutional Remedies** lets you go straight to the Supreme Court " +
                "if any Fundamental Right is violated. The Court can issue writs like *habeas corpus* " +
                "to protect you *(Article 32)*; High Courts have similar power *(Article 226)*.",
            refs = c("Constitution", "32", "226"),
        ),
        regexOf("fundamental duties", "मौलिक कर्तव्य") to Concept(
            overview = "**Fundamental Duties** are the responsibilities every citizen owes the nation " +
                "— respecting the Constitution, the flag, and the anthem, protecting the environment, " +
                "and promoting harmony *(Article 51A)*.",
            refs = c("Constitution", "51A"),
        ),
        regexOf("directive principles", "नीति निदेशक तत्व") to Concept(
            overview = "**Directive Principles of State Policy** are goals that guide the government — " +
                "securing justice, equal pay, free legal aid, and public welfare. They are not " +
                "enforceable in court but are fundamental to governance *(Articles 38–51)*.",
            refs = c("Constitution", "38", "39", "39A", "41"),
        ),
    )

    /** Returns the concept for a broad query, or null if it isn't one. */
    fun resolve(query: String): Concept? {
        val q = query.lowercase()
        return CONCEPTS.firstOrNull { it.first.containsMatchIn(q) }?.second
    }

    private fun regexOf(vararg phrases: String): Regex =
        Regex(phrases.joinToString("|") { Regex.escape(it) }, RegexOption.IGNORE_CASE)
}
