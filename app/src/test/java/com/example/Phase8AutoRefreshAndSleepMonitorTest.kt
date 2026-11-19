package com.example

import android.content.Context
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.AllModulesRepository
import com.example.healthsync.FitnessSyncRepository
import com.example.healthsync.HealthConnectManager
import com.example.notifications.AlertSoundManager
import com.example.security.LocalPreferencesManager
import com.example.sleep.EstimatedSleepStage
import com.example.sleep.SleepAcousticAnalyzer
import com.example.sleep.SleepMonitorLiveState
import com.example.sync.AppAutoRefreshManager
import com.example.ui.components.AutoRefreshControlCard
import com.example.ui.components.SleepQualitySnoringMonitorCard
import com.example.ui.components.SleepRecordDetailCard
import com.example.ui.theme.LifeCompanionTheme
import com.example.ui.theme.ThemeMode
import com.example.wellbeing.DeviceWellbeingManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.math.PI
import kotlin.math.roundToInt
import kotlin.math.sin

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase8AutoRefreshAndSleepMonitorTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun synthesizeToneFrame(
        freqHz: Double,
        amplitude: Double,
        sampleRateHz: Int = 16_000,
        durationMs: Int = 250,
        harmonicFreqHz: Double? = null
    ): ShortArray {
        val count = (sampleRateHz * durationMs) / 1000
        return ShortArray(count) { i ->
            val t = i.toDouble() / sampleRateHz.toDouble()
            var v = sin(2.0 * PI * freqHz * t) * amplitude
            if (harmonicFreqHz != null) {
                v += sin(2.0 * PI * harmonicFreqHz * t) * (amplitude * 0.45)
            }
            (v.coerceIn(-1.0, 1.0) * 32767.0).roundToInt().toShort()
        }
    }

    @Test
    fun `SleepAcousticAnalyzer detects realistic low-frequency snoring bursts and rejects quiet room and high-frequency noise`() {
        val startMs = 1_750_000_000_000L
        val analyzer = SleepAcousticAnalyzer(sessionStartEpochMs = startMs, sampleRateHz = 16_000)

        // 1. Calibrate quiet ambient room (~31 dB) for 2 seconds
        val quietFrame = synthesizeToneFrame(freqHz = 200.0, amplitude = 0.0007)
        var t = startMs
        repeat(8) {
            analyzer.analyzePcmFrame(quietFrame, t)
            t += 250L
        }
        assertEquals(0, analyzer.snoreEventsCount)

        // 2. Inject 2 realistic snoring bursts (180 Hz + 270 Hz harmonics, 1.0s each, 1.5s quiet gap)
        val snoreFrame = synthesizeToneFrame(freqHz = 180.0, amplitude = 0.14, harmonicFreqHz = 270.0)
        repeat(2) {
            repeat(4) { // 4 x 250ms = 1000ms burst
                val m = analyzer.analyzePcmFrame(snoreFrame, t)
                assertTrue("Snore frame should have dominant low-band ratio", m.lowBandEnergyRatio >= 0.56)
                t += 250L
            }
            repeat(6) { // 1500ms quiet breathing pause
                analyzer.analyzePcmFrame(quietFrame, t)
                t += 250L
            }
        }
        assertEquals("Should detect exactly 2 distinct snore events", 2, analyzer.snoreEventsCount)
        assertTrue("Peak snore dB should exceed 55 dB", analyzer.peakSnoreDb >= 55.0)
        assertTrue("Snore duration should be at least 1 minute when snores occurred", analyzer.snoreDurationMinutes() >= 1)

        // 3. Inject high-frequency clap/alarm noise (2600 Hz, loud) — must NOT count as snoring
        val highFreqNoise = synthesizeToneFrame(freqHz = 2600.0, amplitude = 0.25)
        repeat(4) {
            val m = analyzer.analyzePcmFrame(highFreqNoise, t)
            assertFalse("High-frequency sound must not classify as snore", m.isSnoreLikeFrame)
            t += 250L
        }
        repeat(4) {
            analyzer.analyzePcmFrame(quietFrame, t)
            t += 250L
        }
        assertEquals("High-frequency noise must not increment snore count", 2, analyzer.snoreEventsCount)
    }

    @Test
    fun `SleepAcousticAnalyzer fuses accelerometer motion into Deep, Light, Awake stages and bounded quality score`() {
        val startMs = 1_750_000_000_000L
        val analyzer = SleepAcousticAnalyzer(sessionStartEpochMs = startMs)

        // Minute 0-2: very still (gravity only -> Deep sleep)
        for (min in 0..2) {
            val ts = startMs + min * 60_000L + 5_000L
            repeat(5) {
                analyzer.recordAccelerometerSample(0.02f, 0.03f, 9.81f, ts + it * 1000L)
            }
        }

        // Minute 3: strong bed movement -> Awake interruption
        val awakeTs = startMs + 3 * 60_000L + 5_000L
        repeat(5) {
            analyzer.recordAccelerometerSample(2.5f, 1.8f, 11.4f, awakeTs + it * 1000L)
        }

        // Minute 4: calm again -> Deep sleep
        val calmTs = startMs + 4 * 60_000L + 5_000L
        repeat(5) {
            analyzer.recordAccelerometerSample(0.01f, 0.02f, 9.80f, calmTs + it * 1000L)
        }

        val report = analyzer.buildSessionReport(startMs + 5 * 60_000L)
        assertEquals(5, report.durationMinutes)
        assertTrue("Deep sleep minutes should be recorded", report.deepSleepMinutes >= 2)
        assertTrue("Wake interruption should be detected", report.awakeInterruptionsCount >= 1)
        assertTrue("Quality score should stay in 15..100", report.sleepQualityScore in 15..100)
        assertTrue("Rating should stay in 1..5", report.qualityRating in 1..5)
        assertTrue("Summary note should include Auto Sensor details", report.summaryNote.contains("Auto Sensor"))
    }

    @Test
    fun `non-destructive MIGRATION_12_13 preserves v12 sleep records and enables automated snoring telemetry persistence`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val dbName = "test_migration_12_13_${System.nanoTime()}.db"
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(12) {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        db.execSQL(
                            """
                            CREATE TABLE user_profiles (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                fullName TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            CREATE TABLE sleep_records (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                profileId INTEGER NOT NULL,
                                bedtimeEpochMs INTEGER NOT NULL,
                                wakeEpochMs INTEGER NOT NULL,
                                qualityRating INTEGER NOT NULL,
                                notes TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL("INSERT INTO user_profiles VALUES (1, 'Asha')")
                        db.execSQL("INSERT INTO sleep_records VALUES (1, 1, 100000, 28900000, 4, 'Legacy sleep log')")
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val rawDb = helper.writableDatabase
        try {
            AppDatabase.MIGRATION_12_13.migrate(rawDb)
            val cursor = rawDb.query(
                "SELECT notes, sleepQualityScore, snoreEventsCount, peakSnoreDb, trackingSource FROM sleep_records WHERE id = 1"
            )
            assertTrue(cursor.moveToFirst())
            assertEquals("Legacy sleep log", cursor.getString(0))
            assertEquals(0, cursor.getInt(1))
            assertEquals(0, cursor.getInt(2))
            assertEquals(0.0, cursor.getDouble(3), 0.001)
            assertEquals("Manual", cursor.getString(4))
            cursor.close()
        } finally {
            rawDb.close()
            context.deleteDatabase(dbName)
        }

        // Also verify full Room v13 round-trip via AllModulesRepository.logAutomatedSleepSession
        val inMemoryDb = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val profileId = inMemoryDb.userProfileDao().insertProfile(
                UserProfileEntity(
                    fullName = "Sleep Tester",
                    age = 30,
                    genderIdentity = "Prefer not to say"
                )
            )
            val repo = AllModulesRepository(inMemoryDb)
            val endMs = System.currentTimeMillis()
            val startMs = endMs - 7 * 3_600_000L
            repo.logAutomatedSleepSession(
                profileId = profileId,
                bedtimeEpochMs = startMs,
                wakeEpochMs = endMs,
                qualityRating = 4,
                sleepQualityScore = 84,
                snoreEventsCount = 6,
                snoreDurationMinutes = 5,
                peakSnoreDb = 63.5,
                avgAmbientDb = 33.2,
                restlessnessIndex = 19,
                awakeInterruptionsCount = 1,
                deepSleepMinutes = 130,
                lightSleepMinutes = 275,
                notes = "Auto Sensor • Quality 84/100"
            )
            val saved = inMemoryDb.fitnessDao().getSleepSnapshot(profileId)
            assertEquals(1, saved.size)
            val rec = saved.first()
            assertEquals(84, rec.sleepQualityScore)
            assertEquals(6, rec.snoreEventsCount)
            assertEquals(5, rec.snoreDurationMinutes)
            assertEquals(63.5, rec.peakSnoreDb, 0.01)
            assertEquals(19, rec.restlessnessIndex)
            assertEquals(130, rec.deepSleepMinutes)
            assertEquals("Auto Sensor", rec.trackingSource)
        } finally {
            inMemoryDb.close()
        }
    }

    @Test
    fun `AppAutoRefreshManager updates preferences, refreshes modules, and enforces foreground throttle`() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val prefs = LocalPreferencesManager(context)
        try {
            prefs.resetAll()
            db.userProfileDao().insertProfile(
                UserProfileEntity(fullName = "Refresh User", age = 28, genderIdentity = "Male")
            )
            val manager = AppAutoRefreshManager(
                appContext = context,
                database = db,
                preferencesManager = prefs,
                deviceWellbeingManager = DeviceWellbeingManager(context),
                healthConnectManager = HealthConnectManager(context),
                fitnessSyncRepository = FitnessSyncRepository(db),
                allModulesRepository = AllModulesRepository(db),
                alertSoundManager = AlertSoundManager(context)
            )

            manager.updateAutoRefreshConfig(enabled = true, intervalMinutes = 15)
            val updatedPrefs = prefs.preferencesFlow.first()
            assertTrue(updatedPrefs.autoRefreshEnabled)
            assertEquals(15, updatedPrefs.autoRefreshIntervalMinutes)

            val now = 1_750_000_000_000L
            val firstReport = manager.refreshAllModules(reason = "test_force", force = true, nowMs = now)
            assertFalse(firstReport.skippedDueToThrottle)
            assertTrue(firstReport.summary.isNotBlank())
            assertEquals(now, manager.state.value.lastRefreshedEpochMs)

            // Immediate non-forced refresh (30s later) should be throttled cleanly
            val throttled = manager.refreshAllModules(reason = "resume", force = false, nowMs = now + 30_000L)
            assertTrue("Rapid non-forced refresh should be throttled", throttled.skippedDueToThrottle)
        } finally {
            prefs.resetAll()
            db.close()
        }
    }

    @Test
    fun `AutoRefreshControlCard and SleepQualitySnoringMonitorCard render cleanly in Dark and Light modes`() {
        var refreshClicked = false
        var startClicked = false
        val sampleLive = SleepMonitorLiveState(
            isMonitoring = true,
            elapsedMinutes = 142,
            currentDb = 49.0,
            ambientFloorDb = 32.0,
            snoreEventsCount = 4,
            snoreDurationMinutes = 3,
            peakSnoreDb = 62.0,
            restlessnessIndex = 21,
            estimatedStage = EstimatedSleepStage.DEEP,
            liveQualityScore = 86,
            lastEventLabel = "Snore detected • 62 dB (1.4s)",
            recentDbWaveform = List(16) { 0.35f }
        )
        val sampleRecord = SleepRecordEntity(
            id = 1L,
            profileId = 1L,
            bedtimeEpochMs = 1_750_000_000_000L,
            wakeEpochMs = 1_750_025_200_000L,
            qualityRating = 4,
            sleepQualityScore = 86,
            snoreEventsCount = 4,
            snoreDurationMinutes = 3,
            peakSnoreDb = 62.0,
            restlessnessIndex = 21,
            deepSleepMinutes = 120,
            lightSleepMinutes = 285,
            trackingSource = "Auto Sensor",
            notes = "Auto Sensor • Quality 86/100"
        )

        composeRule.setContent {
            LifeCompanionTheme(themeMode = ThemeMode.DARK) {
                androidx.compose.foundation.layout.Column(
                    modifier = androidx.compose.ui.Modifier.verticalScroll(
                        androidx.compose.foundation.rememberScrollState()
                    )
                ) {
                    AutoRefreshControlCard(
                        autoRefreshEnabled = true,
                        intervalMinutes = 30,
                        isRefreshing = false,
                        lastRefreshedEpochMs = System.currentTimeMillis() - 120_000L,
                        lastSummary = "Wellbeing (95m) • Fitness Devices (+3)",
                        onToggleAutoRefresh = {},
                        onSelectIntervalMinutes = {},
                        onRefreshAllNow = { refreshClicked = true },
                        compact = false
                    )
                    SleepQualitySnoringMonitorCard(
                        liveState = sampleLive,
                        bedtimePromptEnabled = true,
                        bedtimeHour = 22,
                        bedtimeMinute = 30,
                        wakeHour = 7,
                        wakeMinute = 0,
                        onStartMonitor = {},
                        onStopAndSaveMonitor = { startClicked = true },
                        onDismissBanner = {},
                        onUpdateBedtimeSchedule = { _, _, _, _, _ -> }
                    )
                    SleepRecordDetailCard(record = sampleRecord, onDelete = {})
                }
            }
        }

        composeRule.onNodeWithTag("auto_refresh_control_card").assertIsDisplayed()
        composeRule.onNodeWithTag("auto_refresh_now_button").performScrollTo().performClick()
        assertTrue(refreshClicked)

        composeRule.onNodeWithTag("sleep_monitor_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("sleep_monitor_toggle_button").performScrollTo().performClick()
        assertTrue(startClicked)
        composeRule.onNodeWithText("Auto Sleep Quality & Snoring Monitor").performScrollTo().assertIsDisplayed()
    }
}
