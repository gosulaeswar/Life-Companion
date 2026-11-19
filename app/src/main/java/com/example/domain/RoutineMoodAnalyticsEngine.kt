package com.example.domain

import com.example.data.local.entity.AchievementBadgeEntity
import com.example.data.local.entity.DailyRoutineEntity
import com.example.data.local.entity.DigitalWellbeingRecordEntity
import com.example.data.local.entity.ExerciseRecordEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.MoodJournalEntryEntity
import com.example.data.local.entity.RoutineCompletionEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.data.local.entity.WellnessChallengeEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

enum class RoutineTimeSlot(val label: String, val emoji: String, val defaultTime24h: String) {
    MORNING("Morning", "🌅", "07:30"),
    MIDDAY("Midday", "☀️", "13:00"),
    EVENING("Evening", "🌆", "21:00"),
    ANYTIME("Anytime", "🌿", "10:00");

    companion object {
        fun fromLabel(label: String): RoutineTimeSlot =
            entries.firstOrNull { it.label.equals(label, ignoreCase = true) } ?: ANYTIME
    }
}

data class SuggestedRoutineTemplate(
    val templateKey: String,
    val title: String,
    val emoji: String,
    val timeSlot: RoutineTimeSlot,
    val triggerTime24h: String,
    val estimatedMinutes: Int,
    val steps: List<String>,
    val linkedModuleKey: String,
    val reason: String
)

data class MoodPresetOption(
    val label: String,
    val emoji: String,
    val defaultScore: Int,
    val defaultEnergy: Int,
    val defaultStress: Int
)

data class MoodDailyTrendPoint(
    val dayKey: String,
    val shortLabel: String,
    val averageMood: Float?,
    val averageEnergy: Float?,
    val entryCount: Int
)

data class MoodJournalAnalytics(
    val totalEntriesAllTime: Int,
    val totalEntriesLast7Days: Int,
    val averageMood7d: Float?,
    val averageEnergy7d: Float?,
    val averageStress7d: Float?,
    val moodFrequency: Map<String, Int>,
    val topMoodLabel: String?,
    val dailyTrend7d: List<MoodDailyTrendPoint>,
    val reflectiveObservations: List<String>
)

data class DayConsistencyPoint(
    val dayKey: String,
    val shortLabel: String,
    val completedCount: Int,
    val activeRoutineCount: Int,
    val isToday: Boolean
)

data class RoutineAdherenceSummary(
    val activeRoutinesCount: Int,
    val completedTodayCount: Int,
    val todayFraction: Float,
    val completionsLast7Days: Int,
    val weeklyConsistencyPercent: Int,
    val sevenDayRhythm: List<DayConsistencyPoint>
)

data class ChallengeTemplateSpec(
    val challengeKey: String,
    val title: String,
    val emoji: String,
    val category: String,
    val description: String,
    val targetDays: Int
)

data class BadgeTemplateSpec(
    val badgeKey: String,
    val title: String,
    val emoji: String,
    val category: String,
    val description: String,
    val target: Int
)

object RoutineMoodAnalyticsEngine {

    private const val STEP_DELIMITER = "||"

    val moodPresets: List<MoodPresetOption> = listOf(
        MoodPresetOption("Joyful", "😊", 5, 4, 1),
        MoodPresetOption("Calm", "😌", 4, 3, 1),
        MoodPresetOption("Focused", "🎯", 4, 4, 2),
        MoodPresetOption("Grateful", "🙏", 5, 4, 1),
        MoodPresetOption("Tired", "😴", 3, 2, 2),
        MoodPresetOption("Stressed", "😮‍💨", 2, 2, 4),
        MoodPresetOption("Low", "🌧️", 2, 2, 3)
    )

    val moodContextTags: List<String> = listOf(
        "Sleep", "Hydration", "Movement", "Work", "Family", "Nutrition", "Rest", "Nature", "Cycle"
    )

