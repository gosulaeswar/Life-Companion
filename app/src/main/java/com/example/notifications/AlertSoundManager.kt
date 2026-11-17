package com.example.notifications

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.annotation.RawRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.entity.ReminderEntity
import com.example.security.AlertToneSource
import com.example.security.CategoryToneSetting
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Calendar

enum class BundledAlertTone(
    val id: String,
    val displayName: String,
    val description: String,
    val durationLabel: String,
    @RawRes val rawRes: Int
) {
    WATER_DROP(
        id = "water_drop",
        displayName = "Water Droplet Chime",
        description = "Crisp ascending liquid droplet notes",
        durationLabel = "1.1s",
        rawRes = R.raw.sound_water_drop
    ),
    BAMBOO_BROOK(
        id = "bamboo_brook",
        displayName = "Bamboo Brook",
        description = "Gentle cascading stream pentatonic chime",
        durationLabel = "1.4s",
        rawRes = R.raw.sound_bamboo_brook
    ),
    PERIOD_CHIME(
        id = "period_chime",
        displayName = "Warm Botanical Bell",
        description = "Soft warm tri-tone bell for cycle check-ins",
        durationLabel = "1.6s",
        rawRes = R.raw.sound_period_chime
    ),
    SINGING_BOWL(
        id = "singing_bowl",
        displayName = "Tibetan Singing Bowl",
        description = "Deep resonant harmonic bowl shimmer",
        durationLabel = "1.8s",
        rawRes = R.raw.sound_singing_bowl
    ),
    MORNING_DEW(
        id = "morning_dew",
        displayName = "Morning Dew Harp",
        description = "Bright major-9th botanical harp pluck",
        durationLabel = "1.5s",
        rawRes = R.raw.sound_morning_dew
    ),
    WELLNESS_GENTLE(
        id = "wellness_gentle",
        displayName = "Gentle Calm Chord",
        description = "Soothing warm fifth chord for daily care",
        durationLabel = "1.3s",
        rawRes = R.raw.sound_wellness_gentle
    );

    companion object {
        fun fromId(id: String?): BundledAlertTone? = entries.firstOrNull { it.id == id }
    }
}

enum class AlertCategory(
    val key: String,
    val channelPrefix: String,
    val title: String,
    val defaultTone: BundledAlertTone,
    val notificationId: Int,
    val requestCode: Int
) {
    WATER(
        key = "water",
        channelPrefix = "life_companion_water",
        title = "Hydration",
        defaultTone = BundledAlertTone.WATER_DROP,
        notificationId = 1001,
        requestCode = 2001
    ),
    PERIOD(
        key = "period",
        channelPrefix = "life_companion_period",
        title = "Cycle Care",
        defaultTone = BundledAlertTone.PERIOD_CHIME,
        notificationId = 1002,
        requestCode = 2002
    ),
    WELLNESS(
        key = "wellness",
        channelPrefix = "life_companion_wellness",
        title = "Daily Wellness",
        defaultTone = BundledAlertTone.WELLNESS_GENTLE,
        notificationId = 1003,
        requestCode = 2003
    )
}

class AlertSoundManager(private val context: Context) {

    companion object {
        private var activePlayer: MediaPlayer? = null
        private val _playingToneKey = MutableStateFlow<String?>(null)
        val playingToneKey: StateFlow<String?> = _playingToneKey.asStateFlow()

        fun stopPreview() {
            runCatching {
                activePlayer?.stop()
                activePlayer?.release()
            }
            activePlayer = null
            _playingToneKey.value = null
        }
    }

    fun rawUri(@RawRes rawRes: Int): Uri {
        return Uri.parse("${ContentResolver.SCHEME_ANDROID_RESOURCE}://${context.packageName}/$rawRes")
    }

    fun bundledToneUri(tone: BundledAlertTone): Uri = rawUri(tone.rawRes)

    fun defaultRawUri(category: AlertCategory): Uri = rawUri(category.defaultTone.rawRes)

