package com.example.healthsync

import android.content.Context
import android.net.Uri
import android.util.Xml
import com.example.domain.DateEpochUtils
import org.json.JSONArray
import org.json.JSONObject
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Parses real exported fitness files selected via Android SAF OpenDocument:
 * - Apple Health export.xml (HKQuantityTypeIdentifierStepCount, HeartRate, Distance, Calories, Water, BodyMass, SleepAnalysis, Workout)
 * - JSON fitness exports
 * - CSV fitness exports (date,steps,calories,heart_rate,sleep_hours,distance_km)
 */
class AppleAndFileFitnessImporter {

    fun importFromUri(context: Context, uri: Uri): Result<HealthSyncPayload> {
        return runCatching {
            val mime = context.contentResolver.getType(uri).orEmpty()
            val nameHint = uri.lastPathSegment.orEmpty()
            context.contentResolver.openInputStream(uri)?.use { stream ->
                val rawBytes = stream.readBytes()
                val textPreview = rawBytes.take(512).toByteArray().toString(Charsets.UTF_8).trimStart()
                when {
                    mime.contains("xml", ignoreCase = true) ||
                        nameHint.endsWith(".xml", ignoreCase = true) ||
                        textPreview.startsWith("<") -> {
                        parseAppleHealthXml(rawBytes.inputStream())
                    }
                    mime.contains("json", ignoreCase = true) ||
                        nameHint.endsWith(".json", ignoreCase = true) ||
                        textPreview.startsWith("{") ||
                        textPreview.startsWith("[") -> {
                        parseJsonExport(rawBytes.toString(Charsets.UTF_8))
                    }
                    else -> parseCsvExport(rawBytes.toString(Charsets.UTF_8))
                }
            } ?: error("Unable to open the selected fitness file.")
        }
    }

