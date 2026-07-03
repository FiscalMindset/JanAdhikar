package com.janadhikar.engine

import android.content.Context
import com.janadhikar.llm.Directive
import com.janadhikar.llm.GemmaTranslator
import com.janadhikar.llm.VerbatimStatuteText
import com.janadhikar.memory.HybridRetriever
import com.janadhikar.memory.KnowledgeBaseProvisioner
import com.janadhikar.memory.KnowledgeDatabase
import com.janadhikar.memory.QueryEmbedder
import com.janadhikar.memory.vec.SqliteVecBridge
import com.janadhikar.stt.AudioCapture
import com.janadhikar.stt.WhisperBridge
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File

/**
 * The explicit production object graph — no DI framework, by policy: the
 * wiring of a safety-critical pipeline should read top-to-bottom (see
 * JanadhikarApp).
 *
 * [create] returns as soon as the TEXT path is ready (DB + query embedder), so
 * the app becomes usable fast. The heavy voice model (whisper, ~180 MB, needed
 * only for speech) and the LLM load in the BACKGROUND and are awaited lazily on
 * first use — a typed query never waits for them.
 */
class EdgeStack private constructor(
    val engine: IncidentEngine,
    private val closeables: List<Closeable>,
    private val lazyCloseables: List<Deferred<Closeable?>>,
    private val scope: CoroutineScope,
) : Closeable {

    override fun close() {
        engine.cancel()
        closeables.forEach(Closeable::close)
        lazyCloseables.forEach { deferred ->
            if (deferred.isCompleted) runCatching { deferred.getCompleted() }.getOrNull()?.close()
        }
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

            // ── Query embedder: ON the text critical path, loaded eagerly ──
            val tokenizer = context.assets.open(QueryEmbedder.VOCAB_ASSET).bufferedReader().useLines {
                com.janadhikar.memory.SentencePieceTokenizer(
                    com.janadhikar.memory.SentencePieceTokenizer.loadVocab(it),
                )
            }
            val embedder = QueryEmbedder(
                modelFile = provisionAsset(context, QueryEmbedder.MODEL_ASSET),
                tokenizer = tokenizer,
            )

            // ── Voice model + LLM: OFF the critical path. Start loading now in
            //    the background; the engine awaits them only when actually used.
            val whisperDeferred: Deferred<WhisperBridge?> = scope.async {
                runCatching {
                    WhisperBridge.open(provisionAsset(context, WHISPER_MODEL_ASSET))
                }.getOrNull()
            }
            // Gemma is license-gated (see scripts/fetch_models.sh). Without it,
            // directives fall back to verbatim statute text — always safe.
            val gemmaDeferred: Deferred<GemmaTranslator?> = scope.async {
                runCatching {
                    GemmaTranslator(context, provisionAsset(context, GemmaTranslator.MODEL_ASSET))
                }.getOrNull()
            }

            val retriever = HybridRetriever(
                dao = db.dao(),
                vectorIndex = { query, k -> vec.search(query, k) },
                embed = embedder::embed,
            )

            val engine = IncidentEngine(
                scope = scope,
                audioSource = { AudioCapture().stream() },
                transcriber = { pcm ->
                    // First voice use awaits the background whisper load.
                    when (val whisper = whisperDeferred.await()) {
                        null -> ""
                        else -> withContext(Dispatchers.Default) {
                            whisper.transcribe(pcm, WhisperBridge.Lang.AUTO)
                        }
                    }
                },
                retrieve = { query -> retriever.retrieve(query.text) },
                translate = { citation, language ->
                    gemmaDeferred.await()?.translate(citation, language) ?: Directive(
                        text = VerbatimStatuteText.from(citation, language).value,
                        language = language,
                        isVerbatimFallback = true,
                    )
                },
                clock = System::currentTimeMillis,
            )

            EdgeStack(
                engine = engine,
                closeables = listOf(embedder, vec, Closeable { db.close() }),
                lazyCloseables = listOf(whisperDeferred, gemmaDeferred),
                scope = scope,
            )
        }

        /** Copies a model out of assets once; models are immutable per APK. */
        private fun provisionAsset(context: Context, assetPath: String): File {
            val dir = context.noBackupFilesDir.apply { mkdirs() }
            val target = File(dir, assetPath.substringAfterLast('/'))
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
