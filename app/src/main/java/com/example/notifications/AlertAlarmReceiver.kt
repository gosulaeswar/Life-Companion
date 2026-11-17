package com.example.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
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

        const val ACTION_DISMISS_ALARM = "com.example.notifications.ACTION_DISMISS_ALARM"
        const val ACTION_SNOOZE_ALARM = "com.example.notifications.ACTION_SNOOZE_ALARM"
        const val ACTION_SNOOZE_TRIGGER = "com.example.notifications.ACTION_SNOOZE_TRIGGER"
        const val ACTION_TEST_BACKGROUND_POPUP = "com.example.notifications.ACTION_TEST_BACKGROUND_POPUP"
        const val EXTRA_MARK_DONE = "extra_mark_done"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        if (action == ACTION_DISMISS_ALARM) {
            AlarmRingerManager.stopRinging()
            val notifId = intent.getIntExtra(AlarmPopupPayload.EXTRA_NOTIFICATION_ID, -1)
            if (notifId > 0) {
                runCatching { NotificationManagerCompat.from(context).cancel(notifId) }
            }
            val alarmId = intent.getLongExtra(AlarmPopupPayload.EXTRA_ALARM_ID, -1L)
            val markDone = intent.getBooleanExtra(EXTRA_MARK_DONE, false)
            if (alarmId > 0L && markDone) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AppDatabase.getInstance(context).reminderDao().recordAlarmCompleted(alarmId)
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }
            return
        }

        if (action == ACTION_SNOOZE_ALARM) {
            AlarmRingerManager.stopRinging()
            val payload = AlarmPopupPayload.fromIntent(intent)
            runCatching { NotificationManagerCompat.from(context).cancel(payload.notificationId) }
            AlertSoundManager(context).scheduleSnoozeAlarm(payload, minutes = 10)
            if (payload.alarmId > 0L) {
                val pendingResult = goAsync()
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        AppDatabase.getInstance(context).reminderDao().recordAlarmSnoozed(payload.alarmId)
                    } finally {
                        pendingResult?.finish()
                    }
                }
            }
            return
        }

        val pendingResult = goAsync()
        val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val cpuWakeLock = runCatching {
            powerManager?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "LifeWeave:AlarmReceiver")
                ?.apply { acquire(20_000L) }
        }.getOrNull()

        val customAlarmId = intent.getLongExtra(EXTRA_CUSTOM_ALARM_ID, -1L)
        val isWellbeingCheck = intent.getBooleanExtra(EXTRA_WELLBEING_CHECK, false)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val soundManager = AlertSoundManager(context)
                when {
                    action == ACTION_TEST_BACKGROUND_POPUP -> {
                        val key = intent.getStringExtra(EXTRA_CATEGORY_KEY) ?: AlertCategory.WATER.key
                        val category = AlertCategory.entries.firstOrNull { it.key == key }
                            ?: AlertCategory.WATER
                        val prefs = LocalPreferencesManager(context).preferencesFlow.first()
                        val setting = when (category) {
                            AlertCategory.WATER -> prefs.waterTone
                            AlertCategory.PERIOD -> prefs.periodTone
                            AlertCategory.WELLNESS -> prefs.wellnessTone
                        }
                        val noteBody = buildRichCareNotificationBody(context, category, setting.customMessage)
                        soundManager.showAlertNotification(
                            category = category,
                            setting = setting,
                            title = if (category == AlertCategory.WATER) {
                                "Hydration & Care Check-In"
                            } else {
                                "LifeWeave Care Check-In"
                            },
                            body = noteBody
                        )
                    }

                    action == ACTION_SNOOZE_TRIGGER -> {
                        val payload = AlarmPopupPayload.fromIntent(intent)
                        val prefs = LocalPreferencesManager(context).preferencesFlow.first()
                        val soundUri = payload.soundUriString?.let { runCatching { Uri.parse(it) }.getOrNull() }
                            ?: soundManager.resolveSoundUri(AlertCategory.WELLNESS, prefs.wellnessTone)
                        val channelId = soundManager.ensureNotificationChannel(AlertCategory.WELLNESS, prefs.wellnessTone)
                        soundManager.dispatchPopupNotification(channelId, payload, soundUri)
                    }

                    isWellbeingCheck -> handleBackgroundWellbeingCheck(context, soundManager)

                    customAlarmId > 0L -> {
                        val db = AppDatabase.getInstance(context)
                        val reminder = db.reminderDao().getReminderById(customAlarmId)
                        if (reminder != null && reminder.isEnabled) {
                            soundManager.scheduleCustomAlarm(reminder)
                            if (isTodayEnabled(reminder.daysOfWeekMask)) {
                                db.reminderDao().recordAlarmTriggered(reminder.id, System.currentTimeMillis())
                                val intervalSuffix = if (reminder.repeatIntervalMinutes in 1 until 1440) {
                                    " (🔁 ${AlertMetricsCalculator.formatIntervalShort(reminder.repeatIntervalMinutes)})"
                                } else {
                                    ""
                                }
                                val enrichedBody = buildCustomAlarmNoteBody(
                                    context,
                                    db,
                                    reminder.moduleKey,
                                    reminder.title,
                                    reminder.customMessage
                                ) + intervalSuffix
                                soundManager.showCustomAlarmNotification(
                                    reminder = reminder,
                                    enrichedMessage = enrichedBody
                                )
                            }
                        }
                    }

                    else -> {
                        val key = intent.getStringExtra(EXTRA_CATEGORY_KEY) ?: AlertCategory.WELLNESS.key
                        val category = AlertCategory.entries.firstOrNull { it.key == key }
                            ?: AlertCategory.WELLNESS
                        val prefs = LocalPreferencesManager(context).preferencesFlow.first()
                        val (setting, title) = when (category) {
                            AlertCategory.WATER -> prefs.waterTone to "Drink Water Reminder"
                            AlertCategory.PERIOD -> prefs.periodTone to "Cycle Care Reminder"
                            AlertCategory.WELLNESS -> prefs.wellnessTone to "Medicine & Wellness Note"
                        }
                        if (setting.reminderEnabled) {
                            soundManager.scheduleDailyReminder(
                                category,
                                setting.reminderHour,
                                setting.reminderMinute,
                                setting.repeatIntervalMinutes
                            )
                        }
                        val noteBody = buildRichCareNotificationBody(context, category, setting.customMessage)
                        soundManager.showAlertNotification(category, setting, title, noteBody)
                    }
                }
            } finally {
                runCatching {
                    if (cpuWakeLock?.isHeld == true) cpuWakeLock.release()
                }
                pendingResult?.finish()
            }
        }
    }

    private suspend fun buildRichCareNotificationBody(
        context: Context,
        category: AlertCategory,
        userCustomMessage: String?
    ): String {
        val db = AppDatabase.getInstance(context)
        val profile = db.userProfileDao().getActiveProfileSnapshot()
        val todayKey = DateEpochUtils.todayDayKey()

        val waterTodayMl = if (profile != null) {
            db.waterDao().getAllForProfileSnapshot(profile.id)
                .filter { it.dayKey == todayKey }
                .sumOf { it.amountMl }
        } else {
            0
        }
        val waterGoalMl = profile?.dailyWaterGoalMl ?: 2500
        val remainingWaterMl = (waterGoalMl - waterTodayMl).coerceAtLeast(0)

        val pendingMedsText = if (profile != null) {
            val activeMeds = db.medicalCareDao().getMedicinesSnapshot(profile.id).filter { it.isActive }
            val takenTodayIds = db.medicalCareDao().getIntakesSnapshot(profile.id)
                .filter { it.dayKey == todayKey && it.status == "Taken" }
                .groupingBy { it.medicineId }
                .eachCount()
            val pending = activeMeds.filter { (takenTodayIds[it.id] ?: 0) < it.dosesPerDay.coerceAtLeast(1) }
            pending.take(2).joinToString(", ") { "${it.name} (${it.dosage})" }
        } else {
            ""
        }

        val customPrefix = userCustomMessage?.trim()?.takeIf { it.isNotEmpty() }

        return when (category) {
            AlertCategory.WATER -> {
                val base = customPrefix ?: "Note: Drink ~250 ml of water now to keep focus and energy steady."
                val progress = if (remainingWaterMl > 0) {
                    "Today: $waterTodayMl / $waterGoalMl ml ($remainingWaterMl ml left)."
                } else {
                    "Today: $waterTodayMl / $waterGoalMl ml goal reached!"
                }
                val medTip = if (pendingMedsText.isNotEmpty()) {
                    " 💊 Due today: $pendingMedsText."
                } else {
                    ""
                }
                "$base\n💧 $progress$medTip"
            }

            AlertCategory.WELLNESS -> {
                val medNote = if (pendingMedsText.isNotEmpty()) {
                    "💊 Take scheduled medicines: $pendingMedsText."
                } else {
                    "💊 Check your scheduled medicines & vitamins."
                }
                val waterNote = if (remainingWaterMl > 0) {
                    "💧 Drink water: $remainingWaterMl ml remaining today ($waterTodayMl / $waterGoalMl ml)."
                } else {
                    "💧 Hydration goal reached ($waterTodayMl ml)."
                }
                val intro = customPrefix ?: "Daily Care Note:"
                "$intro\n$medNote\n$waterNote"
            }

            AlertCategory.PERIOD -> {
                val intro = customPrefix
                    ?: "Note: Log today's cycle flow, mood & symptoms. Drink warm water to stay comfortable."
                "$intro\n💧 Hydration today: $waterTodayMl / $waterGoalMl ml."
            }
        }
    }

    private suspend fun buildCustomAlarmNoteBody(
        context: Context,
        db: AppDatabase,
        moduleKey: String,
        title: String,
        customMessage: String
    ): String {
        val profile = db.userProfileDao().getActiveProfileSnapshot()
        val todayKey = DateEpochUtils.todayDayKey()
        val waterTodayMl = if (profile != null) {
            db.waterDao().getAllForProfileSnapshot(profile.id)
                .filter { it.dayKey == todayKey }
                .sumOf { it.amountMl }
        } else {
            0
        }
        val waterGoalMl = profile?.dailyWaterGoalMl ?: 2500
        val cleanCustom = customMessage.trim()
        return when (moduleKey.lowercase()) {
            "medicine", "medical_care" -> {
                val base = cleanCustom.ifBlank { "Note: Take $title on schedule with a full glass of water." }
                "$base\n💧 Water logged today: $waterTodayMl / $waterGoalMl ml."
            }

            "water" -> {
                val base = cleanCustom.ifBlank { "Note: Drink 250 ml of water to stay hydrated." }
                "$base\n💧 Today: $waterTodayMl / $waterGoalMl ml."
            }

            else -> cleanCustom.ifBlank {
                AlarmEmojiCatalog.defaultMessageForCategory(moduleKey, title)
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

        // Re-schedule next 15-minute Doze-aware check automatically
        soundManager.scheduleWellbeingMonitor(delayMs = 15 * 60_000L)

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
                body = "${alert.message} ${alert.recommendation}\n💧 Take a quick stretch & drink water!",
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
