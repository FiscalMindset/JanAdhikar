package com.janadhikar.memory

/**
 * Maps the colloquial, emotional words people actually use in a crisis
 * ("killed", "slapped", "beat", "stolen") to the formal legal terms that
 * appear in statute text ("murder", "assault", "criminal force", "theft").
 *
 * This is what makes retrieval more than a semantic-similarity guess: pure
 * embedding search scores "someone killed my brother" far from the Murder
 * section because the words don't resemble legal prose. Expanding "killed" →
 * "murder OR homicide" and running an FTS5 keyword search finds the right
 * section directly.
 *
 * English + common Hindi/Hinglish crisis words are covered. Values are FTS5
 * MATCH fragments composed only of controlled legal terms (never raw user
 * text), so the resulting query is always syntactically safe.
 */
object CrisisLexicon {

    // word (lowercased) -> FTS5 fragment of legal terms found in the corpus
    private val MAP: Map<String, String> = buildMap {
        // ── Homicide ──
        for (w in listOf("kill", "killed", "killing", "murder", "murdered", "murdering", "मार", "मारा", "हत्या", "क़त्ल", "क़त्ल"))
            put(w, "murder OR homicide")
        // ── Assault / hurt ──
        for (w in listOf("slap", "slapped", "beat", "beaten", "beating", "hit", "hitting", "punch", "punched",
                "thrash", "thrashed", "assault", "assaulted", "attack", "attacked", "hurt", "injured", "injure",
                "मार", "पीटा", "पीट", "थप्पड़", "मारपीट"))
            put(w, "assault OR hurt OR \"criminal force\"")
        // ── Sexual offences ──
        for (w in listOf("rape", "raped", "raping", "molest", "molested", "molestation", "outrage",
                "बलात्कार", "छेड़छाड़"))
            put(w, "rape OR \"sexual\" OR modesty")
        // ── Property ──
        for (w in listOf("steal", "stole", "stolen", "stealing", "theft", "चोरी", "चुरा"))
            put(w, "theft")
        for (w in listOf("rob", "robbed", "robbery", "robbing", "snatch", "snatched", "loot", "looted", "डकैती", "लूट"))
            put(w, "robbery OR dacoity")
        for (w in listOf("cheat", "cheated", "cheating", "fraud", "defraud", "scam", "scammed", "धोखा", "ठगी"))
            put(w, "cheating OR fraud OR \"dishonestly\"")
        for (w in listOf("extort", "extortion", "extorted", "blackmail", "blackmailed", "वसूली"))
            put(w, "extortion")
        // ── Threats / intimidation ──
        for (w in listOf("threat", "threaten", "threatened", "threatening", "intimidate", "intimidated", "धमकी", "धमकाया"))
            put(w, "\"criminal intimidation\" OR threat")
        // ── Liberty / detention ──
        for (w in listOf("kidnap", "kidnapped", "kidnapping", "abduct", "abducted", "abduction", "अपहरण"))
            put(w, "kidnapping OR abduction")
        for (w in listOf("arrest", "arrested", "detain", "detained", "detention", "custody", "गिरफ़्तार", "गिरफ्तार", "हिरासत"))
            put(w, "arrest OR custody")
        for (w in listOf("wrongful", "confine", "confined", "confinement", "बंधक"))
            put(w, "\"wrongful confinement\" OR \"wrongful restraint\"")
        // ── Family / women ──
        for (w in listOf("dowry", "दहेज"))
            put(w, "dowry")
        for (w in listOf("cruelty", "harass", "harassed", "harassment", "domestic", "प्रताड़ना", "उत्पीड़न"))
            put(w, "cruelty OR harassment")
        // ── Trespass / property entry ──
        for (w in listOf("trespass", "trespassed", "trespassing", "अतिक्रमण"))
            put(w, "trespass")
        // ── Public servant / bribery ──
        for (w in listOf("bribe", "bribed", "bribery", "corrupt", "corruption", "रिश्वत", "घूस"))
            put(w, "\"public servant\" OR \"illegal gratification\"")
        // ── Defamation ──
        for (w in listOf("defame", "defamed", "defamation", "slander", "मानहानि"))
            put(w, "defamation")
    }

    /**
     * Builds an FTS5 MATCH query from the crisis words in [rawQuery], or "" if
     * none are present (in which case the retriever relies on vector search).
     */
    fun toFtsQuery(rawQuery: String): String {
        val fragments = LinkedHashSet<String>()
        for (word in TOKEN.findAll(rawQuery.lowercase()).map { it.value }) {
            MAP[word]?.let { fragments.add("($it)") }
        }
        return fragments.joinToString(" OR ")
    }

    // Include combining marks (\p{M}) so Devanagari vowel signs stay attached —
    // "चोरी" is च+ो+र+ी, and the matras are marks, not letters.
    private val TOKEN = Regex("""[\p{L}\p{M}]+""")
}
