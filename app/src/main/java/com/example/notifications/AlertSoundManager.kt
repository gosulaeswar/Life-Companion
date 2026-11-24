package com.example.notifications

import android.Manifest
import android.app.ActivityManager
import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.SystemClock
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.annotation.RawRes
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.MainActivity
import com.example.R
import com.example.data.local.AppDatabase
import com.example.data.local.entity.ReminderEntity
import com.example.security.AlertToneSource
import com.example.security.CategoryToneSetting
import com.example.security.LocalPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import java.util.Calendar

enum class BundledAlertTone(
    val id: String,
    val displayName: String,
    val description: String,
    val durationLabel: String,
    @param:RawRes val rawRes: Int
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

        fun computeNextIntervalTriggerMillis(
            startHour: Int,
            startMinute: Int,
            intervalMinutes: Int,
            nowMs: Long = System.currentTimeMillis()
        ): Long {
            val cal = Calendar.getInstance().apply {
                timeInMillis = nowMs
                set(Calendar.HOUR_OF_DAY, startHour.coerceIn(0, 23))
                set(Calendar.MINUTE, startMinute.coerceIn(0, 59))
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val safeInterval = if (intervalMinutes <= 0) 1440 else intervalMinutes
            if (safeInterval >= 1440) {
                if (cal.timeInMillis <= nowMs) {
                    cal.add(Calendar.DAY_OF_YEAR, 1)
                }
                return cal.timeInMillis
            }
            val stepMs = safeInterval.coerceIn(5, 10_080) * 60_000L
            val anchorMs = cal.timeInMillis
            return if (anchorMs <= nowMs) {
                val diff = nowMs - anchorMs
                val steps = (diff / stepMs) + 1L
                anchorMs + steps * stepMs
            } else {
                val backSteps = (anchorMs - nowMs - 1L) / stepMs
                anchorMs - backSteps * stepMs
            }
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
                description = "Local on-device alerts & screen-off popups for ${category.title}"
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
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
                "${reminder.alarmEmoji} Alarm: ${reminder.title}",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Custom local alarm & screen-off popup for ${reminder.title}"
                enableVibration(true)
                enableLights(true)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                setSound(soundUri, audioAttrs)
            }
            manager.createNotificationChannel(channel)
        }
        return channelId
    }

    fun canUseFullScreenIntent(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return true
        val manager = context.getSystemService(NotificationManager::class.java) ?: return false
        return manager.canUseFullScreenIntent()
    }

    fun createFullScreenIntentSettingsIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val fsiIntent = Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (fsiIntent.resolveActivity(context.packageManager) != null) {
                return fsiIntent
            }
        }
        return Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            }.getOrDefault(false)
        } else {
            true
        }
    }

    fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun canScheduleExactAlarms(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return true
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return false
        return alarmManager.canScheduleExactAlarms()
    }

    fun createExactAlarmSettingsIntent(): Intent {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val exactIntent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (exactIntent.resolveActivity(context.packageManager) != null) {
                return exactIntent
            }
        }
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun canDrawOverlays(): Boolean {
        return Settings.canDrawOverlays(context)
    }

    fun createOverlayPermissionIntent(): Intent {
        val overlayIntent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (overlayIntent.resolveActivity(context.packageManager) != null) {
            return overlayIntent
        }
        return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.parse("package:${context.packageName}")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isIgnoringBatteryOptimizations(): Boolean {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return false
        return pm.isIgnoringBatteryOptimizations(context.packageName)
    }

    fun createBatteryOptimizationIntent(): Intent {
        val directIntent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        ).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (directIntent.resolveActivity(context.packageManager) != null) {
            return directIntent
        }
        return Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    fun isAppInForeground(): Boolean {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager ?: return false
        val procs = am.runningAppProcesses ?: return false
        return procs.any { proc ->
            proc.processName == context.packageName &&
                proc.importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND
        }
    }

    fun showAlertNotification(
        category: AlertCategory,
        setting: CategoryToneSetting,
        title: String,
        body: String,
        launchPopupImmediately: Boolean = false
    ): Boolean {
        val channelId = ensureNotificationChannel(category, setting)
        val soundUri = resolveSoundUri(category, setting)
        val emoji = setting.customEmoji?.takeIf { it.isNotBlank() }
            ?: AlarmEmojiCatalog.defaultEmojiForCategory(category.key)
        val effectiveBody = body.ifBlank {
            setting.customMessage?.takeIf { it.isNotBlank() }
                ?: AlarmEmojiCatalog.defaultMessageForCategory(category.key, title)
        }
        val payload = AlarmPopupPayload(
            notificationId = category.notificationId,
            categoryKey = category.key,
            title = title,
            message = effectiveBody,
            emoji = emoji,
            timeLabel = String.format("%02d:%02d", setting.reminderHour, setting.reminderMinute),
            toneLabel = describeCurrentTone(category, setting),
            gesturePreset = AlarmEmojiGesturePreset.fromId(setting.gesturePreset),
            soundUriString = soundUri.toString()
        )
        return dispatchPopupNotification(
            channelId = channelId,
            payload = payload,
            soundUri = soundUri,
            forceDirectLaunch = launchPopupImmediately
        )
    }

    fun showWellbeingOveruseNotification(
        title: String,
        body: String,
        wellnessToneSetting: CategoryToneSetting,
        launchPopupImmediately: Boolean = false
    ): Boolean {
        val channelId = ensureNotificationChannel(AlertCategory.WELLNESS, wellnessToneSetting)
        val soundUri = resolveSoundUri(AlertCategory.WELLNESS, wellnessToneSetting)
        val payload = AlarmPopupPayload(
            notificationId = 1004,
            categoryKey = "wellbeing",
            title = title.removePrefix("📵").trim(),
            message = body,
            emoji = "📵",
            toneLabel = describeCurrentTone(AlertCategory.WELLNESS, wellnessToneSetting),
            gesturePreset = AlarmEmojiGesturePreset.SWIPE_SLIDE,
            soundUriString = soundUri.toString()
        )
        return dispatchPopupNotification(
            channelId = channelId,
            payload = payload,
            soundUri = soundUri,
            forceDirectLaunch = launchPopupImmediately
        )
    }

    fun showMedicineRefillOrExpiryNotification(
        title: String,
        body: String,
        wellnessToneSetting: CategoryToneSetting,
        notificationId: Int = 1005
    ): Boolean {
        val channelId = ensureNotificationChannel(AlertCategory.WELLNESS, wellnessToneSetting)
        val soundUri = resolveSoundUri(AlertCategory.WELLNESS, wellnessToneSetting)
        val payload = AlarmPopupPayload(
            notificationId = notificationId,
            categoryKey = "medicine",
            title = title,
            message = body,
            emoji = "💊",
            toneLabel = describeCurrentTone(AlertCategory.WELLNESS, wellnessToneSetting),
            gesturePreset = AlarmEmojiGesturePreset.SWIPE_SLIDE,
            soundUriString = soundUri.toString()
        )
        return dispatchPopupNotification(
            channelId = channelId,
            payload = payload,
            soundUri = soundUri,
            forceDirectLaunch = false
        )
    }

    fun showCustomAlarmNotification(
        reminder: ReminderEntity,
        enrichedMessage: String? = null,
        launchPopupImmediately: Boolean = false
    ): Boolean {
        val soundUri = resolveReminderSoundUri(reminder)
        val channelId = ensureCustomAlarmChannel(reminder, soundUri)
        val toneLabel = describeReminderTone(reminder)
        val emoji = reminder.alarmEmoji.ifBlank {
            AlarmEmojiCatalog.defaultEmojiForCategory(reminder.moduleKey)
        }
        val body = enrichedMessage?.takeIf { it.isNotBlank() }
            ?: reminder.customMessage.ifBlank {
                AlarmEmojiCatalog.defaultMessageForCategory(reminder.moduleKey, reminder.title)
            }
        val payload = AlarmPopupPayload(
            notificationId = 5000 + reminder.id.toInt(),
            alarmId = reminder.id,
            categoryKey = reminder.moduleKey,
            title = reminder.title,
            message = body,
            emoji = emoji,
            timeLabel = reminder.triggerTime24h,
            toneLabel = toneLabel,
            gesturePreset = AlarmEmojiGesturePreset.fromId(reminder.gesturePreset),
            soundUriString = soundUri.toString()
        )
        return dispatchPopupNotification(
            channelId = channelId,
            payload = payload,
            soundUri = soundUri,
            forceDirectLaunch = launchPopupImmediately
        )
    }

    fun dispatchPopupNotification(
        channelId: String,
        payload: AlarmPopupPayload,
        soundUri: Uri?,
        forceDirectLaunch: Boolean = false
    ): Boolean {
        val shouldLaunchOverlay = forceDirectLaunch || canDrawOverlays() || isAppInForeground()
        if (shouldLaunchOverlay) {
            AlarmRingerManager.startRinging(context, soundUri)
            launchPopupActivity(payload)
        }
        val posted = if (hasNotificationPermission()) {
            postPopupAlarmNotification(channelId, payload, soundUri)
        } else {
            false
        }
        return posted || shouldLaunchOverlay
    }

    fun postPopupAlarmNotification(
        channelId: String,
        payload: AlarmPopupPayload,
        soundUri: Uri?
    ): Boolean {
        val reqBase = (payload.notificationId and 0x03FF_FFFF) * 10
        val popupIntent = Intent(context, AlarmPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            payload.writeToIntent(this)
        }
        val popupPending = PendingIntent.getActivity(
            context,
            reqBase + 1,
            popupIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val dismissIntent = Intent(context, AlertAlarmReceiver::class.java).apply {
            action = AlertAlarmReceiver.ACTION_DISMISS_ALARM
            putExtra(AlarmPopupPayload.EXTRA_NOTIFICATION_ID, payload.notificationId)
            putExtra(AlarmPopupPayload.EXTRA_ALARM_ID, payload.alarmId)
            putExtra(AlertAlarmReceiver.EXTRA_MARK_DONE, false)
        }
        val dismissPending = PendingIntent.getBroadcast(
            context,
            reqBase + 2,
            dismissIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val doneIntent = Intent(context, AlertAlarmReceiver::class.java).apply {
            action = AlertAlarmReceiver.ACTION_DISMISS_ALARM
            putExtra(AlarmPopupPayload.EXTRA_NOTIFICATION_ID, payload.notificationId)
            putExtra(AlarmPopupPayload.EXTRA_ALARM_ID, payload.alarmId)
            putExtra(AlertAlarmReceiver.EXTRA_MARK_DONE, true)
        }
        val donePending = PendingIntent.getBroadcast(
            context,
            reqBase + 3,
            doneIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val snoozeIntent = Intent(context, AlertAlarmReceiver::class.java).apply {
            action = AlertAlarmReceiver.ACTION_SNOOZE_ALARM
            payload.writeToIntent(this)
        }
        val snoozePending = PendingIntent.getBroadcast(
            context,
            reqBase + 4,
            snoozeIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val accentColor = when (payload.categoryKey.lowercase()) {
            "water" -> Color.parseColor("#1D6E6B")
            "period" -> Color.parseColor("#B6486E")
            "wellbeing", "digital_wellbeing" -> Color.parseColor("#C63D2F")
            else -> Color.parseColor("#3B6978")
        }

        val styledTitle = "${payload.emoji} ${payload.title}"
        val styledBody = "${payload.message} (${payload.gesturePreset.shortBadge})"

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(styledTitle)
            .setContentText(styledBody)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .setBigContentTitle(styledTitle)
                    .bigText("${payload.message}\nGesture ready: ${payload.gesturePreset.instruction}")
            )
            .setColor(accentColor)
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setFullScreenIntent(popupPending, true)
            .setContentIntent(popupPending)
            .setDeleteIntent(dismissPending)
            .setAutoCancel(true)
            .addAction(0, "✅ Done", donePending)
            .addAction(0, "😴 Snooze 10m", snoozePending)
            .addAction(0, "🔕 Stop", dismissPending)

        if (soundUri != null) {
            builder.setSound(soundUri)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }

        return try {
            NotificationManagerCompat.from(context).notify(payload.notificationId, builder.build())
            true
        } catch (_: SecurityException) {
            false
        } catch (_: Exception) {
            false
        }
    }

    fun launchPopupActivity(payload: AlarmPopupPayload) {
        val intent = Intent(context, AlarmPopupActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_CLEAR_TOP or
                Intent.FLAG_ACTIVITY_SINGLE_TOP or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            payload.writeToIntent(this)
        }
        runCatching { context.startActivity(intent) }
    }

    fun scheduleDailyReminder(
        category: AlertCategory,
        hour: Int,
        minute: Int,
        repeatIntervalMinutes: Int = 1440
    ) {
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
        val triggerAtMs = computeNextIntervalTriggerMillis(hour, minute, repeatIntervalMinutes)
        scheduleWakeUpAlarmClock(alarmManager, triggerAtMs, category.requestCode, pendingIntent)
    }

    fun scheduleTestPopupInSeconds(seconds: Int = 5, category: AlertCategory = AlertCategory.WATER) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMs = System.currentTimeMillis() + seconds.coerceIn(3, 60) * 1000L
        val intent = Intent(context, AlertAlarmReceiver::class.java).apply {
            action = AlertAlarmReceiver.ACTION_TEST_BACKGROUND_POPUP
            putExtra(AlertAlarmReceiver.EXTRA_CATEGORY_KEY, category.key)
        }
        val requestCode = 2999
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleWakeUpAlarmClock(alarmManager, triggerAtMs, requestCode, pendingIntent)
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
        val requestCode = 5000 + reminder.id.toInt()
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerAtMs = computeNextIntervalTriggerMillis(hour, minute, reminder.repeatIntervalMinutes)
        scheduleWakeUpAlarmClock(alarmManager, triggerAtMs, requestCode, pendingIntent)
    }

    fun scheduleSnoozeAlarm(payload: AlarmPopupPayload, minutes: Int = 10) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val triggerAtMs = System.currentTimeMillis() + minutes.coerceAtLeast(1) * 60_000L
        val snoozePayload = payload.copy(
            title = "${payload.title} (Snoozed)"
        )
        val intent = Intent(context, AlertAlarmReceiver::class.java).apply {
            action = AlertAlarmReceiver.ACTION_SNOOZE_TRIGGER
            snoozePayload.writeToIntent(this)
        }
        val requestCode = (payload.notificationId and 0x03FF_FFFF) * 10 + 5
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        scheduleWakeUpAlarmClock(alarmManager, triggerAtMs, requestCode, pendingIntent)
    }

    private fun scheduleWakeUpAlarmClock(
        alarmManager: AlarmManager,
        triggerAtMs: Long,
        showRequestCode: Int,
        operation: PendingIntent
    ) {
        val showIntent = PendingIntent.getActivity(
            context,
            (showRequestCode and 0x03FF_FFFF) * 10 + 6,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val clockInfo = AlarmManager.AlarmClockInfo(triggerAtMs, showIntent)
        val scheduledExact = runCatching {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || alarmManager.canScheduleExactAlarms()) {
                alarmManager.setAlarmClock(clockInfo, operation)
                true
            } else {
                false
            }
        }.getOrDefault(false)

        if (!scheduledExact) {
            runCatching {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMs, operation)
            }
        }
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

    fun scheduleWellbeingMonitor(delayMs: Long = 15 * 60_000L) {
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
        val triggerElapsed = SystemClock.elapsedRealtime() + delayMs.coerceAtLeast(60_000L)
        runCatching {
            alarmManager.setAndAllowWhileIdle(
                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                triggerElapsed,
                pendingIntent
            )
        }
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

    suspend fun rescheduleAllActiveReminders(
        db: AppDatabase,
        preferencesManager: LocalPreferencesManager
    ) {
        val prefs = preferencesManager.preferencesFlow.first()
        if (prefs.waterTone.reminderEnabled) {
            ensureNotificationChannel(AlertCategory.WATER, prefs.waterTone)
            scheduleDailyReminder(
                AlertCategory.WATER,
                prefs.waterTone.reminderHour,
                prefs.waterTone.reminderMinute,
                prefs.waterTone.repeatIntervalMinutes
            )
        }
        if (prefs.periodTone.reminderEnabled) {
            ensureNotificationChannel(AlertCategory.PERIOD, prefs.periodTone)
            scheduleDailyReminder(
                AlertCategory.PERIOD,
                prefs.periodTone.reminderHour,
                prefs.periodTone.reminderMinute,
                prefs.periodTone.repeatIntervalMinutes
            )
        }
        if (prefs.wellnessTone.reminderEnabled) {
            ensureNotificationChannel(AlertCategory.WELLNESS, prefs.wellnessTone)
            scheduleDailyReminder(
                AlertCategory.WELLNESS,
                prefs.wellnessTone.reminderHour,
                prefs.wellnessTone.reminderMinute,
                prefs.wellnessTone.repeatIntervalMinutes
            )
        }
        if (prefs.wellbeingOveruseAlertsEnabled) {
            scheduleWellbeingMonitor(delayMs = 5 * 60_000L)
        }
        val allProfiles = db.userProfileDao().getAllProfilesSnapshot()
        val profile = allProfiles.firstOrNull { it.id == prefs.activeProfileId } ?: allProfiles.firstOrNull()
        if (profile != null) {
            db.reminderDao().getRemindersSnapshot(profile.id)
                .filter { it.isEnabled }
                .forEach { scheduleCustomAlarm(it) }
        }
    }

    private fun isUriReadable(uri: Uri): Boolean {
        return runCatching {
            context.contentResolver.openAssetFileDescriptor(uri, "r")?.use { true } ?: false
        }.getOrDefault(false)
    }
}
