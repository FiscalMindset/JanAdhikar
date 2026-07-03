package com.janadhikar.memory.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Verbatim statute chunk — mirrors the `statute_chunks` table compiled by the
 * offline Cognee pipeline (see COGNEE.md §4).
 *
 * READ-ONLY: there is deliberately no @Insert/@Update/@Delete anywhere in the
 * memory layer. The knowledge base is immutable at runtime (Rule 4).
 */
@Entity(tableName = "statute_chunks")
data class StatuteChunkEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "node_id") val nodeId: Long,
    @ColumnInfo(name = "statute_name") val statuteName: String,
    @ColumnInfo(name = "statute_name_hi") val statuteNameHi: String,
    /** Provision kind: "SECTION" (acts) or "ARTICLE" (Constitution). */
    @ColumnInfo(name = "unit", defaultValue = "SECTION") val unit: String,
    @ColumnInfo(name = "section_number") val sectionNumber: String,
    @ColumnInfo(name = "clause") val clause: String?,
    @ColumnInfo(name = "page_number") val pageNumber: Int,
    @ColumnInfo(name = "chunk_text_en") val chunkTextEn: String,
    @ColumnInfo(name = "chunk_text_hi") val chunkTextHi: String,
    @ColumnInfo(name = "source_document") val sourceDocument: String,
    @ColumnInfo(name = "source_sha256") val sourceSha256: String,
    @ColumnInfo(name = "compilation_date") val compilationDate: String,
    /** Official URL of the source document, so the citizen can verify it. */
    @ColumnInfo(name = "source_url", defaultValue = "") val sourceUrl: String = "",
)

/** Cognee entity node, flattened (STATUTE | SECTION | CLAUSE | RIGHT | AUTHORITY). */
@Entity(tableName = "graph_nodes")
data class GraphNodeEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "kind") val kind: String,
    @ColumnInfo(name = "label") val label: String,
    @ColumnInfo(name = "statute_name") val statuteName: String?,
    @ColumnInfo(name = "section_number") val sectionNumber: String?,
)

/** Cognee relation edge, flattened. */
@Entity(tableName = "graph_edges")
data class GraphEdgeEntity(
    @PrimaryKey val id: Long,
    @ColumnInfo(name = "src_node_id") val srcNodeId: Long,
    @ColumnInfo(name = "dst_node_id") val dstNodeId: Long,
    @ColumnInfo(name = "relation") val relation: String,
    @ColumnInfo(name = "weight") val weight: Double,
)

/** Artifact self-description. The app refuses artifacts it does not recognise. */
@Entity(tableName = "kb_meta")
data class KbMetaEntity(
    @PrimaryKey val key: String,
    @ColumnInfo(name = "value") val value: String,
)

object Relations {
    const val SUPERSEDED_BY = "SUPERSEDED_BY"
    const val AMENDED_BY = "AMENDED_BY"
    const val RELATED_RIGHT = "RELATED_RIGHT"
    const val DEFINED_IN = "DEFINED_IN"
    const val PART_OF = "PART_OF"
}

object MetaKeys {
    const val SCHEMA_VERSION = "schema_version"
    const val EMBEDDER_MODEL_ID = "embedder_model_id"
    const val EMBEDDING_DIM = "embedding_dim"
    const val COMPILED_AT = "compiled_at"
}
