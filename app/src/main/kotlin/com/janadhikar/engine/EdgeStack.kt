package com.janadhikar.engine

import android.content.Context
import com.janadhikar.llm.Directive
import com.janadhikar.llm.GemmaTranslator
import com.janadhikar.llm.LlamaBridge
import com.janadhikar.llm.LlamaTranslator
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
    val engine: ChatEngine,
    /** Live status of each on-device component, for the status row. */
    val status: StateFlow<Status>,
    private val closeables: List<Closeable>,
    private val lazyCloseables: List<Deferred<Closeable?>>,
    private val scope: CoroutineScope,
) : Closeable {

    enum class ModelStatus { LOADING, READY, UNAVAILABLE }

    /** One on-device model's identity + live state, for the Settings screen. */
    data class ModelInfo(
        val label: String,
        val type: String,
        val fileName: String,
        val approxSizeMb: Int,
        val status: ModelStatus,
    )

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
    ) {
        /** Detailed per-model list for the Settings screen. */
        fun models(): List<ModelInfo> = listOf(
            ModelInfo("Knowledge base", "SQLite + sqlite-vec + FTS5", "janadhikar_knowledge.db", 13, knowledgeBase),
            ModelInfo("Search model", "MiniLM-L12-v2 embedder (384-d, LiteRT)", "embedder_minilm_384.tflite", 113, searchEngine),
            ModelInfo("AI translator", "Gemma 3 1B (4-bit, LiteRT)", "gemma3-1b-it-int4.task", 529, translator),
            ModelInfo("Voice (STT)", "whisper small (multilingual, q5)", "ggml-small-q5_1.bin", 181, voice),
        )
    }

    override fun close() {
        engine.cancelVoice()
        closeables.forEach(Closeable::close)
        lazyCloseables.forEach { deferred ->
            if (deferred.isCompleted) runCatching { deferred.getCompleted() }.getOrNull()?.close()
        }
    }

    companion object {

        private const val WHISPER_MODEL_ASSET = "models/ggml-small-q5_1.bin"

        /** Warm-up progress reported to the UI so the load is never a vague light. */
        suspend fun create(
            context: Context,
            onProgress: (String) -> Unit = {},
        ): EdgeStack = withContext(Dispatchers.IO) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

            // ── Memory: relational + graph (Room) and vector (JNI), same file ──
            onProgress("Opening knowledge base (5 laws)…")
            val dbFile = KnowledgeBaseProvisioner(context).provision()
            val db = KnowledgeDatabase.open(context, dbFile)
            val vec = SqliteVecBridge.open(dbFile)
            KnowledgeDatabase.verifyArtifact(
                dao = db.dao(),
                expectedEmbedderId = QueryEmbedder.MODEL_ID,
                expectedDim = SqliteVecBridge.EMBEDDING_DIM,
            )

            // ── Query embedder: ON the text critical path, loaded eagerly ──
            onProgress("Loading search model…")
            val tokenizer = context.assets.open(QueryEmbedder.VOCAB_ASSET).bufferedReader().useLines {
                com.janadhikar.memory.SentencePieceTokenizer(
                    com.janadhikar.memory.SentencePieceTokenizer.loadVocab(it),
                )
            }
            val embedder = QueryEmbedder(
                modelFile = provisionAsset(context, QueryEmbedder.MODEL_ASSET),
                tokenizer = tokenizer,
            )
            onProgress("Ready — voice & AI loading in background…")

            // ── Voice model + LLM: OFF the critical path. Start loading now in
            //    the background; the engine awaits them only when actually used.
            val whisperDeferred: Deferred<WhisperBridge?> = scope.async {
                runCatching {
                    WhisperBridge.open(provisionAsset(context, WHISPER_MODEL_ASSET))
                }.getOrNull()
            }
            // Answer model. Prefer Qwen 2.5 1.5B (llama.cpp) if its GGUF was
            // pushed — a much stronger multilingual model (usable Hindi), and
            // ungated. Else fall back to Gemma (license-gated); else verbatim.
            val qwenFile = provisionQwenOrNull(context)
            val llamaDeferred: Deferred<LlamaTranslator?> = scope.async {
                qwenFile?.let { runCatching { LlamaTranslator.open(it)?.also { t -> t.warmUp() } }.getOrNull() }
            }
            val gemmaDeferred: Deferred<GemmaTranslator?> = scope.async {
                if (qwenFile != null) null else runCatching {
                    GemmaTranslator(context, provisionGemma(context)).also { it.warmUp() }
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
                val ok = llamaDeferred.await() != null || gemmaDeferred.await() != null
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
            val engine = ChatEngine(
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
                translate = { citation, language, onDelta ->
                    llamaDeferred.await()?.translate(citation, language, onDelta)
                        ?: gemmaDeferred.await()?.translate(citation, language, onDelta)
                        ?: Directive(VerbatimStatuteText.from(citation, language).value, language, true)
                },
                define = { phrase, language, onDelta ->
                    llamaDeferred.await()?.define(phrase, language, onDelta)
                        ?: gemmaDeferred.await()?.define(phrase, language, onDelta)
                        ?: Directive("The AI model is not available to explain words.", language, false)
                },
                reexplain = { citation, language, style, onDelta ->
                    llamaDeferred.await()?.translate(citation, language, style, onDelta)
                        ?: gemmaDeferred.await()?.translate(citation, language, style, onDelta)
                        ?: Directive(VerbatimStatuteText.from(citation, language).value, language, true)
                },
                corpusStats = { CorpusSummary.build(db.dao().provisionCounts()) },
                clock = System::currentTimeMillis,
                store = FileConversationStore(File(context.filesDir, "conversation.json")),
                archive = FileSessionArchive(File(context.filesDir, "sessions.json")),
            )

            EdgeStack(
                engine = engine,
                status = status.asStateFlow(),
                closeables = listOf(embedder, vec, Closeable { db.close() }),
                lazyCloseables = listOf(whisperDeferred, gemmaDeferred),
                scope = scope,
            )
        }

        /**
         * Prefer the bigger, more accurate Gemma 3 4B if the user pushed it
         * (much better answers, but slower); otherwise the default 1B.
         */
        /**
         * The pushed Qwen GGUF, but ONLY if the user opted in with a marker file
         * (models/use_qwen.flag). Qwen 2.5 1.5B is more accurate (esp. Hindi) but
         * runs on the CPU via llama.cpp — fast on phones with dot-product support,
         * but slow (~minutes) on budget CPUs that lack it. So Gemma (MediaPipe,
         * hardware-accelerated) stays the DEFAULT; Qwen is a deliberate opt-in.
         */
        private fun provisionQwenOrNull(context: Context): File? {
            val ext = context.getExternalFilesDir("models") ?: return null
            if (!File(ext, "use_qwen.flag").exists()) return null
            val f = File(ext, LlamaBridge.MODEL_ASSET.substringAfterLast('/'))
            return if (f.exists() && f.length() > 0) f else null
        }

        private fun provisionGemma(context: Context): File {
            context.getExternalFilesDir("models")?.let { ext ->
                // Any pushed Gemma 3 4B .task (litert-community names it *-web).
                ext.listFiles()
                    ?.firstOrNull { it.name.contains("4b", true) && it.extension == "task" && it.length() > 0 }
                    ?.let { return it }
            }
            return provisionAsset(context, GemmaTranslator.MODEL_ASSET)
        }

        /** Copies a model out of assets once; models are immutable per APK. */
        private fun provisionAsset(context: Context, assetPath: String): File {
            val name = assetPath.substringAfterLast('/')

            // 1) A model pushed to external storage wins. This lets the big
            //    weights ship OUTSIDE the APK (adb push / on-first-run download)
            //    so the installable stays small — a 900 MB APK is unreliable to
            //    transfer over USB/wifi. Path:
            //    /sdcard/Android/data/com.janadhikar/files/models/<name>
            context.getExternalFilesDir("models")?.let { ext ->
                val pushed = File(ext, name)
                if (pushed.exists() && pushed.length() > 0) return pushed
            }

            // 2) Otherwise fall back to a copy bundled in the APK assets (if any).
            val dir = context.noBackupFilesDir.apply { mkdirs() }
            val target = File(dir, name)
            val assetSize = runCatching { context.assets.openFd(assetPath).use { it.length } }.getOrNull()
                ?: throw IllegalStateException(
                    "Model '$name' not found on device. Run:  ./scripts/push_models.sh",
                )
            if (target.length() != assetSize) {
                context.assets.open(assetPath).use { input ->
                    target.outputStream().use { output -> input.copyTo(output) }
                }
            }
            return target
        }
    }
}
