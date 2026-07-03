package com.janadhikar.memory.vec

import java.io.Closeable
import java.io.File

/**
 * Kotlin face of the statically-fused SQLite + sqlite-vec native library.
 * Handles ONLY the vector layer (`vec_chunks` KNN); relational and graph
 * queries go through Room ([com.janadhikar.memory.KnowledgeDao]).
 *
 * The native side opens the file with SQLITE_OPEN_READONLY and there is no
 * read-write entry point in the .so at all (Rule 4, enforced in C).
 */
class SqliteVecBridge private constructor(private var dbPtr: Long) : Closeable {

    /** One KNN hit: [distance] is cosine distance, in [0, 2]. */
    data class Neighbor(val chunkId: Long, val distance: Float)

    /**
     * K-nearest-neighbour search over chunk embeddings.
     * Thread-safe for concurrent reads (SQLITE_THREADSAFE=1, read-only conn).
     */
    fun search(queryEmbedding: FloatArray, k: Int): List<Neighbor> {
        check(dbPtr != 0L) { "SqliteVecBridge used after close()" }
        require(queryEmbedding.size == EMBEDDING_DIM) {
            "Query embedding dim ${queryEmbedding.size} != $EMBEDDING_DIM"
        }
        val ids = LongArray(k)
        val distances = FloatArray(k)
        val found = nativeVectorSearch(dbPtr, queryEmbedding, k, ids, distances)
        return (0 until found).map { Neighbor(ids[it], distances[it]) }
    }

    /**
     * FTS5 keyword search over the statute text. [ftsMatchQuery] is an FTS5
     * MATCH expression built from the [com.janadhikar.memory.CrisisLexicon]
     * (controlled legal terms only). Returns chunk ids ranked by relevance
     * (title-weighted), best first.
     */
    fun keywordSearch(ftsMatchQuery: String, k: Int): List<Long> {
        check(dbPtr != 0L) { "SqliteVecBridge used after close()" }
        if (ftsMatchQuery.isBlank()) return emptyList()
        val ids = LongArray(k)
        val found = nativeKeywordSearch(dbPtr, ftsMatchQuery, k, ids)
        if (found <= 0) return emptyList()
        return ids.take(found)
    }

    override fun close() {
        if (dbPtr != 0L) {
            nativeClose(dbPtr)
            dbPtr = 0L
        }
    }

    // ── JNI surface (implemented in cpp/sqlitevec/sqlite_vec_jni.cpp) ───────

    private external fun nativeClose(dbPtr: Long)

    private external fun nativeVectorSearch(
        dbPtr: Long,
        query: FloatArray,
        k: Int,
        outIds: LongArray,
        outDistances: FloatArray,
    ): Int

    private external fun nativeKeywordSearch(
        dbPtr: Long,
        matchQuery: String,
        k: Int,
        outIds: LongArray,
    ): Int

    companion object {
        /** Must equal kb_meta.embedding_dim; verified at startup. */
        const val EMBEDDING_DIM = 384

        init {
            System.loadLibrary("janadhikar_sqlitevec")
        }

        @JvmStatic
        private external fun nativeOpenReadOnly(dbPath: String): Long

        /** Opens the provisioned DB file read-only, or throws. */
        fun open(dbFile: File): SqliteVecBridge {
            val ptr = nativeOpenReadOnly(dbFile.absolutePath)
            check(ptr != 0L) { "Failed to open knowledge base (vector layer): $dbFile" }
            return SqliteVecBridge(ptr)
        }
    }
}
