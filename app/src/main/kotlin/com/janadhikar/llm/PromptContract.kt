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

        /**
         * Concatenated verbatim text of SEVERAL provisions — still 100% from the
         * database (each is a [VerifiedCitation]), for synthesising a broad
         * answer (e.g. all Fundamental Rights) from the REAL law, not a canned
         * summary. Capped so the model's context isn't overrun.
         */
        fun combined(citations: List<VerifiedCitation>, language: AppLanguage, maxChars: Int = 2000): VerbatimStatuteText {
            val text = citations.joinToString("\n\n") { c ->
                if (language == AppLanguage.HINDI) c.verbatimTextHi else c.verbatimTextEn
            }
            return VerbatimStatuteText(if (text.length > maxChars) text.take(maxChars) else text)
        }
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
        " Base the answer on the LEGAL TEXT and well-established Indian law. Be accurate; do not " +
            "invent fake case names or provisions. If unsure about an interpretation, keep it general."
    private val RULES_HI =
        " उत्तर LEGAL TEXT और भारतीय कानून की स्थापित समझ पर आधारित रखें। सही जानकारी दें; कोई झूठा " +
            "मुकदमा या प्रावधान न बनाएँ। यदि किसी व्याख्या के बारे में निश्चित न हों तो सामान्य रखें।"

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

    private fun synthesisInstruction(output: AppLanguage): String = when (output) {
        AppLanguage.ENGLISH ->
            "You are explaining a group of related Indian constitutional/legal provisions to an " +
                "ordinary citizen. Read ALL the LEGAL TEXT below and write ONE clear, well-organised " +
                "answer that covers each right/rule in it — a short intro, then a simple line for each, " +
                "in plain everyday words. Be accurate and complete but easy to read." + RULES_EN
        AppLanguage.HINDI ->
            "आप कई संबंधित संवैधानिक/कानूनी प्रावधानों को एक आम नागरिक को समझा रहे हैं। नीचे दिए गए सभी " +
                "कानून पढ़ें और एक स्पष्ट, व्यवस्थित उत्तर लिखें जो हर अधिकार/नियम को सरल रोज़मर्रा की हिंदी में " +
                "कवर करे — पहले एक छोटा परिचय, फिर हर एक के लिए एक सरल पंक्ति।" + RULES_HI
    }

    /** Gemma-format synthesis over MANY provisions (all from the DB). */
    fun buildSynthesis(combined: VerbatimStatuteText, output: AppLanguage): String =
        "<start_of_turn>user\n${synthesisInstruction(output)}\n\nLEGAL TEXT:\n\"\"\"\n" +
            "${combined.value}\n\"\"\"<end_of_turn>\n<start_of_turn>model\n"

    /** Qwen ChatML synthesis over MANY provisions. */
    fun buildSynthesisChatML(combined: VerbatimStatuteText, output: AppLanguage): String =
        "<|im_start|>system\n${synthesisInstruction(output)}<|im_end|>\n<|im_start|>user\nLEGAL TEXT:\n\"\"\"\n" +
            "${combined.value}\n\"\"\"<|im_end|>\n<|im_start|>assistant\n"

    private fun instruction(output: AppLanguage, style: Style): String {
        return when (output) {
            AppLanguage.ENGLISH -> when (style) {
                Style.NORMAL ->
                    "You are an expert Indian legal assistant. Explain the provision in the LEGAL TEXT " +
                        "below in a clear, well-structured, decorative Markdown answer, like a good " +
                        "teacher. Format it like this:\n" +
                        "- Start by quoting the exact wording using a Markdown blockquote line " +
                        "(begin the line with '> ').\n" +
                        "- Then a section **### What it means** in simple everyday words.\n" +
                        "- Then **### Key points / how it is interpreted** — well-known expansions or " +
                        "landmark cases ONLY if you are sure of them.\n" +
                        "- Then **### Why it matters**.\n" +
                        "Put the 2–3 most important words in **bold**. Keep it accurate and readable " +
                        "for an ordinary citizen. Do not begin with 'Okay' or 'Sure'." + RULES_EN
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
