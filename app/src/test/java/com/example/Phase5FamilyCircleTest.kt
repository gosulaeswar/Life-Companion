package com.example

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.UserProfileRepository
import com.example.data.repository.WaterRepository
import com.example.family.FamilyCircleDoc
import com.example.family.FamilyCircleRepository
import com.example.family.FamilyCircleValidator
import com.example.family.FamilyProfileSlotDoc
import com.example.family.FamilyRole
import com.example.family.SharedWellnessCategory
import com.example.family.SharingConsentSettings
import com.example.security.FeatureGate
import com.example.security.LocalDefaultEntitlementPolicy
import com.example.security.LocalPreferencesManager
import com.example.security.SubscriptionTier
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
class Phase5FamilyCircleTest {

    @Test
    fun `exported Room schema 10 json and non-destructive MIGRATION_9_10 preserve v9 records`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val schemaDirs = listOf(
            File("schemas/com.example.data.local.AppDatabase"),
            File("app/schemas/com.example.data.local.AppDatabase")
        ).filter { it.isDirectory }
        val schemaFile = schemaDirs.firstNotNullOfOrNull { dir ->
            File(dir, "10.json").takeIf { it.exists() }
                ?: dir.listFiles { f -> f.extension == "json" }
                    ?.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: 0 }
        }
        assertNotNull("Expected Room schema 10.json or newer to be exported", schemaFile)
        val schemaJson = JSONObject(schemaFile!!.readText()).getJSONObject("database")
        assertTrue(schemaJson.getInt("version") >= 10)
        assertTrue(schemaJson.getJSONArray("entities").length() >= 24)

        val dbName = "migration_9_to_10_test.db"
        context.deleteDatabase(dbName)

        val v9Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(9) {
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
                            CREATE TABLE water_records (
                                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                                profileId INTEGER NOT NULL,
                                amountMl INTEGER NOT NULL,
                                drinkType TEXT NOT NULL,
                                note TEXT NOT NULL,
                                dayKey TEXT NOT NULL,
                                loggedAtEpochMs INTEGER NOT NULL,
                                FOREIGN KEY(profileId) REFERENCES user_profiles(id) ON UPDATE NO ACTION ON DELETE CASCADE
                            )
                            """.trimIndent()
                        )
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_water_records_profileId ON water_records(profileId)")
                        db.execSQL("CREATE INDEX IF NOT EXISTS index_water_records_dayKey ON water_records(dayKey)")

                        // Create remaining v9 minimal tables required by schema validation via empty placeholders if needed:
                        // Actually let's test MIGRATION_9_10 directly on a fresh v1..v9 or inspect column migration
                        db.execSQL(
                            """
                            INSERT INTO user_profiles (
                                fullName, age, genderIdentity, customGenderLabel, heightCm, weightKg,
                                activityLevel, dailyWaterGoalMl, cycleTrackingEnabled, averageCycleLengthDays,
                                averagePeriodDurationDays, preferredLanguageCode, createdAtEpochMs, updatedAtEpochMs
                            ) VALUES ('Kavya', 29, 'Female', '', 162, 58.0, 'Moderate', 2400, 1, 28, 5, 'en', 1000, 1000)
                            """.trimIndent()
                        )
                        db.execSQL(
                            "INSERT INTO water_records (profileId, amountMl, drinkType, note, dayKey, loggedAtEpochMs) VALUES (1, 350, 'Water', 'Morning', '2026-11-18', 1000)"
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        val rawDb = v9Helper.writableDatabase
        AppDatabase.MIGRATION_9_10.migrate(rawDb)

        rawDb.query("SELECT fullName, relationLabel, linkedFamilyId, linkedSlotId FROM user_profiles").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals("Kavya", c.getString(0))
            assertEquals("Self", c.getString(1))
            assertTrue(c.isNull(2))
            assertTrue(c.isNull(3))
        }
        rawDb.query("SELECT amountMl FROM water_records WHERE profileId = 1").use { c ->
            assertTrue(c.moveToFirst())
            assertEquals(350, c.getInt(0))
        }
        v9Helper.close()
        context.deleteDatabase(dbName)
    }

    @Test
    fun `entitlement policy enforces 1-profile Free cap and 5-profile Family Circle cap with isolated wellness records`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefs = LocalPreferencesManager(context)
        prefs.resetAll()

        val freePolicy = LocalDefaultEntitlementPolicy { SubscriptionTier.FREE }
        assertEquals(1, freePolicy.maxAllowedProfiles())
        assertFalse(freePolicy.hasAccess(FeatureGate.FAMILY_CIRCLE))
        assertTrue(freePolicy.hasAccess(FeatureGate.PERIOD_TRACKER))

        val familyPolicy = LocalDefaultEntitlementPolicy { SubscriptionTier.FAMILY }
        assertEquals(5, familyPolicy.maxAllowedProfiles())
        assertTrue(familyPolicy.hasAccess(FeatureGate.FAMILY_CIRCLE))

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        try {
            val profileRepo = UserProfileRepository(db, prefs)
            val waterRepo = WaterRepository(db)
            val allRepo = AllModulesRepository(db)

            val ownerId = profileRepo.saveOrUpdateProfile(
                UserProfileEntity(
                    fullName = "Ananya",
                    age = 33,
                    genderIdentity = "Female",
                    dailyWaterGoalMl = 2500,
                    relationLabel = "Self"
                )
            )
            waterRepo.logWater(ownerId, 400, "Water")

            // On Free tier (cap = 1), creating a 2nd profile MUST fail
            val deniedSecond = profileRepo.createIndividualProfile(
                UserProfileEntity(fullName = "Meera", age = 62, genderIdentity = "Female", relationLabel = "Mother"),
                maxAllowedProfiles = freePolicy.maxAllowedProfiles()
            )
            assertTrue(deniedSecond.isFailure)
            assertEquals(1, profileRepo.getAllProfilesSnapshot().size)

            // On Family Circle tier (cap = 5), creating up to 5 individual profiles succeeds; 6th fails
            val createdIds = mutableListOf(ownerId)
            listOf("Meera" to "Mother", "Rohan" to "Partner", "Aarav" to "Son", "Tara" to "Daughter").forEach { (name, rel) ->
                val res = profileRepo.createIndividualProfile(
                    profile = UserProfileEntity(fullName = name, age = 30, genderIdentity = "Prefer not to say", relationLabel = rel),
                    maxAllowedProfiles = familyPolicy.maxAllowedProfiles(),
                    switchToCreated = false
                )
                assertTrue("Expected $name to be created", res.isSuccess)
                createdIds += res.getOrThrow().id
            }
            assertEquals(5, createdIds.size)
            assertEquals(5, profileRepo.getAllProfilesSnapshot().size)

            val deniedSixth = profileRepo.createIndividualProfile(
                UserProfileEntity(fullName = "Sixth", age = 20, genderIdentity = "Male", relationLabel = "Guest"),
                maxAllowedProfiles = familyPolicy.maxAllowedProfiles()
            )
            assertTrue(deniedSixth.isFailure)

            // Individual wellness record isolation across profiles
            val meeraId = createdIds[1]
            waterRepo.logWater(meeraId, 250, "Herbal Tea")
            allRepo.addMedicine(meeraId, "Thyroxine", "50mcg", "07:00", "Daily", 1, 24, "Before Meal", "💊")

            assertEquals(1, waterRepo.observeTodayRecords(ownerId).first().size)
            assertEquals(400, waterRepo.observeTodayRecords(ownerId).first().first().amountMl)
            assertEquals(0, allRepo.observeMedicines(ownerId).first().size)

            assertEquals(1, waterRepo.observeTodayRecords(meeraId).first().size)
            assertEquals(250, waterRepo.observeTodayRecords(meeraId).first().first().amountMl)
            assertEquals(1, allRepo.observeMedicines(meeraId).first().size)

            // Active profile switcher updates observeActiveProfile()
            assertTrue(profileRepo.switchActiveProfile(meeraId))
            assertEquals("Meera", profileRepo.observeActiveProfile().first()?.fullName)
        } finally {
            db.close()
        }
    }

    @Test
    fun `explicit consent-based sharing strictly filters categories and Cycle stays private by default`() {
        val defaultConsent = SharingConsentSettings()
        assertFalse(defaultConsent.shareCycle)
        assertTrue(defaultConsent.shareHydration)

        val filtered = FamilyCircleRepository.buildConsentedSnapshotsPreview(
            waterTodayMl = 1800,
            waterGoalMl = 2400,
            stepsToday = 6400,
            sleepMinutes = 450,
            caloriesToday = 1650,
            mealsCount = 3,
            takenDosesToday = 2,
            expectedDosesToday = 2,
            cycleDayLabel = "Cycle Day 3",
            consent = defaultConsent.copy(shareFitnessSleep = false, shareMedicines = false)
        )

        assertTrue(filtered.containsKey(SharedWellnessCategory.HYDRATION))
        assertTrue(filtered.containsKey(SharedWellnessCategory.NUTRITION))
        assertFalse(filtered.containsKey(SharedWellnessCategory.FITNESS_SLEEP))
        assertFalse(filtered.containsKey(SharedWellnessCategory.MEDICINES))
        assertFalse(filtered.containsKey(SharedWellnessCategory.CYCLE))

        // Only when explicitly opted in does Cycle appear
        val explicitCycle = FamilyCircleRepository.buildConsentedSnapshotsPreview(
            waterTodayMl = 1800,
            waterGoalMl = 2400,
            stepsToday = 0,
            sleepMinutes = 0,
            caloriesToday = 0,
            mealsCount = 0,
            takenDosesToday = 0,
            expectedDosesToday = 0,
            cycleDayLabel = "Cycle Day 3",
            consent = SharingConsentSettings(
                shareHydration = false,
                shareFitnessSleep = false,
                shareNutrition = false,
                shareMedicines = false,
                shareCycle = true
            )
        )
        assertEquals(setOf(SharedWellnessCategory.CYCLE), explicitCycle.keys)
    }

    @Test
    fun `slot allocation emails times and role authorization helpers match backend rules`() {
        assertTrue(FamilyCircleValidator.isValidEmail("member@lifeweave.in"))
        assertFalse(FamilyCircleValidator.isValidEmail("not-an-email"))
        assertTrue(FamilyCircleValidator.isValidTime24h("08:30"))
        assertFalse(FamilyCircleValidator.isValidTime24h("25:99"))

        val slots = listOf(
            FamilyProfileSlotDoc(id = "slot_1", slotIndex = 1),
            FamilyProfileSlotDoc(id = "slot_2", slotIndex = 2)
        )
        assertEquals("slot_3", FamilyCircleValidator.nextAvailableSlotId(slots, maxProfiles = 5))
        assertNull(FamilyCircleValidator.nextAvailableSlotId(slots, maxProfiles = 1))

        val fullSlots = (1..5).map { FamilyProfileSlotDoc(id = "slot_$it", slotIndex = it) }
        assertNull(FamilyCircleValidator.nextAvailableSlotId(fullSlots, maxProfiles = 5))

        val family = FamilyCircleDoc(
            id = "fam_1",
            ownerUid = "u_owner",
            memberRoles = mapOf("u_owner" to "OWNER", "u_care" to "CAREGIVER", "u_mem" to "MEMBER")
        )
        assertTrue(family.roleOf("u_owner").canManageMembersAndReminders)
        assertTrue(family.roleOf("u_care").canManageMembersAndReminders)
        assertFalse(family.roleOf("u_mem").canManageMembersAndReminders)
        assertEquals(FamilyRole.CAREGIVER, family.roleOf("u_care"))
    }
}