    fun resolveSoundUri(category: AlertCategory, setting: CategoryToneSetting): Uri {
        return when (setting.source) {
            AlertToneSource.CATEGORY_DEFAULT -> {
                val chosen = BundledAlertTone.fromId(setting.bundledToneId) ?: category.defaultTone
                bundledToneUri(chosen)
            }
            AlertToneSource.SYSTEM_ALARM ->
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                    ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: defaultRawUri(category)
            AlertToneSource.SYSTEM_NOTIFICATION ->
                RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                    ?: defaultRawUri(category)
            AlertToneSource.CUSTOM_LOCAL_FILE -> {
                val parsed = setting.customUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                if (parsed != null && isUriReadable(parsed)) parsed else defaultRawUri(category)
            }
        }
    }

    fun resolveReminderSoundUri(reminder: ReminderEntity): Uri {
        val custom = reminder.customSoundUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
        if (reminder.soundKey == "custom_uri" && custom != null && isUriReadable(custom)) {
            return custom
        }
        val bundled = BundledAlertTone.fromId(reminder.soundKey)
            ?: AlertCategory.entries.firstOrNull { it.key == reminder.moduleKey }?.defaultTone
            ?: BundledAlertTone.WELLNESS_GENTLE
        return bundledToneUri(bundled)
    }

    fun describeReminderTone(reminder: ReminderEntity): String {
        if (reminder.soundKey == "custom_uri" && !reminder.customSoundLabel.isNullOrBlank()) {
            return reminder.customSoundLabel
        }
        return BundledAlertTone.fromId(reminder.soundKey)?.displayName
            ?: BundledAlertTone.WELLNESS_GENTLE.displayName
    }

    fun describeCurrentTone(category: AlertCategory, setting: CategoryToneSetting): String {
        return when (setting.source) {
            AlertToneSource.CATEGORY_DEFAULT ->
                (BundledAlertTone.fromId(setting.bundledToneId) ?: category.defaultTone).displayName
            AlertToneSource.SYSTEM_ALARM -> "Device Default Alarm Tone"
            AlertToneSource.SYSTEM_NOTIFICATION -> "Device Default Notification Tone"
            AlertToneSource.CUSTOM_LOCAL_FILE -> setting.customFileName ?: "Custom Local Audio"
        }
    }

