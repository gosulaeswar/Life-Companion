package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.example.BuildConfig
import com.example.security.PersonalAiKeyState
import com.example.security.PersonalAiKeyStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import kotlin.math.max

private const val TAG = "LifeWeaveAiService"

enum class AiActionType {
    LOG_WATER,
    LOG_MEAL,
    LOG_MEDICINE,
    LOG_DOCTOR_VISIT,
    LOG_EXERCISE,
    LOG_SLEEP,
    LOG_STEPS,
    OPEN_MODULE,
    NONE
}

data class AiParsedAction(
    val actionType: AiActionType,
    val titleOrName: String = "",
    val amountOrValue: Int = 0,
    val secondaryText: String = "",
    val calories: Int = 0,
    val dosesPerDay: Int = 1,
    val intervalHours: Int = 24,
    val mealRelation: String = "After Meal"
)

data class AiAssistantOutcome(
    val replyText: String,
    val actions: List<AiParsedAction> = emptyList()
)

data class ExtractedPrescriptionMedicine(
    val name: String,
    val dosage: String,
    val dosesPerDay: Int,
    val intervalHours: Int,
    val scheduleTime24h: String,
    val mealRelation: String,
    val notes: String
)

data class ExtractedPrescriptionResult(
    val summary: String,
    val doctorName: String,
    val specialty: String,
    val clinicOrHospital: String,
    val followUpDaysFromToday: Int,
    val followUpNotes: String,
    val medicines: List<ExtractedPrescriptionMedicine>
)

data class AiDiagnosticReport(
    val isHealthy: Boolean,
    val providerStatus: String,
    val authStatus: String,
    val grokStatus: String,
    val summary: String,
    val recommendation: String
)

data class AiHealthIntelligenceReport(
    val timeframe: String,
    val headline: String,
    val recordedFactsSummary: String,
    val observedChanges: List<String>,
    val personalizedSuggestions: List<String>,
    val dailyPlanHighlights: List<String>,
    val uncertaintyAndCoverageNote: String,
    val disclaimer: String,
    val isFromCloudAi: Boolean = true
)

data class AiReportExplanation(
    val title: String,
    val plainLanguageSummary: String,
    val keyObservations: List<String>,
    val questionsForDoctor: List<String>,
    val disclaimer: String,
    val isFromCloudAi: Boolean = true
)

/**
 * Unified xAI Grok AI orchestration service powering all five LifeWeave workflows:
 * Voice/Text Assistant, Personal Health Intelligence, Prescription Vision Scanner,
 * Lab & Document Explainer, and Regional Indian Nutrition Planner.
 */
