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

    fun build(verbatim: VerbatimStatuteText, output: AppLanguage): String {
        val instruction = when (output) {
            AppLanguage.ENGLISH ->
                "Rewrite the LEGAL TEXT below as ONE short, simple instruction in plain English " +
                    "telling a citizen what they can do or demand right now. " +
                    "Use only information present in the LEGAL TEXT. " +
                    "Do NOT mention or invent any section number, act name, page number, or citation. " +
                    "Do NOT add advice, warnings, or facts that are not in the LEGAL TEXT. " +
                    "Maximum 25 words. Output only the instruction."
            AppLanguage.HINDI ->
                "नीचे दिए गए LEGAL TEXT को सरल हिंदी में एक छोटे निर्देश के रूप में लिखें — " +
                    "नागरिक अभी क्या कर सकता है या क्या माँग सकता है। " +
                    "केवल LEGAL TEXT में मौजूद जानकारी का उपयोग करें। " +
                    "कोई धारा संख्या, अधिनियम का नाम, पृष्ठ संख्या या उद्धरण न लिखें, न बनाएँ। " +
                    "LEGAL TEXT से बाहर की कोई सलाह या तथ्य न जोड़ें। " +
                    "अधिकतम 25 शब्द। केवल निर्देश लिखें।"
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
