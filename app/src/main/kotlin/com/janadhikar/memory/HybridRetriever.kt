package com.janadhikar.memory

import com.janadhikar.memory.model.Relations
import com.janadhikar.memory.model.RetrievalResult
import com.janadhikar.memory.model.StatuteChunkEntity
import com.janadhikar.memory.model.VerifiedCitation
import com.janadhikar.memory.vec.SqliteVecBridge

/**
 * The hybrid memory walk — KEYWORD + SEMANTIC, then graph + relational:
 *
 *   1. KEYWORD  — CrisisLexicon maps colloquial crisis words ("killed",
 *                 "slapped") to legal terms and FTS5 finds the sections.
 *                 This is what makes it more than a semantic guess.
 *   2. VECTOR   — KNN over sqlite-vec, for queries already in legal-ish phrasing.
 *   3. MERGE    — keyword hits lead (intent-matched); vector hits above the
 *                 governed threshold follow. Refuse only if BOTH are empty.
 *   4. GRAPH    — supersession redirect + related provisions.
 *   5. RELATIONAL — typed rows through the strict MetadataExtractor.
 *
 * Interfaces are injected so the walk is unit-testable without a device.
 */
class HybridRetriever(
    private val dao: KnowledgeDao,
    private val vectorIndex: VectorSearch,
    private val embed: (String) -> FloatArray,
    private val keywordIndex: KeywordSearch = KeywordSearch { _, _ -> emptyList() },
) {

    /** Seam over [SqliteVecBridge] for tests. */
    fun interface VectorSearch {
        fun search(queryEmbedding: FloatArray, k: Int): List<SqliteVecBridge.Neighbor>
    }

    /** FTS5 keyword seam over [SqliteVecBridge] for tests. */
    fun interface KeywordSearch {
        fun search(ftsQuery: String, k: Int): List<Long>
    }

    suspend fun retrieve(normalizedQuery: String): RetrievalResult {
        // ── 0a. DIRECT REFERENCE — "article 15", "section 302", "BNS 63" name an
        //    exact provision; look it up instead of guessing by similarity. ─────
        DirectReference.parse(normalizedQuery)?.let { ref ->
            val all = dao.chunksByNumber(ref.number, ref.unit)
                .mapNotNull { (MetadataExtractor.extract(it) as? MetadataExtractor.Extraction.Valid)?.citation }
            if (all.isNotEmpty()) {
                val preferred = ref.statuteHint
                    ?: if (ref.unit == "ARTICLE") "Constitution" else "Nyaya"
                val primary = all.firstOrNull { it.statuteName.contains(preferred, ignoreCase = true) }
                    ?: all.first()
                return RetrievalResult.Match(
                    primary = primary,
                    confidence = DIRECT_CONFIDENCE,
                    related = all.filter { it != primary },
                    redirectedFromSuperseded = false,
                )
            }
        }

        // ── 0b. CONCEPT — broad questions ("fundamental rights") map directly
        //    to the authoritative provisions; similarity search can't. ─────────
        val concept = ConceptLexicon.resolve(normalizedQuery)
        if (concept != null) {
            val citations = concept.refs.mapNotNull { ref ->
                dao.chunkByStatuteAndSection(ref.statuteContains, ref.number)
                    ?.let { (MetadataExtractor.extract(it) as? MetadataExtractor.Extraction.Valid)?.citation }
            }
            if (citations.isNotEmpty()) {
                return RetrievalResult.Match(
                    primary = citations.first(),
                    confidence = CONCEPT_CONFIDENCE,
                    related = citations.drop(1),
                    redirectedFromSuperseded = false,
                    curatedAnswer = concept.overview,
                )
            }
        }

        // ── 1. KEYWORD (crisis-word → legal-term) ────────────────────────────
        val ftsQuery = CrisisLexicon.toFtsQuery(normalizedQuery)
        val keywordIds = if (ftsQuery.isNotBlank()) keywordIndex.search(ftsQuery, K_NEIGHBORS) else emptyList()

        // ── 2. VECTOR ────────────────────────────────────────────────────────
        val neighbors = vectorIndex.search(embed(normalizedQuery), K_NEIGHBORS)
        val bestSim = neighbors.firstOrNull()?.let { 1f - it.distance } ?: 0f
        val vectorIds = neighbors.filter { 1f - it.distance >= CONFIDENCE_THRESHOLD }.map { it.chunkId }

        // ── 3. MERGE — CONSENSUS first ──────────────────────────────────────
        // A section that BOTH the keyword search and the semantic search
        // surface is the strongest signal (e.g. "punishment for theft" →
        // keyword 'theft' AND vector 'punishment for theft' both hit §303
        // "Punishment for theft", so it beats §307 which only the keyword
        // matched). Then remaining keyword hits, then remaining vector hits.
        val consensus = keywordIds.filter { it in vectorIds }
        val orderedIds = LinkedHashSet<Long>().apply {
            addAll(consensus)
            addAll(keywordIds)
            addAll(vectorIds)
        }.toList()
        // Rule 3: nothing matched by keyword OR confident vector → refuse.
        if (orderedIds.isEmpty()) return RetrievalResult.NoVerifiedStatute

        val chunksById = dao.chunksByIds(orderedIds).associateBy { it.id }
        val orderedChunks = orderedIds.mapNotNull { chunksById[it] }
        if (orderedChunks.isEmpty()) return RetrievalResult.NoVerifiedStatute

        // A keyword hit means the user's crisis word mapped to a legal term that
        // actually appears in a section — high confidence even if the raw
        // embedding similarity was low.
        val confidence = if (keywordIds.isNotEmpty()) maxOf(bestSim, KEYWORD_CONFIDENCE) else bestSim

        // ── 4. GRAPH: supersession redirect ─────────────────────────────────
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
        // ── 5. RELATIONAL: strict extraction already applied above ──────────
        if (primary == null) return RetrievalResult.NoVerifiedStatute

        return RetrievalResult.Match(
            primary = primary,
            confidence = confidence,
            related = relatedProvisions(primary, orderedChunks),
            redirectedFromSuperseded = redirected,
        )
    }

    /**
     * Related provisions shown under the directive, from two sources, deduped:
     *   1. GRAPH — RELATED_RIGHT edges (in-text cross-references) of the primary.
     *   2. VECTOR — the other above-threshold KNN hits.
     *
     * (2) is why a query whose ideal section ranks #2 still surfaces it: e.g.
     * "why am I being arrested" ranks §58 (24-hour limit) first, but §47
     * (grounds of arrest) is right behind and appears here. Every entry is
     * still strictly extracted — no unverified row is ever shown.
     */
    private suspend fun relatedProvisions(
        primary: VerifiedCitation,
        vectorChunks: List<StatuteChunkEntity>,
    ): List<VerifiedCitation> {
        val ordered = LinkedHashMap<Long, VerifiedCitation>()
        for (citation in graphRelated(primary) + vectorRelated(vectorChunks)) {
            if (citation.chunkId != primary.chunkId) ordered.putIfAbsent(citation.chunkId, citation)
        }
        return ordered.values.take(MAX_RELATED)
    }

    /** RELATED_RIGHT neighbours of the primary, weight-ranked, strictly extracted. */
    private suspend fun graphRelated(primary: VerifiedCitation): List<VerifiedCitation> {
        val primaryChunk = dao.chunksByIds(listOf(primary.chunkId)).firstOrNull() ?: return emptyList()
        return dao.edgesFrom(primaryChunk.nodeId, Relations.RELATED_RIGHT, MAX_RELATED)
            .mapNotNull { edge -> dao.chunkByNodeId(edge.dstNodeId) }
            .mapNotNull(::extractOrNull)
    }

    /**
     * The other above-threshold KNN hits, in similarity order. Each is put
     * through the SAME supersession redirect as the primary, so a repealed
     * section never appears as a related provision either (Rule 4). A hit that
     * redirects away or fails strict extraction is dropped, not repaired.
     */
    private suspend fun vectorRelated(vectorChunks: List<StatuteChunkEntity>): List<VerifiedCitation> =
        vectorChunks.mapNotNull { chunk ->
            val (current, _) = followSupersession(chunk) ?: return@mapNotNull null
            extractOrNull(current)
        }

    private fun extractOrNull(chunk: StatuteChunkEntity): VerifiedCitation? =
        (MetadataExtractor.extract(chunk) as? MetadataExtractor.Extraction.Valid)?.citation

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

    companion object {
        /**
         * GOVERNED CONSTANT (Rules 3 & 6). Minimum cosine similarity for a
         * match to exist at all. Do not tune without an eval run; a false
         * positive here is a safety incident.
         *
         * Current value from knowledge-pipeline/eval_queries.py on the
         * 2026-07-03 5-statute artifact (paraphrase-multilingual-MiniLM-L12-v2,
         * Constitution + BNS + BNSS + BSA + Motor Vehicles):
         *   best junk-query sim   0.367  ← must stay below (a false match here
         *                                   is a safety incident, Rule 3)
         *   worst genuine-hit sim 0.388  ← must stay above
         *   Precision@1 11/19, Hit@3 16/19
         * 0.38 sits in that window: it rejects every junk query and keeps every
         * genuine hit. The margin is deliberately biased toward refusal.
         */
        const val CONFIDENCE_THRESHOLD = 0.38f

        /** Confidence assigned when a keyword (crisis-lexicon) hit is present. */
        const val KEYWORD_CONFIDENCE = 0.75f

        /** Concept-lexicon matches are authoritative, curated provisions. */
        const val CONCEPT_CONFIDENCE = 0.95f

        /** A direct "article N"/"section N" lookup is an exact hit. */
        const val DIRECT_CONFIDENCE = 1.0f

        const val K_NEIGHBORS = 8
        private const val MAX_RELATED = 4
        private const val MAX_GRAPH_HOPS = 4
    }
}
