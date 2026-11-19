package com.example.domain

import com.example.ai.AiHealthIntelligenceReport
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
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.notifications.AlertMetricsSummary
import com.example.security.AiDataAuthorization
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

enum class IntelligenceTimeframe(val label: String, val badgeText: String) {
    DAILY("Today", "24h"),
    WEEKLY("Weekly", "7 Days"),
    MONTHLY("Monthly", "30 Days")
}

enum class TrendDirection(val emoji: String, val label: String) {
    IMPROVED("📈", "Improved"),
    DECLINED("📉", "Needs attention"),
    STEADY("➡️", "Steady"),
    INSUFFICIENT_DATA("❔", "Baseline needed")
}

data class TrendMetricComparison(
    val id: String,
    val emoji: String,
    val title: String,
    val currentValueLabel: String,
    val previousValueLabel: String,
    val deltaBadge: String,
    val direction: TrendDirection,
    val provenanceNote: String,
    val moduleKey: String
)

data class RoutineGapItem(
    val id: String,
    val emoji: String,
    val title: String,
    val recordedFact: String,
    val wellnessSuggestion: String,
    val moduleKey: String,
    val priority: Int
)

data class DailyWellnessPlanItem(
    val id: String,
    val timeBlock: String,
    val emoji: String,
    val title: String,
    val recordedBasis: String,
    val actionSuggestion: String,
    val moduleKey: String
)

data class PersonalHealthIntelligenceSnapshot(
    val generatedAtEpochMs: Long,
    val authorization: AiDataAuthorization,
    val authorizedModuleCount: Int,
    val loggedModuleCount: Int,
    val dailyBriefingHeadline: String,
    val dailyBriefingBullets: List<String>,
    val weeklySummaryHeadline: String,
    val weeklySummaryBullets: List<String>,
    val monthlySummaryHeadline: String,
    val monthlySummaryBullets: List<String>,
    val trends: List<TrendMetricComparison>,
    val routineGaps: List<RoutineGapItem>,
    val dailyPlan: List<DailyWellnessPlanItem>,
    val missingOrExcludedNotes: List<String>,
    val groundedAiPromptContext: String
) {
    fun offlineReportFor(timeframe: IntelligenceTimeframe): AiHealthIntelligenceReport {
        val (headline, summaryBullets) = when (timeframe) {
            IntelligenceTimeframe.DAILY -> dailyBriefingHeadline to dailyBriefingBullets
            IntelligenceTimeframe.WEEKLY -> weeklySummaryHeadline to weeklySummaryBullets
            IntelligenceTimeframe.MONTHLY -> monthlySummaryHeadline to monthlySummaryBullets
        }
        val recordedSummary = if (summaryBullets.isNotEmpty()) {
            summaryBullets.joinToString("\n• ", prefix = "• ")
        } else {
            "No records are currently logged or authorized for this period."
        }
        val observed = trends.map { trend ->
            "${trend.emoji} ${trend.title}: ${trend.currentValueLabel} (${trend.deltaBadge}; ${trend.provenanceNote})"
        }
        val suggestions = routineGaps.take(4).map { gap ->
            "${gap.emoji} ${gap.wellnessSuggestion}"
        }.ifEmpty {
            listOf("🌿 Keep logging water, sleep, and activity consistently to build richer weekly and monthly insights.")
        }
        val planLines = dailyPlan.take(4).map { item ->
            "${item.emoji} ${item.timeBlock}: ${item.actionSuggestion}"
        }
        val coverageNote = if (missingOrExcludedNotes.isNotEmpty()) {
            missingOrExcludedNotes.joinToString(" • ")
        } else {
            "All $authorizedModuleCount authorized modules have recorded local history available for synthesis."
        }
        return AiHealthIntelligenceReport(
            timeframe = timeframe.name,
            headline = headline,
            recordedFactsSummary = recordedSummary,
            observedChanges = observed,
            personalizedSuggestions = suggestions,
            dailyPlanHighlights = planLines,
            uncertaintyAndCoverageNote = coverageNote,
            disclaimer = STANDARD_WELLNESS_DISCLAIMER,
            isFromCloudAi = false
        )
    }

    companion object {
        const val STANDARD_WELLNESS_DISCLAIMER =
            "Informational wellness insights only—not a medical diagnosis or treatment plan. Recorded data is strictly separated from wellness suggestions."

        fun empty(): PersonalHealthIntelligenceSnapshot = PersonalHealthIntelligenceSnapshot(
            generatedAtEpochMs = 0L,
            authorization = AiDataAuthorization(),
            authorizedModuleCount = 0,
            loggedModuleCount = 0,
            dailyBriefingHeadline = "Ready to analyze your daily habits.",
            dailyBriefingBullets = emptyList(),
            weeklySummaryHeadline = "Log a few days to unlock weekly trends.",
            weeklySummaryBullets = emptyList(),
            monthlySummaryHeadline = "Monthly trend comparison builds as you log.",
            monthlySummaryBullets = emptyList(),
            trends = emptyList(),
            routineGaps = emptyList(),
            dailyPlan = emptyList(),
            missingOrExcludedNotes = listOf("No local records logged yet."),
            groundedAiPromptContext = "No wellness records are available yet."
        )
    }
}

/**
 * Privacy-conscious, strictly non-diagnostic Personal Health Intelligence Engine.
 * Aggregates daily, 7-day, and 30-day windows directly from Room records without inventing values,
 * separates factual observations from wellness suggestions, and respects per-module authorization.
 */
object PersonalHealthIntelligenceEngine {

