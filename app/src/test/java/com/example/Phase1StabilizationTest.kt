package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AiPreferenceEntity
import com.example.data.local.entity.BeautyCareRecordEntity
import com.example.data.local.entity.ConnectedDeviceSourceEntity
import com.example.data.local.entity.DietRecordEntity
import com.example.data.local.entity.DigitalWellbeingRecordEntity
import com.example.data.local.entity.DoctorConsultationEntity
import com.example.data.local.entity.ExerciseRecordEntity
import com.example.data.local.entity.HealthVitalMetricEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.MedicineIntakeEntity
import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.data.repository.PrivacyBackupRepository
import com.example.domain.CycleCalculator
import com.example.domain.DateEpochUtils
import com.example.domain.HydrationCalculator
import com.example.domain.OnDeviceWellnessAdvisor
import com.example.notifications.AlarmEmojiGesturePreset
import com.example.notifications.AlarmPopupPayload
import com.example.notifications.AlertMetricsCalculator
import com.example.notifications.AlertSoundManager
import com.example.security.FeatureGate
import com.example.security.KeystoreVaultManager
import com.example.security.LocalDefaultEntitlementPolicy
import com.example.security.LocalPreferencesManager
import com.example.security.SubscriptionTier
import com.example.security.SupportedLanguage
import com.example.ui.navigation.LifeWeaveDest
import com.example.ui.screens.ModuleSubRoutes
import com.example.ui.theme.ThemeMode
import android.app.AlarmManager
import android.app.NotificationManager
import android.util.Base64
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase1StabilizationTest {

    private fun testAesKey() = KeyGenerator.getInstance("AES").apply {
        init(256)
    }.generateKey()

    @Test
    fun `17-table v2 json export and atomic restore round-trips all data and remaps medicine foreign keys`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsManager = LocalPreferencesManager(context)
        prefsManager.resetAll()
        prefsManager.setThemeMode(ThemeMode.DARK)
        prefsManager.setLanguage(SupportedLanguage.TELUGU)
        prefsManager.updateWellbeingAlertSettings(enabled = false, warningPercent = 90)

        val vaultManager = KeystoreVaultManager { testAesKey() }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val backupRepo = PrivacyBackupRepository(db, prefsManager, vaultManager)
            val today = DateEpochUtils.todayDayKey()
            val todayEpochDay = DateEpochUtils.todayEpochDay()

            val profileId = db.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Eswar G",
                    age = 29,
                    genderIdentity = "Male",
                    heightCm = 176,
                    weightKg = 72.5f,
                    activityLevel = "Active",
                    dailyWaterGoalMl = 3000,
                    cycleTrackingEnabled = false,
                    preferredLanguageCode = "te"
                )
            )
            db.userProfileDao().upsertAiPreference(
                AiPreferenceEntity(
                    profileId = profileId,
                    onDeviceAgentEnabled = true,
                    proactiveTipsEnabled = false,
                    preferredTone = "Motivational",
                    voiceInputEnabled = true
                )
            )
            db.waterDao().insertRecord(
                WaterRecordEntity(
                    profileId = profileId,
                    amountMl = 350,
                    drinkType = "Coconut Water",
                    note = "Morning",
                    dayKey = today
                )
            )
            db.periodDao().insertRecord(
                PeriodRecordEntity(
                    profileId = profileId,
                    startDateEpochDay = todayEpochDay - 10,
                    endDateEpochDay = todayEpochDay - 6,
                    flowIntensity = "Light",
                    symptomsCsv = "Fatigue",
                    mood = "Calm"
                )
            )
            val medId = db.medicalCareDao().insertMedicine(
                MedicineEntity(
                    profileId = profileId,
                    name = "Metformin",
                    dosage = "500mg",
                    scheduleTime24h = "08:30",
                    frequency = "Every 12h (2x/day)",
                    dosesPerDay = 2,
                    intervalHours = 12,
                    mealRelation = "After Meal",
                    categoryEmoji = "💊",
                    notes = "Prescription backup"
                )
            )
            db.medicalCareDao().insertMedicineIntake(
                MedicineIntakeEntity(
                    profileId = profileId,
                    medicineId = medId,
                    medicineName = "Metformin",
                    dosageTaken = "500mg",
                    slotLabel = "Dose 1/2 (08:30)",
                    status = "Taken",
                    dayKey = today
                )
            )
            db.medicalCareDao().insertConsultation(
                DoctorConsultationEntity(
                    profileId = profileId,
                    doctorName = "Dr. Ananya",
                    specialty = "General Medicine",
                    clinicOrHospital = "Apollo Care",
                    consultationEpochMs = System.currentTimeMillis() - 86_400_000L,
                    summary = "Annual checkup",
                    followUpEpochDay = todayEpochDay + 30,
                    followUpNotes = "Fasting sugar",
                    isFollowUpCompleted = false
                )
            )
            db.reminderDao().insertReminder(
                ReminderEntity(
                    profileId = profileId,
                    title = "Hydrate Break",
                    moduleKey = "water",
                    triggerTime24h = "11:00",
                    daysOfWeekMask = 127,
                    isEnabled = true,
                    soundKey = "bamboo_brook",
                    alarmEmoji = "💧",
                    customMessage = "Sip 300ml",
                    gesturePreset = AlarmEmojiGesturePreset.HOLD_RING.id,
                    repeatIntervalMinutes = 120,
                    triggeredCount = 5,
                    completedCount = 4,
                    snoozedCount = 1
                )
            )
            db.fitnessDao().insertExercise(
                ExerciseRecordEntity(
                    profileId = profileId,
                    activityType = "Brisk Walk",
                    category = "Exercise",
                    durationMinutes = 40,
                    intensity = "Moderate",
                    caloriesBurned = 210
                )
            )
            db.fitnessDao().insertSleep(
                SleepRecordEntity(
                    profileId = profileId,
                    bedtimeEpochMs = System.currentTimeMillis() - 32_000_000L,
                    wakeEpochMs = System.currentTimeMillis() - 4_000_000L,
                    qualityRating = 4,
                    notes = "Restful"
                )
            )
            db.fitnessDao().upsertSteps(
                StepRecordEntity(
                    profileId = profileId,
                    dayKey = today,
                    stepCount = 6400,
                    distanceMeters = 4800
                )
            )
            db.dietDao().insertDietRecord(
                DietRecordEntity(
                    profileId = profileId,
                    mealType = "Breakfast",
                    foodSummary = "Pesarattu & Chutney",
                    calories = 380,
                    proteinGrams = 18,
                    carbsGrams = 46,
                    fatGrams = 12
                )
            )
            db.beautyCareDao().insertBeautyRecord(
                BeautyCareRecordEntity(
                    profileId = profileId,
                    routineType = "Morning",
                    productOrStep = "SPF 50 Sunscreen",
                    notes = "Outdoor"
                )
            )
            db.digitalWellbeingDao().upsertWellbeingRecord(
                DigitalWellbeingRecordEntity(
                    profileId = profileId,
                    dayKey = today,
                    screenTimeMinutes = 95,
                    focusSessionsCount = 2,
                    mindfulGoalMinutes = 120,
                    unlockCount = 28,
                    topAppName = "Reader",
                    topAppMinutes = 35
                )
            )
            db.connectedFitnessDao().upsertSource(
                ConnectedDeviceSourceEntity(
                    profileId = profileId,
                    sourceId = "health_connect",
                    displayName = "Health Connect",
                    emoji = "⌚",
                    syncChannel = "Health Connect",
                    recordsImportedCount = 3,
                    latestSummary = "Steps + HR synced"
                )
            )
            db.connectedFitnessDao().insertVitalMetrics(
                listOf(
                    HealthVitalMetricEntity(
                        profileId = profileId,
                        metricType = "HEART_RATE",
                        valuePrimary = 68.0,
                        unit = "bpm",
                        sourceId = "health_connect",
                        sourceDisplayName = "Health Connect",
                        dayKey = today
                    )
                )
            )
            db.moduleActivationDao().insertDefaultsIfMissing(AppDatabase.defaultModuleCatalog())

            val exportedJson = backupRepo.buildExportJson()
            val rootObj = JSONObject(exportedJson)
            assertEquals(PrivacyBackupRepository.SCHEMA_V2, rootObj.getString("schema"))
            assertEquals("Eswar G", rootObj.getJSONObject("profile").getString("fullName"))
            assertEquals(1, rootObj.getJSONArray("medicineIntakes").length())
            assertEquals(1, rootObj.getJSONArray("healthVitalMetrics").length())

            val preview = backupRepo.parseBackupPreview(exportedJson).getOrThrow()
            assertFalse(preview.isEncrypted)
            assertEquals("Eswar G", preview.profileName)
            assertEquals(14, preview.totalRecords)

            // Wipe DB & reset UI prefs to simulate clean reinstall before restore
            backupRepo.deleteAllLocalData()
            assertEquals(0, db.userProfileDao().observeAllProfiles().first().size)
            assertEquals(ThemeMode.SYSTEM, prefsManager.preferencesFlow.first().themeMode)

            val restoreSummary = backupRepo.restoreFromRawPayload(exportedJson).getOrThrow()
            assertEquals("Eswar G", restoreSummary.profileName)
            assertEquals(14, restoreSummary.restoredRecordsCount)
            assertEquals(1, restoreSummary.restoredReminders.size)

            val restoredProfile = db.userProfileDao().getActiveProfileSnapshot()!!
            assertEquals("Eswar G", restoredProfile.fullName)
            assertEquals(3000, restoredProfile.dailyWaterGoalMl)
            val newPid = restoredProfile.id

            assertEquals(1, db.waterDao().getAllForProfileSnapshot(newPid).size)
            assertEquals(1, db.periodDao().getAllForProfileSnapshot(newPid).size)
            val restoredMeds = db.medicalCareDao().getMedicinesSnapshot(newPid)
            assertEquals(1, restoredMeds.size)
            val restoredIntakes = db.medicalCareDao().getIntakesSnapshot(newPid)
            assertEquals(1, restoredIntakes.size)
            assertEquals(restoredMeds.first().id, restoredIntakes.first().medicineId)
            assertEquals(1, db.medicalCareDao().getConsultationsSnapshot(newPid).size)
            val restoredAlarms = db.reminderDao().getRemindersSnapshot(newPid)
            assertEquals(1, restoredAlarms.size)
            assertEquals(120, restoredAlarms.first().repeatIntervalMinutes)
            assertEquals(5, restoredAlarms.first().triggeredCount)
            assertEquals(1, db.fitnessDao().getExercisesSnapshot(newPid).size)
            assertEquals(1, db.fitnessDao().getSleepSnapshot(newPid).size)
            assertEquals(1, db.fitnessDao().getAllStepsSnapshot(newPid).size)
            assertEquals(1, db.dietDao().getDietRecordsSnapshot(newPid).size)
            assertEquals(1, db.beautyCareDao().getBeautyRecordsSnapshot(newPid).size)
            assertEquals(1, db.digitalWellbeingDao().getWellbeingRecordsSnapshot(newPid).size)
            assertEquals(1, db.connectedFitnessDao().getConnectedSourcesSnapshot(newPid).size)
            assertEquals(1, db.connectedFitnessDao().getVitalMetricsSnapshot(newPid).size)

            val restoredPrefs = prefsManager.preferencesFlow.first()
            assertEquals(ThemeMode.DARK, restoredPrefs.themeMode)
            assertEquals(SupportedLanguage.TELUGU, restoredPrefs.language)
            assertFalse(restoredPrefs.wellbeingOveruseAlertsEnabled)
            assertEquals(90, restoredPrefs.wellbeingWarningPercent)

            // Corrupted restore must fail safely without deleting existing restored profile
            val corruptResult = backupRepo.restoreFromRawPayload("{not valid json")
            assertTrue(corruptResult.isFailure)
            assertEquals("Eswar G", db.userProfileDao().getActiveProfileSnapshot()?.fullName)
        } finally {
            db.close()
        }
    }

    @Test
    fun `encrypted LCENC1 vault export preview and restore work and reject tampered payloads`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsManager = LocalPreferencesManager(context)
        prefsManager.resetAll()
        val vaultKey = testAesKey()
        val vaultManager = KeystoreVaultManager { vaultKey }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val backupRepo = PrivacyBackupRepository(db, prefsManager, vaultManager)
            val profileId = db.userProfileDao().insertProfile(
                UserProfileEntity(fullName = "Kavya", age = 26, genderIdentity = "Female")
            )
            db.waterDao().insertRecord(
                WaterRecordEntity(profileId = profileId, amountMl = 500, dayKey = DateEpochUtils.todayDayKey())
            )

            val encrypted = backupRepo.buildEncryptedExportPayload()
            assertTrue(encrypted.startsWith("LCENC1:"))

            val preview = backupRepo.parseBackupPreview(encrypted).getOrThrow()
            assertTrue(preview.isEncrypted)
            assertEquals("Kavya", preview.profileName)
            assertEquals(1, preview.totalRecords)

            backupRepo.deleteAllLocalData()
            val restored = backupRepo.restoreFromRawPayload(encrypted).getOrThrow()
            assertEquals("Kavya", restored.profileName)
            assertEquals(1, restored.restoredRecordsCount)

            val tampered = encrypted.dropLast(4) + "AAAA"
            assertTrue(backupRepo.parseBackupPreview(tampered).isFailure)
            assertTrue(backupRepo.restoreFromRawPayload(tampered).isFailure)
            // Intact DB after failed tampered restore
            assertEquals("Kavya", db.userProfileDao().getActiveProfileSnapshot()?.fullName)
        } finally {
            db.close()
        }
    }

    @Test
    fun `entitlement policy keeps period tracker and all phase 1 modules permanently free across tiers`() {
        val phase1AlwaysFreeGates = listOf(
            FeatureGate.WATER_TRACKER,
            FeatureGate.PERIOD_TRACKER,
            FeatureGate.MEDICAL_CARE,
            FeatureGate.EXERCISE_TRACKER,
            FeatureGate.YOGA_TRACKER,
            FeatureGate.DIET_TRACKER,
            FeatureGate.DAILY_REMINDERS,
            FeatureGate.BEAUTY_CARE,
            FeatureGate.DIGITAL_WELLBEING,
            FeatureGate.REPORTS_ANALYTICS,
            FeatureGate.ON_DEVICE_AI,
            FeatureGate.CONNECTED_DEVICES
        )

        SubscriptionTier.entries.forEach { tier ->
            val policy = LocalDefaultEntitlementPolicy { tier }
            assertEquals(tier, policy.currentTier())
            phase1AlwaysFreeGates.forEach { gate ->
                assertTrue("${gate.key} must remain free on $tier", policy.hasAccess(gate))
            }
        }

        val freePolicy = LocalDefaultEntitlementPolicy { SubscriptionTier.FREE }
        assertEquals(1, freePolicy.maxAllowedProfiles())
        assertFalse(freePolicy.hasAccess(FeatureGate.AI_HEALTH_INTELLIGENCE))
        assertFalse(freePolicy.hasAccess(FeatureGate.HEALTH_RECORDS_VAULT))
        assertFalse(freePolicy.hasAccess(FeatureGate.INDIAN_NUTRITION_PLANNER))
        assertFalse(freePolicy.hasAccess(FeatureGate.FAMILY_CIRCLE))

        val plusPolicy = LocalDefaultEntitlementPolicy { SubscriptionTier.PLUS }
        assertTrue(plusPolicy.hasAccess(FeatureGate.AI_HEALTH_INTELLIGENCE))
        assertTrue(plusPolicy.hasAccess(FeatureGate.HEALTH_RECORDS_VAULT))
        assertTrue(plusPolicy.hasAccess(FeatureGate.INDIAN_NUTRITION_PLANNER))
        assertFalse(plusPolicy.hasAccess(FeatureGate.FAMILY_CIRCLE))

        val familyPolicy = LocalDefaultEntitlementPolicy { SubscriptionTier.FAMILY }
        assertEquals(5, familyPolicy.maxAllowedProfiles())
        assertTrue(familyPolicy.hasAccess(FeatureGate.FAMILY_CIRCLE))
        assertTrue(familyPolicy.hasAccess(FeatureGate.AI_HEALTH_INTELLIGENCE))
    }

    @Test
    fun `exported room schema v7 json exists and non-destructive migrations 1 to 7 preserve legacy rows`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val schemaJson = runCatching {
            context.assets
                .open("com.example.data.local.AppDatabase/7.json")
                .bufferedReader()
                .use { it.readText() }
        }.getOrElse {
            val schemaFile = listOf(
                java.io.File("schemas/com.example.data.local.AppDatabase/7.json"),
                java.io.File("app/schemas/com.example.data.local.AppDatabase/7.json")
            ).firstOrNull { it.exists() }
                ?: error("Exported Room schema 7.json was not found in test assets or schemas directory")
            schemaFile.readText()
        }
        val schemaRoot = JSONObject(schemaJson)
        val dbObj = schemaRoot.getJSONObject("database")
        assertEquals(7, dbObj.getInt("version"))
        assertEquals(17, dbObj.getJSONArray("entities").length())

        val dbName = "migration_test_v1_to_v7.db"
        context.deleteDatabase(dbName)

        // 1. Create authentic v1 SQLite database using version = 1
        val v1Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(1) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE user_profiles (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                fullName TEXT NOT NULL,
                                age INTEGER NOT NULL,
                                genderIdentity TEXT NOT NULL,
                                customGenderLabel TEXT NOT NULL,
                                heightCm INTEGER,
                                weightKg REAL,
                                activityLevel TEXT NOT NULL,
                                dailyWaterGoalMl INTEGER NOT NULL,
                                cycleTrackingEnabled INTEGER NOT NULL,
                                averageCycleLengthDays INTEGER NOT NULL,
                                averagePeriodDurationDays INTEGER NOT NULL,
                                preferredLanguageCode TEXT NOT NULL,
                                createdAtEpochMs INTEGER NOT NULL,
                                updatedAtEpochMs INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE medicines (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                profileId INTEGER NOT NULL,
                                name TEXT NOT NULL,
                                dosage TEXT NOT NULL,
                                scheduleTime24h TEXT NOT NULL,
                                frequency TEXT NOT NULL,
                                isActive INTEGER NOT NULL,
                                notes TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE doctor_consultations (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                profileId INTEGER NOT NULL,
                                doctorName TEXT NOT NULL,
                                specialty TEXT NOT NULL,
                                consultationEpochMs INTEGER NOT NULL,
                                summary TEXT NOT NULL,
                                followUpEpochDay INTEGER
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE reminders (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                profileId INTEGER NOT NULL,
                                title TEXT NOT NULL,
                                moduleKey TEXT NOT NULL,
                                triggerTime24h TEXT NOT NULL,
                                daysOfWeekMask INTEGER NOT NULL,
                                isEnabled INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE digital_wellbeing_records (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                profileId INTEGER NOT NULL,
                                dayKey TEXT NOT NULL,
                                screenTimeMinutes INTEGER NOT NULL,
                                focusSessionsCount INTEGER NOT NULL,
                                mindfulGoalMinutes INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO user_profiles VALUES (1, 'Legacy User', 34, 'Female', '', 165, 62.0, 'Moderate', 2400, 1, 28, 5, 'en', 1000, 1000)
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO reminders VALUES (1, 1, 'Legacy Water Alarm', 'water', '09:00', 127, 1)
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO medicines VALUES (1, 1, 'Iron Supplement', '65mg', '09:00', 'Daily', 1, 'After lunch')
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        v1Helper.writableDatabase.close()

        // 2. Execute ALL_MIGRATIONS sequentially on the real v1 database file
        val supportDb = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        AppDatabase.ALL_MIGRATIONS.forEach { migration ->
                            migration.migrate(db)
                        }
                    }
                })
                .build()
        ).writableDatabase

        try {
            val reminderCursor = supportDb.query(
                "SELECT title, soundKey, alarmEmoji, gesturePreset, repeatIntervalMinutes FROM reminders WHERE id = 1"
            )
            assertTrue(reminderCursor.moveToFirst())
            assertEquals("Legacy Water Alarm", reminderCursor.getString(0))
            assertEquals("water_drop", reminderCursor.getString(1))
            assertEquals("⏰", reminderCursor.getString(2))
            assertEquals("SWIPE_SLIDE", reminderCursor.getString(3))
            assertEquals(1440, reminderCursor.getInt(4))
            reminderCursor.close()

            val medCursor = supportDb.query("SELECT name, dosesPerDay, intervalHours FROM medicines WHERE id = 1")
            assertTrue(medCursor.moveToFirst())
            assertEquals("Iron Supplement", medCursor.getString(0))
            assertEquals(1, medCursor.getInt(1))
            assertEquals(24, medCursor.getInt(2))
            medCursor.close()

            val tableCheck = supportDb.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('medicine_intakes','connected_fitness_sources','health_vital_metrics')"
            )
            val createdTables = mutableSetOf<String>()
            while (tableCheck.moveToNext()) {
                createdTables += tableCheck.getString(0)
            }
            tableCheck.close()
            assertEquals(
                setOf("medicine_intakes", "connected_fitness_sources", "health_vital_metrics"),
                createdTables
            )
        } finally {
            supportDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `type-safe navigation destinations map all module sub-routes cleanly`() {
        assertEquals(LifeWeaveDest.FitnessYoga, LifeWeaveDest.fromSubRouteString(ModuleSubRoutes.ROUTE_FITNESS_YOGA))
        assertEquals(LifeWeaveDest.Diet, LifeWeaveDest.fromSubRouteString(ModuleSubRoutes.ROUTE_DIET))
        assertEquals(LifeWeaveDest.Medical, LifeWeaveDest.fromSubRouteString(ModuleSubRoutes.ROUTE_MEDICAL))
        assertEquals(LifeWeaveDest.Beauty, LifeWeaveDest.fromSubRouteString(ModuleSubRoutes.ROUTE_BEAUTY))
        assertEquals(LifeWeaveDest.Wellbeing, LifeWeaveDest.fromSubRouteString(ModuleSubRoutes.ROUTE_WELLBEING))
        assertEquals(LifeWeaveDest.Reports, LifeWeaveDest.fromSubRouteString(ModuleSubRoutes.ROUTE_REPORTS))
        assertEquals(LifeWeaveDest.OnDeviceAi, LifeWeaveDest.fromSubRouteString(ModuleSubRoutes.ROUTE_ON_DEVICE_AI))
        assertEquals(LifeWeaveDest.ConnectedDevices, LifeWeaveDest.fromSubRouteString(ModuleSubRoutes.ROUTE_CONNECTED_DEVICES))
        assertEquals(LifeWeaveDest.Water, LifeWeaveDest.fromSubRouteString("water"))
        assertEquals(LifeWeaveDest.Period, LifeWeaveDest.fromSubRouteString("period"))
        assertEquals(LifeWeaveDest.Settings, LifeWeaveDest.fromSubRouteString("settings"))
        assertNotNull(LifeWeaveDest.fromSubRouteString("unknown"))
    }

    @Test
    fun `category reminder schedules and custom styles round-trip cleanly in backup export and restore`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsManager = LocalPreferencesManager(context)
        prefsManager.resetAll()
        prefsManager.updateReminderSchedule("water", enabled = true, hour = 9, minute = 15, repeatIntervalMinutes = 90)
        prefsManager.updateCategoryNotificationStyle(
            categoryKey = "water",
            emoji = "🚰",
            customMessage = "Hydrate before standup",
            gesturePreset = AlarmEmojiGesturePreset.HOLD_RING.id
        )
        prefsManager.updateReminderSchedule("period", enabled = true, hour = 20, minute = 45, repeatIntervalMinutes = 1440)
        prefsManager.updateReminderSchedule("wellness", enabled = false, hour = 21, minute = 30, repeatIntervalMinutes = 720)

        val vaultManager = KeystoreVaultManager { testAesKey() }
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            db.userProfileDao().insertProfile(UserProfileEntity(fullName = "Priya", age = 28, genderIdentity = "Female"))
            val backupRepo = PrivacyBackupRepository(db, prefsManager, vaultManager)
            val exported = backupRepo.buildExportJson()
            val schedulesJson = JSONObject(exported)
                .getJSONObject("uiPreferences")
                .getJSONObject("categorySchedules")
            assertTrue(schedulesJson.getJSONObject("water").getBoolean("enabled"))
            assertEquals(90, schedulesJson.getJSONObject("water").getInt("repeatIntervalMinutes"))
            assertEquals("🚰", schedulesJson.getJSONObject("water").getString("customEmoji"))

            backupRepo.deleteAllLocalData()
            assertFalse(prefsManager.preferencesFlow.first().waterTone.reminderEnabled)

            backupRepo.restoreFromRawPayload(exported).getOrThrow()
            val restoredPrefs = prefsManager.preferencesFlow.first()
            assertTrue(restoredPrefs.waterTone.reminderEnabled)
            assertEquals(9, restoredPrefs.waterTone.reminderHour)
            assertEquals(15, restoredPrefs.waterTone.reminderMinute)
            assertEquals(90, restoredPrefs.waterTone.repeatIntervalMinutes)
            assertEquals("🚰", restoredPrefs.waterTone.customEmoji)
            assertEquals("Hydrate before standup", restoredPrefs.waterTone.customMessage)
            assertEquals(AlarmEmojiGesturePreset.HOLD_RING.id, restoredPrefs.waterTone.gesturePreset)

            assertTrue(restoredPrefs.periodTone.reminderEnabled)
            assertEquals(20, restoredPrefs.periodTone.reminderHour)
            assertEquals(45, restoredPrefs.periodTone.reminderMinute)
            assertFalse(restoredPrefs.wellnessTone.reminderEnabled)
            assertEquals(720, restoredPrefs.wellnessTone.repeatIntervalMinutes)
        } finally {
            db.close()
        }
    }

    @Test
    fun `keystore vault rejects invalid IV lengths truncated ciphertext and bad envelopes`() {
        val vaultManager = KeystoreVaultManager { testAesKey() }

        val shortIv = Base64.encodeToString(ByteArray(8), Base64.NO_WRAP)
        val validLenCipher = Base64.encodeToString(ByteArray(32), Base64.NO_WRAP)
        val badIvError = runCatching {
            vaultManager.decryptBackupPayload("LCENC1:$shortIv:$validLenCipher")
        }.exceptionOrNull()
        assertTrue(badIvError is IllegalArgumentException)

        val validIv = Base64.encodeToString(ByteArray(12), Base64.NO_WRAP)
        val truncatedCipher = Base64.encodeToString(ByteArray(10), Base64.NO_WRAP)
        val truncatedError = runCatching {
            vaultManager.decryptBackupPayload("LCENC1:$validIv:$truncatedCipher")
        }.exceptionOrNull()
        assertTrue(truncatedError is IllegalArgumentException)

        val unsupportedError = runCatching {
            vaultManager.decryptBackupPayload("BAD_HEADER:abc:def")
        }.exceptionOrNull()
        assertTrue(unsupportedError is IllegalArgumentException)
    }

    @Test
    fun `high-id custom alarms schedule without request code collisions and domain calculators stay deterministic`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val soundManager = AlertSoundManager(context)
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val shadowAlarmManager = Shadows.shadowOf(alarmManager)

        val lowAlarm = ReminderEntity(
            id = 1L,
            profileId = 1L,
            title = "Morning Vitamin",
            moduleKey = "medicine",
            triggerTime24h = "08:00",
            isEnabled = true
        )
        val highAlarm = ReminderEntity(
            id = 201L,
            profileId = 1L,
            title = "Evening Magnesium",
            moduleKey = "medicine",
            triggerTime24h = "21:00",
            isEnabled = true,
            repeatIntervalMinutes = 720
        )

        soundManager.scheduleCustomAlarm(lowAlarm)
        soundManager.scheduleCustomAlarm(highAlarm)
        assertTrue(shadowAlarmManager.scheduledAlarms.size >= 2)

        val payload1 = AlarmPopupPayload(
            notificationId = 5001,
            alarmId = 1L,
            categoryKey = "medicine",
            title = "Morning Vitamin",
            message = "Take 1 tablet"
        )
        val payload201 = AlarmPopupPayload(
            notificationId = 5201,
            alarmId = 201L,
            categoryKey = "medicine",
            title = "Evening Magnesium",
            message = "Take 1 tablet"
        )
        soundManager.scheduleSnoozeAlarm(payload1, minutes = 10)
        soundManager.scheduleSnoozeAlarm(payload201, minutes = 15)
        assertTrue(shadowAlarmManager.scheduledAlarms.size >= 4)

        val posted1 = soundManager.postPopupAlarmNotification(
            channelId = soundManager.ensureCustomAlarmChannel(lowAlarm, soundManager.resolveReminderSoundUri(lowAlarm)),
            payload = payload1,
            soundUri = null
        )
        val posted201 = soundManager.postPopupAlarmNotification(
            channelId = soundManager.ensureCustomAlarmChannel(highAlarm, soundManager.resolveReminderSoundUri(highAlarm)),
            payload = payload201,
            soundUri = null
        )
        assertTrue(posted1)
        assertTrue(posted201)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val shadowNm = Shadows.shadowOf(nm)
        assertNotNull(shadowNm.getNotification(5001))
        assertNotNull(shadowNm.getNotification(5201))

        val hydration = HydrationCalculator.summarize(emptyList(), 2500)
        val cycle = CycleCalculator.analyze(emptyList(), 28, 5)
        val alertMetrics = AlertMetricsCalculator.summarize(
            listOf(
                lowAlarm.copy(triggeredCount = 4, completedCount = 3, snoozedCount = 1),
                highAlarm.copy(triggeredCount = 2, completedCount = 2, snoozedCount = 0)
            )
        )
        assertEquals(6, alertMetrics.totalTriggered)
        assertEquals(5, alertMetrics.totalCompleted)
        val briefing = OnDeviceWellnessAdvisor.buildDailyBriefing(
            profile = UserProfileEntity(fullName = "Eswar", age = 29, genderIdentity = "Male"),
            hydration = hydration,
            cycle = cycle,
            exercises = emptyList(),
            diet = emptyList(),
            sleep = emptyList(),
            steps = null,
            wellbeing = null,
            aiPref = null
        )
        assertTrue(briefing.isNotBlank())
    }
}
