package com.example

import android.content.Context
import android.util.Base64
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.ModuleRepository
import com.example.domain.DateEpochUtils
import com.example.domain.HydrationSummary
import com.example.domain.OnDeviceWellnessAdvisor
import com.example.notifications.AlertCategory
import com.example.notifications.AlertSoundManager
import com.example.notifications.BootCompletedReceiver
import com.example.notifications.BundledAlertTone
import com.example.security.AlertToneSource
import com.example.security.CategoryToneSetting
import com.example.security.OfflineLicenseVerifier
import com.example.security.VerificationResult
import com.example.ai.AiActionType
import com.example.ai.LifeWeaveAiService
import com.example.ai.SupportedVoiceLanguages
import com.example.healthsync.AppleAndFileFitnessImporter
import com.example.healthsync.FitnessSourceCatalog
import com.example.healthsync.FitnessSyncRepository
import com.example.wellbeing.AppUsageMetric
import com.example.wellbeing.DeviceUsageSnapshot
import com.example.wellbeing.DeviceWellbeingManager
import com.example.wellbeing.OveruseSeverity
import com.example.wellbeing.WellbeingMetricsCalculator
import android.content.Intent
import android.provider.Settings
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.nio.charset.StandardCharsets
import java.security.KeyPairGenerator
import java.security.Signature
import java.security.spec.ECGenParameterSpec

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read app name from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        assertEquals("LifeWeave", context.getString(R.string.app_name))
    }

    @Test
    fun `room database persists profile water period and custom alarms and cascades deletion`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val profileId = db.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Aarav",
                    age = 28,
                    genderIdentity = "Non-Binary",
                    dailyWaterGoalMl = 2600,
                    cycleTrackingEnabled = true
                )
            )
            val todayKey = DateEpochUtils.todayDayKey()
            db.waterDao().insertRecord(
                WaterRecordEntity(
                    profileId = profileId,
                    amountMl = 300,
                    drinkType = "Water",
                    dayKey = todayKey
                )
            )
            db.periodDao().insertRecord(
                PeriodRecordEntity(
                    profileId = profileId,
                    startDateEpochDay = DateEpochUtils.todayEpochDay() - 3,
                    flowIntensity = "Medium",
                    symptomsCsv = "Cramps",
                    mood = "Calm"
                )
            )
            db.reminderDao().insertReminder(
                ReminderEntity(
                    profileId = profileId,
                    title = "Afternoon Hydration",
                    moduleKey = AlertCategory.WATER.key,
                    triggerTime24h = "15:30",
                    daysOfWeekMask = 62,
                    soundKey = BundledAlertTone.BAMBOO_BROOK.id
                )
            )

            assertEquals(1, db.waterDao().observeRecordsForDay(profileId, todayKey).first().size)
            assertEquals(1, db.periodDao().observeAllForProfile(profileId).first().size)
            val alarms = db.reminderDao().observeRemindersForProfile(profileId).first()
            assertEquals(1, alarms.size)
            assertEquals("15:30", alarms.first().triggerTime24h)
            assertEquals(BundledAlertTone.BAMBOO_BROOK.id, alarms.first().soundKey)

            db.userProfileDao().deleteAllProfiles()
            assertEquals(0, db.waterDao().getAllForProfileSnapshot(profileId).size)
            assertEquals(0, db.periodDao().getAllForProfileSnapshot(profileId).size)
            assertEquals(0, db.reminderDao().getRemindersSnapshot(profileId).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `all modules are seeded free and medicines intakes and doctor followups persist offline`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val moduleRepo = ModuleRepository(db, OfflineLicenseVerifier())
            moduleRepo.ensureSeeded()
            val modules = moduleRepo.observeModules().first()
            assertTrue(modules.isNotEmpty())
            assertTrue(modules.all { it.isAlwaysFree && it.isActivated })

            val profileId = db.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Meera",
                    age = 31,
                    genderIdentity = "Female",
                    dailyWaterGoalMl = 2400
                )
            )

            val allRepo = AllModulesRepository(db)
            allRepo.addMedicine(
                profileId = profileId,
                name = "Amoxicillin",
                dosage = "500mg",
                scheduleTime24h = "08:00",
                frequency = "Every 8h (3x/day)",
                dosesPerDay = 3,
                intervalHours = 8,
                mealRelation = "After Meal",
                categoryEmoji = "💊"
            )
            val meds = allRepo.observeMedicines(profileId).first()
            assertEquals(1, meds.size)
            assertEquals(3, meds.first().dosesPerDay)
            assertEquals(8, meds.first().intervalHours)

            allRepo.logMedicineIntake(profileId, meds.first(), "Dose 1/3 (08:00)", "Taken")
            val intakes = allRepo.observeTodayMedicineIntakes(profileId).first()
            assertEquals(1, intakes.size)
            assertEquals("Taken", intakes.first().status)

            val followDay = DateEpochUtils.todayEpochDay() + 5
            allRepo.addConsultation(
                profileId = profileId,
                doctorName = "Rao",
                specialty = "General Wellness",
                clinicOrHospital = "City Care",
                summary = "Complete 5-day course",
                followUpEpochDay = followDay,
                followUpNotes = "Review fever"
            )
            val visits = allRepo.observeConsultations(profileId).first()
            assertEquals(1, visits.size)
            assertFalse(visits.first().isFollowUpCompleted)
            assertEquals(followDay, visits.first().followUpEpochDay)

            allRepo.toggleConsultationFollowUp(visits.first(), true)
            assertTrue(allRepo.observeConsultations(profileId).first().first().isFollowUpCompleted)

            allRepo.addTodaySteps(profileId, null, 2500)
            allRepo.logExercise(profileId, "Yoga Flow", "Yoga", 20, "Calm", 80)
            allRepo.logMeal(profileId, "Breakfast", "Oats", 320, 12, 50, 7)

            val reply = OnDeviceWellnessAdvisor.answerPrompt(
                prompt = "How are my medicines and follow-ups?",
                profile = db.userProfileDao().getActiveProfileSnapshot(),
                hydration = HydrationSummary(1200, 2400, 0.5f, 1200, 2, emptyList()),
                cycle = null,
                exercises = allRepo.observeExercises(profileId).first(),
                diet = allRepo.observeDiet(profileId).first(),
                sleep = emptyList(),
                steps = allRepo.observeTodaySteps(profileId).first(),
                wellbeing = null,
                medicines = meds,
                todayIntakes = intakes,
                consultations = visits
            )
            assertTrue(reply.contains("1/3"))
        } finally {
            db.close()
        }
    }

    @Test
    fun `bundled audio library resolves distinct uris for every tone and category`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = AlertSoundManager(context)

        val uris = BundledAlertTone.entries.map { manager.bundledToneUri(it).toString() }.toSet()
        assertEquals(6, uris.size)

        val waterBamboo = CategoryToneSetting(
            source = AlertToneSource.CATEGORY_DEFAULT,
            bundledToneId = BundledAlertTone.BAMBOO_BROOK.id
        )
        val periodBowl = CategoryToneSetting(
            source = AlertToneSource.CATEGORY_DEFAULT,
            bundledToneId = BundledAlertTone.SINGING_BOWL.id
        )

        assertNotEquals(
            manager.resolveSoundUri(AlertCategory.WATER, waterBamboo),
            manager.resolveSoundUri(AlertCategory.PERIOD, periodBowl)
        )
        assertEquals("Bamboo Brook", manager.describeCurrentTone(AlertCategory.WATER, waterBamboo))
        assertEquals("Tibetan Singing Bowl", manager.describeCurrentTone(AlertCategory.PERIOD, periodBowl))
    }

    @Test
    fun `offline license verifier validates ECDSA P256 signatures and embedded public key parses`() {
        assertTrue(OfflineLicenseVerifier().isPublicKeyReady())

        val keyGen = KeyPairGenerator.getInstance("EC")
        keyGen.initialize(ECGenParameterSpec("secp256r1"))
        val keyPair = keyGen.generateKeyPair()

        val payloadJson = """{"moduleKey":"medical_care","licenseRef":"LC-2026-001","issuedTo":"Owner"}"""
        val payloadBytes = payloadJson.toByteArray(StandardCharsets.UTF_8)
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(keyPair.private)
        signer.update(payloadBytes)
        val sigBytes = signer.sign()

        val b64Flags = Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        val token = "LC1.${Base64.encodeToString(payloadBytes, b64Flags)}.${Base64.encodeToString(sigBytes, b64Flags)}"

        val verifier = OfflineLicenseVerifier { keyPair.public }
        val validResult = verifier.verifyToken(token)
        assertTrue(validResult is VerificationResult.Valid)
        assertEquals("medical_care", (validResult as VerificationResult.Valid).payload.moduleKey)

        val tampered = token.dropLast(2) + "AA"
        assertTrue(verifier.verifyToken(tampered) is VerificationResult.Invalid)
    }

    @Test
    fun `apple health xml importer and fitness sync repository persist and deduplicate wearable metrics`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val profileId = db.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Kiran",
                    age = 30,
                    genderIdentity = "Non-Binary",
                    dailyWaterGoalMl = 2500
                )
            )
            val today = DateEpochUtils.todayDayKey()
            val appleXml = """
                <HealthData>
                  <Record type="HKQuantityTypeIdentifierStepCount" sourceName="Apple Watch" unit="count" startDate="$today 09:00:00" endDate="$today 09:30:00" value="3200"/>
                  <Record type="HKQuantityTypeIdentifierStepCount" sourceName="Apple Watch" unit="count" startDate="$today 14:00:00" endDate="$today 14:20:00" value="1800"/>
                  <Record type="HKQuantityTypeIdentifierHeartRate" sourceName="Apple Watch" unit="count/min" startDate="$today 09:15:00" endDate="$today 09:15:00" value="74"/>
                  <Record type="HKQuantityTypeIdentifierBodyMass" sourceName="Apple Health" unit="kg" startDate="$today 08:00:00" endDate="$today 08:00:00" value="68.5"/>
                  <Record type="HKQuantityTypeIdentifierDietaryWater" sourceName="Apple Health" unit="mL" startDate="$today 10:00:00" endDate="$today 10:00:00" value="300"/>
                  <Workout workoutActivityType="HKWorkoutActivityTypeRunning" duration="35" durationUnit="min" totalEnergyBurned="280" sourceName="Apple Watch" startDate="$today 07:00:00" endDate="$today 07:35:00"/>
                </HealthData>
            """.trimIndent()

            val importer = AppleAndFileFitnessImporter()
            val payload = importer.parseAppleHealthXml(appleXml.byteInputStream())
            assertEquals(1, payload.dailySteps.size)
            assertEquals(5000, payload.dailySteps.first().steps)
            assertEquals(1, payload.workouts.size)
            assertEquals(68.5f, payload.latestWeightKg)
            assertTrue(payload.vitalMetrics.any { it.metricType == "HEART_RATE" && it.valuePrimary.toInt() == 74 })

            val syncRepo = FitnessSyncRepository(db)
            val firstCount = syncRepo.ingestSyncPayload(profileId, payload)
            assertTrue(firstCount >= 4)

            val savedSteps = db.fitnessDao().getStepsForDaySnapshot(profileId, today)
            assertEquals(5000, savedSteps?.stepCount)
            assertEquals(68.5f, db.userProfileDao().getActiveProfileSnapshot()?.weightKg)
            assertEquals(1, syncRepo.observeConnectedSources(profileId).first().size)

            // Re-importing the exact same file should deduplicate workouts, water, and vitals
            syncRepo.ingestSyncPayload(profileId, payload)
            assertEquals(1, db.fitnessDao().getExercisesSnapshot(profileId).size)
            assertEquals(1, db.waterDao().getAllForProfileSnapshot(profileId).size)

            val (fitbitName, fitbitEmoji) = FitnessSourceCatalog.resolveSourceInfo("com.fitbit.FitbitMobile")
            assertEquals("Fitbit", fitbitName)
            assertEquals("⌚", fitbitEmoji)
        } finally {
            db.close()
        }
    }

    @Test
    fun `ai structured json parsers extract voice logging actions and prescription schedules`() {
        val voiceJson = """
            {
              "assistantReply": "Logged your coffee and 200 ml of water!",
              "actions": [
                {
                  "actionType": "LOG_MEAL",
                  "titleOrName": "☕ Coffee",
                  "amountOrValue": 1,
                  "secondaryText": "Snack",
                  "calories": 55
                },
                {
                  "actionType": "LOG_WATER",
                  "titleOrName": "Water",
                  "amountOrValue": 200,
                  "secondaryText": "Water"
                }
              ]
            }
        """.trimIndent()
        val parsedVoice = LifeWeaveAiService.parseAssistantJson(voiceJson)
        assertEquals(2, parsedVoice.actions.size)
        assertEquals(AiActionType.LOG_MEAL, parsedVoice.actions[0].actionType)
        assertEquals(55, parsedVoice.actions[0].calories)
        assertEquals(AiActionType.LOG_WATER, parsedVoice.actions[1].actionType)
        assertEquals(200, parsedVoice.actions[1].amountOrValue)

        val rxJson = """
            {
              "summary": "Amoxicillin 500mg three times daily after meals and follow-up with Dr. Nair in 7 days.",
              "doctorName": "Nair",
              "specialty": "ENT Specialist",
              "clinicOrHospital": "Harmony Clinic",
              "followUpDaysFromToday": 7,
              "followUpNotes": "Check throat recovery",
              "medicines": [
                {
                  "name": "Amoxicillin",
                  "dosage": "500mg",
                  "dosesPerDay": 3,
                  "intervalHours": 8,
                  "scheduleTime24h": "08:00",
                  "mealRelation": "After Meal",
                  "notes": "5 days"
                }
              ]
            }
        """.trimIndent()
        val parsedRx = LifeWeaveAiService.parsePrescriptionJson(rxJson)
        assertEquals("Nair", parsedRx.doctorName)
        assertEquals(7, parsedRx.followUpDaysFromToday)
        assertEquals(1, parsedRx.medicines.size)
        assertEquals("Amoxicillin", parsedRx.medicines.first().name)
        assertEquals(3, parsedRx.medicines.first().dosesPerDay)
        assertEquals(8, parsedRx.medicines.first().intervalHours)
        assertTrue(SupportedVoiceLanguages.options.any { it.tag == "hi-IN" })
        assertTrue(SupportedVoiceLanguages.options.any { it.tag == "es-ES" })
    }

    @Test
    fun `date epoch helpers and custom calendar time scheduling work for medicine intake and doctor appointment`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val targetEpochDay = DateEpochUtils.todayEpochDay() + 3
            val utcPickerMillis = DateEpochUtils.epochDayToUtcPickerMillis(targetEpochDay)
            assertEquals(targetEpochDay, DateEpochUtils.utcPickerMillisToEpochDay(utcPickerMillis))

            val combinedMs = DateEpochUtils.combineEpochDayAndTime(targetEpochDay, 14, 35)
            val (parsedHour, parsedMinute) = DateEpochUtils.epochMsToHourMinute(combinedMs)
            assertEquals(14, parsedHour)
            assertEquals(35, parsedMinute)
            assertEquals(9 to 15, DateEpochUtils.parseHourMinute("09:15"))

            val profileId = db.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Meera",
                    age = 31,
                    genderIdentity = "Female",
                    dailyWaterGoalMl = 2400
                )
            )
            val repo = AllModulesRepository(db)

            repo.addMedicine(
                profileId = profileId,
                name = "Vitamin D3",
                dosage = "1000 IU",
                scheduleTime24h = "09:15",
                frequency = "Daily",
                dosesPerDay = 1,
                intervalHours = 24
            )
            val medicine = repo.observeMedicines(profileId).first().first()

            val customIntakeDay = DateEpochUtils.todayEpochDay() - 1
            val customIntakeMs = DateEpochUtils.combineEpochDayAndTime(customIntakeDay, 20, 45)
            repo.logMedicineIntake(
                profileId = profileId,
                medicine = medicine,
                slotLabel = "20:45",
                status = "Taken",
                takenAtEpochMs = customIntakeMs
            )

            val recentIntakes = repo.observeRecentMedicineIntakes(profileId).first()
            assertEquals(1, recentIntakes.size)
            assertEquals(customIntakeMs, recentIntakes.first().takenAtEpochMs)
            assertEquals(DateEpochUtils.todayDayKey(customIntakeMs), recentIntakes.first().dayKey)

            val followUpDay = targetEpochDay + 14
            repo.addConsultation(
                profileId = profileId,
                doctorName = "Dr. Sharma",
                specialty = "Cardiology",
                clinicOrHospital = "City Care",
                summary = "Routine annual checkup",
                followUpEpochDay = followUpDay,
                followUpNotes = "Bring lipid profile",
                consultationEpochMs = combinedMs
            )
            val consultation = repo.observeConsultations(profileId).first().first()
            assertEquals(combinedMs, consultation.consultationEpochMs)
            assertEquals(followUpDay, consultation.followUpEpochDay)
        } finally {
            db.close()
        }
    }

    @Test
    fun `digital wellbeing snapshot sync persists rich metrics and evaluates overuse alerts accurately`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val profileId = db.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Rohan",
                    age = 27,
                    genderIdentity = "Male",
                    dailyWaterGoalMl = 2500
                )
            )
            val repo = AllModulesRepository(db)
            val apps = listOf(
                AppUsageMetric("com.instagram.android", "Instagram", "Social", minutesUsed = 62, launchCount = 19),
                AppUsageMetric("com.google.android.youtube", "YouTube", "Video", minutesUsed = 48, launchCount = 7),
                AppUsageMetric("com.whatsapp", "WhatsApp", "Messaging", minutesUsed = 24, launchCount = 31)
            )
            val snapshot = DeviceUsageSnapshot(
                dayKey = DateEpochUtils.todayDayKey(),
                totalScreenMinutes = 134,
                unlockCount = 52,
                appLaunchCount = 57,
                nightScreenMinutes = 35,
                longestSessionMinutes = 46,
                topApps = apps,
                categoryMinutes = WellbeingMetricsCalculator.categoryBreakdown(apps)
            )

            // Pre-existing focus session should be preserved after device sync
            repo.updateDigitalWellbeing(profileId = profileId, existing = null, addFocusSession = 2, goalMinutes = 120)
            val existing = repo.observeTodayWellbeing(profileId).first()
            repo.syncWellbeingSnapshot(profileId = profileId, existing = existing, snapshot = snapshot)

            val synced = repo.observeTodayWellbeing(profileId).first()!!
            assertEquals(134, synced.screenTimeMinutes)
            assertEquals(52, synced.unlockCount)
            assertEquals(57, synced.appLaunchCount)
            assertEquals(35, synced.nightScreenMinutes)
            assertEquals(46, synced.longestSessionMinutes)
            assertEquals("Instagram", synced.topAppName)
            assertEquals(62, synced.topAppMinutes)
            assertEquals(2, synced.focusSessionsCount)
            assertEquals(3, WellbeingMetricsCalculator.decodeTopApps(synced.topAppsJson).size)

            val heavyApps = WellbeingMetricsCalculator.longUsedApps(apps, dailyCapMinutes = 120)
            assertEquals(listOf("Instagram", "YouTube"), heavyApps.map { it.appName })
            assertTrue(apps[0].isLongUsed(120))
            assertFalse(apps[2].isLongUsed(120))

            val exceededAlert = WellbeingMetricsCalculator.evaluateOveruse(synced, warningPercent = 80, topApps = apps)
            assertEquals(OveruseSeverity.EXCEEDED, exceededAlert.severity)
            assertTrue(exceededAlert.message.contains("Instagram"))
            assertEquals(2, exceededAlert.flaggedApps.size)

            val criticalAlert = WellbeingMetricsCalculator.evaluateOveruse(synced.copy(screenTimeMinutes = 200), warningPercent = 80, topApps = apps)
            assertEquals(OveruseSeverity.CRITICAL, criticalAlert.severity)

            val singleAppOveruse = WellbeingMetricsCalculator.evaluateOveruse(
                synced.copy(screenTimeMinutes = 70, nightScreenMinutes = 5, longestSessionMinutes = 20),
                warningPercent = 80,
                topApps = listOf(AppUsageMetric("com.instagram.android", "Instagram", "Social", minutesUsed = 50, launchCount = 12))
            )
            assertEquals(OveruseSeverity.EXCEEDED, singleAppOveruse.severity)
            assertTrue(singleAppOveruse.title.contains("Instagram"))

            val mildApps = listOf(
                AppUsageMetric("com.whatsapp", "WhatsApp", "Messaging", minutesUsed = 20, launchCount = 10),
                AppUsageMetric("com.spotify.music", "Spotify", "Music & Audio", minutesUsed = 25, launchCount = 4)
            )
            val approachingAlert = WellbeingMetricsCalculator.evaluateOveruse(
                synced.copy(screenTimeMinutes = 100, nightScreenMinutes = 5, longestSessionMinutes = 20, topAppName = "Spotify", topAppMinutes = 25),
                warningPercent = 80,
                topApps = mildApps
            )
            assertEquals(OveruseSeverity.APPROACHING, approachingAlert.severity)

            val balancedAlert = WellbeingMetricsCalculator.evaluateOveruse(
                synced.copy(screenTimeMinutes = 45, nightScreenMinutes = 5, longestSessionMinutes = 15, topAppName = "Spotify", topAppMinutes = 25),
                warningPercent = 80,
                topApps = mildApps
            )
            assertEquals(OveruseSeverity.BALANCED, balancedAlert.severity)

            assertTrue(WellbeingMetricsCalculator.computeMindfulBalanceScore(synced) in 5..95)
        } finally {
            db.close()
        }
    }

    @Test
    fun `wellbeing manager intent fallback and background monitor scheduling work safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val manager = DeviceWellbeingManager(context)
        val fallbackIntent = manager.createUsageAccessSettingsIntent(includePackageUri = false)
        assertEquals(Settings.ACTION_USAGE_ACCESS_SETTINGS, fallbackIntent.action)
        assertTrue(fallbackIntent.data == null)

        val alertSoundManager = AlertSoundManager(context)
        alertSoundManager.scheduleWellbeingMonitor()
        alertSoundManager.cancelWellbeingMonitor()

        // Ensure BootCompletedReceiver safely handles boot intent without throwing
        val receiver = BootCompletedReceiver()
        receiver.onReceive(context, Intent(Intent.ACTION_BOOT_COMPLETED))
    }
}
