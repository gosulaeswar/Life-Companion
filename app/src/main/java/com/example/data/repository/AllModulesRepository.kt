package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.AiPreferenceEntity
import com.example.data.local.entity.BeautyCareRecordEntity
import com.example.data.local.entity.DietRecordEntity
import com.example.data.local.entity.DigitalWellbeingRecordEntity
import com.example.data.local.entity.DoctorConsultationEntity
import com.example.data.local.entity.ExerciseRecordEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.MedicineIntakeEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.domain.DateEpochUtils
import kotlinx.coroutines.flow.Flow

class AllModulesRepository(private val database: AppDatabase) {
    private val fitnessDao = database.fitnessDao()
    private val dietDao = database.dietDao()
    private val medicalDao = database.medicalCareDao()
    private val beautyDao = database.beautyCareDao()
    private val wellbeingDao = database.digitalWellbeingDao()
    private val profileDao = database.userProfileDao()

    fun observeExercises(profileId: Long): Flow<List<ExerciseRecordEntity>> =
        fitnessDao.observeExercises(profileId)

    fun observeSleep(profileId: Long): Flow<List<SleepRecordEntity>> =
        fitnessDao.observeSleep(profileId)

    fun observeTodaySteps(profileId: Long): Flow<StepRecordEntity?> =
        fitnessDao.observeStepsForDay(profileId, DateEpochUtils.todayDayKey())

    fun observeAllSteps(profileId: Long): Flow<List<StepRecordEntity>> =
        fitnessDao.observeAllSteps(profileId)

    fun observeDiet(profileId: Long): Flow<List<DietRecordEntity>> =
        dietDao.observeDietRecords(profileId)

    fun observeMedicines(profileId: Long): Flow<List<MedicineEntity>> =
        medicalDao.observeMedicines(profileId)

    fun observeTodayMedicineIntakes(profileId: Long): Flow<List<MedicineIntakeEntity>> =
        medicalDao.observeIntakesForDay(profileId, DateEpochUtils.todayDayKey())

    fun observeRecentMedicineIntakes(profileId: Long): Flow<List<MedicineIntakeEntity>> =
        medicalDao.observeRecentIntakes(profileId)

    fun observeConsultations(profileId: Long): Flow<List<DoctorConsultationEntity>> =
        medicalDao.observeConsultations(profileId)

    fun observeBeautyCare(profileId: Long): Flow<List<BeautyCareRecordEntity>> =
        beautyDao.observeBeautyRecords(profileId)

    fun observeTodayWellbeing(profileId: Long): Flow<DigitalWellbeingRecordEntity?> =
        wellbeingDao.observeWellbeingForDay(profileId, DateEpochUtils.todayDayKey())

    fun observeAllWellbeing(profileId: Long): Flow<List<DigitalWellbeingRecordEntity>> =
        wellbeingDao.observeWellbeingRecords(profileId)

    fun observeAiPreference(profileId: Long): Flow<AiPreferenceEntity?> =
        profileDao.observeAiPreference(profileId)

    suspend fun logExercise(
        profileId: Long,
        activityType: String,
        category: String,
        durationMinutes: Int,
        intensity: String,
        caloriesBurned: Int,
        loggedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        fitnessDao.insertExercise(
            ExerciseRecordEntity(
                profileId = profileId,
                activityType = activityType,
                category = category,
                durationMinutes = durationMinutes.coerceIn(1, 360),
                intensity = intensity,
                caloriesBurned = caloriesBurned.coerceAtLeast(0),
                loggedAtEpochMs = loggedAtEpochMs
            )
        )
    }

    suspend fun deleteExercise(id: Long) = fitnessDao.deleteExercise(id)

    suspend fun logSleep(
        profileId: Long,
        hoursSlept: Float,
        qualityRating: Int,
        notes: String,
        wakeEpochMs: Long = System.currentTimeMillis()
    ) {
        val bedMs = wakeEpochMs - (hoursSlept.coerceIn(1f, 16f) * 3_600_000L).toLong()
        fitnessDao.insertSleep(
            SleepRecordEntity(
                profileId = profileId,
                bedtimeEpochMs = bedMs,
                wakeEpochMs = wakeEpochMs,
                qualityRating = qualityRating.coerceIn(1, 5),
                notes = notes.trim()
            )
        )
    }

    suspend fun deleteSleep(id: Long) = fitnessDao.deleteSleep(id)

    suspend fun addTodaySteps(profileId: Long, existing: StepRecordEntity?, deltaSteps: Int) {
        val nextCount = ((existing?.stepCount ?: 0) + deltaSteps).coerceIn(0, 100_000)
        fitnessDao.upsertSteps(
            StepRecordEntity(
                id = existing?.id ?: 0L,
                profileId = profileId,
                dayKey = DateEpochUtils.todayDayKey(),
                stepCount = nextCount,
                distanceMeters = (nextCount * 0.74).toInt(),
                updatedAtEpochMs = System.currentTimeMillis()
            )
        )
    }