    val optionalChallengeCatalog: List<ChallengeTemplateSpec> = listOf(
        ChallengeTemplateSpec(
            challengeKey = "hydration_harmony",
            title = "Hydration Harmony",
            emoji = "💧",
            category = "Hydration",
            description = "Sip water steadily across 7 cumulative days. Rest days never reset your progress.",
            targetDays = 7
        ),
        ChallengeTemplateSpec(
            challengeKey = "calm_morning_anchor",
            title = "Calm Morning Anchor",
            emoji = "🌅",
            category = "Routines",
            description = "Check in with any gentle morning routine on 5 days at your own pace.",
            targetDays = 5
        ),
        ChallengeTemplateSpec(
            challengeKey = "digital_sunset",
            title = "Gentle Digital Sunset",
            emoji = "🌙",
            category = "Rest",
            description = "Take a 15-minute screen-free evening pause across 5 nights whenever it fits.",
            targetDays = 5
        ),
        ChallengeTemplateSpec(
            challengeKey = "reflective_pause",
            title = "Mindful Mood Check-In",
            emoji = "📓",
            category = "Mindfulness",
            description = "Write a short mood or gratitude reflection on 5 separate days — no pressure.",
            targetDays = 5
        ),
        ChallengeTemplateSpec(
            challengeKey = "joyful_movement",
            title = "Joyful Movement Rhythm",
            emoji = "🧘",
            category = "Movement",
            description = "Stretch, walk, or move gently for 10+ minutes across 7 cumulative days.",
            targetDays = 7
        )
    )

    val achievementCatalog: List<BadgeTemplateSpec> = listOf(
        BadgeTemplateSpec(
            badgeKey = "first_routine_anchor",
            title = "Routine Architect",
            emoji = "🛠️",
            category = "Routines",
            description = "Create or adopt your first personalized daily routine.",
            target = 1
        ),
        BadgeTemplateSpec(
            badgeKey = "mindful_routine_5",
            title = "Steady Rhythm",
            emoji = "🌿",
            category = "Routines",
            description = "Complete 5 cumulative daily routine check-ins at your own pace.",
            target = 5
        ),
        BadgeTemplateSpec(
            badgeKey = "mood_reflector_3",
            title = "Quiet Observer",
            emoji = "📓",
            category = "Mood Journal",
            description = "Log 3 reflective entries in your private Mood Journal.",
            target = 3
        ),
        BadgeTemplateSpec(
            badgeKey = "gratitude_keeper_7",
            title = "Gratitude Keeper",
            emoji = "✨",
            category = "Mood Journal",
            description = "Save 5 entries with a reflection or gratitude note.",
            target = 5
        ),
        BadgeTemplateSpec(
            badgeKey = "hydration_steward",
            title = "Hydration Steward",
            emoji = "💧",
            category = "Hydration",
            description = "Log hydration 5 times to support steady daily energy.",
            target = 5
        ),
        BadgeTemplateSpec(
            badgeKey = "challenge_finisher",
            title = "Gentle Finisher",
            emoji = "🏆",
            category = "Challenges",
            description = "Complete an optional wellness challenge at your own rhythm.",
            target = 1
        )
    )

    fun serializeSteps(steps: List<String>): String =
        steps.map { it.trim().replace(STEP_DELIMITER, "/") }
            .filter { it.isNotEmpty() }
            .joinToString(STEP_DELIMITER)

    fun parseSteps(serialized: String): List<String> =
        serialized.split(STEP_DELIMITER)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

