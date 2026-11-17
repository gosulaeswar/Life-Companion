package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AiPreferenceEntity
import com.example.data.local.entity.ModuleActivationEntity
import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.domain.DateEpochUtils
import com.example.security.KeystoreVaultManager
import com.example.security.LocalPreferencesManager
import com.example.security.OfflineLicenseVerifier
import com.example.security.VerificationResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

class UserProfileRepository(private val database: AppDatabase) {
    private val dao = database.userProfileDao()

    fun observeActiveProfile(): Flow<UserProfileEntity?> = dao.observeActiveProfile()

    suspend fun saveOrUpdateProfile(profile: UserProfileEntity): Long {
        val now = System.currentTimeMillis()
        val existing = dao.getActiveProfileSnapshot()
        return if (existing == null) {
            val id = dao.insertProfile(profile.copy(createdAtEpochMs = now, updatedAtEpochMs = now))
            dao.upsertAiPreference(AiPreferenceEntity(profileId = id))
            id
        } else {
            dao.updateProfile(
                profile.copy(
                    id = existing.id,
                    createdAtEpochMs = existing.createdAtEpochMs,
                    updatedAtEpochMs = now
                )
            )
            existing.id
        }
    }

    suspend fun setCycleTrackingEnabled(enabled: Boolean) {
        val existing = dao.getActiveProfileSnapshot() ?: return
        dao.updateProfile(
            existing.copy(
                cycleTrackingEnabled = enabled,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateWaterGoal(goalMl: Int) {
        val existing = dao.getActiveProfileSnapshot() ?: return
        dao.updateProfile(
            existing.copy(
                dailyWaterGoalMl = goalMl.coerceIn(500, 6000),
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }
}

class WaterRepository(private val database: AppDatabase) {
    private val dao = database.waterDao()

    fun observeTodayRecords(profileId: Long): Flow<List<WaterRecordEntity>> =
        dao.observeRecordsForDay(profileId, DateEpochUtils.todayDayKey())

    fun observeLast7Days(profileId: Long): Flow<List<WaterRecordEntity>> =
        dao.observeRecordsSince(profileId, DateEpochUtils.dayKeyDaysAgo(6))

    suspend fun logWater(
        profileId: Long,
        amountMl: Int,
        drinkType: String = "Water",
        note: String = "",
        loggedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        dao.insertRecord(
            WaterRecordEntity(
                profileId = profileId,
                amountMl = amountMl.coerceIn(25, 3000),
                drinkType = drinkType,
                note = note.trim(),
                dayKey = DateEpochUtils.todayDayKey(loggedAtEpochMs),
                loggedAtEpochMs = loggedAtEpochMs
            )
        )
    }

    suspend fun deleteRecord(recordId: Long) {
        dao.deleteById(recordId)
    }
}

class PeriodRepository(private val database: AppDatabase) {
    private val dao = database.periodDao()

    fun observeRecords(profileId: Long): Flow<List<PeriodRecordEntity>> =
        dao.observeAllForProfile(profileId)

    suspend fun saveRecord(record: PeriodRecordEntity): Long {
        return if (record.id == 0L) {
            dao.insertRecord(record.copy(updatedAtEpochMs = System.currentTimeMillis()))
        } else {
            dao.updateRecord(record.copy(updatedAtEpochMs = System.currentTimeMillis()))
            record.id
        }
    }

    suspend fun deleteRecord(recordId: Long) {
        dao.deleteById(recordId)
    }
}

class CustomAlarmRepository(private val database: AppDatabase) {
    private val dao = database.reminderDao()

    fun observeAlarms(profileId: Long): Flow<List<ReminderEntity>> =
        dao.observeRemindersForProfile(profileId)

    suspend fun getAllSnapshot(profileId: Long): List<ReminderEntity> =
        dao.getRemindersSnapshot(profileId)

    suspend fun saveAlarm(reminder: ReminderEntity): Long {
        return if (reminder.id == 0L) {
            dao.insertReminder(reminder)
        } else {
            dao.updateReminder(reminder)
            reminder.id
        }
    }

    suspend fun toggleAlarm(reminder: ReminderEntity, enabled: Boolean): ReminderEntity {
        val updated = reminder.copy(isEnabled = enabled)
        dao.updateReminder(updated)
        return updated
    }

    suspend fun deleteAlarm(reminderId: Long) {
        dao.deleteReminderById(reminderId)
    }

    suspend fun recordTriggered(reminderId: Long) {
        if (reminderId > 0L) dao.recordAlarmTriggered(reminderId)
    }

    suspend fun recordCompleted(reminderId: Long) {
        if (reminderId > 0L) dao.recordAlarmCompleted(reminderId)
    }

    suspend fun recordSnoozed(reminderId: Long) {
        if (reminderId > 0L) dao.recordAlarmSnoozed(reminderId)
    }
}

class ModuleRepository(
    private val database: AppDatabase,
    private val verifier: OfflineLicenseVerifier
) {
    private val dao = database.moduleActivationDao()

    fun observeModules(): Flow<List<ModuleActivationEntity>> = dao.observeModules()

    suspend fun ensureSeeded() {
        dao.insertDefaultsIfMissing(AppDatabase.defaultModuleCatalog())
        dao.unlockAllModulesFree()
    }

    suspend fun verifyAndActivateOffline(token: String): VerificationResult {
        ensureSeeded()
        val result = verifier.verifyToken(token)
        if (result is VerificationResult.Valid) {
            val module = dao.getModuleByKey(result.payload.moduleKey)
            if (module == null) {
                return VerificationResult.Invalid(
                    "Unknown moduleKey '${result.payload.moduleKey}' in activation credential."
                )
            }
            dao.updateActivationStatus(
                moduleKey = module.moduleKey,
                isActivated = true,
                activatedAtEpochMs = System.currentTimeMillis(),
                licenseReference = result.payload.licenseReference,
                signatureDigest = result.signatureDigest
            )
        }
        return result
    }
}

class PrivacyBackupRepository(
    private val database: AppDatabase,
    private val preferencesManager: LocalPreferencesManager,
    private val vaultManager: KeystoreVaultManager
) {
    suspend fun buildExportJson(): String = withContext(Dispatchers.IO) {
        val profile = database.userProfileDao().getActiveProfileSnapshot()
        val waterRecords = profile?.let { database.waterDao().getAllForProfileSnapshot(it.id) }.orEmpty()
        val periodRecords = profile?.let { database.periodDao().getAllForProfileSnapshot(it.id) }.orEmpty()
        val customAlarms = profile?.let { database.reminderDao().getRemindersSnapshot(it.id) }.orEmpty()
        val medicines = profile?.let { database.medicalCareDao().getMedicinesSnapshot(it.id) }.orEmpty()
        val medicineIntakes = profile?.let { database.medicalCareDao().getIntakesSnapshot(it.id) }.orEmpty()
        val consultations = profile?.let { database.medicalCareDao().getConsultationsSnapshot(it.id) }.orEmpty()
        val connectedSources = profile?.let { database.connectedFitnessDao().getConnectedSourcesSnapshot(it.id) }.orEmpty()
        val vitalMetrics = profile?.let { database.connectedFitnessDao().getVitalMetricsSnapshot(it.id) }.orEmpty()
        val modules = database.moduleActivationDao().getAllModulesSnapshot()
        val vaultStatus = vaultManager.inspectVaultStatus()

        val root = JSONObject().apply {
            put("schema", "life_companion_local_export_v1")
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("privacyGuarantee", "100% on-device SQLite export; zero cloud uploads.")
            put(
                "securityArchitecture",
                JSONObject().apply {
                    put("keystoreReady", vaultStatus.keystoreAvailable)
                    put("dataEncryptionAlgorithm", vaultStatus.algorithm)
                    put("dataKeyAlias", vaultStatus.keyAlias)
                    put("activationVerificationAlgorithm", "SHA256withECDSA (Public Key Only)")
                }
            )
            put(
                "profile",
                profile?.let {
                    JSONObject().apply {
                        put("fullName", it.fullName)
                        put("age", it.age)
                        put("genderIdentity", it.genderIdentity)
                        put("customGenderLabel", it.customGenderLabel)
                        put("heightCm", it.heightCm)
                        put("weightKg", it.weightKg)
                        put("activityLevel", it.activityLevel)
                        put("dailyWaterGoalMl", it.dailyWaterGoalMl)
                        put("cycleTrackingEnabled", it.cycleTrackingEnabled)
                        put("averageCycleLengthDays", it.averageCycleLengthDays)
                        put("averagePeriodDurationDays", it.averagePeriodDurationDays)
                        put("preferredLanguageCode", it.preferredLanguageCode)
                    }
                } ?: JSONObject.NULL
            )
            put(
                "waterRecords",
                JSONArray().apply {
                    waterRecords.forEach { r ->
                        put(
                            JSONObject().apply {
                                put("id", r.id)
                                put("amountMl", r.amountMl)
                                put("drinkType", r.drinkType)
                                put("note", r.note)
                                put("dayKey", r.dayKey)
                                put("loggedAtEpochMs", r.loggedAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "periodRecords",
                JSONArray().apply {
                    periodRecords.forEach { p ->
                        put(
                            JSONObject().apply {
                                put("id", p.id)
                                put("startDateEpochDay", p.startDateEpochDay)
                                put("endDateEpochDay", p.endDateEpochDay)
                                put("flowIntensity", p.flowIntensity)
                                put("symptomsCsv", p.symptomsCsv)
                                put("mood", p.mood)
                                put("notes", p.notes)
                            }
                        )
                    }
                }
            )
            put(
                "modules",
                JSONArray().apply {
                    modules.forEach { m ->
                        put(
                            JSONObject().apply {
                                put("moduleKey", m.moduleKey)
                                put("displayName", m.displayName)
                                put("isAlwaysFree", m.isAlwaysFree)
                                put("isActivated", m.isActivated)
                                put("licenseReference", m.licenseReference)
                            }
                        )
                    }
                }
            )
            put(
                "customAlarms",
                JSONArray().apply {
                    customAlarms.forEach { a ->
                        put(
                            JSONObject().apply {
                                put("id", a.id)
                                put("title", a.title)
                                put("moduleKey", a.moduleKey)
                                put("triggerTime24h", a.triggerTime24h)
                                put("daysOfWeekMask", a.daysOfWeekMask)
                                put("isEnabled", a.isEnabled)
                                put("soundKey", a.soundKey)
                                put("customSoundLabel", a.customSoundLabel)
                                put("repeatIntervalMinutes", a.repeatIntervalMinutes)
                                put("triggeredCount", a.triggeredCount)
                                put("completedCount", a.completedCount)
                                put("snoozedCount", a.snoozedCount)
                            }
                        )
                    }
                }
            )
            put(
                "medicines",
                JSONArray().apply {
                    medicines.forEach { med ->
                        put(
                            JSONObject().apply {
                                put("id", med.id)
                                put("name", med.name)
                                put("dosage", med.dosage)
                                put("scheduleTime24h", med.scheduleTime24h)
                                put("dosesPerDay", med.dosesPerDay)
                                put("intervalHours", med.intervalHours)
                                put("mealRelation", med.mealRelation)
                                put("isActive", med.isActive)
                            }
                        )
                    }
                }
            )
            put(
                "medicineIntakes",
                JSONArray().apply {
                    medicineIntakes.forEach { intake ->
                        put(
                            JSONObject().apply {
                                put("id", intake.id)
                                put("medicineId", intake.medicineId)
                                put("medicineName", intake.medicineName)
                                put("dosageTaken", intake.dosageTaken)
                                put("slotLabel", intake.slotLabel)
                                put("status", intake.status)
                                put("dayKey", intake.dayKey)
                                put("takenAtEpochMs", intake.takenAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "doctorConsultations",
                JSONArray().apply {
                    consultations.forEach { c ->
                        put(
                            JSONObject().apply {
                                put("id", c.id)
                                put("doctorName", c.doctorName)
                                put("specialty", c.specialty)
                                put("clinicOrHospital", c.clinicOrHospital)
                                put("consultationEpochMs", c.consultationEpochMs)
                                put("summary", c.summary)
                                put("followUpEpochDay", c.followUpEpochDay)
                                put("followUpNotes", c.followUpNotes)
                                put("isFollowUpCompleted", c.isFollowUpCompleted)
                            }
                        )
                    }
                }
            )
            put(
                "connectedFitnessSources",
                JSONArray().apply {
                    connectedSources.forEach { src ->
                        put(
                            JSONObject().apply {
                                put("sourceId", src.sourceId)
                                put("displayName", src.displayName)
                                put("syncChannel", src.syncChannel)
                                put("lastSyncedEpochMs", src.lastSyncedEpochMs)
                                put("recordsImportedCount", src.recordsImportedCount)
                                put("latestSummary", src.latestSummary)
                            }
                        )
                    }
                }
            )
            put(
                "healthVitalMetrics",
                JSONArray().apply {
                    vitalMetrics.forEach { metric ->
                        put(
                            JSONObject().apply {
                                put("metricType", metric.metricType)
                                put("valuePrimary", metric.valuePrimary)
                                put("unit", metric.unit)
                                put("sourceDisplayName", metric.sourceDisplayName)
                                put("dayKey", metric.dayKey)
                                put("recordedAtEpochMs", metric.recordedAtEpochMs)
                            }
                        )
                    }
                }
            )
        }
        root.toString(2)
    }

    suspend fun writeExportToUri(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val json = buildExportJson()
            val bytes = json.toByteArray(StandardCharsets.UTF_8)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: error("Unable to open selected document for writing.")
            bytes.size
        }
    }

    suspend fun deleteAllLocalData() {
        database.withTransaction {
            database.waterDao().deleteAllWaterRecords()
            database.periodDao().deleteAllPeriodRecords()
            database.reminderDao().deleteAllReminders()
            database.fitnessDao().deleteAllExercises()
            database.fitnessDao().deleteAllSleep()
            database.fitnessDao().deleteAllSteps()
            database.dietDao().deleteAllDietRecords()
            database.medicalCareDao().deleteAllMedicineIntakes()
            database.medicalCareDao().deleteAllMedicines()
            database.medicalCareDao().deleteAllConsultations()
            database.beautyCareDao().deleteAllBeautyRecords()
            database.digitalWellbeingDao().deleteAllWellbeingRecords()
            database.connectedFitnessDao().deleteAllVitalMetrics()
            database.connectedFitnessDao().deleteAllSources()
            database.userProfileDao().deleteAllProfiles()
            database.moduleActivationDao().insertDefaultsIfMissing(AppDatabase.defaultModuleCatalog())
        }
        preferencesManager.resetAll()
    }
}
