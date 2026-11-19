package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlin.random.Random

private const val DIRECT_GEMINI_TAG = "DirectGeminiRestClient"

data class DirectGeminiInlinePart(
    val mimeType: String,
    val bytes: ByteArray
)

data class DirectGeminiPingResult(
    val modelUsed: String,
    val latencyMs: Long,
    val sampleText: String
)

data class DirectGeminiHttpResponse(
    val statusCode: Int,
    val body: String
)

/**
 * Direct HTTPS client for the official Gemini Developer API using a user-supplied
 * personal API key passed strictly via the `x-goog-api-key` request header (never
 * in URL query strings or logs).
 *
 * Includes resilient recovery for transient HTTP 503/429/5xx bursts:
 * 1. Short jittered backoff retry on the primary model (`gemini-3.8-flash`)
 * 2. Automatic failover to `gemini-3.5-flash-lite`
 * 3. Live model discovery safety net (`GET /v1beta/models`) for eligible Gemini 3+ Flash models
 */
class DirectGeminiRestClient(
    private val httpGetExecutor: suspend (
        endpointUrl: String,
        apiKey: String,
        timeoutMs: Int
    ) -> DirectGeminiHttpResponse = ::executeHttpsGet,
    private val delayFn: suspend (Long) -> Unit = { ms -> delay(ms) },
    private val httpExecutor: suspend (
        endpointUrl: String,
        apiKey: String,
        bodyJson: String,
        timeoutMs: Int
    ) -> DirectGeminiHttpResponse = ::executeHttpsPost
) {
    private data class ExecutionSuccess(
        val modelUsed: String,
        val text: String,
        val latencyMs: Long
    )

    private data class HttpFailureSnapshot(
        val modelName: String,
        val statusCode: Int,
        val body: String
    )

    suspend fun generateContent(
        apiKey: String,
        promptText: String,
        inlinePart: DirectGeminiInlinePart? = null,
        responseSchema: JSONObject? = null,
        timeoutMs: Int = 60_000
    ): Result<String> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return Result.failure(IllegalStateException("Personal Gemini API key is empty."))
        }
        val requestBody = buildRequestJson(
            promptText = promptText,
            inlinePart = inlinePart,
            responseSchema = responseSchema
        ).toString()

        return executeWithResilientFallback(
            apiKey = cleanKey,
            requestBody = requestBody,
            timeoutMs = timeoutMs,
            baseBackoffMs = 850L,
            emptyResponseFallback = null
        ).map { it.text }
    }

    suspend fun ping(
        apiKey: String,
        timeoutMs: Int = 15_000
    ): Result<DirectGeminiPingResult> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return Result.failure(IllegalStateException("Personal Gemini API key is empty."))
        }
        val requestBody = buildRequestJson(
            promptText = "Reply with two words: AI Ready",
            inlinePart = null,
            responseSchema = null
        ).toString()

        return executeWithResilientFallback(
            apiKey = cleanKey,
            requestBody = requestBody,
            timeoutMs = timeoutMs,
            baseBackoffMs = 600L,
            emptyResponseFallback = "AI Ready"
        ).map { ok ->
            DirectGeminiPingResult(
                modelUsed = ok.modelUsed,
                latencyMs = ok.latencyMs,
                sampleText = ok.text
            )
        }
    }

    private suspend fun executeWithResilientFallback(
        apiKey: String,
        requestBody: String,
        timeoutMs: Int,
        baseBackoffMs: Long,
        emptyResponseFallback: String?
    ): Result<ExecutionSuccess> {
        val attemptedModels = mutableListOf<String>()
        var lastHttpFailure: HttpFailureSnapshot? = null
        var lastException: Exception? = null
        var shouldTryLiveDiscovery = false

        suspend fun tryModel(modelName: String, maxAttempts: Int): Result<ExecutionSuccess>? {
            attemptedModels += modelName
            val endpoint = "$BASE_URL/$modelName:generateContent"

            for (attempt in 0 until maxAttempts) {
                val startedAt = System.currentTimeMillis()
                try {
                    val response = httpExecutor(endpoint, apiKey, requestBody, timeoutMs)
                    if (response.statusCode in 200..299) {
                        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                        val extracted = extractCandidateText(response.body).trim()
                        val resolvedText = if (extracted.isNotBlank()) {
                            extracted
                        } else {
                            emptyResponseFallback.orEmpty()
                        }
                        if (resolvedText.isNotBlank()) {
                            return Result.success(
                                ExecutionSuccess(
                                    modelUsed = modelName,
                                    text = resolvedText,
                                    latencyMs = elapsed
                                )
                            )
                        }
                        lastException = IllegalStateException("Gemini returned an empty response from $modelName.")
                        return null
                    }

                    val code = response.statusCode
                    lastHttpFailure = HttpFailureSnapshot(modelName, code, response.body)

                    if (code in RETRYABLE_HTTP_CODES && attempt < maxAttempts - 1) {
                        val jitterMs = Random.nextLong(100L, 300L)
                        Log.w(
                            DIRECT_GEMINI_TAG,
                            "Model $modelName returned HTTP $code; retrying once after ${baseBackoffMs + jitterMs}ms."
                        )
                        delayFn(baseBackoffMs + jitterMs)
                        continue
                    }

                    if (code in FALLBACK_HTTP_CODES) {
                        shouldTryLiveDiscovery = true
                        Log.w(
                            DIRECT_GEMINI_TAG,
                            "Model $modelName unavailable (HTTP $code); attempting fallback model."
                        )
                        return null
                    }

                    // Non-retryable client/auth errors (400, 401, 403, etc.) fail fast with clear diagnostics
                    return Result.failure(
                        IllegalStateException(
                            mapHttpError(
                                statusCode = code,
                                responseBody = response.body,
                                modelName = modelName,
                                attemptedModels = attemptedModels,
                                apiKeyToRedact = apiKey
                            )
                        )
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    lastException = e
                    Log.e(
                        DIRECT_GEMINI_TAG,
                        "Direct Gemini call failed on $modelName (attempt ${attempt + 1}): ${e.javaClass.simpleName}"
                    )
                    if (attempt < maxAttempts - 1) {
                        delayFn(400L)
                    }
                }
            }
            return null
        }

        // Phase 1 & 2: Primary (gemini-3.8-flash) then Fallback (gemini-3.5-flash-lite)
        val staticModels = listOf(DEFAULT_MODEL, FALLBACK_MODEL)
        for (modelName in staticModels) {
            val outcome = tryModel(modelName, maxAttempts = MAX_ATTEMPTS_PER_MODEL)
            if (outcome != null) return outcome
        }

        // Phase 3: Live Model Discovery safety net (only if both static models hit 404/429/5xx)
        if (shouldTryLiveDiscovery) {
            val discoveredModels = discoverAlternativeFlashModels(
                apiKey = apiKey,
                alreadyTried = attemptedModels.toSet(),
                timeoutMs = minOf(timeoutMs, 10_000)
            )
            for (discoveredModel in discoveredModels.take(MAX_DISCOVERED_MODELS_TO_TRY)) {
                val outcome = tryModel(discoveredModel, maxAttempts = 1)
                if (outcome != null) return outcome
            }
        }

        // Surface clearest available diagnostic
        lastHttpFailure?.let { failure ->
            return Result.failure(
                IllegalStateException(
                    mapHttpError(
                        statusCode = failure.statusCode,
                        responseBody = failure.body,
                        modelName = failure.modelName,
                        attemptedModels = attemptedModels,
                        apiKeyToRedact = apiKey
                    )
                )
            )
        }

        val errMsg = sanitizeErrorText(lastException?.localizedMessage.orEmpty(), apiKey)
        return Result.failure(
            IllegalStateException(
                if (errMsg.isNotBlank()) "Unable to reach Gemini API: $errMsg"
                else "Unable to reach Gemini API. Check mobile/Wi-Fi internet and retry.",
                lastException
            )
        )
    }

    private suspend fun discoverAlternativeFlashModels(
        apiKey: String,
        alreadyTried: Set<String>,
        timeoutMs: Int
    ): List<String> {
        return try {
            val listResponse = httpGetExecutor(BASE_URL, apiKey, timeoutMs)
            if (listResponse.statusCode !in 200..299 || listResponse.body.isBlank()) {
                emptyList()
            } else {
                selectEligibleDiscoveredModels(listResponse.body, alreadyTried)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(DIRECT_GEMINI_TAG, "Live model discovery skipped: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val FALLBACK_MODEL = "gemini-3.5-flash-lite"

        private const val MAX_ATTEMPTS_PER_MODEL = 2
        private const val MAX_DISCOVERED_MODELS_TO_TRY = 2
        private val RETRYABLE_HTTP_CODES = setOf(429, 500, 502, 503, 504)
        private val FALLBACK_HTTP_CODES = setOf(404, 429, 500, 502, 503, 504)
        private val EXCLUDED_DISCOVERY_TOKENS = listOf(
            "embedding", "tts", "image", "imagen", "veo", "aqa",
            "robotics", "computer-use", "audio", "live", "learnlm", "gemma"
        )

        suspend fun encodeBitmapAsJpegPart(bitmap: Bitmap): DirectGeminiInlinePart =
            withContext(Dispatchers.IO) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                DirectGeminiInlinePart(mimeType = "image/jpeg", bytes = out.toByteArray())
            }

        /**
         * Parses `GET /v1beta/models` output and ranks eligible Gemini 3+ Flash/general text models
         * that support `generateContent` and have not yet been tried.
         */
        internal fun selectEligibleDiscoveredModels(
            modelsListJson: String,
            alreadyTried: Set<String>
        ): List<String> {
            val root = runCatching { JSONObject(modelsListJson) }.getOrNull() ?: return emptyList()
            val modelsArray = root.optJSONArray("models") ?: return emptyList()
            val candidates = mutableListOf<String>()
            for (i in 0 until modelsArray.length()) {
                val obj = modelsArray.optJSONObject(i) ?: continue
                val rawName = obj.optString("name", "").trim()
                val slug = rawName.removePrefix("models/").trim()
                if (slug.isEmpty() || slug in alreadyTried) continue

                val lower = slug.lowercase()
                val isGemini3OrNewer = lower.startsWith("gemini-3") || lower.startsWith("gemini-4")
                if (!isGemini3OrNewer) continue
                if (EXCLUDED_DISCOVERY_TOKENS.any { token -> lower.contains(token) }) continue

                val methods = obj.optJSONArray("supportedGenerationMethods")
                var supportsGenerate = false
                if (methods != null) {
                    for (m in 0 until methods.length()) {
                        if (methods.optString(m) == "generateContent") {
                            supportsGenerate = true
                            break
                        }
                    }
                }
                if (supportsGenerate) {
                    candidates += slug
                }
            }
            return candidates.distinct().sortedBy { slug ->
                val lower = slug.lowercase()
                when {
                    lower.contains("flash-lite") -> 0
                    lower.contains("flash") -> 1
                    else -> 2
                }
            }
        }

        internal fun buildRequestJson(
            promptText: String,
            inlinePart: DirectGeminiInlinePart? = null,
            responseSchema: JSONObject? = null
        ): JSONObject {
            val parts = JSONArray()
            if (inlinePart != null && inlinePart.bytes.isNotEmpty()) {
                val base64Data = Base64.encodeToString(inlinePart.bytes, Base64.NO_WRAP)
                parts.put(
                    JSONObject().put(
                        "inline_data",
                        JSONObject()
                            .put("mime_type", inlinePart.mimeType)
                            .put("data", base64Data)
                    )
                )
            }
            parts.put(JSONObject().put("text", promptText))

            val contentObj = JSONObject()
                .put("role", "user")
                .put("parts", parts)

            val root = JSONObject()
                .put("contents", JSONArray().put(contentObj))

            if (responseSchema != null) {
                root.put(
                    "generationConfig",
                    JSONObject()
                        .put("responseMimeType", "application/json")
                        .put("responseSchema", responseSchema)
                )
            }
            return root
        }

        internal fun extractCandidateText(responseBody: String): String {
            if (responseBody.isBlank()) return ""
            val root = JSONObject(responseBody)
            val candidates = root.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val first = candidates.optJSONObject(0) ?: return ""
            val content = first.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val text = parts.optJSONObject(i)?.optString("text", "").orEmpty()
                if (text.isNotEmpty()) {
                    sb.append(text)
                }
            }
            return sb.toString().trim()
        }

        internal fun sanitizeErrorText(rawText: String, apiKeyToRedact: String = ""): String {
            var cleaned = rawText.replace(Regex("\\s+"), " ").trim()
            if (apiKeyToRedact.isNotBlank()) {
                cleaned = cleaned.replace(apiKeyToRedact, "[redacted-key]")
            }
            if (cleaned.length > 220) {
                cleaned = cleaned.take(217).trimEnd() + "..."
            }
            return cleaned
        }

        internal fun mapHttpError(
            statusCode: Int,
            responseBody: String,
            modelName: String,
            attemptedModels: List<String> = listOf(modelName),
            apiKeyToRedact: String = ""
        ): String {
            val rawApiMessage = runCatching {
                JSONObject(responseBody).optJSONObject("error")?.optString("message", "").orEmpty().trim()
            }.getOrDefault("")
            val apiMessage = sanitizeErrorText(rawApiMessage, apiKeyToRedact)
            val detailSuffix = if (apiMessage.isNotBlank()) " Details: $apiMessage" else ""
            val distinctModels = attemptedModels.distinct()
            val triedSuffix = if (distinctModels.size > 1) {
                " (tried ${distinctModels.joinToString(", ")})"
            } else {
                ""
            }

            return when (statusCode) {
                400 -> {
                    if (apiMessage.contains("API key", ignoreCase = true) ||
                        responseBody.contains("API_KEY_INVALID", ignoreCase = true)
                    ) {
                        "Your Personal Gemini API key is invalid (HTTP 400). Copy a fresh key from aistudio.google.com/apikey."
                    } else {
                        "Gemini rejected the request (HTTP 400).$detailSuffix"
                    }
                }
                401, 403 ->
                    "Your Personal Gemini API key was denied (HTTP $statusCode).$detailSuffix Check key restrictions or generate a new key at aistudio.google.com/apikey."
                404 ->
                    "Model $modelName is unavailable for this API key (HTTP 404)$triedSuffix.$detailSuffix Please retry or verify your Google AI Studio key."
                429 ->
                    "Gemini rate limit or free quota reached for your personal key (HTTP 429)$triedSuffix.$detailSuffix Wait a moment and retry."
                in 500..599 ->
                    "Gemini service is temporarily busy (HTTP $statusCode)$triedSuffix.$detailSuffix Please retry in a few seconds."
                else ->
                    "Gemini API error (HTTP $statusCode)$triedSuffix.$detailSuffix"
            }
        }

        private suspend fun executeHttpsPost(
            endpointUrl: String,
            apiKey: String,
            bodyJson: String,
            timeoutMs: Int
        ): DirectGeminiHttpResponse = withContext(Dispatchers.IO) {
            val conn = (URL(endpointUrl).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = minOf(timeoutMs, 15_000)
                readTimeout = timeoutMs
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("x-goog-api-key", apiKey)
            }
            try {
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(bodyJson)
                    writer.flush()
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                DirectGeminiHttpResponse(statusCode = code, body = body)
            } finally {
                conn.disconnect()
            }
        }

        private suspend fun executeHttpsGet(
            endpointUrl: String,
            apiKey: String,
            timeoutMs: Int
        ): DirectGeminiHttpResponse = withContext(Dispatchers.IO) {
            val conn = (URL(endpointUrl).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = minOf(timeoutMs, 10_000)
                readTimeout = timeoutMs
                useCaches = false
                setRequestProperty("x-goog-api-key", apiKey)
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                DirectGeminiHttpResponse(statusCode = code, body = body)
            } finally {
                conn.disconnect()
            }
        }

        // Structured JSON schemas matching LifeWeaveAiService Firebase.ai Schema.obj definitions

        private fun schemaString(desc: String? = null): JSONObject = JSONObject().apply {
            put("type", "STRING")
            if (!desc.isNullOrBlank()) put("description", desc)
        }

        private fun schemaInt(desc: String? = null): JSONObject = JSONObject().apply {
            put("type", "INTEGER")
            if (!desc.isNullOrBlank()) put("description", desc)
        }

        private fun schemaEnum(values: List<String>, desc: String? = null): JSONObject = JSONObject().apply {
            put("type", "STRING")
            put("enum", JSONArray(values))
            if (!desc.isNullOrBlank()) put("description", desc)
        }

        private fun schemaArray(items: JSONObject, desc: String? = null): JSONObject = JSONObject().apply {
            put("type", "ARRAY")
            put("items", items)
            if (!desc.isNullOrBlank()) put("description", desc)
        }

        private fun schemaObj(
            properties: Map<String, JSONObject>,
            optionalProperties: Set<String> = emptySet(),
            desc: String? = null
        ): JSONObject {
            val propsJson = JSONObject()
            val required = JSONArray()
            properties.forEach { (k, v) ->
                propsJson.put(k, v)
                if (k !in optionalProperties) required.put(k)
            }
            return JSONObject().apply {
                put("type", "OBJECT")
                put("properties", propsJson)
                if (required.length() > 0) put("required", required)
                if (!desc.isNullOrBlank()) put("description", desc)
            }
        }

        fun assistantResponseSchema(): JSONObject = schemaObj(
            properties = mapOf(
                "assistantReply" to schemaString("Warm, concise wellness reply or app guidance in the user's selected language."),
                "actions" to schemaArray(
                    items = schemaObj(
                        properties = mapOf(
                            "actionType" to schemaEnum(AiActionType.entries.map { it.name }),
                            "titleOrName" to schemaString(),
                            "amountOrValue" to schemaInt(),
                            "secondaryText" to schemaString(),
                            "calories" to schemaInt(),
                            "dosesPerDay" to schemaInt(),
                            "intervalHours" to schemaInt(),
                            "mealRelation" to schemaString()
                        ),
                        optionalProperties = setOf("calories", "dosesPerDay", "intervalHours", "mealRelation")
                    )
                )
            )
        )

        fun prescriptionResponseSchema(): JSONObject = schemaObj(
            properties = mapOf(
                "summary" to schemaString(),
                "doctorName" to schemaString(),
                "specialty" to schemaString(),
                "clinicOrHospital" to schemaString(),
                "followUpDaysFromToday" to schemaInt(),
                "followUpNotes" to schemaString(),
                "medicines" to schemaArray(
                    items = schemaObj(
                        properties = mapOf(
                            "name" to schemaString(),
                            "dosage" to schemaString(),
                            "dosesPerDay" to schemaInt(),
                            "intervalHours" to schemaInt(),
                            "scheduleTime24h" to schemaString(),
                            "mealRelation" to schemaString(),
                            "notes" to schemaString()
                        )
                    )
                )
            )
        )

        fun healthIntelligenceResponseSchema(): JSONObject = schemaObj(
            properties = mapOf(
                "headline" to schemaString(),
                "recordedFactsSummary" to schemaString(),
                "observedChanges" to schemaArray(schemaString()),
                "personalizedSuggestions" to schemaArray(schemaString()),
                "dailyPlanHighlights" to schemaArray(schemaString()),
                "uncertaintyAndCoverageNote" to schemaString(),
                "disclaimer" to schemaString()
            )
        )

        fun reportExplanationResponseSchema(): JSONObject = schemaObj(
            properties = mapOf(
                "plainLanguageSummary" to schemaString(),
                "keyObservations" to schemaArray(schemaString()),
                "questionsForDoctor" to schemaArray(schemaString()),
                "disclaimer" to schemaString()
            )
        )

        fun indianNutritionResponseSchema(): JSONObject = schemaObj(
            properties = mapOf(
                "planTitle" to schemaString(),
                "regionalNotes" to schemaString(),
                "groceryHighlights" to schemaArray(schemaString()),
                "meals" to schemaArray(
                    items = schemaObj(
                        properties = mapOf(
                            "mealType" to schemaEnum(listOf("Breakfast", "Snack", "Lunch", "Dinner")),
                            "slotLabel" to schemaString(),
                            "dishName" to schemaString(),
                            "portionLabel" to schemaString(),
                            "calories" to schemaInt(),
                            "proteinGrams" to schemaInt(),
                            "carbsGrams" to schemaInt(),
                            "fatGrams" to schemaInt(),
                            "prepTip" to schemaString()
                        )
                    )
                ),
                "disclaimer" to schemaString()
            )
        )
    }
}
