package com.janadhikar.llm

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * One-time, first-run download of the on-device AI model. A link-shared APK
 * cannot use `adb push`, and the model is too large to bundle — so the app
 * fetches it once, reports progress, and then runs 100% offline forever after.
 *
 * Deliberately dependency-free (HttpURLConnection) — a full SDK is overkill for
 * "download one file with a progress bar". Resumes a partial download.
 */
object ModelDownloader {

    /** Ungated public GGUF — no token, no license wall. */
    const val QWEN_URL =
        "https://huggingface.co/Qwen/Qwen2.5-1.5B-Instruct-GGUF/resolve/main/qwen2.5-1.5b-instruct-q4_k_m.gguf"

    /** Public whisper small (multilingual, q5) — for voice input. */
    const val WHISPER_URL =
        "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-small-q5_1.bin"

    data class Progress(val downloadedBytes: Long, val totalBytes: Long) {
        val percent: Int get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
    }

    /**
     * Ensures [target] exists, downloading from [url] if needed. [onProgress] is
     * called as bytes arrive. Returns the file, or null on failure (caller can
     * fall back). Downloads to a .part file and renames atomically on success.
     */
    suspend fun ensure(
        url: String,
        target: File,
        onProgress: (Progress) -> Unit,
    ): File? = withContext(Dispatchers.IO) {
        if (target.exists() && target.length() > 0) return@withContext target
        target.parentFile?.mkdirs()
        val part = File(target.parentFile, target.name + ".part")
        val already = if (part.exists()) part.length() else 0L

        runCatching {
            var conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            if (already > 0) conn.setRequestProperty("Range", "bytes=$already-")
            conn.instanceFollowRedirects = true
            conn.connect()

            // Some CDNs answer the Range with 200 (full) — then start over.
            val resumed = conn.responseCode == HttpURLConnection.HTTP_PARTIAL
            val startAt = if (resumed) already else 0L
            val contentLen = conn.contentLengthLong.coerceAtLeast(0)
            val total = startAt + contentLen

            conn.inputStream.use { input ->
                java.io.RandomAccessFile(part, "rw").use { out ->
                    if (resumed) out.seek(already) else out.setLength(0)
                    val buf = ByteArray(1 shl 16)
                    var downloaded = startAt
                    var lastReport = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        downloaded += n
                        if (downloaded - lastReport > 2_000_000) { // report every ~2 MB
                            onProgress(Progress(downloaded, total))
                            lastReport = downloaded
                        }
                    }
                    onProgress(Progress(downloaded, total))
                }
            }
            if (part.length() == 0L) error("empty download")
            check(part.renameTo(target)) { "rename failed" }
            target
        }.getOrElse {
            null
        }
    }
}