    fun parseAppleHealthXml(inputStream: InputStream): HealthSyncPayload {
        val parser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(inputStream, "UTF-8")

        val stepsByDay = linkedMapOf<String, Int>()
        val distanceByDay = linkedMapOf<String, Double>()
        val caloriesByDay = linkedMapOf<String, Double>()
        val workouts = mutableListOf<ImportedWorkout>()
        val sleeps = mutableListOf<ImportedSleep>()
        val waters = mutableListOf<ImportedWater>()
        val vitals = mutableListOf<ImportedVitalMetric>()
        var latestWeight: Float? = null
        var latestWeightTs = 0L
        val sourcesSeen = linkedMapOf<String, Int>()

        var eventType = parser.eventType
        while (eventType != XmlPullParser.END_DOCUMENT) {
            if (eventType == XmlPullParser.START_TAG) {
                when (parser.name) {
                    "Record" -> {
                        val type = parser.getAttributeValue(null, "type").orEmpty()
                        val sourceName = parser.getAttributeValue(null, "sourceName")
                            ?.ifBlank { "Apple Health" } ?: "Apple Health"
                        val unit = parser.getAttributeValue(null, "unit").orEmpty()
                        val valueStr = parser.getAttributeValue(null, "value").orEmpty()
                        val startStr = parser.getAttributeValue(null, "startDate").orEmpty()
                        val endStr = parser.getAttributeValue(null, "endDate").orEmpty()

                        val startMs = parseAppleTimestamp(startStr)
                        val endMs = parseAppleTimestamp(endStr).takeIf { it > startMs } ?: startMs
                        val dayKey = DateEpochUtils.todayDayKey(startMs)
                        val numeric = valueStr.toDoubleOrNull()

                        when (type) {
                            "HKQuantityTypeIdentifierStepCount" -> if (numeric != null && numeric > 0) {
                                stepsByDay[dayKey] = (stepsByDay[dayKey] ?: 0) + numeric.toInt()
                                sourcesSeen[sourceName] = (sourcesSeen[sourceName] ?: 0) + 1
                            }

                            "HKQuantityTypeIdentifierDistanceWalkingRunning",
                            "HKQuantityTypeIdentifierDistanceCycling" -> if (numeric != null && numeric > 0) {
                                val meters = when {
                                    unit.equals("km", ignoreCase = true) -> numeric * 1000.0
                                    unit.equals("mi", ignoreCase = true) -> numeric * 1609.34
                                    else -> numeric
                                }
                                distanceByDay[dayKey] = (distanceByDay[dayKey] ?: 0.0) + meters
                                sourcesSeen[sourceName] = (sourcesSeen[sourceName] ?: 0) + 1
                            }

                            "HKQuantityTypeIdentifierActiveEnergyBurned" -> if (numeric != null && numeric > 0) {
                                caloriesByDay[dayKey] = (caloriesByDay[dayKey] ?: 0.0) + numeric
                                sourcesSeen[sourceName] = (sourcesSeen[sourceName] ?: 0) + 1
                            }

                            "HKQuantityTypeIdentifierHeartRate" -> if (numeric != null && numeric > 25) {
                                vitals += ImportedVitalMetric(
                                    metricType = "HEART_RATE",
                                    valuePrimary = numeric,
                                    unit = "bpm",
                                    sourceId = "apple_health_export",
                                    dayKey = dayKey,
                                    recordedAtEpochMs = startMs
                                )
                                sourcesSeen[sourceName] = (sourcesSeen[sourceName] ?: 0) + 1
                            }

                            "HKQuantityTypeIdentifierDietaryWater" -> if (numeric != null && numeric > 0) {
                                val ml = when {
                                    unit.equals("L", ignoreCase = true) -> (numeric * 1000.0).toInt()
                                    unit.contains("oz", ignoreCase = true) -> (numeric * 29.5735).toInt()
                                    else -> numeric.toInt()
                                }
                                if (ml > 0) {
                                    waters += ImportedWater(
                                        amountMl = ml,
                                        drinkType = "Synced Water",
                                        dayKey = dayKey,
                                        loggedAtEpochMs = startMs,
                                        sourceId = "apple_health_export"
                                    )
                                    sourcesSeen[sourceName] = (sourcesSeen[sourceName] ?: 0) + 1
                                }
                            }

                            "HKQuantityTypeIdentifierBodyMass" -> if (numeric != null && numeric > 10) {
                                val kg = if (unit.equals("lb", ignoreCase = true)) {
                                    (numeric * 0.453592).toFloat()
                                } else {
                                    numeric.toFloat()
                                }
                                if (startMs >= latestWeightTs) {
                                    latestWeightTs = startMs
                                    latestWeight = kg
                                }
                                vitals += ImportedVitalMetric(
                                    metricType = "WEIGHT",
                                    valuePrimary = kg.toDouble(),
                                    unit = "kg",
                                    sourceId = "apple_health_export",
                                    dayKey = dayKey,
                                    recordedAtEpochMs = startMs
                                )
                                sourcesSeen[sourceName] = (sourcesSeen[sourceName] ?: 0) + 1
                            }

                            "HKCategoryTypeIdentifierSleepAnalysis" -> {
                                val isAsleep = valueStr.contains("Asleep", ignoreCase = true) ||
                                    valueStr == "1"
                                if (isAsleep && endMs - startMs >= 1_800_000L) {
                                    val hrs = (endMs - startMs) / 3_600_000f
                                    sleeps += ImportedSleep(
                                        bedtimeEpochMs = startMs,
                                        wakeEpochMs = endMs,
                                        qualityRating = if (hrs >= 7f) 5 else 4,
                                        notes = "Imported from $sourceName",
                                        sourceId = "apple_health_export"
                                    )
                                    sourcesSeen[sourceName] = (sourcesSeen[sourceName] ?: 0) + 1
                                }
                            }
                        }
                    }

                    "Workout" -> {
                        val rawType = parser.getAttributeValue(null, "workoutActivityType").orEmpty()
                        val sourceName = parser.getAttributeValue(null, "sourceName")
                            ?.ifBlank { "Apple Watch" } ?: "Apple Watch"
                        val duration = parser.getAttributeValue(null, "duration")?.toDoubleOrNull() ?: 30.0
                        val durationUnit = parser.getAttributeValue(null, "durationUnit").orEmpty()
                        val mins = if (durationUnit.equals("s", ignoreCase = true)) {
                            (duration / 60.0).toInt()
                        } else {
                            duration.toInt()
                        }.coerceIn(1, 480)
                        val cals = parser.getAttributeValue(null, "totalEnergyBurned")
                            ?.toDoubleOrNull()?.toInt() ?: (mins * 6)
                        val startMs = parseAppleTimestamp(
                            parser.getAttributeValue(null, "startDate").orEmpty()
                        )
                        val cleanName = rawType.removePrefix("HKWorkoutActivityType")
                            .replace(Regex("([a-z])([A-Z])"), "$1 $2")
                            .ifBlank { "Workout" }
                        val isYoga = cleanName.contains("Yoga", ignoreCase = true) ||
                            cleanName.contains("Mind", ignoreCase = true)

                        workouts += ImportedWorkout(
                            activityType = (if (isYoga) "🧘 " else "🍎 ") + cleanName,
                            category = if (isYoga) "Yoga" else "Exercise",
                            durationMinutes = mins,
                            intensity = if (mins >= 40) "Vigorous" else "Moderate",
                            caloriesBurned = cals,
                            loggedAtEpochMs = startMs,
                            sourceId = "apple_health_export"
                        )
                        sourcesSeen[sourceName] = (sourcesSeen[sourceName] ?: 0) + 1
                    }
                }
            }
            eventType = parser.next()
        }

        val allStepDays = (stepsByDay.keys + distanceByDay.keys).distinct()
        val dailySteps = allStepDays.map { day ->
            val steps = stepsByDay[day] ?: 0
            val dist = distanceByDay[day]?.toInt() ?: (steps * 0.74).toInt()
            if (dist > 0) {
                vitals += ImportedVitalMetric(
                    metricType = "DISTANCE",
                    valuePrimary = dist.toDouble(),
                    unit = "m",
                    sourceId = "apple_health_export",
                    dayKey = day,
                    recordedAtEpochMs = System.currentTimeMillis()
                )
            }
            ImportedDailySteps(
                dayKey = day,
                steps = steps,
                distanceMeters = dist,
                sourceId = "apple_health_export"
            )
        }
        caloriesByDay.forEach { (day, kcal) ->
            vitals += ImportedVitalMetric(
                metricType = "ACTIVE_CALORIES",
                valuePrimary = kcal,
                unit = "kcal",
                sourceId = "apple_health_export",
                dayKey = day,
                recordedAtEpochMs = System.currentTimeMillis()
            )
        }

        val totalImported = dailySteps.size + workouts.size + sleeps.size + waters.size + vitals.size
        val sourceSummary = if (sourcesSeen.isEmpty()) {
            "Parsed Apple Health XML"
        } else {
            "Sources: ${sourcesSeen.keys.take(3).joinToString(", ")}"
        }

        return HealthSyncPayload(
            dailySteps = dailySteps,
            workouts = workouts,
            sleepSessions = sleeps,
            waterEntries = waters,
            vitalMetrics = vitals,
            latestWeightKg = latestWeight,
            sources = if (totalImported > 0) {
                listOf(
                    ImportedSourceStat(
                        sourceId = "apple_health_export",
                        displayName = "Apple Health / Apple Watch",
                        emoji = "🍎",
                        syncChannel = "File Import",
                        recordsCount = totalImported,
                        summary = sourceSummary
                    )
                )
            } else {
                emptyList()
            }
        )
    }

