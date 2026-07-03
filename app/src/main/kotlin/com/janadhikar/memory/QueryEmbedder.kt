package com.janadhikar.memory

import com.janadhikar.memory.vec.SqliteVecBridge
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.File

/**
 * On-device query embedding via LiteRT: multilingual MiniLM (XLM-R) encoder +
 * mean-pool, all baked into one .tflite graph — the app only tokenizes
 * (SentencePiece unigram) and invokes the interpreter.
 *
 * CRITICAL INVARIANT: this must be the SAME embedding model the offline
 * pipeline used for corpus chunks (knowledge-pipeline/build_db.py), or query
 * and corpus vectors live in different spaces and every similarity score is
 * meaningless. [KnowledgeDatabase.verifyArtifact] asserts [MODEL_ID] against
 * kb_meta.embedder_model_id at startup and refuses to run on mismatch.
 *
 * The model is multilingual (EN + HI share one space), so a Hindi query can
 * retrieve a chunk embedded from its English text and vice versa.
 */
class QueryEmbedder(
    modelFile: File,
    private val tokenizer: SentencePieceTokenizer,
) : Closeable {

    private val interpreter = Interpreter(modelFile, Interpreter.Options().setNumThreads(4))

    /** Pure local compute; no I/O, no network. Thread-confined: one caller at a time. */
    fun embed(text: String): FloatArray {
        val encoding = tokenizer.encode(text, SEQ_LEN)
        val inputIds = arrayOf(encoding.inputIds)
        val attentionMask = arrayOf(encoding.attentionMask)
        val output = arrayOf(FloatArray(SqliteVecBridge.EMBEDDING_DIM))
        interpreter.runForMultipleInputsOutputs(
            arrayOf<Any>(inputIds, attentionMask),
            mapOf(0 to output),
        )
        return output[0]
    }

    override fun close() = interpreter.close()

    companion object {
        /** Must match export_tflite.py SEQ_LEN. */
        private const val SEQ_LEN = 128

        const val MODEL_ASSET = "models/embedder_minilm_384.tflite"
        const val VOCAB_ASSET = "models/spm_vocab.tsv"

        /** Identity string checked against kb_meta.embedder_model_id. */
        const val MODEL_ID = "paraphrase-multilingual-minilm-l12-v2/384/v2"
    }
}
