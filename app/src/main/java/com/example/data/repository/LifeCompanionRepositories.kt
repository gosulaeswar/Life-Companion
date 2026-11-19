package com.example.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AiPreferenceEntity
import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.BeautyCareRecordEntity
import com.example.data.local.entity.ConnectedDeviceSourceEntity
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
import com.example.data.local.entity.NutritionPlanEntity
import com.example.data.local.entity.NutritionPreferenceEntity
import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.data.local.entity.AchievementBadgeEntity
import com.example.data.local.entity.DailyRoutineEntity
import com.example.data.local.entity.MoodJournalEntryEntity
import com.example.data.local.entity.RoutineCompletionEntity
import com.example.data.local.entity.WellnessChallengeEntity
import com.example.domain.DateEpochUtils
import com.example.security.AiDataAuthorization
import com.example.security.EncryptedDocumentVault
import com.example.security.KeystoreVaultManager
import com.example.security.LocalPreferencesManager
import com.example.security.OfflineLicenseVerifier
import com.example.security.SupportedLanguage
import com.example.security.VerificationResult
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets

data class BackupPreviewSummary(
    val schemaVersion: String,
    val isEncrypted: Boolean,
    val exportedAtEpochMs: Long,
    val profileName: String,
    val totalRecords: Int,
    val breakdownText: String
)

data class BackupRestoreSummary(
    val profileName: String,
    val restoredRecordsCount: Int,
    val restoredReminders: List<ReminderEntity>
)