    fun analyze(
        profile: UserProfileEntity?,
        authorization: AiDataAuthorization,
        waterRecords60d: List<WaterRecordEntity>,
        sleepRecords: List<SleepRecordEntity>,
        exercises: List<ExerciseRecordEntity>,
        stepRecords: List<StepRecordEntity>,
        dietRecords: List<DietRecordEntity>,
        medicines: List<MedicineEntity>,
        medicineIntakes60d: List<MedicineIntakeEntity>,
        consultations: List<DoctorConsultationEntity>,
        beautyRecords: List<BeautyCareRecordEntity>,
        wellbeingRecords: List<DigitalWellbeingRecordEntity>,
        cycleInsights: CycleInsights?,
        connectedSources: List<ConnectedDeviceSourceEntity> = emptyList(),
        vitalMetrics: List<HealthVitalMetricEntity> = emptyList(),
        customAlarms: List<ReminderEntity> = emptyList(),
        alertMetrics: AlertMetricsSummary = AlertMetricsSummary(),
        aiPref: AiPreferenceEntity? = null,
        nowMs: Long = System.currentTimeMillis()
    ): PersonalHealthIntelligenceSnapshot {
        val todayKey = DateEpochUtils.todayDayKey(nowMs)
        val todayEpochDay = DateEpochUtils.todayEpochDay(nowMs)
        val dayKey7Start = DateEpochUtils.dayKeyDaysAgo(6, nowMs)
        val dayKeyPrev7Start = DateEpochUtils.dayKeyDaysAgo(13, nowMs)
        val dayKeyPrev7End = DateEpochUtils.dayKeyDaysAgo(7, nowMs)
        val dayKey30Start = DateEpochUtils.dayKeyDaysAgo(29, nowMs)
        val dayKeyPrev30Start = DateEpochUtils.dayKeyDaysAgo(59, nowMs)
        val dayKeyPrev30End = DateEpochUtils.dayKeyDaysAgo(30, nowMs)
        val currentHour = DateEpochUtils.epochMsToHourMinute(nowMs).first

        val missingOrExcluded = mutableListOf<String>()
        val dailyBullets = mutableListOf<String>()
        val weeklyBullets = mutableListOf<String>()
        val monthlyBullets = mutableListOf<String>()
        val trends = mutableListOf<TrendMetricComparison>()
        val gaps = mutableListOf<RoutineGapItem>()
        val plan = mutableListOf<DailyWellnessPlanItem>()

        var loggedModules = 0
        val cycleEnabled = profile?.cycleTrackingEnabled == true
        val authorizedModules = authorization.authorizedCount(cycleEnabled)

        // --- 1. HYDRATION ---
        val waterGoal = (profile?.dailyWaterGoalMl ?: 2500).coerceAtLeast(500)
        val todayWaterMl: Int?
        val weekWaterAvgMl: Int?
        val prevWeekWaterAvgMl: Int?
        if (!authorization.includeHydration) {
            todayWaterMl = null
            weekWaterAvgMl = null
            prevWeekWaterAvgMl = null
            missingOrExcluded += "Hydration excluded by your AI privacy settings."
        } else {
            val waterByDay = waterRecords60d.groupBy { it.dayKey }.mapValues { (_, list) -> list.sumOf { it.amountMl } }
            todayWaterMl = waterByDay[todayKey] ?: 0
            val last7DayKeys = (0..6).map { DateEpochUtils.dayKeyDaysAgo(it, nowMs) }
            val hydration7dSummary = HydrationCalculator.summarize(
                recordsLast7Days = waterRecords60d.filter { it.dayKey >= dayKey7Start },
                goalMl = waterGoal,
                nowMs = nowMs
            )
            val weekLoggedDays = last7DayKeys.count { (waterByDay[it] ?: 0) > 0 }
            val prev7DayKeys = (7..13).map { DateEpochUtils.dayKeyDaysAgo(it, nowMs) }
            val prevWeekLoggedDays = prev7DayKeys.count { (waterByDay[it] ?: 0) > 0 }
            val monthLoggedMap = waterByDay.filterKeys { it in dayKey30Start..todayKey && it.isNotBlank() }
            val prevMonthLoggedMap = waterByDay.filterKeys { it in dayKeyPrev30Start..dayKeyPrev30End && it.isNotBlank() }

            if (weekLoggedDays > 0 || monthLoggedMap.isNotEmpty()) loggedModules++ else {
                missingOrExcluded += "Hydration has no entries logged yet."
            }

            weekWaterAvgMl = if (weekLoggedDays > 0) {
                last7DayKeys.sumOf { waterByDay[it] ?: 0 } / weekLoggedDays
            } else null
            prevWeekWaterAvgMl = if (prevWeekLoggedDays > 0) {
                prev7DayKeys.sumOf { waterByDay[it] ?: 0 } / prevWeekLoggedDays
            } else null
            val monthAvgMl = if (monthLoggedMap.isNotEmpty()) monthLoggedMap.values.sum() / monthLoggedMap.size else null
            val prevMonthAvgMl = if (prevMonthLoggedMap.isNotEmpty()) prevMonthLoggedMap.values.sum() / prevMonthLoggedMap.size else null

            val goalHitDays7d = last7DayKeys.count { (waterByDay[it] ?: 0) >= waterGoal }
            val todayPct = ((todayWaterMl.toFloat() / waterGoal) * 100).roundToInt()
            dailyBullets += "💧 Hydration: $todayWaterMl / $waterGoal ml today ($todayPct%, ${hydration7dSummary.streakDays}d streak)."

            if (weekWaterAvgMl != null) {
                weeklyBullets += "💧 Hydration: Avg $weekWaterAvgMl ml/logged day across $weekLoggedDays/7 days ($goalHitDays7d day(s) hit $waterGoal ml goal)."
            } else {
                weeklyBullets += "💧 Hydration: No water logs recorded in the last 7 days."
            }

            if (monthAvgMl != null) {
                val monthHits = monthLoggedMap.values.count { it >= waterGoal }
                val prevTxt = prevMonthAvgMl?.let { " vs $it ml prior 30d" } ?: ""
                monthlyBullets += "💧 Hydration: Avg $monthAvgMl ml across ${monthLoggedMap.size} logged day(s)$prevTxt ($monthHits goal hits)."
            } else {
                monthlyBullets += "💧 Hydration: No water records available in the past 30 days."
            }

            trends += buildHydrationTrend(weekWaterAvgMl, prevWeekWaterAvgMl, weekLoggedDays, prevWeekLoggedDays, waterGoal)

            val remainingMl = (waterGoal - todayWaterMl).coerceAtLeast(0)
            if (remainingMl > 0 && currentHour >= 14) {
                gaps += RoutineGapItem(
                    id = "gap_water",
                    emoji = "💧",
                    title = "Hydration goal incomplete",
                    recordedFact = "Recorded $todayWaterMl ml of your $waterGoal ml target today ($remainingMl ml remaining).",
                    wellnessSuggestion = "Keep a glass of water nearby and space 200–250 ml sips through the evening.",
                    moduleKey = "water_tracker",
                    priority = if (remainingMl > waterGoal / 2) 1 else 2
                )
            }
            plan += DailyWellnessPlanItem(
                id = "plan_water",
                timeBlock = if (currentHour < 13) "Morning" else "Midday",
                emoji = "💧",
                title = "Hydration pacing",
                recordedBasis = "Recorded: $todayWaterMl / $waterGoal ml today • 7d avg: ${weekWaterAvgMl ?: 0} ml",
                actionSuggestion = if (remainingMl > 0) {
                    "Aim for small 250 ml servings to close the remaining $remainingMl ml comfortably."
                } else {
                    "Today's hydration target is complete; sip lightly with meals or herbal tea."
                },
                moduleKey = "water_tracker"
            )
        }

        // --- 2. SLEEP & BEDTIME CONSISTENCY ---
        val weekSleepAvgHrs: Float?
        val prevWeekSleepAvgHrs: Float?
        if (!authorization.includeSleep) {
            weekSleepAvgHrs = null
            prevWeekSleepAvgHrs = null
            missingOrExcluded += "Sleep logs excluded by your AI privacy settings."
        } else {
            val sleepWithDay = sleepRecords.map { it to DateEpochUtils.todayDayKey(it.wakeEpochMs) }
            val todaySleep = sleepWithDay.filter { it.second == todayKey }.map { it.first }
            val weekSleep = sleepWithDay.filter { it.second in dayKey7Start..todayKey }.map { it.first }
            val prevWeekSleep = sleepWithDay.filter { it.second in dayKeyPrev7Start..dayKeyPrev7End }.map { it.first }
            val monthSleep = sleepWithDay.filter { it.second in dayKey30Start..todayKey }.map { it.first }
            val prevMonthSleep = sleepWithDay.filter { it.second in dayKeyPrev30Start..dayKeyPrev30End }.map { it.first }

            if (sleepRecords.isNotEmpty()) loggedModules++ else {
                missingOrExcluded += "Sleep has no nights logged yet."
            }

            val latestSleep = todaySleep.firstOrNull() ?: sleepRecords.firstOrNull()
            if (latestSleep != null) {
                val hrs = sleepDurationHours(latestSleep)
                val dayLbl = if (DateEpochUtils.todayDayKey(latestSleep.wakeEpochMs) == todayKey) "last night" else DateEpochUtils.todayDayKey(latestSleep.wakeEpochMs)
                dailyBullets += String.format(Locale.US, "😴 Sleep ($dayLbl): %.1f hrs recorded (quality %d/5).", hrs, latestSleep.qualityRating)
            } else {
                dailyBullets += "😴 Sleep: No sleep duration logged yet."
            }

            weekSleepAvgHrs = averageSleepHours(weekSleep)
            prevWeekSleepAvgHrs = averageSleepHours(prevWeekSleep)
            val bedtimeSpreadMins = computeBedtimeSpreadMinutes(weekSleep)

            if (weekSleepAvgHrs != null) {
                val spreadNote = bedtimeSpreadMins?.let {
                    if (it <= 45) " • steady bedtime (~${it}m spread)" else " • bedtime varied by ${it}m"
                } ?: " • log 2+ nights to measure bedtime consistency"
                weeklyBullets += String.format(
                    Locale.US,
                    "😴 Sleep: Avg %.1f hrs across %d logged night(s)%s.",
                    weekSleepAvgHrs,
                    weekSleep.size,
                    spreadNote
                )
            } else {
                weeklyBullets += "😴 Sleep: No nights logged in the last 7 days."
            }

            val monthSleepAvg = averageSleepHours(monthSleep)
            val prevMonthSleepAvg = averageSleepHours(prevMonthSleep)
            if (monthSleepAvg != null) {
                val prevTxt = prevMonthSleepAvg?.let { String.format(Locale.US, " vs %.1f hrs prior 30d", it) } ?: ""
                monthlyBullets += String.format(
                    Locale.US,
                    "😴 Sleep: Avg %.1f hrs across %d logged night(s)%s.",
                    monthSleepAvg,
                    monthSleep.size,
                    prevTxt
                )
            } else {
                monthlyBullets += "😴 Sleep: No sleep entries logged in the past 30 days."
            }

            trends += buildSleepTrend(weekSleepAvgHrs, prevWeekSleepAvgHrs, weekSleep.size, prevWeekSleep.size, bedtimeSpreadMins)

            if (weekSleep.size < 2) {
                gaps += RoutineGapItem(
                    id = "gap_sleep_logging",
                    emoji = "😴",
                    title = "Sleep history sparse",
                    recordedFact = "Only ${weekSleep.size} night(s) logged over the past 7 days.",
                    wellnessSuggestion = "Log wake and bedtime for 3+ nights to reveal sleep duration and consistency patterns.",
                    moduleKey = "exercise_tracker",
                    priority = 3
                )
            } else if ((bedtimeSpreadMins ?: 0) > 75 || (weekSleepAvgHrs != null && weekSleepAvgHrs < 6.8f)) {
                val fact = buildString {
                    append(String.format(Locale.US, "7d sleep avg %.1f hrs", weekSleepAvgHrs ?: 0f))
                    if ((bedtimeSpreadMins ?: 0) > 75) append(" with a ${bedtimeSpreadMins}m bedtime swing")
                    append(" across ${weekSleep.size} logged nights.")
                }
                gaps += RoutineGapItem(
                    id = "gap_sleep_rhythm",
                    emoji = "🌙",
                    title = "Sleep duration or bedtime shift",
                    recordedFact = fact,
                    wellnessSuggestion = "Consider a consistent 20-minute screen-free wind-down around the same hour each evening.",
                    moduleKey = "exercise_tracker",
                    priority = 1
                )
            }
            plan += DailyWellnessPlanItem(
                id = "plan_sleep",
                timeBlock = "Evening",
                emoji = "🌙",
                title = "Rest & bedtime rhythm",
                recordedBasis = weekSleepAvgHrs?.let {
                    String.format(Locale.US, "Recorded 7d avg: %.1f hrs (%d nights)", it, weekSleep.size)
                } ?: "Recorded: No recent sleep logs yet",
                actionSuggestion = "Target a steady lights-out window and dim bright notifications 30 minutes before bed.",
                moduleKey = "exercise_tracker"
            )
        }

        // --- 3. FITNESS, STEPS & WEARABLE VITALS ---
        if (!authorization.includeFitnessAndVitals) {
            missingOrExcluded += "Fitness, steps, and wearable vitals excluded by AI privacy settings."
        } else {
            val todayExercises = exercises.filter { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) == todayKey }
            val weekExercises = exercises.filter { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) in dayKey7Start..todayKey }
            val prevWeekExercises = exercises.filter { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) in dayKeyPrev7Start..dayKeyPrev7End }
            val monthExercises = exercises.filter { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) in dayKey30Start..todayKey }
            val prevMonthExercises = exercises.filter { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) in dayKeyPrev30Start..dayKeyPrev30End }

            val todayStepsCount = stepRecords.firstOrNull { it.dayKey == todayKey }?.stepCount
            val weekSteps = stepRecords.filter { it.dayKey in dayKey7Start..todayKey && it.stepCount > 0 }
            val prevWeekSteps = stepRecords.filter { it.dayKey in dayKeyPrev7Start..dayKeyPrev7End && it.stepCount > 0 }
            val monthSteps = stepRecords.filter { it.dayKey in dayKey30Start..todayKey && it.stepCount > 0 }

            val latestHr = vitalMetrics.firstOrNull { it.metricType == "HEART_RATE" }
            if (exercises.isNotEmpty() || stepRecords.isNotEmpty() || vitalMetrics.isNotEmpty()) {
                loggedModules++
            } else {
                missingOrExcluded += "Workouts, steps, and synced watch vitals have no entries yet."
            }

            val todayActiveMins = todayExercises.sumOf { it.durationMinutes }
            val hrTag = latestHr?.let { " • ❤️ ${it.valuePrimary.toInt()} bpm (${it.sourceDisplayName})" }.orEmpty()
            dailyBullets += "🏃 Movement: $todayActiveMins active min (${todayExercises.size} session(s)) • ${todayStepsCount ?: 0} steps today$hrTag."

            val weekMins = weekExercises.sumOf { it.durationMinutes }
            val prevWeekMins = prevWeekExercises.sumOf { it.durationMinutes }
            val avgWeekSteps = if (weekSteps.isNotEmpty()) weekSteps.sumOf { it.stepCount } / weekSteps.size else null
            val avgPrevWeekSteps = if (prevWeekSteps.isNotEmpty()) prevWeekSteps.sumOf { it.stepCount } / prevWeekSteps.size else null

            weeklyBullets += buildString {
                append("🏃 Fitness: ${weekExercises.size} session(s) totaling ${weekMins}m")
                if (avgWeekSteps != null) {
                    append(" • Avg $avgWeekSteps steps across ${weekSteps.size} day(s)")
                } else {
                    append(" • No step records in last 7 days")
                }
                append(".")
            }

            val monthMins = monthExercises.sumOf { it.durationMinutes }
            val prevMonthMins = prevMonthExercises.sumOf { it.durationMinutes }
            val avgMonthSteps = if (monthSteps.isNotEmpty()) monthSteps.sumOf { it.stepCount } / monthSteps.size else null
            monthlyBullets += buildString {
                append("🏃 Fitness: ${monthExercises.size} workout(s) (${monthMins}m)")
                if (prevMonthExercises.isNotEmpty()) append(" vs ${prevMonthExercises.size} (${prevMonthMins}m) prior 30d")
                if (avgMonthSteps != null) append(" • Avg $avgMonthSteps steps across ${monthSteps.size} day(s)")
                append(".")
            }

            trends += buildMovementTrend(weekExercises.size, weekMins, prevWeekExercises.size, prevWeekMins)
            trends += buildStepsTrend(avgWeekSteps, avgPrevWeekSteps, weekSteps.size, prevWeekSteps.size)

            if (weekExercises.isEmpty() && (avgWeekSteps ?: 0) < 4000) {
                gaps += RoutineGapItem(
                    id = "gap_movement",
                    emoji = "🏃",
                    title = "Low movement logged this week",
                    recordedFact = "Recorded 0 workouts and ${avgWeekSteps ?: 0} avg steps in the past 7 days.",
                    wellnessSuggestion = "Try a short 15-minute walk or gentle yoga flow today to rebuild daily movement.",
                    moduleKey = "exercise_tracker",
                    priority = 2
                )
            }
            plan += DailyWellnessPlanItem(
                id = "plan_movement",
                timeBlock = "Midday",
                emoji = "🏃",
                title = "Activity & step target",
                recordedBasis = "Recorded today: ${todayStepsCount ?: 0} steps, ${todayActiveMins}m workout (${weekExercises.size} this week)",
                actionSuggestion = if (todayActiveMins == 0) {
                    "Schedule a brisk 15–20 minute walk or mobility flow to break up sitting time."
                } else {
                    "Great job logging ${todayActiveMins}m today—stay loose with light afternoon stretching."
                },
                moduleKey = "exercise_tracker"
            )
        }

        // --- 4. MEDICINES & DOCTOR FOLLOW-UPS ---
        if (!authorization.includeMedicinesAndVisits) {
            missingOrExcluded += "Medicines and doctor visits excluded by AI privacy settings."
        } else {
            val activeMeds = medicines.filter { it.isActive }
            val expectedDailyDoses = activeMeds.sumOf { it.dosesPerDay.coerceAtLeast(1) }
            val todayTaken = medicineIntakes60d.count { it.dayKey == todayKey && it.status == "Taken" }
            val weekTaken = medicineIntakes60d.count { it.dayKey in dayKey7Start..todayKey && it.status == "Taken" }
            val prevWeekTaken = medicineIntakes60d.count { it.dayKey in dayKeyPrev7Start..dayKeyPrev7End && it.status == "Taken" }
            val monthTaken = medicineIntakes60d.count { it.dayKey in dayKey30Start..todayKey && it.status == "Taken" }
            val pendingVisits = consultations.filter { it.followUpEpochDay != null && !it.isFollowUpCompleted }
            val overdueVisits = pendingVisits.filter { (it.followUpEpochDay ?: Long.MAX_VALUE) < todayEpochDay }

            if (activeMeds.isNotEmpty() || medicineIntakes60d.isNotEmpty() || consultations.isNotEmpty()) {
                loggedModules++
            }

            if (activeMeds.isNotEmpty()) {
                dailyBullets += "💊 Medicines: $todayTaken/$expectedDailyDoses scheduled dose(s) logged today across ${activeMeds.size} active medicine(s)."
                val expectedWeek = expectedDailyDoses * 7
                val adherencePct = if (expectedWeek > 0) ((weekTaken.toFloat() / expectedWeek) * 100).roundToInt().coerceIn(0, 100) else 0
                weeklyBullets += "💊 Medicine adherence: $weekTaken/$expectedWeek logged doses in 7 days ($adherencePct%)."
                val expectedMonth = expectedDailyDoses * 30
                val monthPct = if (expectedMonth > 0) ((monthTaken.toFloat() / expectedMonth) * 100).roundToInt().coerceIn(0, 100) else 0
                monthlyBullets += "💊 Medicine adherence: $monthTaken/$expectedMonth doses logged over 30 days ($monthPct%)."

                val direction = when {
                    prevWeekTaken == 0 && weekTaken == 0 -> TrendDirection.INSUFFICIENT_DATA
                    weekTaken > prevWeekTaken -> TrendDirection.IMPROVED
                    weekTaken < prevWeekTaken -> TrendDirection.DECLINED
                    else -> TrendDirection.STEADY
                }
                trends += TrendMetricComparison(
                    id = "medicines",
                    emoji = "💊",
                    title = "Medicine Check-Ins",
                    currentValueLabel = "$weekTaken doses",
                    previousValueLabel = if (prevWeekTaken > 0) "$prevWeekTaken doses" else "No prior logs",
                    deltaBadge = when {
                        prevWeekTaken == 0 && weekTaken == 0 -> "No logs"
                        prevWeekTaken == 0 -> "$adherencePct% logged"
                        else -> {
                            val diff = weekTaken - prevWeekTaken
                            if (diff > 0) "+$diff vs last wk" else if (diff < 0) "$diff vs last wk" else "Matched"
                        }
                    },
                    direction = direction,
                    provenanceNote = "Based on $weekTaken recorded intakes vs $expectedWeek scheduled weekly slots",
                    moduleKey = "medical_care"
                )

                val remainingDoses = (expectedDailyDoses - todayTaken).coerceAtLeast(0)
                if (remainingDoses > 0 && currentHour >= 12) {
                    gaps += RoutineGapItem(
                        id = "gap_medicines",
                        emoji = "💊",
                        title = "$remainingDoses dose(s) unlogged today",
                        recordedFact = "Logged $todayTaken of $expectedDailyDoses active daily medicine dose(s) today.",
                        wellnessSuggestion = "Check your Doctors & Medicines schedule and log taken doses (never alter dosages without your doctor).",
                        moduleKey = "medical_care",
                        priority = 1
                    )
                }

                val firstMed = activeMeds.first()
                plan += DailyWellnessPlanItem(
                    id = "plan_medicines",
                    timeBlock = "Morning",
                    emoji = "💊",
                    title = "Medication schedule check",
                    recordedBasis = "Recorded: ${activeMeds.size} active medicine(s) • $todayTaken/$expectedDailyDoses logged today",
                    actionSuggestion = "Follow your saved schedule (next listed: ${firstMed.name} at ${firstMed.scheduleTime24h}, ${firstMed.mealRelation}).",
                    moduleKey = "medical_care"
                )
            }

            if (overdueVisits.isNotEmpty()) {
                val nearestOverdue = overdueVisits.minByOrNull { it.followUpEpochDay ?: Long.MAX_VALUE }
                gaps += RoutineGapItem(
                    id = "gap_doctor_overdue",
                    emoji = "🩺",
                    title = "Doctor follow-up date passed",
                    recordedFact = "Dr. ${nearestOverdue?.doctorName ?: "Doctor"} follow-up was scheduled for ${DateEpochUtils.formatEpochDayShort(nearestOverdue?.followUpEpochDay ?: todayEpochDay)}.",
                    wellnessSuggestion = "Review your visit note and reschedule or mark the follow-up completed.",
                    moduleKey = "medical_care",
                    priority = 1
                )
            }
        }

        // --- 5. NUTRITION ---
        if (!authorization.includeNutrition) {
            missingOrExcluded += "Nutrition logs excluded by AI privacy settings."
        } else {
            val todayDiet = dietRecords.filter { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) == todayKey }
            val weekDiet = dietRecords.filter { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) in dayKey7Start..todayKey }
            val prevWeekDiet = dietRecords.filter { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) in dayKeyPrev7Start..dayKeyPrev7End }
            val monthDiet = dietRecords.filter { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) in dayKey30Start..todayKey }

            if (dietRecords.isNotEmpty()) loggedModules++ else {
                missingOrExcluded += "Nutrition has no meal logs recorded yet."
            }

            val todayKcal = todayDiet.sumOf { it.calories }
            val todayProtein = todayDiet.sumOf { it.proteinGrams }
            if (todayDiet.isNotEmpty()) {
                dailyBullets += "🥗 Nutrition: $todayKcal kcal • ${todayProtein}g protein across ${todayDiet.size} meal(s) today."
            } else {
                dailyBullets += "🥗 Nutrition: No meals logged yet today."
            }

            val weekMealDays = weekDiet.map { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) }.distinct().size
            if (weekDiet.isNotEmpty() && weekMealDays > 0) {
                val avgKcalPerLoggedDay = weekDiet.sumOf { it.calories } / weekMealDays
                val avgProteinPerLoggedDay = weekDiet.sumOf { it.proteinGrams } / weekMealDays
                weeklyBullets += "🥗 Nutrition: Avg $avgKcalPerLoggedDay kcal & ${avgProteinPerLoggedDay}g protein/logged day ($weekMealDays day(s) logged)."
            } else {
                weeklyBullets += "🥗 Nutrition: No meals logged in the past 7 days."
            }

            val monthMealDays = monthDiet.map { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) }.distinct().size
            if (monthDiet.isNotEmpty() && monthMealDays > 0) {
                monthlyBullets += "🥗 Nutrition: ${monthDiet.size} meal(s) across $monthMealDays logged day(s) (${monthDiet.sumOf { it.calories } / monthMealDays} kcal/day avg)."
            }

            if (todayDiet.isEmpty() && currentHour >= 13) {
                gaps += RoutineGapItem(
                    id = "gap_nutrition",
                    emoji = "🥗",
                    title = "No meals logged today",
                    recordedFact = "0 meals recorded so far today (7d: ${weekDiet.size} entries).",
                    wellnessSuggestion = "Log a quick meal or snack via voice or the Diet tracker to keep energy and protein insights accurate.",
                    moduleKey = "diet_tracker",
                    priority = 3
                )
            }
            if (prevWeekDiet.isNotEmpty() && weekDiet.isNotEmpty()) {
                val currAvgProt = weekDiet.sumOf { it.proteinGrams } / weekMealDays.coerceAtLeast(1)
                val prevDays = prevWeekDiet.map { DateEpochUtils.todayDayKey(it.loggedAtEpochMs) }.distinct().size.coerceAtLeast(1)
                val prevAvgProt = prevWeekDiet.sumOf { it.proteinGrams } / prevDays
                val diff = currAvgProt - prevAvgProt
                trends += TrendMetricComparison(
                    id = "nutrition",
                    emoji = "🥗",
                    title = "Daily Protein",
                    currentValueLabel = "${currAvgProt}g / day",
                    previousValueLabel = "${prevAvgProt}g / day",
                    deltaBadge = if (diff > 0) "+${diff}g/d" else if (diff < 0) "${diff}g/d" else "Steady",
                    direction = when {
                        diff >= 5 -> TrendDirection.IMPROVED
                        diff <= -5 -> TrendDirection.DECLINED
                        else -> TrendDirection.STEADY
                    },
                    provenanceNote = "Averaged strictly across $weekMealDays vs $prevDays logged meal days",
                    moduleKey = "diet_tracker"
                )
            }
        }

        // --- 6. DIGITAL WELLBEING & SELF-CARE ---
        if (!authorization.includeWellbeingAndSelfCare) {
            missingOrExcluded += "Screen time, focus, and self-care rituals excluded by AI privacy settings."
        } else {
            val todayWb = wellbeingRecords.firstOrNull { it.dayKey == todayKey }
            val weekWb = wellbeingRecords.filter { it.dayKey in dayKey7Start..todayKey }
            val prevWeekWb = wellbeingRecords.filter { it.dayKey in dayKeyPrev7Start..dayKeyPrev7End }
            val monthWb = wellbeingRecords.filter { it.dayKey in dayKey30Start..todayKey }
            val weekBeauty = beautyRecords.filter { DateEpochUtils.todayDayKey(it.completedAtEpochMs) in dayKey7Start..todayKey }

            if (wellbeingRecords.isNotEmpty() || beautyRecords.isNotEmpty()) loggedModules++

            if (todayWb != null) {
                dailyBullets += "📵 Digital Wellbeing: ${todayWb.screenTimeMinutes}/${todayWb.mindfulGoalMinutes}m screen • ${todayWb.focusSessionsCount} focus block(s)."
            }
            if (weekWb.isNotEmpty()) {
                val avgScreen = weekWb.sumOf { it.screenTimeMinutes } / weekWb.size
                val focusTotal = weekWb.sumOf { it.focusSessionsCount }
                weeklyBullets += "📵 Screen & Self-Care: Avg ${avgScreen}m/day across ${weekWb.size} day(s) • $focusTotal focus blocks • ${weekBeauty.size} beauty ritual(s)."
            }
            if (monthWb.isNotEmpty()) {
                val avgMonthScreen = monthWb.sumOf { it.screenTimeMinutes } / monthWb.size
                monthlyBullets += "📵 Digital Wellbeing: Avg ${avgMonthScreen}m/day across ${monthWb.size} recorded day(s)."
            }

            if (weekWb.isNotEmpty()) {
                val currAvgScreen = weekWb.sumOf { it.screenTimeMinutes } / weekWb.size
                val prevAvgScreen = if (prevWeekWb.isNotEmpty()) prevWeekWb.sumOf { it.screenTimeMinutes } / prevWeekWb.size else null
                trends += TrendMetricComparison(
                    id = "wellbeing",
                    emoji = "📵",
                    title = "Screen Time",
                    currentValueLabel = "${currAvgScreen}m / day",
                    previousValueLabel = prevAvgScreen?.let { "${it}m / day" } ?: "No prior logs",
                    deltaBadge = if (prevAvgScreen == null) {
                        "${weekWb.size}d logged"
                    } else {
                        val diff = currAvgScreen - prevAvgScreen
                        if (diff < 0) "${diff}m (lower)" else if (diff > 0) "+${diff}m higher" else "Matched"
                    },
                    direction = when {
                        prevAvgScreen == null -> TrendDirection.INSUFFICIENT_DATA
                        currAvgScreen <= prevAvgScreen - 10 -> TrendDirection.IMPROVED
                        currAvgScreen >= prevAvgScreen + 15 -> TrendDirection.DECLINED
                        else -> TrendDirection.STEADY
                    },
                    provenanceNote = "From ${weekWb.size} recorded day(s)" + if (prevWeekWb.isNotEmpty()) " vs ${prevWeekWb.size} prior day(s)" else "",
                    moduleKey = "digital_wellbeing"
                )
            }

            if (todayWb != null && todayWb.screenTimeMinutes > todayWb.mindfulGoalMinutes) {
                gaps += RoutineGapItem(
                    id = "gap_screen_cap",
                    emoji = "📵",
                    title = "Daily screen cap exceeded",
                    recordedFact = "Recorded ${todayWb.screenTimeMinutes}m screen time today vs ${todayWb.mindfulGoalMinutes}m mindful goal.",
                    wellnessSuggestion = "Try one 20-minute focus block without social/video apps or enable your wind-down cue.",
                    moduleKey = "digital_wellbeing",
                    priority = 2
                )
            }
        }

        // --- 7. CYCLE CARE (IF ENABLED & AUTHORIZED) ---
        if (cycleEnabled) {
            if (!authorization.includeCycle) {
                missingOrExcluded += "Cycle records excluded by your AI privacy settings."
            } else if (cycleInsights?.currentCycleDay != null) {
                loggedModules++
                dailyBullets += "🌸 Cycle Care: Estimated Cycle Day ${cycleInsights.currentCycleDay} (${cycleInsights.currentPhase.label}) — ${cycleInsights.currentPhase.guidance}"
                cycleInsights.daysUntilNextPeriod?.let { days ->
                    weeklyBullets += "🌸 Cycle estimate: Next cycle predicted in ~$days day(s) based on your logged history (not clinical fertility advice)."
                }
            }
        }

        // --- 8. ALERTS ADHERENCE SUMMARY ---
        if (alertMetrics.totalAlarmsCount > 0) {
            dailyBullets += "🔔 Alert Cadence: ${alertMetrics.enabledAlarmsCount} active alarm(s) • ${alertMetrics.adherencePercent}% completion rate."
        }

        val greetingName = profile?.fullName?.takeIf { it.isNotBlank() } ?: "friend"
        val tone = aiPref?.preferredTone ?: "Gentle"
        val dailyHeadline = when {
            loggedModules == 0 -> "Ready for your first check-in, $greetingName"
            gaps.any { it.priority == 1 } -> "$greetingName: ${gaps.count { it.priority == 1 }} high-priority routine check-in(s) today"
            tone == "Energetic" -> "Strong momentum today, $greetingName — $loggedModules module(s) active!"
            else -> "Steady daily overview for $greetingName ($loggedModules active module(s))"
        }

        val weeklyHeadline = buildWeeklyHeadline(trends, loggedModules)
        val monthlyHeadline = if (loggedModules > 0) {
            "30-day overview across $loggedModules tracked wellness module(s)"
        } else {
            "Monthly trends unlock as you log daily habits"
        }

        val sortedGaps = gaps.sortedBy { it.priority }
        val sortedPlan = plan.sortedBy { blockOrder(it.timeBlock) }

        val groundedPromptContext = buildGroundedPromptContext(
            profile = profile,
            authorization = authorization,
            dailyHeadline = dailyHeadline,
            dailyBullets = dailyBullets,
            weeklyHeadline = weeklyHeadline,
            weeklyBullets = weeklyBullets,
            monthlyHeadline = monthlyHeadline,
            monthlyBullets = monthlyBullets,
            trends = trends,
            gaps = sortedGaps,
            plan = sortedPlan,
            missingOrExcluded = missingOrExcluded,
            connectedSources = if (authorization.includeFitnessAndVitals) connectedSources else emptyList()
        )

        return PersonalHealthIntelligenceSnapshot(
            generatedAtEpochMs = nowMs,
            authorization = authorization,
            authorizedModuleCount = authorizedModules,
            loggedModuleCount = loggedModules,
            dailyBriefingHeadline = dailyHeadline,
            dailyBriefingBullets = dailyBullets,
            weeklySummaryHeadline = weeklyHeadline,
            weeklySummaryBullets = weeklyBullets,
            monthlySummaryHeadline = monthlyHeadline,
            monthlySummaryBullets = monthlyBullets,
            trends = trends,
            routineGaps = sortedGaps,
            dailyPlan = sortedPlan,
            missingOrExcludedNotes = missingOrExcluded,
            groundedAiPromptContext = groundedPromptContext
        )
    }

    /**
     * Privacy-conscious offline answer helper grounded in the computed intelligence snapshot.
     * Never invents metrics or provides medical diagnoses.
     */
    fun answerGroundedQuestion(
        prompt: String,
        snapshot: PersonalHealthIntelligenceSnapshot
    ): String {
        val q = prompt.lowercase(Locale.US)
        return when {
            "week" in q || "routine" in q || "summary" in q || "trend" in q -> {
                val facts = snapshot.weeklySummaryBullets.take(4).joinToString(" ")
                val gaps = snapshot.routineGaps.firstOrNull()?.let {
                    " Gap noticed: ${it.recordedFact} Suggestion: ${it.wellnessSuggestion}"
                }.orEmpty()
                if (facts.isBlank()) {
                    "📊 I don't have enough logged records for this week yet. Log hydration, sleep, or workouts to generate a grounded weekly summary."
                } else {
                    "📊 Recorded this week: $facts$gaps (Wellness guidance only, not a medical diagnosis.)"
                }
            }
            "month" in q -> {
                val facts = snapshot.monthlySummaryBullets.take(4).joinToString(" ")
                if (facts.isBlank()) {
                    "📅 No 30-day history is available yet in your authorized modules."
                } else {
                    "📅 30-day recorded summary: $facts (Informational only, not clinical advice.)"
                }
            }
            "plan" in q || "today" in q -> {
                val planText = snapshot.dailyPlan.joinToString(" • ") { "${it.timeBlock}: ${it.actionSuggestion}" }
                if (planText.isBlank()) {
                    "🗓️ Enable at least one module and log a metric to build a personalized daily wellness plan."
                } else {
                    "🗓️ Suggested plan based on your records: $planText"
                }
            }
            "sleep" in q || "bed" in q -> {
                if (!snapshot.authorization.includeSleep) {
                    "🔒 Sleep records are currently excluded by your AI privacy settings."
                } else {
                    val sleepTrend = snapshot.trends.firstOrNull { it.id == "sleep" }
                    val line = snapshot.weeklySummaryBullets.firstOrNull { it.startsWith("😴") }
                        ?: "No sleep records logged yet."
                    val trendDetail = sleepTrend?.let { " (${it.deltaBadge}; ${it.provenanceNote})" }.orEmpty()
                    "😴 Recorded sleep data: $line$trendDetail"
                }
            }
            "miss" in q || "gap" in q || "incomplete" in q -> {
                if (snapshot.routineGaps.isEmpty()) {
                    "✅ No missed routines or overdue items detected in your authorized logs right now."
                } else {
                    val topGaps = snapshot.routineGaps.take(3).joinToString(" • ") { "${it.emoji} ${it.recordedFact}" }
                    "🔎 Observed gaps from your logs: $topGaps"
                }
            }
            else -> {
                val daily = snapshot.dailyBriefingBullets.take(3).joinToString(" ")
                val coverage = snapshot.missingOrExcludedNotes.firstOrNull()?.let { " Note: $it" }.orEmpty()
                "✨ Grounded check-in: ${daily.ifBlank { "No records logged yet." }}$coverage"
            }
        }
    }

    private fun sleepDurationHours(record: SleepRecordEntity): Float {
        return ((record.wakeEpochMs - record.bedtimeEpochMs).coerceAtLeast(0L) / 3_600_000f)
    }

    private fun averageSleepHours(records: List<SleepRecordEntity>): Float? {
        if (records.isEmpty()) return null
        return records.map { sleepDurationHours(it) }.average().toFloat()
    }

    private fun computeBedtimeSpreadMinutes(records: List<SleepRecordEntity>): Int? {
        if (records.size < 2) return null
        val shiftedMinutes = records.map { rec ->
            val cal = Calendar.getInstance().apply { timeInMillis = rec.bedtimeEpochMs }
            val h = cal.get(Calendar.HOUR_OF_DAY)
            val m = cal.get(Calendar.MINUTE)
            val adjustedHour = if (h >= 12) h - 24 else h
            adjustedHour * 60 + m
        }
        val minVal = shiftedMinutes.minOrNull() ?: return null
        val maxVal = shiftedMinutes.maxOrNull() ?: return null
        return (maxVal - minVal).coerceAtLeast(0)
    }

    private fun buildHydrationTrend(
        currAvg: Int?,
        prevAvg: Int?,
        currDays: Int,
        prevDays: Int,
        goalMl: Int
    ): TrendMetricComparison {
        if (currAvg == null) {
            return TrendMetricComparison(
                id = "hydration",
                emoji = "💧",
                title = "Hydration Consistency",
                currentValueLabel = "No logs (7d)",
                previousValueLabel = prevAvg?.let { "$it ml/day" } ?: "No prior logs",
                deltaBadge = "No logs",
                direction = TrendDirection.INSUFFICIENT_DATA,
                provenanceNote = "No hydration records in last 7 days (goal: $goalMl ml)",
                moduleKey = "water_tracker"
            )
        }
        if (prevAvg == null || prevAvg == 0) {
            return TrendMetricComparison(
                id = "hydration",
                emoji = "💧",
                title = "Hydration Consistency",
                currentValueLabel = "$currAvg ml / day",
                previousValueLabel = "No prior logs",
                deltaBadge = "$currDays/7d logged",
                direction = TrendDirection.INSUFFICIENT_DATA,
                provenanceNote = "Averaged across $currDays logged day(s); need prior 7d for comparison",
                moduleKey = "water_tracker"
            )
        }
        val diff = currAvg - prevAvg
        val pct = ((diff.toFloat() / prevAvg) * 100f).roundToInt()
        val direction = when {
            pct >= 5 -> TrendDirection.IMPROVED
            pct <= -5 -> TrendDirection.DECLINED
            else -> TrendDirection.STEADY
        }
        return TrendMetricComparison(
            id = "hydration",
            emoji = "💧",
            title = "Hydration Consistency",
            currentValueLabel = "$currAvg ml / day",
            previousValueLabel = "$prevAvg ml / day",
            deltaBadge = if (pct > 0) "+$pct%" else "$pct%",
            direction = direction,
            provenanceNote = "From $currDays logged day(s) vs $prevDays prior logged day(s)",
            moduleKey = "water_tracker"
        )
    }

    private fun buildSleepTrend(
        currAvg: Float?,
        prevAvg: Float?,
        currNights: Int,
        prevNights: Int,
        bedtimeSpreadMins: Int?
    ): TrendMetricComparison {
        if (currAvg == null) {
            return TrendMetricComparison(
                id = "sleep",
                emoji = "😴",
                title = "Sleep Duration",
                currentValueLabel = "No logs (7d)",
                previousValueLabel = prevAvg?.let { String.format(Locale.US, "%.1fh", it) } ?: "No prior logs",
                deltaBadge = "No logs",
                direction = TrendDirection.INSUFFICIENT_DATA,
                provenanceNote = "No sleep entries in the last 7 days",
                moduleKey = "exercise_tracker"
            )
        }
        val currLabel = String.format(Locale.US, "%.1f hrs", currAvg)
        if (prevAvg == null) {
            return TrendMetricComparison(
                id = "sleep",
                emoji = "😴",
                title = "Sleep Duration",
                currentValueLabel = currLabel,
                previousValueLabel = "No prior logs",
                deltaBadge = "$currNights night(s)",
                direction = TrendDirection.INSUFFICIENT_DATA,
                provenanceNote = buildString {
                    append("Recorded $currNights night(s) this week; prior week unavailable")
                    bedtimeSpreadMins?.let { append(" • bedtime spread ${it}m") }
                },
                moduleKey = "exercise_tracker"
            )
        }
        val diffHrs = currAvg - prevAvg
        val direction = when {
            diffHrs >= 0.3f -> TrendDirection.IMPROVED
            diffHrs <= -0.3f -> TrendDirection.DECLINED
            else -> TrendDirection.STEADY
        }
        val deltaSign = if (diffHrs >= 0f) "+" else ""
        return TrendMetricComparison(
            id = "sleep",
            emoji = "😴",
            title = "Sleep Duration",
            currentValueLabel = currLabel,
            previousValueLabel = String.format(Locale.US, "%.1f hrs", prevAvg),
            deltaBadge = String.format(Locale.US, "%s%.1fh", deltaSign, diffHrs),
            direction = direction,
            provenanceNote = "Recorded $currNights night(s) vs $prevNights prior night(s)" +
                (bedtimeSpreadMins?.let { " • ${it}m bedtime spread" } ?: ""),
            moduleKey = "exercise_tracker"
        )
    }

    private fun buildMovementTrend(
        currWorkouts: Int,
        currMins: Int,
        prevWorkouts: Int,
        prevMins: Int
    ): TrendMetricComparison {
        if (currWorkouts == 0 && prevWorkouts == 0) {
            return TrendMetricComparison(
                id = "movement",
                emoji = "🏃",
                title = "Workout Minutes",
                currentValueLabel = "0m (7d)",
                previousValueLabel = "0m",
                deltaBadge = "No sessions",
                direction = TrendDirection.INSUFFICIENT_DATA,
                provenanceNote = "No workouts logged in the last 14 days",
                moduleKey = "exercise_tracker"
            )
        }
        if (prevWorkouts == 0) {
            return TrendMetricComparison(
                id = "movement",
                emoji = "🏃",
                title = "Workout Minutes",
                currentValueLabel = "${currMins}m ($currWorkouts sessions)",
                previousValueLabel = "No prior sessions",
                deltaBadge = "+${currMins}m",
                direction = TrendDirection.IMPROVED,
                provenanceNote = "Recorded $currWorkouts workout(s) this week; 0 prior week",
                moduleKey = "exercise_tracker"
            )
        }
        val diffMins = currMins - prevMins
        val direction = when {
            diffMins >= 15 -> TrendDirection.IMPROVED
            diffMins <= -15 -> TrendDirection.DECLINED
            else -> TrendDirection.STEADY
        }
        return TrendMetricComparison(
            id = "movement",
            emoji = "🏃",
            title = "Workout Minutes",
            currentValueLabel = "${currMins}m ($currWorkouts)",
            previousValueLabel = "${prevMins}m ($prevWorkouts)",
            deltaBadge = if (diffMins > 0) "+${diffMins}m" else if (diffMins < 0) "${diffMins}m" else "Steady",
            direction = direction,
            provenanceNote = "Logged $currWorkouts session(s) vs $prevWorkouts prior week",
            moduleKey = "exercise_tracker"
        )
    }

    private fun buildStepsTrend(
        currAvgSteps: Int?,
        prevAvgSteps: Int?,
        currDays: Int,
        prevDays: Int
    ): TrendMetricComparison {
        if (currAvgSteps == null) {
            return TrendMetricComparison(
                id = "steps",
                emoji = "👟",
                title = "Daily Steps",
                currentValueLabel = "No logs (7d)",
                previousValueLabel = prevAvgSteps?.let { "$it steps" } ?: "No prior logs",
                deltaBadge = "No logs",
                direction = TrendDirection.INSUFFICIENT_DATA,
                provenanceNote = "Log steps or sync Health Connect to track trends",
                moduleKey = "exercise_tracker"
            )
        }
        if (prevAvgSteps == null || prevAvgSteps == 0) {
            return TrendMetricComparison(
                id = "steps",
                emoji = "👟",
                title = "Daily Steps",
                currentValueLabel = "$currAvgSteps / day",
                previousValueLabel = "No prior logs",
                deltaBadge = "${currDays}d logged",
                direction = TrendDirection.INSUFFICIENT_DATA,
                provenanceNote = "Averaged over $currDays logged day(s)",
                moduleKey = "exercise_tracker"
            )
        }
        val diff = currAvgSteps - prevAvgSteps
        val pct = ((diff.toFloat() / prevAvgSteps) * 100f).roundToInt()
        val direction = when {
            pct >= 5 -> TrendDirection.IMPROVED
            pct <= -5 -> TrendDirection.DECLINED
            else -> TrendDirection.STEADY
        }
        return TrendMetricComparison(
            id = "steps",
            emoji = "👟",
            title = "Daily Steps",
            currentValueLabel = "$currAvgSteps / day",
            previousValueLabel = "$prevAvgSteps / day",
            deltaBadge = if (pct > 0) "+$pct%" else "$pct%",
            direction = direction,
            provenanceNote = "From $currDays logged day(s) vs $prevDays prior day(s)",
            moduleKey = "exercise_tracker"
        )
    }

    private fun buildWeeklyHeadline(
        trends: List<TrendMetricComparison>,
        loggedModules: Int
    ): String {
        if (loggedModules == 0) return "Log records across modules to unlock weekly trend comparisons"
        val improvedCount = trends.count { it.direction == TrendDirection.IMPROVED }
        val declinedCount = trends.count { it.direction == TrendDirection.DECLINED }
        return when {
            improvedCount > 0 && declinedCount == 0 -> "$improvedCount wellness trend(s) improved this week"
            improvedCount > 0 && declinedCount > 0 -> "$improvedCount metric(s) improved • $declinedCount need attention"
            declinedCount > 0 -> "$declinedCount metric(s) dipped vs last week — check gaps below"
            else -> "Weekly check-in across $loggedModules tracked wellness module(s)"
        }
    }

    private fun blockOrder(timeBlock: String): Int = when (timeBlock) {
        "Morning" -> 0
        "Midday" -> 1
        else -> 2
    }

    private fun buildGroundedPromptContext(
        profile: UserProfileEntity?,
        authorization: AiDataAuthorization,
        dailyHeadline: String,
        dailyBullets: List<String>,
        weeklyHeadline: String,
        weeklyBullets: List<String>,
        monthlyHeadline: String,
        monthlyBullets: List<String>,
        trends: List<TrendMetricComparison>,
        gaps: List<RoutineGapItem>,
        plan: List<DailyWellnessPlanItem>,
        missingOrExcluded: List<String>,
        connectedSources: List<ConnectedDeviceSourceEntity>
    ): String = buildString {
        appendLine("=== AUTHORIZED LOCAL WELLNESS RECORDS (DO NOT INVENT DATA) ===")
        profile?.let {
            appendLine("Profile: ${it.fullName} • Water Goal: ${it.dailyWaterGoalMl} ml/day • Activity: ${it.activityLevel}")
        }
        appendLine("Authorized Categories: Hydration=${authorization.includeHydration}, Sleep=${authorization.includeSleep}, Fitness=${authorization.includeFitnessAndVitals}, Nutrition=${authorization.includeNutrition}, Medicines=${authorization.includeMedicinesAndVisits}, Wellbeing=${authorization.includeWellbeingAndSelfCare}, Cycle=${profile?.cycleTrackingEnabled == true && authorization.includeCycle}")
        if (connectedSources.isNotEmpty()) {
            appendLine("Synced Wearables: ${connectedSources.joinToString(", ") { it.displayName }}")
        }
        appendLine()
        appendLine("[TODAY - RECORDED]")
        appendLine("Headline: $dailyHeadline")
        if (dailyBullets.isEmpty()) appendLine("- No today entries logged or authorized.") else dailyBullets.forEach { appendLine("- $it") }

        appendLine()
        appendLine("[WEEKLY 7D - RECORDED]")
        appendLine("Headline: $weeklyHeadline")
        if (weeklyBullets.isEmpty()) appendLine("- No 7-day entries logged or authorized.") else weeklyBullets.forEach { appendLine("- $it") }

        appendLine()
        appendLine("[MONTHLY 30D - RECORDED]")
        appendLine("Headline: $monthlyHeadline")
        if (monthlyBullets.isEmpty()) appendLine("- No 30-day entries logged or authorized.") else monthlyBullets.forEach { appendLine("- $it") }

        if (trends.isNotEmpty()) {
            appendLine()
            appendLine("[TREND COMPARISONS]")
            trends.forEach { t ->
                appendLine("- ${t.title}: current=${t.currentValueLabel}, previous=${t.previousValueLabel}, delta=${t.deltaBadge}, status=${t.direction.label} (${t.provenanceNote})")
            }
        }

        if (gaps.isNotEmpty()) {
            appendLine()
            appendLine("[ROUTINE GAPS & INCOMPLETE GOALS]")
            gaps.forEach { g ->
                appendLine("- ${g.title}: ${g.recordedFact}")
            }
        }

        if (plan.isNotEmpty()) {
            appendLine()
            appendLine("[DAILY PLAN SIGNALS]")
            plan.forEach { p ->
                appendLine("- ${p.timeBlock} ${p.title}: ${p.recordedBasis}")
            }
        }

        if (missingOrExcluded.isNotEmpty()) {
            appendLine()
            appendLine("[UNAVAILABLE OR EXCLUDED BY PRIVACY - DO NOT FABRICATE]")
            missingOrExcluded.forEach { note -> appendLine("- $note") }
        }
    }
}
