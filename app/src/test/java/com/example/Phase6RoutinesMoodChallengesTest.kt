package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.DigitalWellbeingRecordEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.data.repository.CustomAlarmRepository
import com.example.data.repository.PrivacyBackupRepository
import com.example.data.repository.RoutineMoodRepository
import com.example.domain.DateEpochUtils
import com.example.domain.RoutineMoodAnalyticsEngine
import com.example.domain.RoutineTimeSlot
import com.example.notifications.AlertSoundManager
import com.example.notifications.BundledAlertTone
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase6RoutinesMoodChallengesTest {

    @Test
    fun `exported Room schema 11 json and non-destructive MIGRATION_10_11 preserve v10 records`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val schemaDir = listOf(
            File("schemas/com.example.data.local.AppDatabase"),
            File("app/schemas/com.example.data.local.AppDatabase")
        ).firstOrNull { it.exists() && it.isDirectory }
        val schemaFile = schemaDir?.let { dir ->
            File(dir, "11.json").takeIf { it.exists() }
                ?: dir.listFiles { f -> f.extension == "json" }
                    ?.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: 0 }
        }
        assertNotNull("Expected exported Room schema (>= 11.json) to exist", schemaFile)
        val schemaJson = JSONObject(schemaFile!!.readText()).getJSONObject("database")
        val exportedVersion = schemaJson.getInt("version")
        val entities = schemaJson.getJSONArray("entities")
        assertTrue("Expected exported schema version >= 11, got $exportedVersion", exportedVersion >= 11)
        assertTrue("Expected at least 29 entities in exported schema", entities.length() >= 29)
        val tableNames = (0 until entities.length())
            .map { entities.getJSONObject(it).getString("tableName") }
            .toSet()
        assertTrue(tableNames.containsAll(listOf("daily_routines", "routine_completions", "mood_journal_entries", "wellness_challenges", "achievement_badges")))

        val dbName = "migration_10_to_11_test.db"
        context.deleteDatabase(dbName)

        val v10Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(10) {
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
                                relationLabel TEXT NOT NULL,
                                linkedFamilyId TEXT,
                                linkedSlotId TEXT,
                                createdAtEpochMs INTEGER NOT NULL,
                                updatedAtEpochMs INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO user_profiles (
                                fullName, age, genderIdentity, customGenderLabel, heightCm, weightKg,
                                activityLevel, dailyWaterGoalMl, cycleTrackingEnabled, averageCycleLengthDays,
                                averagePeriodDurationDays, preferredLanguageCode, relationLabel,
                                linkedFamilyId, linkedSlotId, createdAtEpochMs, updatedAtEpochMs
                            ) VALUES ('Ananya', 27, 'Female', '', 160, 55.0, 'Moderate', 2500, 1, 28, 5, 'en', 'Self', NULL, NULL, 1000, 1000)
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )

        val rawDb = v10Helper.writableDatabase
        AppDatabase.MIGRATION_10_11.migrate(rawDb)

        rawDb.query("SELECT fullName, relationLabel FROM user_profiles").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Ananya", c.getString(0))
            assertEquals("Self", c.getString(1))
        }
        rawDb.execSQL(
            """
            INSERT INTO daily_routines (
                profileId, title, emoji, timeSlot, triggerTime24h, estimatedMinutes,
                stepsSerialized, linkedModuleKey, reminderId, reminderEnabled,
                isOptional, isActive, sortOrder, createdAtEpochMs
            ) VALUES (1, 'Morning Sip', '💧', 'Morning', '07:30', 5, 'Drink 250ml', 'water_tracker', NULL, 0, 1, 1, 0, 2000)
            """.trimIndent()
        )
        rawDb.query("SELECT title FROM daily_routines WHERE profileId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Morning Sip", c.getString(0))
        }
        v10Helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `smart routine suggestions adapt to real records and custom reminders persist per profile`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val profile1 = UserProfileEntity(
                fullName = "Riya",
                age = 29,
                genderIdentity = "Female",
                dailyWaterGoalMl = 2600,
                cycleTrackingEnabled = true
            )
            val pid1 = db.userProfileDao().insertProfile(profile1)
            val pid2 = db.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Kabir",
                    age = 33,
                    genderIdentity = "Male",
                    dailyWaterGoalMl = 2800,
                    cycleTrackingEnabled = false
                )
            )

            val savedP1 = profile1.copy(id = pid1)
            val meds = listOf(
                MedicineEntity(
                    profileId = pid1,
                    name = "Vitamin D3",
                    dosage = "1000 IU",
                    scheduleTime24h = "08:30",
                    frequency = "Daily"
                )
            )
            val nowMs = System.currentTimeMillis()
            val sleep = listOf(
                SleepRecordEntity(
                    profileId = pid1,
                    bedtimeEpochMs = nowMs - 6 * 3_600_000L,
                    wakeEpochMs = nowMs,
                    qualityRating = 3
                )
            )
            val wellbeing = listOf(
                DigitalWellbeingRecordEntity(
                    profileId = pid1,
                    dayKey = DateEpochUtils.todayDayKey(nowMs),
                    screenTimeMinutes = 110,
                    nightScreenMinutes = 35
                )
            )

            val suggestions = RoutineMoodAnalyticsEngine.suggestOptionalRoutines(
                profile = savedP1,
                existingRoutines = emptyList(),
                medicines = meds,
                sleepRecords = sleep,
                wellbeingRecords = wellbeing,
                waterRecords = emptyList()
            )
            assertTrue(suggestions.any { it.templateKey == "medication_meal_pause" })
            assertTrue(suggestions.any { it.templateKey == "cycle_comfort_check" })
            val windDown = suggestions.first { it.templateKey == "evening_wind_down" }
            assertTrue(windDown.reason.contains("6.0h"))

            val alarmRepo = CustomAlarmRepository(db)
            val repo = RoutineMoodRepository(db, alarmRepo, AlertSoundManager(context))
            repo.ensureDefaultCatalogSeeded(pid1)
            repo.ensureDefaultCatalogSeeded(pid2)

            val routineId = repo.createOrUpdateRoutine(
                profileId = pid1,
                title = "Gentle Morning Flow",
                emoji = "🌅",
                timeSlot = RoutineTimeSlot.MORNING,
                triggerTime24h = "07:45",
                estimatedMinutes = 10,
                steps = listOf("Warm water", "2m breathing"),
                reminderEnabled = true,
                reminderSoundKey = BundledAlertTone.MORNING_DEW.id,
                repeatIntervalMinutes = 1440
            )
            assertTrue(routineId > 0L)

            val p1Routines = repo.observeRoutines(pid1).first()
            val p2Routines = repo.observeRoutines(pid2).first()
            assertEquals(1, p1Routines.size)
            assertEquals(0, p2Routines.size)
            assertTrue(p1Routines.first().reminderEnabled)
            assertNotNull(p1Routines.first().reminderId)

            val alarms = alarmRepo.observeAlarms(pid1).first()
            assertEquals(1, alarms.size)
            assertEquals("07:45", alarms.first().triggerTime24h)
            assertEquals(BundledAlertTone.MORNING_DEW.id, alarms.first().soundKey)

            // Toggle step 1 of 2, then step 2 of 2
            val routine = p1Routines.first()
            repo.toggleRoutineStepForDay(routine, 0)
            val partial = repo.observeRecentCompletions(pid1).first()
            assertEquals(1, partial.size)
            assertEquals(1, partial.first().completedStepsCount)
            assertEquals(2, partial.first().totalStepsCount)

            repo.toggleRoutineStepForDay(routine, 1)
            val full = repo.observeRecentCompletions(pid1).first()
            assertEquals(2, full.first().completedStepsCount)

            // Pause reminder cleanly
            repo.toggleRoutineReminder(routine, enabled = false)
            assertFalse(repo.observeRoutines(pid1).first().first().reminderEnabled)
            assertFalse(alarmRepo.observeAlarms(pid1).first().first().isEnabled)
        } finally {
            db.close()
        }
    }

    @Test
    fun `mood journal analytics non-manipulative challenges and backup roundtrip work seamlessly`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = LocalPreferencesManager(context)
        prefs.resetAll()

        try {
            val pid = db.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Vikram",
                    age = 31,
                    genderIdentity = "Male",
                    dailyWaterGoalMl = 2500
                )
            )
            prefs.setActiveProfileId(pid)

            val alarmRepo = CustomAlarmRepository(db)
            val soundManager = AlertSoundManager(context)
            val repo = RoutineMoodRepository(db, alarmRepo, soundManager)
            repo.ensureDefaultCatalogSeeded(pid)

            // Challenges start strictly AVAILABLE (opt-in only)
            val initialChallenges = repo.observeChallenges(pid).first()
            assertEquals(5, initialChallenges.size)
            assertTrue(initialChallenges.all { it.status == "AVAILABLE" && it.isOptInOnly })

            // Adopt & complete a challenge with pause/resume preservation
            val hydrationChallenge = initialChallenges.first { it.challengeKey == "hydration_harmony" }
            repo.joinChallenge(hydrationChallenge)
            val day1 = DateEpochUtils.dayKeyDaysAgo(2)
            val day2 = DateEpochUtils.dayKeyDaysAgo(1)
            val today = DateEpochUtils.todayDayKey()

            var ch = repo.observeChallenges(pid).first().first { it.challengeKey == "hydration_harmony" }
            assertEquals("ACTIVE", ch.status)
            assertTrue(repo.checkInChallengeDay(ch, day1))
            ch = repo.observeChallenges(pid).first().first { it.challengeKey == "hydration_harmony" }
            // Idempotent same-day check-in does not inflate count
            assertFalse(repo.checkInChallengeDay(ch, day1))
            assertEquals(1, ch.currentProgressDays)

            // Pause and resume preserves progress without zeroing out
            repo.pauseChallenge(ch)
            val paused = repo.observeChallenges(pid).first().first { it.challengeKey == "hydration_harmony" }
            assertEquals("PAUSED", paused.status)
            assertEquals(1, paused.currentProgressDays)

            repo.joinChallenge(paused)
            assertTrue(repo.checkInChallengeDay(repo.observeChallenges(pid).first().first { it.challengeKey == "hydration_harmony" }, day2))
            assertEquals(2, repo.observeChallenges(pid).first().first { it.challengeKey == "hydration_harmony" }.currentProgressDays)

            // Log mood entries + water + routine completion and verify grounded analytics + badges
            val rId = repo.createOrUpdateRoutine(
                profileId = pid,
                title = "Evening Wind-Down",
                emoji = "🌙",
                timeSlot = RoutineTimeSlot.EVENING,
                triggerTime24h = "21:30",
                estimatedMinutes = 8,
                steps = listOf("Dim screen")
            )
            val routine = repo.observeRoutines(pid).first().first { it.id == rId }
            repo.toggleCompleteWholeRoutineToday(routine)

            val calmPreset = RoutineMoodAnalyticsEngine.moodPresets.first { it.label == "Calm" }
            val joyfulPreset = RoutineMoodAnalyticsEngine.moodPresets.first { it.label == "Joyful" }
            val tiredPreset = RoutineMoodAnalyticsEngine.moodPresets.first { it.label == "Tired" }

            repo.logMoodEntry(pid, joyfulPreset, 5, 1, listOf("Hydration", "Rest"), "Refreshed after walk", "Warm sunlight")
            repo.logMoodEntry(pid, calmPreset, 4, 2, listOf("Sleep"), "Quiet evening", "Home cooked meal")
            repo.logMoodEntry(pid, tiredPreset, 2, 3, listOf("Work"), "Busy afternoon", "Kind teammate", loggedAtEpochMs = System.currentTimeMillis() - 86_400_000L)

            db.waterDao().insertRecord(WaterRecordEntity(profileId = pid, amountMl = 1600, dayKey = today))
            db.waterDao().insertRecord(WaterRecordEntity(profileId = pid, amountMl = 1700, dayKey = day2))

            repo.syncAchievements(pid)
            val badges = repo.observeBadges(pid).first()
            assertNotNull(badges.first { it.badgeKey == "first_routine_anchor" }.earnedAtEpochMs)
            assertNotNull(badges.first { it.badgeKey == "mood_reflector_3" }.earnedAtEpochMs)
            assertNull(badges.first { it.badgeKey == "challenge_finisher" }.earnedAtEpochMs)

            val moodAnalytics = RoutineMoodAnalyticsEngine.analyzeMoodJournal(
                entries = repo.observeMoodEntries(pid).first(),
                sleepRecords = emptyList(),
                waterRecords = db.waterDao().getAllForProfileSnapshot(pid),
                routineCompletions = repo.observeRecentCompletions(pid).first()
            )
            assertEquals(3, moodAnalytics.totalEntriesLast7Days)
            assertTrue(moodAnalytics.reflectiveObservations.isNotEmpty())

            // Mood reminder configuration
            repo.configureMoodJournalReminder(pid, enabled = true, triggerTime24h = "20:30")
            assertEquals(1, alarmRepo.observeAlarms(pid).first().count { it.moduleKey == "mood_journal" && it.isEnabled })

            // Verify backup JSON export + restore roundtrip retains Phase 6 tables cleanly
            val backupRepo = PrivacyBackupRepository(db, prefs, KeystoreVaultManager())
            val exportedJson = backupRepo.buildExportJson()
            val preview = backupRepo.parseBackupPreview(exportedJson).getOrThrow()
            assertTrue(preview.breakdownText.contains("Routines"))
            assertTrue(preview.breakdownText.contains("Mood"))

            val restoreSummary = backupRepo.restoreFromRawPayload(exportedJson).getOrThrow()
            assertTrue(restoreSummary.restoredRecordsCount > 0)
            val restoredProfileId = db.userProfileDao().getActiveProfileSnapshot()!!.id
            assertEquals(1, db.wellnessEngagementDao().getRoutinesSnapshot(restoredProfileId).size)
            assertEquals(1, db.wellnessEngagementDao().getCompletionsSnapshot(restoredProfileId).size)
            assertEquals(3, db.wellnessEngagementDao().getMoodEntriesSnapshot(restoredProfileId).size)
            assertEquals(5, db.wellnessEngagementDao().getChallengesSnapshot(restoredProfileId).size)
            assertEquals(6, db.wellnessEngagementDao().getBadgesSnapshot(restoredProfileId).size)
        } finally {
            db.close()
        }
    }

    @Test
    fun `syncAchievements returns newly unlocked badges once without duplicate triggers`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val pid = db.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Meera",
                    age = 28,
                    genderIdentity = "Female",
                    dailyWaterGoalMl = 2400
                )
            )
            val repo = RoutineMoodRepository(db, CustomAlarmRepository(db), AlertSoundManager(context))
            repo.ensureDefaultCatalogSeeded(pid)

            // No badges earned initially
            assertTrue(repo.syncAchievements(pid).isEmpty())

            // Directly insert a routine and verify first_routine_anchor unlocks once
            db.wellnessEngagementDao().insertRoutine(
                com.example.data.local.entity.DailyRoutineEntity(
                    profileId = pid,
                    title = "Stretch Break",
                    emoji = "🧘",
                    timeSlot = "Morning",
                    triggerTime24h = "08:00",
                    estimatedMinutes = 5,
                    stepsSerialized = "Shoulder rolls||Deep breath"
                )
            )
            val firstSync = repo.syncAchievements(pid)
            assertEquals(1, firstSync.size)
            assertEquals("first_routine_anchor", firstSync.first().badgeKey)

            // Subsequent sync should return empty unless another milestone is crossed
            assertTrue(repo.syncAchievements(pid).isEmpty())

            val adherence = RoutineMoodAnalyticsEngine.summarizeRoutineAdherence(
                routines = repo.observeRoutines(pid).first(),
                completions = repo.observeRecentCompletions(pid).first()
            )
            assertEquals(1, adherence.activeRoutinesCount)
            assertEquals(0, adherence.completedTodayCount)
            assertEquals(7, adherence.sevenDayRhythm.size)
        } finally {
            db.close()
        }
    }
}
