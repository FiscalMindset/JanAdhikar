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

    /** Gemma-format prompt (MediaPipe). */
    fun build(verbatim: VerbatimStatuteText, output: AppLanguage, style: Style = Style.NORMAL): String =
        buildString {
            append("<start_of_turn>user\n")
            append(instruction(output, style))
            append("\n\nLEGAL TEXT:\n\"\"\"\n")
            append(verbatim.value) // ← the single injection point
            append("\n\"\"\"<end_of_turn>\n<start_of_turn>model\n")
        }

    /** Qwen ChatML-format prompt (llama.cpp). Same single injection point. */
    fun buildChatML(verbatim: VerbatimStatuteText, output: AppLanguage, style: Style = Style.NORMAL): String =
        buildString {
            append("<|im_start|>system\n")
            append(instruction(output, style))
            append("<|im_end|>\n<|im_start|>user\nLEGAL TEXT:\n\"\"\"\n")
            append(verbatim.value) // ← the single injection point
            append("\n\"\"\"<|im_end|>\n<|im_start|>assistant\n")
        }

    private fun instruction(output: AppLanguage, style: Style): String {
        return when (output) {
            AppLanguage.ENGLISH -> when (style) {
                Style.NORMAL ->
                    "You are a helpful legal assistant explaining Indian law to an ordinary citizen " +
                        "with a class-5 reading level. Read the LEGAL TEXT below and explain it in " +
                        "clear, simple, everyday words — 2 to 4 short natural sentences. Just say what " +
                        "it means for the person and their main right, protection, or punishment. " +
                        "Do NOT use headings, labels, or bold like 'In simple words' or 'Key point'. " +
                        "Do not begin with 'Okay' or 'Sure'. Write plain, warm sentences." + RULES_EN
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
                    "नीचे दिए गए कानून को ऐसे समझाएँ जैसे किसी दस साल के बच्चे को — बहुत आसान हिंदी और " +
                        "रोज़मर्रा के उदाहरण से। कठिन शब्द न लिखें।" + RULES_HI
                Style.EXAMPLE ->
                    "नीचे दिए गए कानून का एक असली-ज़िंदगी का उदाहरण दें (किसी व्यक्ति की छोटी कहानी) " +
                        "जिससे पता चले कि यह कानून कैसे काम करता है।" + RULES_HI
                Style.NORMAL ->
                    "आप एक आम नागरिक को भारतीय कानून सरल हिंदी में समझा रहे हैं। नीचे दिए गए कानून का अर्थ " +
                        "रोज़मर्रा के आसान शब्दों में बताएँ — यह व्यक्ति के लिए क्या मायने रखता है और उसका मुख्य " +
                        "अधिकार या सज़ा क्या है। कोई शीर्षक, लेबल या अंग्रेज़ी न लिखें।" + RULES_HI
            }
        }
    }
}
