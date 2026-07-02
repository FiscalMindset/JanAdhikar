package com.janadhikar.memory

import android.content.Context
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder.TextEmbedderOptions
import com.janadhikar.memory.vec.SqliteVecBridge
import java.io.Closeable

/**
 * On-device query embedding via LiteRT (MediaPipe TextEmbedder task).
 *
 * CRITICAL INVARIANT: this must be the SAME embedding model family the offline
 * pipeline used for corpus chunks, or query and corpus vectors live in
 * different spaces and every similarity score is meaningless.
 * [KnowledgeDatabase.verifyArtifact] asserts [MODEL_ID] against
 * kb_meta.embedder_model_id at startup and refuses to run on mismatch.
 *
 * The model is multilingual (EN + HI share one space), so a Hindi query can
 * retrieve a chunk embedded from its English text and vice versa.
 */
class QueryEmbedder(context: Context) : Closeable {

    private val embedder: TextEmbedder = TextEmbedder.createFromOptions(
        context,
        TextEmbedderOptions.builder()
            .setBaseOptions(BaseOptions.builder().setModelAssetPath(MODEL_ASSET).build())
            .build(),
    )

    /** Embeds one normalized query. Pure local compute; no I/O, no network. */
    fun embed(text: String): FloatArray {
        val embedding = embedder.embed(text).embeddingResult().embeddings().first()
        val vector = embedding.floatEmbedding()
        check(vector.size == SqliteVecBridge.EMBEDDING_DIM) {
            "Embedder produced dim ${vector.size}, expected ${SqliteVecBridge.EMBEDDING_DIM}"
        }
        return vector
    }

    override fun close() = embedder.close()

    companion object {
        /** Multilingual sentence embedder, 4-bit friendly, 384-dim. */
        const val MODEL_ASSET = "models/embedder_multilingual_minilm_384.tflite"

        /** Identity string checked against kb_meta.embedder_model_id. */
        const val MODEL_ID = "multilingual-minilm-l12-v2/384/v1"
    }
}
