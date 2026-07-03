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
/** One row of the corpus self-description (statute + unit + provision count). */
data class ProvisionCount(val statuteName: String, val unit: String, val count: Int)

@Dao
interface KnowledgeDao {

    // ── Relational: verbatim chunks ─────────────────────────────────────────

    @Query("SELECT * FROM statute_chunks WHERE id IN (:ids)")
    suspend fun chunksByIds(ids: List<Long>): List<StatuteChunkEntity>

    @Query("SELECT * FROM statute_chunks WHERE node_id = :nodeId LIMIT 1")
    suspend fun chunkByNodeId(nodeId: Long): StatuteChunkEntity?

    /** First chunk of a provision, looked up by statute name + number (concepts). */
    @Query(
        "SELECT * FROM statute_chunks WHERE statute_name LIKE '%' || :statuteContains || '%' " +
            "AND section_number = :number ORDER BY id LIMIT 1",
    )
    suspend fun chunkByStatuteAndSection(statuteContains: String, number: String): StatuteChunkEntity?

    /** All acts' provisions with this exact number + unit (direct "article N"/"section N"). */
    @Query(
        "SELECT * FROM statute_chunks WHERE section_number = :number AND unit = :unit " +
            "GROUP BY statute_name ORDER BY id",
    )
    suspend fun chunksByNumber(number: String, unit: String): List<StatuteChunkEntity>

    /** Per-statute count of distinct provisions — for "how many articles" etc. */
    @Query(
        "SELECT statute_name AS statuteName, unit AS unit, " +
            "COUNT(DISTINCT section_number) AS count FROM statute_chunks " +
            "GROUP BY statute_name, unit ORDER BY statute_name",
    )
    suspend fun provisionCounts(): List<ProvisionCount>

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
