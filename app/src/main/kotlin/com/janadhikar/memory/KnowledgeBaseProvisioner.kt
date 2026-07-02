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
        val target = File(context.noBackupFilesDir, DB_FILE_NAME)
        val expectedSha = context.assets.open(SHA_ASSET).bufferedReader().use { it.readText().trim() }

        if (target.exists() && sha256(target) == expectedSha) {
            return target
        }

        // First run, APK upgrade (new artifact), or corrupted copy: re-copy.
        val staging = File(context.noBackupFilesDir, "$DB_FILE_NAME.staging")
        context.assets.open(DB_ASSET).use { input ->
            staging.outputStream().use { output -> input.copyTo(output) }
        }
        check(sha256(staging) == expectedSha) {
            staging.delete()
            "Knowledge base failed integrity check after copy — refusing to serve legal data " +
                "from a corrupt artifact."
        }
        target.delete()
        check(staging.renameTo(target)) { "Could not move knowledge base into place." }
        target.setReadOnly()
        return target
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
