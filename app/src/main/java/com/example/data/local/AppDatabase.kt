package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.local.dao.BeautyCareDao
import com.example.data.local.dao.ConnectedFitnessDao
import com.example.data.local.dao.DietDao
import com.example.data.local.dao.DigitalWellbeingDao
import com.example.data.local.dao.FitnessDao
import com.example.data.local.dao.MedicalCareDao
import com.example.data.local.dao.ModuleActivationDao
import com.example.data.local.dao.PeriodDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.UserProfileDao
import com.example.data.local.dao.WaterDao
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
import com.example.data.local.entity.ModuleActivationEntity
import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [
        UserProfileEntity::class,
        WaterRecordEntity::class,
        PeriodRecordEntity::class,
        MedicineEntity::class,
        MedicineIntakeEntity::class,
        ExerciseRecordEntity::class,
        DietRecordEntity::class,
        SleepRecordEntity::class,
        StepRecordEntity::class,
        DoctorConsultationEntity::class,
        ReminderEntity::class,
        BeautyCareRecordEntity::class,
        DigitalWellbeingRecordEntity::class,
        AiPreferenceEntity::class,
        ModuleActivationEntity::class,
        ConnectedDeviceSourceEntity::class,
        HealthVitalMetricEntity::class
    ],
    version = 7,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userProfileDao(): UserProfileDao
    abstract fun waterDao(): WaterDao
    abstract fun periodDao(): PeriodDao
    abstract fun reminderDao(): ReminderDao
    abstract fun fitnessDao(): FitnessDao
    abstract fun dietDao(): DietDao
    abstract fun medicalCareDao(): MedicalCareDao
    abstract fun beautyCareDao(): BeautyCareDao
    abstract fun digitalWellbeingDao(): DigitalWellbeingDao
    abstract fun moduleActivationDao(): ModuleActivationDao
    abstract fun connectedFitnessDao(): ConnectedFitnessDao

    companion object {
        const val DATABASE_NAME = "life_companion_local.db"

        /**
         * Explicit non-destructive migration from version 1 to version 2 adding
         * per-alarm sound customization columns to the reminders table.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN soundKey TEXT NOT NULL DEFAULT 'water_drop'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN customSoundUri TEXT")
                db.execSQL("ALTER TABLE reminders ADD COLUMN customSoundLabel TEXT")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicines ADD COLUMN dosesPerDay INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE medicines ADD COLUMN intervalHours INTEGER NOT NULL DEFAULT 24")
                db.execSQL("ALTER TABLE medicines ADD COLUMN mealRelation TEXT NOT NULL DEFAULT 'After Meal'")
                db.execSQL("ALTER TABLE medicines ADD COLUMN categoryEmoji TEXT NOT NULL DEFAULT '💊'")
                db.execSQL("ALTER TABLE doctor_consultations ADD COLUMN clinicOrHospital TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE doctor_consultations ADD COLUMN followUpNotes TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE doctor_consultations ADD COLUMN isFollowUpCompleted INTEGER NOT NULL DEFAULT 0")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medicine_intakes (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        medicineId INTEGER NOT NULL,
                        medicineName TEXT NOT NULL,
                        dosageTaken TEXT NOT NULL,
                        slotLabel TEXT NOT NULL,
                        status TEXT NOT NULL,
                        dayKey TEXT NOT NULL,
                        takenAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(medicineId) REFERENCES medicines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medicine_intakes_profileId ON medicine_intakes(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medicine_intakes_medicineId ON medicine_intakes(medicineId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medicine_intakes_dayKey ON medicine_intakes(dayKey)")
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS connected_fitness_sources (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        sourceId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        emoji TEXT NOT NULL,
                        syncChannel TEXT NOT NULL,
                        lastSyncedEpochMs INTEGER NOT NULL,
                        recordsImportedCount INTEGER NOT NULL,
                        latestSummary TEXT NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_connected_fitness_sources_profileId ON connected_fitness_sources(profileId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_connected_fitness_sources_profileId_sourceId ON connected_fitness_sources(profileId, sourceId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS health_vital_metrics (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        metricType TEXT NOT NULL,
                        valuePrimary REAL NOT NULL,
                        unit TEXT NOT NULL,
                        sourceId TEXT NOT NULL,
                        sourceDisplayName TEXT NOT NULL,
                        dayKey TEXT NOT NULL,
                        recordedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_vital_metrics_profileId ON health_vital_metrics(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_vital_metrics_dayKey ON health_vital_metrics(dayKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_vital_metrics_metricType ON health_vital_metrics(metricType)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE digital_wellbeing_records ADD COLUMN unlockCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE digital_wellbeing_records ADD COLUMN appLaunchCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE digital_wellbeing_records ADD COLUMN nightScreenMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE digital_wellbeing_records ADD COLUMN longestSessionMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE digital_wellbeing_records ADD COLUMN topAppName TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE digital_wellbeing_records ADD COLUMN topAppMinutes INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE digital_wellbeing_records ADD COLUMN topAppsJson TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE digital_wellbeing_records ADD COLUMN sourceLabel TEXT NOT NULL DEFAULT 'Manual'")
                db.execSQL("ALTER TABLE digital_wellbeing_records ADD COLUMN lastSyncedEpochMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN alarmEmoji TEXT NOT NULL DEFAULT '⏰'")
                db.execSQL("ALTER TABLE reminders ADD COLUMN customMessage TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE reminders ADD COLUMN gesturePreset TEXT NOT NULL DEFAULT 'SWIPE_SLIDE'")
            }
        }

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE reminders ADD COLUMN repeatIntervalMinutes INTEGER NOT NULL DEFAULT 1440")
                db.execSQL("ALTER TABLE reminders ADD COLUMN triggeredCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN completedCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN snoozedCount INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE reminders ADD COLUMN lastTriggeredEpochMs INTEGER NOT NULL DEFAULT 0")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7
        )

        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    DATABASE_NAME
                )
                    .addMigrations(*ALL_MIGRATIONS)
                    .addCallback(object : Callback() {
                        override fun onOpen(db: SupportSQLiteDatabase) {
                            super.onOpen(db)
                            db.execSQL("PRAGMA foreign_keys = ON;")
                        }

                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            CoroutineScope(Dispatchers.IO).launch {
                                INSTANCE?.moduleActivationDao()
                                    ?.insertDefaultsIfMissing(defaultModuleCatalog())
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }

        fun defaultModuleCatalog(): List<ModuleActivationEntity> = listOf(
            ModuleActivationEntity(
                moduleKey = "water_tracker",
                displayName = "Water & Hydration",
                description = "Daily hydration vessel, smart target, and 7-day trends",
                category = "Daily Essentials",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "period_tracker",
                displayName = "Cycle & Period Care",
                description = "Inclusive menstrual logging, flow, mood, and predictions",
                category = "Health & Body",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "exercise_tracker",
                displayName = "Exercise & Movement",
                description = "Workouts, step trends, and active minute goals",
                category = "Fitness (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "yoga_tracker",
                displayName = "Yoga & Breathwork",
                description = "Mindful movement flows and restorative sessions",
                category = "Fitness (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "diet_tracker",
                displayName = "Diet & Nutrition",
                description = "Balanced meal logging and local macro awareness",
                category = "Nutrition (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "daily_reminders",
                displayName = "Daily Reminders",
                description = "Local native notifications for routines and hydration",
                category = "Routines (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "medical_care",
                displayName = "Health, Medicines & Doctors",
                description = "Daily doses, intervals, intake logs & doctor follow-ups",
                category = "Health & Medical (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "beauty_care",
                displayName = "Beauty & Personal Care",
                description = "Skincare, haircare, and self-care ritual logs",
                category = "Self-Care (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "digital_wellbeing",
                displayName = "Digital Wellbeing",
                description = "Focus blocks, mindful screen goals, and rest windows",
                category = "Digital Balance (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "reports_analytics",
                displayName = "Reports & Analytics",
                description = "Cross-module wellness streaks, hydration, and activity trends",
                category = "Insights (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "on_device_ai",
                displayName = "On-Device AI Companion",
                description = "Open-source local wellness agent with zero cloud inference",
                category = "On-Device AI (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "connected_devices",
                displayName = "Connected Apps & Devices",
                description = "Sync Fitbit, Google Fit, Samsung Health, Garmin & Apple Health exports",
                category = "Wearables & Sync (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            )
        )
    }
}
