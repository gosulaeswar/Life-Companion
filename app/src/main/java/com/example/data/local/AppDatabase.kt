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
import com.example.data.local.dao.HealthVaultDao
import com.example.data.local.dao.MedicalCareDao
import com.example.data.local.dao.ModuleActivationDao
import com.example.data.local.dao.NutritionDao
import com.example.data.local.dao.PeriodDao
import com.example.data.local.dao.ReminderDao
import com.example.data.local.dao.SubscriptionBillingDao
import com.example.data.local.dao.UserProfileDao
import com.example.data.local.dao.WaterDao
import com.example.data.local.dao.WellnessEngagementDao
import com.example.data.local.entity.AchievementBadgeEntity
import com.example.data.local.entity.AiPreferenceEntity
import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.BeautyCareRecordEntity
import com.example.data.local.entity.ConfigurablePricingPlanEntity
import com.example.data.local.entity.ConnectedDeviceSourceEntity
import com.example.data.local.entity.DailyRoutineEntity
import com.example.data.local.entity.DietRecordEntity
import com.example.data.local.entity.DigitalWellbeingRecordEntity
import com.example.data.local.entity.DoctorConsultationEntity
import com.example.data.local.entity.ExerciseRecordEntity
import com.example.data.local.entity.HealthDocumentEntity
import com.example.data.local.entity.HealthVitalMetricEntity
import com.example.data.local.entity.LabResultEntity
import com.example.data.local.entity.MedicalHistoryEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.MedicineIntakeEntity
import com.example.data.local.entity.MedicineRefillEntity
import com.example.data.local.entity.ModuleActivationEntity
import com.example.data.local.entity.MoodJournalEntryEntity
import com.example.data.local.entity.NutritionPlanEntity
import com.example.data.local.entity.NutritionPreferenceEntity
import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.RoutineCompletionEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.data.local.entity.SubscriptionEntitlementEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.data.local.entity.WellnessChallengeEntity
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
        HealthVitalMetricEntity::class,
        HealthDocumentEntity::class,
        LabResultEntity::class,
        AllergyRecordEntity::class,
        MedicalHistoryEntity::class,
        MedicineRefillEntity::class,
        NutritionPreferenceEntity::class,
        NutritionPlanEntity::class,
        DailyRoutineEntity::class,
        RoutineCompletionEntity::class,
        MoodJournalEntryEntity::class,
        WellnessChallengeEntity::class,
        AchievementBadgeEntity::class,
        SubscriptionEntitlementEntity::class,
        ConfigurablePricingPlanEntity::class
    ],
    version = 13,
    exportSchema = true
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
    abstract fun healthVaultDao(): HealthVaultDao
    abstract fun nutritionDao(): NutritionDao
    abstract fun wellnessEngagementDao(): WellnessEngagementDao
    abstract fun subscriptionBillingDao(): SubscriptionBillingDao

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

        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE medicines ADD COLUMN stockQuantity REAL NOT NULL DEFAULT 0.0")
                db.execSQL("ALTER TABLE medicines ADD COLUMN stockUnit TEXT NOT NULL DEFAULT 'tablets'")
                db.execSQL("ALTER TABLE medicines ADD COLUMN unitsPerDose REAL NOT NULL DEFAULT 1.0")
                db.execSQL("ALTER TABLE medicines ADD COLUMN lowStockThresholdDays INTEGER NOT NULL DEFAULT 5")
                db.execSQL("ALTER TABLE medicines ADD COLUMN expiryEpochDay INTEGER")
                db.execSQL("ALTER TABLE medicines ADD COLUMN linkedPrescriptionDocId INTEGER")
                db.execSQL("ALTER TABLE medicines ADD COLUMN refillReminderEnabled INTEGER NOT NULL DEFAULT 1")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS health_documents (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        category TEXT NOT NULL,
                        documentEpochDay INTEGER NOT NULL,
                        providerOrLabName TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        mimeType TEXT NOT NULL,
                        originalFileName TEXT NOT NULL,
                        encryptedFilePath TEXT NOT NULL,
                        fileSizeBytes INTEGER NOT NULL,
                        aiExplanation TEXT NOT NULL,
                        aiExplainedAtEpochMs INTEGER,
                        linkedConsultationId INTEGER,
                        createdAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_documents_profileId ON health_documents(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_documents_category ON health_documents(category)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_health_documents_documentEpochDay ON health_documents(documentEpochDay)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS lab_results (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        testName TEXT NOT NULL,
                        valueNumeric REAL,
                        valueText TEXT NOT NULL,
                        unit TEXT NOT NULL,
                        referenceRangeText TEXT NOT NULL,
                        statusFlag TEXT NOT NULL,
                        recordedEpochDay INTEGER NOT NULL,
                        labName TEXT NOT NULL,
                        documentId INTEGER,
                        notes TEXT NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_results_profileId ON lab_results(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_results_testName ON lab_results(testName)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_lab_results_recordedEpochDay ON lab_results(recordedEpochDay)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS allergy_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        allergen TEXT NOT NULL,
                        reaction TEXT NOT NULL,
                        severity TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        recordedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_allergy_records_profileId ON allergy_records(profileId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medical_history_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        conditionOrEvent TEXT NOT NULL,
                        category TEXT NOT NULL,
                        diagnosedYearOrDate TEXT NOT NULL,
                        status TEXT NOT NULL,
                        notes TEXT NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medical_history_entries_profileId ON medical_history_entries(profileId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS medicine_refill_logs (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        medicineId INTEGER NOT NULL,
                        medicineName TEXT NOT NULL,
                        quantityAdded REAL NOT NULL,
                        unitLabel TEXT NOT NULL,
                        purchaseDateEpochDay INTEGER NOT NULL,
                        expiryEpochDay INTEGER,
                        pharmacyOrSource TEXT NOT NULL,
                        costNote TEXT NOT NULL,
                        prescriptionDocId INTEGER,
                        loggedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(medicineId) REFERENCES medicines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medicine_refill_logs_profileId ON medicine_refill_logs(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_medicine_refill_logs_medicineId ON medicine_refill_logs(medicineId)")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nutrition_preferences (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        preferredRegion TEXT NOT NULL,
                        dietaryPreference TEXT NOT NULL,
                        nutritionGoal TEXT NOT NULL,
                        customCalorieTarget INTEGER,
                        avoidVaultAllergens INTEGER NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_nutrition_preferences_profileId ON nutrition_preferences(profileId)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS nutrition_plans (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        planTitle TEXT NOT NULL,
                        regionLabel TEXT NOT NULL,
                        dietaryLabel TEXT NOT NULL,
                        goalLabel TEXT NOT NULL,
                        regionalNotes TEXT NOT NULL,
                        totalCalories INTEGER NOT NULL,
                        totalProteinGrams INTEGER NOT NULL,
                        totalCarbsGrams INTEGER NOT NULL,
                        totalFatGrams INTEGER NOT NULL,
                        mealsJson TEXT NOT NULL,
                        groceryHighlightsJson TEXT NOT NULL,
                        avoidedAllergensCsv TEXT NOT NULL,
                        isFromCloudAi INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nutrition_plans_profileId ON nutrition_plans(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_nutrition_plans_createdAtEpochMs ON nutrition_plans(createdAtEpochMs)")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN relationLabel TEXT NOT NULL DEFAULT 'Self'")
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN linkedFamilyId TEXT")
                db.execSQL("ALTER TABLE user_profiles ADD COLUMN linkedSlotId TEXT")
            }
        }

        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS daily_routines (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        title TEXT NOT NULL,
                        emoji TEXT NOT NULL,
                        timeSlot TEXT NOT NULL,
                        triggerTime24h TEXT NOT NULL,
                        estimatedMinutes INTEGER NOT NULL,
                        stepsSerialized TEXT NOT NULL,
                        linkedModuleKey TEXT NOT NULL,
                        reminderId INTEGER,
                        reminderEnabled INTEGER NOT NULL,
                        isOptional INTEGER NOT NULL,
                        isActive INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        createdAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_routines_profileId ON daily_routines(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_daily_routines_timeSlot ON daily_routines(timeSlot)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS routine_completions (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        routineId INTEGER NOT NULL,
                        dayKey TEXT NOT NULL,
                        completedStepsMask INTEGER NOT NULL,
                        completedStepsCount INTEGER NOT NULL,
                        totalStepsCount INTEGER NOT NULL,
                        reflectionNote TEXT NOT NULL,
                        completedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE,
                        FOREIGN KEY(routineId) REFERENCES daily_routines(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_completions_profileId ON routine_completions(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_completions_routineId ON routine_completions(routineId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_routine_completions_dayKey ON routine_completions(dayKey)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_routine_completions_profileId_routineId_dayKey ON routine_completions(profileId, routineId, dayKey)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS mood_journal_entries (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        dayKey TEXT NOT NULL,
                        moodLabel TEXT NOT NULL,
                        moodEmoji TEXT NOT NULL,
                        moodScore INTEGER NOT NULL,
                        energyLevel INTEGER NOT NULL,
                        stressLevel INTEGER NOT NULL,
                        tagsCsv TEXT NOT NULL,
                        reflectionNote TEXT NOT NULL,
                        gratitudeText TEXT NOT NULL,
                        loggedAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_journal_entries_profileId ON mood_journal_entries(profileId)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_journal_entries_dayKey ON mood_journal_entries(dayKey)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_mood_journal_entries_loggedAtEpochMs ON mood_journal_entries(loggedAtEpochMs)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS wellness_challenges (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        challengeKey TEXT NOT NULL,
                        title TEXT NOT NULL,
                        emoji TEXT NOT NULL,
                        category TEXT NOT NULL,
                        description TEXT NOT NULL,
                        targetDays INTEGER NOT NULL,
                        currentProgressDays INTEGER NOT NULL,
                        status TEXT NOT NULL,
                        isOptInOnly INTEGER NOT NULL,
                        startDayKey TEXT NOT NULL,
                        lastCheckInDayKey TEXT NOT NULL,
                        completedAtEpochMs INTEGER,
                        createdAtEpochMs INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_wellness_challenges_profileId ON wellness_challenges(profileId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_wellness_challenges_profileId_challengeKey ON wellness_challenges(profileId, challengeKey)")

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS achievement_badges (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        badgeKey TEXT NOT NULL,
                        title TEXT NOT NULL,
                        emoji TEXT NOT NULL,
                        description TEXT NOT NULL,
                        category TEXT NOT NULL,
                        earnedAtEpochMs INTEGER,
                        progressCurrent INTEGER NOT NULL,
                        progressTarget INTEGER NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_achievement_badges_profileId ON achievement_badges(profileId)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_achievement_badges_profileId_badgeKey ON achievement_badges(profileId, badgeKey)")
            }
        }

        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS subscription_entitlements (
                        entitlementId TEXT NOT NULL,
                        tierId TEXT NOT NULL,
                        productId TEXT NOT NULL,
                        basePlanId TEXT NOT NULL,
                        offerId TEXT NOT NULL,
                        purchaseToken TEXT NOT NULL,
                        orderId TEXT NOT NULL,
                        purchaseState TEXT NOT NULL,
                        lifecycleStatus TEXT NOT NULL,
                        verificationStatus TEXT NOT NULL,
                        verificationReceiptId TEXT NOT NULL,
                        verificationMessage TEXT NOT NULL,
                        autoRenewing INTEGER NOT NULL,
                        acknowledged INTEGER NOT NULL,
                        purchasedAtEpochMs INTEGER NOT NULL,
                        expiresAtEpochMs INTEGER NOT NULL,
                        gracePeriodEndsAtEpochMs INTEGER NOT NULL,
                        lastValidatedEpochMs INTEGER NOT NULL,
                        linkedFirebaseUid TEXT NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(entitlementId)
                    )
                    """.trimIndent()
                )

                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS configurable_pricing_plans (
                        planKey TEXT NOT NULL,
                        tierId TEXT NOT NULL,
                        playProductId TEXT NOT NULL,
                        basePlanId TEXT NOT NULL,
                        displayName TEXT NOT NULL,
                        billingPeriodLabel TEXT NOT NULL,
                        priceText TEXT NOT NULL,
                        currencyCode TEXT NOT NULL,
                        priceMicros INTEGER NOT NULL,
                        badgeText TEXT NOT NULL,
                        maxProfiles INTEGER NOT NULL,
                        featuresCsv TEXT NOT NULL,
                        isEnabled INTEGER NOT NULL,
                        sortOrder INTEGER NOT NULL,
                        configSource TEXT NOT NULL,
                        updatedAtEpochMs INTEGER NOT NULL,
                        PRIMARY KEY(planKey)
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS sleep_records (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        profileId INTEGER NOT NULL,
                        bedtimeEpochMs INTEGER NOT NULL,
                        wakeEpochMs INTEGER NOT NULL,
                        qualityRating INTEGER NOT NULL,
                        notes TEXT NOT NULL,
                        FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sleep_records_profileId ON sleep_records(profileId)")

                val existingColumns = mutableSetOf<String>()
                val cursor = db.query("PRAGMA table_info(sleep_records)")
                try {
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0) {
                            existingColumns += cursor.getString(nameIdx)
                        }
                    }
                } finally {
                    cursor.close()
                }

                fun addColIfMissing(colName: String, definition: String) {
                    if (colName !in existingColumns) {
                        db.execSQL("ALTER TABLE sleep_records ADD COLUMN $colName $definition")
                    }
                }

                addColIfMissing("sleepQualityScore", "INTEGER NOT NULL DEFAULT 0")
                addColIfMissing("snoreEventsCount", "INTEGER NOT NULL DEFAULT 0")
                addColIfMissing("snoreDurationMinutes", "INTEGER NOT NULL DEFAULT 0")
                addColIfMissing("peakSnoreDb", "REAL NOT NULL DEFAULT 0.0")
                addColIfMissing("avgAmbientDb", "REAL NOT NULL DEFAULT 0.0")
                addColIfMissing("restlessnessIndex", "INTEGER NOT NULL DEFAULT 0")
                addColIfMissing("awakeInterruptionsCount", "INTEGER NOT NULL DEFAULT 0")
                addColIfMissing("deepSleepMinutes", "INTEGER NOT NULL DEFAULT 0")
                addColIfMissing("lightSleepMinutes", "INTEGER NOT NULL DEFAULT 0")
                addColIfMissing("trackingSource", "TEXT NOT NULL DEFAULT 'Manual'")
            }
        }

        val ALL_MIGRATIONS: Array<Migration> = arrayOf(
            MIGRATION_1_2,
            MIGRATION_2_3,
            MIGRATION_3_4,
            MIGRATION_4_5,
            MIGRATION_5_6,
            MIGRATION_6_7,
            MIGRATION_7_8,
            MIGRATION_8_9,
            MIGRATION_9_10,
            MIGRATION_10_11,
            MIGRATION_11_12,
            MIGRATION_12_13
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
                description = "Regional Indian food catalog, AI meal planner & Health Connect sync",
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
            ),
            ModuleActivationEntity(
                moduleKey = "health_vault",
                displayName = "Health Records Vault",
                description = "Encrypted local storage for prescriptions, labs, allergies & Doctor PDF",
                category = "Health & Medical (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "daily_routines",
                displayName = "Smart Daily Routines",
                description = "Customizable morning, midday & evening routines with gentle reminders",
                category = "Routines & Growth (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            ),
            ModuleActivationEntity(
                moduleKey = "mood_journal",
                displayName = "Mood Journal & Challenges",
                description = "Reflective mood notes, optional wellness challenges & non-punitive badges",
                category = "Mindfulness & Mood (Free)",
                isAlwaysFree = true,
                isBuiltInPhaseOne = true,
                isActivated = true
            )
        )
    }
}
