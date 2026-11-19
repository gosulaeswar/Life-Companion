package com.example.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
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
import com.example.data.local.entity.NutritionPlanEntity
import com.example.data.local.entity.NutritionPreferenceEntity
import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface UserProfileDao {
    @Query("SELECT * FROM user_profiles ORDER BY id ASC LIMIT 1")
    fun observeActiveProfile(): Flow<UserProfileEntity?>

    @Query("SELECT * FROM user_profiles ORDER BY id ASC LIMIT 1")
    suspend fun getActiveProfileSnapshot(): UserProfileEntity?

    @Query("SELECT * FROM user_profiles ORDER BY id ASC")
    fun observeAllProfiles(): Flow<List<UserProfileEntity>>

    @Query("SELECT * FROM user_profiles ORDER BY id ASC")
    suspend fun getAllProfilesSnapshot(): List<UserProfileEntity>

    @Query("SELECT COUNT(*) FROM user_profiles")
    suspend fun getProfileCount(): Int

    @Query("SELECT * FROM user_profiles WHERE id = :profileId LIMIT 1")
    suspend fun getProfileById(profileId: Long): UserProfileEntity?

    @Query("SELECT * FROM user_profiles WHERE id = :profileId LIMIT 1")
    fun observeProfileById(profileId: Long): Flow<UserProfileEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProfile(profile: UserProfileEntity): Long

    @Update
    suspend fun updateProfile(profile: UserProfileEntity)

    @Query("DELETE FROM user_profiles WHERE id = :profileId")
    suspend fun deleteProfileById(profileId: Long)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAiPreference(preference: AiPreferenceEntity)

    @Query("SELECT * FROM ai_preferences WHERE profileId = :profileId LIMIT 1")
    fun observeAiPreference(profileId: Long): Flow<AiPreferenceEntity?>

    @Query("SELECT * FROM ai_preferences WHERE profileId = :profileId LIMIT 1")
    suspend fun getAiPreferenceSnapshot(profileId: Long): AiPreferenceEntity?

    @Query("DELETE FROM user_profiles")
    suspend fun deleteAllProfiles()
}

@Dao
interface WaterDao {
    @Query(
        "SELECT * FROM water_records WHERE profileId = :profileId AND dayKey = :dayKey ORDER BY loggedAtEpochMs DESC"
    )
    fun observeRecordsForDay(profileId: Long, dayKey: String): Flow<List<WaterRecordEntity>>

    @Query(
        "SELECT * FROM water_records WHERE profileId = :profileId AND dayKey >= :startDayKey ORDER BY loggedAtEpochMs DESC"
    )
    fun observeRecordsSince(profileId: Long, startDayKey: String): Flow<List<WaterRecordEntity>>

    @Query("SELECT * FROM water_records WHERE profileId = :profileId ORDER BY loggedAtEpochMs DESC")
    suspend fun getAllForProfileSnapshot(profileId: Long): List<WaterRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: WaterRecordEntity): Long

    @Query("DELETE FROM water_records WHERE id = :recordId")
    suspend fun deleteById(recordId: Long)

    @Query("DELETE FROM water_records")
    suspend fun deleteAllWaterRecords()
}

@Dao
interface PeriodDao {
    @Query("SELECT * FROM period_records WHERE profileId = :profileId ORDER BY startDateEpochDay DESC")
    fun observeAllForProfile(profileId: Long): Flow<List<PeriodRecordEntity>>

    @Query("SELECT * FROM period_records WHERE profileId = :profileId ORDER BY startDateEpochDay DESC")
    suspend fun getAllForProfileSnapshot(profileId: Long): List<PeriodRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecord(record: PeriodRecordEntity): Long

    @Update
    suspend fun updateRecord(record: PeriodRecordEntity)

    @Query("DELETE FROM period_records WHERE id = :recordId")
    suspend fun deleteById(recordId: Long)

    @Query("DELETE FROM period_records")
    suspend fun deleteAllPeriodRecords()
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders WHERE profileId = :profileId ORDER BY triggerTime24h ASC, id ASC")
    fun observeRemindersForProfile(profileId: Long): Flow<List<ReminderEntity>>

