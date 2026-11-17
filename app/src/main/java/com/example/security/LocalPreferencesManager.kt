package com.example.security

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.lifeCompanionPrefsDataStore by preferencesDataStore(name = "life_companion_ui_prefs")

enum class SupportedLanguage(val code: String, val displayLabel: String, val nativeName: String) {
    ENGLISH("en", "English", "English"),
    HINDI("hi", "Hindi", "हिन्दी"),
    TAMIL("ta", "Tamil", "தமிழ்"),
    TELUGU("te", "Telugu", "తెలుగు"),
    BENGALI("bn", "Bengali", "বাংলা"),
    MARATHI("mr", "Marathi", "मराठी")
}

enum class AlertToneSource(val label: String) {
    CATEGORY_DEFAULT("Bundled Local Audio"),
    SYSTEM_ALARM("Device Alarm Sound"),
    SYSTEM_NOTIFICATION("Device Notification Sound"),
    CUSTOM_LOCAL_FILE("Device Audio File")
}

data class CategoryToneSetting(
    val source: AlertToneSource = AlertToneSource.CATEGORY_DEFAULT,
    val bundledToneId: String? = null,
    val customUri: String? = null,
    val customFileName: String? = null,
    val channelVersion: Int = 1,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val customEmoji: String? = null,
    val customMessage: String? = null,
    val gesturePreset: String = "SWIPE_SLIDE",
    val repeatIntervalMinutes: Int = 1440
)

data class UiPreferencesState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: SupportedLanguage = SupportedLanguage.ENGLISH,
    val welcomeCompleted: Boolean = false,
    val permissionsSetupCompleted: Boolean = false,
    val waterTone: CategoryToneSetting = CategoryToneSetting(
        bundledToneId = "water_drop",
        reminderHour = 10,
        reminderMinute = 0,
        customEmoji = "💧",
        customMessage = "Note: Drink 250 ml of water now to stay hydrated and energized."
    ),
    val periodTone: CategoryToneSetting = CategoryToneSetting(
        bundledToneId = "period_chime",
        reminderHour = 8,
        reminderMinute = 30,
        customEmoji = "🌸"
    ),
    val wellnessTone: CategoryToneSetting = CategoryToneSetting(
        bundledToneId = "wellness_gentle",
        reminderHour = 20,
        reminderMinute = 0,
        customEmoji = "💊",
        customMessage = "Note: Take scheduled medicines on time and log today's water."
    ),
    val wellbeingOveruseAlertsEnabled: Boolean = true,
    val wellbeingWarningPercent: Int = 80,
    val lastOveruseAlertDayKey: String = "",
    val lastOveruseAlertStage: String = ""
)

class LocalPreferencesManager(private val context: Context) {

    private object Keys {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val LANGUAGE_CODE = stringPreferencesKey("language_code")
        val WELCOME_COMPLETED = booleanPreferencesKey("welcome_completed")
        val PERMISSIONS_SETUP_COMPLETED = booleanPreferencesKey("permissions_setup_completed")
        val DEFAULT_REMINDERS_SEEDED = booleanPreferencesKey("default_reminders_seeded")

        fun toneSource(cat: String) = stringPreferencesKey("tone_source_$cat")
        fun bundledToneId(cat: String) = stringPreferencesKey("tone_bundled_id_$cat")
        fun toneUri(cat: String) = stringPreferencesKey("tone_uri_$cat")
        fun toneName(cat: String) = stringPreferencesKey("tone_name_$cat")
        fun toneVersion(cat: String) = intPreferencesKey("tone_version_$cat")
        fun reminderEnabled(cat: String) = booleanPreferencesKey("reminder_enabled_$cat")
        fun reminderHour(cat: String) = intPreferencesKey("reminder_hour_$cat")
        fun reminderMinute(cat: String) = intPreferencesKey("reminder_minute_$cat")
        fun customEmoji(cat: String) = stringPreferencesKey("reminder_emoji_$cat")
        fun customMessage(cat: String) = stringPreferencesKey("reminder_message_$cat")
        fun gesturePreset(cat: String) = stringPreferencesKey("reminder_gesture_$cat")
        fun reminderIntervalMin(cat: String) = intPreferencesKey("reminder_interval_min_$cat")

