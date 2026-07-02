package com.janadhikar.memory

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import com.janadhikar.memory.model.GraphEdgeEntity
import com.janadhikar.memory.model.GraphNodeEntity
import com.janadhikar.memory.model.KbMetaEntity
import com.janadhikar.memory.model.MetaKeys
import com.janadhikar.memory.model.StatuteChunkEntity
import java.io.File

/**
 * Room wrapper over `janadhikar_knowledge.db` for the RELATIONAL and GRAPH
 * layers. The VECTOR layer (`vec_chunks` vec0 virtual table) is invisible to
 * Room — it is queried through the statically-linked native build via
 * [com.janadhikar.memory.vec.SqliteVecBridge], against the same file.
 */
@Database(
    entities = [
        StatuteChunkEntity::class,
        GraphNodeEntity::class,
        GraphEdgeEntity::class,
        KbMetaEntity::class,
    ],
    version = KnowledgeDatabase.SCHEMA_VERSION,
    exportSchema = true,
)
abstract class KnowledgeDatabase : RoomDatabase() {

    abstract fun dao(): KnowledgeDao

    companion object {
        /** Must match kb_meta.schema_version in the compiled artifact. */
        const val SCHEMA_VERSION = 1

        /**
         * Opens the provisioned DB file read-only. [dbFile] comes from
         * [KnowledgeBaseProvisioner] — never construct the path by hand.
         */
        fun open(context: Context, dbFile: File): KnowledgeDatabase =
            Room.databaseBuilder(context, KnowledgeDatabase::class.java, dbFile.absolutePath)
                .addCallback(object : Callback() {
                    override fun onOpen(db: SupportSQLiteDatabase) {
                        // Belt-and-braces with the file-level read-only bit and
                        // the SELECT-only DAO: the connection itself refuses writes.
                        db.query("PRAGMA query_only = 1").close()
                    }
                })
                .build()

        /**
         * Startup handshake: the app refuses an artifact whose schema version
         * or embedder identity it does not recognise. Fail CLOSED — a mismatch
         * means retrieval quality is undefined, which is a safety issue.
         */
        suspend fun verifyArtifact(dao: KnowledgeDao, expectedEmbedderId: String, expectedDim: Int) {
            val schema = dao.meta(MetaKeys.SCHEMA_VERSION)?.value
            check(schema == SCHEMA_VERSION.toString()) {
                "Knowledge base schema '$schema' != expected '$SCHEMA_VERSION'. Refusing to start."
            }
            val embedder = dao.meta(MetaKeys.EMBEDDER_MODEL_ID)?.value
            check(embedder == expectedEmbedderId) {
                "Artifact embedder '$embedder' != on-device embedder '$expectedEmbedderId'. " +
                    "Query and corpus vectors would live in different spaces. Refusing to start."
            }
            val dim = dao.meta(MetaKeys.EMBEDDING_DIM)?.value?.toIntOrNull()
            check(dim == expectedDim) {
                "Artifact embedding dim '$dim' != expected '$expectedDim'. Refusing to start."
            }
        }
    }
}
