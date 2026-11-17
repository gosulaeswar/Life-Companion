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

class HealthConnectManager(private val context: Context) {

    companion object {
        const val HEALTH_CONNECT_PACKAGE = "com.google.android.apps.healthdata"

        val REQUIRED_READ_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getReadPermission(StepsRecord::class),
            HealthPermission.getReadPermission(DistanceRecord::class),
            HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class),
            HealthPermission.getReadPermission(ExerciseSessionRecord::class),
            HealthPermission.getReadPermission(SleepSessionRecord::class),
            HealthPermission.getReadPermission(HeartRateRecord::class),
            HealthPermission.getReadPermission(HydrationRecord::class),
            HealthPermission.getReadPermission(WeightRecord::class)
        )
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

    suspend fun readRecentMetrics(daysBack: Int = 7): Result<HealthSyncPayload> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return Result.failure(IllegalStateException("Health Connect requires Android 8.0 or newer."))
        }
        val status = checkAvailability()
        if (status != HealthConnectAvailability.AVAILABLE) {
            return Result.failure(
                IllegalStateException(
                    "Health Connect is not active yet on this device. Install or update Health Connect, or import an Apple Health / fitness export file."
                )
            )
        }
        return readRecentMetricsApi26(daysBack)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    private suspend fun readRecentMetricsApi26(daysBack: Int): Result<HealthSyncPayload> {
        return try {
            val client = HealthConnectClient.getOrCreate(context)
            val granted = client.permissionController.getGrantedPermissions()
            if (granted.intersect(REQUIRED_READ_PERMISSIONS).isEmpty()) {
                return Result.failure(
                    SecurityException("Please grant Health Connect read permissions first.")
                )
            }

            val zone = ZoneId.systemDefault()
            val today = LocalDate.now(zone)
            val now = Instant.now()
            val rangeStart = today.minusDays((daysBack - 1).coerceAtLeast(0).toLong())
                .atStartOfDay(zone)
                .toInstant()

            val stepsPerm = HealthPermission.getReadPermission(StepsRecord::class)
            val distancePerm = HealthPermission.getReadPermission(DistanceRecord::class)
            val activeCalPerm = HealthPermission.getReadPermission(ActiveCaloriesBurnedRecord::class)
            val totalCalPerm = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
            val exercisePerm = HealthPermission.getReadPermission(ExerciseSessionRecord::class)
            val sleepPerm = HealthPermission.getReadPermission(SleepSessionRecord::class)
            val hrPerm = HealthPermission.getReadPermission(HeartRateRecord::class)
            val hydrationPerm = HealthPermission.getReadPermission(HydrationRecord::class)
            val weightPerm = HealthPermission.getReadPermission(WeightRecord::class)

            val sourceCounter = linkedMapOf<String, Int>()
            fun recordOrigin(pkg: String, count: Int = 1) {
                val key = pkg.ifBlank { "health_connect" }
                sourceCounter[key] = (sourceCounter[key] ?: 0) + count
            }

            val dailySteps = mutableListOf<ImportedDailySteps>()
            val vitals = mutableListOf<ImportedVitalMetric>()

            // 1. Aggregate Steps, Distance, and Calories per day to prevent double-counting across apps
            for (offset in 0 until daysBack) {
                val date = today.minusDays(offset.toLong())
                val dayStart = date.atStartOfDay(zone).toInstant()
                val dayEnd = if (offset == 0) now else date.plusDays(1).atStartOfDay(zone).toInstant()
                if (!dayEnd.isAfter(dayStart)) continue

                val dayKey = DateEpochUtils.todayDayKey(dayStart.toEpochMilli() + 43_200_000L)
                val metricsToRequest = buildSet {
                    if (stepsPerm in granted) add(StepsRecord.COUNT_TOTAL)
                    if (distancePerm in granted) add(DistanceRecord.DISTANCE_TOTAL)
                    if (activeCalPerm in granted) add(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL)
                    if (totalCalPerm in granted) add(TotalCaloriesBurnedRecord.ENERGY_TOTAL)
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

            // 2. Paginated Exercise Sessions
            val workouts = mutableListOf<ImportedWorkout>()
            if (exercisePerm in granted) {
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
            if (sleepPerm in granted) {
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
            if (hrPerm in granted) {
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
            if (hydrationPerm in granted) {
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

            // 6. Paginated Weight Records
            var latestWeight: Float? = null
            var latestWeightTs = 0L
            if (weightPerm in granted) {
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
