package com.example.security

import android.content.Context
import android.util.Log
import com.example.BuildConfig
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class PersonalAiKeyState(
    val hasPersonalKey: Boolean = false,
    val maskedPreview: String = "",
    val activeEngineLabel: String = defaultEngineLabel()
) {
    companion object {
        const val ENGINE_PERSONAL_KEY = "Personal xAI Grok API Key"
        const val ENGINE_APP_SECRET_GROK = "xAI Grok (App Secret)"
        const val ENGINE_GROK_UNCONFIGURED = "xAI Grok (Enter Key in Settings)"

        fun isUsableGrokKey(rawKey: String?): Boolean {
            val cleaned = rawKey?.trim().orEmpty()
            if (cleaned.length < 20) return false
            if (cleaned.any { it.isWhitespace() }) return false
            if (cleaned.equals("XAI_API_KEY_DEFAULT_VALUE", ignoreCase = true)) return false
            if (cleaned.contains("DEFAULT_VALUE", ignoreCase = true) || cleaned.contains("PLACEHOLDER", ignoreCase = true)) {
                return false
            }
            if (cleaned.startsWith("AIza") || cleaned.startsWith("AQ.")) return false
            return cleaned.startsWith("xai-")
        }

        fun hasBuildConfigGrokKey(): Boolean {
            val raw = runCatching { BuildConfig.XAI_API_KEY }.getOrDefault("")
            return isUsableGrokKey(raw)
        }

        fun defaultEngineLabel(): String =
            if (hasBuildConfigGrokKey()) ENGINE_APP_SECRET_GROK else ENGINE_GROK_UNCONFIGURED
    }
}

/**
 * Device-local, Android Keystore (AES-256-GCM) encrypted storage for an optional
 * user-supplied personal xAI Grok API key (`xai-...`).
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
                throw IllegalStateException("Unable to persist encrypted Grok API key on this device.")
            }
            val updated = PersonalAiKeyState(
                hasPersonalKey = true,
                maskedPreview = masked,
                activeEngineLabel = PersonalAiKeyState.ENGINE_PERSONAL_KEY
            )
            _keyState.value = updated
            updated
        }.onFailure { err ->
            Log.e(TAG, "Failed to encrypt and save personal xAI Grok API key: ${err.javaClass.simpleName}")
        }
    }

    fun readPersonalKeyOrNull(): String? {
        val encrypted = prefs.getString(KEY_ENCRYPTED_API_KEY, null)?.trim()
        if (encrypted.isNullOrEmpty()) return null
        return runCatching {
            vaultManager.decryptBackupPayload(encrypted).trim()
                .takeIf { PersonalAiKeyState.isUsableGrokKey(it) }
        }.onFailure { err ->
            Log.e(TAG, "Failed to decrypt stored personal xAI Grok API key: ${err.javaClass.simpleName}")
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
            activeEngineLabel = PersonalAiKeyState.defaultEngineLabel()
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
        internal const val KEY_ENCRYPTED_API_KEY = "encrypted_xai_grok_api_key"
        internal const val KEY_MASKED_PREVIEW = "masked_xai_grok_api_key_preview"

        fun validateKeyFormat(rawKey: String): String? {
            val cleaned = rawKey.trim()
            if (cleaned.isEmpty()) {
                return "Please paste your xAI Grok API key first."
            }
            if (cleaned.any { it.isWhitespace() }) {
                return "API key cannot contain spaces or line breaks. Copy only the key string."
            }
            if (cleaned.startsWith("AIza") || cleaned.startsWith("AQ.")) {
                return "That looks like a Google Gemini key. Please paste an xAI Grok API key (starts with xai-) from console.x.ai."
            }
            if (!cleaned.startsWith("xai-")) {
                return "xAI Grok API keys start with \"xai-\". Create or copy your key from console.x.ai."
            }
            if (cleaned.length < 20) {
                return "That key looks too short. Copy the full xAI Grok API key (xai-...) from console.x.ai."
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
