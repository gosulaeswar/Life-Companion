package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.data.local.AppDatabase
import com.example.security.LocalPreferencesManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class BootCompletedReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val prefs = LocalPreferencesManager(context).preferencesFlow.first()
                val soundManager = AlertSoundManager(context)
                if (prefs.waterTone.reminderEnabled) {
                    soundManager.scheduleDailyReminder(
                        AlertCategory.WATER,
                        prefs.waterTone.reminderHour,
                        prefs.waterTone.reminderMinute
                    )
                }
                if (prefs.periodTone.reminderEnabled) {
                    soundManager.scheduleDailyReminder(
                        AlertCategory.PERIOD,
                        prefs.periodTone.reminderHour,
                        prefs.periodTone.reminderMinute
                    )
                }
                if (prefs.wellnessTone.reminderEnabled) {
                    soundManager.scheduleDailyReminder(
                        AlertCategory.WELLNESS,
                        prefs.wellnessTone.reminderHour,
                        prefs.wellnessTone.reminderMinute
                    )
                }
                if (prefs.wellbeingOveruseAlertsEnabled) {
                    soundManager.scheduleWellbeingMonitor()
                }
                val db = AppDatabase.getInstance(context)
                val profile = db.userProfileDao().getActiveProfileSnapshot()
                if (profile != null) {
                    db.reminderDao().getRemindersSnapshot(profile.id)
                        .filter { it.isEnabled }
                        .forEach { soundManager.scheduleCustomAlarm(it) }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
