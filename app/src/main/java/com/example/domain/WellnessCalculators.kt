package com.example.domain

import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.WaterRecordEntity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Date helpers compatible with minSdk 24 without requiring coreLibraryDesugaring.
 */
object DateEpochUtils {
    private val dayKeyFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)
    private val shortLabelFormat = SimpleDateFormat("EEE", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
    private val fullDateFormat = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("h:mm a", Locale.getDefault())
    private val monthYearFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())

    fun todayDayKey(nowMs: Long = System.currentTimeMillis()): String {
        return dayKeyFormat.format(Date(nowMs))
    }

    fun dayKeyDaysAgo(daysAgo: Int, nowMs: Long = System.currentTimeMillis()): String {
        val cal = Calendar.getInstance().apply {
            timeInMillis = nowMs
            add(Calendar.DAY_OF_YEAR, -daysAgo)
        }
        return dayKeyFormat.format(cal.time)
    }

    fun shortWeekdayForDayKey(dayKey: String): String {
        val date = runCatching { dayKeyFormat.parse(dayKey) }.getOrNull() ?: return dayKey
        return shortLabelFormat.format(date)
    }

    fun todayEpochDay(nowMs: Long = System.currentTimeMillis()): Long {
        val localCal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(
                localCal.get(Calendar.YEAR),
                localCal.get(Calendar.MONTH),
                localCal.get(Calendar.DAY_OF_MONTH)
            )
        }
        return TimeUnit.MILLISECONDS.toDays(utcCal.timeInMillis)
    }

    fun epochDayFromYmd(year: Int, monthZeroBased: Int, dayOfMonth: Int): Long {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(year, monthZeroBased, dayOfMonth)
        }
        return TimeUnit.MILLISECONDS.toDays(utcCal.timeInMillis)
    }

    fun ymdFromEpochDay(epochDay: Long): Triple<Int, Int, Int> {
        val utcCal = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = TimeUnit.DAYS.toMillis(epochDay)
        }
        return Triple(
            utcCal.get(Calendar.YEAR),
            utcCal.get(Calendar.MONTH),
            utcCal.get(Calendar.DAY_OF_MONTH)
        )
    }

    fun formatEpochDayShort(epochDay: Long): String {
        val (y, m, d) = ymdFromEpochDay(epochDay)
        val cal = Calendar.getInstance().apply {
            clear()
            set(y, m, d)
        }
        return displayDateFormat.format(cal.time)
    }

    fun formatEpochDayFull(epochDay: Long): String {
        val (y, m, d) = ymdFromEpochDay(epochDay)
        val cal = Calendar.getInstance().apply {
            clear()
            set(y, m, d)
        }
        return fullDateFormat.format(cal.time)
    }

    fun formatTime(epochMs: Long): String = timeFormat.format(Date(epochMs))

    fun formatMonthYear(year: Int, monthZeroBased: Int): String {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, monthZeroBased, 1)
        }
        return monthYearFormat.format(cal.time)
    }

    fun daysInMonth(year: Int, monthZeroBased: Int): Int {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, monthZeroBased, 1)
        }
        return cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }

    fun firstDayOfWeekOffset(year: Int, monthZeroBased: Int): Int {
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, monthZeroBased, 1)
        }
        // Sunday = 1 -> 0 offset
        return cal.get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
    }
}

data class DailyHydrationBar(
    val dayKey: String,
    val label: String,
    val totalMl: Int,
    val isToday: Boolean
)

data class HydrationSummary(
    val totalTodayMl: Int,
    val goalMl: Int,
    val progressFraction: Float,
    val remainingMl: Int,
    val streakDays: Int,
    val weeklyBars: List<DailyHydrationBar>
)

object HydrationCalculator {
    fun suggestedDailyGoalMl(weightKg: Float?, activityLevel: String): Int {
        val base = if (weightKg != null && weightKg in 30f..220f) {
            (weightKg * 33f).roundToInt()
        } else {
            2300
        }
        val activityBonus = when (activityLevel) {
            "Active" -> 450
            "Moderate" -> 250
            else -> 0
        }
        return ((base + activityBonus) / 50) * 50
    }

