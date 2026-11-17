package com.example.security

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.nio.charset.StandardCharsets
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

data class VaultSecurityStatus(
    val keystoreAvailable: Boolean,
    val keyAlias: String,
    val algorithm: String = "AES-256-GCM",
    val isolatedFromActivationKeys: Boolean = true
)

/**
 * Manages the device-bound symmetric data-encryption key inside Android Keystore.
 * Strictly separated from [OfflineLicenseVerifier] public-key activation credentials.
 * Ready to wrap user-controlled local or encrypted Google Drive backup blobs.
 */
class KeystoreVaultManager {

    companion object {
        private const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val DATA_KEY_ALIAS = "life_companion_data_master_v1"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_BITS = 128
    }

    fun inspectVaultStatus(): VaultSecurityStatus {
        return try {
            ensureDataKey()
            VaultSecurityStatus(
                keystoreAvailable = true,
                keyAlias = DATA_KEY_ALIAS
            )
        } catch (_: Throwable) {
            VaultSecurityStatus(
                keystoreAvailable = false,
                keyAlias = DATA_KEY_ALIAS
            )
        }
    }

    fun encryptForLocalOrDriveBackup(plainText: String): String {
        val secretKey = ensureDataKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val cipherBytes = cipher.doFinal(plainText.toByteArray(StandardCharsets.UTF_8))
        val ivB64 = Base64.encodeToString(iv, Base64.NO_WRAP)
        val dataB64 = Base64.encodeToString(cipherBytes, Base64.NO_WRAP)
        return "LCENC1:$ivB64:$dataB64"
    }

    fun decryptBackupPayload(encryptedBlob: String): String {
        val parts = encryptedBlob.split(":")
        require(parts.size == 3 && parts[0] == "LCENC1") {
            "Unsupported encrypted backup envelope"
        }
        val iv = Base64.decode(parts[1], Base64.NO_WRAP)
        val cipherBytes = Base64.decode(parts[2], Base64.NO_WRAP)
        val secretKey = ensureDataKey()
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_BITS, iv))
        val plainBytes = cipher.doFinal(cipherBytes)
        return String(plainBytes, StandardCharsets.UTF_8)
    }

    private fun ensureDataKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        val existing = keyStore.getKey(DATA_KEY_ALIAS, null) as? SecretKey
        if (existing != null) return existing

        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            ANDROID_KEYSTORE
        )
        val spec = KeyGenParameterSpec.Builder(
            DATA_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }
}
