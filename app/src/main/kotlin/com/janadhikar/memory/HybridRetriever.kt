package com.janadhikar.memory

import com.janadhikar.memory.model.Relations
import com.janadhikar.memory.model.RetrievalResult
import com.janadhikar.memory.model.StatuteChunkEntity
import com.janadhikar.memory.model.VerifiedCitation
import com.janadhikar.memory.vec.SqliteVecBridge

/**
 * The hybrid memory walk (COGNEE.md §5):
 *
 *   1. VECTOR      — KNN over sqlite-vec chunk embeddings
 *   2. CONFIDENCE  — governed gate; below threshold → NoVerifiedStatute. Hard stop.
 *   3. GRAPH       — Cognee-derived edges: supersession redirect + related rights
 *   4. RELATIONAL  — typed rows through the strict MetadataExtractor
 *
 * Interfaces are injected so the walk is unit-testable without a device.
 */
class HybridRetriever(
    private val dao: KnowledgeDao,
    private val vectorIndex: VectorSearch,
    private val embed: (String) -> FloatArray,
) {

    /** Seam over [SqliteVecBridge] for tests. */
    fun interface VectorSearch {
        fun search(queryEmbedding: FloatArray, k: Int): List<SqliteVecBridge.Neighbor>
    }

    suspend fun retrieve(normalizedQuery: String): RetrievalResult {
        // ── 1. VECTOR ────────────────────────────────────────────────────────
        val neighbors = vectorIndex.search(embed(normalizedQuery), K_NEIGHBORS)
        if (neighbors.isEmpty()) return RetrievalResult.NoVerifiedStatute

        // ── 2. CONFIDENCE GATE (Rule 3) ─────────────────────────────────────
        // cosine similarity = 1 - cosine distance. The threshold is a GOVERNED
        // constant: changing it requires an eval run (CONTRIBUTING.md Rule 6).
        val best = neighbors.first()
        val confidence = 1f - best.distance
        if (confidence < CONFIDENCE_THRESHOLD) return RetrievalResult.NoVerifiedStatute

        val candidateIds = neighbors
            .filter { 1f - it.distance >= CONFIDENCE_THRESHOLD }
            .map { it.chunkId }
        val chunksById = dao.chunksByIds(candidateIds).associateBy { it.id }
        // Preserve similarity order — the DB returns rows unordered.
        val orderedChunks = candidateIds.mapNotNull { chunksById[it] }
        if (orderedChunks.isEmpty()) return RetrievalResult.NoVerifiedStatute

        // ── 3. GRAPH: supersession redirect ─────────────────────────────────
        var redirected = false
        var primary: VerifiedCitation? = null
        for (chunk in orderedChunks) {
            val (current, wasRedirected) = followSupersession(chunk) ?: continue
            val extraction = MetadataExtractor.extract(current)
            if (extraction is MetadataExtractor.Extraction.Valid) {
                primary = extraction.citation
                redirected = wasRedirected
                break
            }
            // Rejected row → try the next candidate; never repair (Rule 2).
        }
        // ── 4. RELATIONAL: strict extraction already applied above ──────────
        if (primary == null) return RetrievalResult.NoVerifiedStatute

        return RetrievalResult.Match(
            primary = primary,
            confidence = confidence,
            related = relatedRights(primary),
            redirectedFromSuperseded = redirected,
        )
    }

    /**
     * Walks SUPERSEDED_BY / AMENDED_BY edges to the CURRENT law. Returns null
     * if the chunk is superseded but its successor is not in the corpus —
     * showing a repealed section as live law is a factual error, so we drop it.
     */
    private suspend fun followSupersession(
        chunk: StatuteChunkEntity,
    ): Pair<StatuteChunkEntity, Boolean>? {
        var current = chunk
        var hops = 0
        var redirected = false
        while (hops < MAX_GRAPH_HOPS) {
            val successorEdge =
                dao.edgesFrom(current.nodeId, Relations.SUPERSEDED_BY).firstOrNull()
                    ?: dao.edgesFrom(current.nodeId, Relations.AMENDED_BY).firstOrNull()
                    ?: return current to redirected
            val successorChunk = dao.chunkByNodeId(successorEdge.dstNodeId) ?: return null
            current = successorChunk
            redirected = true
            hops++
        }
        return null // supersession cycle or chain too deep: fail closed
    }

    /** RELATED_RIGHT neighbours, weight-ranked, capped, strictly extracted. */
    private suspend fun relatedRights(primary: VerifiedCitation): List<VerifiedCitation> {
        val primaryChunk = dao.chunksByIds(listOf(primary.chunkId)).firstOrNull() ?: return emptyList()
        return dao.edgesFrom(primaryChunk.nodeId, Relations.RELATED_RIGHT, MAX_RELATED)
            .mapNotNull { edge -> dao.chunkByNodeId(edge.dstNodeId) }
            .mapNotNull { chunk ->
                (MetadataExtractor.extract(chunk) as? MetadataExtractor.Extraction.Valid)?.citation
            }
    }

    companion object {
        /**
         * GOVERNED CONSTANT (Rules 3 & 6). Minimum cosine similarity for a
         * match to exist at all. Do not tune without an eval run; a false
         * positive here is a safety incident.
         */
        const val CONFIDENCE_THRESHOLD = 0.72f

        const val K_NEIGHBORS = 8
        private const val MAX_RELATED = 3
        private const val MAX_GRAPH_HOPS = 4
    }
}
