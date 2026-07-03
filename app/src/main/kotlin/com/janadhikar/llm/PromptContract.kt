package com.janadhikar.llm

import com.janadhikar.input.AppLanguage
import com.janadhikar.memory.model.VerifiedCitation

/**
 * Statute text that provably originated from a [VerifiedCitation]. The private
 * constructor is the enforcement mechanism: there is no way to get user input,
 * transcripts, or arbitrary strings into this type, therefore no way to get
 * them into the prompt. (CONTRIBUTING.md Rule 1 — "single sealed injection point".)
 */
@JvmInline
value class VerbatimStatuteText private constructor(val value: String) {
    companion object {
        fun from(citation: VerifiedCitation, language: AppLanguage): VerbatimStatuteText =
            VerbatimStatuteText(
                when (language) {
                    AppLanguage.ENGLISH -> citation.verbatimTextEn
                    AppLanguage.HINDI -> citation.verbatimTextHi
                },
            )
    }
}

/**
 * THE prompt template. This object is the ONLY place in the codebase that
 * constructs LLM prompts, and [build] accepts exactly ONE dynamic field —
 * [VerbatimStatuteText]. Adding a second parameter that reaches the prompt
 * string is an automatic PR rejection (Rule 1).
 *
 * The LLM's role is TRANSLATOR, not lawyer: it rewrites retrieved statute text
 * as one plain directive. All metadata (statute, section, page) bypasses the
 * LLM entirely and is rendered from typed DB fields.
 */
object PromptContract {

    /**
     * Fixed answer styles. This is NOT a dynamic field — it selects among
     * hard-coded instructions, so no user text reaches the prompt (Rule 1 still
     * holds; [VerbatimStatuteText] remains the single injection point).
     */
    enum class Style { NORMAL, SIMPLER, EXAMPLE }

    private val RULES_EN =
        " Use ONLY information present in the LEGAL TEXT. " +
            "Do NOT mention or invent any section number, act name, page number, or citation. " +
            "Do NOT add advice or facts that are not in the LEGAL TEXT."
    private val RULES_HI =
        " केवल LEGAL TEXT में मौजूद जानकारी का उपयोग करें। " +
            "कोई धारा संख्या, अधिनियम का नाम, पृष्ठ संख्या या उद्धरण न लिखें, न बनाएँ। " +
            "LEGAL TEXT से बाहर कोई सलाह या तथ्य न जोड़ें।"

    fun build(verbatim: VerbatimStatuteText, output: AppLanguage, style: Style = Style.NORMAL): String {
        val instruction = when (output) {
            AppLanguage.ENGLISH -> when (style) {
                Style.NORMAL ->
                    "You are a helpful legal assistant explaining Indian law to an ordinary citizen " +
                        "with a class-5 reading level. Read the LEGAL TEXT below and explain it clearly " +
                        "in simple, everyday words. Structure your answer as:\n" +
                        "**In simple words:** one or two sentences on what this means for the person.\n" +
                        "**Key point:** the main right, protection, or punishment, as a short line.\n" +
                        "Do not begin with filler like 'Okay' or 'Sure'. Be direct and warm." + RULES_EN
                Style.SIMPLER ->
                    "Explain the LEGAL TEXT below to a 10-year-old child. Use the SHORTEST, simplest " +
                        "sentences possible and everyday examples from daily life. Avoid all legal or " +
                        "difficult words — if you must use one, explain it. Keep it to 3 short lines." + RULES_EN
                Style.EXAMPLE ->
                    "Read the LEGAL TEXT below and give ONE short, concrete real-life example (a small " +
                        "story of a person) that shows how this law works in practice. Start with " +
                        "**Example:** then 2–3 simple sentences." + RULES_EN
            }
            AppLanguage.HINDI -> when (style) {
                Style.SIMPLER ->
                    "नीचे दिए गए LEGAL TEXT को ऐसे समझाएँ जैसे किसी 10 साल के बच्चे को समझा रहे हों। " +
                        "बहुत छोटे और आसान वाक्य और रोज़मर्रा के उदाहरण इस्तेमाल करें। कठिन शब्द न लिखें। " +
                        "केवल 3 छोटी पंक्तियाँ लिखें।" + RULES_HI
                Style.EXAMPLE ->
                    "नीचे दिए गए LEGAL TEXT का एक छोटा असली-ज़िंदगी का उदाहरण (किसी व्यक्ति की छोटी कहानी) दें " +
                        "जिससे पता चले कि यह कानून कैसे काम करता है। **उदाहरण:** से शुरू करें, फिर 2–3 सरल वाक्य।" + RULES_HI
                Style.NORMAL ->
                    "आप एक आम नागरिक को कानून समझा रहे हैं जिसकी पढ़ाई कक्षा 5 तक है। " +
                        "नीचे दिए गए LEGAL TEXT को बहुत सरल, रोज़मर्रा के शब्दों में समझाएँ। " +
                        "2 से 4 छोटे वाक्य लिखें: पहले इसका व्यक्ति के लिए क्या मतलब है, फिर एक पंक्ति में " +
                        "उसके अधिकार या अपराध का सारांश।" + RULES_HI
            }
        }
        return buildString {
            append("<start_of_turn>user\n")
            append(instruction)
            append("\n\nLEGAL TEXT:\n\"\"\"\n")
            append(verbatim.value) // ← the single injection point
            append("\n\"\"\"<end_of_turn>\n<start_of_turn>model\n")
        }
    }
}
