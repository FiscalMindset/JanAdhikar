package com.janadhikar.memory

import com.janadhikar.memory.model.StatuteChunkEntity
import com.janadhikar.memory.model.VerifiedCitation

/**
 * THE strict metadata extraction function (Rule 2).
 *
 * Maps a raw database row to a [VerifiedCitation] — typed field to typed
 * field, verbatim, no derivation. If ANY required field is missing or
 * malformed the row is REJECTED whole. There is no repair path: a citation
 * we cannot fully verify is a citation we do not show.
 *
 * Pure function, no I/O — unit-tested exhaustively (see MetadataExtractorTest).
 */
object MetadataExtractor {

    sealed interface Extraction {
        data class Valid(val citation: VerifiedCitation) : Extraction

        /** [reason] is for logs/tests only — NEVER user-visible, NEVER LLM-visible. */
        data class Rejected(val chunkId: Long, val reason: String) : Extraction
    }

    private val ISO_DATE = Regex("""^\d{4}-\d{2}-\d{2}$""")
    private val SHA256_HEX = Regex("""^[0-9a-f]{64}$""")

    fun extract(row: StatuteChunkEntity): Extraction {
        val reason = firstViolation(row)
        return if (reason == null) {
            Extraction.Valid(
                VerifiedCitation(
                    chunkId = row.id,
                    statuteName = row.statuteName.trim(),
                    statuteNameHi = row.statuteNameHi.trim(),
                    sectionNumber = row.sectionNumber.trim(),
                    clause = row.clause?.trim()?.takeIf { it.isNotEmpty() },
                    pageNumber = row.pageNumber,
                    verbatimTextEn = row.chunkTextEn,
                    verbatimTextHi = row.chunkTextHi,
                    sourceDocument = row.sourceDocument.trim(),
                    compilationDate = row.compilationDate.trim(),
                ),
            )
        } else {
            Extraction.Rejected(row.id, reason)
        }
    }

    private fun firstViolation(row: StatuteChunkEntity): String? = when {
        row.statuteName.isBlank() -> "blank statute_name"
        row.statuteNameHi.isBlank() -> "blank statute_name_hi"
        row.sectionNumber.isBlank() -> "blank section_number"
        row.pageNumber <= 0 -> "non-positive page_number (${row.pageNumber})"
        row.chunkTextEn.isBlank() -> "blank chunk_text_en"
        row.chunkTextHi.isBlank() -> "blank chunk_text_hi"
        row.sourceDocument.isBlank() -> "blank source_document"
        !SHA256_HEX.matches(row.sourceSha256.trim()) -> "malformed source_sha256"
        !ISO_DATE.matches(row.compilationDate.trim()) -> "malformed compilation_date"
        else -> null
    }
}
