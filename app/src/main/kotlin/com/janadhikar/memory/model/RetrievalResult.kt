package com.janadhikar.memory.model

/**
 * A citation whose every field came verbatim from a typed database row and
 * survived [com.janadhikar.memory.MetadataExtractor]'s strict validation.
 *
 * This is the ONLY type the citation card is allowed to render from, and the
 * ONLY carrier of statute text into the LLM layer (Rules 1 & 2).
 */
data class VerifiedCitation(
    val chunkId: Long,
    val statuteName: String,
    val statuteNameHi: String,
    /** "SECTION" or "ARTICLE" — governs the citation-card label. */
    val unit: String,
    val sectionNumber: String,
    val clause: String?,
    val pageNumber: Int,
    val verbatimTextEn: String,
    val verbatimTextHi: String,
    val sourceDocument: String,
    val sourceUrl: String,
    val compilationDate: String,
)

/** Outcome of one hybrid retrieval. Exactly two shapes — there is no third. */
sealed interface RetrievalResult {

    /** High-confidence, graph-verified, strictly-extracted match. */
    data class Match(
        /** The provision to act on. */
        val primary: VerifiedCitation,
        /** Similarity of the primary chunk, in [0, 1]. */
        val confidence: Float,
        /** Graph-attached related provisions (RELATED_RIGHT), already verified. */
        val related: List<VerifiedCitation>,
        /** True if the graph redirected a superseded section to its successor. */
        val redirectedFromSuperseded: Boolean,
    ) : RetrievalResult

    /**
     * The governed refusal (Rule 3). The UI must render
     * R.string.no_verified_statute and nothing else. No retry-with-lower-bar,
     * no LLM fallback — those paths must not exist.
     */
    data object NoVerifiedStatute : RetrievalResult
}
