package com.example

import android.content.Context
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.units.Energy
import androidx.health.connect.client.units.Mass
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.ai.LifeWeaveAiService
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.DietRecordEntity
import com.example.data.local.entity.NutritionPreferenceEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.PrivacyBackupRepository
import com.example.domain.DateEpochUtils
import com.example.domain.IndianDietaryPreference
import com.example.domain.IndianFoodCatalog
import com.example.domain.IndianFoodTag
import com.example.domain.IndianNutritionEngine
import com.example.domain.IndianRegion
import com.example.domain.NutritionGoalType
import com.example.healthsync.FitnessSyncRepository
import com.example.healthsync.HealthConnectDataCategory
import com.example.healthsync.HealthConnectManager
import com.example.healthsync.HealthSyncPayload
import com.example.healthsync.ImportedNutrition
import com.example.healthsync.ImportedSourceStat
import com.example.healthsync.ImportedWater
import com.example.security.EncryptedDocumentVault
import com.example.security.HealthConnectImportAuthorization
import com.example.security.KeystoreVaultManager
import com.example.security.LocalPreferencesManager
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
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.ZoneOffset
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase4NutritionAndHealthConnectTest {

    private fun testAesKey() = KeyGenerator.getInstance("AES").apply {
        init(256)
    }.generateKey()

    @Test
    fun `exported Room schema 9 json and non-destructive MIGRATION_8_9 preserve existing v8 records and create nutrition tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val schemaDir = listOf(
            File("schemas/com.example.data.local.AppDatabase"),
            File("app/schemas/com.example.data.local.AppDatabase")
        ).firstOrNull { it.exists() && it.isDirectory }
        val schemaFile = schemaDir?.let { dir ->
            File(dir, "9.json").takeIf { it.exists() }
                ?: dir.listFiles { f -> f.extension == "json" }
                    ?.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: 0 }
        }
        assertNotNull("Expected exported Room schema (>= 9.json) to exist", schemaFile)
        val schemaJson = JSONObject(schemaFile!!.readText()).getJSONObject("database")
        val exportedVersion = schemaJson.getInt("version")
        val entities = schemaJson.getJSONArray("entities")
        assertTrue("Expected exported schema version >= 9, got $exportedVersion", exportedVersion >= 9)
        assertTrue("Expected at least 24 entities in exported schema", entities.length() >= 24)
        val tableNames = (0 until entities.length())
            .map { entities.getJSONObject(it).getString("tableName") }
            .toSet()
        assertTrue(tableNames.containsAll(listOf("nutrition_preferences", "nutrition_plans")))

        val dbName = "migration_8_to_9_test.db"
        context.deleteDatabase(dbName)

        // Create real v8 SQLite DB with existing user_profiles, diet_records, water_records
        val v8Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
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
                            CREATE TABLE diet_records (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                profileId INTEGER NOT NULL,
                                mealType TEXT NOT NULL,
                                foodSummary TEXT NOT NULL,
                                calories INTEGER NOT NULL,
                                proteinGrams INTEGER NOT NULL,
                                carbsGrams INTEGER NOT NULL,
                                fatGrams INTEGER NOT NULL,
                                loggedAtEpochMs INTEGER NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO user_profiles VALUES (1, 'Kavya', 29, 'Female', '', 162, 60.0, 'Moderate', 2400, 1, 28, 5, 'en', 1000, 1000)
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO diet_records VALUES (1, 1, 'Breakfast', 'Idli & Sambar', 310, 11, 56, 4, 2000)
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        v8Helper.writableDatabase.close()

        val upgradedDb = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        AppDatabase.MIGRATION_8_9.migrate(db)
                    }
                })
                .build()
        ).writableDatabase

        try {
            val dietCursor = upgradedDb.query("SELECT mealType, foodSummary, calories FROM diet_records WHERE id = 1")
            assertTrue(dietCursor.moveToFirst())
            assertEquals("Breakfast", dietCursor.getString(0))
            assertEquals("Idli & Sambar", dietCursor.getString(1))
            assertEquals(310, dietCursor.getInt(2))
            dietCursor.close()

            val tablesCursor = upgradedDb.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('nutrition_preferences','nutrition_plans')"
            )
            val createdTables = mutableSetOf<String>()
            while (tablesCursor.moveToNext()) {
                createdTables += tablesCursor.getString(0)
            }
            tablesCursor.close()
            assertEquals(setOf("nutrition_preferences", "nutrition_plans"), createdTables)
        } finally {
            upgradedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `IndianFoodCatalog and IndianNutritionEngine calculate targets, dietary filters, Vault allergy exclusions, and macro progress accurately`() {
        assertTrue(IndianFoodCatalog.foods.size >= 40)
        assertTrue(IndianFoodCatalog.foods.any { it.region == IndianRegion.NORTH })
        assertTrue(IndianFoodCatalog.foods.any { it.region == IndianRegion.SOUTH })
        assertTrue(IndianFoodCatalog.foods.any { it.region == IndianRegion.WEST })
        assertTrue(IndianFoodCatalog.foods.any { it.region == IndianRegion.EAST })
        assertTrue(IndianFoodCatalog.foods.any { it.region == IndianRegion.PAN_INDIAN })

        val profile = UserProfileEntity(
            id = 1L,
            fullName = "Rohan",
            age = 30,
            genderIdentity = "Male",
            heightCm = 176,
            weightKg = 74f,
            activityLevel = "Moderate"
        )
        val balanced = IndianNutritionEngine.calculateTargets(
            profile = profile,
            goal = NutritionGoalType.BALANCED,
            dietaryPreference = IndianDietaryPreference.VEGETARIAN
        )
        val fatLoss = IndianNutritionEngine.calculateTargets(
            profile = profile,
            goal = NutritionGoalType.WEIGHT_LOSS,
            dietaryPreference = IndianDietaryPreference.VEGETARIAN
        )
        val highProtein = IndianNutritionEngine.calculateTargets(
            profile = profile,
            goal = NutritionGoalType.MUSCLE_PROTEIN,
            dietaryPreference = IndianDietaryPreference.EGGETARIAN
        )
        assertTrue(balanced.bmrCalories in 1600..1850)
        assertTrue(fatLoss.targetCalories < balanced.targetCalories)
        assertTrue(highProtein.targetProteinGrams > balanced.targetProteinGrams)

        // Dietary matching
        val jainItems = IndianNutritionEngine.filterFoods(
            region = IndianRegion.ALL,
            dietaryPreference = IndianDietaryPreference.JAIN
        )
        assertTrue(jainItems.isNotEmpty())
        assertTrue(jainItems.all { IndianFoodTag.JAIN_FRIENDLY in it.tags })

        val veganItems = IndianNutritionEngine.filterFoods(
            region = IndianRegion.ALL,
            dietaryPreference = IndianDietaryPreference.VEGAN
        )
        assertTrue(veganItems.all { IndianFoodTag.VEGAN in it.tags && !it.commonAllergens.contains("Dairy") })

        // Vault allergy exclusion (Peanut + Dairy)
        val vaultAllergies = listOf(
            AllergyRecordEntity(profileId = 1L, allergen = "Peanut", reaction = "Swelling", severity = "Severe"),
            AllergyRecordEntity(profileId = 1L, allergen = "Dairy", reaction = "Intolerance", severity = "Mild")
        )
        val safeFoods = IndianNutritionEngine.filterFoods(
            region = IndianRegion.ALL,
            dietaryPreference = IndianDietaryPreference.VEGETARIAN,
            allergies = vaultAllergies
        )
        assertTrue(safeFoods.isNotEmpty())
        assertTrue(safeFoods.none { IndianNutritionEngine.findAllergenMatches(it, vaultAllergies).isNotEmpty() })

        // Portion scaling
        val dosa = IndianFoodCatalog.foods.first { it.id == "south_pesarattu" }
        val scaledHalf = IndianNutritionEngine.scaleFood(dosa, 0.5f)
        val scaledOneHalf = IndianNutritionEngine.scaleFood(dosa, 1.5f)
        assertEquals((dosa.calories * 0.5f).toInt(), scaledHalf.calories)
        assertEquals((dosa.calories * 1.5f).toInt(), scaledOneHalf.calories)
        assertTrue(scaledOneHalf.formattedName.contains("1.5×"))

        // Manual vs Health Connect today intake breakdown
        val now = System.currentTimeMillis()
        val progress = IndianNutritionEngine.evaluateTodayIntake(
            dietRecords = listOf(
                DietRecordEntity(
                    profileId = 1L,
                    mealType = "Breakfast",
                    foodSummary = "🇮🇳 Poha",
                    calories = 300,
                    proteinGrams = 8,
                    carbsGrams = 50,
                    fatGrams = 7,
                    loggedAtEpochMs = now
                ),
                DietRecordEntity(
                    profileId = 1L,
                    mealType = "Lunch",
                    foodSummary = "⌚ Grilled Paneer Bowl [Health Connect • Fitbit]",
                    calories = 500,
                    proteinGrams = 28,
                    carbsGrams = 40,
                    fatGrams = 20,
                    loggedAtEpochMs = now
                )
            ),
            target = balanced
        )
        assertEquals(800, progress.consumedCalories)
        assertEquals(36, progress.consumedProteinGrams)
        assertEquals(2, progress.todayMealCount)
        assertEquals(1, progress.manualMealCount)
        assertEquals(1, progress.syncedMealCount)
        assertEquals(balanced.targetCalories - 800, progress.remainingCalories)
    }

    @Test
    fun `AI Indian nutrition JSON parser, offline meal plan fallback, and 24-table backup round-trip cleanly`() = runTest {
        val sampleJson = """
            {
              "planTitle": "South Indian High-Protein Day",
              "regionalNotes": "Balanced idli, sambar, and millet plates.",
              "meals": [
                {"slotLabel": "Breakfast", "mealType": "Breakfast", "dishName": "Moong Dal Pesarattu", "portionLabel": "2 medium dosas", "calories": 320, "proteinGrams": 16, "carbsGrams": 44, "fatGrams": 8, "prepTip": "Serve with ginger chutney"},
                {"slotLabel": "Mid-Morning", "mealType": "Mid-Morning", "dishName": "Roasted Makhana", "portionLabel": "1 bowl", "calories": 150, "proteinGrams": 5, "carbsGrams": 22, "fatGrams": 4, "prepTip": "Dry roast with pepper"},
                {"slotLabel": "Lunch", "mealType": "Lunch", "dishName": "Ragi Mudde & Keerai Sambar", "portionLabel": "1 millet ball + katori", "calories": 390, "proteinGrams": 14, "carbsGrams": 62, "fatGrams": 8, "prepTip": "Rich in iron and calcium"},
                {"slotLabel": "Evening Snack", "mealType": "Snack", "dishName": "Sprouted Moong Sundal", "portionLabel": "1 katori", "calories": 190, "proteinGrams": 12, "carbsGrams": 26, "fatGrams": 3, "prepTip": "Top with lemon"},
                {"slotLabel": "Dinner", "mealType": "Dinner", "dishName": "Quinoa Vegetable Pongal", "portionLabel": "1 medium bowl", "calories": 360, "proteinGrams": 14, "carbsGrams": 52, "fatGrams": 9, "prepTip": "Light on digestion"}
              ],
              "groceryHighlights": ["Green moong", "Ragi flour", "Spinach", "Curry leaves"],
              "disclaimer": "Informational wellness guidance only."
            }
        """.trimIndent()

        val parsed = LifeWeaveAiService.parseIndianNutritionPlanJson(
            jsonString = sampleJson,
            regionLabel = "South Indian",
            dietaryLabel = "Vegetarian",
            goalLabel = "High Protein & Strength",
            avoidedAllergens = listOf("Peanut")
        )
        assertTrue(parsed.isFromCloudAi)
        assertEquals(5, parsed.meals.size)
        assertEquals("Snack", parsed.meals[1].mealType) // Mid-Morning normalized to Snack
        assertEquals(1410, parsed.totalCalories)
        assertEquals(61, parsed.totalProteinGrams)
        assertEquals(listOf("Peanut"), parsed.avoidedAllergens)

        // Offline fallback honors region, dietary preference, and Vault allergies
        val target = IndianNutritionEngine.calculateTargets(null, NutritionGoalType.SUGAR_BALANCE, IndianDietaryPreference.VEGAN)
        val offline = IndianNutritionEngine.generateOfflineIndianMealPlan(
            region = IndianRegion.NORTH,
            dietaryPreference = IndianDietaryPreference.VEGAN,
            target = target,
            allergies = listOf(AllergyRecordEntity(profileId = 1L, allergen = "Peanut", reaction = "Hives"))
        )
        assertFalse(offline.isFromCloudAi)
        assertEquals(5, offline.meals.size)
        assertTrue(offline.avoidedAllergens.contains("Peanut"))
        assertEquals(IndianNutritionEngine.STANDARD_NUTRITION_DISCLAIMER, offline.disclaimer)

        // 24-table Persistence & Backup Round-Trip
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsManager = LocalPreferencesManager(context)
        prefsManager.resetAll()
        val vaultManager = KeystoreVaultManager { testAesKey() }
        val docVault = EncryptedDocumentVault(context, vaultManager)
        docVault.wipeAllVaultFiles()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val allModulesRepo = AllModulesRepository(db)
            val backupRepo = PrivacyBackupRepository(db, prefsManager, vaultManager, docVault)
            val pid = db.userProfileDao().insertProfile(UserProfileEntity(fullName = "Ananya", age = 27, genderIdentity = "Female"))

            allModulesRepo.saveNutritionPreference(
                NutritionPreferenceEntity(
                    profileId = pid,
                    preferredRegion = IndianRegion.EAST.name,
                    dietaryPreference = IndianDietaryPreference.VEGETARIAN.name,
                    nutritionGoal = NutritionGoalType.IRON_PCOS_SUPPORT.name,
                    customCalorieTarget = 1950,
                    avoidVaultAllergens = true
                )
            )
            allModulesRepo.saveNutritionPlan(pid, parsed)

            val savedPref = allModulesRepo.observeNutritionPreference(pid).first()
            assertEquals("EAST", savedPref?.preferredRegion)
            assertEquals(1950, savedPref?.customCalorieTarget)

            val exported = backupRepo.buildExportJson()
            assertNotNull(JSONObject(exported).optJSONObject("nutritionPreference"))
            val preview = backupRepo.parseBackupPreview(exported).getOrThrow()
            assertEquals(1, preview.totalRecords) // 1 nutrition plan array record

            backupRepo.deleteAllLocalData()
            assertTrue(db.userProfileDao().getActiveProfileSnapshot() == null)

            backupRepo.restoreFromRawPayload(exported).getOrThrow()
            val restoredProfile = db.userProfileDao().getActiveProfileSnapshot()!!
            val restoredPref = allModulesRepo.observeNutritionPreference(restoredProfile.id).first()!!
            val restoredPlanEntity = allModulesRepo.observeLatestNutritionPlan(restoredProfile.id).first()!!
            val restoredPlan = AllModulesRepository.entityToMealPlanResult(restoredPlanEntity)

            assertEquals("EAST", restoredPref.preferredRegion)
            assertEquals(1950, restoredPref.customCalorieTarget)
            assertEquals(parsed.planTitle, restoredPlan.planTitle)
            assertEquals(5, restoredPlan.meals.size)
            assertTrue(restoredPlan.isFromCloudAi)
        } finally {
            docVault.wipeAllVaultFiles()
            db.close()
        }
    }

    @Test
    fun `Health Connect category permissions, NutritionRecord mapping, and idempotent sync preserve manual Diet and Water records`() = runTest {
        // Explicit permission check
        assertTrue(HealthConnectManager.PERMISSION_NUTRITION in HealthConnectManager.REQUIRED_READ_PERMISSIONS)
        val selectivePerms = HealthConnectManager.permissionsForCategories(
            setOf(HealthConnectDataCategory.NUTRITION, HealthConnectDataCategory.HYDRATION)
        )
        assertTrue(HealthConnectManager.PERMISSION_NUTRITION in selectivePerms)
        assertTrue(HealthConnectManager.PERMISSION_HYDRATION in selectivePerms)
        assertFalse(HealthConnectManager.PERMISSION_SLEEP in selectivePerms)
        assertFalse(HealthConnectManager.PERMISSION_STEPS in selectivePerms)

        // DataStore per-category import toggles
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsManager = LocalPreferencesManager(context)
        prefsManager.resetAll()
        prefsManager.setHealthConnectCategoryEnabled(HealthConnectDataCategory.NUTRITION, false)
        val auth = prefsManager.preferencesFlow.first().healthConnectImportAuth
        assertFalse(auth.isCategoryEnabled(HealthConnectDataCategory.NUTRITION))
        assertTrue(auth.isCategoryEnabled(HealthConnectDataCategory.HYDRATION))

        // Map real NutritionRecord -> ImportedNutrition
        val start = Instant.parse("2026-05-10T07:30:00Z")
        val end = Instant.parse("2026-05-10T08:00:00Z")
        val record = NutritionRecord(
            startTime = start,
            startZoneOffset = ZoneOffset.UTC,
            endTime = end,
            endZoneOffset = ZoneOffset.UTC,
            metadata = Metadata.manualEntry(),
            energy = Energy.kilocalories(480.0),
            protein = Mass.grams(22.0),
            totalCarbohydrate = Mass.grams(58.0),
            totalFat = Mass.grams(14.0),
            name = "Rajma Chawal Bowl",
            mealType = MealType.MEAL_TYPE_LUNCH
        )
        val mapped = HealthConnectManager.mapNutritionRecordToImported(record)!!
        assertEquals("Lunch", mapped.mealType)
        assertEquals(480, mapped.calories)
        assertEquals(22, mapped.proteinGrams)
        assertEquals(58, mapped.carbsGrams)
        assertEquals(14, mapped.fatGrams)
        assertTrue(mapped.foodSummary.contains("Rajma Chawal Bowl"))
        assertTrue(mapped.foodSummary.contains("[Health Connect"))

        // Idempotent ingestion into Room preserves existing manual Diet & Water records
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val allModulesRepo = AllModulesRepository(db)
            val syncRepo = FitnessSyncRepository(db)
            val pid = db.userProfileDao().insertProfile(UserProfileEntity(fullName = "Vikram", age = 34, genderIdentity = "Male"))

            val now = System.currentTimeMillis()
            val todayKey = DateEpochUtils.todayDayKey(now)
            allModulesRepo.logMeal(
                profileId = pid,
                mealType = "Breakfast",
                foodSummary = "Manual Poha & Chai",
                calories = 280,
                proteinGrams = 7,
                carbsGrams = 46,
                fatGrams = 7,
                loggedAtEpochMs = now - 3_600_000L
            )
            db.waterDao().insertRecord(
                com.example.data.local.entity.WaterRecordEntity(
                    profileId = pid,
                    amountMl = 300,
                    drinkType = "Water",
                    note = "Manual morning glass",
                    dayKey = todayKey,
                    loggedAtEpochMs = now - 3_600_000L
                )
            )

            val payload = HealthSyncPayload(
                waterEntries = listOf(
                    ImportedWater(
                        amountMl = 500,
                        drinkType = "Synced Water",
                        dayKey = todayKey,
                        loggedAtEpochMs = now - 1_800_000L,
                        sourceId = "com.fitbit.FitbitMobile"
                    )
                ),
                nutritionEntries = listOf(
                    ImportedNutrition(
                        mealType = mapped.mealType,
                        foodSummary = mapped.foodSummary,
                        calories = mapped.calories,
                        proteinGrams = mapped.proteinGrams,
                        carbsGrams = mapped.carbsGrams,
                        fatGrams = mapped.fatGrams,
                        loggedAtEpochMs = now,
                        sourceId = mapped.sourceId
                    )
                ),
                sources = listOf(
                    ImportedSourceStat(
                        sourceId = "com.fitbit.FitbitMobile",
                        displayName = "Fitbit",
                        emoji = "⌚",
                        syncChannel = "Health Connect",
                        recordsCount = 2,
                        summary = "Nutrition & Hydration synced"
                    )
                )
            )

            val firstImported = syncRepo.ingestSyncPayload(pid, payload)
            assertEquals(2, firstImported)

            // Repeat same payload -> must deduplicate instead of duplicating
            val secondImported = syncRepo.ingestSyncPayload(pid, payload)
            assertEquals(0, secondImported)

            val dietRecords = db.dietDao().getDietRecordsSnapshot(pid)
            val waterRecords = db.waterDao().getAllForProfileSnapshot(pid)
            assertEquals(2, dietRecords.size)
            assertTrue(dietRecords.any { it.foodSummary == "Manual Poha & Chai" })
            assertTrue(dietRecords.any { it.foodSummary == mapped.foodSummary })
            assertEquals(2, waterRecords.size)
            assertTrue(waterRecords.any { it.note == "Manual morning glass" && it.amountMl == 300 })
            assertTrue(waterRecords.any { it.drinkType == "Synced Water" && it.amountMl == 500 })
        } finally {
            db.close()
        }
    }
}