    fun queryDisplayName(uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (idx >= 0) {
                            val name = cursor.getString(idx)
                            if (!name.isNullOrBlank()) return name
                        }
                    }
                }
        }
        return uri.lastPathSegment?.substringAfterLast('/') ?: "Custom Audio"
    }

    fun previewBundledTone(tone: BundledAlertTone): Boolean {
        return previewUri(uri = bundledToneUri(tone), playbackKey = "bundled_${tone.id}")
    }

    fun previewTone(category: AlertCategory, setting: CategoryToneSetting): Boolean {
        val uri = resolveSoundUri(category, setting)
        return previewUri(uri = uri, playbackKey = "category_${category.key}")
    }

    fun previewUri(uri: Uri, playbackKey: String): Boolean {
        if (_playingToneKey.value == playbackKey) {
            stopPreview()
            return true
        }
        stopPreview()
        return runCatching {
            val player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                setOnCompletionListener { mp ->
                    runCatching { mp.release() }
                    if (activePlayer === mp) {
                        activePlayer = null
                        _playingToneKey.value = null
                    }
                }
                prepare()
                start()
            }
            activePlayer = player
            _playingToneKey.value = playbackKey
            true
        }.getOrElse {
            _playingToneKey.value = null
            false
        }
    }

    fun ensureNotificationChannel(
        category: AlertCategory,
        setting: CategoryToneSetting
    ): String {
        val channelId = "${category.channelPrefix}_v${setting.channelVersion}"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notificationChannels
                .filter { it.id.startsWith("${category.channelPrefix}_v") && it.id != channelId }
                .forEach { old -> runCatching { manager.deleteNotificationChannel(old.id) } }

            val soundUri = resolveSoundUri(category, setting)
            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                channelId,
                "${category.title} Alerts",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Local on-device alerts for ${category.title}"
                enableVibration(true)
                setSound(soundUri, audioAttrs)
            }
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    fun ensureCustomAlarmChannel(reminder: ReminderEntity, soundUri: Uri): String {
        val soundToken = reminder.soundKey.replace(Regex("[^a-zA-Z0-9_]"), "_")
        val channelId = "life_companion_alarm_${reminder.id}_$soundToken"
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)
            manager.notificationChannels
                .filter { it.id.startsWith("life_companion_alarm_${reminder.id}_") && it.id != channelId }
                .forEach { old -> runCatching { manager.deleteNotificationChannel(old.id) } }

            val audioAttrs = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ALARM)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            val channel = NotificationChannel(
                channelId,
                "Alarm: ${reminder.title}",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Custom local alarm for ${reminder.title}"
                enableVibration(true)
                setSound(soundUri, audioAttrs)
            }
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showAlertNotification(
        category: AlertCategory,
        setting: CategoryToneSetting,
        title: String,
        body: String
    ): Boolean {
        val channelId = ensureNotificationChannel(category, setting)
        if (!hasNotificationPermission()) return false

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            category.requestCode + 50,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = resolveSoundUri(category, setting)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(category.notificationId, notification)
            true
        }.getOrDefault(false)
    }

    fun showWellbeingOveruseNotification(
        title: String,
        body: String,
        wellnessToneSetting: CategoryToneSetting
    ): Boolean {
        val channelId = ensureNotificationChannel(AlertCategory.WELLNESS, wellnessToneSetting)
        if (!hasNotificationPermission()) return false

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            2099,
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val soundUri = resolveSoundUri(AlertCategory.WELLNESS, wellnessToneSetting)
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(1004, notification)
            true
        }.getOrDefault(false)
    }

    fun showCustomAlarmNotification(reminder: ReminderEntity): Boolean {
        val soundUri = resolveReminderSoundUri(reminder)
        val channelId = ensureCustomAlarmChannel(reminder, soundUri)
        if (!hasNotificationPermission()) return false

        val tapIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val tapPending = PendingIntent.getActivity(
            context,
            5000 + reminder.id.toInt(),
            tapIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val body = "Scheduled ${reminder.triggerTime24h} alarm • Sound: ${describeReminderTone(reminder)}"
        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(reminder.title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setSound(soundUri)
            .setAutoCancel(true)
            .setContentIntent(tapPending)
            .build()

        return runCatching {
            NotificationManagerCompat.from(context).notify(5000 + reminder.id.toInt(), notification)
            true
        }.getOrDefault(false)
    }

    fun scheduleDailyReminder(category: AlertCategory, hour: Int, minute: Int) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlertAlarmReceiver::class.java).apply {
            putExtra(AlertAlarmReceiver.EXTRA_CATEGORY_KEY, category.key)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            category.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerCal = nextTriggerCalendar(hour, minute)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerCal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancelReminder(category: AlertCategory) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlertAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            category.requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleCustomAlarm(reminder: ReminderEntity) {
        val parts = reminder.triggerTime24h.split(":")
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
        val soundUri = resolveReminderSoundUri(reminder)
        ensureCustomAlarmChannel(reminder, soundUri)

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlertAlarmReceiver::class.java).apply {
            putExtra(AlertAlarmReceiver.EXTRA_CUSTOM_ALARM_ID, reminder.id)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            5000 + reminder.id.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerCal = nextTriggerCalendar(hour, minute)
        alarmManager.setInexactRepeating(
            AlarmManager.RTC_WAKEUP,
            triggerCal.timeInMillis,
            AlarmManager.INTERVAL_DAY,
            pendingIntent
        )
    }

    fun cancelCustomAlarm(reminderId: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlertAlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            5000 + reminderId.toInt(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    fun scheduleWellbeingMonitor() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlertAlarmReceiver::class.java).apply {
            putExtra(AlertAlarmReceiver.EXTRA_WELLBEING_CHECK, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2901,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 2 * 60_000L,
            AlarmManager.INTERVAL_FIFTEEN_MINUTES,
            pendingIntent
        )
    }

    fun cancelWellbeingMonitor() {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(context, AlertAlarmReceiver::class.java).apply {
            putExtra(AlertAlarmReceiver.EXTRA_WELLBEING_CHECK, true)
        }
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            2901,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        alarmManager.cancel(pendingIntent)
    }

    private fun nextTriggerCalendar(hour: Int, minute: Int): Calendar {
        return Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour.coerceIn(0, 23))
            set(Calendar.MINUTE, minute.coerceIn(0, 59))
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) {
                add(Calendar.DAY_OF_YEAR, 1)
            }
        }
    }

    private fun isUriReadable(uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
}
