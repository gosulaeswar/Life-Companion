package com.example

import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.domain.CycleCalculator
import com.example.domain.CyclePhase
import com.example.domain.DateEpochUtils
import com.example.domain.HydrationCalculator
import org.junit.Assert.assertEquals
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun `hydration summary computes today total and streak`() {
        val now = System.currentTimeMillis()
        val today = DateEpochUtils.todayDayKey(now)
        val yesterday = DateEpochUtils.dayKeyDaysAgo(1, now)
        val records = listOf(
            WaterRecordEntity(profileId = 1, amountMl = 1000, dayKey = today, loggedAtEpochMs = now),
            WaterRecordEntity(profileId = 1, amountMl = 1200, dayKey = today, loggedAtEpochMs = now),
            WaterRecordEntity(profileId = 1, amountMl = 2100, dayKey = yesterday, loggedAtEpochMs = now)
        )
        val summary = HydrationCalculator.summarize(records, goalMl = 2000, nowMs = now)
        assertEquals(2200, summary.totalTodayMl)
        assertEquals(0, summary.remainingMl)
        assertEquals(2, summary.streakDays)
        assertEquals(7, summary.weeklyBars.size)
    }

    @Test
    fun `cycle calculator derives average cycle length and menstrual phase`() {
        val today = 20_000L
        val records = listOf(
            PeriodRecordEntity(profileId = 1, startDateEpochDay = today - 2, endDateEpochDay = null),
            PeriodRecordEntity(profileId = 1, startDateEpochDay = today - 30, endDateEpochDay = today - 26)
        )
        val insights = CycleCalculator.analyze(
            recordsDesc = records,
            configuredCycleLength = 28,
            configuredPeriodLength = 5,
            todayEpochDay = today
        )
        assertEquals(28, insights.averageCycleLengthDays)
        assertEquals(3, insights.currentCycleDay)
        assertEquals(CyclePhase.MENSTRUAL, insights.currentPhase)
        assertEquals(today + 26, insights.nextPredictedStartEpochDay)
    }
}
