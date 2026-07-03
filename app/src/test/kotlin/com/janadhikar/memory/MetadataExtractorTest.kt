package com.janadhikar.memory

import com.google.common.truth.Truth.assertThat
import com.janadhikar.memory.model.StatuteChunkEntity
import org.junit.Test

class MetadataExtractorTest {

    private val validRow = StatuteChunkEntity(
        id = 1L,
        nodeId = 10L,
        statuteName = "Bharatiya Nagarik Suraksha Sanhita, 2023",
        statuteNameHi = "भारतीय नागरिक सुरक्षा संहिता, 2023",
        unit = "SECTION",
        sectionNumber = "47",
        clause = "(1)",
        pageNumber = 21,
        chunkTextEn = "Every police officer... shall forthwith communicate to him full particulars of the offence.",
        chunkTextHi = "प्रत्येक पुलिस अधिकारी... अपराध की पूरी जानकारी तुरंत बताएगा।",
        sourceDocument = "BNSS_2023_Gazette.pdf",
        sourceSha256 = "a".repeat(64),
        compilationDate = "2026-05-01",
    )

    @Test
    fun `valid row maps field-for-field with no derivation`() {
        val result = MetadataExtractor.extract(validRow)
        val citation = (result as MetadataExtractor.Extraction.Valid).citation
        assertThat(citation.statuteName).isEqualTo(validRow.statuteName)
        assertThat(citation.sectionNumber).isEqualTo("47")
        assertThat(citation.clause).isEqualTo("(1)")
        assertThat(citation.pageNumber).isEqualTo(21)
        assertThat(citation.verbatimTextEn).isEqualTo(validRow.chunkTextEn)
        assertThat(citation.verbatimTextHi).isEqualTo(validRow.chunkTextHi)
        assertThat(citation.compilationDate).isEqualTo("2026-05-01")
    }

    @Test
    fun `null clause is preserved as null, never invented`() {
        val result = MetadataExtractor.extract(validRow.copy(clause = null))
        assertThat((result as MetadataExtractor.Extraction.Valid).citation.clause).isNull()
    }

    @Test
    fun `blank clause normalizes to null`() {
        val result = MetadataExtractor.extract(validRow.copy(clause = "  "))
        assertThat((result as MetadataExtractor.Extraction.Valid).citation.clause).isNull()
    }

    // ── Rejection: every malformed field kills the WHOLE row (Rule 2) ───────

    @Test
    fun `blank statute name rejects the row`() {
        assertRejected(validRow.copy(statuteName = " "))
    }

    @Test
    fun `blank hindi statute name rejects the row`() {
        assertRejected(validRow.copy(statuteNameHi = ""))
    }

    @Test
    fun `blank section number rejects the row`() {
        assertRejected(validRow.copy(sectionNumber = ""))
    }

    @Test
    fun `zero page number rejects the row`() {
        assertRejected(validRow.copy(pageNumber = 0))
    }

    @Test
    fun `negative page number rejects the row`() {
        assertRejected(validRow.copy(pageNumber = -3))
    }

    @Test
    fun `blank english text rejects the row`() {
        assertRejected(validRow.copy(chunkTextEn = ""))
    }

    @Test
    fun `blank hindi text rejects the row`() {
        assertRejected(validRow.copy(chunkTextHi = ""))
    }

    @Test
    fun `blank source document rejects the row`() {
        assertRejected(validRow.copy(sourceDocument = "   "))
    }

    @Test
    fun `malformed source hash rejects the row`() {
        assertRejected(validRow.copy(sourceSha256 = "not-a-hash"))
    }

    @Test
    fun `malformed compilation date rejects the row`() {
        assertRejected(validRow.copy(compilationDate = "May 2026"))
    }

    private fun assertRejected(row: StatuteChunkEntity) {
        assertThat(MetadataExtractor.extract(row))
            .isInstanceOf(MetadataExtractor.Extraction.Rejected::class.java)
    }
}
