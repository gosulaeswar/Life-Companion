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
import com.example.notifications.AlarmEmojiCatalog
import com.example.notifications.AlarmEmojiGesturePreset
import com.example.notifications.AlarmPopupPayload
import com.example.notifications.AlertAlarmReceiver
import com.example.notifications.AlertCategory
import com.example.notifications.AlertMetricsCalculator
import com.example.notifications.AlertSoundManager
import com.example.notifications.BootCompletedReceiver
import com.example.notifications.BundledAlertTone
import com.example.security.AlertToneSource
import com.example.security.AppLocaleManager
import com.example.security.CategoryToneSetting
import com.example.security.OfflineLicenseVerifier
import com.example.security.SupportedLanguage
import com.example.security.VerificationResult
import com.example.ai.AiActionType
import com.example.ai.AppCheckProviderChoice
import com.example.ai.LifeWeaveAiService
import com.example.ai.SupportedVoiceLanguages
import com.example.ai.configureAppCheck
import com.example.ai.buildFailureDiagnostic
import com.example.ai.buildTimeoutDiagnostic
import com.example.ai.currentAppCheckProviderChoice
import com.example.ai.readOrCreateDebugAppCheckToken
import com.example.ai.resolveRecaptchaSiteKey
import com.example.ai.saveCustomDebugAppCheckToken
import com.example.ai.selectAppCheckProviderChoice
import com.google.firebase.FirebaseApp
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
import java.util.Calendar

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
                    soundKey = BundledAlertTone.BAMBOO_BROOK.id,
                    alarmEmoji = "💧",
                    customMessage = "Sip 300ml & stretch",
                    gesturePreset = AlarmEmojiGesturePreset.HOLD_RING.id
                )
            )

            assertEquals(1, db.waterDao().observeRecordsForDay(profileId, todayKey).first().size)
            assertEquals(1, db.periodDao().observeAllForProfile(profileId).first().size)
            val alarms = db.reminderDao().observeRemindersForProfile(profileId).first()
            assertEquals(1, alarms.size)
            assertEquals("15:30", alarms.first().triggerTime24h)
            assertEquals(BundledAlertTone.BAMBOO_BROOK.id, alarms.first().soundKey)
            assertEquals("💧", alarms.first().alarmEmoji)
            assertEquals("Sip 300ml & stretch", alarms.first().customMessage)
            assertEquals(AlarmEmojiGesturePreset.HOLD_RING.id, alarms.first().gesturePreset)

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

    @Test
    fun `customized alarm popup payload emoji gesture presets and snooze actions roundtrip safely`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = AlarmPopupPayload(
            notificationId = 5042,
            alarmId = 42L,
            categoryKey = "water",
            title = "Evening Sip",
            message = "Drink 250ml warm water",
            emoji = "💧",
            timeLabel = "14:55",
            toneLabel = "Water Droplet Chime",
            gesturePreset = AlarmEmojiGesturePreset.DOUBLE_TAP_POP,
            soundUriString = "android.resource://${context.packageName}/raw/sound_water_drop"
        )
        val roundtrip = AlarmPopupPayload.fromIntent(original.writeToIntent(Intent()))
        assertEquals(original.notificationId, roundtrip.notificationId)
        assertEquals(original.alarmId, roundtrip.alarmId)
        assertEquals("💧", roundtrip.emoji)
        assertEquals("14:55", roundtrip.timeLabel)
        assertEquals("Drink 250ml warm water", roundtrip.message)
        assertEquals(AlarmEmojiGesturePreset.DOUBLE_TAP_POP, roundtrip.gesturePreset)

        assertEquals("🌸", AlarmEmojiCatalog.defaultEmojiForCategory("period"))
        assertEquals(AlarmEmojiGesturePreset.SWIPE_SLIDE, AlarmEmojiGesturePreset.fromId("unknown"))

        val soundManager = AlertSoundManager(context)
        val reminder = ReminderEntity(
            id = 42L,
            profileId = 1L,
            title = "Medicine Check",
            moduleKey = AlertCategory.WELLNESS.key,
            triggerTime24h = "14:55",
            alarmEmoji = "💊",
            customMessage = "Take Vitamin D3 after lunch",
            gesturePreset = AlarmEmojiGesturePreset.HOLD_RING.id
        )
        soundManager.scheduleCustomAlarm(reminder)
        soundManager.scheduleSnoozeAlarm(roundtrip, minutes = 10)
        soundManager.cancelCustomAlarm(42L)

        // Verify dismiss & snooze broadcasts from notification shade execute safely
        val alarmReceiver = AlertAlarmReceiver()
        alarmReceiver.onReceive(
            context,
            Intent(AlertAlarmReceiver.ACTION_DISMISS_ALARM).putExtra(AlarmPopupPayload.EXTRA_NOTIFICATION_ID, 5042)
        )
        alarmReceiver.onReceive(
            context,
            original.writeToIntent(Intent(AlertAlarmReceiver.ACTION_SNOOZE_ALARM))
        )
    }

    @Test
    fun `regional language wrapper resolves localized strings for all supported Indian languages`() {
        val baseContext = ApplicationProvider.getApplicationContext<Context>()

        val expectedNavHome = mapOf(
            SupportedLanguage.ENGLISH to "Home",
            SupportedLanguage.HINDI to "होम",
            SupportedLanguage.TAMIL to "முகப்பு",
            SupportedLanguage.TELUGU to "హోమ్",
            SupportedLanguage.BENGALI to "হোম",
            SupportedLanguage.MARATHI to "मुख्यपृष्ठ"
        )

        expectedNavHome.forEach { (lang, expectedHomeLabel) ->
            val localizedContext = AppLocaleManager.wrapContext(baseContext, lang)
            assertEquals(expectedHomeLabel, localizedContext.getString(R.string.nav_home))
            assertNotEquals("", localizedContext.getString(R.string.welcome_title))
            if (lang != SupportedLanguage.ENGLISH) {
                assertNotEquals("Home", localizedContext.getString(R.string.nav_home))
                assertNotEquals("Daily Steps", localizedContext.getString(R.string.home_daily_steps))
            }
        }
    }

    @Test
    fun `default water and medicine care notes, pre-login permissions flag, and 5s popup scheduler work cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsManager = com.example.security.LocalPreferencesManager(context)
        prefsManager.resetAll()

        val initial = prefsManager.preferencesFlow.first()
        assertFalse(initial.permissionsSetupCompleted)

        prefsManager.setPermissionsSetupCompleted(true)
        val seeded = prefsManager.enableDefaultCareRemindersIfNeeded()
        assertTrue(seeded)

        val updated = prefsManager.preferencesFlow.first()
        assertTrue(updated.permissionsSetupCompleted)
        assertTrue(updated.waterTone.reminderEnabled)
        assertTrue(updated.wellnessTone.reminderEnabled)
        assertTrue(updated.waterTone.customMessage.orEmpty().contains("water", ignoreCase = true))
        assertTrue(updated.wellnessTone.customMessage.orEmpty().contains("medicine", ignoreCase = true))

        val soundManager = AlertSoundManager(context)
        soundManager.scheduleTestPopupInSeconds(5, AlertCategory.WATER)
        assertTrue(
            soundManager.createOverlayPermissionIntent().action in setOf(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            )
        )
        assertTrue(
            soundManager.createExactAlarmSettingsIntent().action in setOf(
                Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            )
        )

        val receiver = AlertAlarmReceiver()
        receiver.onReceive(
            context,
            Intent(AlertAlarmReceiver.ACTION_TEST_BACKGROUND_POPUP)
                .putExtra(AlertAlarmReceiver.EXTRA_CATEGORY_KEY, AlertCategory.WATER.key)
        )
    }

    @Test
    fun `repeating intervals (1h, 2h, custom) schedule accurately and alert telemetry metrics aggregate in Room`() = runTest {
        // Verify interval trigger alignment math
        val nowCal = Calendar.getInstance().apply {
            set(2026, Calendar.NOVEMBER, 17, 14, 20, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val nowMs = nowCal.timeInMillis

        // Anchor 14:00 every 1h -> next trigger should be 15:00 (+40 min)
        val nextHourlyMs = AlertSoundManager.computeNextIntervalTriggerMillis(14, 0, 60, nowMs)
        assertEquals(40 * 60_000L, nextHourlyMs - nowMs)

        // Anchor 10:00 every 2h -> next trigger should be 16:00 (+100 min)
        val nextTwoHourMs = AlertSoundManager.computeNextIntervalTriggerMillis(10, 0, 120, nowMs)
        assertEquals(100 * 60_000L, nextTwoHourMs - nowMs)

        // Anchor 14:00 custom 45m -> next trigger should be 14:45 (+25 min)
        val nextCustomMs = AlertSoundManager.computeNextIntervalTriggerMillis(14, 0, 45, nowMs)
        assertEquals(25 * 60_000L, nextCustomMs - nowMs)

        // Verify interval labels
        assertEquals("Every 1h", AlertMetricsCalculator.formatIntervalShort(60))
        assertEquals("Every 2h", AlertMetricsCalculator.formatIntervalShort(120))
        assertEquals("Every 45m", AlertMetricsCalculator.formatIntervalShort(45))
        assertEquals("Daily", AlertMetricsCalculator.formatIntervalShort(1440))

        // Verify Room DAO metric increments & summary aggregation
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val profileId = db.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Aarav",
                    age = 28,
                    genderIdentity = "Male",
                    dailyWaterGoalMl = 2500
                )
            )
            val waterAlarmId = db.reminderDao().insertReminder(
                ReminderEntity(
                    profileId = profileId,
                    title = "Drink Water",
                    moduleKey = "water",
                    triggerTime24h = "09:00",
                    repeatIntervalMinutes = 60,
                    alarmEmoji = "💧"
                )
            )
            val medAlarmId = db.reminderDao().insertReminder(
                ReminderEntity(
                    profileId = profileId,
                    title = "Take Vitamin D",
                    moduleKey = "medicine",
                    triggerTime24h = "20:00",
                    repeatIntervalMinutes = 120,
                    alarmEmoji = "💊"
                )
            )
            db.reminderDao().recordAlarmTriggered(waterAlarmId, nowMs)
            db.reminderDao().recordAlarmTriggered(waterAlarmId, nowMs + 60_000L)
            db.reminderDao().recordAlarmCompleted(waterAlarmId)
            db.reminderDao().recordAlarmSnoozed(waterAlarmId)
            db.reminderDao().recordAlarmTriggered(medAlarmId, nowMs)
            db.reminderDao().recordAlarmCompleted(medAlarmId)

            val allAlarms = db.reminderDao().getRemindersSnapshot(profileId)
            val metrics = AlertMetricsCalculator.summarize(allAlarms)
            assertEquals(2, metrics.totalAlarmsCount)
            assertEquals(2, metrics.enabledAlarmsCount)
            assertEquals(2, metrics.intervalAlarmsCount)
            assertEquals(0, metrics.dailyAlarmsCount)
            assertEquals(3, metrics.totalTriggered)
            assertEquals(2, metrics.totalCompleted)
            assertEquals(1, metrics.totalSnoozed)
            assertEquals(67, metrics.adherencePercent)
            assertEquals(2, metrics.cadenceBreakdown.size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `app check provider uses debug provider on debug builds and recaptcha on release builds`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val siteKey = resolveRecaptchaSiteKey(context)
        assertTrue(!siteKey.isNullOrBlank())

        assertEquals(
            AppCheckProviderChoice.DEBUG_PROVIDER,
            selectAppCheckProviderChoice(
                isDebugBuild = true,
                hasIntentDebugToken = false,
                hasPersistedInjectedToken = false,
                isEmulator = false,
                hasRecaptchaSiteKey = true
            )
        )
        assertEquals(
            AppCheckProviderChoice.DEBUG_WITH_INTENT_TOKEN,
            selectAppCheckProviderChoice(
                isDebugBuild = true,
                hasIntentDebugToken = true,
                hasPersistedInjectedToken = false,
                isEmulator = false,
                hasRecaptchaSiteKey = true
            )
        )
        assertEquals(
            AppCheckProviderChoice.DEBUG_PROVIDER,
            selectAppCheckProviderChoice(
                isDebugBuild = true,
                hasIntentDebugToken = false,
                hasPersistedInjectedToken = true,
                isEmulator = false,
                hasRecaptchaSiteKey = true
            )
        )
        assertEquals(
            AppCheckProviderChoice.DEBUG_PROVIDER,
            selectAppCheckProviderChoice(
                isDebugBuild = true,
                hasIntentDebugToken = false,
                hasPersistedInjectedToken = false,
                isEmulator = true,
                hasRecaptchaSiteKey = true
            )
        )
        assertEquals(
            AppCheckProviderChoice.RECAPTCHA_ENTERPRISE,
            selectAppCheckProviderChoice(
                isDebugBuild = false,
                hasIntentDebugToken = false,
                hasPersistedInjectedToken = false,
                isEmulator = false,
                hasRecaptchaSiteKey = true
            )
        )

        val testToken = "00000000-1111-2222-3333-444444444444"
        configureAppCheck(
            context,
            Intent().putExtra("FIREBASE_APPCHECK_DEBUG_TOKEN", testToken)
        )
        val persistenceKey = FirebaseApp.getInstance().persistenceKey
        val debugPrefs = context.getSharedPreferences(
            "com.google.firebase.appcheck.debug.store.$persistenceKey",
            Context.MODE_PRIVATE
        )
        assertEquals(testToken, debugPrefs.getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null))
        assertEquals(AppCheckProviderChoice.DEBUG_WITH_INTENT_TOKEN, currentAppCheckProviderChoice())
    }

    @Test
    fun streamingPreviewExtractionAndAppCheckDiagnosticsWork() {
        val partial = """{"assistantReply":"Logged 200 ml water! Stay hydrated"""
        assertEquals(
            "Logged 200 ml water! Stay hydrated",
            LifeWeaveAiService.extractStreamingFieldPreview(partial, "assistantReply")
        )
        val escaped = """{"summary":"Line 1\n\"Take 1 pill\"","medicines":[]}"""
        assertEquals(
            """Line 1 "Take 1 pill"""",
            LifeWeaveAiService.extractStreamingFieldPreview(escaped, "summary")
        )
        assertEquals(
            "",
            LifeWeaveAiService.extractStreamingFieldPreview("""{"actions":[]}""", "assistantReply")
        )

        val timeoutMsg = buildTimeoutDiagnostic("reCAPTCHA Enterprise", 15_000L)
        assertTrue(timeoutMsg.contains("reCAPTCHA Enterprise"))
        assertTrue(timeoutMsg.contains("15s"))

        val failureMsg = buildFailureDiagnostic("Debug Provider", "403 Attestation Failed")
        assertTrue(failureMsg.contains("Debug Provider"))
        assertTrue(failureMsg.contains("403 Attestation Failed"))

        val context = ApplicationProvider.getApplicationContext<Context>()
        val generated = readOrCreateDebugAppCheckToken(context)
        assertTrue(!generated.isNullOrBlank())
        val customToken = "11111111-2222-3333-4444-555555555555"
        assertTrue(saveCustomDebugAppCheckToken(context, customToken))
        assertEquals(customToken, readOrCreateDebugAppCheckToken(context))
    }

    @Test
    fun `authGateCompleted preference toggles cleanly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = com.example.security.LocalPreferencesManager(context)
        prefs.resetAll()
        assertFalse(prefs.preferencesFlow.first().authGateCompleted)
        prefs.setAuthGateCompleted(true)
        assertTrue(prefs.preferencesFlow.first().authGateCompleted)
    }
}
