package com.janadhikar.memory

import androidx.room.Dao
import androidx.room.Query
import com.janadhikar.memory.model.GraphEdgeEntity
import com.janadhikar.memory.model.GraphNodeEntity
import com.janadhikar.memory.model.KbMetaEntity
import com.janadhikar.memory.model.StatuteChunkEntity

/**
 * Read-only access to the compiled knowledge base.
 *
 * AUDIT INVARIANT: this interface contains @Query SELECT statements ONLY.
 * Adding @Insert / @Update / @Delete / a writable @Query here violates
 * CONTRIBUTING.md Rule 4 and will fail review. The connection additionally
 * runs under `PRAGMA query_only = 1` (see KnowledgeDatabase).
 */
@Dao
interface KnowledgeDao {

    // ── Relational: verbatim chunks ─────────────────────────────────────────

    @Query("SELECT * FROM statute_chunks WHERE id IN (:ids)")
    suspend fun chunksByIds(ids: List<Long>): List<StatuteChunkEntity>

    @Query("SELECT * FROM statute_chunks WHERE node_id = :nodeId LIMIT 1")
    suspend fun chunkByNodeId(nodeId: Long): StatuteChunkEntity?

    // ── Graph: Cognee-derived nodes and edges ───────────────────────────────

    @Query("SELECT * FROM graph_nodes WHERE id = :id")
    suspend fun nodeById(id: Long): GraphNodeEntity?

    @Query(
        "SELECT * FROM graph_edges WHERE src_node_id = :nodeId AND relation = :relation " +
            "ORDER BY weight DESC",
    )
    suspend fun edgesFrom(nodeId: Long, relation: String): List<GraphEdgeEntity>

    @Query(
        "SELECT * FROM graph_edges WHERE src_node_id = :nodeId AND relation = :relation " +
            "ORDER BY weight DESC LIMIT :limit",
    )
    suspend fun edgesFrom(nodeId: Long, relation: String, limit: Int): List<GraphEdgeEntity>

    // ── Meta: artifact self-description ─────────────────────────────────────

    @Query("SELECT * FROM kb_meta WHERE `key` = :key")
    suspend fun meta(key: String): KbMetaEntity?
}
