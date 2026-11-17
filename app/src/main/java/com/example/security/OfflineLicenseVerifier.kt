package com.example.security

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

data class ActivationPayload(
    val moduleKey: String,
    val licenseReference: String,
    val issuedTo: String,
    val expiresAtEpochMs: Long?
)

sealed interface VerificationResult {
    data class Valid(
        val payload: ActivationPayload,
        val signatureDigest: String
    ) : VerificationResult

    data class Invalid(val reason: String) : VerificationResult
}

/**
 * Verifies owner-issued module activation tokens 100% offline using ECDSA (SHA256withECDSA).
 *
 * SECURITY DESIGN:
 * - Only the owner's X.509 SubjectPublicKeyInfo (P-256) PUBLIC key is embedded here.
 * - The owner's private signing key is NEVER bundled or stored inside this application.
 * - Token format: "LC1.<base64UrlPayloadJson>.<base64UrlDerSignature>"
 */
class OfflineLicenseVerifier(
    private val publicKeyProvider: () -> PublicKey = { decodeOwnerPublicKey(OWNER_PUBLIC_KEY_SPKI_B64) }
) {

    companion object {
        private const val TOKEN_PREFIX = "LC1"

        // Valid NIST P-256 X.509 SubjectPublicKeyInfo public key (no private key is embedded in the app).
        const val OWNER_PUBLIC_KEY_SPKI_B64 =
            "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEaxfR8uEsQkf4vOblY6RA8ncDfYEt6zOg9KE5RdiYwpZP40Li/hp/m47n60p8D54WK84zV2sxXs7LtkBoN79R9Q=="

        fun decodeOwnerPublicKey(base64Spki: String): PublicKey {
            val bytes = Base64.decode(base64Spki, Base64.DEFAULT)
            val spec = X509EncodedKeySpec(bytes)
            return KeyFactory.getInstance("EC").generatePublic(spec)
        }
    }

    fun isPublicKeyReady(): Boolean = runCatching { publicKeyProvider() }.isSuccess

    fun verifyToken(rawToken: String, currentTimeMs: Long = System.currentTimeMillis()): VerificationResult {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) {
            return VerificationResult.Invalid("Paste a signed LC1 activation key first.")
        }

        val segments = trimmed.split(".")
        if (segments.size != 3 || segments[0] != TOKEN_PREFIX) {
            return VerificationResult.Invalid(
                "Invalid key format. Expected LC1.<payload>.<signature>."
            )
        }

        val payloadBytes = runCatching {
            decodeBase64Url(segments[1])
        }.getOrElse {
            return VerificationResult.Invalid("Activation payload is not valid Base64URL.")
        }

        val signatureBytes = runCatching {
            decodeBase64Url(segments[2])
        }.getOrElse {
            return VerificationResult.Invalid("Activation signature is not valid Base64URL.")
        }

        val signatureValid = runCatching {
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(publicKeyProvider())
            verifier.update(payloadBytes)
            verifier.verify(signatureBytes)
        }.getOrElse {
            return VerificationResult.Invalid("Signature verification failed: ${it.message ?: "malformed signature"}")
        }

        if (!signatureValid) {
            return VerificationResult.Invalid(
                "Cryptographic signature mismatch. Token was not signed by the trusted owner key."
            )
        }

        val json = runCatching {
            JSONObject(String(payloadBytes, StandardCharsets.UTF_8))
        }.getOrElse {
            return VerificationResult.Invalid("Activation payload is not valid JSON.")
        }

        val moduleKey = json.optString("moduleKey", "").trim()
        val licenseRef = json.optString("licenseRef", "").trim()
        val issuedTo = json.optString("issuedTo", "Local Owner").trim()
        val expiresAt = if (json.has("expiresAtEpochMs") && !json.isNull("expiresAtEpochMs")) {
            json.optLong("expiresAtEpochMs")
        } else {
            null
        }

        if (moduleKey.isEmpty() || licenseRef.isEmpty()) {
            return VerificationResult.Invalid("Token payload missing required moduleKey or licenseRef.")
        }

        if (expiresAt != null && expiresAt < currentTimeMs) {
            return VerificationResult.Invalid("Activation token has expired.")
        }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(signatureBytes)
            .take(8)
            .joinToString("") { "%02x".format(it) }

        return VerificationResult.Valid(
            payload = ActivationPayload(
                moduleKey = moduleKey,
                licenseReference = licenseRef,
                issuedTo = issuedTo,
                expiresAtEpochMs = expiresAt
            ),
            signatureDigest = digest
        )
    }

    private fun decodeBase64Url(input: String): ByteArray {
        return Base64.decode(input, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
    }
}
