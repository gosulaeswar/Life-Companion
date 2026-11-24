package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStreamWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection
import kotlin.math.min
import kotlin.random.Random

private const val GROK_API_TAG = "GrokApiClient"

data class GrokInlinePart(
    val mimeType: String,
    val bytes: ByteArray
)

data class GrokPingResult(
    val modelUsed: String,
    val latencyMs: Long,
    val sampleText: String
)

data class GrokHttpResponse(
    val statusCode: Int,
    val body: String
)

/**
 * Direct HTTPS client for the official xAI Grok API (`https://api.x.ai/v1/chat/completions`).
 *
 * Security & Resilience:
 * - Sends the API key strictly in the `Authorization: Bearer <key>` HTTPS header (never in URLs or logs).
 * - Redacts any accidental key echo from surfaced server errors.
 * - Retries transient HTTP 429/500/502/503/504 errors with jittered backoff, fails over cleanly across
 *   text/vision Grok models, falls back from strict `json_schema` to `json_object` if needed, and performs
 *   live `GET /v1/models` discovery when default models are unavailable on an account.
 */
class GrokApiClient(
    private val httpGetExecutor: suspend (
        endpointUrl: String,
        apiKey: String,
        timeoutMs: Int
    ) -> GrokHttpResponse = ::executeHttpsGet,
    private val delayFn: suspend (Long) -> Unit = { ms -> delay(ms) },
    private val httpExecutor: suspend (
        endpointUrl: String,
        apiKey: String,
        bodyJson: String,
        timeoutMs: Int
    ) -> GrokHttpResponse = ::executeHttpsPost
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
        inlinePart: GrokInlinePart? = null,
        schemaName: String? = null,
        responseSchema: JSONObject? = null,
        timeoutMs: Int = 60_000
    ): Result<String> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return Result.failure(
                IllegalStateException("xAI Grok API key is missing. Add XAI_API_KEY in Secrets or enter your personal Grok key in Settings.")
            )
        }
        val isVision = inlinePart != null && inlinePart.bytes.isNotEmpty() &&
            inlinePart.mimeType.startsWith("image/", ignoreCase = true)

        return executeWithResilientFallback(
            apiKey = cleanKey,
            promptText = promptText,
            inlinePart = inlinePart,
            schemaName = schemaName,
            responseSchema = responseSchema,
            isVision = isVision,
            timeoutMs = timeoutMs,
            baseBackoffMs = 800L,
            emptyResponseFallback = null
        ).map { it.text }
    }

    suspend fun ping(
        apiKey: String,
        timeoutMs: Int = 15_000
    ): Result<GrokPingResult> {
        val cleanKey = apiKey.trim()
        if (cleanKey.isEmpty()) {
            return Result.failure(
                IllegalStateException("xAI Grok API key is missing. Enter your Grok key in Settings or configure XAI_API_KEY.")
            )
        }
        return executeWithResilientFallback(
            apiKey = cleanKey,
            promptText = "Reply with two words: Grok Ready",
            inlinePart = null,
            schemaName = null,
            responseSchema = null,
            isVision = false,
            timeoutMs = timeoutMs,
            baseBackoffMs = 550L,
            emptyResponseFallback = "Grok Ready"
        ).map { ok ->
            GrokPingResult(
                modelUsed = ok.modelUsed,
                latencyMs = ok.latencyMs,
                sampleText = ok.text
            )
        }
    }

    private suspend fun executeWithResilientFallback(
        apiKey: String,
        promptText: String,
        inlinePart: GrokInlinePart?,
        schemaName: String?,
        responseSchema: JSONObject?,
        isVision: Boolean,
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
            var useStrictJsonSchema = responseSchema != null

            for (attempt in 0 until maxAttempts) {
                val requestBody = buildChatCompletionRequestJson(
                    modelName = modelName,
                    promptText = promptText,
                    inlinePart = inlinePart,
                    schemaName = schemaName,
                    responseSchema = responseSchema,
                    useStrictJsonSchema = useStrictJsonSchema
                ).toString()

                val startedAt = System.currentTimeMillis()
                try {
                    val response = httpExecutor(CHAT_COMPLETIONS_URL, apiKey, requestBody, timeoutMs)
                    if (response.statusCode in 200..299) {
                        val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                        val extracted = extractAssistantMessageText(response.body)
                        val normalized = if (responseSchema != null) {
                            normalizeJsonPayload(extracted)
                        } else {
                            extracted.trim()
                        }
                        val resolvedText = if (normalized.isNotBlank()) {
                            normalized
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
                        lastException = IllegalStateException("Grok returned an empty response from $modelName.")
                        return null
                    }

                    val code = response.statusCode
                    lastHttpFailure = HttpFailureSnapshot(modelName, code, response.body)

                    // If a Grok model rejects strict json_schema format, automatically downgrade once to json_object
                    if (code == 400 && useStrictJsonSchema &&
                        (response.body.contains("response_format", ignoreCase = true) ||
                            response.body.contains("json_schema", ignoreCase = true) ||
                            response.body.contains("structured", ignoreCase = true))
                    ) {
                        Log.w(GROK_API_TAG, "Model $modelName rejected strict json_schema; retrying with json_object.")
                        useStrictJsonSchema = false
                        continue
                    }

                    // If a model ID is unknown/deprecated on this account (400 model not found or 404), fall back
                    val isModelUnavailable400 = code == 400 && (
                        response.body.contains("model", ignoreCase = true) &&
                            (response.body.contains("not found", ignoreCase = true) ||
                                response.body.contains("does not exist", ignoreCase = true) ||
                                response.body.contains("not support", ignoreCase = true))
                        )
                    if (isModelUnavailable400 || code == 404) {
                        shouldTryLiveDiscovery = true
                        Log.w(GROK_API_TAG, "Grok model $modelName unavailable (HTTP $code); trying fallback.")
                        return null
                    }

                    if (code in RETRYABLE_HTTP_CODES && attempt < maxAttempts - 1) {
                        val jitterMs = Random.nextLong(100L, 300L)
                        Log.w(
                            GROK_API_TAG,
                            "Grok model $modelName returned HTTP $code; retrying once after ${baseBackoffMs + jitterMs}ms."
                        )
                        delayFn(baseBackoffMs + jitterMs)
                        continue
                    }

                    if (code in FALLBACK_HTTP_CODES) {
                        shouldTryLiveDiscovery = true
                        Log.w(GROK_API_TAG, "Grok model $modelName busy/rate-limited (HTTP $code); failing over.")
                        return null
                    }

                    // Auth/billing/validation failures fail fast with actionable guidance
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
                    Log.e(GROK_API_TAG, "Grok API call failed on $modelName (attempt ${attempt + 1}): ${e.javaClass.simpleName}")
                    if (attempt < maxAttempts - 1) {
                        delayFn(400L)
                    }
                }
            }
            return null
        }

        val initialModels = if (isVision) {
            listOf(DEFAULT_VISION_MODEL, FALLBACK_VISION_MODEL, DEFAULT_TEXT_MODEL)
        } else {
            listOf(DEFAULT_TEXT_MODEL, FALLBACK_TEXT_MODEL)
        }

        for (modelName in initialModels) {
            val outcome = tryModel(modelName, maxAttempts = MAX_ATTEMPTS_PER_MODEL)
            if (outcome != null) return outcome
        }

        if (shouldTryLiveDiscovery) {
            val discoveredModels = discoverAvailableGrokModels(
                apiKey = apiKey,
                alreadyTried = attemptedModels.toSet(),
                preferVision = isVision,
                timeoutMs = minOf(timeoutMs, 10_000)
            )
            for (discoveredModel in discoveredModels.take(MAX_DISCOVERED_MODELS_TO_TRY)) {
                val outcome = tryModel(discoveredModel, maxAttempts = 1)
                if (outcome != null) return outcome
            }
        }

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
                if (errMsg.isNotBlank()) "Unable to reach xAI Grok API: $errMsg"
                else "Unable to reach xAI Grok API. Check mobile/Wi-Fi internet and retry.",
                lastException
            )
        )
    }

    private suspend fun discoverAvailableGrokModels(
        apiKey: String,
        alreadyTried: Set<String>,
        preferVision: Boolean,
        timeoutMs: Int
    ): List<String> {
        return try {
            val response = httpGetExecutor(MODELS_URL, apiKey, timeoutMs)
            if (response.statusCode !in 200..299 || response.body.isBlank()) {
                emptyList()
            } else {
                selectEligibleDiscoveredModels(response.body, alreadyTried, preferVision)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(GROK_API_TAG, "Live xAI model discovery skipped: ${e.javaClass.simpleName}")
            emptyList()
        }
    }

    companion object {
        const val CHAT_COMPLETIONS_URL = "https://api.x.ai/v1/chat/completions"
        const val MODELS_URL = "https://api.x.ai/v1/models"
        const val CONSOLE_KEY_URL = "https://console.x.ai"

        const val DEFAULT_TEXT_MODEL = "grok-3"
        const val FALLBACK_TEXT_MODEL = "grok-3-mini"
        const val DEFAULT_VISION_MODEL = "grok-2-vision-1212"
        const val FALLBACK_VISION_MODEL = "grok-4"

        private const val MAX_ATTEMPTS_PER_MODEL = 2
        private const val MAX_DISCOVERED_MODELS_TO_TRY = 2
        private val RETRYABLE_HTTP_CODES = setOf(429, 500, 502, 503, 504)
        private val FALLBACK_HTTP_CODES = setOf(404, 429, 500, 502, 503, 504)
        private val EXCLUDED_DISCOVERY_TOKENS = listOf("imagine", "image-gen", "embed", "audio", "tts")

        suspend fun encodeBitmapAsJpegPart(bitmap: Bitmap): GrokInlinePart =
            withContext(Dispatchers.IO) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                GrokInlinePart(mimeType = "image/jpeg", bytes = out.toByteArray())
            }

        /**
         * Renders the first page of a PDF document to a crisp JPEG image using Android's built-in
         * [PdfRenderer] so Grok Vision models can inspect scanned prescriptions and lab reports.
         */
        suspend fun renderPdfFirstPageAsJpegPart(
            context: Context?,
            pdfBytes: ByteArray,
            maxDimension: Int = 1280
        ): GrokInlinePart? = withContext(Dispatchers.IO) {
            val cacheDir = context?.cacheDir ?: return@withContext null
            if (pdfBytes.isEmpty()) return@withContext null
            val tempFile = File.createTempFile("lw_grok_pdf_", ".pdf", cacheDir)
            try {
                tempFile.writeBytes(pdfBytes)
                ParcelFileDescriptor.open(tempFile, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        if (renderer.pageCount <= 0) return@withContext null
                        renderer.openPage(0).use { page ->
                            val scale = min(
                                maxDimension.toFloat() / page.width.coerceAtLeast(1),
                                maxDimension.toFloat() / page.height.coerceAtLeast(1)
                            ).coerceIn(0.5f, 2.0f)
                            val targetW = (page.width * scale).toInt().coerceAtLeast(1)
                            val targetH = (page.height * scale).toInt().coerceAtLeast(1)
                            val bitmap = Bitmap.createBitmap(targetW, targetH, Bitmap.Config.ARGB_8888)
                            try {
                                val canvas = Canvas(bitmap)
                                canvas.drawColor(Color.WHITE)
                                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                                val out = ByteArrayOutputStream()
                                bitmap.compress(Bitmap.CompressFormat.JPEG, 86, out)
                                GrokInlinePart(mimeType = "image/jpeg", bytes = out.toByteArray())
                            } finally {
                                bitmap.recycle()
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.w(GROK_API_TAG, "Unable to rasterize PDF page for Grok Vision: ${e.javaClass.simpleName}")
                null
            } finally {
                runCatching { tempFile.delete() }
            }
        }

        internal fun selectEligibleDiscoveredModels(
            modelsListJson: String,
            alreadyTried: Set<String>,
            preferVision: Boolean
        ): List<String> {
            val root = runCatching { JSONObject(modelsListJson) }.getOrNull() ?: return emptyList()
            val dataArray = root.optJSONArray("data") ?: return emptyList()
            val candidates = mutableListOf<String>()
            for (i in 0 until dataArray.length()) {
                val id = dataArray.optJSONObject(i)?.optString("id", "").orEmpty().trim()
                if (id.isEmpty() || id in alreadyTried) continue
                val lower = id.lowercase()
                if (!lower.startsWith("grok")) continue
                if (EXCLUDED_DISCOVERY_TOKENS.any { lower.contains(it) }) continue
                candidates += id
            }
            return candidates.distinct().sortedBy { id ->
                val lower = id.lowercase()
                if (preferVision) {
                    when {
                        lower.contains("vision") -> 0
                        lower.startsWith("grok-4") -> 1
                        lower.startsWith("grok-3") -> 2
                        else -> 3
                    }
                } else {
                    when {
                        lower.startsWith("grok-4") && lower.contains("fast") -> 0
                        lower.startsWith("grok-3-mini") -> 1
                        lower.startsWith("grok-3") -> 2
                        lower.startsWith("grok-4") -> 3
                        else -> 4
                    }
                }
            }
        }

        internal fun buildChatCompletionRequestJson(
            modelName: String,
            promptText: String,
            inlinePart: GrokInlinePart? = null,
            schemaName: String? = null,
            responseSchema: JSONObject? = null,
            useStrictJsonSchema: Boolean = responseSchema != null
        ): JSONObject {
            val messages = JSONArray()

            if (responseSchema != null) {
                val systemInstructions = buildString {
                    append("You are LifeWeave's privacy-first wellness assistant powered by xAI Grok. ")
                    append("Return ONLY a valid raw JSON object matching the requested schema with no markdown code fences or commentary.")
                    if (!useStrictJsonSchema) {
                        append("\nRequired JSON schema:\n")
                        append(responseSchema.toString())
                    }
                }
                messages.put(
                    JSONObject()
                        .put("role", "system")
                        .put("content", systemInstructions)
                )
            }

            val userMessage = JSONObject().put("role", "user")
            if (inlinePart != null && inlinePart.bytes.isNotEmpty() &&
                inlinePart.mimeType.startsWith("image/", ignoreCase = true)
            ) {
                val base64Data = Base64.encodeToString(inlinePart.bytes, Base64.NO_WRAP)
                val dataUrl = "data:${inlinePart.mimeType};base64,$base64Data"
                val contentArray = JSONArray()
                    .put(JSONObject().put("type", "text").put("text", promptText))
                    .put(
                        JSONObject()
                            .put("type", "image_url")
                            .put(
                                "image_url",
                                JSONObject()
                                    .put("url", dataUrl)
                                    .put("detail", "high")
                            )
                    )
                userMessage.put("content", contentArray)
            } else {
                userMessage.put("content", promptText)
            }
            messages.put(userMessage)

            val root = JSONObject()
                .put("model", modelName)
                .put("messages", messages)
                .put("temperature", 0.2)

            if (responseSchema != null) {
                if (useStrictJsonSchema) {
                    root.put(
                        "response_format",
                        JSONObject()
                            .put("type", "json_schema")
                            .put(
                                "json_schema",
                                JSONObject()
                                    .put("name", schemaName?.ifBlank { "lifeweave_response" } ?: "lifeweave_response")
                                    .put("strict", true)
                                    .put("schema", responseSchema)
                            )
                    )
                } else {
                    root.put("response_format", JSONObject().put("type", "json_object"))
                }
            }
            return root
        }

        internal fun extractAssistantMessageText(responseBody: String): String {
            if (responseBody.isBlank()) return ""
            val root = JSONObject(responseBody)
            val choices = root.optJSONArray("choices") ?: return ""
            if (choices.length() == 0) return ""
            val message = choices.optJSONObject(0)?.optJSONObject("message") ?: return ""
            val contentObj = message.opt("content") ?: return ""
            return when (contentObj) {
                is String -> contentObj.trim()
                is JSONArray -> {
                    val sb = StringBuilder()
                    for (i in 0 until contentObj.length()) {
                        val part = contentObj.optJSONObject(i)
                        val text = part?.optString("text", "").orEmpty()
                        if (text.isNotEmpty()) sb.append(text)
                    }
                    sb.toString().trim()
                }
                else -> contentObj.toString().trim()
            }
        }

        internal fun normalizeJsonPayload(raw: String): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return ""
            if (trimmed.startsWith("{") && trimmed.endsWith("}")) return trimmed
            val fenceCleaned = trimmed
                .removePrefix("```json")
                .removePrefix("```JSON")
                .removePrefix("```")
                .removeSuffix("```")
                .trim()
            val start = fenceCleaned.indexOf('{')
            val end = fenceCleaned.lastIndexOf('}')
            return if (start >= 0 && end > start) {
                fenceCleaned.substring(start, end + 1).trim()
            } else {
                fenceCleaned
            }
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
                val root = JSONObject(responseBody)
                val errField = root.opt("error")
                when (errField) {
                    is JSONObject -> errField.optString("message", "").trim()
                    is String -> errField.trim()
                    else -> root.optString("message", "").trim()
                }
            }.getOrDefault("")
            val apiMessage = sanitizeErrorText(rawApiMessage, apiKeyToRedact)
            val detailSuffix = if (apiMessage.isNotBlank()) " Details: $apiMessage" else ""
            val distinctModels = attemptedModels.distinct()
            val triedSuffix = if (distinctModels.size > 1) {
                " (tried ${distinctModels.joinToString(", ")})"
            } else {
                ""
            }

            val isInvalidApiKey400 = statusCode == 400 && (
                apiMessage.contains("incorrect api key", ignoreCase = true) ||
                    apiMessage.contains("invalid api key", ignoreCase = true) ||
                    apiMessage.contains("api key", ignoreCase = true)
                )

            return when {
                isInvalidApiKey400 || statusCode == 401 || statusCode == 403 ->
                    "Your xAI Grok API key was rejected (HTTP $statusCode).$detailSuffix Open Settings -> xAI Grok Connection and paste a valid xai-... key from console.x.ai (or set XAI_API_KEY in AI Studio Secrets)."
                statusCode == 400 ->
                    "xAI Grok rejected the request (HTTP 400).$detailSuffix"
                statusCode == 404 ->
                    "Grok model $modelName is unavailable for this API key (HTTP 404)$triedSuffix.$detailSuffix Check model access at console.x.ai."
                statusCode == 429 ->
                    "xAI Grok rate limit or quota reached (HTTP 429)$triedSuffix.$detailSuffix Wait a moment or check credits at console.x.ai."
                statusCode in 500..599 ->
                    "xAI Grok service is temporarily busy (HTTP $statusCode)$triedSuffix.$detailSuffix Please retry in a few seconds."
                else ->
                    "xAI Grok API error (HTTP $statusCode)$triedSuffix.$detailSuffix"
            }
        }

        private suspend fun executeHttpsPost(
            endpointUrl: String,
            apiKey: String,
            bodyJson: String,
            timeoutMs: Int
        ): GrokHttpResponse = withContext(Dispatchers.IO) {
            val conn = (URL(endpointUrl).openConnection() as HttpsURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = minOf(timeoutMs, 15_000)
                readTimeout = timeoutMs
                doOutput = true
                useCaches = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            try {
                OutputStreamWriter(conn.outputStream, StandardCharsets.UTF_8).use { writer ->
                    writer.write(bodyJson)
                    writer.flush()
                }
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                GrokHttpResponse(statusCode = code, body = body)
            } finally {
                conn.disconnect()
            }
        }

        private suspend fun executeHttpsGet(
            endpointUrl: String,
            apiKey: String,
            timeoutMs: Int
        ): GrokHttpResponse = withContext(Dispatchers.IO) {
            val conn = (URL(endpointUrl).openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = minOf(timeoutMs, 10_000)
                readTimeout = timeoutMs
                useCaches = false
                setRequestProperty("Authorization", "Bearer $apiKey")
            }
            try {
                val code = conn.responseCode
                val stream = if (code in 200..299) conn.inputStream else conn.errorStream
                val body = stream?.bufferedReader(StandardCharsets.UTF_8)?.use { it.readText() }.orEmpty()
                GrokHttpResponse(statusCode = code, body = body)
            } finally {
                conn.disconnect()
            }
        }

        // OpenAI / xAI JSON Schema (Draft 7 strict-compatible with additionalProperties = false)

        private fun schemaString(desc: String? = null): JSONObject = JSONObject().apply {
            put("type", "string")
            if (!desc.isNullOrBlank()) put("description", desc)
        }

        private fun schemaInt(desc: String? = null): JSONObject = JSONObject().apply {
            put("type", "integer")
            if (!desc.isNullOrBlank()) put("description", desc)
        }

        private fun schemaEnum(values: List<String>, desc: String? = null): JSONObject = JSONObject().apply {
            put("type", "string")
            put("enum", JSONArray(values))
            if (!desc.isNullOrBlank()) put("description", desc)
        }

        private fun schemaArray(items: JSONObject, desc: String? = null): JSONObject = JSONObject().apply {
            put("type", "array")
            put("items", items)
            if (!desc.isNullOrBlank()) put("description", desc)
        }

        private fun schemaObj(
            properties: Map<String, JSONObject>,
            desc: String? = null
        ): JSONObject {
            val propsJson = JSONObject()
            val required = JSONArray()
            properties.forEach { (k, v) ->
                propsJson.put(k, v)
                required.put(k)
            }
            return JSONObject().apply {
                put("type", "object")
                put("properties", propsJson)
                put("required", required)
                put("additionalProperties", false)
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
                        )
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
