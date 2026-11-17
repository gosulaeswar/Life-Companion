package com.example.notifications

import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

enum class AlarmEmojiGesturePreset(
    val id: String,
    val label: String,
    val shortBadge: String,
    val instruction: String,
    val emojiHint: String
) {
    SWIPE_SLIDE(
        id = "SWIPE_SLIDE",
        label = "Swipe Emoji Pill",
        shortBadge = "↔️ Swipe",
        instruction = "Drag emoji ➡️ Done or ⬅️ Snooze 10m",
        emojiHint = "↔️"
    ),
    HOLD_RING(
        id = "HOLD_RING",
        label = "Press & Hold Glow",
        shortBadge = "🫧 Hold",
        instruction = "Press & hold emoji orb for 1s to silence",
        emojiHint = "🫧"
    ),
    DOUBLE_TAP_POP(
        id = "DOUBLE_TAP_POP",
        label = "Double-Tap Burst",
        shortBadge = "⚡ 2× Tap",
        instruction = "Double-tap the bouncing emoji to complete",
        emojiHint = "⚡"
    );

    companion object {
        fun fromId(id: String?): AlarmEmojiGesturePreset {
            return entries.firstOrNull { it.id == id } ?: SWIPE_SLIDE
        }
    }
}

object AlarmEmojiCatalog {
    val curatedEmojis: List<String> = listOf(
        "💧", "💊", "🩺", "🧘", "🌸", "🥗",
        "📵", "🌙", "⏰", "🏃", "✨", "☀️",
        "💚", "🔔", "🍵", "🧠"
    )

    fun defaultEmojiForCategory(categoryKey: String): String {
        return when (categoryKey.lowercase()) {
            "water" -> "💧"
            "period" -> "🌸"
            "wellness" -> "🌿"
            "medicine", "medical_care" -> "💊"
            "wellbeing", "digital_wellbeing" -> "📵"
            else -> "⏰"
        }
    }

    fun defaultMessageForCategory(categoryKey: String, title: String): String {
        return when (categoryKey.lowercase()) {
            "water" -> "Time for a refreshing glass of water to hit today's hydration target."
            "period" -> "Gentle check-in for your cycle phase, flow, or comfort notes."
            "wellness" -> "Take 60 seconds to breathe, unclench your shoulders, and check in."
            "wellbeing", "digital_wellbeing" -> "Rest your eyes from the screen and take a mindful movement break."
            else -> "Scheduled alert for $title • Tap or use your emoji gesture to respond."
        }
    }
}

