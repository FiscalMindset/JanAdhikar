package com.janadhikar.llm

import com.google.common.truth.Truth.assertThat
import com.janadhikar.input.AppLanguage
import com.janadhikar.memory.model.VerifiedCitation
import org.junit.Test
import kotlin.reflect.full.declaredFunctions

class PromptContractTest {

    private val citation = VerifiedCitation(
        chunkId = 1L,
        statuteName = "Bharatiya Nagarik Suraksha Sanhita, 2023",
        statuteNameHi = "भारतीय नागरिक सुरक्षा संहिता, 2023",
        unit = "SECTION",
        sectionNumber = "47",
        clause = null,
        pageNumber = 21,
        verbatimTextEn = "Every police officer arresting any person shall forthwith communicate " +
            "to him full particulars of the offence for which he is arrested.",
        verbatimTextHi = "गिरफ्तार करने वाला प्रत्येक पुलिस अधिकारी उस व्यक्ति को अपराध की पूरी जानकारी तुरंत बताएगा।",
        sourceDocument = "BNSS_2023_Gazette.pdf",
        sourceUrl = "https://www.indiacode.nic.in/handle/123456789/21419",
        compilationDate = "2026-05-01",
    )

    @Test
    fun `prompt contains the verbatim statute text exactly once`() {
        val verbatim = VerbatimStatuteText.from(citation, AppLanguage.ENGLISH)
        val prompt = PromptContract.build(verbatim, AppLanguage.ENGLISH)

        assertThat(prompt).contains(citation.verbatimTextEn)
        // exactly one occurrence — the single injection point
        assertThat(prompt.split(citation.verbatimTextEn)).hasSize(2)
    }

    @Test
    fun `prompt never carries citation metadata to the model`() {
        // Rule 1: the LLM must not even SEE section/page metadata — what it
        // never receives, it can never echo with corruption.
        val verbatim = VerbatimStatuteText.from(citation, AppLanguage.ENGLISH)
        val prompt = PromptContract.build(verbatim, AppLanguage.ENGLISH)

        assertThat(prompt).doesNotContain(citation.sectionNumber + " of")
        assertThat(prompt).doesNotContain("Page")
        assertThat(prompt).doesNotContain(citation.statuteName)
        assertThat(prompt).doesNotContain(citation.sourceDocument)
        assertThat(prompt).doesNotContain(citation.compilationDate)
    }

    @Test
    fun `hindi output language selects the hindi verbatim text`() {
        val verbatim = VerbatimStatuteText.from(citation, AppLanguage.HINDI)
        val prompt = PromptContract.build(verbatim, AppLanguage.HINDI)
        assertThat(prompt).contains(citation.verbatimTextHi)
        assertThat(prompt).doesNotContain(citation.verbatimTextEn)
    }

    @Test
    fun `prompt instructs the model not to cite`() {
        val en = PromptContract.build(VerbatimStatuteText.from(citation, AppLanguage.ENGLISH), AppLanguage.ENGLISH)
        val hi = PromptContract.build(VerbatimStatuteText.from(citation, AppLanguage.HINDI), AppLanguage.HINDI)
        assertThat(en).contains("Do NOT mention or invent any section number")
        assertThat(hi).contains("कोई धारा संख्या")
    }

    @Test
    fun `contract shape - build is the only prompt-producing function and takes exactly one dynamic field`() {
        // Structural guard (Rule 1): PromptContract exposes exactly one public
        // function; its only dynamic parameter type is VerbatimStatuteText,
        // which is constructible solely from a VerifiedCitation.
        // The PUBLIC prompt builders (Gemma `build`, Qwen `buildChatML`) must
        // each take VerbatimStatuteText as the ONLY free-text field; AppLanguage
        // and Style are closed types. No public builder may take a raw String —
        // that would be an uncontrolled injection point (Rule 1).
        val publicBuilders = PromptContract::class.declaredFunctions
            .filter { it.visibility == kotlin.reflect.KVisibility.PUBLIC }
        assertThat(publicBuilders.map { it.name })
            .containsExactly("build", "buildChatML")
        publicBuilders.forEach { fn ->
            val types = fn.parameters.drop(1).map { it.type.toString() }
            assertThat(types).containsExactly(
                "com.janadhikar.llm.VerbatimStatuteText",
                "com.janadhikar.input.AppLanguage",
                "com.janadhikar.llm.PromptContract.Style",
            )
            assertThat(types.count { it.contains("kotlin.String") }).isEqualTo(0)
        }
    }
}