    suspend fun logMeal(
        profileId: Long,
        mealType: String,
        foodSummary: String,
        calories: Int,
        proteinGrams: Int,
        carbsGrams: Int,
        fatGrams: Int,
        loggedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        dietDao.insertDietRecord(
            DietRecordEntity(
                profileId = profileId,
                mealType = mealType,
                foodSummary = foodSummary.trim(),
                calories = calories.coerceAtLeast(0),
                proteinGrams = proteinGrams.coerceAtLeast(0),
                carbsGrams = carbsGrams.coerceAtLeast(0),
                fatGrams = fatGrams.coerceAtLeast(0),
                loggedAtEpochMs = loggedAtEpochMs
            )
        )
    }

    suspend fun deleteMeal(id: Long) = dietDao.deleteDietRecord(id)

    suspend fun addMedicine(
        profileId: Long,
        name: String,
        dosage: String,
        scheduleTime24h: String,
        frequency: String,
        dosesPerDay: Int = 1,
        intervalHours: Int = 24,
        mealRelation: String = "After Meal",
        categoryEmoji: String = "💊",
        notes: String = ""
    ) {
        medicalDao.insertMedicine(
            MedicineEntity(
                profileId = profileId,
                name = name.trim(),
                dosage = dosage.trim(),
                scheduleTime24h = scheduleTime24h,
                frequency = frequency,
                dosesPerDay = dosesPerDay.coerceIn(1, 6),
                intervalHours = intervalHours.coerceIn(1, 24),
                mealRelation = mealRelation,
                categoryEmoji = categoryEmoji,
                isActive = true,
                notes = notes.trim()
            )
        )
    }

    suspend fun toggleMedicine(medicine: MedicineEntity, active: Boolean) {
        medicalDao.updateMedicine(medicine.copy(isActive = active))
    }

    suspend fun deleteMedicine(id: Long) = medicalDao.deleteMedicine(id)

    suspend fun logMedicineIntake(
        profileId: Long,
        medicine: MedicineEntity,
        slotLabel: String,
        status: String = "Taken",
        takenAtEpochMs: Long = System.currentTimeMillis()
    ) {
        medicalDao.insertMedicineIntake(
            MedicineIntakeEntity(
                profileId = profileId,
                medicineId = medicine.id,
                medicineName = medicine.name,
                dosageTaken = medicine.dosage,
                slotLabel = slotLabel,
                status = status,
                dayKey = DateEpochUtils.todayDayKey(takenAtEpochMs),
                takenAtEpochMs = takenAtEpochMs
            )
        )
    }

    suspend fun deleteMedicineIntake(id: Long) = medicalDao.deleteMedicineIntake(id)

    suspend fun addConsultation(
        profileId: Long,
        doctorName: String,
        specialty: String,
        clinicOrHospital: String = "",
        summary: String,
        followUpEpochDay: Long? = null,
        followUpNotes: String = "",
        consultationEpochMs: Long = System.currentTimeMillis()
    ) {
        medicalDao.insertConsultation(
            DoctorConsultationEntity(
                profileId = profileId,
                doctorName = doctorName.trim(),
                specialty = specialty.trim(),
                clinicOrHospital = clinicOrHospital.trim(),
                consultationEpochMs = consultationEpochMs,
                summary = summary.trim(),
                followUpEpochDay = followUpEpochDay,
                followUpNotes = followUpNotes.trim(),
                isFollowUpCompleted = false
            )
        )
    }

    suspend fun toggleConsultationFollowUp(consultation: DoctorConsultationEntity, completed: Boolean) {
        medicalDao.updateConsultation(consultation.copy(isFollowUpCompleted = completed))
    }

    suspend fun deleteConsultation(id: Long) = medicalDao.deleteConsultation(id)

    suspend fun logBeautyCare(
        profileId: Long,
        routineType: String,
        productOrStep: String,
        notes: String,
        completedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        beautyDao.insertBeautyRecord(
            BeautyCareRecordEntity(
                profileId = profileId,
                routineType = routineType,
                productOrStep = productOrStep.trim(),
                completedAtEpochMs = completedAtEpochMs,
                notes = notes.trim()
            )
        )
    }

    suspend fun deleteBeautyCare(id: Long) = beautyDao.deleteBeautyRecord(id)

    suspend fun updateDigitalWellbeing(
        profileId: Long,
        existing: DigitalWellbeingRecordEntity?,
        addScreenMinutes: Int = 0,
        addFocusSession: Int = 0,
        goalMinutes: Int? = null
    ) {
        val screen = ((existing?.screenTimeMinutes ?: 0) + addScreenMinutes).coerceIn(0, 1440)
        val focus = ((existing?.focusSessionsCount ?: 0) + addFocusSession).coerceAtLeast(0)
        val goal = (goalMinutes ?: existing?.mindfulGoalMinutes ?: 120).coerceIn(30, 600)
        wellbeingDao.upsertWellbeingRecord(
            DigitalWellbeingRecordEntity(
                id = existing?.id ?: 0L,
                profileId = profileId,
                dayKey = DateEpochUtils.todayDayKey(),
                screenTimeMinutes = screen,
                focusSessionsCount = focus,
                mindfulGoalMinutes = goal
            )
        )
    }

    suspend fun updateAiPreference(preference: AiPreferenceEntity) {
        profileDao.upsertAiPreference(preference.copy(updatedAtEpochMs = System.currentTimeMillis()))
    }
}
