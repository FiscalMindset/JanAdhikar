package com.janadhikar.engine

import com.janadhikar.input.AppLanguage
import com.janadhikar.llm.Directive
import com.janadhikar.memory.model.VerifiedCitation
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Persists the conversation so it survives app restarts — the "memory" the
 * user expects. Only COMPLETED turns are stored (a Thinking/streaming turn is
 * transient). Plain JSON in the app's private files dir; 100% local.
 */
interface ConversationStore {
    fun load(): List<Turn>
    fun save(turns: List<Turn>)
}

/** No-op store for tests / when persistence is unavailable. */
object NoopConversationStore : ConversationStore {
    override fun load(): List<Turn> = emptyList()
    override fun save(turns: List<Turn>) = Unit
}

class FileConversationStore(private val file: File) : ConversationStore {

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val listSerializer = ListSerializer(StoredTurn.serializer())

    override fun load(): List<Turn> = runCatching {
        if (!file.exists()) return emptyList()
        json.decodeFromString(listSerializer, file.readText()).map { it.toTurn() }
    }.getOrDefault(emptyList())

    override fun save(turns: List<Turn>) {
        runCatching {
            val stored = turns.mapNotNull { it.toStoredOrNull() }
            file.parentFile?.mkdirs()
            file.writeText(json.encodeToString(listSerializer, stored))
        }
    }

    // ── DTOs (decoupled from the domain models) ─────────────────────────────

    @Serializable
    private data class StoredTurn(
        val id: Long,
        val query: String,
        val refused: Boolean,
        val explanation: String = "",
        val modelId: String = Directive.VERBATIM_MODEL_ID,
        val verbatimFallback: Boolean = false,
        val language: String = "ENGLISH",
        val confidence: Float = 0f,
        val generationMillis: Long = 0,
        val citations: List<StoredCitation> = emptyList(),
    ) {
        fun toTurn(): Turn {
            val lang = runCatching { AppLanguage.valueOf(language) }.getOrDefault(AppLanguage.ENGLISH)
            val answer = if (refused) {
                Answer.NoStatute
            } else {
                Answer.Grounded(
                    explanation = Directive(explanation, lang, verbatimFallback, modelId, generationMillis),
                    citations = citations.map { it.toCitation() },
                    confidence = confidence,
                    redirectedFromSuperseded = false,
                    streaming = false,
                )
            }
            return Turn(id, query, answer)
        }
    }

    @Serializable
    private data class StoredCitation(
        val chunkId: Long, val statuteName: String, val statuteNameHi: String, val unit: String,
        val sectionNumber: String, val clause: String?, val pageNumber: Int,
        val verbatimTextEn: String, val verbatimTextHi: String,
        val sourceDocument: String, val sourceUrl: String, val compilationDate: String,
    ) {
        fun toCitation() = VerifiedCitation(
            chunkId, statuteName, statuteNameHi, unit, sectionNumber, clause, pageNumber,
            verbatimTextEn, verbatimTextHi, sourceDocument, sourceUrl, compilationDate,
        )
    }

    private fun Turn.toStoredOrNull(): StoredTurn? = when (val a = answer) {
        Answer.Thinking -> null // transient — do not persist
        Answer.NoStatute -> StoredTurn(id, query, refused = true)
        is Answer.Grounded -> if (a.streaming) null else StoredTurn(
            id = id, query = query, refused = false,
            explanation = a.explanation.text, modelId = a.explanation.modelId,
            verbatimFallback = a.explanation.isVerbatimFallback,
            language = a.explanation.language.name, confidence = a.confidence,
            generationMillis = a.explanation.generationMillis,
            citations = a.citations.map {
                StoredCitation(
                    it.chunkId, it.statuteName, it.statuteNameHi, it.unit, it.sectionNumber, it.clause,
                    it.pageNumber, it.verbatimTextEn, it.verbatimTextHi, it.sourceDocument, it.sourceUrl,
                    it.compilationDate,
                )
            },
        )
    }
}
