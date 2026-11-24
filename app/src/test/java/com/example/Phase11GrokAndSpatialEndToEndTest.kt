package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.AiActionType
import com.example.ai.GrokApiClient
import com.example.ai.GrokHttpResponse
import com.example.ai.LifeWeaveAiService
import com.example.ai.SupportedVoiceLanguages
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.HealthVaultRepository
import com.example.data.repository.PeriodRepository
import com.example.data.repository.UserProfileRepository
import com.example.data.repository.WaterRepository
import com.example.domain.CycleCalculator
import com.example.domain.DateEpochUtils
import com.example.domain.IndianDietaryPreference
import com.example.domain.IndianRegion
import com.example.domain.IntelligenceTimeframe
import com.example.domain.PersonalHealthIntelligenceEngine
import com.example.security.AiDataAuthorization
import com.example.security.EncryptedDocumentVault
import com.example.security.KeystoreVaultManager
import com.example.security.PersonalAiKeyState
import com.example.security.PersonalAiKeyStore
import com.example.ui.theme.AiStudioMidnight
import com.example.ui.theme.BotanicalSagePrimary
import com.example.ui.theme.CobaltBlueSecondary
import com.example.ui.theme.DeepPineBackground
import com.example.ui.theme.HeroForestDark
import com.example.ui.theme.HeroTealDeep
import com.example.ui.theme.LuminousBlueSecondary
import com.example.ui.theme.RadiantOrangePrimary
import com.example.ui.theme.SolarOrangePrimary
import com.example.ui.theme.SpatialBlueAccent
import com.example.ui.theme.SpatialDarkBase
import com.example.ui.theme.SpatialGlassDark
import com.example.ui.theme.SpatialGlassLight
import com.example.ui.theme.SpatialGrokAccent
import com.example.ui.theme.SpatialHeroBlueDeep
import com.example.ui.theme.SpatialHeroCobalt
import com.example.ui.theme.SpatialHeroOrangeGlow
import com.example.ui.theme.SpatialLightBase
import com.example.ui.theme.WarmLinenBackground
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

