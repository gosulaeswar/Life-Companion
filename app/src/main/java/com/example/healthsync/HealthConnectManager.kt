package com.example.healthsync

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.RemoteException
import androidx.annotation.RequiresApi
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.HydrationRecord
import androidx.health.connect.client.records.MealType
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.example.domain.DateEpochUtils
import java.io.IOException
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.roundToInt

class HealthConnectManager(private val context: Context) {

    companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

        val PERMISSION_STEPS = HealthPermission.getReadPermission(StepsRecord::class)
        val PERMISSION_DISTANCE = HealthPermission.getReadPermission(DistanceRecord::class)
        val PERMISSION_TOTAL_CALORIES = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
        val PERMISSION_ACTIVE_CALORIES = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
        val PERMISSION_EXERCISE = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
        val PERMISSION_SLEEP = HealthPermission.getReadPermission(SleepSessionRecord::class)
        val PERMISSION_HEART_RATE = HealthPermission.getReadPermission(HeartRateRecord::class)
        val PERMISSION_HYDRATION = HealthPermission.getReadPermission(HydrationRecord::class)
        val PERMISSION_NUTRITION = HealthPermission.getReadPermission(NutritionRecord::class)
        val PERMISSION_WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)

        val REQUIRED_READ_PERMISSIONS: Set<String> = setOf(
            PERMISSION_STEPS,
            PERMISSION_DISTANCE,
            PERMISSION_TOTAL_CALORIES,
            PERMISSION_ACTIVE_CALORIES,
            PERMISSION_EXERCISE,
            PERMISSION_SLEEP,
            PERMISSION_HEART_RATE,
            PERMISSION_HYDRATION,
            PERMISSION_NUTRITION,
            PERMISSION_WEIGHT
        )

        fun permissionsForCategory(category: HealthConnectDataCategory): Set<String> = when (category) {
            HealthConnectDataCategory.STEPS_AND_ACTIVITY ->
                setOf(PERMISSION_STEPS, PERMISSION_DISTANCE, PERMISSION_ACTIVE_CALORIES, PERMISSION_TOTAL_CALORIES)
            HealthConnectDataCategory.WORKOUTS -> setOf(PERMISSION_EXERCISE)
            HealthConnectDataCategory.SLEEP -> setOf(PERMISSION_SLEEP)
            HealthConnectDataCategory.HEART_RATE -> setOf(PERMISSION_HEART_RATE)
            HealthConnectDataCategory.HYDRATION -> setOf(PERMISSION_HYDRATION)
            HealthConnectDataCategory.NUTRITION -> setOf(PERMISSION_NUTRITION)
            HealthConnectDataCategory.WEIGHT -> setOf(PERMISSION_WEIGHT)
        }

        fun permissionsForCategories(categories: Set<HealthConnectDataCategory>): Set<String> =
            categories.flatMap { permissionsForCategory(it) }.toSet()

        fun isCategoryOsGranted(
            category: HealthConnectDataCategory,
            grantedPermissions: Set<String>
        ): Boolean = permissionsForCategory(category).any { it in grantedPermissions }

        fun mapNutritionRecordToImported(rec: NutritionRecord): ImportedNutrition? {
            val kcal = rec.energy?.inKilocalories?.roundToInt() ?: 0
            val protein = rec.protein?.inGrams?.roundToInt() ?: 0
            val carbs = rec.totalCarbohydrate?.inGrams?.roundToInt() ?: 0
            val fat = rec.totalFat?.inGrams?.roundToInt() ?: 0
            if (kcal <= 0 && protein <= 0 && carbs <= 0 && fat <= 0) return null

            val pkg = rec.metadata.dataOrigin.packageName
            val mealTypeLabel = when (rec.mealType) {
                MealType.MEAL_TYPE_BREAKFAST -> "Breakfast"
                MealType.MEAL_TYPE_LUNCH -> "Lunch"
                MealType.MEAL_TYPE_DINNER -> "Dinner"
                else -> "Snack"
            }
            val (sourceName, _) = FitnessSourceCatalog.resolveSourceInfo(pkg)
            val baseName = rec.name?.trim()?.takeIf { it.isNotBlank() } ?: "$mealTypeLabel entry"
            return ImportedNutrition(
                mealType = mealTypeLabel,
                foodSummary = "⌚ $baseName [Health Connect • $sourceName]",
                calories = kcal.coerceAtLeast(0),
                proteinGrams = protein.coerceAtLeast(0),
                carbsGrams = carbs.coerceAtLeast(0),
                fatGrams = fat.coerceAtLeast(0),
                loggedAtEpochMs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    rec.startTime.toEpochMilli()
                } else {
                    System.currentTimeMillis()
                },
                sourceId = pkg.ifBlank { "health_connect" }
            )
        }
    }

    fun checkAvailability(): HealthConnectAvailability {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return HealthConnectAvailability.REQUIRES_ANDROID_8
        }
        return when (HealthConnectClient.getSdkStatus(context, HEALTH_CONNECT_PACKAGE)) {
            HealthConnectClient.SDK_AVAILABLE -> HealthConnectAvailability.AVAILABLE
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED ->
                HealthConnectAvailability.UPDATE_OR_INSTALL_REQUIRED
            else -> HealthConnectAvailability.UNAVAILABLE
        }
    }

    fun buildInstallOrUpdateIntent(): Intent {
        val uriString =
            "market://details?id=$HEALTH_CONNECT_PACKAGE&url=healthconnect%3A%2F%2Fonboarding"
        return Intent(Intent.ACTION_VIEW).apply {
            setPackage("com.android.vending")
            data = Uri.parse(uriString)
            putExtra("overlay", true)
            putExtra("callerId", context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun buildSettingsIntent(): Intent {
        val action = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS
        } else {
            HealthConnectClient.ACTION_HEALTH_CONNECT_SETTINGS
        }
        return Intent(action).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    suspend fun getGrantedPermissions(): Set<String> {
        if (checkAvailability() != HealthConnectAvailability.AVAILABLE) return emptySet()
        return runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())
    }

    suspend fun readRecentMetrics(
        daysBack: Int = 7,
        enabledCategories: Set<HealthConnectDataCategory> = HealthConnectDataCategory.entries.toSet()
    ): Result<HealthSyncPayload> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Result.failure(IllegalStateException("Health Connect requires Android 8.0 or newer."))
        }
        if (enabledCategories.isEmpty()) {
            return Result.failure(IllegalStateException("Enable at least one Health Connect import category first."))
        }
        val status = checkAvailability()
        if (status != HealthConnectAvailability.AVAILABLE) {
            return Result.failure(
                IllegalStateException(
                    "Health Connect is not active yet on this device. Install or update Health Connect, or import an Apple Health / fitness export file."
                )
            )
        }
        return readRecentMetricsApi26(daysBack, enabledCategories)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun readRecentMetricsApi26(
        daysBack: Int,
        enabledCategories: Set<HealthConnectDataCategory>
    ): Result<HealthSyncPayload> {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            val requestedAllowed = permissionsForCategories(enabledCategories)
            if (granted.intersect(requestedAllowed).isEmpty()) {
                return Result.failure(
                    SecurityException("Please grant Health Connect read permissions for at least one enabled category first.")
                )
            }

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val now = Instant.now()
            val rangeStart = today.minusDays((daysBack - 1).coerceAtLeast(0).toLong())
                .atStartOfDay(zone)
                .toInstant()

            val allowActivity = HealthConnectDataCategory.STEPS_AND_ACTIVITY in enabledCategories
            val allowExercise = HealthConnectDataCategory.WORKOUTS in enabledCategories
            val allowSleep = HealthConnectDataCategory.SLEEP in enabledCategories
            val allowHr = HealthConnectDataCategory.HEART_RATE in enabledCategories
            val allowHydration = HealthConnectDataCategory.HYDRATION in enabledCategories
            val allowNutrition = HealthConnectDataCategory.NUTRITION in enabledCategories
            val allowWeight = HealthConnectDataCategory.WEIGHT in enabledCategories

            val sourceCounter = linkedMapOf<String, Int>()
            fun recordOrigin(pkg: String, count: Int = 1) {
                val key = pkg.ifBlank { "health_connect" }
                sourceCounter[key] = (sourceCounter[key] ?: 0) + count
            }

            val dailySteps = mutableListOf<ImportedDailySteps>()
            val vitals = mutableListOf<ImportedVitalMetric>()

            // 1. Aggregate Steps, Distance, and Calories per day to prevent double-counting across apps
            if (allowActivity) {
                for (offset in 0 until daysBack) {
                    val date = today.minusDays(offset.toLong())
                    val dayStart = date.atStartOfDay(zone).toInstant()
                    val dayEnd = if (offset == 0) now else date.plusDays(1).atStartOfDay(zone).toInstant()
                    if (!dayEnd.isAfter(dayStart)) continue

                    val dayKey = DateEpochUtils.todayDayKey(dayStart.toEpochMilli() + 43_200_000L)
                    val metricsToRequest = buildSet {
                        if (PERMISSION_STEPS in granted) add(StepsRecord.COUNT_TOTAL)
                        if (PERMISSION_DISTANCE in granted) add(DistanceRecord.DISTANCE_TOTAL)
                        if (PERMISSION_ACTIVE_CALORIES in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                        if (PERMISSION_TOTAL_CALORIES in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
                    }
                    if (metricsToRequest.isNotEmpty()) {
                        val agg = client.aggregate(
                            AggregateRequest(
                                metrics = metricsToRequest,
                                timeRangeFilter = TimeRangeFilter.between(dayStart, dayEnd)
                            )
                        )
                        val primaryOrigin = agg.dataOrigins.firstOrNull()?.packageName ?: "health_connect"
                        agg.dataOrigins.forEach { recordOrigin(it.packageName, 1) }

                        val steps = agg[StepsRecord.COUNT_TOTAL]?.toInt() ?: 0
                        val distMeters = agg[DistanceRecord.DISTANCE_TOTAL]?.inMeters?.toInt() ?: 0
                        if (steps > 0 || distMeters > 0) {
                            dailySteps += ImportedDailySteps(
                                dayKey = dayKey,
                                steps = steps,
                                distanceMeters = distMeters,
                                sourceId = primaryOrigin
                            )
                        }
                        if (distMeters > 0) {
                            vitals += ImportedVitalMetric(
                                metricType = "DISTANCE",
                                valuePrimary = distMeters.toDouble(),
                                unit = "m",
                                sourceId = primaryOrigin,
                                dayKey = dayKey,
                                recordedAtEpochMs = dayEnd.toEpochMilli()
                            )
                        }
                        val activeKcal = agg[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories
                            ?: agg[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                        if (activeKcal != null && activeKcal > 1.0) {
                            vitals += ImportedVitalMetric(
                                metricType = "ACTIVE_CALORIES",
                                valuePrimary = activeKcal,
                                unit = "kcal",
                                sourceId = primaryOrigin,
                                dayKey = dayKey,
                                recordedAtEpochMs = dayEnd.toEpochMilli()
                            )
                        }
                    }
                }
            }

            // 2. Paginated Exercise Sessions
            val workouts = mutableListOf<ImportedWorkout>()
            if (allowExercise && PERMISSION_EXERCISE in granted) {
                readAllPages(client, ExerciseSessionRecord::class, rangeStart, now).forEach { rec ->
                    val pkg = rec.metadata.dataOrigin.packageName
                    recordOrigin(pkg)
                    val mins = ChronoUnit.MINUTES.between(rec.startTime, rec.endTime).toInt().coerceIn(1, 480)
                    val label = mapExerciseType(rec.exerciseType, rec.title)
                    val isYoga = rec.exerciseType == ExerciseSessionRecord.EXERCISE_TYPE_YOGA ||
                        label.contains("yoga", ignoreCase = true) ||
                        label.contains("pilates", ignoreCase = true)
                    workouts += ImportedWorkout(
                        activityType = label,
                        category = if (isYoga) "Yoga" else "Exercise",
                        durationMinutes = mins,
                        intensity = if (mins >= 45) "Vigorous" else "Moderate",
                        caloriesBurned = mins * if (isYoga) 4 else 7,
                        loggedAtEpochMs = rec.startTime.toEpochMilli(),
                        sourceId = pkg
                    )
                }
            }

            // 3. Paginated Sleep Sessions
            val sleeps = mutableListOf<ImportedSleep>()
            if (allowSleep && PERMISSION_SLEEP in granted) {
                readAllPages(client, SleepSessionRecord::class, rangeStart, now).forEach { rec ->
                    val pkg = rec.metadata.dataOrigin.packageName
                    recordOrigin(pkg)
                    val hrs = (rec.endTime.toEpochMilli() - rec.startTime.toEpochMilli()) / 3_600_000f
                    val (name, _) = FitnessSourceCatalog.resolveSourceInfo(pkg)
                    sleeps += ImportedSleep(
                        bedtimeEpochMs = rec.startTime.toEpochMilli(),
                        wakeEpochMs = rec.endTime.toEpochMilli(),
                        qualityRating = when {
                            hrs >= 7.5f -> 5
                            hrs >= 6.5f -> 4
                            hrs >= 5.5f -> 3
                            else -> 2
                        },
                        notes = "Synced from $name",
                        sourceId = pkg
                    )
                }
            }

            // 4. Paginated Heart Rate Records (average sample per record)
            if (allowHr && PERMISSION_HEART_RATE in granted) {
                readAllPages(client, HeartRateRecord::class, rangeStart, now).forEach { rec ->
                    if (rec.samples.isNotEmpty()) {
                        val pkg = rec.metadata.dataOrigin.packageName
                        recordOrigin(pkg)
                        val avgBpm = rec.samples.map { it.beatsPerMinute }.average()
                        val ts = rec.endTime.toEpochMilli()
                        vitals += ImportedVitalMetric(
                            metricType = "HEART_RATE",
                            valuePrimary = avgBpm,
                            unit = "bpm",
                            sourceId = pkg,
                            dayKey = DateEpochUtils.todayDayKey(ts),
                            recordedAtEpochMs = ts
                        )
                    }
                }
            }

            // 5. Paginated Hydration Records
            val waters = mutableListOf<ImportedWater>()
            if (allowHydration && PERMISSION_HYDRATION in granted) {
                readAllPages(client, HydrationRecord::class, rangeStart, now).forEach { rec ->
                    val ml = rec.volume.inMilliliters.toInt()
                    if (ml > 0) {
                        val pkg = rec.metadata.dataOrigin.packageName
                        recordOrigin(pkg)
                        val ts = rec.startTime.toEpochMilli()
                        waters += ImportedWater(
                            amountMl = ml,
                            drinkType = "Synced Water",
                            dayKey = DateEpochUtils.todayDayKey(ts),
                            loggedAtEpochMs = ts,
                            sourceId = pkg
                        )
                    }
                }
            }

            // 6. Paginated Nutrition Records
            val nutritionList = mutableListOf<ImportedNutrition>()
            if (allowNutrition && PERMISSION_NUTRITION in granted) {
                readAllPages(client, NutritionRecord::class, rangeStart, now).forEach { rec ->
                    mapNutritionRecordToImported(rec)?.let { importedMeal ->
                        recordOrigin(importedMeal.sourceId)
                        nutritionList += importedMeal
                    }
                }
            }

            // 7. Paginated Weight Records
            var latestWeight: Float? = null
            var latestWeightTs = 0L
            if (allowWeight && PERMISSION_WEIGHT in granted) {
                readAllPages(client, WeightRecord::class, rangeStart, now).forEach { rec ->
                    val kg = rec.weight.inKilograms.toFloat()
                    if (kg > 10f) {
                        val pkg = rec.metadata.dataOrigin.packageName
                        recordOrigin(pkg)
                        val ts = rec.time.toEpochMilli()
                        if (ts >= latestWeightTs) {
                            latestWeightTs = ts
                            latestWeight = kg
                        }
                        vitals += ImportedVitalMetric(
                            metricType = "WEIGHT",
                            valuePrimary = kg.toDouble(),
                            unit = "kg",
                            sourceId = pkg,
                            dayKey = DateEpochUtils.todayDayKey(ts),
                            recordedAtEpochMs = ts
                        )
                    }
                }
            }

            val sources = sourceCounter.entries.map { (pkg, count) ->
                val (name, emoji) = FitnessSourceCatalog.resolveSourceInfo(pkg)
                ImportedSourceStat(
                    sourceId = pkg,
                    displayName = name,
                    emoji = emoji,
                    syncChannel = "Health Connect",
                    recordsCount = count,
                    summary = "$count metric group(s) synced over last $daysBack days"
                )
            }

            Result.success(
                HealthSyncPayload(
                    dailySteps = dailySteps,
                    workouts = workouts,
                    sleepSessions = sleeps,
                    waterEntries = waters,
                    nutritionEntries = nutritionList,
                    vitalMetrics = vitals,
                    latestWeightKg = latestWeight,
                    sources = sources
                )
            )
        } catch (e: SecurityException) {
            Result.failure(e)
        } catch (e: IllegalStateException) {
            Result.failure(e)
        } catch (e: IOException) {
            Result.failure(e)
        } catch (e: RemoteException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun <T : androidx.health.connect.client.records.Record> readAllPages(
        client: HealthConnectClient,
        recordType: kotlin.reflect.KClass<T>,
        start: Instant,
        end: Instant
    ): List<T> {
        val collected = mutableListOf<T>()
        var pageToken: String? = null
        var guard = 0
        do {
            val response = client.readRecords(
                ReadRecordsRequest(
                    recordType = recordType,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    pageToken = pageToken
                )
            )
            collected += response.records
            pageToken = response.pageToken
            guard++
        } while (!pageToken.isNullOrEmpty() && guard < 15)
        return collected
    }

    private fun mapExerciseType(exerciseType: Int, title: String?): String {
        if (!title.isNullOrBlank()) return title.trim()
        return when (exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING -> "🚶 Walking"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING -> "🏃 Running"
            ExerciseSessionRecord.EXERCISE_TYPE_YOGA -> "🧘 Yoga"
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING -> "🚴 Cycling"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER -> "🏊 Swimming"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING -> "🏋️ Strength Training"
            ExerciseSessionRecord.EXERCISE_TYPE_PILATES -> "🧘 Pilates"
            ExerciseSessionRecord.EXERCISE_TYPE_HIKING -> "🥾 Hiking"
            else -> "🏅 Connected Workout"
        }
    }
}
