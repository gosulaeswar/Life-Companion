package com.example.healthsync

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ConnectedDeviceSourceEntity
import com.example.data.local.entity.DietRecordEntity
import com.example.data.local.entity.ExerciseRecordEntity
import com.example.data.local.entity.HealthVitalMetricEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.data.local.entity.WaterRecordEntity
import kotlinx.coroutines.flow.Flow
import kotlin.math.abs

class FitnessSyncRepository(private val database: AppDatabase) {
    private val connectedDao = database.connectedFitnessDao()
    private val fitnessDao = database.fitnessDao()
    private val waterDao = database.waterDao()
    private val dietDao = database.dietDao()
    private val profileDao = database.userProfileDao()

    fun observeConnectedSources(profileId: Long): Flow<List<ConnectedDeviceSourceEntity>> =
        connectedDao.observeConnectedSources(profileId)

    fun observeVitalMetrics(profileId: Long): Flow<List<HealthVitalMetricEntity>> =
        connectedDao.observeVitalMetrics(profileId)

    suspend fun deleteSource(id: Long) = connectedDao.deleteSource(id)

    suspend fun deleteVitalMetric(id: Long) = connectedDao.deleteVitalMetric(id)

    /**
     * Persists synced metrics across Room tables idempotently so repeated syncs
     * enrich rather than duplicate existing readings.
     */
    suspend fun ingestSyncPayload(profileId: Long, payload: HealthSyncPayload): Int {
        if (payload.totalRecordsCount == 0 && payload.sources.isEmpty()) return 0

        var importedCount = 0
        database.withTransaction {
            val now = System.currentTimeMillis()

            // 1. Merge daily steps (preserve highest accurate count for the day)
            for (stepDay in payload.dailySteps) {
                val existing = fitnessDao.getStepsForDaySnapshot(profileId, stepDay.dayKey)
                val mergedSteps = maxOf(existing?.stepCount ?: 0, stepDay.steps)
                val mergedDist = maxOf(existing?.distanceMeters ?: 0, stepDay.distanceMeters)
                fitnessDao.upsertSteps(
                    StepRecordEntity(
                        id = existing?.id ?: 0L,
                        profileId = profileId,
                        dayKey = stepDay.dayKey,
                        stepCount = mergedSteps,
                        distanceMeters = mergedDist,
                        updatedAtEpochMs = now
                    )
                )
                importedCount++
            }

            // 2. Deduplicated workouts
            val existingWorkouts = fitnessDao.getExercisesSnapshot(profileId)
            for (workout in payload.workouts) {
                val alreadyPresent = existingWorkouts.any {
                    abs(it.loggedAtEpochMs - workout.loggedAtEpochMs) < 60_000L &&
                        it.durationMinutes == workout.durationMinutes
                }
                if (!alreadyPresent) {
                    fitnessDao.insertExercise(
                        ExerciseRecordEntity(
                            profileId = profileId,
                            activityType = workout.activityType,
                            category = workout.category,
                            durationMinutes = workout.durationMinutes,
                            intensity = workout.intensity,
                            caloriesBurned = workout.caloriesBurned,
                            loggedAtEpochMs = workout.loggedAtEpochMs
                        )
                    )
                    importedCount++
                }
            }

            // 3. Deduplicated sleep sessions
            val existingSleep = fitnessDao.getSleepSnapshot(profileId)
            for (sleep in payload.sleepSessions) {
                val alreadyPresent = existingSleep.any {
                    abs(it.bedtimeEpochMs - sleep.bedtimeEpochMs) < 120_000L &&
                        abs(it.wakeEpochMs - sleep.wakeEpochMs) < 120_000L
                }
                if (!alreadyPresent) {
                    val durationMin = ((sleep.wakeEpochMs - sleep.bedtimeEpochMs) / 60_000L).toInt().coerceAtLeast(1)
                    val estDeep = (durationMin * 0.24).toInt().coerceAtLeast(0)
                    val estLight = (durationMin - estDeep).coerceAtLeast(0)
                    fitnessDao.insertSleep(
                        SleepRecordEntity(
                            profileId = profileId,
                            bedtimeEpochMs = sleep.bedtimeEpochMs,
                            wakeEpochMs = sleep.wakeEpochMs,
                            qualityRating = sleep.qualityRating,
                            notes = sleep.notes,
                            sleepQualityScore = (sleep.qualityRating * 20).coerceIn(20, 100),
                            deepSleepMinutes = estDeep,
                            lightSleepMinutes = estLight,
                            trackingSource = "Health Connect"
                        )
                    )
                    importedCount++
                }
            }

            // 4. Deduplicated hydration entries
            val existingWater = waterDao.getAllForProfileSnapshot(profileId)
            for (w in payload.waterEntries) {
                val alreadyPresent = existingWater.any {
                    abs(it.loggedAtEpochMs - w.loggedAtEpochMs) < 60_000L &&
                        it.amountMl == w.amountMl
                }
                if (!alreadyPresent) {
                    waterDao.insertRecord(
                        WaterRecordEntity(
                            profileId = profileId,
                            amountMl = w.amountMl,
                            drinkType = w.drinkType,
                            note = "Synced",
                            dayKey = w.dayKey,
                            loggedAtEpochMs = w.loggedAtEpochMs
                        )
                    )
                    importedCount++
                }
            }

            // 4b. Deduplicated nutrition entries (never overwrites manual diet logs)
            val existingDiet = dietDao.getDietRecordsSnapshot(profileId)
            for (meal in payload.nutritionEntries) {
                val alreadyPresent = existingDiet.any {
                    abs(it.loggedAtEpochMs - meal.loggedAtEpochMs) < 120_000L &&
                        it.calories == meal.calories &&
                        it.foodSummary.equals(meal.foodSummary, ignoreCase = true)
                }
                if (!alreadyPresent) {
                    dietDao.insertDietRecord(
                        DietRecordEntity(
                            profileId = profileId,
                            mealType = meal.mealType,
                            foodSummary = meal.foodSummary,
                            calories = meal.calories.coerceAtLeast(0),
                            proteinGrams = meal.proteinGrams.coerceAtLeast(0),
                            carbsGrams = meal.carbsGrams.coerceAtLeast(0),
                            fatGrams = meal.fatGrams.coerceAtLeast(0),
                            loggedAtEpochMs = meal.loggedAtEpochMs
                        )
                    )
                    importedCount++
                }
            }

            // 5. Vital metrics (Heart Rate, Distance, Active Calories, Weight)
            val existingVitals = connectedDao.getVitalMetricsSnapshot(profileId)
            val toInsert = mutableListOf<HealthVitalMetricEntity>()
            for (metric in payload.vitalMetrics) {
                val duplicate = existingVitals.any {
                    it.metricType == metric.metricType &&
                        it.dayKey == metric.dayKey &&
                        abs(it.recordedAtEpochMs - metric.recordedAtEpochMs) < 60_000L &&
                        abs(it.valuePrimary - metric.valuePrimary) < 0.01
                }
                if (!duplicate) {
                    val (displayName, _) = FitnessSourceCatalog.resolveSourceInfo(metric.sourceId)
                    toInsert += HealthVitalMetricEntity(
                        profileId = profileId,
                        metricType = metric.metricType,
                        valuePrimary = metric.valuePrimary,
                        unit = metric.unit,
                        sourceId = metric.sourceId,
                        sourceDisplayName = displayName,
                        dayKey = metric.dayKey,
                        recordedAtEpochMs = metric.recordedAtEpochMs
                    )
                    importedCount++
                }
            }
            if (toInsert.isNotEmpty()) {
                connectedDao.insertVitalMetrics(toInsert)
            }

            // 6. Update profile weight if synced
            payload.latestWeightKg?.let { latestKg ->
                val profile = profileDao.getActiveProfileSnapshot()
                if (profile != null && profile.id == profileId) {
                    profileDao.updateProfile(
                        profile.copy(
                            weightKg = latestKg,
                            updatedAtEpochMs = now
                        )
                    )
                }
            }

            // 7. Upsert connected device/app source metadata
            for (src in payload.sources) {
                val existingSrc = connectedDao.getSourceByKey(profileId, src.sourceId)
                connectedDao.upsertSource(
                    ConnectedDeviceSourceEntity(
                        id = existingSrc?.id ?: 0L,
                        profileId = profileId,
                        sourceId = src.sourceId,
                        displayName = src.displayName,
                        emoji = src.emoji,
                        syncChannel = src.syncChannel,
                        lastSyncedEpochMs = now,
                        recordsImportedCount = (existingSrc?.recordsImportedCount ?: 0) + src.recordsCount,
                        latestSummary = src.summary
                    )
                )
            }
        }
        return importedCount
    }
}
