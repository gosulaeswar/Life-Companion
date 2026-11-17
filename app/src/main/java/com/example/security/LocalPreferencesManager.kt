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
    TAMIL("ta", "Tamil (Ready)", "தமிழ்"),
    TELUGU("te", "Telugu (Ready)", "తెలుగు"),
    BENGALI("bn", "Bengali (Ready)", "বাংলা"),
    MARATHI("mr", "Marathi (Ready)", "मराठी")
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
    val reminderMinute: Int = 0
)

data class UiPreferencesState(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val language: SupportedLanguage = SupportedLanguage.ENGLISH,
    val welcomeCompleted: Boolean = false,
    val waterTone: CategoryToneSetting = CategoryToneSetting(
        bundledToneId = "water_drop",
        reminderHour = 10,
        reminderMinute = 0
    ),
    val periodTone: CategoryToneSetting = CategoryToneSetting(
        bundledToneId = "period_chime",
        reminderHour = 8,
        reminderMinute = 30
    ),
    val wellnessTone: CategoryToneSetting = CategoryToneSetting(
        bundledToneId = "wellness_gentle",
        reminderHour = 20,
        reminderMinute = 0
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

        fun toneSource(cat: String) = stringPreferencesKey("tone_source_$cat")
        fun bundledToneId(cat: String) = stringPreferencesKey("tone_bundled_id_$cat")
        fun toneUri(cat: String) = stringPreferencesKey("tone_uri_$cat")
        fun toneName(cat: String) = stringPreferencesKey("tone_name_$cat")
        fun toneVersion(cat: String) = intPreferencesKey("tone_version_$cat")
        fun reminderEnabled(cat: String) = booleanPreferencesKey("reminder_enabled_$cat")
        fun reminderHour(cat: String) = intPreferencesKey("reminder_hour_$cat")
        fun reminderMinute(cat: String) = intPreferencesKey("reminder_minute_$cat")

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
                reminderMinute = prefs[Keys.reminderMinute(cat)] ?: defaultMin
            )
        }

        UiPreferencesState(
            themeMode = ThemeMode.entries.firstOrNull { it.name == themeName } ?: ThemeMode.SYSTEM,
            language = SupportedLanguage.entries.firstOrNull { it.code == langCode }
                ?: SupportedLanguage.ENGLISH,
            welcomeCompleted = prefs[Keys.WELCOME_COMPLETED] ?: false,
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
        minute: Int
    ) {
        context.lifeCompanionPrefsDataStore.edit { prefs ->
            prefs[Keys.reminderEnabled(categoryKey)] = enabled
            prefs[Keys.reminderHour(categoryKey)] = hour.coerceIn(0, 23)
            prefs[Keys.reminderMinute(categoryKey)] = minute.coerceIn(0, 59)
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
