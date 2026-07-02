package com.janadhikar.engine

import android.content.Context
import com.janadhikar.llm.GemmaTranslator
import com.janadhikar.memory.HybridRetriever
import com.janadhikar.memory.KnowledgeBaseProvisioner
import com.janadhikar.memory.KnowledgeDatabase
import com.janadhikar.memory.QueryEmbedder
import com.janadhikar.memory.vec.SqliteVecBridge
import com.janadhikar.stt.AudioCapture
import com.janadhikar.stt.WhisperBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * The explicit production object graph — no DI framework, by policy: the
 * wiring of a safety-critical pipeline should read top-to-bottom (see
 * JanadhikarApp). Built once via [create] off the main thread, then warm for
 * the life of the process so Trigger → Active stays under the 400 ms budget.
 */
class EdgeStack private constructor(
    val engine: IncidentEngine,
    private val closeables: List<Closeable>,
    private val scope: CoroutineScope,
) : Closeable {

    override fun close() {
        engine.cancel()
        closeables.forEach(Closeable::close)
    }

    companion object {

        private const val WHISPER_MODEL_ASSET = "models/ggml-small-q5_1.bin"

        suspend fun create(context: Context): EdgeStack = withContext(Dispatchers.IO) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            // ── Memory: relational + graph (Room) and vector (JNI), same file ──
            val dbFile = KnowledgeBaseProvisioner(context).provision()
            val db = KnowledgeDatabase.open(context, dbFile)
            val vec = SqliteVecBridge.open(dbFile)
            KnowledgeDatabase.verifyArtifact(
                dao = db.dao(),
                expectedEmbedderId = QueryEmbedder.MODEL_ID,
                expectedDim = SqliteVecBridge.EMBEDDING_DIM,
            )

            // ── Edge models: provisioned from APK assets, memory-mapped ──
            val embedder = QueryEmbedder(context)
            val whisper = WhisperBridge.open(provisionAsset(context, WHISPER_MODEL_ASSET))
            val gemma = GemmaTranslator(context, provisionAsset(context, GemmaTranslator.MODEL_ASSET))

            val retriever = HybridRetriever(
                dao = db.dao(),
                vectorIndex = { query, k -> vec.search(query, k) },
                embed = embedder::embed,
            )

            val engine = IncidentEngine(
                scope = scope,
                audioSource = { AudioCapture().stream() },
                transcriber = { pcm ->
                    withContext(Dispatchers.Default) {
                        whisper.transcribe(pcm, WhisperBridge.Lang.AUTO)
                    }
                },
                retrieve = { query -> retriever.retrieve(query.text) },
                translate = gemma::translate,
                clock = System::currentTimeMillis,
            )

            EdgeStack(
                engine = engine,
                closeables = listOf(gemma, whisper, embedder, vec, Closeable { db.close() }),
                scope = scope,
            )
        }

        /** Copies a model out of assets once; models are immutable per APK. */
        private fun provisionAsset(context: Context, assetPath: String): File {
            val target = File(context.noBackupFilesDir, assetPath.substringAfterLast('/'))
            val assetSize = context.assets.openFd(assetPath).use { it.length }
            if (target.length() != assetSize) {
                context.assets.open(assetPath).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return target
        }
    }
}
