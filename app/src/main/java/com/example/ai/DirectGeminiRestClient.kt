package com.example.ai

import android.graphics.Bitmap
import android.util.Base64
import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.OutputStreamWriter
import java.net.URL
import java.nio.charset.StandardCharsets
import javax.net.ssl.HttpsURLConnection

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
 * Used only when the user explicitly configures a personal Gemini API key in-app
 * (e.g., on sideloaded or family-shared APKs without Firebase App Check debug tokens).
 */
class DirectGeminiRestClient(
    private val httpExecutor: suspend (
        endpointUrl: String,
        apiKey: String,
        bodyJson: String,
        timeoutMs: Int
    ) -> DirectGeminiHttpResponse = ::executeHttpsPost
) {
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

        val modelsToTry = listOf(DEFAULT_MODEL, FALLBACK_MODEL)
        var lastError: Exception? = null
        for ((index, modelName) in modelsToTry.withIndex()) {
            val endpoint = "$BASE_URL/$modelName:generateContent"
            try {
                val response = httpExecutor(endpoint, cleanKey, requestBody, timeoutMs)
                if (response.statusCode in 200..299) {
                    val text = extractCandidateText(response.body)
                    if (text.isBlank()) {
                        return Result.failure(IllegalStateException("Gemini returned an empty response."))
                    }
                    return Result.success(text)
                }
                val isLastAttempt = index == modelsToTry.lastIndex
                if (response.statusCode == 404 && !isLastAttempt) {
                    Log.w(DIRECT_GEMINI_TAG, "Model $modelName unavailable (404); falling back to $FALLBACK_MODEL.")
                    continue
                }
                return Result.failure(
                    IllegalStateException(mapHttpError(response.statusCode, response.body, modelName))
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                lastError = e
                Log.e(DIRECT_GEMINI_TAG, "Direct Gemini call failed on $modelName: ${e.javaClass.simpleName}")
                if (index == modelsToTry.lastIndex) {
                    val msg = e.localizedMessage?.trim().orEmpty()
                    return Result.failure(
                        IllegalStateException(
                            if (msg.isNotBlank()) "Direct Gemini connection failed: $msg"
                            else "Unable to reach Gemini API. Check internet connectivity and retry.",
                            e
                        )
                    )
                }
            }
        }
        return Result.failure(lastError ?: IllegalStateException("Direct Gemini request failed."))
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

        val modelsToTry = listOf(DEFAULT_MODEL, FALLBACK_MODEL)
        for ((index, modelName) in modelsToTry.withIndex()) {
            val endpoint = "$BASE_URL/$modelName:generateContent"
            val startedAt = System.currentTimeMillis()
            try {
                val response = httpExecutor(endpoint, cleanKey, requestBody, timeoutMs)
                if (response.statusCode in 200..299) {
                    val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                    val sample = extractCandidateText(response.body).trim().ifEmpty { "AI Ready" }
                    return Result.success(
                        DirectGeminiPingResult(
                            modelUsed = modelName,
                            latencyMs = elapsed,
                            sampleText = sample
                        )
                    )
                }
                if (response.statusCode == 404 && index < modelsToTry.lastIndex) {
                    continue
                }
                return Result.failure(
                    IllegalStateException(mapHttpError(response.statusCode, response.body, modelName))
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (index == modelsToTry.lastIndex) {
                    val msg = e.localizedMessage?.trim().orEmpty()
                    return Result.failure(
                        IllegalStateException(
                            if (msg.isNotBlank()) "Connection test failed: $msg"
                            else "Unable to reach Gemini API. Check mobile/Wi-Fi internet.",
                            e
                        )
                    )
                }
            }
        }
        return Result.failure(IllegalStateException("Gemini API ping failed."))
    }

    companion object {
        const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
        const val DEFAULT_MODEL = "gemini-3.8-flash"
        const val FALLBACK_MODEL = "gemini-3.5-flash-lite"

        suspend fun encodeBitmapAsJpegPart(bitmap: Bitmap): DirectGeminiInlinePart =
            withContext(Dispatchers.IO) {
                val out = ByteArrayOutputStream()
                bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
                DirectGeminiInlinePart(mimeType = "image/jpeg", bytes = out.toByteArray())
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

        internal fun mapHttpError(statusCode: Int, responseBody: String, modelName: String): String {
            val apiMessage = runCatching {
                JSONObject(responseBody).optJSONObject("error")?.optString("message", "").orEmpty().trim()
            }.getOrDefault("")

            return when (statusCode) {
                400 -> {
                    if (apiMessage.contains("API key", ignoreCase = true) ||
                        responseBody.contains("API_KEY_INVALID", ignoreCase = true)
                    ) {
                        "Your Personal Gemini API key is invalid (HTTP 400). Copy a fresh key from aistudio.google.com/apikey."
                    } else {
                        "Gemini rejected the request (HTTP 400)${if (apiMessage.isNotBlank()) ": $apiMessage" else "."}"
                    }
                }
                401, 403 ->
                    "Your Personal Gemini API key was denied (HTTP $statusCode). Check key restrictions or generate a new key at aistudio.google.com/apikey."
                404 ->
                    "Model $modelName is unavailable for this API key (HTTP 404). Please retry or verify your Google AI Studio key."
                429 ->
                    "Gemini rate limit or free quota reached for your personal key (HTTP 429). Wait a moment and retry."
                in 500..599 ->
                    "Gemini service is temporarily busy (HTTP $statusCode). Please retry in a few seconds."
                else ->
                    "Gemini API error (HTTP $statusCode)${if (apiMessage.isNotBlank()) ": $apiMessage" else "."}"
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