/**
 * Phase 11 End-to-End Verification Suite:
 * Validates all 5 xAI Grok AI workflows, BuildConfig.XAI_API_KEY fallback vs Personal Key precedence,
 * invalid/Gemini key rejection guards, end-to-end Room + Repository integration across LifeWeave modules,
 * and Spatial Orange & Blue theme tokens.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class Phase11GrokAndSpatialEndToEndTest {

    private fun createTestVaultManager(): KeystoreVaultManager {
        val keyGen = KeyGenerator.getInstance("AES").apply { init(256) }
        val secretKey = keyGen.generateKey()
        return KeystoreVaultManager(testKeyProvider = { secretKey })
    }

    private fun wrapGrokChoice(content: String): String =
        JSONObject()
            .put(
                "choices",
                JSONArray().put(
                    JSONObject().put(
                        "message",
                        JSONObject().put("role", "assistant").put("content", content)
                    )
                )
            )
            .toString()

    @Test
    fun grokKeyGuards_rejectLegacyGeminiAndPlaceholderKeysBeforeNetworkCall() {
        assertFalse(PersonalAiKeyState.isUsableGrokKey(""))
        assertFalse(PersonalAiKeyState.isUsableGrokKey("XAI_API_KEY_DEFAULT_VALUE"))
        assertFalse(PersonalAiKeyState.isUsableGrokKey("AIzaSyTestExampleKey9876543210ABCD9xQ2"))
        assertFalse(PersonalAiKeyState.isUsableGrokKey("AQ.Ab8RN6ExampleGoogleAIStudioKey12345JyfQ"))
        assertTrue(PersonalAiKeyState.isUsableGrokKey("xai-ValidGrokKey1234567890ABCDEF"))

        val geminiErr = PersonalAiKeyStore.validateKeyFormat("AIzaSyTestExampleKey9876543210ABCD9xQ2")
        assertTrue(geminiErr != null && geminiErr.contains("Gemini", ignoreCase = true))

        val mapped400 = GrokApiClient.mapHttpError(
            statusCode = 400,
            responseBody = """{"error":"Incorrect API key provided. You can obtain an API key from https://console.x.ai."}""",
            modelName = GrokApiClient.DEFAULT_TEXT_MODEL
        )
        assertTrue(mapped400.contains("rejected", ignoreCase = true))
        assertTrue(mapped400.contains("xai-", ignoreCase = true))
    }

    @Test
    fun grokEndToEnd_executesAllFiveAiWorkflowsWithBuildConfigSecretFallback() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val store = PersonalAiKeyStore(context, createTestVaultManager())
        store.clearPersonalKey()

        val bundledKey = "xai-BuildConfigSecretFallbackKey1234567890"
        val observedSchemas = mutableListOf<String>()
        val observedModels = mutableListOf<String>()

        val fakeGrokClient = GrokApiClient(
            delayFn = { _ -> },
            httpExecutor = { endpointUrl, apiKey, bodyJson, _ ->
                assertEquals(GrokApiClient.CHAT_COMPLETIONS_URL, endpointUrl)
                assertEquals(bundledKey, apiKey)
                val req = JSONObject(bodyJson)
                observedModels += req.getString("model")
                val schemaName = req.optJSONObject("response_format")
                    ?.optJSONObject("json_schema")
                    ?.optString("name", "")
                    .orEmpty()
                if (schemaName.isNotEmpty()) observedSchemas += schemaName

                val replyJson = when (schemaName) {
                    "lifeweave_assistant_outcome" -> JSONObject()
                        .put("assistantReply", "Logged 300 ml water and 450 kcal lunch via Grok!")
                        .put(
                            "actions",
                            JSONArray()
                                .put(
                                    JSONObject()
                                        .put("actionType", "LOG_WATER")
                                        .put("titleOrName", "Water")
                                        .put("amountOrValue", 300)
                                        .put("secondaryText", "Water")
                                )
                                .put(
                                    JSONObject()
                                        .put("actionType", "LOG_MEAL")
                                        .put("titleOrName", "Dal Tadka & 2 Phulkas")
                                        .put("amountOrValue", 450)
                                        .put("secondaryText", "Lunch")
                                        .put("calories", 450)
                                )
                        ).toString()

                    "lifeweave_health_intelligence" -> JSONObject()
                        .put("headline", "Hydration up 18% this week")
                        .put("recordedFactsSummary", "Averaged 2300 ml water and 7.4h sleep across 7 logged days.")
                        .put("observedChanges", JSONArray().put("Hydration increased +350 ml/day vs prior week"))
                        .put("personalizedSuggestions", JSONArray().put("Keep evening screen wind-down 30 mins earlier"))
                        .put("dailyPlanHighlights", JSONArray().put("Drink 500 ml before 11 AM"))
                        .put("uncertaintyAndCoverageNote", "All 7 days recorded.")
                        .put("disclaimer", "Wellness guidance only — not medical advice.")
                        .toString()

                    "lifeweave_report_explanation" -> JSONObject()
                        .put("plainLanguageSummary", "Your CBC report shows hemoglobin and platelet counts in standard reference intervals.")
                        .put("keyObservations", JSONArray().put("Hemoglobin 13.8 g/dL recorded").put("Vitamin D borderline low"))
                        .put("questionsForDoctor", JSONArray().put("Should I check Vitamin D dosage with sunlight exposure?"))
                        .put("disclaimer", "Informational wellness summary only — discuss lab results with your clinician.")
                        .toString()

                    "lifeweave_indian_nutrition" -> JSONObject()
                        .put("planTitle", "South Indian Balanced High-Protein Day")
                        .put("regionalNotes", "Uses fermented idli batter, sambar lentils, and peanut-free chutney.")
                        .put("groceryHighlights", JSONArray().put("Toor dal").put("Ragi flour").put("Paneer"))
                        .put(
                            "meals",
                            JSONArray().put(
                                JSONObject()
                                    .put("mealType", "Breakfast")
                                    .put("slotLabel", "08:00 AM")
                                    .put("dishName", "3 Ragi Idlis with Drumstick Sambar")
                                    .put("portionLabel", "3 idlis + 1 katori sambar")
                                    .put("calories", 380)
                                    .put("proteinGrams", 14)
                                    .put("carbsGrams", 64)
                                    .put("fatGrams", 6)
                                    .put("prepTip", "Steam fresh for light digestion")
                            )
                        )
                        .put("disclaimer", "Wellness meal planning only.")
                        .toString()

                    else -> "Grok Ready"
                }
                GrokHttpResponse(200, wrapGrokChoice(replyJson))
            }
        )

        val service = LifeWeaveAiService(
            context = context,
            personalAiKeyStore = store,
            grokClient = fakeGrokClient,
            buildConfigKeyProvider = { bundledKey }
        )

        val english = SupportedVoiceLanguages.options.first()

        // 1. Voice / Text Assistant multi-action command
        val assistantOutcome = service.processVoiceOrTextCommand(
            userMessage = "I drank 300 ml water and ate dal tadka with 2 phulkas for lunch",
            languageOption = english,
            wellnessContextSummary = "Hydration: 1200 / 2500 ml"
        ).getOrThrow()
        assertEquals(2, assistantOutcome.actions.size)
        assertEquals(AiActionType.LOG_WATER, assistantOutcome.actions[0].actionType)
        assertEquals(AiActionType.LOG_MEAL, assistantOutcome.actions[1].actionType)
        assertEquals(450, assistantOutcome.actions[1].calories)

        // 2. Personal Health Intelligence synthesis
        val intelReport = service.generatePersonalHealthIntelligence(
            timeframeName = IntelligenceTimeframe.WEEKLY.name,
            languageOption = english,
            groundedWellnessContext = "7-day hydration avg 2300 ml; sleep avg 7.4h"
        ).getOrThrow()
        assertTrue(intelReport.isFromCloudAi)
        assertEquals("Hydration up 18% this week", intelReport.headline)
        assertEquals(1, intelReport.observedChanges.size)

        // 3. Health Report & Document Explainer (metadata + text)
        val explanation = service.explainHealthDocument(
            title = "Annual CBC Blood Panel",
            category = "Lab Report",
            providerOrLab = "Apollo Diagnostics",
            notes = "Hb 13.8 g/dL, Vitamin D 22 ng/mL",
            fileBytes = null,
            mimeType = "",
            languageOption = english
        ).getOrThrow()
        assertTrue(explanation.isFromCloudAi)
        assertTrue(explanation.plainLanguageSummary.contains("CBC"))
        assertEquals(2, explanation.keyObservations.size)

        // 4. Regional Indian Nutrition Planner
        val mealPlan = service.generateIndianNutritionPlan(
            regionLabel = IndianRegion.SOUTH.label,
            dietaryLabel = IndianDietaryPreference.VEGETARIAN.label,
            goalLabel = "Balanced Energy",
            targetCalories = 1950,
            targetProteinGrams = 75,
            targetCarbsGrams = 245,
            targetFatGrams = 58,
            alreadyLoggedTodaySummary = "No meals logged yet today.",
            avoidAllergens = listOf("Peanuts"),
            languageOption = english
        ).getOrThrow()
        assertTrue(mealPlan.isFromCloudAi)
        assertEquals("South Indian Balanced High-Protein Day", mealPlan.planTitle)
        assertEquals(1, mealPlan.meals.size)
        assertEquals(380, mealPlan.meals.first().calories)

        // 5. Prescription JSON parser (handles markdown-fenced JSON from vision model cleanly)
        val parsedRx = LifeWeaveAiService.parsePrescriptionJson(
            """
            ```json
            {
              "summary": "7-day vitamin & hydration support",
              "doctorName": "Dr. Meera Nair",
              "specialty": "Internal Medicine",
              "clinicOrHospital": "Aster Care Clinic",
              "followUpDaysFromToday": 14,
              "followUpNotes": "Repeat Vitamin D test",
              "medicines": [
                {
                  "name": "Cholecalciferol 60K",
                  "dosage": "1 capsule",
                  "dosesPerDay": 1,
                  "intervalHours": 24,
                  "scheduleTime24h": "09:00",
                  "mealRelation": "After Meal",
                  "notes": "Take with warm milk"
                }
              ]
            }
            ```
            """.trimIndent()
        )
        assertEquals("Dr. Meera Nair", parsedRx.doctorName)
        assertEquals(1, parsedRx.medicines.size)
        assertEquals("Cholecalciferol 60K", parsedRx.medicines.first().name)

        // 6. Diagnostics verifies BuildConfig App Secret route
        val diag = service.runDiagnostics()
        assertTrue(diag.isHealthy)
        assertTrue(diag.authStatus.contains("App Secret", ignoreCase = true))
        assertTrue(diag.grokStatus.contains(GrokApiClient.DEFAULT_TEXT_MODEL))

        // Verify all 4 live schema names were requested via Grok text model
        assertTrue(
            observedSchemas.containsAll(
                listOf(
                    "lifeweave_assistant_outcome",
                    "lifeweave_health_intelligence",
                    "lifeweave_report_explanation",
                    "lifeweave_indian_nutrition"
                )
            )
        )
        assertTrue(observedModels.all { it == GrokApiClient.DEFAULT_TEXT_MODEL })
    }

    @Test
    fun endToEndModules_roomRepositoriesAndHealthIntelligenceWorkCohesively() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val vaultManager = createTestVaultManager()
            val profileRepo = UserProfileRepository(db)
            val waterRepo = WaterRepository(db)
            val periodRepo = PeriodRepository(db)
            val allModulesRepo = AllModulesRepository(db)
            val healthVaultRepo = HealthVaultRepository(
                database = db,
                encryptedVault = EncryptedDocumentVault(context, vaultManager)
            )

            // 1. Create profile
            val profileId = profileRepo.saveOrUpdateProfile(
                UserProfileEntity(
                    fullName = "Aarav Rao",
                    age = 31,
                    genderIdentity = "Female",
                    dailyWaterGoalMl = 2400,
                    cycleTrackingEnabled = true,
                    averageCycleLengthDays = 28
                )
            )
            assertTrue(profileId > 0L)

            // 2. Hydration logging
            waterRepo.logWater(profileId, 500, "Water")
            waterRepo.logWater(profileId, 250, "Electrolyte")
            val todayWaterRecords = waterRepo.observeTodayRecords(profileId).first()
            assertEquals(750, todayWaterRecords.sumOf { it.amountMl })

            // 3. Period logging
            val nowEpochDay = DateEpochUtils.todayEpochDay()
            periodRepo.saveRecord(
                PeriodRecordEntity(
                    profileId = profileId,
                    startDateEpochDay = nowEpochDay - 4L,
                    endDateEpochDay = nowEpochDay,
                    flowIntensity = "Medium",
                    symptomsCsv = "Cramps",
                    mood = "Calm",
                    notes = "Normal cycle"
                )
            )
            val periodLogs = periodRepo.observeRecords(profileId).first()
            assertEquals(1, periodLogs.size)

            // 4. Medicines, Diet, Exercise, Sleep, Steps
            allModulesRepo.addMedicine(
                profileId = profileId,
                name = "Vitamin D3",
                dosage = "1000 IU",
                scheduleTime24h = "09:00",
                frequency = "Daily",
                dosesPerDay = 1,
                intervalHours = 24,
                mealRelation = "After Meal",
                stockQuantity = 30f
            )
            allModulesRepo.logMeal(profileId, "Breakfast", "2 Idlis & Sambar", 340, 12, 58, 5)
            allModulesRepo.logExercise(profileId, "Brisk Walk", "Cardio", 35, "Moderate", 190)
            allModulesRepo.logSleep(profileId, 7.5f, 4, "Restful")
            allModulesRepo.addTodaySteps(profileId, null, 6800)

            assertEquals(1, allModulesRepo.observeMedicines(profileId).first().size)
            assertEquals(1, allModulesRepo.observeDiet(profileId).first().size)
            assertEquals(1, allModulesRepo.observeExercises(profileId).first().size)
            assertEquals(1, allModulesRepo.observeSleep(profileId).first().size)
            assertEquals(6800, allModulesRepo.observeAllSteps(profileId).first().first().stepCount)

            // 5. Health Vault allergy & lab record
            healthVaultRepo.addAllergy(profileId, "Peanuts", "Hives", "High", "Carry antihistamine")
            healthVaultRepo.addLabResult(
                profileId = profileId,
                testName = "Hemoglobin",
                valueText = "13.4",
                unit = "g/dL",
                referenceRangeText = "12.0 - 15.5",
                statusFlag = "Normal",
                recordedEpochDay = nowEpochDay,
                labName = "Apollo Diagnostics"
            )
            assertEquals(1, healthVaultRepo.observeAllergies(profileId).first().size)
            assertEquals(1, healthVaultRepo.observeLabResults(profileId).first().size)

            // 6. Grounded Personal Health Intelligence Engine aggregates all recorded modules
            val profile = profileRepo.getActiveProfileSnapshot()
            val cycleInsights = CycleCalculator.analyze(
                recordsDesc = periodLogs,
                configuredCycleLength = profile?.averageCycleLengthDays ?: 28,
                configuredPeriodLength = profile?.averagePeriodDurationDays ?: 5,
                todayEpochDay = nowEpochDay
            )
            val snapshot = PersonalHealthIntelligenceEngine.analyze(
                profile = profile,
                authorization = AiDataAuthorization(),
                waterRecords60d = waterRepo.observeLast60Days(profileId).first(),
                sleepRecords = allModulesRepo.observeSleep(profileId).first(),
                exercises = allModulesRepo.observeExercises(profileId).first(),
                stepRecords = allModulesRepo.observeAllSteps(profileId).first(),
                dietRecords = allModulesRepo.observeDiet(profileId).first(),
                medicines = allModulesRepo.observeMedicines(profileId).first(),
                medicineIntakes60d = emptyList(),
                consultations = emptyList(),
                beautyRecords = emptyList(),
                wellbeingRecords = emptyList(),
                cycleInsights = cycleInsights
            )
            assertTrue(snapshot.authorizedModuleCount >= 5)
            assertTrue(snapshot.groundedAiPromptContext.contains("750"))
            assertFalse(snapshot.offlineReportFor(IntelligenceTimeframe.DAILY).recordedFactsSummary.isBlank())
        } finally {
            db.close()
        }
    }

    @Test
    fun spatialOrangeAndBlueThemeTokens_maintainWarmOrangeCoolBlueAndGlassTranslucency() {
        // Primary Orange tokens must be warm (red channel clearly dominant over blue)
        assertTrue(SolarOrangePrimary.red > 0.85f && SolarOrangePrimary.blue < 0.2f)
        assertTrue(RadiantOrangePrimary.red > 0.90f && RadiantOrangePrimary.blue < 0.35f)
        assertTrue(SpatialGrokAccent.red > 0.90f && SpatialGrokAccent.green > 0.55f)
        assertTrue(SpatialHeroOrangeGlow.red > 0.85f && SpatialHeroOrangeGlow.blue < 0.2f)

        // Secondary & Hero Cobalt Blue tokens must be blue-dominant
        assertTrue(CobaltBlueSecondary.blue > CobaltBlueSecondary.red && CobaltBlueSecondary.blue > 0.75f)
        assertTrue(LuminousBlueSecondary.blue > LuminousBlueSecondary.red && LuminousBlueSecondary.blue > 0.90f)
        assertTrue(SpatialHeroBlueDeep.blue > SpatialHeroBlueDeep.red)
        assertTrue(SpatialHeroCobalt.blue > SpatialHeroCobalt.red && SpatialHeroCobalt.blue > 0.5f)
        assertTrue(SpatialBlueAccent.blue > SpatialBlueAccent.red)

        // Spatial glass card surfaces MUST be translucent (alpha < 1f) so the Orange & Blue backdrop breathes through
        assertTrue(SpatialGlassLight.alpha in 0.70f..0.95f)
        assertTrue(SpatialGlassDark.alpha in 0.65f..0.90f)

        // Legacy screen aliases must map directly to the Spatial Orange & Blue palette (zero legacy emerald/linen drift)
        assertEquals(SpatialLightBase, WarmLinenBackground)
        assertEquals(SpatialDarkBase, DeepPineBackground)
        assertEquals(SolarOrangePrimary, BotanicalSagePrimary)
        assertEquals(SpatialHeroBlueDeep, HeroForestDark)
        assertEquals(SpatialHeroCobalt, HeroTealDeep)
        assertEquals(SpatialHeroBlueDeep, AiStudioMidnight)
    }
}
