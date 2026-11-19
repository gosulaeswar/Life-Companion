package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.ai.LifeWeaveAiService
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DietRecordEntity
import com.example.data.local.entity.DigitalWellbeingRecordEntity
import com.example.data.local.entity.DoctorConsultationEntity
import com.example.data.local.entity.ExerciseRecordEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.MedicineIntakeEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.data.repository.PrivacyBackupRepository
import com.example.domain.CycleCalculator
import com.example.domain.DateEpochUtils
import com.example.domain.IntelligenceTimeframe
import com.example.domain.PersonalHealthIntelligenceEngine
import com.example.domain.TrendDirection
import com.example.security.AiDataAuthorization
import com.example.security.KeystoreVaultManager
import com.example.security.LocalPreferencesManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase2HealthIntelligenceTest {

    private fun testAesKey() = KeyGenerator.getInstance("AES").apply {
        init(256)
    }.generateKey()

    private fun middayNowMs(): Long {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 16)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return cal.timeInMillis
    }

    @Test
    fun `weekly and monthly intelligence computes accurate trends, midnight bedtime spread, and routine gaps without inventing values`() {
        val nowMs = middayNowMs()
        val profile = UserProfileEntity(
            id = 1L,
            fullName = "Eswar",
            age = 29,
            genderIdentity = "Male",
            dailyWaterGoalMl = 2500,
            cycleTrackingEnabled = false
        )

        // Hydration: 2200 ml/day in last 7d vs 1600 ml/day in prior 7d -> IMPROVED
        val waterLogs = mutableListOf<WaterRecordEntity>()
        for (d in 1..6) {
            waterLogs += WaterRecordEntity(
                profileId = 1L,
                amountMl = 2200,
                dayKey = DateEpochUtils.dayKeyDaysAgo(d, nowMs),
                loggedAtEpochMs = nowMs - d * 86_400_000L
            )
        }
        // Today low (400 ml at 16:00 -> triggers hydration routine gap)
        waterLogs += WaterRecordEntity(
            profileId = 1L,
            amountMl = 400,
            dayKey = DateEpochUtils.todayDayKey(nowMs),
            loggedAtEpochMs = nowMs
        )
        for (d in 7..13) {
            waterLogs += WaterRecordEntity(
                profileId = 1L,
                amountMl = 1600,
                dayKey = DateEpochUtils.dayKeyDaysAgo(d, nowMs),
                loggedAtEpochMs = nowMs - d * 86_400_000L
            )
        }

        // Sleep: cross-midnight bedtimes 23:45 -> 00:15 (only 30m real spread!), 6.2h avg this week vs 7.5h prior week -> DECLINED
        fun sleepEntry(daysAgo: Int, bedHour: Int, bedMin: Int, durationHrs: Float): SleepRecordEntity {
            val bedCal = Calendar.getInstance().apply {
                timeInMillis = nowMs - daysAgo * 86_400_000L
                if (bedHour >= 12) add(Calendar.DAY_OF_YEAR, -1)
                set(Calendar.HOUR_OF_DAY, bedHour)
                set(Calendar.MINUTE, bedMin)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val durationMs = (durationHrs * 3_600_000f).toLong()
            return SleepRecordEntity(
                profileId = 1L,
                bedtimeEpochMs = bedCal.timeInMillis,
                wakeEpochMs = bedCal.timeInMillis + durationMs,
                qualityRating = 3
            )
        }

        val sleepRecords = listOf(
            sleepEntry(daysAgo = 0, bedHour = 23, bedMin = 45, durationHrs = 6.2f),
            sleepEntry(daysAgo = 1, bedHour = 0, bedMin = 15, durationHrs = 6.2f),
            sleepEntry(daysAgo = 8, bedHour = 23, bedMin = 30, durationHrs = 7.5f),
            sleepEntry(daysAgo = 9, bedHour = 23, bedMin = 40, durationHrs = 7.5f)
        )

        val exercises = (0..3).map { d ->
            ExerciseRecordEntity(
                profileId = 1L,
                activityType = "Run",
                category = "Exercise",
                durationMinutes = 35,
                intensity = "Moderate",
                caloriesBurned = 260,
                loggedAtEpochMs = nowMs - d * 86_400_000L
            )
        }
        val stepRecords = listOf(
            StepRecordEntity(profileId = 1L, dayKey = DateEpochUtils.todayDayKey(nowMs), stepCount = 7200, distanceMeters = 5300),
            StepRecordEntity(profileId = 1L, dayKey = DateEpochUtils.dayKeyDaysAgo(1, nowMs), stepCount = 7800, distanceMeters = 5700),
            StepRecordEntity(profileId = 1L, dayKey = DateEpochUtils.dayKeyDaysAgo(8, nowMs), stepCount = 5000, distanceMeters = 3700)
        )
        val med = MedicineEntity(
            id = 10L,
            profileId = 1L,
            name = "Vitamin D3",
            dosage = "1000IU",
            scheduleTime24h = "08:00",
            frequency = "Once Daily",
            dosesPerDay = 1,
            isActive = true
        )
        val wellbeing = listOf(
            DigitalWellbeingRecordEntity(profileId = 1L, dayKey = DateEpochUtils.todayDayKey(nowMs), screenTimeMinutes = 150, mindfulGoalMinutes = 120, focusSessionsCount = 1),
            DigitalWellbeingRecordEntity(profileId = 1L, dayKey = DateEpochUtils.dayKeyDaysAgo(8, nowMs), screenTimeMinutes = 180, mindfulGoalMinutes = 120, focusSessionsCount = 0)
        )

        val snapshot = PersonalHealthIntelligenceEngine.analyze(
            profile = profile,
            authorization = AiDataAuthorization(),
            waterRecords60d = waterLogs,
            sleepRecords = sleepRecords,
            exercises = exercises,
            stepRecords = stepRecords,
            dietRecords = emptyList(),
            medicines = listOf(med),
            medicineIntakes60d = emptyList(),
            consultations = listOf(
                DoctorConsultationEntity(
                    profileId = 1L,
                    doctorName = "Meera",
                    specialty = "General",
                    consultationEpochMs = nowMs - 2 * 86_400_000L,
                    summary = "Annual review",
                    followUpEpochDay = DateEpochUtils.todayEpochDay(nowMs) - 2L,
                    isFollowUpCompleted = false
                )
            ),
            beautyRecords = emptyList(),
            wellbeingRecords = wellbeing,
            cycleInsights = null,
            nowMs = nowMs
        )

        val hydrationTrend = snapshot.trends.first { it.id == "hydration" }
        assertEquals(TrendDirection.IMPROVED, hydrationTrend.direction)

        val sleepTrend = snapshot.trends.first { it.id == "sleep" }
        assertEquals(TrendDirection.DECLINED, sleepTrend.direction)
        // Cross-midnight bedtime spread between 23:45 and 00:15 must be ~30m, NOT ~1410m
        assertTrue(sleepTrend.provenanceNote.contains("30m bedtime spread"))

        val stepTrend = snapshot.trends.first { it.id == "steps" }
        assertEquals(TrendDirection.IMPROVED, stepTrend.direction)

        val screenTrend = snapshot.trends.first { it.id == "wellbeing" }
        assertEquals(TrendDirection.IMPROVED, screenTrend.direction)

        // Nutrition has no logs -> explicitly noted as missing rather than invented
        assertTrue(snapshot.missingOrExcludedNotes.any { it.contains("Nutrition", ignoreCase = true) })
        assertTrue(snapshot.routineGaps.any { it.id == "gap_water" })
        assertTrue(snapshot.routineGaps.any { it.id == "gap_medicines" })
        assertTrue(snapshot.routineGaps.any { it.id == "gap_doctor_overdue" })
        assertTrue(snapshot.routineGaps.any { it.id == "gap_screen_cap" })
        assertTrue(snapshot.dailyPlan.isNotEmpty())

        // Offline report & grounded Q&A verification
        val weeklyReport = snapshot.offlineReportFor(IntelligenceTimeframe.WEEKLY)
        assertFalse(weeklyReport.isFromCloudAi)
        assertTrue(weeklyReport.disclaimer.contains("not a medical diagnosis", ignoreCase = true))

        val weeklyAnswer = PersonalHealthIntelligenceEngine.answerGroundedQuestion(
            prompt = "How was my health routine this week?",
            snapshot = snapshot
        )
        assertTrue(weeklyAnswer.contains("Hydration", ignoreCase = true))
        assertTrue(weeklyAnswer.contains("Sleep", ignoreCase = true))
        assertTrue(weeklyAnswer.contains("not a medical diagnosis", ignoreCase = true))
    }

    @Test
    fun `privacy authorization strictly excludes opted-out modules from intelligence output and prompt context`() {
        val nowMs = middayNowMs()
        val profile = UserProfileEntity(
            id = 1L,
            fullName = "Ananya",
            age = 27,
            genderIdentity = "Female",
            cycleTrackingEnabled = true
        )
        val sleep = listOf(
            SleepRecordEntity(profileId = 1L, bedtimeEpochMs = nowMs - 28_000_000L, wakeEpochMs = nowMs - 3_600_000L, qualityRating = 4)
        )
        val med = MedicineEntity(
            profileId = 1L,
            name = "PrivateRx",
            dosage = "50mg",
            scheduleTime24h = "09:00",
            frequency = "Once Daily",
            isActive = true
        )
        val cycle = CycleCalculator.analyze(emptyList(), 28, 5)
        val diet = listOf(
            DietRecordEntity(profileId = 1L, mealType = "Lunch", foodSummary = "Dal Rice", calories = 520, proteinGrams = 18, loggedAtEpochMs = nowMs)
        )

        val restrictedAuth = AiDataAuthorization(
            includeHydration = true,
            includeSleep = false,
            includeFitnessAndVitals = true,
            includeNutrition = true,
            includeMedicinesAndVisits = false,
            includeWellbeingAndSelfCare = true,
            includeCycle = false
        )

        val snapshot = PersonalHealthIntelligenceEngine.analyze(
            profile = profile,
            authorization = restrictedAuth,
            waterRecords60d = emptyList(),
            sleepRecords = sleep,
            exercises = emptyList(),
            stepRecords = emptyList(),
            dietRecords = diet,
            medicines = listOf(med),
            medicineIntakes60d = emptyList(),
            consultations = emptyList(),
            beautyRecords = emptyList(),
            wellbeingRecords = emptyList(),
            cycleInsights = cycle,
            nowMs = nowMs
        )

        assertNull(snapshot.trends.firstOrNull { it.id == "sleep" })
        assertNull(snapshot.trends.firstOrNull { it.id == "medicines" })
        assertFalse(snapshot.groundedAiPromptContext.contains("PrivateRx"))
        assertTrue(snapshot.groundedAiPromptContext.contains("Sleep logs excluded by your AI privacy settings"))
        assertTrue(snapshot.groundedAiPromptContext.contains("Medicines and doctor visits excluded by AI privacy settings"))
        assertTrue(snapshot.groundedAiPromptContext.contains("Cycle records excluded by your AI privacy settings"))

        val sleepQuestion = PersonalHealthIntelligenceEngine.answerGroundedQuestion("How did I sleep?", snapshot)
        assertTrue(sleepQuestion.contains("excluded by your AI privacy settings", ignoreCase = true))
    }

    @Test
    fun `empty records never invent values and yield insufficient baseline guidance`() {
        val snapshot = PersonalHealthIntelligenceEngine.analyze(
            profile = UserProfileEntity(fullName = "New User", age = 25, genderIdentity = "Non-binary"),
            authorization = AiDataAuthorization(),
            waterRecords60d = emptyList(),
            sleepRecords = emptyList(),
            exercises = emptyList(),
            stepRecords = emptyList(),
            dietRecords = emptyList(),
            medicines = emptyList(),
            medicineIntakes60d = emptyList(),
            consultations = emptyList(),
            beautyRecords = emptyList(),
            wellbeingRecords = emptyList(),
            cycleInsights = null,
            nowMs = middayNowMs()
        )

        assertEquals(0, snapshot.loggedModuleCount)
        assertTrue(snapshot.trends.all { it.direction == TrendDirection.INSUFFICIENT_DATA })
        val monthly = snapshot.offlineReportFor(IntelligenceTimeframe.MONTHLY)
        assertTrue(monthly.uncertaintyAndCoverageNote.isNotBlank())
    }

    @Test
    fun `ai service parses structured health intelligence JSON and extracts streaming headline preview`() {
        val partial = """{"headline":"Hydration improved +18% while sleep stayed steady","recordedFactsSummary":"Avg 2100 ml"""
        assertEquals(
            "Hydration improved +18% while sleep stayed steady",
            LifeWeaveAiService.extractStreamingFieldPreview(partial, "headline")
        )

        val fullJson = """
            {
              "headline": "Hydration up, bedtime steady this week",
              "recordedFactsSummary": "Recorded 2,200 ml/day water and 4 workouts.",
              "observedChanges": ["Water consistency +18% vs prior week", "4 exercise sessions totaling 140m"],
              "personalizedSuggestions": ["Consider a 15-minute evening stretch before lights-out."],
              "dailyPlanHighlights": ["Morning: Log first 300 ml glass after waking."],
              "uncertaintyAndCoverageNote": "Nutrition had 0 logs this week.",
              "disclaimer": "Informational wellness insights only, not a medical diagnosis."
            }
        """.trimIndent()

        val report = LifeWeaveAiService.parseHealthIntelligenceJson(fullJson, "WEEKLY")
        assertTrue(report.isFromCloudAi)
        assertEquals("WEEKLY", report.timeframe)
        assertEquals("Hydration up, bedtime steady this week", report.headline)
        assertEquals(2, report.observedChanges.size)
        assertEquals(1, report.personalizedSuggestions.size)
        assertEquals(1, report.dailyPlanHighlights.size)
        assertTrue(report.uncertaintyAndCoverageNote.contains("Nutrition"))
    }

    @Test
    fun `60-day medicine intake query avoids 30-row cap and privacy toggles round-trip in backup`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsManager = LocalPreferencesManager(context)
        prefsManager.resetAll()
        prefsManager.setAiModuleSharing(AiDataAuthorization.MODULE_SLEEP, false)
        prefsManager.setAiModuleSharing(AiDataAuthorization.MODULE_CYCLE, false)

        val vaultManager = KeystoreVaultManager { testAesKey() }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val backupRepo = PrivacyBackupRepository(db, prefsManager, vaultManager)
            val pid = db.userProfileDao().insertProfile(
                UserProfileEntity(fullName = "Riya", age = 31, genderIdentity = "Female")
            )
            val medId = db.medicalCareDao().insertMedicine(
                MedicineEntity(
                    profileId = pid,
                    name = "Omega 3",
                    dosage = "1000mg",
                    scheduleTime24h = "08:00",
                    frequency = "Once Daily"
                )
            )
            // Insert 35 intakes across last 20 days -> must exceed legacy 30-row cap!
            for (i in 0 until 35) {
                db.medicalCareDao().insertMedicineIntake(
                    MedicineIntakeEntity(
                        profileId = pid,
                        medicineId = medId,
                        medicineName = "Omega 3",
                        dosageTaken = "1000mg",
                        slotLabel = "Dose ${i % 2 + 1}",
                        status = "Taken",
                        dayKey = DateEpochUtils.dayKeyDaysAgo(i / 2)
                    )
                )
            }
            val since60d = db.medicalCareDao().observeIntakesSince(pid, DateEpochUtils.dayKeyDaysAgo(59)).first()
            assertEquals(35, since60d.size)

            val exported = backupRepo.buildExportJson()
            val aiAuthJson = JSONObject(exported).getJSONObject("uiPreferences").getJSONObject("aiDataAuthorization")
            assertFalse(aiAuthJson.getBoolean("includeSleep"))
            assertFalse(aiAuthJson.getBoolean("includeCycle"))
            assertTrue(aiAuthJson.getBoolean("includeHydration"))

            backupRepo.deleteAllLocalData()
            assertTrue(prefsManager.preferencesFlow.first().aiDataAuth.includeSleep)

            backupRepo.restoreFromRawPayload(exported).getOrThrow()
            val restoredAuth = prefsManager.preferencesFlow.first().aiDataAuth
            assertFalse(restoredAuth.includeSleep)
            assertFalse(restoredAuth.includeCycle)
            assertTrue(restoredAuth.includeHydration)
        } finally {
            db.close()
        }
    }

    @Test
    fun `default AI privacy authorization enables all opt-out toggles cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsManager = LocalPreferencesManager(context)
        prefsManager.resetAll()

        val defaults = prefsManager.preferencesFlow.first().aiDataAuth
        assertTrue(defaults.includeHydration)
        assertTrue(defaults.includeSleep)
        assertTrue(defaults.includeFitnessAndVitals)
        assertTrue(defaults.includeNutrition)
        assertTrue(defaults.includeMedicinesAndVisits)
        assertTrue(defaults.includeWellbeingAndSelfCare)
        assertTrue(defaults.includeCycle)
        assertEquals(7, defaults.authorizedCount(cycleTrackingEnabled = true))
        assertEquals(6, defaults.authorizedCount(cycleTrackingEnabled = false))
    }
}
