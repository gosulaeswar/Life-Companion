package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
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
    val firebaseStatus: String,
    val appCheckStatus: String,
    val geminiStatus: String,
    val summary: String,
    val recommendation: String
)

class LifeWeaveAiService(context: Context? = null) {
    private val appContext = context?.applicationContext

    private fun ensureFirebaseInitialized(): Boolean {
        val ctx = appContext ?: return false
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(ctx)
        }
        return FirebaseApp.getApps(ctx).isNotEmpty()
    }

    private val assistantSchema = Schema.obj(
        properties = mapOf(
            "assistantReply" to Schema.string(
                description = "Warm, concise wellness reply or app guidance in the user's selected language."
            ),
            "actions" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "actionType" to Schema.enumeration(
                            values = AiActionType.entries.map { it.name },
                            description = "Action to automatically execute in the app."
                        ),
                        "titleOrName" to Schema.string(
                            description = "Item name, food/drink, medicine name, doctor name, exercise name, or module display name."
                        ),
                        "amountOrValue" to Schema.integer(
                            description = "Numeric amount: ml for LOG_WATER, minutes for LOG_EXERCISE, step count for LOG_STEPS, hours*10 for LOG_SLEEP (e.g. 75 = 7.5h), followUpDays for LOG_DOCTOR_VISIT."
                        ),
                        "secondaryText" to Schema.string(
                            description = "Drink type ('Water','Coffee','Herbal Tea'), meal type ('Breakfast','Lunch','Dinner','Snack'), dosage ('500mg'), specialty, or moduleKey for OPEN_MODULE ('water_tracker','period_tracker','medical_care','exercise_tracker','diet_tracker','beauty_care','digital_wellbeing','connected_devices','daily_reminders','reports_analytics')."
                        ),
                        "calories" to Schema.integer(description = "Estimated kcal for LOG_MEAL or LOG_EXERCISE"),
                        "dosesPerDay" to Schema.integer(description = "Daily doses (1..6) for LOG_MEDICINE"),
                        "intervalHours" to Schema.integer(description = "Hours between doses (4..24) for LOG_MEDICINE"),
                        "mealRelation" to Schema.string(description = "'Before Meal', 'After Meal', 'With Meal', or 'Anytime'")
                    ),
                    optionalProperties = listOf("calories", "dosesPerDay", "intervalHours", "mealRelation")
                ),
                description = "List of zero or more structured app logging or navigation actions."
            )
        )
    )

    private val prescriptionSchema = Schema.obj(
        properties = mapOf(
            "summary" to Schema.string(description = "Patient-friendly summary of the prescription in the requested language."),
            "doctorName" to Schema.string(description = "Doctor's name without 'Dr.' prefix, or empty if illegible."),
            "specialty" to Schema.string(description = "Doctor specialty such as General Physician, Dentist, Cardiologist."),
            "clinicOrHospital" to Schema.string(description = "Clinic or hospital name if visible, else empty."),
            "followUpDaysFromToday" to Schema.integer(description = "Days until follow-up visit (0 if not mentioned)."),
            "followUpNotes" to Schema.string(description = "Follow-up instructions or tests mentioned."),
            "medicines" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "name" to Schema.string(description = "Medicine name"),
                        "dosage" to Schema.string(description = "Strength or dose e.g. '500mg', '1 tablet', '5ml'"),
                        "dosesPerDay" to Schema.integer(description = "Number of doses per day (1..6)"),
                        "intervalHours" to Schema.integer(description = "Interval in hours e.g. 24, 12, 8, 6"),
                        "scheduleTime24h" to Schema.string(description = "First daily dose time in HH:mm format e.g. '08:00'"),
                        "mealRelation" to Schema.string(description = "'Before Meal', 'After Meal', 'With Meal', or 'Anytime'"),
                        "notes" to Schema.string(description = "Duration or special instruction e.g. '5 days'")
                    )
                ),
                description = "Extracted medicines with dosing schedules."
            )
        )
    )

    private val assistantModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = assistantSchema
            }
        )
    }

    private val prescriptionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = prescriptionSchema
            }
        )
    }

    private val diagnosticPingModel by lazy {
        Firebase.ai.generativeModel(modelName = "gemini-3.8-flash")
    }

    suspend fun processVoiceOrTextCommand(
        userMessage: String,
        languageOption: VoiceLanguageOption,
        wellnessContextSummary: String,
        onStageUpdate: (String) -> Unit = {},
        onPartialPreview: (String) -> Unit = {}
    ): Result<AiAssistantOutcome> {
        var appCheckPassed = false
        val accumulatedJson = StringBuilder()
        return try {
            onStageUpdate("Checking Firebase & App Check...")
            if (appContext != null && !ensureFirebaseInitialized()) {
                return Result.failure(
                    IllegalStateException(
                        "AI Voice needs Firebase configuration bundled in the APK (missing google-services.json). " +
                            "Use AI Studio Preview or add GOOGLE_SERVICES_JSON to your GitHub Secrets and rebuild."
                    )
                )
            }

            if (appContext != null) {
                when (val check = verifyAppCheckToken(appContext, timeoutMs = 15_000L)) {
                    is AppCheckVerificationResult.Success -> {
                        appCheckPassed = true
                    }
                    is AppCheckVerificationResult.Timeout -> {
                        return Result.failure(IllegalStateException(check.diagnosticMessage))
                    }
                    is AppCheckVerificationResult.Failure -> {
                        return Result.failure(IllegalStateException(check.diagnosticMessage))
                    }
                }
            }

            val prompt = buildString {
                appendLine("You are LifeWeave's friendly, voice-ready wellness assistant inside an Android health app.")
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
                appendLine("Current snapshot:")
                appendLine(wellnessContextSummary)
                appendLine()
                appendLine("Rules:")
                appendLine("1. Drinking water -> LOG_WATER (ml in amountOrValue, default 250, secondaryText='Water').")
                appendLine("2. Coffee/tea/food -> LOG_MEAL (titleOrName e.g. '☕ Coffee', secondaryText='Snack'/'Breakfast'/'Lunch'/'Dinner', calories e.g. 50) + optional LOG_WATER.")
                appendLine("3. Medicine mention -> LOG_MEDICINE.")
                appendLine("4. Steps/exercise/sleep -> LOG_STEPS, LOG_EXERCISE, or LOG_SLEEP (hours*10).")
                appendLine("5. Open module request -> OPEN_MODULE with moduleKey in secondaryText.")
                appendLine("6. Question or tip -> actions=[] and answer concisely in ${languageOption.label}.")
                appendLine()
                appendLine("User: \"$userMessage\"")
            }

            onStageUpdate("Streaming reply in ${languageOption.label}...")
            withTimeout(60_000L) {
                assistantModel.generateContentStream(prompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulatedJson.append(chunkText)
                        val preview = extractStreamingFieldPreview(accumulatedJson.toString(), "assistantReply")
                        if (preview.isNotBlank()) {
                            onPartialPreview(preview)
                        }
                    }
                }
            }

            val rawJson = accumulatedJson.toString()
            if (rawJson.isBlank()) {
                throw IllegalStateException("AI returned an empty response.")
            }
            Result.success(parseAssistantJson(rawJson))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI assistant stream timed out", e)
            val detail = if (appCheckPassed) {
                "Gemini streaming timed out after 60s (App Check passed; received ${accumulatedJson.length} chars). Please check network speed and retry."
            } else {
                "AI request timed out after 60 seconds. Please check connection and tap Diagnose."
            }
            Result.failure(IllegalStateException(detail))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI assistant stream failed", e)
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

    suspend fun extractPrescriptionFromUri(
        context: Context,
        imageUri: Uri,
        languageOption: VoiceLanguageOption,
        onStageUpdate: (String) -> Unit = {},
        onPartialPreview: (String) -> Unit = {}
    ): Result<ExtractedPrescriptionResult> {
        var appCheckPassed = false
        val accumulatedJson = StringBuilder()
        return try {
            onStageUpdate("Checking Firebase & App Check...")
            if (appContext != null && !ensureFirebaseInitialized()) {
                return Result.failure(
                    IllegalStateException(
                        "Prescription Scanner needs Firebase configuration bundled in the APK (missing google-services.json). " +
                            "Use AI Studio Preview or add GOOGLE_SERVICES_JSON to your GitHub Secrets and rebuild."
                    )
                )
            }

            if (appContext != null) {
                when (val check = verifyAppCheckToken(appContext, timeoutMs = 15_000L)) {
                    is AppCheckVerificationResult.Success -> {
                        appCheckPassed = true
                    }
                    is AppCheckVerificationResult.Timeout -> {
                        return Result.failure(IllegalStateException(check.diagnosticMessage))
                    }
                    is AppCheckVerificationResult.Failure -> {
                        return Result.failure(IllegalStateException(check.diagnosticMessage))
                    }
                }
            }

            onStageUpdate("Preparing prescription image...")
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

            onStageUpdate("Streaming prescription extraction...")
            withTimeout(60_000L) {
                prescriptionModel.generateContentStream(
                    content {
                        image(bitmap)
                        text(promptText)
                    }
                ).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulatedJson.append(chunkText)
                        val preview = extractStreamingFieldPreview(accumulatedJson.toString(), "summary")
                        if (preview.isNotBlank()) {
                            onPartialPreview(preview)
                        }
                    }
                }
            }
            val rawJson = accumulatedJson.toString()
            if (rawJson.isBlank()) {
                throw IllegalStateException("AI returned empty prescription output.")
            }
            Result.success(parsePrescriptionJson(rawJson))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Prescription scan stream timed out", e)
            val detail = if (appCheckPassed) {
                "Prescription scan timed out after 60s (received ${accumulatedJson.length} chars). Try a clearer or smaller photo."
            } else {
                "Prescription scan timed out while verifying App Check."
            }
            Result.failure(IllegalStateException(detail))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Prescription scan failed", e)
            Result.failure(e)
        }
    }

    suspend fun runDiagnostics(): AiDiagnosticReport {
        val ctx = appContext
        if (ctx != null && !ensureFirebaseInitialized()) {
            return AiDiagnosticReport(
                isHealthy = false,
                firebaseStatus = "Missing google-services.json",
                appCheckStatus = "Skipped (Firebase uninitialized)",
                geminiStatus = "Skipped",
                summary = "Firebase config is missing from this APK.",
                recommendation = "Ensure GOOGLE_SERVICES_JSON is set in GitHub Actions Secrets and rebuild the APK."
            )
        }

        val firebaseStatus = runCatching {
            val app = FirebaseApp.getInstance()
            "Ready (project: ${app.options.projectId ?: "unknown"})"
        }.getOrElse { "Not initialized" }

        val checkResult = verifyAppCheckToken(ctx, timeoutMs = 15_000L)
        return when (checkResult) {
            is AppCheckVerificationResult.Timeout -> {
                AiDiagnosticReport(
                    isHealthy = false,
                    firebaseStatus = firebaseStatus,
                    appCheckStatus = "Timed out after ${checkResult.timeoutMs / 1000}s (${checkResult.providerLabel})",
                    geminiStatus = "Blocked by App Check",
                    summary = "App Check attestation stalled before reaching Gemini.",
                    recommendation = checkResult.diagnosticMessage
                )
            }
            is AppCheckVerificationResult.Failure -> {
                AiDiagnosticReport(
                    isHealthy = false,
                    firebaseStatus = firebaseStatus,
                    appCheckStatus = "Failed (${checkResult.providerLabel}): ${checkResult.errorMessage}",
                    geminiStatus = "Blocked by App Check",
                    summary = "App Check rejected or could not fetch a token.",
                    recommendation = checkResult.diagnosticMessage
                )
            }
            is AppCheckVerificationResult.Success -> {
                val appCheckDesc =
                    "OK (${checkResult.providerLabel}, ${checkResult.latencyMs}ms, ttl ${checkResult.expiresInSeconds}s)"
                val geminiStart = System.currentTimeMillis()
                try {
                    val replyBuilder = StringBuilder()
                    withTimeout(20_000L) {
                        diagnosticPingModel.generateContentStream("Reply with two words: AI Ready").collect { chunk ->
                            replyBuilder.append(chunk.text.orEmpty())
                        }
                    }
                    val geminiMs = (System.currentTimeMillis() - geminiStart).coerceAtLeast(1L)
                    val sample = replyBuilder.toString().trim().ifEmpty { "Empty response" }
                    AiDiagnosticReport(
                        isHealthy = true,
                        firebaseStatus = firebaseStatus,
                        appCheckStatus = appCheckDesc,
                        geminiStatus = "Connected (${geminiMs}ms): $sample",
                        summary = "App Check and Gemini streaming are working on this device.",
                        recommendation = "You can now use Voice, Quick Chips, and Prescription Scanner."
                    )
                } catch (e: TimeoutCancellationException) {
                    AiDiagnosticReport(
                        isHealthy = false,
                        firebaseStatus = firebaseStatus,
                        appCheckStatus = appCheckDesc,
                        geminiStatus = "Stream timed out after 20s",
                        summary = "App Check succeeded, but Gemini streaming did not respond within 20s.",
                        recommendation = "Check mobile/Wi-Fi internet speed, disable VPN/private DNS, and retry."
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "Diagnostic Gemini ping failed", e)
                    val err = e.localizedMessage?.trim().orEmpty().ifEmpty { e.javaClass.simpleName }
                    AiDiagnosticReport(
                        isHealthy = false,
                        firebaseStatus = firebaseStatus,
                        appCheckStatus = appCheckDesc,
                        geminiStatus = "Error: $err",
                        summary = "App Check succeeded, but Gemini returned an error.",
                        recommendation = "Verify Firebase AI Logic is enabled for this project and retry."
                    )
                }
            }
        }
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
            val json = JSONObject(jsonString)
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
            val json = JSONObject(jsonString)
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
    }
}
