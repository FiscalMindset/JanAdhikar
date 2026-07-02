package com.janadhikar.stt

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

/**
 * 16 kHz mono float PCM capture for whisper.cpp.
 *
 * PRIVACY INVARIANT: audio exists ONLY as transient FloatArrays flowing
 * through this Flow. Nothing here (or downstream) writes PCM to disk.
 */
class AudioCapture {

    /**
     * Emits ~[CHUNK_MILLIS] chunks until the collector cancels.
     * Caller must hold RECORD_AUDIO permission (checked by the engine before start).
     */
    @SuppressLint("MissingPermission")
    fun stream(): Flow<FloatArray> = callbackFlow {
        val minBuffer = AudioRecord.getMinBufferSize(
            WhisperBridge.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val chunkSamples = WhisperBridge.SAMPLE_RATE_HZ * CHUNK_MILLIS / 1000
        val record = AudioRecord(
            MediaRecorder.AudioSource.VOICE_RECOGNITION,
            WhisperBridge.SAMPLE_RATE_HZ,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minBuffer, chunkSamples * Float.SIZE_BYTES),
        )
        check(record.state == AudioRecord.STATE_INITIALIZED) { "AudioRecord failed to initialise" }

        record.startRecording()
        try {
            while (isActive) {
                val buffer = FloatArray(chunkSamples)
                val read = record.read(buffer, 0, buffer.size, AudioRecord.READ_BLOCKING)
                if (read > 0) trySend(if (read == buffer.size) buffer else buffer.copyOf(read))
            }
        } finally {
            record.stop()
            record.release()
        }
        awaitClose { }
    }.flowOn(Dispatchers.IO)

    companion object {
        const val CHUNK_MILLIS = 500
    }
}
