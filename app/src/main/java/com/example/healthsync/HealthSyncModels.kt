package com.example.healthsync

enum class HealthConnectAvailability {
    AVAILABLE,
    UPDATE_OR_INSTALL_REQUIRED,
    UNAVAILABLE,
    REQUIRES_ANDROID_8
}

enum class HealthConnectDataCategory(
    val label: String,
    val emoji: String,
    val description: String
) {
    STEPS_AND_ACTIVITY("Steps, Distance & Calories", "👟", "Daily step count, distance, active/total calories"),
    WORKOUTS("Workouts & Yoga", "🏋️", "Exercise sessions, duration & estimated burn"),
    SLEEP("Sleep Sessions", "😴", "Bedtime, wake time & rest quality"),
    HEART_RATE("Heart Rate", "❤️", "Heart rate samples from connected watches"),
    HYDRATION("Hydration Logs", "💧", "Water volume logged by connected apps"),
    NUTRITION("Nutrition & Meals", "🥗", "Calories, protein, carbs & fats from diet apps"),
    WEIGHT("Body Weight", "⚖️", "Latest body mass measurements")
}

data class FitnessEcosystemBrand(
    val id: String,
    val name: String,
    val emoji: String,
    val channelLabel: String
)

object FitnessSourceCatalog {
    val supportedBrands: List<FitnessEcosystemBrand> = listOf(
        FitnessEcosystemBrand("com.fitbit.FitbitMobile", "Fitbit", "⌚", "Health Connect"),
        FitnessEcosystemBrand("com.google.android.apps.fitness", "Google Fit", "🏃", "Health Connect"),
        FitnessEcosystemBrand("com.sec.android.app.shealth", "Samsung Health", "💙", "Health Connect"),
        FitnessEcosystemBrand("com.garmin.android.apps.connectmobile", "Garmin Connect", "🧭", "Health Connect"),
        FitnessEcosystemBrand("com.ouraring.oura", "Oura Ring", "💍", "Health Connect"),
        FitnessEcosystemBrand("com.strava", "Strava", "🚴", "Health Connect"),
        FitnessEcosystemBrand("com.withings.wiscale2", "Withings", "⚖️", "Health Connect"),
        FitnessEcosystemBrand("fi.polar.polarflow", "Polar Flow", "❄️", "Health Connect"),
        FitnessEcosystemBrand("apple_health_export", "Apple Health", "🍎", "File Import (export.xml)")
    )

    fun resolveSourceInfo(rawSourceId: String, customLabel: String? = null): Pair<String, String> {
        val clean = rawSourceId.trim()
        val matched = supportedBrands.firstOrNull { brand ->
            clean.equals(brand.id, ignoreCase = true) ||
                clean.contains(brand.name, ignoreCase = true)
        }
        if (matched != null) {
            return matched.name to matched.emoji
        }
        return when {
            clean.contains("fitbit", ignoreCase = true) -> "Fitbit" to "⌚"
            clean.contains("shealth", ignoreCase = true) || clean.contains("samsung", ignoreCase = true) -> "Samsung Health" to "💙"
            clean.contains("garmin", ignoreCase = true) -> "Garmin Connect" to "🧭"
            clean.contains("oura", ignoreCase = true) -> "Oura Ring" to "💍"
            clean.contains("strava", ignoreCase = true) -> "Strava" to "🚴"
            clean.contains("polar", ignoreCase = true) -> "Polar Flow" to "❄️"
            clean.contains("withings", ignoreCase = true) -> "Withings" to "⚖️"
            clean.contains("whoop", ignoreCase = true) -> "WHOOP" to "💪"
            clean.contains("zepp", ignoreCase = true) || clean.contains("huami", ignoreCase = true) || clean.contains("xiaomi", ignoreCase = true) -> "Zepp / Mi Fitness" to "⌚"
            clean.contains("apple", ignoreCase = true) || clean.contains("watch", ignoreCase = true) -> "Apple Health" to "🍎"
            clean.contains("fitness", ignoreCase = true) -> "Google Fit" to "🏃"
            !customLabel.isNullOrBlank() -> customLabel to "📱"
            clean.isNotBlank() -> clean.substringAfterLast('.').replaceFirstChar { it.uppercase() } to "⌚"
            else -> "Connected Device" to "⌚"
        }
    }
}

data class ImportedDailySteps(
    val dayKey: String,
    val steps: Int,
    val distanceMeters: Int,
    val sourceId: String
)

data class ImportedWorkout(
    val activityType: String,
    val category: String, // "Exercise" or "Yoga"
    val durationMinutes: Int,
    val intensity: String,
    val caloriesBurned: Int,
    val loggedAtEpochMs: Long,
    val sourceId: String
)

data class ImportedSleep(
    val bedtimeEpochMs: Long,
    val wakeEpochMs: Long,
    val qualityRating: Int,
    val notes: String,
    val sourceId: String
)

data class ImportedWater(
    val amountMl: Int,
    val drinkType: String,
    val dayKey: String,
    val loggedAtEpochMs: Long,
    val sourceId: String
)

data class ImportedNutrition(
    val mealType: String, // "Breakfast", "Lunch", "Dinner", "Snack"
    val foodSummary: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val loggedAtEpochMs: Long,
    val sourceId: String
)

data class ImportedVitalMetric(
    val metricType: String, // "HEART_RATE", "DISTANCE", "ACTIVE_CALORIES", "WEIGHT"
    val valuePrimary: Double,
    val unit: String,
    val sourceId: String,
    val dayKey: String,
    val recordedAtEpochMs: Long
)

data class ImportedSourceStat(
    val sourceId: String,
    val displayName: String,
    val emoji: String,
    val syncChannel: String,
    val recordsCount: Int,
    val summary: String
)

data class HealthSyncPayload(
    val dailySteps: List<ImportedDailySteps> = emptyList(),
    val workouts: List<ImportedWorkout> = emptyList(),
    val sleepSessions: List<ImportedSleep> = emptyList(),
    val waterEntries: List<ImportedWater> = emptyList(),
    val nutritionEntries: List<ImportedNutrition> = emptyList(),
    val vitalMetrics: List<ImportedVitalMetric> = emptyList(),
    val latestWeightKg: Float? = null,
    val sources: List<ImportedSourceStat> = emptyList()
) {
    val totalRecordsCount: Int
        get() = dailySteps.size + workouts.size + sleepSessions.size +
            waterEntries.size + nutritionEntries.size + vitalMetrics.size
}
