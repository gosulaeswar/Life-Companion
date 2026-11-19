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
import com.example.security.PersonalAiKeyStore
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

class LifeWeaveAiService(
    context: Context? = null,
    private val personalAiKeyStore: PersonalAiKeyStore? = null,
    private val directGeminiClient: DirectGeminiRestClient = DirectGeminiRestClient()
) {
    private val appContext = context?.applicationContext

    private fun activePersonalApiKey(): String? =
        personalAiKeyStore?.readPersonalKeyOrNull()?.takeIf { it.isNotBlank() }

    suspend fun testPersonalApiKey(rawKey: String? = null): Result<DirectGeminiPingResult> {
        val key = rawKey?.trim()?.takeIf { it.isNotEmpty() } ?: activePersonalApiKey()
            ?: return Result.failure(IllegalStateException("No personal Gemini API key is saved on this device."))
        return directGeminiClient.ping(key)
    }

    private fun ensureFirebaseInitialized(): Boolean {
        val ctx = appContext ?: return false
        if (FirebaseApp.getApps(ctx).isEmpty()) {
            FirebaseApp.initializeApp(ctx)
        }
        return FirebaseApp.getApps(ctx).isNotEmpty()
    }

    private suspend fun probeAppCheckNonBlocking(): Pair<Boolean, String?> {
        val ctx = appContext ?: return false to null
        return when (val check = verifyAppCheckToken(ctx, timeoutMs = 8_000L)) {
            is AppCheckVerificationResult.Success -> true to null
            is AppCheckVerificationResult.Timeout -> {
                Log.w(TAG, "App Check pre-flight timed out; proceeding to Firebase AI call: ${check.diagnosticMessage}")
                false to check.diagnosticMessage
            }
            is AppCheckVerificationResult.Failure -> {
                Log.w(TAG, "App Check pre-flight returned warning; proceeding to Firebase AI call: ${check.diagnosticMessage}")
                false to check.diagnosticMessage
            }
        }
    }

    private fun enrichAiFailure(e: Exception, appCheckHint: String?): Exception {
        val raw = e.localizedMessage?.trim().orEmpty().ifEmpty { e.javaClass.simpleName }
        return if (!appCheckHint.isNullOrBlank() &&
            (raw.contains("403", ignoreCase = true) ||
                raw.contains("app check", ignoreCase = true) ||
                raw.contains("attestation", ignoreCase = true) ||
                raw.contains("unauthorized", ignoreCase = true))
        ) {
            IllegalStateException("$raw — $appCheckHint", e)
        } else {
            e
        }
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

    private val healthIntelligenceSchema = Schema.obj(
        properties = mapOf(
            "headline" to Schema.string(
                description = "One warm, concise headline summarizing the authorized recorded period."
            ),
            "recordedFactsSummary" to Schema.string(
                description = "Concise summary stating ONLY recorded user metrics from context, explicitly noting unlogged or privacy-excluded modules."
            ),
            "observedChanges" to Schema.array(
                items = Schema.string(),
                description = "2 to 5 bullet observations comparing current vs previous period strictly based on recorded values."
            ),
            "personalizedSuggestions" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 gentle, non-diagnostic AI wellness suggestions clearly phrased as optional suggestions."
            ),
            "dailyPlanHighlights" to Schema.array(
                items = Schema.string(),
                description = "2 to 3 actionable morning/midday/evening wellness steps tied to missed routines or incomplete goals."
            ),
            "uncertaintyAndCoverageNote" to Schema.string(
                description = "Explicit statement of sparse data, missing logs, or privacy-excluded categories."
            ),
            "disclaimer" to Schema.string(
                description = "Brief non-diagnostic wellness disclaimer stating AI suggestions are informational only."
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

    private val healthIntelligenceModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = healthIntelligenceSchema
            }
        )
    }

    private val reportExplanationSchema = Schema.obj(
        properties = mapOf(
            "plainLanguageSummary" to Schema.string(
                description = "Clear, calm plain-language explanation of what this health report or prescription describes, without diagnosing or changing doses."
            ),
            "keyObservations" to Schema.array(
                items = Schema.string(),
                description = "2 to 6 key items, values, or instructions strictly visible in the uploaded document or metadata."
            ),
            "questionsForDoctor" to Schema.array(
                items = Schema.string(),
                description = "2 to 4 helpful questions the patient can ask their healthcare professional."
            ),
            "disclaimer" to Schema.string(
                description = "Explicit statement that this explanation is informational only and not a substitute for a qualified medical professional."
            )
        )
    )

    private val reportExplanationModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = reportExplanationSchema
            }
        )
    }

    private val indianNutritionSchema = Schema.obj(
        properties = mapOf(
            "planTitle" to Schema.string(
                description = "Warm regional Indian nutrition plan title e.g. 'South Indian Balanced Day Plan'"
            ),
            "regionalNotes" to Schema.string(
                description = "2-3 concise sentences explaining how this regional Indian plan supports the user's goal, macro targets, and hydration."
            ),
            "groceryHighlights" to Schema.array(
                items = Schema.string(),
                description = "3 to 6 practical Indian kitchen staples or fresh produce needed for this plan."
            ),
            "meals" to Schema.array(
                items = Schema.obj(
                    properties = mapOf(
                        "mealType" to Schema.enumeration(
                            values = listOf("Breakfast", "Snack", "Lunch", "Dinner"),
                            description = "App logging category"
                        ),
                        "slotLabel" to Schema.string(
                            description = "Display slot e.g. 'Breakfast', 'Mid-Morning', 'Lunch', 'Evening Snack', 'Dinner'"
                        ),
                        "dishName" to Schema.string(
                            description = "Authentic Indian dish name matching the requested region and diet preference"
                        ),
                        "portionLabel" to Schema.string(
                            description = "Practical Indian household portion e.g. '2 phulkas + 1 katori dal + cucumber salad'"
                        ),
                        "calories" to Schema.integer(description = "Estimated kcal for this portion"),
                        "proteinGrams" to Schema.integer(description = "Estimated protein in grams"),
                        "carbsGrams" to Schema.integer(description = "Estimated carbs in grams"),
                        "fatGrams" to Schema.integer(description = "Estimated fat in grams"),
                        "prepTip" to Schema.string(description = "Quick kitchen or nutrition tip (1 short sentence)")
                    )
                ),
                description = "5 structured Indian meal slots (Breakfast, Mid-Morning, Lunch, Evening Snack, Dinner)."
            ),
            "disclaimer" to Schema.string(
                description = "Informational Indian wellness nutrition disclaimer stating this is not medical nutrition therapy."
            )
        )
    )

    private val indianNutritionModel by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = indianNutritionSchema
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
        var appCheckHint: String? = null
        val accumulatedJson = StringBuilder()
        val personalKey = activePersonalApiKey()
        return try {
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

            if (personalKey != null) {
                onStageUpdate("Connecting via Personal Gemini API Key (${languageOption.label})...")
                val rawJson = withTimeout(60_000L) {
                    directGeminiClient.generateContent(
                        apiKey = personalKey,
                        promptText = prompt,
                        responseSchema = DirectGeminiRestClient.assistantResponseSchema()
                    ).getOrThrow()
                }
                val parsed = parseAssistantJson(rawJson)
                onPartialPreview(parsed.replyText)
                return Result.success(parsed)
            }

            onStageUpdate("Checking Firebase & App Check...")
            if (appContext != null && !ensureFirebaseInitialized()) {
                return Result.failure(
                    IllegalStateException(
                        "AI Voice needs Firebase configuration or a Personal Gemini API Key. " +
                            "Open Settings -> Cloud AI Connection to enter your own free Gemini API key."
                    )
                )
            }

            if (appContext != null) {
                val (passed, hint) = probeAppCheckNonBlocking()
                appCheckPassed = passed
                appCheckHint = hint
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
            Result.failure(enrichAiFailure(e, appCheckHint))
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
        var appCheckPassed = false
        var appCheckHint: String? = null
        val accumulatedJson = StringBuilder()
        val personalKey = activePersonalApiKey()
        return try {
            val prompt = buildString {
                appendLine("You are LifeWeave Personal Health Intelligence.")
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

            if (personalKey != null) {
                onStageUpdate("Synthesizing $timeframeName insights via Personal Gemini API Key...")
                val rawJson = withTimeout(60_000L) {
                    directGeminiClient.generateContent(
                        apiKey = personalKey,
                        promptText = prompt,
                        responseSchema = DirectGeminiRestClient.healthIntelligenceResponseSchema()
                    ).getOrThrow()
                }
                val parsed = parseHealthIntelligenceJson(rawJson, timeframeName)
                onPartialPreview(parsed.headline)
                return Result.success(parsed)
            }

            onStageUpdate("Checking Firebase & App Check...")
            if (appContext != null && !ensureFirebaseInitialized()) {
                return Result.failure(
                    IllegalStateException(
                        "Personal Health Intelligence needs Firebase config or a Personal Gemini API Key in Settings."
                    )
                )
            }

            if (appContext != null) {
                val (passed, hint) = probeAppCheckNonBlocking()
                appCheckPassed = passed
                appCheckHint = hint
            }

            onStageUpdate("Synthesizing $timeframeName insights in ${languageOption.label}...")
            withTimeout(60_000L) {
                healthIntelligenceModel.generateContentStream(prompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulatedJson.append(chunkText)
                        val preview = extractStreamingFieldPreview(accumulatedJson.toString(), "headline")
                        if (preview.isNotBlank()) {
                            onPartialPreview(preview)
                        }
                    }
                }
            }

            val rawJson = accumulatedJson.toString()
            if (rawJson.isBlank()) {
                throw IllegalStateException("AI returned an empty intelligence report.")
            }
            Result.success(parseHealthIntelligenceJson(rawJson, timeframeName))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Personal health intelligence stream timed out", e)
            val detail = if (appCheckPassed) {
                "AI synthesis timed out after 60s (App Check passed; received ${accumulatedJson.length} chars). Local insights below remain up to date."
            } else {
                "AI request timed out after 60 seconds while checking App Check."
            }
            Result.failure(IllegalStateException(detail))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Personal health intelligence synthesis failed", e)
            Result.failure(enrichAiFailure(e, appCheckHint))
        }
    }

    suspend fun extractPrescriptionFromUri(
        context: Context,
        imageUri: Uri,
        languageOption: VoiceLanguageOption,
        onStageUpdate: (String) -> Unit = {},
        onPartialPreview: (String) -> Unit = {}
    ): Result<ExtractedPrescriptionResult> {
        var appCheckPassed = false
        var appCheckHint: String? = null
        val accumulatedJson = StringBuilder()
        val personalKey = activePersonalApiKey()
        return try {
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

            if (personalKey != null) {
                onStageUpdate("Extracting prescription via Personal Gemini API Key...")
                val jpegPart = DirectGeminiRestClient.encodeBitmapAsJpegPart(bitmap)
                val rawJson = withTimeout(60_000L) {
                    directGeminiClient.generateContent(
                        apiKey = personalKey,
                        promptText = promptText,
                        inlinePart = jpegPart,
                        responseSchema = DirectGeminiRestClient.prescriptionResponseSchema()
                    ).getOrThrow()
                }
                val parsed = parsePrescriptionJson(rawJson)
                onPartialPreview(parsed.summary)
                return Result.success(parsed)
            }

            onStageUpdate("Checking Firebase & App Check...")
            if (appContext != null && !ensureFirebaseInitialized()) {
                return Result.failure(
                    IllegalStateException(
                        "Prescription Scanner needs Firebase configuration or a Personal Gemini API Key in Settings."
                    )
                )
            }

            if (appContext != null) {
                val (passed, hint) = probeAppCheckNonBlocking()
                appCheckPassed = passed
                appCheckHint = hint
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
            Result.failure(enrichAiFailure(e, appCheckHint))
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
        var appCheckPassed = false
        var appCheckHint: String? = null
        val accumulatedJson = StringBuilder()
        val personalKey = activePersonalApiKey()
        return try {
            val imageBitmap: Bitmap? = if (fileBytes != null && mimeType.startsWith("image/", ignoreCase = true)) {
                withContext(Dispatchers.IO) {
                    decodeScaledBitmapFromBytes(fileBytes, maxDimension = 1280)
                }
            } else null

            val promptText = buildString {
                appendLine("You are explaining a personal health document in calm, plain ${languageOption.label} (${languageOption.tag}).")
                appendLine("Document Title: $title")
                appendLine("Category: $category")
                if (providerOrLab.isNotBlank()) appendLine("Provider/Lab: $providerOrLab")
                if (notes.isNotBlank()) appendLine("User Notes: $notes")
                appendLine()
                appendLine("STRICT SAFETY RULES:")
                appendLine("1. Explain medical terms and lab values clearly for a layperson using ONLY details visible in the document or metadata above.")
                appendLine("2. NEVER invent values, NEVER diagnose diseases, and NEVER suggest changing medication dosages or schedules.")
                appendLine("3. Always state clearly in 'disclaimer' that this is informational only and NOT a substitute for a qualified medical professional.")
            }

            val maxInlinePdfBytes = 19 * 1024 * 1024
            val canAttachPdfInline = fileBytes != null &&
                mimeType.equals("application/pdf", ignoreCase = true) &&
                fileBytes.size <= maxInlinePdfBytes
            if (fileBytes != null && mimeType.equals("application/pdf", ignoreCase = true) && !canAttachPdfInline) {
                Log.w(TAG, "PDF size (${fileBytes.size} bytes) exceeds 19MB inline limit; analyzing metadata and notes.")
            }

            val finalPromptText = if (!canAttachPdfInline && fileBytes != null && mimeType.equals("application/pdf", ignoreCase = true)) {
                "$promptText\nNote: The attached PDF exceeds the 19 MB direct upload limit, so summarize conservatively from the provided title, category, provider, and notes only, and mention that uploading a smaller PDF or photo enables full page analysis."
            } else {
                promptText
            }

            if (personalKey != null) {
                onStageUpdate("Analyzing $category via Personal Gemini API Key...")
                val inlinePart = when {
                    imageBitmap != null -> DirectGeminiRestClient.encodeBitmapAsJpegPart(imageBitmap)
                    canAttachPdfInline -> DirectGeminiInlinePart("application/pdf", fileBytes!!)
                    else -> null
                }
                val rawJson = withTimeout(60_000L) {
                    directGeminiClient.generateContent(
                        apiKey = personalKey,
                        promptText = finalPromptText,
                        inlinePart = inlinePart,
                        responseSchema = DirectGeminiRestClient.reportExplanationResponseSchema()
                    ).getOrThrow()
                }
                val parsed = parseReportExplanationJson(rawJson, title)
                onPartialPreview(parsed.plainLanguageSummary)
                return Result.success(parsed)
            }

            onStageUpdate("Checking Firebase & App Check...")
            if (appContext != null && !ensureFirebaseInitialized()) {
                return Result.failure(
                    IllegalStateException(
                        "Report AI Explainer needs Firebase config or a Personal Gemini API Key in Settings."
                    )
                )
            }

            if (appContext != null) {
                val (passed, hint) = probeAppCheckNonBlocking()
                appCheckPassed = passed
                appCheckHint = hint
            }

            val inputContent = content {
                if (imageBitmap != null) {
                    image(imageBitmap)
                } else if (canAttachPdfInline) {
                    inlineData(fileBytes!!, "application/pdf")
                }
                text(finalPromptText)
            }

            onStageUpdate("Analyzing $category in ${languageOption.label}...")
            withTimeout(60_000L) {
                reportExplanationModel.generateContentStream(inputContent).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulatedJson.append(chunkText)
                        val preview = extractStreamingFieldPreview(accumulatedJson.toString(), "plainLanguageSummary")
                        if (preview.isNotBlank()) {
                            onPartialPreview(preview)
                        }
                    }
                }
            }

            val rawJson = accumulatedJson.toString()
            if (rawJson.isBlank()) {
                throw IllegalStateException("AI returned an empty report explanation.")
            }
            Result.success(parseReportExplanationJson(rawJson, title))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Report explanation stream timed out", e)
            val detail = if (appCheckPassed) {
                "Document explanation timed out after 60s (received ${accumulatedJson.length} chars)."
            } else {
                "Document explanation timed out while verifying App Check."
            }
            Result.failure(IllegalStateException(detail))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Document explanation failed", e)
            Result.failure(enrichAiFailure(e, appCheckHint))
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
        var appCheckPassed = false
        var appCheckHint: String? = null
        val accumulatedJson = StringBuilder()
        val personalKey = activePersonalApiKey()
        return try {
            val allergenClause = if (avoidAllergens.isEmpty()) {
                "No food allergies recorded in vault."
            } else {
                "CRITICAL: Strictly exclude these allergens and all dishes containing them: ${avoidAllergens.joinToString(", ")}."
            }

            val prompt = buildString {
                appendLine("You are LifeWeave's Indian Home-Kitchen Nutrition Planner.")
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

            if (personalKey != null) {
                onStageUpdate("Planning $regionLabel meals via Personal Gemini API Key...")
                val rawJson = withTimeout(60_000L) {
                    directGeminiClient.generateContent(
                        apiKey = personalKey,
                        promptText = prompt,
                        responseSchema = DirectGeminiRestClient.indianNutritionResponseSchema()
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
                return Result.success(parsed)
            }

            onStageUpdate("Checking Firebase & App Check...")
            if (appContext != null && !ensureFirebaseInitialized()) {
                return Result.failure(
                    IllegalStateException("Cloud Indian Meal Planner needs Firebase config or a Personal Gemini API Key in Settings.")
                )
            }

            if (appContext != null) {
                val (passed, hint) = probeAppCheckNonBlocking()
                appCheckPassed = passed
                appCheckHint = hint
            }

            onStageUpdate("Planning $regionLabel meals in ${languageOption.label}...")
            withTimeout(60_000L) {
                indianNutritionModel.generateContentStream(prompt).collect { chunk ->
                    val chunkText = chunk.text.orEmpty()
                    if (chunkText.isNotEmpty()) {
                        accumulatedJson.append(chunkText)
                        val preview = extractStreamingFieldPreview(accumulatedJson.toString(), "planTitle")
                        if (preview.isNotBlank()) {
                            onPartialPreview(preview)
                        }
                    }
                }
            }

            val rawJson = accumulatedJson.toString()
            if (rawJson.isBlank()) {
                throw IllegalStateException("AI returned an empty nutrition plan.")
            }
            Result.success(
                parseIndianNutritionPlanJson(
                    jsonString = rawJson,
                    regionLabel = regionLabel,
                    dietaryLabel = dietaryLabel,
                    goalLabel = goalLabel,
                    avoidedAllergens = avoidAllergens
                )
            )
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Indian nutrition plan stream timed out", e)
            val detail = if (appCheckPassed) {
                "Indian meal plan timed out after 60s (received ${accumulatedJson.length} chars)."
            } else {
                "Indian meal plan timed out while verifying App Check."
            }
            Result.failure(IllegalStateException(detail))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Indian nutrition planner failed", e)
            Result.failure(enrichAiFailure(e, appCheckHint))
        }
    }

    suspend fun runDiagnostics(): AiDiagnosticReport {
        val personalKey = activePersonalApiKey()
        if (personalKey != null) {
            return directGeminiClient.ping(personalKey).fold(
                onSuccess = { ping ->
                    AiDiagnosticReport(
                        isHealthy = true,
                        firebaseStatus = "Bypassed (Personal Gemini API Key active)",
                        appCheckStatus = "Not required with Personal API Key",
                        geminiStatus = "Connected via ${ping.modelUsed} (${ping.latencyMs}ms): ${ping.sampleText}",
                        summary = "Personal Gemini API Key is connected and ready.",
                        recommendation = "All cloud AI features (Voice, Prescription Scanner, Lab Explainer, Briefings, Meal Planner) are active on this device."
                    )
                },
                onFailure = { err ->
                    val msg = err.localizedMessage?.trim().orEmpty().ifEmpty { err.javaClass.simpleName }
                    AiDiagnosticReport(
                        isHealthy = false,
                        firebaseStatus = "Bypassed (Personal Gemini API Key active)",
                        appCheckStatus = "Not required with Personal API Key",
                        geminiStatus = "Error: $msg",
                        summary = "Personal Gemini API Key test failed.",
                        recommendation = "Verify or replace your Personal Gemini API Key in Cloud AI Connection below, or remove it to return to Firebase AI Logic."
                    )
                }
            )
        }

        val ctx = appContext
        if (ctx != null && !ensureFirebaseInitialized()) {
            return AiDiagnosticReport(
                isHealthy = false,
                firebaseStatus = "Missing google-services.json",
                appCheckStatus = "Skipped (Firebase uninitialized)",
                geminiStatus = "Skipped",
                summary = "Firebase config is missing from this APK.",
                recommendation = "Enter your own free Personal Gemini API Key in Cloud AI Connection below to run AI without Firebase."
            )
        }

        val firebaseStatus = runCatching {
            val app = FirebaseApp.getInstance()
            "Ready (project: ${app.options.projectId ?: "unknown"})"
        }.getOrElse { "Not initialized" }

        val checkResult = verifyAppCheckToken(ctx, timeoutMs = 10_000L)
        val appCheckDesc = when (checkResult) {
            is AppCheckVerificationResult.Success ->
                "OK (${checkResult.providerLabel}, ${checkResult.latencyMs}ms, ttl ${checkResult.expiresInSeconds}s)"
            is AppCheckVerificationResult.Timeout ->
                "Timed out (${checkResult.providerLabel})"
            is AppCheckVerificationResult.Failure ->
                "Not attested (${checkResult.providerLabel}): ${checkResult.errorMessage}"
        }
        val appCheckHint = when (checkResult) {
            is AppCheckVerificationResult.Success -> null
            is AppCheckVerificationResult.Timeout -> checkResult.diagnosticMessage
            is AppCheckVerificationResult.Failure -> checkResult.diagnosticMessage
        }

        val geminiStart = System.currentTimeMillis()
        return try {
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
                summary = "Gemini streaming is working on this device.",
                recommendation = if (appCheckHint == null) {
                    "You can now use Voice, Report Explainer, and Prescription Scanner."
                } else {
                    "Cloud AI succeeded! You can also enter a Personal Gemini API Key below if needed."
                }
            )
        } catch (e: TimeoutCancellationException) {
            AiDiagnosticReport(
                isHealthy = false,
                firebaseStatus = firebaseStatus,
                appCheckStatus = appCheckDesc,
                geminiStatus = "Stream timed out after 20s",
                summary = "Gemini streaming did not respond within 20s.",
                recommendation = appCheckHint ?: "Check mobile/Wi-Fi internet speed, or enter your Personal Gemini API Key below."
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
                summary = "Gemini could not connect via App Check on this sideloaded build.",
                recommendation = "No debug token needed: enter your free Personal Gemini API Key in Cloud AI Connection below to activate AI immediately."
            )
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

        fun parseHealthIntelligenceJson(
            jsonString: String,
            defaultTimeframe: String = "WEEKLY"
        ): AiHealthIntelligenceReport {
            val json = JSONObject(jsonString)
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
            val json = JSONObject(jsonString)
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
            val json = JSONObject(jsonString)
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