    fun summarize(
        recordsLast7Days: List<WaterRecordEntity>,
        goalMl: Int,
        nowMs: Long = System.currentTimeMillis()
    ): HydrationSummary {
        val safeGoal = max(goalMl, 500)
        val grouped = recordsLast7Days.groupBy { it.dayKey }
            .mapValues { (_, list) -> list.sumOf { it.amountMl } }

        val todayKey = DateEpochUtils.todayDayKey(nowMs)
        val totalToday = grouped[todayKey] ?: 0
        val progress = (totalToday.toFloat() / safeGoal.toFloat()).coerceIn(0f, 1.5f)
        val remaining = (safeGoal - totalToday).coerceAtLeast(0)

        val bars = (6 downTo 0).map { daysAgo ->
            val key = DateEpochUtils.dayKeyDaysAgo(daysAgo, nowMs)
            DailyHydrationBar(
                dayKey = key,
                label = if (daysAgo == 0) "Today" else DateEpochUtils.shortWeekdayForDayKey(key),
                totalMl = grouped[key] ?: 0,
                isToday = daysAgo == 0
            )
        }

        var streak = 0
        for (daysAgo in 0..6) {
            val key = DateEpochUtils.dayKeyDaysAgo(daysAgo, nowMs)
            val amount = grouped[key] ?: 0
            if (amount >= safeGoal) {
                streak++
            } else if (daysAgo > 0) {
                break
            }
        }

        return HydrationSummary(
            totalTodayMl = totalToday,
            goalMl = safeGoal,
            progressFraction = progress,
            remainingMl = remaining,
            streakDays = streak,
            weeklyBars = bars
        )
    }
}

enum class CyclePhase(val label: String, val guidance: String) {
    MENSTRUAL("Menstrual Phase", "Prioritize gentle rest, warmth, and iron-rich nourishment."),
    FOLLICULAR("Follicular Phase", "Energy gently rises—great time for focus and fresh habits."),
    OVULATION("Ovulation Window", "Peak vitality and social energy window."),
    LUTEAL("Luteal Phase", "Wind down with steady hydration, sleep, and calm pacing."),
    UNLOGGED("Ready to Track", "Log your first period start date to unlock local cycle predictions.")
}

data class CycleInsights(
    val averageCycleLengthDays: Int,
    val averagePeriodLengthDays: Int,
    val currentCycleDay: Int?,
    val currentPhase: CyclePhase,
    val nextPredictedStartEpochDay: Long?,
    val daysUntilNextPeriod: Int?,
    val fertileWindowStartEpochDay: Long?,
    val fertileWindowEndEpochDay: Long?
)

object CycleCalculator {
    fun analyze(
        recordsDesc: List<PeriodRecordEntity>,
        configuredCycleLength: Int,
        configuredPeriodLength: Int,
        todayEpochDay: Long = DateEpochUtils.todayEpochDay()
    ): CycleInsights {
        val sortedAsc = recordsDesc.sortedBy { it.startDateEpochDay }
        if (sortedAsc.isEmpty()) {
            return CycleInsights(
                averageCycleLengthDays = configuredCycleLength,
                averagePeriodLengthDays = configuredPeriodLength,
                currentCycleDay = null,
                currentPhase = CyclePhase.UNLOGGED,
                nextPredictedStartEpochDay = null,
                daysUntilNextPeriod = null,
                fertileWindowStartEpochDay = null,
                fertileWindowEndEpochDay = null
            )
        }

        val observedCycles = sortedAsc.zipWithNext { a, b ->
            (b.startDateEpochDay - a.startDateEpochDay).toInt()
        }.filter { it in 18..48 }

        val avgCycle = if (observedCycles.isNotEmpty()) {
            observedCycles.average().roundToInt().coerceIn(21, 45)
        } else {
            configuredCycleLength.coerceIn(21, 45)
        }

        val observedDurations = sortedAsc.mapNotNull { rec ->
            rec.endDateEpochDay?.let { end -> (end - rec.startDateEpochDay + 1).toInt() }
        }.filter { it in 1..14 }

        val avgPeriod = if (observedDurations.isNotEmpty()) {
            observedDurations.average().roundToInt().coerceIn(2, 10)
        } else {
            configuredPeriodLength.coerceIn(2, 10)
        }

        val latest = sortedAsc.last()
        val cycleDay = (todayEpochDay - latest.startDateEpochDay + 1).toInt().coerceAtLeast(1)
        val nextStart = latest.startDateEpochDay + avgCycle
        val daysUntil = (nextStart - todayEpochDay).toInt()

        val ovulationDay = nextStart - 14
        val fertileStart = ovulationDay - 4
        val fertileEnd = ovulationDay + 1

        val activePeriodEnd = latest.endDateEpochDay ?: (latest.startDateEpochDay + avgPeriod - 1)
        val phase = when {
            todayEpochDay in latest.startDateEpochDay..activePeriodEnd -> CyclePhase.MENSTRUAL
            todayEpochDay in fertileStart..fertileEnd -> CyclePhase.OVULATION
            cycleDay <= (avgCycle / 2) -> CyclePhase.FOLLICULAR
            else -> CyclePhase.LUTEAL
        }

        return CycleInsights(
            averageCycleLengthDays = avgCycle,
            averagePeriodLengthDays = avgPeriod,
            currentCycleDay = cycleDay,
            currentPhase = phase,
            nextPredictedStartEpochDay = nextStart,
            daysUntilNextPeriod = daysUntil,
            fertileWindowStartEpochDay = fertileStart,
            fertileWindowEndEpochDay = fertileEnd
        )
    }
}
