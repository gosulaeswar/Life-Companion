package com.example.security

import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PersonalAiKeyState(
    val hasPersonalKey: Boolean = false,
    val maskedPreview: String = "",
    val activeEngineLabel: String = ENGINE_FIREBASE_AI_LOGIC
) {
    companion object {
        const val ENGINE_PERSONAL_KEY = "Personal Gemini API Key"
        const val ENGINE_FIREBASE_AI_LOGIC = "Firebase AI Logic (Default)"
    }
}

/**
 * Device-local, Android Keystore (AES-256-GCM) encrypted storage for an optional
 * user-supplied personal Gemini API key.
 *
 * Security boundaries:
 * - Stored in a dedicated private SharedPreferences file excluded from backups and cloud sync.
 * - Encrypted via [KeystoreVaultManager] (`LCENC1:...`) before touching disk.
 * - Never logged to Logcat or exposed in plaintext via UI state.
 */
class PersonalAiKeyStore(
    context: Context,
    private val vaultManager: KeystoreVaultManager
) {
    private val prefs = context.applicationContext.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val _keyState = MutableStateFlow(loadCurrentState())
    val keyState: StateFlow<PersonalAiKeyState> = _keyState.asStateFlow()

    fun currentState(): PersonalAiKeyState = _keyState.value

    fun hasPersonalKey(): Boolean = _keyState.value.hasPersonalKey

    fun savePersonalKey(rawKey: String): Result<PersonalAiKeyState> {
        val cleaned = rawKey.trim()
        val validationError = validateKeyFormat(cleaned)
        if (validationError != null) {
            return Result.failure(IllegalArgumentException(validationError))
        }
        return runCatching {
            val encryptedEnvelope = vaultManager.encryptForLocalOrDriveBackup(cleaned)
            val masked = maskKey(cleaned)
            val committed = prefs.edit()
                .putString(KEY_ENCRYPTED_API_KEY, encryptedEnvelope)
                .putString(KEY_MASKED_PREVIEW, masked)
                .commit()
            if (!committed) {
                throw IllegalStateException("Unable to persist encrypted API key on this device.")
            }
            val updated = PersonalAiKeyState(
                hasPersonalKey = true,
                maskedPreview = masked,
                activeEngineLabel = PersonalAiKeyState.ENGINE_PERSONAL_KEY
            )
            _keyState.value = updated
            updated
        }.onFailure { err ->
            Log.e(TAG, "Failed to encrypt and save personal Gemini API key: ${err.javaClass.simpleName}")
        }
    }

    fun readPersonalKeyOrNull(): String? {
        val encrypted = prefs.getString(KEY_ENCRYPTED_API_KEY, null)?.trim()
        if (encrypted.isNullOrEmpty()) return null
        return runCatching {
            vaultManager.decryptBackupPayload(encrypted).trim().takeIf { it.isNotEmpty() }
        }.onFailure { err ->
            Log.e(TAG, "Failed to decrypt stored personal Gemini API key: ${err.javaClass.simpleName}")
        }.getOrNull()
    }

    fun clearPersonalKey() {
        prefs.edit()
            .remove(KEY_ENCRYPTED_API_KEY)
            .remove(KEY_MASKED_PREVIEW)
            .commit()
        _keyState.value = PersonalAiKeyState(
            hasPersonalKey = false,
            maskedPreview = "",
            activeEngineLabel = PersonalAiKeyState.ENGINE_FIREBASE_AI_LOGIC
        )
    }

    private fun loadCurrentState(): PersonalAiKeyState {
        val hasEncrypted = !prefs.getString(KEY_ENCRYPTED_API_KEY, null).isNullOrBlank()
        if (!hasEncrypted) {
            return PersonalAiKeyState()
        }
        val storedMask = prefs.getString(KEY_MASKED_PREVIEW, null)?.trim().orEmpty()
            .ifBlank { "••••••••" }
        return PersonalAiKeyState(
            hasPersonalKey = true,
            maskedPreview = storedMask,
            activeEngineLabel = PersonalAiKeyState.ENGINE_PERSONAL_KEY
        )
    }

    companion object {
        private const val TAG = "PersonalAiKeyStore"
        internal const val PREFS_NAME = "lifeweave_personal_ai_key_vault"
        internal const val KEY_ENCRYPTED_API_KEY = "encrypted_gemini_api_key"
        internal const val KEY_MASKED_PREVIEW = "masked_gemini_api_key_preview"

        fun validateKeyFormat(rawKey: String): String? {
            val cleaned = rawKey.trim()
            if (cleaned.isEmpty()) {
                return "Please paste your Gemini API key first."
            }
            if (cleaned.any { it.isWhitespace() }) {
                return "API key cannot contain spaces or line breaks. Copy only the key string."
            }
            if (cleaned.length < 20) {
                return "That key looks too short. Copy the full Gemini API key from Google AI Studio."
            }
            return null
        }

        fun maskKey(rawKey: String): String {
            val cleaned = rawKey.trim()
            return if (cleaned.length >= 10) {
                "${cleaned.take(4)}••••••${cleaned.takeLast(4)}"
            } else {
                "••••••••"
            }
        }
    }
}