    fun suggestOptionalRoutines(
        profile: UserProfileEntity?,
        existingRoutines: List<DailyRoutineEntity>,
        medicines: List<MedicineEntity>,
        sleepRecords: List<SleepRecordEntity>,
        wellbeingRecords: List<DigitalWellbeingRecordEntity>,
        waterRecords: List<WaterRecordEntity>
    ): List<SuggestedRoutineTemplate> {
        val existingTitles = existingRoutines.map { it.title.trim().lowercase() }.toSet()
        val suggestions = mutableListOf<SuggestedRoutineTemplate>()

        suggestions += SuggestedRoutineTemplate(
            templateKey = "morning_hydration_breath",
            title = "Morning Hydration & Sunlight",
            emoji = "🌅",
            timeSlot = RoutineTimeSlot.MORNING,
            triggerTime24h = "07:30",
            estimatedMinutes = 8,
            steps = listOf(
                "Drink 250 ml warm water",
                "3 slow deep breaths near daylight",
                "Quick body check-in"
            ),
            linkedModuleKey = "water_tracker",
            reason = if (waterRecords.isEmpty()) {
                "Optional morning anchor to start your ${profile?.dailyWaterGoalMl ?: 2500} ml hydration gently."
            } else {
                "Pairs morning light with steady early hydration."
            }
        )

        if (medicines.any { it.isActive }) {
            suggestions += SuggestedRoutineTemplate(
                templateKey = "medication_meal_pause",
                title = "Calm Medicine & Breakfast Check",
                emoji = "💊",
                timeSlot = RoutineTimeSlot.MORNING,
                triggerTime24h = medicines.firstOrNull { it.isActive }?.scheduleTime24h ?: "08:30",
                estimatedMinutes = 5,
                steps = listOf(
                    "Take scheduled morning dose",
                    "Sip a full glass of water",
                    "Note how you feel after eating"
                ),
                linkedModuleKey = "medical_care",
                reason = "Matches your ${medicines.count { it.isActive }} active medicine schedule(s)."
            )
        }

        suggestions += SuggestedRoutineTemplate(
            templateKey = "midday_posture_reset",
            title = "Midday Eye & Posture Reset",
            emoji = "🧘",
            timeSlot = RoutineTimeSlot.MIDDAY,
            triggerTime24h = "13:30",
            estimatedMinutes = 6,
            steps = listOf(
                "Look 20 feet away for 20 seconds",
                "Shoulder & neck gentle stretch",
                "Refill water bottle"
            ),
            linkedModuleKey = "exercise_tracker",
            reason = "Short restorative break to ease midday screen or desk fatigue."
        )

        val avgSleepHours = sleepRecords.take(7)
            .map { ((it.wakeEpochMs - it.bedtimeEpochMs).coerceAtLeast(0L)) / 3_600_000f }
            .takeIf { it.isNotEmpty() }
            ?.average()
        val highNightScreen = wellbeingRecords.take(5).any { it.nightScreenMinutes >= 25 }

        suggestions += SuggestedRoutineTemplate(
            templateKey = "evening_wind_down",
            title = "Peaceful Evening Wind-Down",
            emoji = "🌙",
            timeSlot = RoutineTimeSlot.EVENING,
            triggerTime24h = "21:30",
            estimatedMinutes = 12,
            steps = listOf(
                "Dim screens & enable night warmth",
                "Write one gratitude line in Mood Journal",
                "5 calm belly breaths before rest"
            ),
            linkedModuleKey = "mood_journal",
            reason = when {
                avgSleepHours != null && avgSleepHours < 7.0 ->
                    "Optional wind-down based on recent sleep averaging ${"%.1f".format(Locale.US, avgSleepHours)}h."
                highNightScreen ->
                    "Gentle buffer after recent evening screen activity."
                else ->
                    "Soft transition between day tasks and restful sleep."
            }
        )

        if (profile?.cycleTrackingEnabled == true) {
            suggestions += SuggestedRoutineTemplate(
                templateKey = "cycle_comfort_check",
                title = "Cycle Comfort & Warmth",
                emoji = "🌸",
                timeSlot = RoutineTimeSlot.ANYTIME,
                triggerTime24h = "19:00",
                estimatedMinutes = 10,
                steps = listOf(
                    "Herbal tea or warm water",
                    "Gentle hip & lower-back stretch",
                    "Optional Cycle & Mood note"
                ),
                linkedModuleKey = "period_tracker",
                reason = "Tailored for gentle self-care whenever you want extra comfort."
            )
        }

        return suggestions.filterNot { it.title.lowercase() in existingTitles }
    }