data class AlarmPopupPayload(
    val notificationId: Int,
    val alarmId: Long = -1L,
    val categoryKey: String = AlertCategory.WELLNESS.key,
    val title: String,
    val message: String,
    val emoji: String = "⏰",
    val timeLabel: String = "",
    val toneLabel: String = "Chime",
    val gesturePreset: AlarmEmojiGesturePreset = AlarmEmojiGesturePreset.SWIPE_SLIDE,
    val soundUriString: String? = null
) {
    fun writeToIntent(intent: Intent): Intent = intent.apply {
        putExtra(EXTRA_NOTIFICATION_ID, notificationId)
        putExtra(EXTRA_ALARM_ID, alarmId)
        putExtra(EXTRA_CATEGORY_KEY, categoryKey)
        putExtra(EXTRA_TITLE, title)
        putExtra(EXTRA_MESSAGE, message)
        putExtra(EXTRA_EMOJI, emoji)
        putExtra(EXTRA_TIME_LABEL, timeLabel)
        putExtra(EXTRA_TONE_LABEL, toneLabel)
        putExtra(EXTRA_GESTURE_PRESET, gesturePreset.id)
        if (!soundUriString.isNullOrBlank()) {
            putExtra(EXTRA_SOUND_URI, soundUriString)
        }
    }

    companion object {
        const val EXTRA_NOTIFICATION_ID = "alarm_popup_notification_id"
        const val EXTRA_ALARM_ID = "alarm_popup_alarm_id"
        const val EXTRA_CATEGORY_KEY = "alarm_popup_category_key"
        const val EXTRA_TITLE = "alarm_popup_title"
        const val EXTRA_MESSAGE = "alarm_popup_message"
        const val EXTRA_EMOJI = "alarm_popup_emoji"
        const val EXTRA_TIME_LABEL = "alarm_popup_time_label"
        const val EXTRA_TONE_LABEL = "alarm_popup_tone_label"
        const val EXTRA_GESTURE_PRESET = "alarm_popup_gesture_preset"
        const val EXTRA_SOUND_URI = "alarm_popup_sound_uri"

        fun fromIntent(intent: Intent?): AlarmPopupPayload {
            val safe = intent ?: Intent()
            val cat = safe.getStringExtra(EXTRA_CATEGORY_KEY) ?: AlertCategory.WELLNESS.key
            val title = safe.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "LifeWeave Alarm" }
            val emoji = safe.getStringExtra(EXTRA_EMOJI).orEmpty()
                .ifBlank { AlarmEmojiCatalog.defaultEmojiForCategory(cat) }
            val message = safe.getStringExtra(EXTRA_MESSAGE).orEmpty()
                .ifBlank { AlarmEmojiCatalog.defaultMessageForCategory(cat, title) }
            return AlarmPopupPayload(
                notificationId = safe.getIntExtra(EXTRA_NOTIFICATION_ID, 1003),
                alarmId = safe.getLongExtra(EXTRA_ALARM_ID, -1L),
                categoryKey = cat,
                title = title,
                message = message,
                emoji = emoji,
                timeLabel = safe.getStringExtra(EXTRA_TIME_LABEL).orEmpty(),
                toneLabel = safe.getStringExtra(EXTRA_TONE_LABEL).orEmpty().ifBlank { "Bundled Chime" },
                gesturePreset = AlarmEmojiGesturePreset.fromId(safe.getStringExtra(EXTRA_GESTURE_PRESET)),
                soundUriString = safe.getStringExtra(EXTRA_SOUND_URI)
            )
        }
    }
}

object AlarmRingerManager {
    private const val AUTO_STOP_DELAY_MS = 45_000L

    @Volatile
    private var activePlayer: MediaPlayer? = null

    @Volatile
    private var activeVibrator: Vibrator? = null

    private val mainHandler by lazy { Handler(Looper.getMainLooper()) }
    private val autoStopRunnable = Runnable { stopRinging() }

    fun startRinging(context: Context, soundUri: Uri?, vibrate: Boolean = true) {
        stopRinging()
        if (soundUri != null) {
            runCatching {
                val player = MediaPlayer().apply {
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ALARM)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    setDataSource(context.applicationContext, soundUri)
                    isLooping = true
                    prepare()
                    start()
                }
                activePlayer = player
            }
        }
        if (vibrate) {
            runCatching {
                val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                    vm?.defaultVibrator
                } else {
                    @Suppress("DEPRECATION")
                    context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                }
                activeVibrator = vibrator
                if (vibrator?.hasVibrator() == true) {
                    val pattern = longArrayOf(0L, 420L, 280L, 420L, 700L)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        vibrator.vibrate(VibrationEffect.createWaveform(pattern, 0))
                    } else {
                        @Suppress("DEPRECATION")
                        vibrator.vibrate(pattern, 0)
                    }
                }
            }
        }
        mainHandler.removeCallbacks(autoStopRunnable)
        mainHandler.postDelayed(autoStopRunnable, AUTO_STOP_DELAY_MS)
    }

    fun stopRinging() {
        mainHandler.removeCallbacks(autoStopRunnable)
        runCatching {
            activePlayer?.stop()
            activePlayer?.release()
        }
        activePlayer = null
        runCatching {
            activeVibrator?.cancel()
        }
        activeVibrator = null
    }
}
