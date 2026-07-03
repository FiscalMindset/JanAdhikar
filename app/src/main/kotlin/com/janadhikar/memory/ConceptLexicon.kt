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

        // ── Everyday criminal-procedure questions ──
        regexOf("rights when arrested", "rights if arrested", "arrest rights", "police arrest me",
            "if police arrest", "गिरफ्तारी पर मेरे अधिकार") to Concept(
            overview = """
                If you are arrested, you have these rights:

                - **Know why** — the police must tell you the exact grounds of arrest, and whether the offence is bailable *(Section 47)*.
                - **Not be handcuffed needlessly** and be treated with dignity while arrested *(Section 43)*.
                - **A lawyer** — you may meet a lawyer of your choice during questioning *(Section 38)*.
                - **Produced before a court within 24 hours** — the police cannot hold you longer without a Magistrate's order *(Section 58)*.

                These come from the Bharatiya Nagarik Suraksha Sanhita, 2023. Tap the cards below for the exact words.
            """.trimIndent(),
            refs = c("Nagarik Suraksha", "47", "43", "38", "58", "35"),
        ),
        regexOf("file an fir", "file fir", "how to file fir", "what is fir", "lodge complaint",
            "police not registering", "एफआईआर", "प्राथमिकी") to Concept(
            overview = """
                An **FIR (First Information Report)** is the first step to start a police investigation into a serious (cognizable) crime.

                - The police **must** write down the information you give about such an offence, read it back to you, and give you a **free copy** *(Section 173)*.
                - You can give the information orally or in writing; a woman may give it to a woman officer for certain offences.
                - If the police refuse, you can send it in writing to the Superintendent of Police.
            """.trimIndent(),
            refs = c("Nagarik Suraksha", "173", "174"),
        ),
        regexOf("get bail", "how to get bail", "what is bail", "apply for bail", "जमानत") to Concept(
            overview = """
                **Bail** means being released from custody while the case continues, usually on a promise (bond) to appear in court.

                - For a **bailable** offence, bail is your right — the police or court must release you on bond *(Section 478)*.
                - For a **non-bailable** offence, only a court can grant bail, and it decides based on the seriousness of the case *(Section 480)*.
                - The police must tell you at the time of arrest whether your offence is bailable *(Section 47)*.
            """.trimIndent(),
            refs = c("Nagarik Suraksha", "478", "480", "47"),
        ),
        regexOf("woman arrest", "women arrest", "arrest a woman", "woman at night",
            "महिला की गिरफ्तारी", "रात में गिरफ्तारी") to Concept(
            overview = """
                Special protections apply when a **woman** is arrested:

                - A woman **cannot normally be arrested after sunset or before sunrise**; if truly necessary, a woman police officer must get a Magistrate's prior written permission *(Section 43)*.
                - Her arrest and search must be done by or in the presence of a **woman officer**, with dignity.
                - She still has all the general arrest rights — grounds, a lawyer, and court within 24 hours.
            """.trimIndent(),
            refs = c("Nagarik Suraksha", "43", "47", "58"),
        ),
        regexOf("driving without licence", "driving without license", "no licence", "traffic fine",
            "traffic rules", "बिना लाइसेंस", "यातायात") to Concept(
            overview = """
                Under the **Motor Vehicles Act, 1988**:

                - You **must hold a valid driving licence** to drive, and carry it while driving *(Section 3)*.
                - Driving without a licence, or letting an unlicensed person drive your vehicle, is a punishable offence with a fine.
                - Traffic police can check your licence and vehicle documents on duty.

                Tap the cards below for the exact provisions.
            """.trimIndent(),
            refs = c("Motor Vehicles", "3", "4", "5"),
        ),
        regexOf("self defence", "self-defence", "private defence", "right to defend",
            "आत्मरक्षा", "निजी प्रतिरक्षा") to Concept(
            overview = """
                The law gives you a **Right of Private Defence** — you may protect yourself and your property from an attack.

                - You can use reasonable force to defend your own body, or another person's, against an assault *(Sections 34–38)*.
                - The force must be **proportionate** — only as much as needed to stop the threat, not revenge afterwards.
                - This right does not extend to causing more harm than necessary.
            """.trimIndent(),
            refs = c("Nyaya Sanhita", "34", "35", "37", "38"),
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