class LifeWeaveAiService(
    context: Context? = null,
    private val personalAiKeyStore: PersonalAiKeyStore? = null,
    private val grokClient: GrokApiClient = GrokApiClient(),
    private val buildConfigKeyProvider: () -> String = {
        runCatching { BuildConfig.XAI_API_KEY }.getOrDefault("")
    }
) {
    private val appContext = context?.applicationContext

    private data class ResolvedGrokKey(
        val apiKey: String,
        val sourceLabel: String
    )

    private fun resolveActiveGrokKey(): ResolvedGrokKey? {
        val personal = personalAiKeyStore?.readPersonalKeyOrNull()
            ?.takeIf { PersonalAiKeyState.isUsableGrokKey(it) }
        if (personal != null) {
            return ResolvedGrokKey(personal, "Personal xAI Grok Key")
        }
        val bundled = buildConfigKeyProvider().trim()
        if (PersonalAiKeyState.isUsableGrokKey(bundled)) {
            return ResolvedGrokKey(bundled, "App Secret (XAI_API_KEY)")
        }
        return null
    }

    private fun logAiFailure(featureLabel: String, err: Exception) {
        val msg = err.localizedMessage.orEmpty()
        val isExpectedAuthIssue = msg.contains("API key", ignoreCase = true) ||
            msg.contains("console.x.ai", ignoreCase = true) ||
            msg.contains("HTTP 401") ||
            msg.contains("HTTP 403")
        if (isExpectedAuthIssue) {
            Log.w(TAG, "$featureLabel skipped/rejected: $msg")
        } else {
            Log.e(TAG, "$featureLabel failed", err)
        }
    }

    private fun missingKeyError(featureName: String): IllegalStateException =
        IllegalStateException(
            "$featureName requires an xAI Grok API key. Open Settings -> Cloud AI Connection (Grok) to paste your xai-... key from console.x.ai, or configure XAI_API_KEY in AI Studio Secrets."
        )

    suspend fun testPersonalApiKey(rawKey: String? = null): Result<GrokPingResult> {
        val explicit = rawKey?.trim()?.takeIf { it.isNotEmpty() }
        val resolvedKey = explicit ?: resolveActiveGrokKey()?.apiKey
            ?: return Result.failure(missingKeyError("xAI Grok connection test"))
        return grokClient.ping(resolvedKey)
    }

    suspend fun processVoiceOrTextCommand(
        userMessage: String,
        languageOption: VoiceLanguageOption,
        wellnessContextSummary: String,
        onStageUpdate: (String) -> Unit = {},
        onPartialPreview: (String) -> Unit = {}
    ): Result<AiAssistantOutcome> {
        val resolved = resolveActiveGrokKey()
            ?: return Result.failure(missingKeyError("Grok Voice Assistant"))

        return try {
            val prompt = buildString {
                appendLine("You are LifeWeave's friendly, voice-ready wellness assistant powered by xAI Grok inside an Android health app.")
                appendLine("Respond naturally in this language: ${languageOption.label} (${languageOption.tag}). Keep spoken replies concise (2-4 sentences).")
                appendLine()
                appendLine("Modules:")
                appendLine("- water_tracker: hydration goal & water logs")
                appendLine("- period_tracker: cycle & period tracker")
                appendLine("- medical_care: medicines (doses, intervals, meal timing) & doctors")
                appendLine("- exercise_tracker: workouts, yoga, steps, sleep")
                appendLine("- diet_tracker: meals & coffee/drink calories")
                appendLine("- connected_devices: Fitbit, Health Connect, Samsung, Garmin, Apple Health")
                appendLine("- daily_reminders: custom alarms & tones")
                appendLine("- beauty_care: skincare & haircare")
                appendLine("- digital_wellbeing: screen time cap & focus blocks")
                appendLine("- reports_analytics: weekly trends")
                appendLine()
                appendLine("Current authorized wellness records & trends:")
                appendLine(wellnessContextSummary)
                appendLine()
                appendLine("Rules:")
                appendLine("1. Drinking water -> LOG_WATER (ml in amountOrValue, default 250, secondaryText='Water').")
                appendLine("2. Coffee/tea/food -> LOG_MEAL (titleOrName e.g. '☕ Coffee', secondaryText='Snack'/'Breakfast'/'Lunch'/'Dinner', calories e.g. 50) + optional LOG_WATER.")
                appendLine("3. Medicine mention -> LOG_MEDICINE.")
                appendLine("4. Steps/exercise/sleep -> LOG_STEPS, LOG_EXERCISE, or LOG_SLEEP (hours*10).")
                appendLine("5. Open module request -> OPEN_MODULE with moduleKey in secondaryText.")
                appendLine("6. Question, summary, or wellness tip -> actions=[] and answer concisely in ${languageOption.label} using ONLY facts present in the authorized snapshot above.")
                appendLine("7. STRICT SAFETY & PRIVACY RULES: NEVER invent measurements or guess missing data; clearly state when a category has no records or is excluded by privacy settings; clearly distinguish recorded numbers from gentle suggestions; NEVER diagnose medical conditions or change prescribed medication dosages.")
                appendLine()
                appendLine("User: \"$userMessage\"")
            }

            onStageUpdate("Connecting to xAI Grok (${languageOption.label})...")
            val rawJson = withTimeout(60_000L) {
                grokClient.generateContent(
                    apiKey = resolved.apiKey,
                    promptText = prompt,
                    schemaName = "lifeweave_assistant_outcome",
                    responseSchema = GrokApiClient.assistantResponseSchema()
                ).getOrThrow()
            }
            val parsed = parseAssistantJson(rawJson)
            onPartialPreview(parsed.replyText)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Grok assistant request timed out", e)
            Result.failure(IllegalStateException("xAI Grok request timed out after 60 seconds. Check internet connectivity and retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logAiFailure("Grok assistant request", e)
            Result.failure(e)
        }
    }

    suspend fun generateMultilingualAppGuide(
        languageOption: VoiceLanguageOption,
        wellnessContextSummary: String,
        onStageUpdate: (String) -> Unit = {},
        onPartialPreview: (String) -> Unit = {}
    ): Result<AiAssistantOutcome> {
        val guidePrompt =
            "Give me a warm, easy-to-follow spoken guided tour of LifeWeave in ${languageOption.label}. " +
                "Cover how to log water/coffee by voice, upload a doctor's prescription photo, connect Fitbit/Apple Health devices, and manage alarms."
        return processVoiceOrTextCommand(
            userMessage = guidePrompt,
            languageOption = languageOption,
            wellnessContextSummary = wellnessContextSummary,
            onStageUpdate = onStageUpdate,
            onPartialPreview = onPartialPreview
        )
    }

    suspend fun generatePersonalHealthIntelligence(
        timeframeName: String,
        languageOption: VoiceLanguageOption,
        groundedWellnessContext: String,
        onStageUpdate: (String) -> Unit = {},
        onPartialPreview: (String) -> Unit = {}
    ): Result<AiHealthIntelligenceReport> {
        val resolved = resolveActiveGrokKey()
            ?: return Result.failure(missingKeyError("Grok Personal Health Intelligence"))

        return try {
            val prompt = buildString {
                appendLine("You are LifeWeave Personal Health Intelligence powered by xAI Grok.")
                appendLine("Generate a $timeframeName wellness intelligence report in ${languageOption.label} (${languageOption.tag}).")
                appendLine()
                appendLine("STRICT GROUNDING & SAFETY RULES:")
                appendLine("1. Use ONLY the authorized recorded measurements below. NEVER invent or extrapolate numbers.")
                appendLine("2. Separate recorded observations ('recordedFactsSummary' and 'observedChanges') from wellness ideas ('personalizedSuggestions' and 'dailyPlanHighlights').")
                appendLine("3. NEVER provide medical diagnoses or recommend changing prescribed medicine dosages.")
                appendLine("4. If data for a metric or prior baseline is unavailable or excluded by privacy settings, state that clearly in 'uncertaintyAndCoverageNote'.")
                appendLine()
                appendLine(groundedWellnessContext)
            }

            onStageUpdate("Synthesizing $timeframeName insights with xAI Grok...")
            val rawJson = withTimeout(60_000L) {
                grokClient.generateContent(
                    apiKey = resolved.apiKey,
                    promptText = prompt,
                    schemaName = "lifeweave_health_intelligence",
                    responseSchema = GrokApiClient.healthIntelligenceResponseSchema()
                ).getOrThrow()
            }
            val parsed = parseHealthIntelligenceJson(rawJson, timeframeName)
            onPartialPreview(parsed.headline)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Grok health intelligence timed out", e)
            Result.failure(IllegalStateException("Grok health briefing timed out after 60s. Local grounded insights below remain up to date."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logAiFailure("Grok health intelligence synthesis", e)
            Result.failure(e)
        }
    }

    suspend fun extractPrescriptionFromUri(
        context: Context,
        imageUri: Uri,
        languageOption: VoiceLanguageOption,
        onStageUpdate: (String) -> Unit = {},
        onPartialPreview: (String) -> Unit = {}
    ): Result<ExtractedPrescriptionResult> {
        val resolved = resolveActiveGrokKey()
            ?: return Result.failure(missingKeyError("Grok Prescription Scanner"))

        return try {
            onStageUpdate("Preparing prescription image for Grok Vision...")
            val bitmap = withContext(Dispatchers.IO) {
                decodeScaledBitmap(context, imageUri, maxDimension = 1280)
            } ?: return Result.failure(IllegalArgumentException("Unable to read the selected prescription image."))

            val promptText = buildString {
                appendLine("Analyze this medical prescription or doctor visit summary image carefully.")
                appendLine("Write 'summary' and 'followUpNotes' in ${languageOption.label} (${languageOption.tag}).")
                appendLine("Extract every medicine with its exact name, dosage strength, dosesPerDay (1-6), intervalHours (24 for once daily, 12 for twice daily, 8 for 3x daily, 6 for 4x daily), scheduleTime24h (e.g. '08:00'), and mealRelation ('Before Meal', 'After Meal', 'With Meal', or 'Anytime').")
                appendLine("Also extract doctorName, specialty, clinicOrHospital, and followUpDaysFromToday if present (0 if not stated).")
                appendLine("Do not invent medicines that are not visible in the image.")
            }

            onStageUpdate("Scanning prescription via xAI Grok Vision...")
            val jpegPart = GrokApiClient.encodeBitmapAsJpegPart(bitmap)
            val rawJson = withTimeout(60_000L) {
                grokClient.generateContent(
                    apiKey = resolved.apiKey,
                    promptText = promptText,
                    inlinePart = jpegPart,
                    schemaName = "lifeweave_prescription_extract",
                    responseSchema = GrokApiClient.prescriptionResponseSchema()
                ).getOrThrow()
            }
            val parsed = parsePrescriptionJson(rawJson)
            onPartialPreview(parsed.summary)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Grok prescription scan timed out", e)
            Result.failure(IllegalStateException("Grok prescription scan timed out after 60s. Try a clearer or smaller photo."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logAiFailure("Grok prescription scan", e)
            Result.failure(e)
        }
    }

    suspend fun explainHealthDocument(
        title: String,
        category: String,
        providerOrLab: String,
        notes: String,
        fileBytes: ByteArray?,
        mimeType: String,
        languageOption: VoiceLanguageOption,
        onStageUpdate: (String) -> Unit = {},
        onPartialPreview: (String) -> Unit = {}
    ): Result<AiReportExplanation> {
        val resolved = resolveActiveGrokKey()
            ?: return Result.failure(missingKeyError("Grok Health Document Explainer"))

        return try {
            val imagePart: GrokInlinePart? = when {
                fileBytes != null && mimeType.startsWith("image/", ignoreCase = true) -> {
                    val bmp = withContext(Dispatchers.IO) {
                        decodeScaledBitmapFromBytes(fileBytes, maxDimension = 1280)
                    }
                    if (bmp != null) GrokApiClient.encodeBitmapAsJpegPart(bmp) else null
                }
                fileBytes != null && mimeType.equals("application/pdf", ignoreCase = true) -> {
                    GrokApiClient.renderPdfFirstPageAsJpegPart(appContext, fileBytes, maxDimension = 1280)
                }
                else -> null
            }

            val promptText = buildString {
                appendLine("You are explaining a personal health document in calm, plain ${languageOption.label} (${languageOption.tag}).")
                appendLine("Document Title: $title")
                appendLine("Category: $category")
                if (providerOrLab.isNotBlank()) appendLine("Provider/Lab: $providerOrLab")
                if (notes.isNotBlank()) appendLine("User Notes: $notes")
                if (imagePart == null && fileBytes != null && mimeType.equals("application/pdf", ignoreCase = true)) {
                    appendLine("Note: PDF preview was unavailable; explain conservatively from metadata and user notes only.")
                }
                appendLine()
                appendLine("STRICT SAFETY RULES:")
                appendLine("1. Explain medical terms and lab values clearly for a layperson using ONLY details visible in the document or metadata above.")
                appendLine("2. NEVER invent values, NEVER diagnose diseases, and NEVER suggest changing medication dosages or schedules.")
                appendLine("3. Always state clearly in 'disclaimer' that this is informational only and NOT a substitute for a qualified medical professional.")
            }

            onStageUpdate("Analyzing $category with xAI Grok...")
            val rawJson = withTimeout(60_000L) {
                grokClient.generateContent(
                    apiKey = resolved.apiKey,
                    promptText = promptText,
                    inlinePart = imagePart,
                    schemaName = "lifeweave_report_explanation",
                    responseSchema = GrokApiClient.reportExplanationResponseSchema()
                ).getOrThrow()
            }
            val parsed = parseReportExplanationJson(rawJson, title)
            onPartialPreview(parsed.plainLanguageSummary)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Grok document explanation timed out", e)
            Result.failure(IllegalStateException("Grok document explanation timed out after 60s. Please retry."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logAiFailure("Grok document explanation", e)
            Result.failure(e)
        }
    }

    suspend fun generateIndianNutritionPlan(
        regionLabel: String,
        dietaryLabel: String,
        goalLabel: String,
        targetCalories: Int,
        targetProteinGrams: Int,
        targetCarbsGrams: Int,
        targetFatGrams: Int,
        alreadyLoggedTodaySummary: String,
        avoidAllergens: List<String>,
        languageOption: VoiceLanguageOption,
        onStageUpdate: (String) -> Unit = {},
        onPartialPreview: (String) -> Unit = {}
    ): Result<com.example.domain.IndianMealPlanResult> {
        val resolved = resolveActiveGrokKey()
            ?: return Result.failure(missingKeyError("Grok Indian Meal Planner"))

        return try {
            val allergenClause = if (avoidAllergens.isEmpty()) {
                "No food allergies recorded in vault."
            } else {
                "CRITICAL: Strictly exclude these allergens and all dishes containing them: ${avoidAllergens.joinToString(", ")}."
            }

            val prompt = buildString {
                appendLine("You are LifeWeave's Indian Home-Kitchen Nutrition Planner powered by xAI Grok.")
                appendLine("Create a practical, realistic 1-day meal plan in ${languageOption.label} (${languageOption.tag}).")
                appendLine("Regional Preference: $regionLabel")
                appendLine("Dietary Preference: $dietaryLabel")
                appendLine("Wellness Goal: $goalLabel")
                appendLine("Daily Targets: ~$targetCalories kcal, ${targetProteinGrams}g protein, ${targetCarbsGrams}g carbs, ${targetFatGrams}g fat.")
                appendLine("Already Logged Today: $alreadyLoggedTodaySummary")
                appendLine(allergenClause)
                appendLine()
                appendLine("RULES:")
                appendLine("1. Suggest authentic everyday Indian dishes (use katori, phulka, idli, dosa, bowl, glass portions).")
                appendLine("2. Strictly honor dietary preference ($dietaryLabel) and NEVER include excluded allergens.")
                appendLine("3. Provide realistic macro estimates per slot and include high-protein Indian pairings where helpful.")
                appendLine("4. Informational wellness guidance only — never diagnose disease or prescribe medical therapy.")
            }

            onStageUpdate("Planning $regionLabel meals with xAI Grok...")
            val rawJson = withTimeout(60_000L) {
                grokClient.generateContent(
                    apiKey = resolved.apiKey,
                    promptText = prompt,
                    schemaName = "lifeweave_indian_nutrition",
                    responseSchema = GrokApiClient.indianNutritionResponseSchema()
                ).getOrThrow()
            }
            val parsed = parseIndianNutritionPlanJson(
                jsonString = rawJson,
                regionLabel = regionLabel,
                dietaryLabel = dietaryLabel,
                goalLabel = goalLabel,
                avoidedAllergens = avoidAllergens
            )
            onPartialPreview(parsed.planTitle)
            Result.success(parsed)
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Grok Indian meal plan timed out", e)
            Result.failure(IllegalStateException("Grok meal planner timed out after 60s. Offline regional planner remains ready."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            logAiFailure("Grok Indian nutrition planner", e)
            Result.failure(e)
        }
    }

    suspend fun runDiagnostics(): AiDiagnosticReport {
        val resolved = resolveActiveGrokKey()
        if (resolved == null) {
            return AiDiagnosticReport(
                isHealthy = false,
                providerStatus = "xAI Grok API (api.x.ai)",
                authStatus = "Not configured",
                grokStatus = "Missing API key",
                summary = "xAI Grok API key is not configured yet.",
                recommendation = "Paste your xAI Grok key (xai-...) in Cloud AI Connection below, or add XAI_API_KEY in AI Studio Secrets."
            )
        }

        return grokClient.ping(resolved.apiKey).fold(
            onSuccess = { ping ->
                AiDiagnosticReport(
                    isHealthy = true,
                    providerStatus = "xAI Grok API (api.x.ai)",
                    authStatus = "Active via ${resolved.sourceLabel}",
                    grokStatus = "Connected via ${ping.modelUsed} (${ping.latencyMs}ms): ${ping.sampleText}",
                    summary = "xAI Grok is connected and ready.",
                    recommendation = "Voice Assistant, Prescription Scanner, Lab Explainer, Health Briefings, and Meal Planner are powered by Grok."
                )
            },
            onFailure = { err ->
                val msg = err.localizedMessage?.trim().orEmpty().ifEmpty { err.javaClass.simpleName }
                AiDiagnosticReport(
                    isHealthy = false,
                    providerStatus = "xAI Grok API (api.x.ai)",
                    authStatus = "Using ${resolved.sourceLabel}",
                    grokStatus = "Error: $msg",
                    summary = "xAI Grok connection test failed.",
                    recommendation = "Verify your xai-... API key and account credits at console.x.ai, then tap Test Connection."
                )
            }
        )
    }

    private fun decodeScaledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, boundsOpts)
        }
        val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
        var sample = 1
        while (maxSide / sample > maxDimension) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, decodeOpts)
        }
    }

    private fun decodeScaledBitmapFromBytes(bytes: ByteArray, maxDimension: Int): Bitmap? {
        val boundsOpts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, boundsOpts)
        val maxSide = max(boundsOpts.outWidth, boundsOpts.outHeight)
        if (maxSide <= 0) return null
        var sample = 1
        while (maxSide / sample > maxDimension) {
            sample *= 2
        }
        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, decodeOpts)
    }

    companion object {
        internal fun extractStreamingFieldPreview(partialJson: String, fieldName: String): String {
            val token = "\"$fieldName\""
            val keyIdx = partialJson.indexOf(token)
            if (keyIdx < 0) return ""
            val colonIdx = partialJson.indexOf(':', keyIdx + token.length)
            if (colonIdx < 0) return ""
            val quoteStart = partialJson.indexOf('"', colonIdx + 1)
            if (quoteStart < 0) return ""

            val out = StringBuilder()
            var i = quoteStart + 1
            while (i < partialJson.length) {
                val ch = partialJson[i]
                if (ch == '\\' && i + 1 < partialJson.length) {
                    when (val next = partialJson[i + 1]) {
                        'n' -> out.append(' ')
                        '"', '\\', '/' -> out.append(next)
                        else -> out.append(next)
                    }
                    i += 2
                    continue
                }
                if (ch == '"') break
                out.append(ch)
                i++
            }
            return out.toString().trim()
        }

        fun parseAssistantJson(jsonString: String): AiAssistantOutcome {
            val json = JSONObject(GrokApiClient.normalizeJsonPayload(jsonString))
            val reply = json.optString("assistantReply").ifBlank { "Updated your wellness companion." }
            val actionsArray = json.optJSONArray("actions")
            val actions = mutableListOf<AiParsedAction>()
            if (actionsArray != null) {
                for (i in 0 until actionsArray.length()) {
                    val obj = actionsArray.optJSONObject(i) ?: continue
                    val rawType = obj.optString("actionType", "NONE")
                    val actionType = AiActionType.entries.firstOrNull { it.name == rawType } ?: AiActionType.NONE
                    if (actionType != AiActionType.NONE) {
                        actions += AiParsedAction(
                            actionType = actionType,
                            titleOrName = obj.optString("titleOrName", ""),
                            amountOrValue = obj.optInt("amountOrValue", 0),
                            secondaryText = obj.optString("secondaryText", ""),
                            calories = obj.optInt("calories", 0),
                            dosesPerDay = obj.optInt("dosesPerDay", 1).coerceIn(1, 6),
                            intervalHours = obj.optInt("intervalHours", 24).coerceIn(1, 24),
                            mealRelation = obj.optString("mealRelation", "After Meal").ifBlank { "After Meal" }
                        )
                    }
                }
            }
            return AiAssistantOutcome(replyText = reply, actions = actions)
        }

        fun parsePrescriptionJson(jsonString: String): ExtractedPrescriptionResult {
            val json = JSONObject(GrokApiClient.normalizeJsonPayload(jsonString))
            val medsArray = json.optJSONArray("medicines")
            val meds = mutableListOf<ExtractedPrescriptionMedicine>()
            if (medsArray != null) {
                for (i in 0 until medsArray.length()) {
                    val m = medsArray.optJSONObject(i) ?: continue
                    val name = m.optString("name", "").trim()
                    if (name.isNotEmpty()) {
                        meds += ExtractedPrescriptionMedicine(
                            name = name,
                            dosage = m.optString("dosage", "1 dose").ifBlank { "1 dose" },
                            dosesPerDay = m.optInt("dosesPerDay", 1).coerceIn(1, 6),
                            intervalHours = m.optInt("intervalHours", 24).coerceIn(1, 24),
                            scheduleTime24h = m.optString("scheduleTime24h", "08:00").ifBlank { "08:00" },
                            mealRelation = m.optString("mealRelation", "After Meal").ifBlank { "After Meal" },
                            notes = m.optString("notes", "")
                        )
                    }
                }
            }
            return ExtractedPrescriptionResult(
                summary = json.optString("summary", "Prescription analyzed."),
                doctorName = json.optString("doctorName", "").trim(),
                specialty = json.optString("specialty", "General Care").ifBlank { "General Care" },
                clinicOrHospital = json.optString("clinicOrHospital", "").trim(),
                followUpDaysFromToday = json.optInt("followUpDaysFromToday", 0).coerceAtLeast(0),
                followUpNotes = json.optString("followUpNotes", "").trim(),
                medicines = meds
            )
        }

        fun parseHealthIntelligenceJson(
            jsonString: String,
            defaultTimeframe: String = "WEEKLY"
        ): AiHealthIntelligenceReport {
            val json = JSONObject(GrokApiClient.normalizeJsonPayload(jsonString))
            fun readStringArray(key: String): List<String> {
                val arr = json.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length())
                    .mapNotNull { idx -> arr.optString(idx)?.trim()?.takeIf { it.isNotEmpty() } }
            }
            return AiHealthIntelligenceReport(
                timeframe = defaultTimeframe,
                headline = json.optString("headline", "Personal Wellness Summary").ifBlank { "Personal Wellness Summary" },
                recordedFactsSummary = json.optString("recordedFactsSummary", "").trim(),
                observedChanges = readStringArray("observedChanges"),
                personalizedSuggestions = readStringArray("personalizedSuggestions"),
                dailyPlanHighlights = readStringArray("dailyPlanHighlights"),
                uncertaintyAndCoverageNote = json.optString(
                    "uncertaintyAndCoverageNote",
                    "Based strictly on records logged and authorized on this device."
                ).trim(),
                disclaimer = json.optString(
                    "disclaimer",
                    "Informational wellness insights only—not a medical diagnosis."
                ).trim(),
                isFromCloudAi = true
            )
        }

        fun parseReportExplanationJson(
            jsonString: String,
            docTitle: String
        ): AiReportExplanation {
            val json = JSONObject(GrokApiClient.normalizeJsonPayload(jsonString))
            fun readStringArray(key: String): List<String> {
                val arr = json.optJSONArray(key) ?: return emptyList()
                return (0 until arr.length())
                    .mapNotNull { idx -> arr.optString(idx)?.trim()?.takeIf { it.isNotEmpty() } }
            }
            return AiReportExplanation(
                title = docTitle,
                plainLanguageSummary = json.optString(
                    "plainLanguageSummary",
                    "Document summarized for informational review."
                ).trim(),
                keyObservations = readStringArray("keyObservations"),
                questionsForDoctor = readStringArray("questionsForDoctor"),
                disclaimer = json.optString(
                    "disclaimer",
                    "Informational explanation only — not a substitute for a qualified medical professional."
                ).trim(),
                isFromCloudAi = true
            )
        }

        fun parseIndianNutritionPlanJson(
            jsonString: String,
            regionLabel: String,
            dietaryLabel: String,
            goalLabel: String,
            avoidedAllergens: List<String> = emptyList()
        ): com.example.domain.IndianMealPlanResult {
            val json = JSONObject(GrokApiClient.normalizeJsonPayload(jsonString))
            val mealsArray = json.optJSONArray("meals")
            val slots = mutableListOf<com.example.domain.PlannedIndianMealSlot>()
            if (mealsArray != null) {
                for (i in 0 until mealsArray.length()) {
                    val m = mealsArray.optJSONObject(i) ?: continue
                    val dish = m.optString("dishName", "").trim()
                    if (dish.isNotEmpty()) {
                        val rawType = m.optString("mealType", "Lunch").ifBlank { "Lunch" }
                        val normalizedType = when {
                            rawType.equals("Breakfast", ignoreCase = true) -> "Breakfast"
                            rawType.equals("Lunch", ignoreCase = true) -> "Lunch"
                            rawType.equals("Dinner", ignoreCase = true) -> "Dinner"
                            else -> "Snack"
                        }
                        slots += com.example.domain.PlannedIndianMealSlot(
                            mealType = normalizedType,
                            slotLabel = m.optString("slotLabel", normalizedType).ifBlank { normalizedType },
                            dishName = dish,
                            portionLabel = m.optString("portionLabel", "1 serving").ifBlank { "1 serving" },
                            calories = m.optInt("calories", 300).coerceIn(30, 1500),
                            proteinGrams = m.optInt("proteinGrams", 10).coerceIn(0, 120),
                            carbsGrams = m.optInt("carbsGrams", 35).coerceIn(0, 250),
                            fatGrams = m.optInt("fatGrams", 8).coerceIn(0, 100),
                            prepTip = m.optString("prepTip", "").trim()
                        )
                    }
                }
            }
            val groceryArr = json.optJSONArray("groceryHighlights")
            val groceries = mutableListOf<String>()
            if (groceryArr != null) {
                for (i in 0 until groceryArr.length()) {
                    groceryArr.optString(i)?.trim()?.takeIf { it.isNotEmpty() }?.let { groceries += it }
                }
            }
            return com.example.domain.IndianMealPlanResult(
                planTitle = json.optString("planTitle", "$regionLabel $goalLabel Plan").ifBlank { "$regionLabel Plan" },
                regionLabel = regionLabel,
                dietaryLabel = dietaryLabel,
                goalLabel = goalLabel,
                regionalNotes = json.optString("regionalNotes", "").trim(),
                meals = slots,
                totalCalories = slots.sumOf { it.calories },
                totalProteinGrams = slots.sumOf { it.proteinGrams },
                totalCarbsGrams = slots.sumOf { it.carbsGrams },
                totalFatGrams = slots.sumOf { it.fatGrams },
                groceryHighlights = groceries,
                avoidedAllergens = avoidedAllergens,
                disclaimer = json.optString(
                    "disclaimer",
                    com.example.domain.IndianNutritionEngine.STANDARD_NUTRITION_DISCLAIMER
                ).trim(),
                isFromCloudAi = true
            )
        }
    }
}