    fun summarizeRoutineAdherence(
        routines: List<DailyRoutineEntity>,
        completions: List<RoutineCompletionEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): RoutineAdherenceSummary {
        val activeRoutines = routines.filter { it.isActive }
        val todayKey = DateEpochUtils.todayDayKey(nowMs)
        val completionMap = completions.groupBy { it.dayKey }

        val todayCompletedIds = completionMap[todayKey]
            .orEmpty()
            .filter { it.completedStepsCount >= it.totalStepsCount.coerceAtLeast(1) }
            .map { it.routineId }
            .toSet()
        val completedToday = activeRoutines.count { it.id in todayCompletedIds }
        val todayFraction = if (activeRoutines.isEmpty()) 0f else {
            (completedToday.toFloat() / activeRoutines.size.toFloat()).coerceIn(0f, 1f)
        }

        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val sevenDays = (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            val key = DateEpochUtils.dayKeyDaysAgo(daysAgo, nowMs)
            val doneCount = completionMap[key]
                .orEmpty()
                .count { it.completedStepsCount >= it.totalStepsCount.coerceAtLeast(1) }
            DayConsistencyPoint(
                dayKey = key,
                shortLabel = dayFormat.format(cal.time).take(2),
                completedCount = doneCount,
                activeRoutineCount = activeRoutines.size,
                isToday = daysAgo == 0
            )
        }

        val totalDone7d = sevenDays.sumOf { it.completedCount }
        val maxPossible7d = (activeRoutines.size * 7).coerceAtLeast(1)
        val consistencyPct = if (activeRoutines.isEmpty()) 0 else {
            ((totalDone7d.toFloat() / maxPossible7d.toFloat()) * 100f).toInt().coerceIn(0, 100)
        }

        return RoutineAdherenceSummary(
            activeRoutinesCount = activeRoutines.size,
            completedTodayCount = completedToday,
            todayFraction = todayFraction,
            completionsLast7Days = totalDone7d,
            weeklyConsistencyPercent = consistencyPct,
            sevenDayRhythm = sevenDays
        )
    }

    fun analyzeMoodJournal(
        entries: List<MoodJournalEntryEntity>,
        sleepRecords: List<SleepRecordEntity>,
        waterRecords: List<WaterRecordEntity>,
        routineCompletions: List<RoutineCompletionEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): MoodJournalAnalytics {
        val start7d = DateEpochUtils.dayKeyDaysAgo(6, nowMs)
        val last7d = entries.filter { it.dayKey >= start7d }

        val avgMood = last7d.takeIf { it.isNotEmpty() }?.map { it.moodScore }?.average()?.toFloat()
        val avgEnergy = last7d.takeIf { it.isNotEmpty() }?.map { it.energyLevel }?.average()?.toFloat()
        val avgStress = last7d.takeIf { it.isNotEmpty() }?.map { it.stressLevel }?.average()?.toFloat()

        val freq = last7d.groupingBy { it.moodLabel }.eachCount()
            .entries.sortedByDescending { it.value }
            .associate { it.key to it.value }
        val topMood = freq.keys.firstOrNull()

        val dayFormat = SimpleDateFormat("EEE", Locale.getDefault())
        val byDay = entries.groupBy { it.dayKey }
        val trend = (6 downTo 0).map { daysAgo ->
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                add(Calendar.DAY_OF_YEAR, -daysAgo)
            }
            val key = DateEpochUtils.dayKeyDaysAgo(daysAgo, nowMs)
            val dayList = byDay[key].orEmpty()
            MoodDailyTrendPoint(
                dayKey = key,
                shortLabel = dayFormat.format(cal.time).take(2),
                averageMood = dayList.takeIf { it.isNotEmpty() }?.map { it.moodScore }?.average()?.toFloat(),
                averageEnergy = dayList.takeIf { it.isNotEmpty() }?.map { it.energyLevel }?.average()?.toFloat(),
                entryCount = dayList.size
            )
        }