        val WELLBEING_ALERTS_ENABLED = booleanPreferencesKey("wellbeing_overuse_alerts_enabled")
        val WELLBEING_WARNING_PERCENT = intPreferencesKey("wellbeing_warning_percent")
        val LAST_OVERUSE_DAY_KEY = stringPreferencesKey("wellbeing_last_alert_day")
        val LAST_OVERUSE_STAGE = stringPreferencesKey("wellbeing_last_alert_stage")
    }

    val preferencesFlow: Flow<UiPreferencesState> = context.lifeCompanionPrefsDataStore.data.map { prefs ->
        val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.SYSTEM.name
        val langCode = prefs[Keys.LANGUAGE_CODE] ?: SupportedLanguage.ENGLISH.code

        fun readCategory(
            cat: String,
            defaultBundledId: String,
            defaultHour: Int,
            defaultMin: Int
        ): CategoryToneSetting {
            val sourceName = prefs[Keys.toneSource(cat)] ?: AlertToneSource.CATEGORY_DEFAULT.name
            return CategoryToneSetting(
                source = AlertToneSource.entries.firstOrNull { it.name == sourceName }
                    ?: AlertToneSource.CATEGORY_DEFAULT,
                bundledToneId = prefs[Keys.bundledToneId(cat)] ?: defaultBundledId,
                customUri = prefs[Keys.toneUri(cat)],
                customFileName = prefs[Keys.toneName(cat)],
                channelVersion = prefs[Keys.toneVersion(cat)] ?: 1,
                reminderEnabled = prefs[Keys.reminderEnabled(cat)] ?: false,
                reminderHour = prefs[Keys.reminderHour(cat)] ?: defaultHour,
                reminderMinute = prefs[Keys.reminderMinute(cat)] ?: defaultMin,
                customEmoji = prefs[Keys.customEmoji(cat)],
                customMessage = prefs[Keys.customMessage(cat)],
                gesturePreset = prefs[Keys.gesturePreset(cat)] ?: "SWIPE_SLIDE",
                repeatIntervalMinutes = prefs[Keys.reminderIntervalMin(cat)] ?: 1440
            )
        }

        UiPreferencesState(
            themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM,
            language = SupportedLanguage.entries.firstOrNull { it.code == langCode }
                ?: SupportedLanguage.ENGLISH,
            welcomeCompleted = prefs[Keys.WELCOME_COMPLETED] ?: false,
            permissionsSetupCompleted = prefs[Keys.PERMISSIONS_SETUP_COMPLETED] ?: false,
            waterTone = readCategory("water", "water_drop", 10, 0),
            periodTone = readCategory("period", "period_chime", 8, 30),
            wellnessTone = readCategory("wellness", "wellness_gentle", 20, 0),
            wellbeingOveruseAlertsEnabled = prefs[Keys.WELLBEING_ALERTS_ENABLED] ?: true,
            wellbeingWarningPercent = (prefs[Keys.WELLBEING_WARNING_PERCENT] ?: 80).coerceIn(50, 95),
            lastOveruseAlertDayKey = prefs[Keys.LAST_OVERUSE_DAY_KEY] ?: "",
            lastOveruseAlertStage = prefs[Keys.LAST_OVERUSE_STAGE] ?: ""
        )
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.lifeCompanionPrefsDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setLanguage(language: SupportedLanguage) {
        context.lifeCompanionPrefsDataStore.edit { it[Keys.LANGUAGE_CODE] = language.code }
    }

    suspend fun setWelcomeCompleted(completed: Boolean) {
        context.lifeCompanionPrefsDataStore.edit { it[Keys.WELCOME_COMPLETED] = completed }
    }

    suspend fun setPermissionsSetupCompleted(completed: Boolean) {
        context.lifeCompanionPrefsDataStore.edit { it[Keys.PERMISSIONS_SETUP_COMPLETED] = completed }
    }

    suspend fun enableDefaultCareRemindersIfNeeded(): Boolean {
        var enabledNow = false
        context.lifeCompanionPrefsDataStore.edit { prefs ->
            if (prefs[Keys.DEFAULT_REMINDERS_SEEDED] != true) {
                prefs[Keys.DEFAULT_REMINDERS_SEEDED] = true
                if (prefs[Keys.reminderEnabled("water")] == null) {
                    prefs[Keys.reminderEnabled("water")] = true
                    prefs[Keys.reminderHour("water")] = 10
                    prefs[Keys.reminderMinute("water")] = 0
                    prefs[Keys.reminderIntervalMin("water")] = 120
                    prefs[Keys.customEmoji("water")] = "💧"
                    prefs[Keys.customMessage("water")] =
                        "Note: Drink a 250 ml glass of water now to stay hydrated & energized."
                }
                if (prefs[Keys.reminderEnabled("wellness")] == null) {
                    prefs[Keys.reminderEnabled("wellness")] = true
                    prefs[Keys.reminderHour("wellness")] = 20
                    prefs[Keys.reminderMinute("wellness")] = 0
                    prefs[Keys.customEmoji("wellness")] = "💊"
                    prefs[Keys.customMessage("wellness")] =
                        "Note: Take scheduled medicines on time and finish today's water target."
                }
                enabledNow = true
            }
        }
        return enabledNow
    }

    suspend fun updateCategoryTone(
        categoryKey: String,
        source: AlertToneSource,
        bundledToneId: String? = null,
        customUri: String? = null,
        customFileName: String? = null
    ): Int {
        var newVersion = 1
        context.lifeCompanionPrefsDataStore.edit { prefs ->
            newVersion = (prefs[Keys.toneVersion(categoryKey)] ?: 1) + 1
            prefs[Keys.toneSource(categoryKey)] = source.name
            prefs[Keys.toneVersion(categoryKey)] = newVersion
            if (bundledToneId != null) {
                prefs[Keys.bundledToneId(categoryKey)] = bundledToneId
            }
            if (customUri != null) {
                prefs[Keys.toneUri(categoryKey)] = customUri
            } else if (source != AlertToneSource.CUSTOM_LOCAL_FILE) {
                prefs.remove(Keys.toneUri(categoryKey))
            }
            if (customFileName != null) {
                prefs[Keys.toneName(categoryKey)] = customFileName
            } else if (source != AlertToneSource.CUSTOM_LOCAL_FILE) {
                prefs.remove(Keys.toneName(categoryKey))
            }
        }
        return newVersion
    }

    suspend fun updateReminderSchedule(
        categoryKey: String,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        repeatIntervalMinutes: Int? = null
    ) {
        context.lifeCompanionPrefsDataStore.edit { prefs ->
            prefs[Keys.reminderEnabled(categoryKey)] = enabled
            prefs[Keys.reminderHour(categoryKey)] = hour.coerceIn(0, 23)
            prefs[Keys.reminderMinute(categoryKey)] = minute.coerceIn(0, 59)
            if (repeatIntervalMinutes != null) {
                prefs[Keys.reminderIntervalMin(categoryKey)] = repeatIntervalMinutes.coerceIn(5, 10_080)
            }
        }
    }

    suspend fun updateCategoryNotificationStyle(
        categoryKey: String,
        emoji: String,
        customMessage: String,
        gesturePreset: String
    ) {
        context.lifeCompanionPrefsDataStore.edit { prefs ->
            val trimmedEmoji = emoji.trim()
            if (trimmedEmoji.isNotEmpty()) {
                prefs[Keys.customEmoji(categoryKey)] = trimmedEmoji
            } else {
                prefs.remove(Keys.customEmoji(categoryKey))
            }
            val trimmedMsg = customMessage.trim()
            if (trimmedMsg.isNotEmpty()) {
                prefs[Keys.customMessage(categoryKey)] = trimmedMsg
            } else {
                prefs.remove(Keys.customMessage(categoryKey))
            }
            prefs[Keys.gesturePreset(categoryKey)] = gesturePreset
        }
    }

    suspend fun updateWellbeingAlertSettings(enabled: Boolean, warningPercent: Int) {
        context.lifeCompanionPrefsDataStore.edit { prefs ->
            prefs[Keys.WELLBEING_ALERTS_ENABLED] = enabled
            prefs[Keys.WELLBEING_WARNING_PERCENT] = warningPercent.coerceIn(50, 95)
        }
    }

    suspend fun recordOveruseAlertDispatched(dayKey: String, stageKey: String) {
        context.lifeCompanionPrefsDataStore.edit { prefs ->
            prefs[Keys.LAST_OVERUSE_DAY_KEY] = dayKey
            prefs[Keys.LAST_OVERUSE_STAGE] = stageKey
        }
    }

    suspend fun resetAll() {
        context.lifeCompanionPrefsDataStore.edit { it.clear() }
    }
}