    fun parseJsonExport(rawJson: String): HealthSyncPayload {
        val trimmed = rawJson.trim()
        val root = if (trimmed.startsWith("[")) {
            JSONObject().put("entries", JSONArray(trimmed))
        } else {
            JSONObject(trimmed)
        }
        val sourceLabel = root.optString("source", "Fitness JSON Export").ifBlank { "Fitness JSON Export" }
        val today = DateEpochUtils.todayDayKey()
        val dailySteps = mutableListOf<ImportedDailySteps>()
        val workouts = mutableListOf<ImportedWorkout>()
        val vitals = mutableListOf<ImportedVitalMetric>()
        val nutritionEntries = mutableListOf<ImportedNutrition>()

        val directSteps = root.optInt("steps", 0)
        if (directSteps > 0) {
            dailySteps += ImportedDailySteps(
                dayKey = root.optString("date", today).ifBlank { today },
                steps = directSteps,
                distanceMeters = root.optInt("distanceMeters", (directSteps * 0.74).toInt()),
                sourceId = "fitness_file_import"
            )
        }
        val directHr = root.optDouble("heartRate", 0.0)
        if (directHr > 25.0) {
            vitals += ImportedVitalMetric(
                metricType = "HEART_RATE",
                valuePrimary = directHr,
                unit = "bpm",
                sourceId = "fitness_file_import",
                dayKey = today,
                recordedAtEpochMs = System.currentTimeMillis()
            )
        }
        val entries = root.optJSONArray("entries") ?: root.optJSONArray("metrics")
        if (entries != null) {
            for (i in 0 until entries.length()) {
                val obj = entries.optJSONObject(i) ?: continue
                val day = obj.optString("date", today).ifBlank { today }
                val s = obj.optInt("steps", 0)
                if (s > 0) {
                    dailySteps += ImportedDailySteps(
                        dayKey = day,
                        steps = s,
                        distanceMeters = obj.optInt("distanceMeters", (s * 0.74).toInt()),
                        sourceId = "fitness_file_import"
                    )
                }
                val hr = obj.optDouble("heartRate", 0.0)
                if (hr > 25.0) {
                    vitals += ImportedVitalMetric(
                        metricType = "HEART_RATE",
                        valuePrimary = hr,
                        unit = "bpm",
                        sourceId = "fitness_file_import",
                        dayKey = day,
                        recordedAtEpochMs = System.currentTimeMillis()
                    )
                }
                val activity = obj.optString("workout", "")
                if (activity.isNotBlank()) {
                    workouts += ImportedWorkout(
                        activityType = activity,
                        category = if (activity.contains("yoga", true)) "Yoga" else "Exercise",
                        durationMinutes = obj.optInt("durationMinutes", 30).coerceIn(1, 360),
                        intensity = "Moderate",
                        caloriesBurned = obj.optInt("calories", 180),
                        loggedAtEpochMs = System.currentTimeMillis(),
                        sourceId = "fitness_file_import"
                    )
                }
                val foodName = obj.optString("food", obj.optString("mealName", ""))
                val foodCals = obj.optInt("intakeCalories", 0)
                if (foodName.isNotBlank() && foodCals > 0) {
                    nutritionEntries += ImportedNutrition(
                        mealType = obj.optString("mealType", "Snack").ifBlank { "Snack" },
                        foodSummary = "📁 $foodName",
                        calories = foodCals,
                        proteinGrams = obj.optInt("proteinGrams", 0),
                        carbsGrams = obj.optInt("carbsGrams", 0),
                        fatGrams = obj.optInt("fatGrams", 0),
                        loggedAtEpochMs = System.currentTimeMillis(),
                        sourceId = "fitness_file_import"
                    )
                }
            }
        }
        val count = dailySteps.size + workouts.size + vitals.size + nutritionEntries.size
        return HealthSyncPayload(
            dailySteps = dailySteps,
            workouts = workouts,
            nutritionEntries = nutritionEntries,
            vitalMetrics = vitals,
            sources = if (count > 0) {
                listOf(
                    ImportedSourceStat(
                        sourceId = "fitness_file_import",
                        displayName = sourceLabel,
                        emoji = "📁",
                        syncChannel = "File Import",
                        recordsCount = count,
                        summary = "Imported $count record(s) from JSON"
                    )
                )
            } else {
                emptyList()
            }
        )
    }

