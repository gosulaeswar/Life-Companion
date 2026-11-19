package com.example.sync

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.repository.AllModulesRepository
import com.example.healthsync.FitnessSyncRepository
import com.example.healthsync.HealthConnectManager
import com.example.notifications.AlertSoundManager
import com.example.security.LocalPreferencesManager
import com.example.sleep.SleepMonitorStateStore
import com.example.wellbeing.DeviceWellbeingManager
import com.example.domain.DateEpochUtils
import kotlinx.coroutines.flow.first
import java.util.Calendar
import java.util.Locale

/**
 * Play-policy compliant WorkManager periodic worker that automatically refreshes:
 * - Digital Wellbeing metrics (Android UsageStatsManager)
 * - Connected Fitness Devices & Wearables (Health Connect)
 * - Optional gentle bedtime prompt notification to launch Sleep & Snoring Monitor
 */
class ModuleAutoRefreshWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    companion object {
        private const val BEDTIME_CHANNEL_ID = "lifeweave_bedtime_sleep_prompt"
        private const val BEDTIME_NOTIF_ID = 4410
    }

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        return runCatching {
            val db = AppDatabase.getInstance(ctx)
            val prefsManager = LocalPreferencesManager(ctx)
            val prefs = prefsManager.preferencesFlow.first()
            if (!prefs.autoRefreshEnabled) {
                return Result.success()
            }

            val manager = AppAutoRefreshManager(
                appContext = ctx,
                database = db,
                preferencesManager = prefsManager,
                deviceWellbeingManager = DeviceWellbeingManager(ctx),
                healthConnectManager = HealthConnectManager(ctx),
                fitnessSyncRepository = FitnessSyncRepository(db),
                allModulesRepository = AllModulesRepository(db),
                alertSoundManager = AlertSoundManager(ctx)
            )
            manager.refreshAllModules(
                reason = "background_worker",
                force = true,
                includePastWeekWellbeing = false
            )

            maybeNotifyBedtimeSleepMonitor(ctx, prefsManager)
            Result.success()
        }.getOrElse {
            if (runAttemptCount < 2) Result.retry() else Result.success()
        }
    }

    private suspend fun maybeNotifyBedtimeSleepMonitor(
        ctx: Context,
        prefsManager: LocalPreferencesManager
    ) {
        val prefs = prefsManager.preferencesFlow.first()
        if (!prefs.sleepAutoBedtimePromptEnabled) return
        if (SleepMonitorStateStore.state.value.isMonitoring) return

        val nowMs = System.currentTimeMillis()
        val todayKey = DateEpochUtils.todayDayKey(nowMs)
        if (prefs.lastBedtimePromptDayKey == todayKey) return

        val cal = Calendar.getInstance().apply { timeInMillis = nowMs }
        val nowMin = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val bedMin = prefs.sleepBedtimeHour * 60 + prefs.sleepBedtimeMinute
        // Within [bedtime - 15m .. bedtime + 45m]
        val diff = nowMin - bedMin
        if (diff !in -15..45) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            if (nm != null && nm.getNotificationChannel(BEDTIME_CHANNEL_ID) == null) {
                val ch = NotificationChannel(
                    BEDTIME_CHANNEL_ID,
                    "Bedtime Sleep Monitor Prompt",
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    description = "Gentle bedtime reminder to start overnight sleep quality and snoring monitoring."
                }
                nm.createNotificationChannel(ch)
            }
        }

        val openIntent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pi = PendingIntent.getActivity(
            ctx,
            BEDTIME_NOTIF_ID,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val bedTimeFormatted = String.format(Locale.US, "%02d:%02d", prefs.sleepBedtimeHour, prefs.sleepBedtimeMinute)
        val notif = NotificationCompat.Builder(ctx, BEDTIME_CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("😴 Ready for Sleep & Snoring Tracking?")
            .setContentText("Bedtime ($bedTimeFormatted) is here. Tap to start on-device sleep quality & snoring monitor.")
            .setAutoCancel(true)
            .setContentIntent(pi)
            .build()

        runCatching {
            NotificationManagerCompat.from(ctx).notify(BEDTIME_NOTIF_ID, notif)
            prefsManager.recordBedtimePromptShown(todayKey)
        }
    }
}
