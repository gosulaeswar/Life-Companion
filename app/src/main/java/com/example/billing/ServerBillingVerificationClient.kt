package com.example.billing

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.nio.charset.StandardCharsets

data class HttpTransportRequest(
    val url: String,
    val method: String = "GET",
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null
)

data class HttpTransportResponse(
    val statusCode: Int,
    val body: String
)

fun interface BillingHttpTransport {
    suspend fun execute(request: HttpTransportRequest): HttpTransportResponse
}

class UrlConnectionBillingHttpTransport(
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 15_000
) : BillingHttpTransport {
    override suspend fun execute(request: HttpTransportRequest): HttpTransportResponse =
        withContext(Dispatchers.IO) {
            val conn = (URL(request.url).openConnection() as HttpURLConnection).apply {
                requestMethod = request.method
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                useCaches = false
                doInput = true
                request.headers.forEach { (k, v) -> setRequestProperty(k, v) }
                if (request.body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    outputStream.use { os ->
                        os.write(request.body.toByteArray(StandardCharsets.UTF_8))
                    }
                }
            }
            try {
                val status = conn.responseCode
                val stream = if (status in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
                val responseBody = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                HttpTransportResponse(statusCode = status, body = responseBody)
            } finally {
                conn.disconnect()
            }
        }
}

/**
 * Client interface to the LifeWeave server-side Google Play Developer API verifier & remote pricing catalog.
 *
 * SECURITY INVARIANT: This class NEVER holds Play service-account JSON keys, RSA/ECDSA private keys,
 * or webhook HMAC secrets. It submits the purchaseToken and optional Firebase ID token over HTTPS so
 * the backend validates ownership & expiry directly with Google Play Developer API.
 */
class ServerBillingVerificationClient(
    private val transport: BillingHttpTransport = UrlConnectionBillingHttpTransport()
) {
    fun isValidEndpointUrl(rawUrl: String, allowLocalDebugLoopback: Boolean = false): Boolean {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return false
        val uri = runCatching { URI(trimmed) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        val host = uri.host?.lowercase() ?: return false
        if (scheme == "https") return host.isNotBlank()
        if (allowLocalDebugLoopback && scheme == "http") {
            return host == "127.0.0.1" || host == "localhost" || host == "10.0.2.2"
        }
        return false
    }

    suspend fun verifyPurchase(
        request: ServerPurchaseValidationRequest,
        endpointUrl: String,
        allowLocalDebugLoopback: Boolean = false
    ): ServerPurchaseValidationResult {
        val cleanUrl = endpointUrl.trim()
        if (cleanUrl.isEmpty()) {
            return ServerPurchaseValidationResult.EndpointUnconfigured(
                "Purchase validation server URL is not configured yet. Tap 'Restore' after configuring BILLING_VERIFICATION_URL."
            )
        }
        if (!isValidEndpointUrl(cleanUrl, allowLocalDebugLoopback)) {
            return ServerPurchaseValidationResult.Rejected(
                reason = "Billing verification endpoint must use HTTPS.",
                lifecycleStatus = EntitlementLifecycleStatus.PENDING_VERIFICATION
            )
        }
        if (request.purchaseToken.isBlank()) {
            return ServerPurchaseValidationResult.Rejected(
                reason = "Missing Google Play purchaseToken."
            )
        }

        val headers = buildMap {
            put("Accept", "application/json")
            if (!request.firebaseIdToken.isNullOrBlank()) {
                put("Authorization", "Bearer ${request.firebaseIdToken}")
            }
        }
        val payloadJson = SubscriptionEntitlementEngine.buildServerValidationRequestJson(request)

        return runCatching {
            val response = transport.execute(
                HttpTransportRequest(
                    url = cleanUrl,
                    method = "POST",
                    headers = headers,
                    body = payloadJson
                )
            )
            when (response.statusCode) {
                in 200..299 -> {
                    SubscriptionEntitlementEngine.parseServerValidationResponseJson(
                        responseBody = response.body,
                        fallbackProductId = request.productId,
                        fallbackBasePlanId = request.basePlanId
                    )
                }
                400, 401, 403, 404, 409, 422 -> {
                    val parsedMsg = runCatching {
                        org.json.JSONObject(response.body).optString("message")
                    }.getOrNull().orEmpty()
                    ServerPurchaseValidationResult.Rejected(
                        reason = parsedMsg.ifBlank { "Server rejected purchase verification (HTTP ${response.statusCode})." }
                    )
                }
                else -> {
                    ServerPurchaseValidationResult.TransientFailure(
                        "Billing server unavailable (HTTP ${response.statusCode}). Will retry automatically."
                    )
                }
            }
        }.getOrElse { err ->
            ServerPurchaseValidationResult.TransientFailure(
                "Network error contacting billing verifier: ${err.message ?: "offline"}"
            )
        }
    }

    suspend fun fetchRemotePricingCatalog(
        configUrl: String,
        allowLocalDebugLoopback: Boolean = false
    ): RemotePricingFetchResult {
        val cleanUrl = configUrl.trim()
        if (cleanUrl.isEmpty()) {
            return RemotePricingFetchResult.Unconfigured(
                "Remote pricing catalog URL is not set. Using bundled defaults & Google Play Store prices."
            )
        }
        if (!isValidEndpointUrl(cleanUrl, allowLocalDebugLoopback)) {
            return RemotePricingFetchResult.Failure("Pricing config URL must use HTTPS.")
        }

        return runCatching {
            val response = transport.execute(
                HttpTransportRequest(
                    url = cleanUrl,
                    method = "GET",
                    headers = mapOf("Accept" to "application/json")
                )
            )
            if (response.statusCode in 200..299) {
                SubscriptionEntitlementEngine.parseRemotePricingConfigJson(response.body)
            } else {
                RemotePricingFetchResult.Failure("Pricing config returned HTTP ${response.statusCode}.")
            }
        }.getOrElse { err ->
            RemotePricingFetchResult.Failure("Unable to fetch remote pricing: ${err.message ?: "offline"}")
        }
    }
}
