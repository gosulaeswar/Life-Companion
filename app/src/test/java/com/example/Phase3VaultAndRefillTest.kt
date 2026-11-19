package com.example

import android.app.NotificationManager
import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.DoctorConsultationEntity
import com.example.data.local.entity.HealthDocumentEntity
import com.example.data.local.entity.LabResultEntity
import com.example.data.local.entity.MedicalHistoryEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.HealthVaultRepository
import com.example.data.repository.PrivacyBackupRepository
import com.example.domain.DateEpochUtils
import com.example.domain.DoctorSummaryPdfGenerator
import com.example.domain.ExpiryAlertState
import com.example.domain.HealthVaultAndRefillEngine
import com.example.domain.RefillAlertState
import com.example.domain.VaultDateFilter
import com.example.notifications.AlertSoundManager
import com.example.security.CategoryToneSetting
import com.example.security.EncryptedDocumentVault
import com.example.security.KeystoreVaultManager
import com.example.security.LocalPreferencesManager
import com.example.ui.navigation.LifeWeaveDest
import com.example.ui.screens.ModuleSubRoutes
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config
import java.io.File
import javax.crypto.KeyGenerator

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Phase3VaultAndRefillTest {

    private fun testAesKey() = KeyGenerator.getInstance("AES").apply {
        init(256)
    }.generateKey()

    @Test
    fun `exported Room schema 8 json and non-destructive MIGRATION_7_8 preserve existing data and create Phase 3 tables`() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        val schemaDir = listOf(
            File("schemas/com.example.data.local.AppDatabase"),
            File("app/schemas/com.example.data.local.AppDatabase")
        ).firstOrNull { it.exists() && it.isDirectory }
        val schemaFile = schemaDir?.let { dir ->
            File(dir, "8.json").takeIf { it.exists() }
                ?: dir.listFiles { f -> f.extension == "json" }
                    ?.maxByOrNull { it.nameWithoutExtension.toIntOrNull() ?: 0 }
        }
        assertNotNull("Expected exported Room schema (>= 8.json) to exist", schemaFile)
        val schemaJson = JSONObject(schemaFile!!.readText()).getJSONObject("database")
        val exportedVersion = schemaJson.getInt("version")
        val entities = schemaJson.getJSONArray("entities")
        assertTrue("Expected exported schema version >= 8, got $exportedVersion", exportedVersion >= 8)
        assertTrue("Expected at least 22 entities in exported schema", entities.length() >= 22)
        val tableNames = (0 until entities.length())
            .map { entities.getJSONObject(it).getString("tableName") }
            .toSet()
        assertTrue(tableNames.containsAll(listOf("health_documents", "lab_results", "doctor_consultations", "allergy_records", "medical_history_entries", "medicine_refill_logs")))

        val dbName = "migration_7_to_8_test.db"
        context.deleteDatabase(dbName)

        // Create real v7 database with user_profiles and v7 medicines schema
        val v7Helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(7) {
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
                                dosesPerDay INTEGER NOT NULL,
                                intervalHours INTEGER NOT NULL,
                                mealRelation TEXT NOT NULL,
                                categoryEmoji TEXT NOT NULL,
                                isActive INTEGER NOT NULL,
                                notes TEXT NOT NULL
                            )
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO user_profiles VALUES (1, 'Aarav', 31, 'Male', '', 175, 70.0, 'Moderate', 2500, 0, 28, 5, 'en', 1000, 1000)
                            """.trimIndent()
                        )
                        db.execSQL(
                            """
                            INSERT INTO medicines VALUES (1, 1, 'Metformin', '500mg', '08:00', 'Every 12h (2x/day)', 2, 12, 'After Meal', '💊', 1, 'With breakfast')
                            """.trimIndent()
                        )
                    }

                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
                })
                .build()
        )
        v7Helper.writableDatabase.close()

        val upgradedDb = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(object : SupportSQLiteOpenHelper.Callback(8) {
                    override fun onCreate(db: SupportSQLiteDatabase) = Unit
                    override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {
                        AppDatabase.MIGRATION_7_8.migrate(db)
                    }
                })
                .build()
        ).writableDatabase

        try {
            val medCursor = upgradedDb.query(
                "SELECT name, stockQuantity, stockUnit, unitsPerDose, lowStockThresholdDays, refillReminderEnabled FROM medicines WHERE id = 1"
            )
            assertTrue(medCursor.moveToFirst())
            assertEquals("Metformin", medCursor.getString(0))
            assertEquals(0.0, medCursor.getDouble(1), 0.001)
            assertEquals("tablets", medCursor.getString(2))
            assertEquals(1.0, medCursor.getDouble(3), 0.001)
            assertEquals(5, medCursor.getInt(4))
            assertEquals(1, medCursor.getInt(5))
            medCursor.close()

            val tablesCursor = upgradedDb.query(
                "SELECT name FROM sqlite_master WHERE type='table' AND name IN ('health_documents','lab_results','allergy_records','medical_history_entries','medicine_refill_logs')"
            )
            val created = mutableSetOf<String>()
            while (tablesCursor.moveToNext()) {
                created += tablesCursor.getString(0)
            }
            tablesCursor.close()
            assertEquals(
                setOf("health_documents", "lab_results", "allergy_records", "medical_history_entries", "medicine_refill_logs"),
                created
            )
        } finally {
            upgradedDb.close()
            context.deleteDatabase(dbName)
        }
    }

    @Test
    fun `KeystoreVaultManager binary encryption and EncryptedDocumentVault write opaque ciphertext and verify round-trip`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val fixedKey = testAesKey()
        val vaultManager = KeystoreVaultManager { fixedKey }
        val docVault = EncryptedDocumentVault(context, vaultManager)
        docVault.wipeAllVaultFiles()

        val rawPayload = "%PDF-1.4\n1 0 obj\n<< /Title (Sample Lab Report) >>\nendobj\n".toByteArray()
        val encryptedDirect = vaultManager.encryptBytes(rawPayload)
        assertFalse(rawPayload.contentEquals(encryptedDirect))
        assertArrayEquals(rawPayload, vaultManager.decryptBytes(encryptedDirect))

        // Tamper ciphertext byte -> AEAD authentication tag must fail
        val tampered = encryptedDirect.copyOf()
        tampered[tampered.size - 1] = (tampered.last().toInt() xor 0xFF).toByte()
        assertTrue(runCatching { vaultManager.decryptBytes(tampered) }.isFailure)

        val relPath = docVault.storeRawBytesEncrypted(rawPayload)
        assertTrue(docVault.encryptedBlobExists(relPath))
        val diskFile = File(File(context.filesDir, "health_vault"), relPath)
        assertTrue(diskFile.exists())
        assertFalse(diskFile.readBytes().contentEquals(rawPayload))

        val decrypted = docVault.readDecryptedBytes(relPath).getOrThrow()
        assertArrayEquals(rawPayload, decrypted)

        // Path traversal attempt must fail safely
        assertTrue(docVault.readDecryptedBytes("../shared_prefs/secret.xml").isFailure)

        docVault.deleteEncryptedFile(relPath)
        assertFalse(docVault.encryptedBlobExists(relPath))
    }

    @Test
    fun `medicine intake auto-decrements stock on Taken, restock logs refill history, and expiry and refill alerts trigger accurately`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val fixedKey = testAesKey()
        val vaultManager = KeystoreVaultManager { fixedKey }
        val docVault = EncryptedDocumentVault(context, vaultManager)

        try {
            val profileId = db.userProfileDao().insertProfile(
                UserProfileEntity(fullName = "Meera", age = 32, genderIdentity = "Female")
            )
            val allModulesRepo = AllModulesRepository(db)
            val healthVaultRepo = HealthVaultRepository(db, docVault)
            val today = DateEpochUtils.todayEpochDay()

            val medId = allModulesRepo.addMedicine(
                profileId = profileId,
                name = "Levothyroxine",
                dosage = "50mcg",
                scheduleTime24h = "07:00",
                frequency = "Every 12h (2x/day)",
                dosesPerDay = 2,
                intervalHours = 12,
                stockQuantity = 3f,
                stockUnit = "tablets",
                unitsPerDose = 1f,
                lowStockThresholdDays = 2,
                expiryEpochDay = today + 10L
            )
            val initialMed = db.medicalCareDao().getMedicineById(medId)!!

            // Skipped intake must NOT decrement stock
            allModulesRepo.logMedicineIntake(profileId, initialMed, "Dose 1/2", status = "Skipped")
            assertEquals(3f, db.medicalCareDao().getMedicineById(medId)!!.stockQuantity, 0.01f)

            // Taken intake MUST decrement stock by unitsPerDose (1.0f -> 2.0f)
            allModulesRepo.logMedicineIntake(profileId, initialMed, "Dose 1/2", status = "Taken")
            val afterTaken = db.medicalCareDao().getMedicineById(medId)!!
            assertEquals(2f, afterTaken.stockQuantity, 0.01f)

            val invLow = HealthVaultAndRefillEngine.evaluateMedicineInventory(afterTaken, today)
            assertEquals(2f, invLow.dailyConsumptionUnits, 0.01f)
            assertEquals(1f, invLow.estimatedDaysRemaining!!, 0.01f)
            assertEquals(RefillAlertState.REFILL_SOON, invLow.refillState)
            assertEquals(ExpiryAlertState.EXPIRING_SOON, invLow.expiryState)
            assertTrue(invLow.needsAttention)

            // Refill / purchase restock adds stock and attaches prescription doc atomically
            val docId = db.healthVaultDao().insertDocument(
                HealthDocumentEntity(
                    profileId = profileId,
                    title = "Endocrinology Rx",
                    category = "Prescription",
                    documentEpochDay = today
                )
            )
            healthVaultRepo.logMedicinePurchaseAndRefill(
                profileId = profileId,
                medicine = afterTaken,
                quantityAdded = 30f,
                unitLabel = "tablets",
                purchaseDateEpochDay = today,
                expiryEpochDay = today + 180L,
                lowStockThresholdDays = 5,
                pharmacyOrSource = "Apollo Pharmacy",
                costNote = "Batch #L42",
                prescriptionDocId = docId
            )

            val restocked = db.medicalCareDao().getMedicineById(medId)!!
            assertEquals(32f, restocked.stockQuantity, 0.01f)
            assertEquals(today + 180L, restocked.expiryEpochDay)
            assertEquals(docId, restocked.linkedPrescriptionDocId)

            val invHealthy = HealthVaultAndRefillEngine.evaluateMedicineInventory(restocked, today)
            assertEquals(RefillAlertState.OK, invHealthy.refillState)
            assertEquals(ExpiryAlertState.OK, invHealthy.expiryState)
            assertFalse(invHealthy.needsAttention)

            val expiredCheck = HealthVaultAndRefillEngine.evaluateMedicineInventory(
                restocked.copy(stockQuantity = 0f, expiryEpochDay = today - 1L),
                today
            )
            assertEquals(RefillAlertState.OUT_OF_STOCK, expiredCheck.refillState)
            assertEquals(ExpiryAlertState.EXPIRED, expiredCheck.expiryState)

            val refillHistory = healthVaultRepo.observeRefills(profileId).first()
            assertEquals(1, refillHistory.size)
            assertEquals("Apollo Pharmacy", refillHistory.first().pharmacyOrSource)

            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val shadowNm = Shadows.shadowOf(nm)
            Shadows.shadowOf(context as android.app.Application)
                .grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS)
            val alertManager = AlertSoundManager(context)
            val posted = alertManager.showMedicineRefillOrExpiryNotification(
                title = "Medicine Refill Alert",
                body = "Levothyroxine inventory is low (~1d left).",
                wellnessToneSetting = CategoryToneSetting()
            )
            assertTrue(posted)
            assertTrue(shadowNm.allNotifications.isNotEmpty())
        } finally {
            db.close()
        }
    }

    @Test
    fun `lab comparisons, document filters, offline disclaimer fallback, and DoctorSummaryPdfGenerator work accurately`() {
        val today = DateEpochUtils.todayEpochDay()
        val labs = listOf(
            LabResultEntity(
                id = 1L,
                profileId = 1L,
                testName = "HbA1c",
                valueNumeric = 6.9,
                valueText = "6.9",
                unit = "%",
                referenceRangeText = "< 5.7",
                statusFlag = "High",
                recordedEpochDay = today - 90L,
                labName = "City Diagnostics"
            ),
            LabResultEntity(
                id = 2L,
                profileId = 1L,
                testName = "HbA1c",
                valueNumeric = 6.1,
                valueText = "6.1",
                unit = "%",
                referenceRangeText = "< 5.7",
                statusFlag = "Borderline",
                recordedEpochDay = today - 5L,
                labName = "City Diagnostics"
            ),
            LabResultEntity(
                id = 3L,
                profileId = 1L,
                testName = "Vitamin D",
                valueNumeric = 38.0,
                valueText = "38.0",
                unit = "ng/mL",
                referenceRangeText = "30 - 100",
                statusFlag = "Normal",
                recordedEpochDay = today - 2L,
                labName = "HealthLab"
            )
        )

        val comparisons = HealthVaultAndRefillEngine.compareLabResults(labs)
        assertEquals(2, comparisons.size)
        val vitD = comparisons[0]
        assertEquals("Vitamin D", vitD.displayTestName)
        assertEquals("•", vitD.directionSymbol)

        val hba1c = comparisons[1]
        assertEquals("HbA1c", hba1c.displayTestName)
        assertEquals("6.1", hba1c.latest.valueText)
        assertEquals("6.9", hba1c.previous?.valueText)
        assertEquals(-0.8, hba1c.numericDelta!!, 0.001)
        assertEquals("↓", hba1c.directionSymbol)
        assertEquals(2, hba1c.historyCount)

        val docs = listOf(
            HealthDocumentEntity(
                id = 1L,
                profileId = 1L,
                title = "Annual Lipid & HbA1c Panel",
                category = "Lab Report",
                documentEpochDay = today - 5L,
                providerOrLabName = "City Diagnostics"
            ),
            HealthDocumentEntity(
                id = 2L,
                profileId = 1L,
                title = "Cardiology Rx",
                category = "Prescription",
                documentEpochDay = today - 60L,
                providerOrLabName = "Dr. Sharma"
            )
        )
        assertEquals(1, HealthVaultAndRefillEngine.filterDocuments(docs, "sharma", "All", VaultDateFilter.ALL, todayEpochDay = today).size)
        assertEquals(1, HealthVaultAndRefillEngine.filterDocuments(docs, "", "Lab Report", VaultDateFilter.LAST_30_DAYS, todayEpochDay = today).size)
        assertEquals(1, HealthVaultAndRefillEngine.filterDocuments(docs, "", "All", VaultDateFilter.CUSTOM_DATE, customEpochDay = today - 60L, todayEpochDay = today).size)

        val allergies = listOf(AllergyRecordEntity(id = 1L, profileId = 1L, allergen = "Penicillin", reaction = "Rash", severity = "Moderate"))
        val history = listOf(MedicalHistoryEntity(id = 1L, profileId = 1L, conditionOrEvent = "Hypothyroidism", category = "Condition", diagnosedYearOrDate = "2021"))

        val fallback = HealthVaultAndRefillEngine.buildLocalFallbackExplanation(
            doc = docs.first(),
            linkedLabs = labs.take(2),
            allergies = allergies,
            history = history
        )
        assertFalse(fallback.isFromCloudAi)
        assertEquals(HealthVaultAndRefillEngine.STANDARD_AI_MEDICAL_DISCLAIMER, fallback.disclaimer)
        assertTrue(fallback.questionsForDoctor.any { it.contains("Penicillin") })

        val pdfBytes = DoctorSummaryPdfGenerator.buildSummaryPdfBytes(
            profile = UserProfileEntity(id = 1L, fullName = "Meera", age = 32, genderIdentity = "Female"),
            allergies = allergies,
            medicalHistory = history,
            medicines = listOf(
                MedicineEntity(
                    id = 1L,
                    profileId = 1L,
                    name = "Levothyroxine",
                    dosage = "50mcg",
                    scheduleTime24h = "07:00",
                    frequency = "Daily",
                    stockQuantity = 28f,
                    stockUnit = "tablets",
                    expiryEpochDay = today + 120L
                )
            ),
            labComparisons = comparisons,
            consultations = listOf(
                DoctorConsultationEntity(
                    id = 1L,
                    profileId = 1L,
                    doctorName = "Dr. Rao",
                    specialty = "Endocrinology",
                    consultationEpochMs = System.currentTimeMillis(),
                    summary = "Continue 50mcg daily",
                    followUpEpochDay = today + 30L
                )
            ),
            documents = docs,
            todayEpochDay = today
        )
        assertTrue(pdfBytes.size > 200)
        assertEquals("%PDF-", String(pdfBytes.copyOfRange(0, 5), Charsets.US_ASCII))
    }

    @Test
    fun `22-table backup export and restore round-trips Phase 3 vault records and wipes encrypted blobs on delete`() = runTest {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val prefsManager = LocalPreferencesManager(context)
        prefsManager.resetAll()
        val fixedKey = testAesKey()
        val vaultManager = KeystoreVaultManager { fixedKey }
        val docVault = EncryptedDocumentVault(context, vaultManager)
        docVault.wipeAllVaultFiles()

        val db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        try {
            val backupRepo = PrivacyBackupRepository(db, prefsManager, vaultManager, docVault)
            val today = DateEpochUtils.todayEpochDay()
            val profileId = db.userProfileDao().insertProfile(
                UserProfileEntity(fullName = "Kiran", age = 36, genderIdentity = "Male")
            )

            val encRelPath = docVault.storeRawBytesEncrypted("secret-doc".toByteArray())
            val docId = db.healthVaultDao().insertDocument(
                HealthDocumentEntity(
                    profileId = profileId,
                    title = "Blood Work May",
                    category = "Lab Report",
                    documentEpochDay = today,
                    providerOrLabName = "Apex Labs",
                    mimeType = "application/pdf",
                    originalFileName = "blood_may.pdf",
                    encryptedFilePath = encRelPath,
                    fileSizeBytes = 10L
                )
            )
            db.healthVaultDao().insertLabResult(
                LabResultEntity(
                    profileId = profileId,
                    testName = "TSH",
                    valueNumeric = 2.4,
                    valueText = "2.4",
                    unit = "mIU/L",
                    referenceRangeText = "0.4 - 4.0",
                    statusFlag = "Normal",
                    recordedEpochDay = today,
                    labName = "Apex Labs",
                    documentId = docId
                )
            )
            db.healthVaultDao().insertAllergy(
                AllergyRecordEntity(profileId = profileId, allergen = "Sulfa", reaction = "Hives", severity = "Severe")
            )
            db.healthVaultDao().insertMedicalHistory(
                MedicalHistoryEntity(profileId = profileId, conditionOrEvent = "Appendectomy", category = "Surgery", diagnosedYearOrDate = "2018")
            )
            val medId = db.medicalCareDao().insertMedicine(
                MedicineEntity(
                    profileId = profileId,
                    name = "Vitamin D3",
                    dosage = "2000 IU",
                    scheduleTime24h = "09:00",
                    frequency = "Daily (1x)",
                    stockQuantity = 45f,
                    stockUnit = "capsules",
                    expiryEpochDay = today + 200L,
                    linkedPrescriptionDocId = docId
                )
            )
            val healthVaultRepo = HealthVaultRepository(db, docVault)
            healthVaultRepo.logMedicinePurchaseAndRefill(
                profileId = profileId,
                medicine = db.medicalCareDao().getMedicineById(medId)!!,
                quantityAdded = 15f,
                unitLabel = "capsules",
                purchaseDateEpochDay = today,
                expiryEpochDay = today + 200L,
                pharmacyOrSource = "MedPlus",
                prescriptionDocId = docId
            )

            val exportedJson = backupRepo.buildExportJson()
            val root = JSONObject(exportedJson)
            assertEquals(1, root.getJSONArray("healthDocuments").length())
            assertEquals(1, root.getJSONArray("labResults").length())
            assertEquals(1, root.getJSONArray("allergyRecords").length())
            assertEquals(1, root.getJSONArray("medicalHistoryEntries").length())
            assertEquals(1, root.getJSONArray("medicineRefillLogs").length())

            val preview = backupRepo.parseBackupPreview(exportedJson).getOrThrow()
            assertEquals(6, preview.totalRecords)

            backupRepo.deleteAllLocalData()
            assertFalse(docVault.encryptedBlobExists(encRelPath))
            assertTrue(db.userProfileDao().getActiveProfileSnapshot() == null)

            backupRepo.restoreFromRawPayload(exportedJson).getOrThrow()
            val restoredProfile = db.userProfileDao().getActiveProfileSnapshot()!!
            val restoredDocs = db.healthVaultDao().getDocumentsSnapshot(restoredProfile.id)
            val restoredLabs = db.healthVaultDao().getLabResultsSnapshot(restoredProfile.id)
            val restoredAllergies = db.healthVaultDao().getAllergiesSnapshot(restoredProfile.id)
            val restoredHistory = db.healthVaultDao().getMedicalHistorySnapshot(restoredProfile.id)
            val restoredMeds = db.medicalCareDao().getMedicinesSnapshot(restoredProfile.id)
            val restoredRefills = db.healthVaultDao().getRefillsSnapshot(restoredProfile.id)

            assertEquals(1, restoredDocs.size)
            assertEquals(1, restoredLabs.size)
            assertEquals(1, restoredAllergies.size)
            assertEquals(1, restoredHistory.size)
            assertEquals(1, restoredMeds.size)
            assertEquals(1, restoredRefills.size)

            val newDocId = restoredDocs.first().id
            val newMedId = restoredMeds.first().id
            assertEquals(newDocId, restoredLabs.first().documentId)
            assertEquals(newDocId, restoredMeds.first().linkedPrescriptionDocId)
            assertEquals(newDocId, restoredRefills.first().prescriptionDocId)
            assertEquals(newMedId, restoredRefills.first().medicineId)
            assertEquals(60f, restoredMeds.first().stockQuantity, 0.01f)
            assertEquals(LifeWeaveDest.HealthVault, LifeWeaveDest.fromSubRouteString(ModuleSubRoutes.ROUTE_HEALTH_VAULT))
        } finally {
            docVault.wipeAllVaultFiles()
            db.close()
        }
    }
}