    @Query("SELECT * FROM reminders WHERE profileId = :profileId ORDER BY triggerTime24h ASC, id ASC")
    suspend fun getRemindersSnapshot(profileId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :reminderId LIMIT 1")
    suspend fun getReminderById(reminderId: Long): ReminderEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertReminder(reminder: ReminderEntity): Long

    @Update
    suspend fun updateReminder(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :reminderId")
    suspend fun deleteReminderById(reminderId: Long)

    @Query("UPDATE reminders SET triggeredCount = triggeredCount + 1, lastTriggeredEpochMs = :timestampMs WHERE id = :reminderId")
    suspend fun recordAlarmTriggered(reminderId: Long, timestampMs: Long = System.currentTimeMillis())

    @Query("UPDATE reminders SET completedCount = completedCount + 1 WHERE id = :reminderId")
    suspend fun recordAlarmCompleted(reminderId: Long)

    @Query("UPDATE reminders SET snoozedCount = snoozedCount + 1 WHERE id = :reminderId")
    suspend fun recordAlarmSnoozed(reminderId: Long)

    @Query("DELETE FROM reminders")
    suspend fun deleteAllReminders()
}

@Dao
interface FitnessDao {
    @Query("SELECT * FROM exercise_records WHERE profileId = :profileId ORDER BY loggedAtEpochMs DESC")
    fun observeExercises(profileId: Long): Flow<List<ExerciseRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExercise(record: ExerciseRecordEntity): Long

    @Query("DELETE FROM exercise_records WHERE id = :id")
    suspend fun deleteExercise(id: Long)

    @Query("SELECT * FROM sleep_records WHERE profileId = :profileId ORDER BY wakeEpochMs DESC")
    fun observeSleep(profileId: Long): Flow<List<SleepRecordEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSleep(record: SleepRecordEntity): Long

    @Query("DELETE FROM sleep_records WHERE id = :id")
    suspend fun deleteSleep(id: Long)

    @Query("SELECT * FROM step_records WHERE profileId = :profileId AND dayKey = :dayKey LIMIT 1")
    fun observeStepsForDay(profileId: Long, dayKey: String): Flow<StepRecordEntity?>

    @Query("SELECT * FROM step_records WHERE profileId = :profileId ORDER BY dayKey DESC")
    fun observeAllSteps(profileId: Long): Flow<List<StepRecordEntity>>

    @Query("SELECT * FROM step_records WHERE profileId = :profileId AND dayKey = :dayKey LIMIT 1")
    suspend fun getStepsForDaySnapshot(profileId: Long, dayKey: String): StepRecordEntity?

    @Query("SELECT * FROM step_records WHERE profileId = :profileId ORDER BY dayKey DESC")
    suspend fun getAllStepsSnapshot(profileId: Long): List<StepRecordEntity>

    @Query("SELECT * FROM exercise_records WHERE profileId = :profileId ORDER BY loggedAtEpochMs DESC")
    suspend fun getExercisesSnapshot(profileId: Long): List<ExerciseRecordEntity>

    @Query("SELECT * FROM sleep_records WHERE profileId = :profileId ORDER BY wakeEpochMs DESC")
    suspend fun getSleepSnapshot(profileId: Long): List<SleepRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSteps(record: StepRecordEntity): Long

    @Query("DELETE FROM exercise_records")
    suspend fun deleteAllExercises()

    @Query("DELETE FROM sleep_records")
    suspend fun deleteAllSleep()

    @Query("DELETE FROM step_records")
    suspend fun deleteAllSteps()
}

@Dao
interface DietDao {
    @Query("SELECT * FROM diet_records WHERE profileId = :profileId ORDER BY loggedAtEpochMs DESC")
    fun observeDietRecords(profileId: Long): Flow<List<DietRecordEntity>>

    @Query("SELECT * FROM diet_records WHERE profileId = :profileId ORDER BY loggedAtEpochMs DESC")
    suspend fun getDietRecordsSnapshot(profileId: Long): List<DietRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDietRecord(record: DietRecordEntity): Long

    @Query("DELETE FROM diet_records WHERE id = :id")
    suspend fun deleteDietRecord(id: Long)

    @Query("DELETE FROM diet_records")
    suspend fun deleteAllDietRecords()
}

@Dao
interface MedicalCareDao {
    @Query("SELECT * FROM medicines WHERE profileId = :profileId ORDER BY isActive DESC, id DESC")
    fun observeMedicines(profileId: Long): Flow<List<MedicineEntity>>

    @Query("SELECT * FROM medicines WHERE profileId = :profileId ORDER BY id DESC")
    suspend fun getMedicinesSnapshot(profileId: Long): List<MedicineEntity>

    @Query("SELECT * FROM medicines WHERE id = :id LIMIT 1")
    suspend fun getMedicineById(id: Long): MedicineEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicine(medicine: MedicineEntity): Long

    @Update
    suspend fun updateMedicine(medicine: MedicineEntity)

    @Query("DELETE FROM medicines WHERE id = :id")
    suspend fun deleteMedicine(id: Long)

    @Query("SELECT * FROM medicine_intakes WHERE profileId = :profileId AND dayKey = :dayKey ORDER BY takenAtEpochMs DESC")
    fun observeIntakesForDay(profileId: Long, dayKey: String): Flow<List<MedicineIntakeEntity>>

    @Query("SELECT * FROM medicine_intakes WHERE profileId = :profileId ORDER BY takenAtEpochMs DESC LIMIT 30")
    fun observeRecentIntakes(profileId: Long): Flow<List<MedicineIntakeEntity>>

    @Query("SELECT * FROM medicine_intakes WHERE profileId = :profileId AND dayKey >= :startDayKey ORDER BY takenAtEpochMs DESC")
    fun observeIntakesSince(profileId: Long, startDayKey: String): Flow<List<MedicineIntakeEntity>>

    @Query("SELECT * FROM medicine_intakes WHERE profileId = :profileId ORDER BY takenAtEpochMs DESC")
    suspend fun getIntakesSnapshot(profileId: Long): List<MedicineIntakeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMedicineIntake(intake: MedicineIntakeEntity): Long

    @Query("DELETE FROM medicine_intakes WHERE id = :id")
    suspend fun deleteMedicineIntake(id: Long)

    @Query("SELECT * FROM doctor_consultations WHERE profileId = :profileId ORDER BY consultationEpochMs DESC")
    fun observeConsultations(profileId: Long): Flow<List<DoctorConsultationEntity>>

    @Query("SELECT * FROM doctor_consultations WHERE profileId = :profileId ORDER BY consultationEpochMs DESC")
    suspend fun getConsultationsSnapshot(profileId: Long): List<DoctorConsultationEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertConsultation(consultation: DoctorConsultationEntity): Long

    @Update
    suspend fun updateConsultation(consultation: DoctorConsultationEntity)

    @Query("DELETE FROM doctor_consultations WHERE id = :id")
    suspend fun deleteConsultation(id: Long)

    @Query("DELETE FROM medicine_intakes")
    suspend fun deleteAllMedicineIntakes()

    @Query("DELETE FROM medicines")
    suspend fun deleteAllMedicines()

    @Query("DELETE FROM doctor_consultations")
    suspend fun deleteAllConsultations()
}

@Dao
interface BeautyCareDao {
    @Query("SELECT * FROM beauty_care_records WHERE profileId = :profileId ORDER BY completedAtEpochMs DESC")
    fun observeBeautyRecords(profileId: Long): Flow<List<BeautyCareRecordEntity>>

    @Query("SELECT * FROM beauty_care_records WHERE profileId = :profileId ORDER BY completedAtEpochMs DESC")
    suspend fun getBeautyRecordsSnapshot(profileId: Long): List<BeautyCareRecordEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertBeautyRecord(record: BeautyCareRecordEntity): Long

    @Query("DELETE FROM beauty_care_records WHERE id = :id")
    suspend fun deleteBeautyRecord(id: Long)

    @Query("DELETE FROM beauty_care_records")
    suspend fun deleteAllBeautyRecords()
}

@Dao
interface DigitalWellbeingDao {
    @Query("SELECT * FROM digital_wellbeing_records WHERE profileId = :profileId ORDER BY dayKey DESC")
    fun observeWellbeingRecords(profileId: Long): Flow<List<DigitalWellbeingRecordEntity>>

    @Query("SELECT * FROM digital_wellbeing_records WHERE profileId = :profileId ORDER BY dayKey DESC")
    suspend fun getWellbeingRecordsSnapshot(profileId: Long): List<DigitalWellbeingRecordEntity>

    @Query("SELECT * FROM digital_wellbeing_records WHERE profileId = :profileId AND dayKey = :dayKey LIMIT 1")
    fun observeWellbeingForDay(profileId: Long, dayKey: String): Flow<DigitalWellbeingRecordEntity?>

    @Query("SELECT * FROM digital_wellbeing_records WHERE profileId = :profileId AND dayKey = :dayKey LIMIT 1")
    suspend fun getWellbeingForDaySnapshot(profileId: Long, dayKey: String): DigitalWellbeingRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWellbeingRecord(record: DigitalWellbeingRecordEntity): Long

    @Query("DELETE FROM digital_wellbeing_records")
    suspend fun deleteAllWellbeingRecords()
}

@Dao
interface ModuleActivationDao {
    @Query("SELECT * FROM module_activation_status ORDER BY isAlwaysFree DESC, isBuiltInPhaseOne DESC, displayName ASC")
    fun observeModules(): Flow<List<ModuleActivationEntity>>

    @Query("SELECT * FROM module_activation_status")
    suspend fun getAllModulesSnapshot(): List<ModuleActivationEntity>

    @Query("SELECT * FROM module_activation_status WHERE moduleKey = :moduleKey LIMIT 1")
    suspend fun getModuleByKey(moduleKey: String): ModuleActivationEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDefaultsIfMissing(modules: List<ModuleActivationEntity>)

    @Query("UPDATE module_activation_status SET isAlwaysFree = 1, isBuiltInPhaseOne = 1, isActivated = 1")
    suspend fun unlockAllModulesFree()

    @Query(
        """
        UPDATE module_activation_status
        SET isActivated = :isActivated,
            activatedAtEpochMs = :activatedAtEpochMs,
            licenseReference = :licenseReference,
            signatureDigest = :signatureDigest
        WHERE moduleKey = :moduleKey
        """
    )
    suspend fun updateActivationStatus(
        moduleKey: String,
        isActivated: Boolean,
        activatedAtEpochMs: Long?,
        licenseReference: String?,
        signatureDigest: String?
    )
}

@Dao
interface ConnectedFitnessDao {
    @Query("SELECT * FROM connected_fitness_sources WHERE profileId = :profileId ORDER BY lastSyncedEpochMs DESC")
    fun observeConnectedSources(profileId: Long): Flow<List<ConnectedDeviceSourceEntity>>

    @Query("SELECT * FROM connected_fitness_sources WHERE profileId = :profileId ORDER BY lastSyncedEpochMs DESC")
    suspend fun getConnectedSourcesSnapshot(profileId: Long): List<ConnectedDeviceSourceEntity>

    @Query("SELECT * FROM connected_fitness_sources WHERE profileId = :profileId AND sourceId = :sourceId LIMIT 1")
    suspend fun getSourceByKey(profileId: Long, sourceId: String): ConnectedDeviceSourceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSource(source: ConnectedDeviceSourceEntity): Long

    @Query("DELETE FROM connected_fitness_sources WHERE id = :id")
    suspend fun deleteSource(id: Long)

    @Query("DELETE FROM connected_fitness_sources")
    suspend fun deleteAllSources()

    @Query("SELECT * FROM health_vital_metrics WHERE profileId = :profileId ORDER BY recordedAtEpochMs DESC LIMIT 200")
    fun observeVitalMetrics(profileId: Long): Flow<List<HealthVitalMetricEntity>>

    @Query("SELECT * FROM health_vital_metrics WHERE profileId = :profileId ORDER BY recordedAtEpochMs DESC")
    suspend fun getVitalMetricsSnapshot(profileId: Long): List<HealthVitalMetricEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVitalMetrics(metrics: List<HealthVitalMetricEntity>)

    @Query("DELETE FROM health_vital_metrics WHERE id = :id")
    suspend fun deleteVitalMetric(id: Long)

    @Query("DELETE FROM health_vital_metrics")
    suspend fun deleteAllVitalMetrics()
}

@Dao
interface NutritionDao {
    @Query("SELECT * FROM nutrition_preferences WHERE profileId = :profileId LIMIT 1")
    fun observePreference(profileId: Long): Flow<NutritionPreferenceEntity?>

    @Query("SELECT * FROM nutrition_preferences WHERE profileId = :profileId LIMIT 1")
    suspend fun getPreferenceSnapshot(profileId: Long): NutritionPreferenceEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertPreference(preference: NutritionPreferenceEntity): Long

    @Query("DELETE FROM nutrition_preferences")
    suspend fun deleteAllPreferences()

    @Query("SELECT * FROM nutrition_plans WHERE profileId = :profileId ORDER BY createdAtEpochMs DESC LIMIT 1")
    fun observeLatestPlan(profileId: Long): Flow<NutritionPlanEntity?>

    @Query("SELECT * FROM nutrition_plans WHERE profileId = :profileId ORDER BY createdAtEpochMs DESC")
    fun observePlans(profileId: Long): Flow<List<NutritionPlanEntity>>

    @Query("SELECT * FROM nutrition_plans WHERE profileId = :profileId ORDER BY createdAtEpochMs DESC")
    suspend fun getPlansSnapshot(profileId: Long): List<NutritionPlanEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlan(plan: NutritionPlanEntity): Long

    @Query("DELETE FROM nutrition_plans WHERE id = :id")
    suspend fun deletePlan(id: Long)

    @Query("DELETE FROM nutrition_plans")
    suspend fun deleteAllPlans()
}

