package com.example.security

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.UUID
import kotlin.math.max

data class StoredEncryptedFileResult(
    val relativePath: String,
    val originalFileName: String,
    val mimeType: String,
    val plainSizeBytes: Long
)

/**
 * Manages hardware-backed AES-256-GCM encrypted document blobs inside private app storage
 * (`filesDir/health_vault/`). Raw unencrypted document bytes are never written to disk.
 */
class EncryptedDocumentVault(
    context: Context,
    private val vaultManager: KeystoreVaultManager
) {
    private val appContext = context.applicationContext
    private val vaultDir: File = File(appContext.filesDir, VAULT_SUBDIR)

    companion object {
        private const val VAULT_SUBDIR = "health_vault"
        private const val MAX_DOCUMENT_BYTES = 16 * 1024 * 1024 // 16 MB safety bound
    }

    suspend fun importAndEncryptUri(uri: Uri): Result<StoredEncryptedFileResult> = withContext(Dispatchers.IO) {
        runCatching {
            val resolver = appContext.contentResolver
            val mimeType = resolver.getType(uri)?.trim().orEmpty().ifBlank { "application/octet-stream" }
            val originalName = queryDisplayName(uri).ifBlank {
                if (mimeType.contains("pdf", ignoreCase = true)) "Health_Document.pdf" else "Health_Record.jpg"
            }

            val rawBytes = resolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                var total = 0
                while (true) {
                    val read = input.read(buf)
                    if (read <= 0) break
                    total += read
                    require(total <= MAX_DOCUMENT_BYTES) {
                        "Document exceeds 16 MB on-device encrypted vault limit."
                    }
                    out.write(buf, 0, read)
                }
                out.toByteArray()
            } ?: error("Unable to read selected document from device picker.")

            require(rawBytes.isNotEmpty()) { "Selected document file is empty." }

            val relPath = storeRawBytesEncrypted(rawBytes)
            StoredEncryptedFileResult(
                relativePath = relPath,
                originalFileName = originalName,
                mimeType = mimeType,
                plainSizeBytes = rawBytes.size.toLong()
            )
        }
    }

    suspend fun storeRawBytesEncrypted(rawBytes: ByteArray): String = withContext(Dispatchers.IO) {
        if (!vaultDir.exists()) {
            vaultDir.mkdirs()
        }
        val fileName = "${UUID.randomUUID()}.lcenc"
        val targetFile = File(vaultDir, fileName)
        val encrypted = vaultManager.encryptBytes(rawBytes)
        targetFile.writeBytes(encrypted)
        fileName
    }

    suspend fun readDecryptedBytes(relativePath: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        runCatching {
            val file = resolveSafeVaultFile(relativePath)
                ?: throw IllegalArgumentException("Invalid vault file reference.")
            require(file.exists() && file.isFile) { "Encrypted document blob was not found on this device." }
            val encrypted = file.readBytes()
            vaultManager.decryptBytes(encrypted)
        }
    }

    suspend fun decodeDecryptedBitmap(
        relativePath: String,
        maxDimension: Int = 1280
    ): Result<Bitmap> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = readDecryptedBytes(relativePath).getOrThrow()
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
            val maxSide = max(bounds.outWidth, bounds.outHeight)
            require(maxSide > 0) { "Document bytes could not be decoded as an image." }
            var sample = 1
            while (maxSide / sample > maxDimension) {
                sample *= 2
            }
            val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
                ?: error("Unable to render decrypted image preview.")
        }
    }

    suspend fun deleteEncryptedFile(relativePath: String) = withContext(Dispatchers.IO) {
        val file = resolveSafeVaultFile(relativePath) ?: return@withContext
        if (file.exists()) {
            file.delete()
        }
    }

    suspend fun wipeAllVaultFiles() = withContext(Dispatchers.IO) {
        if (vaultDir.exists()) {
            vaultDir.listFiles()?.forEach { it.delete() }
        }
    }

    fun encryptedBlobExists(relativePath: String): Boolean {
        val file = resolveSafeVaultFile(relativePath) ?: return false
        return file.exists() && file.isFile
    }

    private fun resolveSafeVaultFile(relativePath: String): File? {
        val clean = relativePath.trim()
        if (clean.isEmpty()) return null
        val candidate = File(vaultDir, clean)
        val baseCanonical = runCatching { vaultDir.canonicalPath }.getOrNull() ?: return null
        val candCanonical = runCatching { candidate.canonicalPath }.getOrNull() ?: return null
        return if (candCanonical.startsWith(baseCanonical + File.separator)) candidate else null
    }

    private fun queryDisplayName(uri: Uri): String {
        return runCatching {
            appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx).orEmpty() else ""
                } else ""
            }.orEmpty()
        }.getOrDefault("")
    }
}