        val observations = buildList {
            if (last7d.isEmpty()) {
                add("Whenever you feel like it, capture a quick mood or gratitude reflection to spot gentle patterns.")
            } else {
                topMood?.let {
                    add("Your most frequent tone this week is $it (${freq[it]} check-in${if ((freq[it] ?: 0) == 1) "" else "s"}).")
                }
                val completionDays = routineCompletions.map { it.dayKey }.toSet()
                val moodWithRoutine = last7d.filter { it.dayKey in completionDays }
                val moodWithoutRoutine = last7d.filter { it.dayKey !in completionDays }
                if (moodWithRoutine.isNotEmpty() && moodWithoutRoutine.isNotEmpty()) {
                    val avgWith = moodWithRoutine.map { it.moodScore }.average()
                    val avgWithout = moodWithoutRoutine.map { it.moodScore }.average()
                    if (avgWith - avgWithout >= 0.4) {
                        add(
                            "On days with a completed routine, your logged mood averaged " +
                                "${"%.1f".format(Locale.US, avgWith)}/5 vs ${"%.1f".format(Locale.US, avgWithout)}/5."
                        )
                    }
                }
                val waterDays = waterRecords.groupBy { it.dayKey }.mapValues { (_, list) -> list.sumOf { it.amountMl } }
                val hydratedMoodDays = last7d.filter { (waterDays[it.dayKey] ?: 0) >= 1500 }
                if (hydratedMoodDays.size >= 2) {
                    val avgEnergyHydrated = hydratedMoodDays.map { it.energyLevel }.average()
                    add("On days with 1,500+ ml hydration, your logged energy averaged ${"%.1f".format(Locale.US, avgEnergyHydrated)}/5.")
                }
                val recentSleep = sleepRecords.take(3)
                if (recentSleep.isNotEmpty() && avgEnergy != null) {
                    val avgHrs = recentSleep.map { ((it.wakeEpochMs - it.bedtimeEpochMs).coerceAtLeast(0L)) / 3_600_000f }.average()
                    add("Recent sleep averaged ${"%.1f".format(Locale.US, avgHrs)}h alongside an energy rating of ${"%.1f".format(Locale.US, avgEnergy)}/5.")
                }
            }
        }

        return MoodJournalAnalytics(
            totalEntriesAllTime = entries.size,
            totalEntriesLast7Days = last7d.size,
            averageMood7d = avgMood,
            averageEnergy7d = avgEnergy,
            averageStress7d = avgStress,
            moodFrequency = freq,
            topMoodLabel = topMood,
            dailyTrend7d = trend,
            reflectiveObservations = observations
        )
    }

    fun evaluateBadgeProgress(
        profileId: Long,
        existingBadges: List<AchievementBadgeEntity>,
        routines: List<DailyRoutineEntity>,
        completions: List<RoutineCompletionEntity>,
        moodEntries: List<MoodJournalEntryEntity>,
        challenges: List<WellnessChallengeEntity>,
        waterRecords: List<WaterRecordEntity>,
        nowMs: Long = System.currentTimeMillis()
    ): List<AchievementBadgeEntity> {
        val existingByKey = existingBadges.associateBy { it.badgeKey }
        val completedRoutinesCount = completions.count { it.completedStepsCount >= it.totalStepsCount.coerceAtLeast(1) }
        val reflectiveCount = moodEntries.count { it.reflectionNote.isNotBlank() || it.gratitudeText.isNotBlank() }
        val completedChallengesCount = challenges.count { it.status == "COMPLETED" }

        return achievementCatalog.map { spec ->
            val current = when (spec.badgeKey) {
                "first_routine_anchor" -> routines.size
                "mindful_routine_5" -> completedRoutinesCount
                "mood_reflector_3" -> moodEntries.size
                "gratitude_keeper_7" -> reflectiveCount
                "hydration_steward" -> waterRecords.size
                "challenge_finisher" -> completedChallengesCount
                else -> 0
            }.coerceAtMost(spec.target)

            val prev = existingByKey[spec.badgeKey]
            val earnedAt = when {
                prev?.earnedAtEpochMs != null -> prev.earnedAtEpochMs
                current >= spec.target -> nowMs
                else -> null
            }

            AchievementBadgeEntity(
                id = prev?.id ?: 0L,
                profileId = profileId,
                badgeKey = spec.badgeKey,
                title = spec.title,
                emoji = spec.emoji,
                description = spec.description,
                category = spec.category,
                earnedAtEpochMs = earnedAt,
                progressCurrent = current,
                progressTarget = spec.target
            )
        }
    }
}