    fun parseCsvExport(rawCsv: String): HealthSyncPayload {
        val lines = rawCsv.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (lines.size < 2) return HealthSyncPayload()
        val headers = lines.first().split(',').map { it.trim().lowercase() }
        val dateIdx = headers.indexOfFirst { "date" in it || "day" in it }
        val stepsIdx = headers.indexOfFirst { "step" in it }
        val hrIdx = headers.indexOfFirst { "heart" in it || it == "hr" || "bpm" in it }
        val calIdx = headers.indexOfFirst { "cal" in it }
        val distIdx = headers.indexOfFirst { "dist" in it }

        val dailySteps = mutableListOf<ImportedDailySteps>()
        val vitals = mutableListOf<ImportedVitalMetric>()
        val today = DateEpochUtils.todayDayKey()

        lines.drop(1).forEach { row ->
            val cols = row.split(',').map { it.trim().trim('"') }
            val day = cols.getOrNull(dateIdx)?.take(10)?.takeIf { it.length == 10 } ?: today
            val steps = cols.getOrNull(stepsIdx)?.toDoubleOrNull()?.toInt() ?: 0
            val dist = cols.getOrNull(distIdx)?.toDoubleOrNull()?.let {
                if (it < 100.0) (it * 1000).toInt() else it.toInt()
            } ?: (steps * 0.74).toInt()
            if (steps > 0) {
                dailySteps += ImportedDailySteps(day, steps, dist, "fitness_file_import")
            }
            val hr = cols.getOrNull(hrIdx)?.toDoubleOrNull()
            if (hr != null && hr > 25.0) {
                vitals += ImportedVitalMetric(
                    metricType = "HEART_RATE",
                    valuePrimary = hr,
                    unit = "bpm",
                    sourceId = "fitness_file_import",
                    dayKey = day,
                    recordedAtEpochMs = System.currentTimeMillis()
                )
            }
            val cal = cols.getOrNull(calIdx)?.toDoubleOrNull()
            if (cal != null && cal > 1.0) {
                vitals += ImportedVitalMetric(
                    metricType = "ACTIVE_CALORIES",
                    valuePrimary = cal,
                    unit = "kcal",
                    sourceId = "fitness_file_import",
                    dayKey = day,
                    recordedAtEpochMs = System.currentTimeMillis()
                )
            }
        }
        val count = dailySteps.size + vitals.size
        return HealthSyncPayload(
            dailySteps = dailySteps,
            vitalMetrics = vitals,
            sources = if (count > 0) {
                listOf(
                    ImportedSourceStat(
                        sourceId = "fitness_file_import",
                        displayName = "Fitbit / Fitness CSV",
                        emoji = "📊",
                        syncChannel = "File Import",
                        recordsCount = count,
                        summary = "Imported $count CSV metric rows"
                    )
                )
            } else {
                emptyList()
            }
        )
    }

    private fun parseAppleTimestamp(raw: String): Long {
        if (raw.isBlank()) return System.currentTimeMillis()
        val formats = listOf(
            "yyyy-MM-dd HH:mm:ss Z",
            "yyyy-MM-dd'T'HH:mm:ss'Z'",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )
        for (pattern in formats) {
            runCatching {
                val parsed = SimpleDateFormat(pattern, Locale.US).parse(raw.trim())
                if (parsed != null) return parsed.time
            }
        }
        return System.currentTimeMillis()
    }
}
