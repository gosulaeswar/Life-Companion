package com.example.notifications

import com.example.data.local.entity.ReminderEntity
import kotlin.math.roundToInt

data class AlertCadenceBreakdown(
    val intervalMinutes: Int,
    val label: String,
    val alarmCount: Int
)

data class AlertMetricsSummary(
    val totalAlarmsCount: Int = 0,
    val enabledAlarmsCount: Int = 0,
    val intervalAlarmsCount: Int = 0,
    val dailyAlarmsCount: Int = 0,
    val totalTriggered: Int = 0,
    val totalCompleted: Int = 0,
    val totalSnoozed: Int = 0,
    val adherencePercent: Int = 100,
    val cadenceBreakdown: List<AlertCadenceBreakdown> = emptyList()
) {
    val hasActiveAlarms: Boolean
        get() = enabledAlarmsCount > 0

    val topRecurringLabel: String
        get() = cadenceBreakdown.firstOrNull { it.intervalMinutes < 1440 }?.label
            ?: cadenceBreakdown.firstOrNull()?.label
            ?: "Daily"
}

object AlertMetricsCalculator {

    val intervalPresetsMinutes: List<Int> = listOf(
        30,   // Every 30m
        60,   // Every 1h
        120,  // Every 2h
        180,  // Every 3h
        240,  // Every 4h
        360,  // Every 6h
        480,  // Every 8h
        720,  // Every 12h
        1440  // Daily
    )

    fun normalizeIntervalMinutes(minutes: Int): Int {
        return minutes.coerceIn(5, 10_080)
    }

    fun formatIntervalLabel(minutes: Int): String {
        val safe = if (minutes <= 0) 1440 else minutes
        return when {
            safe == 1440 -> "Daily (24h)"
            safe < 60 -> "Every ${safe}m"
            safe % 60 == 0 -> "Every ${safe / 60}h"
            else -> "Every ${safe / 60}h ${safe % 60}m"
        }
    }

    fun formatIntervalShort(minutes: Int): String {
        val safe = if (minutes <= 0) 1440 else minutes
        return when {
            safe == 1440 -> "Daily"
            safe < 60 -> "Every ${safe}m"
            safe % 60 == 0 -> "Every ${safe / 60}h"
            else -> "Every ${safe / 60}h ${safe % 60}m"
        }
    }

    fun summarize(alarms: List<ReminderEntity>): AlertMetricsSummary {
        if (alarms.isEmpty()) return AlertMetricsSummary()

        val enabledAlarms = alarms.filter { it.isEnabled }
        val intervalAlarms = enabledAlarms.count { it.repeatIntervalMinutes in 1 until 1440 }
        val dailyAlarms = enabledAlarms.count { it.repeatIntervalMinutes >= 1440 || it.repeatIntervalMinutes <= 0 }
        val totalTriggered = alarms.sumOf { it.triggeredCount.coerceAtLeast(0) }
        val totalCompleted = alarms.sumOf { it.completedCount.coerceAtLeast(0) }
        val totalSnoozed = alarms.sumOf { it.snoozedCount.coerceAtLeast(0) }

        val adherence = if (totalTriggered == 0) {
            100
        } else {
            ((totalCompleted.toFloat() / totalTriggered.toFloat()) * 100f)
                .roundToInt()
                .coerceIn(0, 100)
        }

        val breakdown = enabledAlarms
            .groupBy { if (it.repeatIntervalMinutes <= 0) 1440 else it.repeatIntervalMinutes }
            .entries
            .sortedBy { it.key }
            .map { (intervalMin, list) ->
                AlertCadenceBreakdown(
                    intervalMinutes = intervalMin,
                    label = formatIntervalShort(intervalMin),
                    alarmCount = list.size
                )
            }

        return AlertMetricsSummary(
            totalAlarmsCount = alarms.size,
            enabledAlarmsCount = enabledAlarms.size,
            intervalAlarmsCount = intervalAlarms,
            dailyAlarmsCount = dailyAlarms,
            totalTriggered = totalTriggered,
            totalCompleted = totalCompleted,
            totalSnoozed = totalSnoozed,
            adherencePercent = adherence,
            cadenceBreakdown = breakdown
        )
    }
}
