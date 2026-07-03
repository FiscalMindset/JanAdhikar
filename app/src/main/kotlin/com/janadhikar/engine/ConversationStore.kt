package com.janadhikar.engine

import com.janadhikar.input.AppLanguage
import com.janadhikar.llm.Directive
import com.janadhikar.memory.model.VerifiedCitation
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

private val JSON = Json { ignoreUnknownKeys = true; encodeDefaults = true }

// ── Serializable DTOs, shared by the current-conversation store and the
//    session archive. Decoupled from the domain models on purpose. ───────────

@Serializable
internal data class StoredTurn(
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
internal data class StoredCitation(
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

/** A completed turn → DTO, or null for transient (Thinking / still-streaming). */
internal fun Turn.toStoredOrNull(): StoredTurn? = when (val a = answer) {
    Answer.Thinking -> null
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

// ── Current conversation (survives restart) ─────────────────────────────────

interface ConversationStore {
    fun load(): List<Turn>
    fun save(turns: List<Turn>)
}

object NoopConversationStore : ConversationStore {
    override fun load(): List<Turn> = emptyList()
    override fun save(turns: List<Turn>) = Unit
}

class FileConversationStore(private val file: File) : ConversationStore {
    private val ser = ListSerializer(StoredTurn.serializer())
    override fun load(): List<Turn> = runCatching {
        if (!file.exists()) return emptyList()
        JSON.decodeFromString(ser, file.readText()).map { it.toTurn() }
    }.getOrDefault(emptyList())

    override fun save(turns: List<Turn>) {
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(JSON.encodeToString(ser, turns.mapNotNull { it.toStoredOrNull() }))
        }
    }
}

// ── Past conversations (history) ────────────────────────────────────────────

/** One archived past conversation. */
data class Session(val id: Long, val title: String, val turns: List<Turn>)

interface SessionArchive {
    fun list(): List<Session>
    /** Archive a finished conversation (newest first). No-op if empty. */
    fun add(id: Long, turns: List<Turn>)
    fun delete(id: Long)
}

object NoopSessionArchive : SessionArchive {
    override fun list(): List<Session> = emptyList()
    override fun add(id: Long, turns: List<Turn>) = Unit
    override fun delete(id: Long) = Unit
}

class FileSessionArchive(private val file: File) : SessionArchive {

    @Serializable
    private data class StoredSession(val id: Long, val turns: List<StoredTurn>)

    private val ser = ListSerializer(StoredSession.serializer())
    private val max = 100

    private fun read(): List<StoredSession> = runCatching {
        if (!file.exists()) emptyList() else JSON.decodeFromString(ser, file.readText())
    }.getOrDefault(emptyList())

    private fun write(sessions: List<StoredSession>) = runCatching {
        file.parentFile?.mkdirs()
        file.writeText(JSON.encodeToString(ser, sessions.take(max)))
    }

    override fun list(): List<Session> = read().map { s ->
        Session(s.id, s.turns.firstOrNull()?.query.orEmpty().ifBlank { "(empty)" }, s.turns.map { it.toTurn() })
    }

    override fun add(id: Long, turns: List<Turn>) {
        val stored = turns.mapNotNull { it.toStoredOrNull() }
        if (stored.isEmpty()) return
        write(listOf(StoredSession(id, stored)) + read().filter { it.id != id })
    }

    override fun delete(id: Long) = write(read().filter { it.id != id }).let {}
}
