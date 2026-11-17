package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.data.repository.AllModulesRepository
import com.example.domain.DateEpochUtils
import com.example.security.LocalPreferencesManager
import com.example.wellbeing.DeviceWellbeingManager
import com.example.wellbeing.OveruseSeverity
import com.example.wellbeing.WellbeingMetricsCalculator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.util.Calendar

class AlertAlarmReceiver : BroadcastReceiver() {

    companion object {
        const val EXTRA_CATEGORY_KEY = "extra_category_key"
        const val EXTRA_CUSTOM_ALARM_ID = "extra_custom_alarm_id"
        const val EXTRA_WELLBEING_CHECK = "extra_wellbeing_check"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val pendingResult = goAsync()
        val customAlarmId = intent.getLongExtra(EXTRA_CUSTOM_ALARM_ID, -1L)
        val isWellbeingCheck = intent.getBooleanExtra(EXTRA_WELLBEING_CHECK, false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val soundManager = AlertSoundManager(context)
                when {
                    isWellbeingCheck -> handleBackgroundWellbeingCheck(context, soundManager)

                    customAlarmId > 0L -> {
                        val reminder = AppDatabase.getInstance(context).reminderDao()
                            .getReminderById(customAlarmId)
                        if (reminder != null && reminder.isEnabled && isTodayEnabled(reminder.daysOfWeekMask)) {
                            soundManager.showCustomAlarmNotification(reminder)
                        }
                    }

                    else -> {
                        val key = intent.getStringExtra(EXTRA_CATEGORY_KEY) ?: AlertCategory.WELLNESS.key
                        val category = AlertCategory.entries.firstOrNull { it.key == key }
                            ?: AlertCategory.WELLNESS
                        val prefs = LocalPreferencesManager(context).preferencesFlow.first()
                        val (setting, title, body) = when (category) {
                            AlertCategory.WATER -> Triple(
                                prefs.waterTone,
                                "Hydration Check-In",
                                "Time for a calm glass of water to stay on track with your daily goal."
                            )
                            AlertCategory.PERIOD -> Triple(
                                prefs.periodTone,
                                "Cycle Care Reminder",
                                "Take a moment to check your cycle phase or log today's flow and symptoms."
                            )
                            AlertCategory.WELLNESS -> Triple(
                                prefs.wellnessTone,
                                "LifeWeave Check-In",
                                "Pause for a mindful breath and review today's wellness goals locally."
                            )
                        }
                        soundManager.showAlertNotification(category, setting, title, body)
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun handleBackgroundWellbeingCheck(
        context: Context,
        soundManager: AlertSoundManager
    ) {
        val prefsManager = LocalPreferencesManager(context)
        val prefs = prefsManager.preferencesFlow.first()
        if (!prefs.wellbeingOveruseAlertsEnabled) return

        val db = AppDatabase.getInstance(context)
        val profile = db.userProfileDao().getActiveProfileSnapshot() ?: return
        val wellbeingManager = DeviceWellbeingManager(context)
        if (!wellbeingManager.hasUsageAccessPermission()) return

        val snapshot = wellbeingManager.fetchDayUsageSnapshot(daysAgo = 0).getOrNull() ?: return
        val repo = AllModulesRepository(db)
        val existing = db.digitalWellbeingDao().getWellbeingForDaySnapshot(profile.id, snapshot.dayKey)
        repo.syncWellbeingSnapshot(
            profileId = profile.id,
            existing = existing,
            snapshot = snapshot
        )

        val updatedRecord = db.digitalWellbeingDao().getWellbeingForDaySnapshot(profile.id, snapshot.dayKey)
            ?: return
        val alert = WellbeingMetricsCalculator.evaluateOveruse(
            record = updatedRecord,
            warningPercent = prefs.wellbeingWarningPercent,
            topApps = snapshot.topApps
        )
        if (alert.severity == OveruseSeverity.BALANCED) return

        val todayKey = DateEpochUtils.todayDayKey()
        val alreadySentSameStage = prefs.lastOveruseAlertDayKey == todayKey &&
            prefs.lastOveruseAlertStage == alert.stageKey
        if (!alreadySentSameStage) {
            soundManager.showWellbeingOveruseNotification(
                title = "📵 ${alert.title}",
                body = "${alert.message} ${alert.recommendation}",
                wellnessToneSetting = prefs.wellnessTone
            )
            prefsManager.recordOveruseAlertDispatched(todayKey, alert.stageKey)
        }
    }

    private fun isTodayEnabled(daysMask: Int): Boolean {
        if (daysMask == 127) return true
        val dayBit = Calendar.getInstance().get(Calendar.DAY_OF_WEEK) - Calendar.SUNDAY
        return (daysMask and (1 shl dayBit)) != 0
    }
}