class UserProfileRepository(
    private val database: AppDatabase,
    private val preferencesManager: LocalPreferencesManager? = null
) {
    private val dao = database.userProfileDao()

    fun observeActiveProfile(): Flow<UserProfileEntity?> {
        val prefs = preferencesManager ?: return dao.observeActiveProfile()
        return combine(
            prefs.preferencesFlow.map { it.activeProfileId }.distinctUntilChanged(),
            dao.observeAllProfiles()
        ) { activeId, profiles ->
            profiles.firstOrNull { it.id == activeId } ?: profiles.firstOrNull()
        }
    }

    fun observeAllProfiles(): Flow<List<UserProfileEntity>> = dao.observeAllProfiles()

    suspend fun getAllProfilesSnapshot(): List<UserProfileEntity> = dao.getAllProfilesSnapshot()

    suspend fun getActiveProfileSnapshot(): UserProfileEntity? {
        val all = dao.getAllProfilesSnapshot()
        if (all.isEmpty()) return null
        val selectedId = preferencesManager?.preferencesFlow?.first()?.activeProfileId ?: 0L
        return all.firstOrNull { it.id == selectedId } ?: all.first()
    }

    suspend fun switchActiveProfile(profileId: Long): Boolean {
        val exists = dao.getProfileById(profileId) ?: return false
        preferencesManager?.setActiveProfileId(exists.id)
        return true
    }

    suspend fun saveOrUpdateProfile(profile: UserProfileEntity): Long {
        val now = System.currentTimeMillis()
        val existing = getActiveProfileSnapshot()
        return if (existing == null) {
            val id = dao.insertProfile(profile.copy(createdAtEpochMs = now, updatedAtEpochMs = now))
            dao.upsertAiPreference(AiPreferenceEntity(profileId = id))
            preferencesManager?.setActiveProfileId(id)
            id
        } else {
            dao.updateProfile(
                profile.copy(
                    id = existing.id,
                    relationLabel = profile.relationLabel.ifBlank { existing.relationLabel },
                    linkedFamilyId = profile.linkedFamilyId ?: existing.linkedFamilyId,
                    linkedSlotId = profile.linkedSlotId ?: existing.linkedSlotId,
                    createdAtEpochMs = existing.createdAtEpochMs,
                    updatedAtEpochMs = now
                )
            )
            existing.id
        }
    }

    suspend fun createIndividualProfile(
        profile: UserProfileEntity,
        maxAllowedProfiles: Int,
        switchToCreated: Boolean = true
    ): Result<UserProfileEntity> {
        val cleanName = profile.fullName.trim()
        if (cleanName.isEmpty()) {
            return Result.failure(IllegalArgumentException("Profile name is required."))
        }
        val currentCount = dao.getProfileCount()
        val cappedLimit = maxAllowedProfiles.coerceIn(1, 5)
        if (currentCount >= cappedLimit) {
            return Result.failure(
                IllegalStateException(
                    if (cappedLimit <= 1) {
                        "Your current plan supports 1 profile. Upgrade to LifeWeave Family Circle to create up to 5 individual wellness profiles."
                    } else {
                        "Maximum limit of 5 profiles per Family Circle reached."
                    }
                )
            )
        }
        val now = System.currentTimeMillis()
        val toInsert = profile.copy(
            id = 0L,
            fullName = cleanName,
            relationLabel = profile.relationLabel.trim().ifEmpty { "Family Member" },
            dailyWaterGoalMl = profile.dailyWaterGoalMl.coerceIn(500, 6000),
            createdAtEpochMs = now,
            updatedAtEpochMs = now
        )
        val newId = dao.insertProfile(toInsert)
        dao.upsertAiPreference(AiPreferenceEntity(profileId = newId))
        if (switchToCreated) {
            preferencesManager?.setActiveProfileId(newId)
        }
        val saved = dao.getProfileById(newId) ?: toInsert.copy(id = newId)
        return Result.success(saved)
    }

    suspend fun linkProfileToFamilySlot(profileId: Long, familyId: String, slotId: String) {
        val existing = dao.getProfileById(profileId) ?: return
        dao.updateProfile(
            existing.copy(
                linkedFamilyId = familyId,
                linkedSlotId = slotId,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun deleteSecondaryProfile(profileId: Long): Result<Unit> {
        val all = dao.getAllProfilesSnapshot()
        if (all.size <= 1) {
            return Result.failure(IllegalStateException("Primary profile cannot be deleted."))
        }
        val primaryId = all.first().id
        if (profileId == primaryId) {
            return Result.failure(IllegalStateException("Primary owner profile cannot be removed."))
        }
        dao.deleteProfileById(profileId)
        val activeId = preferencesManager?.preferencesFlow?.first()?.activeProfileId ?: 0L
        if (activeId == profileId) {
            preferencesManager?.setActiveProfileId(primaryId)
        }
        return Result.success(Unit)
    }

    suspend fun setCycleTrackingEnabled(enabled: Boolean) {
        val existing = getActiveProfileSnapshot() ?: return
        dao.updateProfile(
            existing.copy(
                cycleTrackingEnabled = enabled,
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun updateWaterGoal(goalMl: Int) {
        val existing = getActiveProfileSnapshot() ?: return
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

    fun observeLast60Days(profileId: Long): Flow<List<WaterRecordEntity>> =
        dao.observeRecordsSince(profileId, DateEpochUtils.dayKeyDaysAgo(59))

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
    private val verifier: OfflineLicenseVerifier,
    private val preferencesManager: LocalPreferencesManager? = null
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
            if (result.payload.moduleKey == "family_circle") {
                preferencesManager?.setFamilyPlanActivated(true, result.payload.licenseReference)
                return result
            }
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
    private val vaultManager: KeystoreVaultManager,
    private val encryptedDocumentVault: EncryptedDocumentVault? = null
) {
    companion object {
        const val SCHEMA_V1 = "life_companion_local_export_v1"
        const val SCHEMA_V2 = "life_companion_local_export_v2"
        private const val MAX_RESTORE_BYTES = 8 * 1024 * 1024
    }

    suspend fun buildExportJson(): String = withContext(Dispatchers.IO) {
        val profile = database.userProfileDao().getActiveProfileSnapshot()
        val pid = profile?.id
        val aiPref = pid?.let { database.userProfileDao().getAiPreferenceSnapshot(it) }
        val waterRecords = pid?.let { database.waterDao().getAllForProfileSnapshot(it) }.orEmpty()
        val periodRecords = pid?.let { database.periodDao().getAllForProfileSnapshot(it) }.orEmpty()
        val customAlarms = pid?.let { database.reminderDao().getRemindersSnapshot(it) }.orEmpty()
        val medicines = pid?.let { database.medicalCareDao().getMedicinesSnapshot(it) }.orEmpty()
        val medicineIntakes = pid?.let { database.medicalCareDao().getIntakesSnapshot(it) }.orEmpty()
        val consultations = pid?.let { database.medicalCareDao().getConsultationsSnapshot(it) }.orEmpty()
        val exercises = pid?.let { database.fitnessDao().getExercisesSnapshot(it) }.orEmpty()
        val sleepRecords = pid?.let { database.fitnessDao().getSleepSnapshot(it) }.orEmpty()
        val stepRecords = pid?.let { database.fitnessDao().getAllStepsSnapshot(it) }.orEmpty()
        val dietRecords = pid?.let { database.dietDao().getDietRecordsSnapshot(it) }.orEmpty()
        val beautyRecords = pid?.let { database.beautyCareDao().getBeautyRecordsSnapshot(it) }.orEmpty()
        val wellbeingRecords = pid?.let { database.digitalWellbeingDao().getWellbeingRecordsSnapshot(it) }.orEmpty()
        val connectedSources = pid?.let { database.connectedFitnessDao().getConnectedSourcesSnapshot(it) }.orEmpty()
        val vitalMetrics = pid?.let { database.connectedFitnessDao().getVitalMetricsSnapshot(it) }.orEmpty()
        val healthDocs = pid?.let { database.healthVaultDao().getDocumentsSnapshot(it) }.orEmpty()
        val labResults = pid?.let { database.healthVaultDao().getLabResultsSnapshot(it) }.orEmpty()
        val allergies = pid?.let { database.healthVaultDao().getAllergiesSnapshot(it) }.orEmpty()
        val medicalHistory = pid?.let { database.healthVaultDao().getMedicalHistorySnapshot(it) }.orEmpty()
        val refillLogs = pid?.let { database.healthVaultDao().getRefillsSnapshot(it) }.orEmpty()
        val nutritionPref = pid?.let { database.nutritionDao().getPreferenceSnapshot(it) }
        val nutritionPlans = pid?.let { database.nutritionDao().getPlansSnapshot(it) }.orEmpty()
        val dailyRoutines = pid?.let { database.wellnessEngagementDao().getRoutinesSnapshot(it) }.orEmpty()
        val routineCompletions = pid?.let { database.wellnessEngagementDao().getCompletionsSnapshot(it) }.orEmpty()
        val moodEntries = pid?.let { database.wellnessEngagementDao().getMoodEntriesSnapshot(it) }.orEmpty()
        val challenges = pid?.let { database.wellnessEngagementDao().getChallengesSnapshot(it) }.orEmpty()
        val badges = pid?.let { database.wellnessEngagementDao().getBadgesSnapshot(it) }.orEmpty()
        val modules = database.moduleActivationDao().getAllModulesSnapshot()
        val prefs = preferencesManager.preferencesFlow.first()
        val vaultStatus = vaultManager.inspectVaultStatus()

        val root = JSONObject().apply {
            put("schema", SCHEMA_V2)
            put("exportedAtEpochMs", System.currentTimeMillis())
            put("privacyGuarantee", "100% on-device SQLite full-vault export covering all 29 tables.")
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
                "uiPreferences",
                JSONObject().apply {
                    put("themeMode", prefs.themeMode.name)
                    put("languageCode", prefs.language.code)
                    put("wellbeingOveruseAlertsEnabled", prefs.wellbeingOveruseAlertsEnabled)
                    put("wellbeingWarningPercent", prefs.wellbeingWarningPercent)
                    put(
                        "categorySchedules",
                        JSONObject().apply {
                            listOf("water" to prefs.waterTone, "period" to prefs.periodTone, "wellness" to prefs.wellnessTone).forEach { (cat, tone) ->
                                put(
                                    cat,
                                    JSONObject().apply {
                                        put("enabled", tone.reminderEnabled)
                                        put("hour", tone.reminderHour)
                                        put("minute", tone.reminderMinute)
                                        put("repeatIntervalMinutes", tone.repeatIntervalMinutes)
                                        put("customEmoji", tone.customEmoji ?: "")
                                        put("customMessage", tone.customMessage ?: "")
                                        put("gesturePreset", tone.gesturePreset)
                                    }
                                )
                            }
                        }
                    )
                    put(
                        "aiDataAuthorization",
                        JSONObject().apply {
                            val auth = prefs.aiDataAuth
                            put("includeHydration", auth.includeHydration)
                            put("includeSleep", auth.includeSleep)
                            put("includeFitnessAndVitals", auth.includeFitnessAndVitals)
                            put("includeNutrition", auth.includeNutrition)
                            put("includeMedicinesAndVisits", auth.includeMedicinesAndVisits)
                            put("includeWellbeingAndSelfCare", auth.includeWellbeingAndSelfCare)
                            put("includeCycle", auth.includeCycle)
                        }
                    )
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
                        put("heightCm", it.heightCm ?: JSONObject.NULL)
                        put("weightKg", it.weightKg?.toDouble() ?: JSONObject.NULL)
                        put("activityLevel", it.activityLevel)
                        put("dailyWaterGoalMl", it.dailyWaterGoalMl)
                        put("cycleTrackingEnabled", it.cycleTrackingEnabled)
                        put("averageCycleLengthDays", it.averageCycleLengthDays)
                        put("averagePeriodDurationDays", it.averagePeriodDurationDays)
                        put("preferredLanguageCode", it.preferredLanguageCode)
                        put("relationLabel", it.relationLabel)
                        put("createdAtEpochMs", it.createdAtEpochMs)
                        put("updatedAtEpochMs", it.updatedAtEpochMs)
                    }
                } ?: JSONObject.NULL
            )
            put(
                "aiPreference",
                aiPref?.let {
                    JSONObject().apply {
                        put("onDeviceAgentEnabled", it.onDeviceAgentEnabled)
                        put("proactiveTipsEnabled", it.proactiveTipsEnabled)
                        put("preferredTone", it.preferredTone)
                        put("voiceInputEnabled", it.voiceInputEnabled)
                        put("updatedAtEpochMs", it.updatedAtEpochMs)
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
                                put("endDateEpochDay", p.endDateEpochDay ?: JSONObject.NULL)
                                put("flowIntensity", p.flowIntensity)
                                put("symptomsCsv", p.symptomsCsv)
                                put("mood", p.mood)
                                put("notes", p.notes)
                                put("updatedAtEpochMs", p.updatedAtEpochMs)
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
                                put("activatedAtEpochMs", m.activatedAtEpochMs ?: JSONObject.NULL)
                                put("licenseReference", m.licenseReference ?: JSONObject.NULL)
                                put("signatureDigest", m.signatureDigest ?: JSONObject.NULL)
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
                                put("customSoundUri", a.customSoundUri ?: JSONObject.NULL)
                                put("customSoundLabel", a.customSoundLabel ?: JSONObject.NULL)
                                put("alarmEmoji", a.alarmEmoji)
                                put("customMessage", a.customMessage)
                                put("gesturePreset", a.gesturePreset)
                                put("repeatIntervalMinutes", a.repeatIntervalMinutes)
                                put("triggeredCount", a.triggeredCount)
                                put("completedCount", a.completedCount)
                                put("snoozedCount", a.snoozedCount)
                                put("lastTriggeredEpochMs", a.lastTriggeredEpochMs)
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
                                put("frequency", med.frequency)
                                put("dosesPerDay", med.dosesPerDay)
                                put("intervalHours", med.intervalHours)
                                put("mealRelation", med.mealRelation)
                                put("categoryEmoji", med.categoryEmoji)
                                put("isActive", med.isActive)
                                put("notes", med.notes)
                                put("stockQuantity", med.stockQuantity.toDouble())
                                put("stockUnit", med.stockUnit)
                                put("unitsPerDose", med.unitsPerDose.toDouble())
                                put("lowStockThresholdDays", med.lowStockThresholdDays)
                                put("expiryEpochDay", med.expiryEpochDay ?: JSONObject.NULL)
                                put("linkedPrescriptionDocId", med.linkedPrescriptionDocId ?: JSONObject.NULL)
                                put("refillReminderEnabled", med.refillReminderEnabled)
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
                                put("followUpEpochDay", c.followUpEpochDay ?: JSONObject.NULL)
                                put("followUpNotes", c.followUpNotes)
                                put("isFollowUpCompleted", c.isFollowUpCompleted)
                            }
                        )
                    }
                }
            )
            put(
                "exerciseRecords",
                JSONArray().apply {
                    exercises.forEach { e ->
                        put(
                            JSONObject().apply {
                                put("id", e.id)
                                put("activityType", e.activityType)
                                put("category", e.category)
                                put("durationMinutes", e.durationMinutes)
                                put("intensity", e.intensity)
                                put("caloriesBurned", e.caloriesBurned)
                                put("loggedAtEpochMs", e.loggedAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "sleepRecords",
                JSONArray().apply {
                    sleepRecords.forEach { s ->
                        put(
                            JSONObject().apply {
                                put("id", s.id)
                                put("bedtimeEpochMs", s.bedtimeEpochMs)
                                put("wakeEpochMs", s.wakeEpochMs)
                                put("qualityRating", s.qualityRating)
                                put("notes", s.notes)
                            }
                        )
                    }
                }
            )
            put(
                "stepRecords",
                JSONArray().apply {
                    stepRecords.forEach { s ->
                        put(
                            JSONObject().apply {
                                put("id", s.id)
                                put("dayKey", s.dayKey)
                                put("stepCount", s.stepCount)
                                put("distanceMeters", s.distanceMeters)
                                put("updatedAtEpochMs", s.updatedAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "dietRecords",
                JSONArray().apply {
                    dietRecords.forEach { d ->
                        put(
                            JSONObject().apply {
                                put("id", d.id)
                                put("mealType", d.mealType)
                                put("foodSummary", d.foodSummary)
                                put("calories", d.calories)
                                put("proteinGrams", d.proteinGrams)
                                put("carbsGrams", d.carbsGrams)
                                put("fatGrams", d.fatGrams)
                                put("loggedAtEpochMs", d.loggedAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "beautyCareRecords",
                JSONArray().apply {
                    beautyRecords.forEach { b ->
                        put(
                            JSONObject().apply {
                                put("id", b.id)
                                put("routineType", b.routineType)
                                put("productOrStep", b.productOrStep)
                                put("completedAtEpochMs", b.completedAtEpochMs)
                                put("notes", b.notes)
                            }
                        )
                    }
                }
            )
            put(
                "digitalWellbeingRecords",
                JSONArray().apply {
                    wellbeingRecords.forEach { w ->
                        put(
                            JSONObject().apply {
                                put("id", w.id)
                                put("dayKey", w.dayKey)
                                put("screenTimeMinutes", w.screenTimeMinutes)
                                put("focusSessionsCount", w.focusSessionsCount)
                                put("mindfulGoalMinutes", w.mindfulGoalMinutes)
                                put("unlockCount", w.unlockCount)
                                put("appLaunchCount", w.appLaunchCount)
                                put("nightScreenMinutes", w.nightScreenMinutes)
                                put("longestSessionMinutes", w.longestSessionMinutes)
                                put("topAppName", w.topAppName)
                                put("topAppMinutes", w.topAppMinutes)
                                put("topAppsJson", w.topAppsJson)
                                put("sourceLabel", w.sourceLabel)
                                put("lastSyncedEpochMs", w.lastSyncedEpochMs)
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
                                put("emoji", src.emoji)
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
                                put("sourceId", metric.sourceId)
                                put("sourceDisplayName", metric.sourceDisplayName)
                                put("dayKey", metric.dayKey)
                                put("recordedAtEpochMs", metric.recordedAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "healthDocuments",
                JSONArray().apply {
                    healthDocs.forEach { d ->
                        put(
                            JSONObject().apply {
                                put("id", d.id)
                                put("title", d.title)
                                put("category", d.category)
                                put("documentEpochDay", d.documentEpochDay)
                                put("providerOrLabName", d.providerOrLabName)
                                put("notes", d.notes)
                                put("mimeType", d.mimeType)
                                put("originalFileName", d.originalFileName)
                                put("encryptedFilePath", d.encryptedFilePath)
                                put("fileSizeBytes", d.fileSizeBytes)
                                put("aiExplanation", d.aiExplanation)
                                put("aiExplainedAtEpochMs", d.aiExplainedAtEpochMs ?: JSONObject.NULL)
                                put("linkedConsultationId", d.linkedConsultationId ?: JSONObject.NULL)
                                put("createdAtEpochMs", d.createdAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "labResults",
                JSONArray().apply {
                    labResults.forEach { l ->
                        put(
                            JSONObject().apply {
                                put("id", l.id)
                                put("testName", l.testName)
                                put("valueNumeric", l.valueNumeric ?: JSONObject.NULL)
                                put("valueText", l.valueText)
                                put("unit", l.unit)
                                put("referenceRangeText", l.referenceRangeText)
                                put("statusFlag", l.statusFlag)
                                put("recordedEpochDay", l.recordedEpochDay)
                                put("labName", l.labName)
                                put("documentId", l.documentId ?: JSONObject.NULL)
                                put("notes", l.notes)
                                put("createdAtEpochMs", l.createdAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "allergyRecords",
                JSONArray().apply {
                    allergies.forEach { a ->
                        put(
                            JSONObject().apply {
                                put("id", a.id)
                                put("allergen", a.allergen)
                                put("reaction", a.reaction)
                                put("severity", a.severity)
                                put("notes", a.notes)
                                put("recordedAtEpochMs", a.recordedAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "medicalHistoryEntries",
                JSONArray().apply {
                    medicalHistory.forEach { h ->
                        put(
                            JSONObject().apply {
                                put("id", h.id)
                                put("conditionOrEvent", h.conditionOrEvent)
                                put("category", h.category)
                                put("diagnosedYearOrDate", h.diagnosedYearOrDate)
                                put("status", h.status)
                                put("notes", h.notes)
                                put("updatedAtEpochMs", h.updatedAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "medicineRefillLogs",
                JSONArray().apply {
                    refillLogs.forEach { r ->
                        put(
                            JSONObject().apply {
                                put("id", r.id)
                                put("medicineId", r.medicineId)
                                put("medicineName", r.medicineName)
                                put("quantityAdded", r.quantityAdded.toDouble())
                                put("unitLabel", r.unitLabel)
                                put("purchaseDateEpochDay", r.purchaseDateEpochDay)
                                put("expiryEpochDay", r.expiryEpochDay ?: JSONObject.NULL)
                                put("pharmacyOrSource", r.pharmacyOrSource)
                                put("costNote", r.costNote)
                                put("prescriptionDocId", r.prescriptionDocId ?: JSONObject.NULL)
                                put("loggedAtEpochMs", r.loggedAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "nutritionPreference",
                nutritionPref?.let { np ->
                    JSONObject().apply {
                        put("preferredRegion", np.preferredRegion)
                        put("dietaryPreference", np.dietaryPreference)
                        put("nutritionGoal", np.nutritionGoal)
                        put("customCalorieTarget", np.customCalorieTarget ?: JSONObject.NULL)
                        put("avoidVaultAllergens", np.avoidVaultAllergens)
                        put("updatedAtEpochMs", np.updatedAtEpochMs)
                    }
                } ?: JSONObject.NULL
            )
            put(
                "nutritionPlans",
                JSONArray().apply {
                    nutritionPlans.forEach { plan ->
                        put(
                            JSONObject().apply {
                                put("id", plan.id)
                                put("planTitle", plan.planTitle)
                                put("regionLabel", plan.regionLabel)
                                put("dietaryLabel", plan.dietaryLabel)
                                put("goalLabel", plan.goalLabel)
                                put("regionalNotes", plan.regionalNotes)
                                put("totalCalories", plan.totalCalories)
                                put("totalProteinGrams", plan.totalProteinGrams)
                                put("totalCarbsGrams", plan.totalCarbsGrams)
                                put("totalFatGrams", plan.totalFatGrams)
                                put("mealsJson", plan.mealsJson)
                                put("groceryHighlightsJson", plan.groceryHighlightsJson)
                                put("avoidedAllergensCsv", plan.avoidedAllergensCsv)
                                put("isFromCloudAi", plan.isFromCloudAi)
                                put("createdAtEpochMs", plan.createdAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "dailyRoutines",
                JSONArray().apply {
                    dailyRoutines.forEach { r ->
                        put(
                            JSONObject().apply {
                                put("id", r.id)
                                put("title", r.title)
                                put("emoji", r.emoji)
                                put("timeSlot", r.timeSlot)
                                put("triggerTime24h", r.triggerTime24h)
                                put("estimatedMinutes", r.estimatedMinutes)
                                put("stepsSerialized", r.stepsSerialized)
                                put("linkedModuleKey", r.linkedModuleKey)
                                put("reminderEnabled", r.reminderEnabled)
                                put("isOptional", r.isOptional)
                                put("isActive", r.isActive)
                                put("sortOrder", r.sortOrder)
                                put("createdAtEpochMs", r.createdAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "routineCompletions",
                JSONArray().apply {
                    routineCompletions.forEach { c ->
                        put(
                            JSONObject().apply {
                                put("routineId", c.routineId)
                                put("dayKey", c.dayKey)
                                put("completedStepsMask", c.completedStepsMask)
                                put("completedStepsCount", c.completedStepsCount)
                                put("totalStepsCount", c.totalStepsCount)
                                put("reflectionNote", c.reflectionNote)
                                put("completedAtEpochMs", c.completedAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "moodJournalEntries",
                JSONArray().apply {
                    moodEntries.forEach { m ->
                        put(
                            JSONObject().apply {
                                put("id", m.id)
                                put("dayKey", m.dayKey)
                                put("moodLabel", m.moodLabel)
                                put("moodEmoji", m.moodEmoji)
                                put("moodScore", m.moodScore)
                                put("energyLevel", m.energyLevel)
                                put("stressLevel", m.stressLevel)
                                put("tagsCsv", m.tagsCsv)
                                put("reflectionNote", m.reflectionNote)
                                put("gratitudeText", m.gratitudeText)
                                put("loggedAtEpochMs", m.loggedAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "wellnessChallenges",
                JSONArray().apply {
                    challenges.forEach { ch ->
                        put(
                            JSONObject().apply {
                                put("challengeKey", ch.challengeKey)
                                put("title", ch.title)
                                put("emoji", ch.emoji)
                                put("category", ch.category)
                                put("description", ch.description)
                                put("targetDays", ch.targetDays)
                                put("currentProgressDays", ch.currentProgressDays)
                                put("status", ch.status)
                                put("isOptInOnly", ch.isOptInOnly)
                                put("startDayKey", ch.startDayKey)
                                put("lastCheckInDayKey", ch.lastCheckInDayKey)
                                put("completedAtEpochMs", ch.completedAtEpochMs ?: JSONObject.NULL)
                                put("createdAtEpochMs", ch.createdAtEpochMs)
                            }
                        )
                    }
                }
            )
            put(
                "achievementBadges",
                JSONArray().apply {
                    badges.forEach { b ->
                        put(
                            JSONObject().apply {
                                put("badgeKey", b.badgeKey)
                                put("title", b.title)
                                put("emoji", b.emoji)
                                put("description", b.description)
                                put("category", b.category)
                                put("earnedAtEpochMs", b.earnedAtEpochMs ?: JSONObject.NULL)
                                put("progressCurrent", b.progressCurrent)
                                put("progressTarget", b.progressTarget)
                            }
                        )
                    }
                }
            )
        }
        root.toString(2)
    }

    suspend fun buildEncryptedExportPayload(): String = withContext(Dispatchers.IO) {
        val json = buildExportJson()
        vaultManager.encryptForLocalOrDriveBackup(json)
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

    suspend fun writeEncryptedExportToUri(context: Context, uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val encrypted = buildEncryptedExportPayload()
            val bytes = encrypted.toByteArray(StandardCharsets.UTF_8)
            context.contentResolver.openOutputStream(uri)?.use { stream ->
                stream.write(bytes)
                stream.flush()
            } ?: error("Unable to open selected encrypted archive for writing.")
            bytes.size
        }
    }

    suspend fun readPayloadFromUri(context: Context, uri: Uri): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val buffer = input.readBytes()
                require(buffer.size <= MAX_RESTORE_BYTES) { "Backup file exceeds 8 MB safety limit." }
                buffer
            } ?: error("Unable to read selected backup document.")
            String(bytes, StandardCharsets.UTF_8)
        }
    }

    suspend fun parseBackupPreview(rawText: String): Result<BackupPreviewSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawText.trim()
            val isEncrypted = trimmed.startsWith("LCENC1:")
            val jsonStr = if (isEncrypted) {
                try {
                    vaultManager.decryptBackupPayload(trimmed)
                } catch (e: Exception) {
                    throw IllegalStateException(
                        "Unable to decrypt LCENC1 archive on this device. Encrypted vaults are hardware-bound to the device Keystore that created them.",
                        e
                    )
                }
            } else {
                trimmed
            }
            val root = JSONObject(jsonStr)
            val schema = root.optString("schema")
            require(schema == SCHEMA_V1 || schema == SCHEMA_V2) {
                "Unrecognized backup format ('$schema')."
            }
            val profileObj = root.optJSONObject("profile")
                ?: error("Backup does not contain a local profile record.")
            val profileName = profileObj.optString("fullName").trim().ifEmpty { "User" }

            val counts = listOf(
                "Water" to (root.optJSONArray("waterRecords")?.length() ?: 0),
                "Cycle" to (root.optJSONArray("periodRecords")?.length() ?: 0),
                "Alarms" to (root.optJSONArray("customAlarms")?.length() ?: 0),
                "Meds" to (root.optJSONArray("medicines")?.length() ?: 0),
                "Visits" to (root.optJSONArray("doctorConsultations")?.length() ?: 0),
                "Fitness" to ((root.optJSONArray("exerciseRecords")?.length() ?: 0) +
                    (root.optJSONArray("sleepRecords")?.length() ?: 0) +
                    (root.optJSONArray("stepRecords")?.length() ?: 0)),
                "Diet" to (root.optJSONArray("dietRecords")?.length() ?: 0),
                "Vitals" to (root.optJSONArray("healthVitalMetrics")?.length() ?: 0),
                "Vault Docs" to (root.optJSONArray("healthDocuments")?.length() ?: 0),
                "Labs" to (root.optJSONArray("labResults")?.length() ?: 0),
                "Routines" to (root.optJSONArray("dailyRoutines")?.length() ?: 0),
                "Mood" to (root.optJSONArray("moodJournalEntries")?.length() ?: 0)
            )
            val total = counts.sumOf { it.second } +
                (root.optJSONArray("medicineIntakes")?.length() ?: 0) +
                (root.optJSONArray("beautyCareRecords")?.length() ?: 0) +
                (root.optJSONArray("digitalWellbeingRecords")?.length() ?: 0) +
                (root.optJSONArray("connectedFitnessSources")?.length() ?: 0) +
                (root.optJSONArray("allergyRecords")?.length() ?: 0) +
                (root.optJSONArray("medicalHistoryEntries")?.length() ?: 0) +
                (root.optJSONArray("medicineRefillLogs")?.length() ?: 0) +
                (root.optJSONArray("nutritionPlans")?.length() ?: 0) +
                (root.optJSONArray("routineCompletions")?.length() ?: 0) +
                (root.optJSONArray("wellnessChallenges")?.length() ?: 0) +
                (root.optJSONArray("achievementBadges")?.length() ?: 0)
            val breakdown = counts.filter { it.second > 0 }
                .joinToString(" • ") { "${it.second} ${it.first}" }
                .ifEmpty { "Profile & Preferences" }

            BackupPreviewSummary(
                schemaVersion = schema,
                isEncrypted = isEncrypted,
                exportedAtEpochMs = root.optLong("exportedAtEpochMs", 0L),
                profileName = profileName,
                totalRecords = total,
                breakdownText = breakdown
            )
        }
    }

    suspend fun restoreFromRawPayload(rawText: String): Result<BackupRestoreSummary> = withContext(Dispatchers.IO) {
        runCatching {
            val trimmed = rawText.trim()
            val jsonStr = if (trimmed.startsWith("LCENC1:")) {
                vaultManager.decryptBackupPayload(trimmed)
            } else {
                trimmed
            }
            val root = JSONObject(jsonStr)
            val schema = root.optString("schema")
            require(schema == SCHEMA_V1 || schema == SCHEMA_V2) {
                "Unsupported backup schema: $schema"
            }
            val profileObj = root.optJSONObject("profile")
                ?: error("Backup is missing profile data.")
            val fullName = profileObj.optString("fullName").trim().ifEmpty { "User" }

            val restoredReminders = mutableListOf<ReminderEntity>()
            var totalRestored = 0

            database.withTransaction {
                clearAllDatabaseTablesLocked()

                val newProfileId = database.userProfileDao().insertProfile(
                    UserProfileEntity(
                        fullName = fullName,
                        age = profileObj.optInt("age", 28).coerceIn(13, 120),
                        genderIdentity = profileObj.optString("genderIdentity", "Prefer not to say"),
                        customGenderLabel = profileObj.optString("customGenderLabel", ""),
                        heightCm = profileObj.optNullableInt("heightCm"),
                        weightKg = profileObj.optNullableFloat("weightKg"),
                        activityLevel = profileObj.optString("activityLevel", "Moderate"),
                        dailyWaterGoalMl = profileObj.optInt("dailyWaterGoalMl", 2500).coerceIn(500, 6000),
                        cycleTrackingEnabled = profileObj.optBoolean("cycleTrackingEnabled", true),
                        averageCycleLengthDays = profileObj.optInt("averageCycleLengthDays", 28).coerceIn(18, 48),
                        averagePeriodDurationDays = profileObj.optInt("averagePeriodDurationDays", 5).coerceIn(1, 14),
                        preferredLanguageCode = profileObj.optString("preferredLanguageCode", "en"),
                        relationLabel = profileObj.optString("relationLabel", "Self"),
                        createdAtEpochMs = profileObj.optLong("createdAtEpochMs", System.currentTimeMillis()),
                        updatedAtEpochMs = System.currentTimeMillis()
                    )
                )

                val aiObj = root.optJSONObject("aiPreference")
                database.userProfileDao().upsertAiPreference(
                    AiPreferenceEntity(
                        profileId = newProfileId,
                        onDeviceAgentEnabled = aiObj?.optBoolean("onDeviceAgentEnabled", false) ?: false,
                        proactiveTipsEnabled = aiObj?.optBoolean("proactiveTipsEnabled", true) ?: true,
                        preferredTone = aiObj?.optString("preferredTone", "Gentle") ?: "Gentle",
                        voiceInputEnabled = aiObj?.optBoolean("voiceInputEnabled", false) ?: false,
                        updatedAtEpochMs = aiObj?.optLong("updatedAtEpochMs", System.currentTimeMillis())
                            ?: System.currentTimeMillis()
                    )
                )

                root.optJSONArray("waterRecords")?.forEachObject { w ->
                    database.waterDao().insertRecord(
                        WaterRecordEntity(
                            profileId = newProfileId,
                            amountMl = w.optInt("amountMl", 250).coerceIn(25, 3000),
                            drinkType = w.optString("drinkType", "Water"),
                            note = w.optString("note", ""),
                            dayKey = w.optString("dayKey", DateEpochUtils.todayDayKey()),
                            loggedAtEpochMs = w.optLong("loggedAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("periodRecords")?.forEachObject { p ->
                    database.periodDao().insertRecord(
                        PeriodRecordEntity(
                            profileId = newProfileId,
                            startDateEpochDay = p.optLong("startDateEpochDay"),
                            endDateEpochDay = p.optNullableLong("endDateEpochDay"),
                            flowIntensity = p.optString("flowIntensity", "Medium"),
                            symptomsCsv = p.optString("symptomsCsv", ""),
                            mood = p.optString("mood", "Calm"),
                            notes = p.optString("notes", ""),
                            updatedAtEpochMs = p.optLong("updatedAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                val docIdMap = mutableMapOf<Long, Long>()
                root.optJSONArray("healthDocuments")?.forEachObject { d ->
                    val oldDocId = d.optLong("id", 0L)
                    val newDocId = database.healthVaultDao().insertDocument(
                        HealthDocumentEntity(
                            profileId = newProfileId,
                            title = d.optString("title", "Health Document"),
                            category = d.optString("category", "Medical Record"),
                            documentEpochDay = d.optLong("documentEpochDay", DateEpochUtils.todayEpochDay()),
                            providerOrLabName = d.optString("providerOrLabName", ""),
                            notes = d.optString("notes", ""),
                            mimeType = d.optString("mimeType", ""),
                            originalFileName = d.optString("originalFileName", ""),
                            encryptedFilePath = d.optString("encryptedFilePath", ""),
                            fileSizeBytes = d.optLong("fileSizeBytes", 0L),
                            aiExplanation = d.optString("aiExplanation", ""),
                            aiExplainedAtEpochMs = d.optNullableLong("aiExplainedAtEpochMs"),
                            linkedConsultationId = d.optNullableLong("linkedConsultationId"),
                            createdAtEpochMs = d.optLong("createdAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    if (oldDocId > 0L) docIdMap[oldDocId] = newDocId
                    totalRestored++
                }

                val medicineIdMap = mutableMapOf<Long, Long>()
                root.optJSONArray("medicines")?.forEachObject { med ->
                    val oldMedId = med.optLong("id", 0L)
                    val oldLinkedDocId = med.optNullableLong("linkedPrescriptionDocId")
                    val mappedDocId = oldLinkedDocId?.let { docIdMap[it] ?: it }
                    val newMedId = database.medicalCareDao().insertMedicine(
                        MedicineEntity(
                            profileId = newProfileId,
                            name = med.optString("name"),
                            dosage = med.optString("dosage"),
                            scheduleTime24h = med.optString("scheduleTime24h", "08:00"),
                            frequency = med.optString("frequency", "Daily"),
                            dosesPerDay = med.optInt("dosesPerDay", 1).coerceIn(1, 6),
                            intervalHours = med.optInt("intervalHours", 24).coerceIn(1, 24),
                            mealRelation = med.optString("mealRelation", "After Meal"),
                            categoryEmoji = med.optString("categoryEmoji", "💊"),
                            isActive = med.optBoolean("isActive", true),
                            notes = med.optString("notes", ""),
                            stockQuantity = med.optDouble("stockQuantity", 0.0).toFloat().coerceAtLeast(0f),
                            stockUnit = med.optString("stockUnit", "tablets"),
                            unitsPerDose = med.optDouble("unitsPerDose", 1.0).toFloat().coerceIn(0.25f, 20f),
                            lowStockThresholdDays = med.optInt("lowStockThresholdDays", 5).coerceIn(1, 30),
                            expiryEpochDay = med.optNullableLong("expiryEpochDay"),
                            linkedPrescriptionDocId = mappedDocId,
                            refillReminderEnabled = med.optBoolean("refillReminderEnabled", true)
                        )
                    )
                    if (oldMedId > 0L) medicineIdMap[oldMedId] = newMedId
                    totalRestored++
                }

                root.optJSONArray("labResults")?.forEachObject { l ->
                    val oldDocId = l.optNullableLong("documentId")
                    database.healthVaultDao().insertLabResult(
                        LabResultEntity(
                            profileId = newProfileId,
                            testName = l.optString("testName"),
                            valueNumeric = if (!l.has("valueNumeric") || l.isNull("valueNumeric")) null else l.optDouble("valueNumeric"),
                            valueText = l.optString("valueText", ""),
                            unit = l.optString("unit", ""),
                            referenceRangeText = l.optString("referenceRangeText", ""),
                            statusFlag = l.optString("statusFlag", "Normal"),
                            recordedEpochDay = l.optLong("recordedEpochDay", DateEpochUtils.todayEpochDay()),
                            labName = l.optString("labName", ""),
                            documentId = oldDocId?.let { docIdMap[it] ?: it },
                            notes = l.optString("notes", ""),
                            createdAtEpochMs = l.optLong("createdAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("allergyRecords")?.forEachObject { a ->
                    database.healthVaultDao().insertAllergy(
                        AllergyRecordEntity(
                            profileId = newProfileId,
                            allergen = a.optString("allergen"),
                            reaction = a.optString("reaction", ""),
                            severity = a.optString("severity", "Moderate"),
                            notes = a.optString("notes", ""),
                            recordedAtEpochMs = a.optLong("recordedAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("medicalHistoryEntries")?.forEachObject { h ->
                    database.healthVaultDao().insertMedicalHistory(
                        MedicalHistoryEntity(
                            profileId = newProfileId,
                            conditionOrEvent = h.optString("conditionOrEvent"),
                            category = h.optString("category", "Condition"),
                            diagnosedYearOrDate = h.optString("diagnosedYearOrDate", ""),
                            status = h.optString("status", "Active"),
                            notes = h.optString("notes", ""),
                            updatedAtEpochMs = h.optLong("updatedAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("medicineRefillLogs")?.forEachObject { r ->
                    val mappedMedId = medicineIdMap[r.optLong("medicineId", 0L)]
                    if (mappedMedId != null) {
                        val oldDocId = r.optNullableLong("prescriptionDocId")
                        database.healthVaultDao().insertRefill(
                            MedicineRefillEntity(
                                profileId = newProfileId,
                                medicineId = mappedMedId,
                                medicineName = r.optString("medicineName"),
                                quantityAdded = r.optDouble("quantityAdded", 0.0).toFloat(),
                                unitLabel = r.optString("unitLabel", "tablets"),
                                purchaseDateEpochDay = r.optLong("purchaseDateEpochDay", DateEpochUtils.todayEpochDay()),
                                expiryEpochDay = r.optNullableLong("expiryEpochDay"),
                                pharmacyOrSource = r.optString("pharmacyOrSource", ""),
                                costNote = r.optString("costNote", ""),
                                prescriptionDocId = oldDocId?.let { docIdMap[it] ?: it },
                                loggedAtEpochMs = r.optLong("loggedAtEpochMs", System.currentTimeMillis())
                            )
                        )
                        totalRestored++
                    }
                }

                root.optJSONArray("medicineIntakes")?.forEachObject { intake ->
                    val mappedMedId = medicineIdMap[intake.optLong("medicineId", 0L)]
                    if (mappedMedId != null) {
                        database.medicalCareDao().insertMedicineIntake(
                            MedicineIntakeEntity(
                                profileId = newProfileId,
                                medicineId = mappedMedId,
                                medicineName = intake.optString("medicineName"),
                                dosageTaken = intake.optString("dosageTaken"),
                                slotLabel = intake.optString("slotLabel"),
                                status = intake.optString("status", "Taken"),
                                dayKey = intake.optString("dayKey", DateEpochUtils.todayDayKey()),
                                takenAtEpochMs = intake.optLong("takenAtEpochMs", System.currentTimeMillis())
                            )
                        )
                        totalRestored++
                    }
                }

                root.optJSONArray("doctorConsultations")?.forEachObject { c ->
                    database.medicalCareDao().insertConsultation(
                        DoctorConsultationEntity(
                            profileId = newProfileId,
                            doctorName = c.optString("doctorName"),
                            specialty = c.optString("specialty"),
                            clinicOrHospital = c.optString("clinicOrHospital", ""),
                            consultationEpochMs = c.optLong("consultationEpochMs", System.currentTimeMillis()),
                            summary = c.optString("summary", ""),
                            followUpEpochDay = c.optNullableLong("followUpEpochDay"),
                            followUpNotes = c.optString("followUpNotes", ""),
                            isFollowUpCompleted = c.optBoolean("isFollowUpCompleted", false)
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("customAlarms")?.forEachObject { a ->
                    val entity = ReminderEntity(
                        profileId = newProfileId,
                        title = a.optString("title", "Wellness Alarm"),
                        moduleKey = a.optString("moduleKey", "wellness"),
                        triggerTime24h = a.optString("triggerTime24h", "09:00"),
                        daysOfWeekMask = a.optInt("daysOfWeekMask", 127),
                        isEnabled = a.optBoolean("isEnabled", true),
                        soundKey = a.optString("soundKey", "water_drop"),
                        customSoundUri = a.optNullableString("customSoundUri"),
                        customSoundLabel = a.optNullableString("customSoundLabel"),
                        alarmEmoji = a.optString("alarmEmoji", "⏰"),
                        customMessage = a.optString("customMessage", ""),
                        gesturePreset = a.optString("gesturePreset", "SWIPE_SLIDE"),
                        repeatIntervalMinutes = a.optInt("repeatIntervalMinutes", 1440).coerceIn(5, 10_080),
                        triggeredCount = a.optInt("triggeredCount", 0),
                        completedCount = a.optInt("completedCount", 0),
                        snoozedCount = a.optInt("snoozedCount", 0),
                        lastTriggeredEpochMs = a.optLong("lastTriggeredEpochMs", 0L)
                    )
                    val newAlarmId = database.reminderDao().insertReminder(entity)
                    restoredReminders += entity.copy(id = newAlarmId)
                    totalRestored++
                }

                root.optJSONArray("exerciseRecords")?.forEachObject { e ->
                    database.fitnessDao().insertExercise(
                        ExerciseRecordEntity(
                            profileId = newProfileId,
                            activityType = e.optString("activityType"),
                            category = e.optString("category", "Exercise"),
                            durationMinutes = e.optInt("durationMinutes", 20),
                            intensity = e.optString("intensity", "Moderate"),
                            caloriesBurned = e.optInt("caloriesBurned", 0),
                            loggedAtEpochMs = e.optLong("loggedAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("sleepRecords")?.forEachObject { s ->
                    database.fitnessDao().insertSleep(
                        SleepRecordEntity(
                            profileId = newProfileId,
                            bedtimeEpochMs = s.optLong("bedtimeEpochMs"),
                            wakeEpochMs = s.optLong("wakeEpochMs"),
                            qualityRating = s.optInt("qualityRating", 3).coerceIn(1, 5),
                            notes = s.optString("notes", "")
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("stepRecords")?.forEachObject { s ->
                    database.fitnessDao().upsertSteps(
                        StepRecordEntity(
                            profileId = newProfileId,
                            dayKey = s.optString("dayKey", DateEpochUtils.todayDayKey()),
                            stepCount = s.optInt("stepCount", 0),
                            distanceMeters = s.optInt("distanceMeters", 0),
                            updatedAtEpochMs = s.optLong("updatedAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("dietRecords")?.forEachObject { d ->
                    database.dietDao().insertDietRecord(
                        DietRecordEntity(
                            profileId = newProfileId,
                            mealType = d.optString("mealType", "Snack"),
                            foodSummary = d.optString("foodSummary"),
                            calories = d.optInt("calories", 0),
                            proteinGrams = d.optInt("proteinGrams", 0),
                            carbsGrams = d.optInt("carbsGrams", 0),
                            fatGrams = d.optInt("fatGrams", 0),
                            loggedAtEpochMs = d.optLong("loggedAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("beautyCareRecords")?.forEachObject { b ->
                    database.beautyCareDao().insertBeautyRecord(
                        BeautyCareRecordEntity(
                            profileId = newProfileId,
                            routineType = b.optString("routineType", "Morning"),
                            productOrStep = b.optString("productOrStep"),
                            completedAtEpochMs = b.optLong("completedAtEpochMs", System.currentTimeMillis()),
                            notes = b.optString("notes", "")
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("digitalWellbeingRecords")?.forEachObject { w ->
                    database.digitalWellbeingDao().upsertWellbeingRecord(
                        DigitalWellbeingRecordEntity(
                            profileId = newProfileId,
                            dayKey = w.optString("dayKey", DateEpochUtils.todayDayKey()),
                            screenTimeMinutes = w.optInt("screenTimeMinutes", 0),
                            focusSessionsCount = w.optInt("focusSessionsCount", 0),
                            mindfulGoalMinutes = w.optInt("mindfulGoalMinutes", 120),
                            unlockCount = w.optInt("unlockCount", 0),
                            appLaunchCount = w.optInt("appLaunchCount", 0),
                            nightScreenMinutes = w.optInt("nightScreenMinutes", 0),
                            longestSessionMinutes = w.optInt("longestSessionMinutes", 0),
                            topAppName = w.optString("topAppName", ""),
                            topAppMinutes = w.optInt("topAppMinutes", 0),
                            topAppsJson = w.optString("topAppsJson", ""),
                            sourceLabel = w.optString("sourceLabel", "Manual"),
                            lastSyncedEpochMs = w.optLong("lastSyncedEpochMs", 0L)
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("connectedFitnessSources")?.forEachObject { src ->
                    database.connectedFitnessDao().upsertSource(
                        ConnectedDeviceSourceEntity(
                            profileId = newProfileId,
                            sourceId = src.optString("sourceId", "imported"),
                            displayName = src.optString("displayName", "Connected Source"),
                            emoji = src.optString("emoji", "⌚"),
                            syncChannel = src.optString("syncChannel", "Backup Import"),
                            lastSyncedEpochMs = src.optLong("lastSyncedEpochMs", System.currentTimeMillis()),
                            recordsImportedCount = src.optInt("recordsImportedCount", 0),
                            latestSummary = src.optString("latestSummary", "")
                        )
                    )
                    totalRestored++
                }

                val vitalBatch = mutableListOf<HealthVitalMetricEntity>()
                root.optJSONArray("healthVitalMetrics")?.forEachObject { m ->
                    vitalBatch += HealthVitalMetricEntity(
                        profileId = newProfileId,
                        metricType = m.optString("metricType", "HEART_RATE"),
                        valuePrimary = m.optDouble("valuePrimary", 0.0),
                        unit = m.optString("unit", ""),
                        sourceId = m.optString("sourceId", "backup"),
                        sourceDisplayName = m.optString("sourceDisplayName", "Restored"),
                        dayKey = m.optString("dayKey", DateEpochUtils.todayDayKey()),
                        recordedAtEpochMs = m.optLong("recordedAtEpochMs", System.currentTimeMillis())
                    )
                }
                if (vitalBatch.isNotEmpty()) {
                    database.connectedFitnessDao().insertVitalMetrics(vitalBatch)
                    totalRestored += vitalBatch.size
                }

                root.optJSONObject("nutritionPreference")?.let { np ->
                    database.nutritionDao().upsertPreference(
                        NutritionPreferenceEntity(
                            profileId = newProfileId,
                            preferredRegion = np.optString("preferredRegion", "SOUTH"),
                            dietaryPreference = np.optString("dietaryPreference", "VEGETARIAN"),
                            nutritionGoal = np.optString("nutritionGoal", "BALANCED"),
                            customCalorieTarget = np.optNullableInt("customCalorieTarget"),
                            avoidVaultAllergens = np.optBoolean("avoidVaultAllergens", true),
                            updatedAtEpochMs = np.optLong("updatedAtEpochMs", System.currentTimeMillis())
                        )
                    )
                }

                root.optJSONArray("nutritionPlans")?.forEachObject { plan ->
                    database.nutritionDao().insertPlan(
                        NutritionPlanEntity(
                            profileId = newProfileId,
                            planTitle = plan.optString("planTitle", "Indian Meal Plan"),
                            regionLabel = plan.optString("regionLabel", "South Indian"),
                            dietaryLabel = plan.optString("dietaryLabel", "Vegetarian"),
                            goalLabel = plan.optString("goalLabel", "Balanced Everyday"),
                            regionalNotes = plan.optString("regionalNotes", ""),
                            totalCalories = plan.optInt("totalCalories", 0),
                            totalProteinGrams = plan.optInt("totalProteinGrams", 0),
                            totalCarbsGrams = plan.optInt("totalCarbsGrams", 0),
                            totalFatGrams = plan.optInt("totalFatGrams", 0),
                            mealsJson = plan.optString("mealsJson", "[]"),
                            groceryHighlightsJson = plan.optString("groceryHighlightsJson", "[]"),
                            avoidedAllergensCsv = plan.optString("avoidedAllergensCsv", ""),
                            isFromCloudAi = plan.optBoolean("isFromCloudAi", false),
                            createdAtEpochMs = plan.optLong("createdAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                val routineIdMap = mutableMapOf<Long, Long>()
                root.optJSONArray("dailyRoutines")?.forEachObject { r ->
                    val oldRoutineId = r.optLong("id", 0L)
                    val newRoutineId = database.wellnessEngagementDao().insertRoutine(
                        DailyRoutineEntity(
                            profileId = newProfileId,
                            title = r.optString("title", "Mindful Routine"),
                            emoji = r.optString("emoji", "🌿"),
                            timeSlot = r.optString("timeSlot", "Morning"),
                            triggerTime24h = r.optString("triggerTime24h", "08:00"),
                            estimatedMinutes = r.optInt("estimatedMinutes", 10).coerceIn(1, 180),
                            stepsSerialized = r.optString("stepsSerialized", ""),
                            linkedModuleKey = r.optString("linkedModuleKey", ""),
                            reminderId = null,
                            reminderEnabled = false,
                            isOptional = r.optBoolean("isOptional", true),
                            isActive = r.optBoolean("isActive", true),
                            sortOrder = r.optInt("sortOrder", 0),
                            createdAtEpochMs = r.optLong("createdAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    if (oldRoutineId > 0L) routineIdMap[oldRoutineId] = newRoutineId
                    totalRestored++
                }

                root.optJSONArray("routineCompletions")?.forEachObject { c ->
                    val mappedRoutineId = routineIdMap[c.optLong("routineId", 0L)]
                    if (mappedRoutineId != null) {
                        database.wellnessEngagementDao().upsertCompletion(
                            RoutineCompletionEntity(
                                profileId = newProfileId,
                                routineId = mappedRoutineId,
                                dayKey = c.optString("dayKey", DateEpochUtils.todayDayKey()),
                                completedStepsMask = c.optInt("completedStepsMask", 1),
                                completedStepsCount = c.optInt("completedStepsCount", 1),
                                totalStepsCount = c.optInt("totalStepsCount", 1),
                                reflectionNote = c.optString("reflectionNote", ""),
                                completedAtEpochMs = c.optLong("completedAtEpochMs", System.currentTimeMillis())
                            )
                        )
                        totalRestored++
                    }
                }

                root.optJSONArray("moodJournalEntries")?.forEachObject { m ->
                    database.wellnessEngagementDao().insertMoodEntry(
                        MoodJournalEntryEntity(
                            profileId = newProfileId,
                            dayKey = m.optString("dayKey", DateEpochUtils.todayDayKey()),
                            moodLabel = m.optString("moodLabel", "Calm"),
                            moodEmoji = m.optString("moodEmoji", "😌"),
                            moodScore = m.optInt("moodScore", 4).coerceIn(1, 5),
                            energyLevel = m.optInt("energyLevel", 3).coerceIn(1, 5),
                            stressLevel = m.optInt("stressLevel", 2).coerceIn(1, 5),
                            tagsCsv = m.optString("tagsCsv", ""),
                            reflectionNote = m.optString("reflectionNote", ""),
                            gratitudeText = m.optString("gratitudeText", ""),
                            loggedAtEpochMs = m.optLong("loggedAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("wellnessChallenges")?.forEachObject { ch ->
                    database.wellnessEngagementDao().upsertChallenge(
                        WellnessChallengeEntity(
                            profileId = newProfileId,
                            challengeKey = ch.optString("challengeKey"),
                            title = ch.optString("title"),
                            emoji = ch.optString("emoji", "🌱"),
                            category = ch.optString("category", "Routines"),
                            description = ch.optString("description", ""),
                            targetDays = ch.optInt("targetDays", 7).coerceIn(1, 90),
                            currentProgressDays = ch.optInt("currentProgressDays", 0).coerceAtLeast(0),
                            status = ch.optString("status", "AVAILABLE"),
                            isOptInOnly = ch.optBoolean("isOptInOnly", true),
                            startDayKey = ch.optString("startDayKey", ""),
                            lastCheckInDayKey = ch.optString("lastCheckInDayKey", ""),
                            completedAtEpochMs = ch.optNullableLong("completedAtEpochMs"),
                            createdAtEpochMs = ch.optLong("createdAtEpochMs", System.currentTimeMillis())
                        )
                    )
                    totalRestored++
                }

                root.optJSONArray("achievementBadges")?.forEachObject { b ->
                    database.wellnessEngagementDao().upsertBadge(
                        AchievementBadgeEntity(
                            profileId = newProfileId,
                            badgeKey = b.optString("badgeKey"),
                            title = b.optString("title"),
                            emoji = b.optString("emoji", "🏅"),
                            description = b.optString("description", ""),
                            category = b.optString("category", "Routines"),
                            earnedAtEpochMs = b.optNullableLong("earnedAtEpochMs"),
                            progressCurrent = b.optInt("progressCurrent", 0),
                            progressTarget = b.optInt("progressTarget", 1)
                        )
                    )
                    totalRestored++
                }

                database.moduleActivationDao().insertDefaultsIfMissing(AppDatabase.defaultModuleCatalog())
                database.moduleActivationDao().unlockAllModulesFree()
            }

            // Apply UI preferences after atomic SQLite commit
            val restoredProfileId = database.userProfileDao().getActiveProfileSnapshot()?.id ?: 0L
            preferencesManager.setActiveProfileId(restoredProfileId)
            preferencesManager.setWelcomeCompleted(true)
            preferencesManager.setPermissionsSetupCompleted(true)
            root.optJSONObject("uiPreferences")?.let { uiPrefs ->
                val theme = ThemeMode.entries.firstOrNull {
                    it.name == uiPrefs.optString("themeMode")
                } ?: ThemeMode.SYSTEM
                val lang = SupportedLanguage.entries.firstOrNull {
                    it.code == uiPrefs.optString("languageCode")
                } ?: SupportedLanguage.ENGLISH
                preferencesManager.setThemeMode(theme)
                preferencesManager.setLanguage(lang)
                preferencesManager.updateWellbeingAlertSettings(
                    enabled = uiPrefs.optBoolean("wellbeingOveruseAlertsEnabled", true),
                    warningPercent = uiPrefs.optInt("wellbeingWarningPercent", 80)
                )
                uiPrefs.optJSONObject("categorySchedules")?.let { schedules ->
                    listOf("water", "period", "wellness").forEach { cat ->
                        schedules.optJSONObject(cat)?.let { toneObj ->
                            preferencesManager.updateReminderSchedule(
                                categoryKey = cat,
                                enabled = toneObj.optBoolean("enabled", false),
                                hour = toneObj.optInt("hour", 9),
                                minute = toneObj.optInt("minute", 0),
                                repeatIntervalMinutes = toneObj.optInt("repeatIntervalMinutes", 1440)
                            )
                            preferencesManager.updateCategoryNotificationStyle(
                                categoryKey = cat,
                                emoji = toneObj.optString("customEmoji", ""),
                                customMessage = toneObj.optString("customMessage", ""),
                                gesturePreset = toneObj.optString("gesturePreset", "SWIPE_SLIDE")
                            )
                        }
                    }
                }
                uiPrefs.optJSONObject("aiDataAuthorization")?.let { authObj ->
                    preferencesManager.updateAiDataAuthorization(
                        AiDataAuthorization(
                            includeHydration = authObj.optBoolean("includeHydration", true),
                            includeSleep = authObj.optBoolean("includeSleep", true),
                            includeFitnessAndVitals = authObj.optBoolean("includeFitnessAndVitals", true),
                            includeNutrition = authObj.optBoolean("includeNutrition", true),
                            includeMedicinesAndVisits = authObj.optBoolean("includeMedicinesAndVisits", true),
                            includeWellbeingAndSelfCare = authObj.optBoolean("includeWellbeingAndSelfCare", true),
                            includeCycle = authObj.optBoolean("includeCycle", true)
                        )
                    )
                }
            }

            BackupRestoreSummary(
                profileName = fullName,
                restoredRecordsCount = totalRestored,
                restoredReminders = restoredReminders
            )
        }
    }

    suspend fun deleteAllLocalData() {
        database.withTransaction {
            clearAllDatabaseTablesLocked()
            database.moduleActivationDao().insertDefaultsIfMissing(AppDatabase.defaultModuleCatalog())
        }
        encryptedDocumentVault?.wipeAllVaultFiles()
        preferencesManager.resetAll()
    }

    private suspend fun clearAllDatabaseTablesLocked() {
        database.waterDao().deleteAllWaterRecords()
        database.periodDao().deleteAllPeriodRecords()
        database.reminderDao().deleteAllReminders()
        database.fitnessDao().deleteAllExercises()
        database.fitnessDao().deleteAllSleep()
        database.fitnessDao().deleteAllSteps()
        database.dietDao().deleteAllDietRecords()
        database.healthVaultDao().deleteAllRefills()
        database.healthVaultDao().deleteAllLabResults()
        database.healthVaultDao().deleteAllAllergies()
        database.healthVaultDao().deleteAllMedicalHistory()
        database.healthVaultDao().deleteAllDocuments()
        database.medicalCareDao().deleteAllMedicineIntakes()
        database.medicalCareDao().deleteAllMedicines()
        database.medicalCareDao().deleteAllConsultations()
        database.beautyCareDao().deleteAllBeautyRecords()
        database.digitalWellbeingDao().deleteAllWellbeingRecords()
        database.connectedFitnessDao().deleteAllVitalMetrics()
        database.connectedFitnessDao().deleteAllSources()
        database.nutritionDao().deleteAllPlans()
        database.nutritionDao().deleteAllPreferences()
        database.wellnessEngagementDao().deleteAllBadges()
        database.wellnessEngagementDao().deleteAllChallenges()
        database.wellnessEngagementDao().deleteAllMoodEntries()
        database.wellnessEngagementDao().deleteAllCompletions()
        database.wellnessEngagementDao().deleteAllRoutines()
        database.subscriptionBillingDao().clearAllEntitlements()
        database.userProfileDao().deleteAllProfiles()
    }

    private inline fun JSONArray.forEachObject(block: (JSONObject) -> Unit) {
        for (i in 0 until length()) {
            optJSONObject(i)?.let(block)
        }
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (!has(key) || isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableInt(key: String): Int? =
        if (!has(key) || isNull(key)) null else optInt(key)

    private fun JSONObject.optNullableFloat(key: String): Float? =
        if (!has(key) || isNull(key)) null else optDouble(key).toFloat()

    private fun JSONObject.optNullableString(key: String): String? =
        if (!has(key) || isNull(key)) null else optString(key)
}
