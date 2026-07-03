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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    /** Live status of each on-device component, for the status row. */
    val status: StateFlow<Status>,
    private val closeables: List<Closeable>,
    private val lazyCloseables: List<Deferred<Closeable?>>,
    private val scope: CoroutineScope,
) : Closeable {

    enum class ModelStatus { LOADING, READY, UNAVAILABLE }

    /**
     * What the user can see is loaded. Knowledge base + search embedder are
     * always READY here (create() only returns once they are). Voice and the
     * AI translator load in the background.
     */
    data class Status(
        val knowledgeBase: ModelStatus = ModelStatus.READY,
        val searchEngine: ModelStatus = ModelStatus.READY,
        val voice: ModelStatus = ModelStatus.LOADING,
        val translator: ModelStatus = ModelStatus.LOADING,
    )

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

            // Reflect background-load outcomes into the visible status row.
            val status = MutableStateFlow(Status())
            scope.launch {
                val ok = whisperDeferred.await() != null
                status.value = status.value.copy(
                    voice = if (ok) ModelStatus.READY else ModelStatus.UNAVAILABLE,
                )
            }
            scope.launch {
                val ok = gemmaDeferred.await() != null
                status.value = status.value.copy(
                    translator = if (ok) ModelStatus.READY else ModelStatus.UNAVAILABLE,
                )
            }

            val retriever = HybridRetriever(
                dao = db.dao(),
                vectorIndex = { query, k -> vec.search(query, k) },
                embed = embedder::embed,
                keywordIndex = { ftsQuery, k -> vec.keywordSearch(ftsQuery, k) },
            )

            // whisper_context is NOT thread-safe. The engine's streaming decode
            // and its final decode can overlap, so every transcribe goes through
            // this mutex — concurrent native decode is the classic whisper.cpp
            // SIGSEGV.
            val whisperMutex = Mutex()
            val engine = IncidentEngine(
                scope = scope,
                audioSource = { AudioCapture().stream() },
                transcriber = { pcm ->
                    // First voice use awaits the background whisper load.
                    when (val whisper = whisperDeferred.await()) {
                        null -> ""
                        else -> whisperMutex.withLock {
                            withContext(Dispatchers.Default) {
                                whisper.transcribe(pcm, WhisperBridge.Lang.AUTO)
                            }
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
                status = status.asStateFlow(),
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
