package com.janadhikar.memory

import com.google.common.truth.Truth.assertThat
import com.janadhikar.memory.model.GraphEdgeEntity
import com.janadhikar.memory.model.GraphNodeEntity
import com.janadhikar.memory.model.KbMetaEntity
import com.janadhikar.memory.model.Relations
import com.janadhikar.memory.model.RetrievalResult
import com.janadhikar.memory.model.StatuteChunkEntity
import com.janadhikar.memory.vec.SqliteVecBridge
import kotlinx.coroutines.test.runTest
import org.junit.Test

class HybridRetrieverTest {

    private val zeroEmbedding: (String) -> FloatArray = { FloatArray(SqliteVecBridge.EMBEDDING_DIM) }

    private fun chunk(id: Long, nodeId: Long, section: String = "47") = StatuteChunkEntity(
        id = id,
        nodeId = nodeId,
        statuteName = "Bharatiya Nagarik Suraksha Sanhita, 2023",
        statuteNameHi = "भारतीय नागरिक सुरक्षा संहिता, 2023",
        sectionNumber = section,
        clause = null,
        pageNumber = 21,
        chunkTextEn = "Grounds of arrest shall be communicated.",
        chunkTextHi = "गिरफ्तारी के आधार बताए जाएँगे।",
        sourceDocument = "BNSS_2023_Gazette.pdf",
        sourceSha256 = "b".repeat(64),
        compilationDate = "2026-05-01",
    )

    // ── Rule 3: the refusal path ─────────────────────────────────────────────

    @Test
    fun `no neighbours returns NoVerifiedStatute`() = runTest {
        val retriever = HybridRetriever(FakeDao(), { _, _ -> emptyList() }, zeroEmbedding)
        assertThat(retriever.retrieve("koi bhi sawaal"))
            .isEqualTo(RetrievalResult.NoVerifiedStatute)
    }

    @Test
    fun `below-threshold match returns NoVerifiedStatute, never a weak answer`() = runTest {
        // distance 0.8 → similarity 0.2 < CONFIDENCE_THRESHOLD (0.34)
        val vec = HybridRetriever.VectorSearch { _, _ ->
            listOf(SqliteVecBridge.Neighbor(chunkId = 1L, distance = 0.8f))
        }
        val retriever = HybridRetriever(FakeDao(chunks = listOf(chunk(1L, 10L))), vec, zeroEmbedding)
        assertThat(retriever.retrieve("unrelated query"))
            .isEqualTo(RetrievalResult.NoVerifiedStatute)
    }

    @Test
    fun `high-confidence match whose row is malformed still refuses`() = runTest {
        val malformed = chunk(1L, 10L).copy(pageNumber = 0) // fails strict extraction
        val vec = HybridRetriever.VectorSearch { _, _ ->
            listOf(SqliteVecBridge.Neighbor(chunkId = 1L, distance = 0.1f))
        }
        val retriever = HybridRetriever(FakeDao(chunks = listOf(malformed)), vec, zeroEmbedding)
        assertThat(retriever.retrieve("grounds of arrest"))
            .isEqualTo(RetrievalResult.NoVerifiedStatute)
    }

    // ── The happy path ───────────────────────────────────────────────────────

    @Test
    fun `high-confidence match returns verbatim citation`() = runTest {
        val vec = HybridRetriever.VectorSearch { _, _ ->
            listOf(SqliteVecBridge.Neighbor(chunkId = 1L, distance = 0.1f))
        }
        val retriever = HybridRetriever(FakeDao(chunks = listOf(chunk(1L, 10L))), vec, zeroEmbedding)

        val result = retriever.retrieve("police is not telling me why I am arrested")

        val match = result as RetrievalResult.Match
        assertThat(match.primary.sectionNumber).isEqualTo("47")
        assertThat(match.primary.pageNumber).isEqualTo(21)
        assertThat(match.confidence).isWithin(1e-6f).of(0.9f)
        assertThat(match.redirectedFromSuperseded).isFalse()
    }

    // ── Graph layer: supersession redirect ──────────────────────────────────

    @Test
    fun `superseded section redirects to successor chunk`() = runTest {
        // Old IPC-style chunk (node 10) superseded by BNSS chunk (node 20)
        val old = chunk(1L, 10L, section = "50-IPC")
        val current = chunk(2L, 20L, section = "47")
        val dao = FakeDao(
            chunks = listOf(old, current),
            edges = listOf(GraphEdgeEntity(1L, srcNodeId = 10L, dstNodeId = 20L, relation = Relations.SUPERSEDED_BY, weight = 1.0)),
        )
        val vec = HybridRetriever.VectorSearch { _, _ ->
            listOf(SqliteVecBridge.Neighbor(chunkId = 1L, distance = 0.1f)) // old law matches best
        }

        val result = HybridRetriever(dao, vec, zeroEmbedding).retrieve("arrest grounds")

        val match = result as RetrievalResult.Match
        assertThat(match.primary.sectionNumber).isEqualTo("47") // the CURRENT law
        assertThat(match.redirectedFromSuperseded).isTrue()
    }

    @Test
    fun `superseded section with missing successor refuses rather than citing repealed law`() = runTest {
        val old = chunk(1L, 10L)
        val dao = FakeDao(
            chunks = listOf(old),
            // successor node 99 has no chunk in the corpus
            edges = listOf(GraphEdgeEntity(1L, srcNodeId = 10L, dstNodeId = 99L, relation = Relations.SUPERSEDED_BY, weight = 1.0)),
        )
        val vec = HybridRetriever.VectorSearch { _, _ ->
            listOf(SqliteVecBridge.Neighbor(chunkId = 1L, distance = 0.1f))
        }

        assertThat(HybridRetriever(dao, vec, zeroEmbedding).retrieve("arrest"))
            .isEqualTo(RetrievalResult.NoVerifiedStatute)
    }

    // ── Test double ──────────────────────────────────────────────────────────

    private class FakeDao(
        private val chunks: List<StatuteChunkEntity> = emptyList(),
        private val edges: List<GraphEdgeEntity> = emptyList(),
    ) : KnowledgeDao {
        override suspend fun chunksByIds(ids: List<Long>) = chunks.filter { it.id in ids }
        override suspend fun chunkByNodeId(nodeId: Long) = chunks.firstOrNull { it.nodeId == nodeId }
        override suspend fun nodeById(id: Long): GraphNodeEntity? = null
        override suspend fun edgesFrom(nodeId: Long, relation: String) =
            edges.filter { it.srcNodeId == nodeId && it.relation == relation }.sortedByDescending { it.weight }
        override suspend fun edgesFrom(nodeId: Long, relation: String, limit: Int) =
            edgesFrom(nodeId, relation).take(limit)
        override suspend fun meta(key: String): KbMetaEntity? = null
    }
}
