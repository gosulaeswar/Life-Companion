package com.example.ai

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import com.google.firebase.Firebase
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

class LifeWeaveAiService {

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

    suspend fun processVoiceOrTextCommand(
        userMessage: String,
        languageOption: VoiceLanguageOption,
        wellnessContextSummary: String
    ): Result<AiAssistantOutcome> {
        return try {
            val prompt = buildString {
                appendLine("You are LifeWeave's friendly, voice-ready wellness assistant inside an Android health app.")
                appendLine("Respond naturally in this language: ${languageOption.label} (${languageOption.tag}). Keep spoken replies concise (2-4 sentences).")
                appendLine()
                appendLine("Available LifeWeave modules users can ask about or open:")
                appendLine("- water_tracker: 💧 Hydration goal, quick water logs, streaks")
                appendLine("- period_tracker: 🌸 Cycle & period tracker with phase guidance")
                appendLine("- medical_care: 💊 Medicines (daily doses, intervals like 6h/8h/12h/24h, meal timing, Taken/Skipped logs) & 🩺 Doctors + follow-ups")
                appendLine("- exercise_tracker: 🏃 Workouts, yoga, steps, sleep logs")
                appendLine("- diet_tracker: 🥗 Meals, coffee/drinks calories, protein/carbs/fats")
                appendLine("- connected_devices: ⌚ Sync Fitbit, Google Fit, Samsung Health, Garmin, Oura, Strava via Health Connect or Apple Health export.xml")
                appendLine("- daily_reminders: ⏰ Custom alarms & per-category audio tones")
                appendLine("- beauty_care: ✨ Skincare & haircare rituals")
                appendLine("- digital_wellbeing: 📵 Screen time cap & focus blocks")
                appendLine("- reports_analytics: 📊 Weekly trends")
                appendLine()
                appendLine("Current user live wellness snapshot:")
                appendLine(wellnessContextSummary)
                appendLine()
                appendLine("Action extraction rules:")
                appendLine("1. If user mentions drinking water (e.g. '200 ml water', 'two glasses of water'), add LOG_WATER with amountOrValue in ml (default 250 ml per glass) and secondaryText='Water'.")
                appendLine("2. If user mentions drinking coffee, tea, smoothie, juice, or eating food (e.g. 'I just had a coffee'), add LOG_MEAL with titleOrName (e.g. '☕ Coffee'), secondaryText='Snack' (or Breakfast/Lunch/Dinner), and sensible calories (e.g. 50 for coffee with milk, 350 for lunch). If it is a hydrating drink like coffee/tea/coconut water, you may ALSO add LOG_WATER (e.g. 180-200 ml, secondaryText='Herbal Tea' or 'Coffee') or keep it clear.")
                appendLine("3. If user mentions taking or adding a medicine (e.g. 'Add Vitamin D3 1000IU once a day after breakfast'), emit LOG_MEDICINE.")
                appendLine("4. If user mentions steps, walking/yoga/running, or sleeping hours, emit LOG_STEPS, LOG_EXERCISE, or LOG_SLEEP (hours*10 in amountOrValue).")
                appendLine("5. If user asks to open or navigate to a module, emit OPEN_MODULE with moduleKey in secondaryText.")
                appendLine("6. If user only asks a question or asks for app guidance, emit actions=[] (empty) or NONE and give helpful instructions in ${languageOption.label}.")
                appendLine()
                appendLine("User message: \"$userMessage\"")
            }

            val response = withTimeout(60_000L) {
                assistantModel.generateContent(prompt)
            }
            val rawJson = response.text ?: throw IllegalStateException("AI returned an empty response.")
            Result.success(parseAssistantJson(rawJson))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "AI assistant request timed out", e)
            Result.failure(IllegalStateException("AI request timed out after 60 seconds. Please try again."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Firebase AI assistant call failed", e)
            Result.failure(e)
        }
    }

    suspend fun generateMultilingualAppGuide(
        languageOption: VoiceLanguageOption,
        wellnessContextSummary: String
    ): Result<AiAssistantOutcome> {
        val guidePrompt =
            "Give me a warm, easy-to-follow spoken guided tour of LifeWeave in ${languageOption.label}. " +
                "Cover how to log water/coffee by voice, upload a doctor's prescription photo, connect Fitbit/Apple Health devices, and manage alarms."
        return processVoiceOrTextCommand(guidePrompt, languageOption, wellnessContextSummary)
    }

    suspend fun extractPrescriptionFromUri(
        context: Context,
        imageUri: Uri,
        languageOption: VoiceLanguageOption
    ): Result<ExtractedPrescriptionResult> {
        return try {
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

            val response = withTimeout(60_000L) {
                prescriptionModel.generateContent(
                    content {
                        image(bitmap)
                        text(promptText)
                    }
                )
            }
            val rawJson = response.text ?: throw IllegalStateException("AI returned empty prescription output.")
            Result.success(parsePrescriptionJson(rawJson))
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Prescription scan timed out", e)
            Result.failure(IllegalStateException("Prescription analysis timed out. Please try a clearer or smaller image."))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Prescription scan failed", e)
            Result.failure(e)
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
