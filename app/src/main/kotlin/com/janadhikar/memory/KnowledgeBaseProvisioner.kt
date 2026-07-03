package com.janadhikar.memory

import android.content.Context
import java.io.File
import java.security.MessageDigest

/**
 * Copies the compiled knowledge base out of APK assets exactly once, verifies
 * its integrity, and marks it read-only at the filesystem level.
 *
 * Lives in [Context.getNoBackupFilesDir]: the artifact must never ride a cloud
 * backup (it is rebuilt from the APK), and nothing user-specific is in it.
 */
class KnowledgeBaseProvisioner(private val context: Context) {

    /** Returns the ready-to-open DB file, provisioning it on first call. */
    fun provision(): File {
        // Force-create no_backup/ up front: on a fresh install the directory
        // may not exist until first written, and the getter alone is not always
        // enough on every OEM/OS build.
        val dir = context.noBackupFilesDir.apply { mkdirs() }
        val target = File(dir, DB_FILE_NAME)
        val expectedSha = context.assets.open(SHA_ASSET).bufferedReader().use { it.readText().trim() }

        if (target.exists() && sha256(target) == expectedSha) {
            clearSidecars(target) // a stale -wal from a prior DB corrupts reads
            return target
        }

        // First run, APK upgrade (new artifact), or corrupted copy: copy the
        // asset straight to the target. No staging/rename — those were flaky on
        // some filesystems. Retried once because the app-private dir can lag
        // right after a fresh install.
        var lastError: Exception? = null
        repeat(2) { attempt ->
            try {
                dir.mkdirs()
                clearSidecars(target)
                target.delete()
                context.assets.open(DB_ASSET).use { input ->
                    target.outputStream().use { output -> input.copyTo(output); output.flush() }
                }
                check(target.exists() && sha256(target) == expectedSha) {
                    "integrity check failed after copy"
                }
                clearSidecars(target)
                return target
            } catch (e: Exception) {
                lastError = e
                Thread.sleep(150L * (attempt + 1))
            }
        }
        throw IllegalStateException(
            "Could not provision knowledge base: ${lastError?.message}", lastError,
        )
        // NOTE: no filesystem read-only bit — Android's SQLite helper needs to
        // open the file r/w even for reads. Immutability is enforced instead by
        // PRAGMA query_only (Room), SQLITE_OPEN_READONLY (native), and the
        // SELECT-only DAO (Rule 4) — plus the SHA-256 check above on every start.
        return target
    }

    /**
     * Delete SQLite journal sidecars. A `-wal`/`-shm` left by a PRIOR database
     * file gets applied over the freshly-provisioned one, corrupting reads
     * (kb_meta comes back empty). Safe because the DB is opened read-only, so
     * no committed data ever lives only in a WAL.
     */
    private fun clearSidecars(dbFile: File) {
        for (suffix in listOf("-wal", "-shm", "-journal")) {
            File(dbFile.path + suffix).delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    companion object {
        const val DB_FILE_NAME = "janadhikar_knowledge.db"
        private const val DB_ASSET = "db/$DB_FILE_NAME"

        /** Written by knowledge-pipeline/pack alongside the DB. */
        private const val SHA_ASSET = "db/$DB_FILE_NAME.sha256"
    }
}
