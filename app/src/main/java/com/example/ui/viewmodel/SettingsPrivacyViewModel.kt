package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ModuleActivationEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.CustomAlarmRepository
import com.example.data.repository.ModuleRepository
import com.example.data.repository.PrivacyBackupRepository
import com.example.data.repository.UserProfileRepository
import com.example.notifications.AlertCategory
import com.example.notifications.AlertSoundManager
import com.example.notifications.BundledAlertTone
import com.example.security.AlertToneSource
import com.example.security.CategoryToneSetting
import com.example.security.KeystoreVaultManager
import com.example.security.LocalPreferencesManager
import com.example.security.OfflineLicenseVerifier
import com.example.security.SupportedLanguage
import com.example.security.UiPreferencesState
import com.example.security.VaultSecurityStatus
import com.example.security.VerificationResult
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsPrivacyUiState(
    val preferences: UiPreferencesState = UiPreferencesState(),
    val profile: UserProfileEntity? = null,
    val customAlarms: List<ReminderEntity> = emptyList(),
    val modules: List<ModuleActivationEntity> = emptyList(),
    val playingToneKey: String? = null,
    val vaultStatus: VaultSecurityStatus = VaultSecurityStatus(false, KeystoreVaultManager.DATA_KEY_ALIAS),
    val publicKeyReady: Boolean = true,
    val activationBanner: String? = null,
    val activationSuccess: Boolean? = null,
    val statusMessage: String? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsPrivacyViewModel(
    private val preferencesManager: LocalPreferencesManager,
    private val userProfileRepository: UserProfileRepository,
    private val customAlarmRepository: CustomAlarmRepository,
    private val moduleRepository: ModuleRepository,
    private val privacyBackupRepository: PrivacyBackupRepository,
    private val alertSoundManager: AlertSoundManager,
    vaultManager: KeystoreVaultManager,
    licenseVerifier: OfflineLicenseVerifier
) : ViewModel() {

    private val activationBanner = MutableStateFlow<Pair<String, Boolean>?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val vaultStatus = vaultManager.inspectVaultStatus()
    private val publicKeyReady = licenseVerifier.isPublicKeyReady()

    private val profileFlow = userProfileRepository.observeActiveProfile()
    private val alarmsFlow = profileFlow.flatMapLatest { profile ->
        if (profile == null) flowOf(emptyList()) else customAlarmRepository.observeAlarms(profile.id)
    }

    val uiState: StateFlow<SettingsPrivacyUiState> = combine(
        preferencesManager.preferencesFlow,
        profileFlow,
        alarmsFlow,
        moduleRepository.observeModules(),
        AlertSoundManager.playingToneKey,
        activationBanner,
        statusMessage
    ) { values ->
        val prefs = values[0] as UiPreferencesState
        val profile = values[1] as UserProfileEntity?
        @Suppress("UNCHECKED_CAST")
        val alarms = values[2] as List<ReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val modules = values[3] as List<ModuleActivationEntity>
        val playingKey = values[4] as String?
        @Suppress("UNCHECKED_CAST")
        val actBanner = values[5] as Pair<String, Boolean>?
        val msg = values[6] as String?

        SettingsPrivacyUiState(
            preferences = prefs,
            profile = profile,
            customAlarms = alarms,
            modules = modules,
            playingToneKey = playingKey,
            vaultStatus = vaultStatus,
            publicKeyReady = publicKeyReady,
            activationBanner = actBanner?.first,
            activationSuccess = actBanner?.second,
            statusMessage = msg
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsPrivacyUiState(
            vaultStatus = vaultStatus,
            publicKeyReady = publicKeyReady
        )
    )

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setLanguage(language: SupportedLanguage) {
        viewModelScope.launch {
            preferencesManager.setLanguage(language)
            statusMessage.value = "Language preference set to ${language.displayLabel}."
        }
    }

    fun setCycleVisibility(enabled: Boolean) {
        viewModelScope.launch { userProfileRepository.setCycleTrackingEnabled(enabled) }
    }

    fun previewBundledTone(tone: BundledAlertTone) {
        val ok = alertSoundManager.previewBundledTone(tone)
        if (!ok) {
            statusMessage.value = "Unable to play ${tone.displayName}."
        }
    }

    fun previewAlertTone(category: AlertCategory) {
        val setting = settingFor(category)
        val ok = alertSoundManager.previewTone(category, setting)
        statusMessage.value = if (ok) {
            "Playing ${category.title}: ${alertSoundManager.describeCurrentTone(category, setting)}"
        } else {
            "Unable to preview audio file; falling back to default chime."
        }
    }

    fun stopAudioPreview() {
        AlertSoundManager.stopPreview()
    }

    fun selectBundledToneForCategory(category: AlertCategory, tone: BundledAlertTone) {
        viewModelScope.launch {
            val newVersion = preferencesManager.updateCategoryTone(
                categoryKey = category.key,
                source = AlertToneSource.CATEGORY_DEFAULT,
                bundledToneId = tone.id
            )
            val updatedSetting = settingFor(category).copy(
                source = AlertToneSource.CATEGORY_DEFAULT,
                bundledToneId = tone.id,
                channelVersion = newVersion,
                customUri = null,
                customFileName = null
            )
            alertSoundManager.ensureNotificationChannel(category, updatedSetting)
            alertSoundManager.previewBundledTone(tone)
            statusMessage.value = "${category.title} sound updated to ${tone.displayName}."
        }
    }

    fun selectBuiltInOrSystemTone(category: AlertCategory, source: AlertToneSource) {
        viewModelScope.launch {
            val newVersion = preferencesManager.updateCategoryTone(
                categoryKey = category.key,
                source = source,
                bundledToneId = if (source == AlertToneSource.CATEGORY_DEFAULT) category.defaultTone.id else null
            )
            val updatedSetting = settingFor(category).copy(
                source = source,
                channelVersion = newVersion,
                customUri = null,
                customFileName = null
            )
            alertSoundManager.ensureNotificationChannel(category, updatedSetting)
            alertSoundManager.previewTone(category, updatedSetting)
            statusMessage.value =
                "${category.title} tone set to ${alertSoundManager.describeCurrentTone(category, updatedSetting)}."
        }
    }

    fun selectCustomLocalAudio(context: Context, category: AlertCategory, uri: Uri) {
        takePersistablePermission(context, uri)
        val displayName = alertSoundManager.queryDisplayName(uri)
        viewModelScope.launch {
            val newVersion = preferencesManager.updateCategoryTone(
                categoryKey = category.key,
                source = AlertToneSource.CUSTOM_LOCAL_FILE,
                customUri = uri.toString(),
                customFileName = displayName
            )
            val updatedSetting = settingFor(category).copy(
                source = AlertToneSource.CUSTOM_LOCAL_FILE,
                customUri = uri.toString(),
                customFileName = displayName,
                channelVersion = newVersion
            )
            alertSoundManager.ensureNotificationChannel(category, updatedSetting)
            alertSoundManager.previewTone(category, updatedSetting)
            statusMessage.value = "${category.title} updated to local audio: $displayName"
        }
    }

    fun resolvePickedAudioDisplayName(context: Context, uri: Uri): String {
        takePersistablePermission(context, uri)
        return alertSoundManager.queryDisplayName(uri)
    }

    fun previewCustomUri(uri: Uri, key: String = "custom_picker") {
        alertSoundManager.previewUri(uri, key)
    }

    fun toggleReminder(category: AlertCategory, enabled: Boolean, hour: Int, minute: Int) {
        viewModelScope.launch {
            preferencesManager.updateReminderSchedule(category.key, enabled, hour, minute)
            val setting = settingFor(category).copy(
                reminderEnabled = enabled,
                reminderHour = hour,
                reminderMinute = minute
            )
            alertSoundManager.ensureNotificationChannel(category, setting)
            if (enabled) {
                alertSoundManager.scheduleDailyReminder(category, hour, minute)
                statusMessage.value = String.format(
                    "%s daily reminder scheduled for %02d:%02d.",
                    category.title,
                    hour,
                    minute
                )
            } else {
                alertSoundManager.cancelReminder(category)
                statusMessage.value = "${category.title} daily reminder paused."
            }
        }
    }

    fun sendTestNotification(category: AlertCategory) {
        val setting = settingFor(category)
        val (title, body) = when (category) {
            AlertCategory.WATER ->
                "Hydration Alert Test" to "Sound: ${alertSoundManager.describeCurrentTone(category, setting)}"
            AlertCategory.PERIOD ->
                "Cycle Care Alert Test" to "Sound: ${alertSoundManager.describeCurrentTone(category, setting)}"
            AlertCategory.WELLNESS ->
                "Wellness Alert Test" to "Sound: ${alertSoundManager.describeCurrentTone(category, setting)}"
        }
        val sent = alertSoundManager.showAlertNotification(category, setting, title, body)
        statusMessage.value = if (sent) {
            "Test notification posted with ${category.title} channel sound."
        } else {
            "Notification permission not granted yet. Allow notifications to hear alert notifications."
        }
    }

    // Custom Alarms (Room-backed)
    fun saveCustomAlarm(
        existingId: Long = 0L,
        title: String,
        moduleKey: String,
        hour: Int,
        minute: Int,
        daysOfWeekMask: Int,
        soundKey: String,
        customSoundUri: String?,
        customSoundLabel: String?,
        isEnabled: Boolean = true
    ) {
        val profileId = uiState.value.profile?.id ?: return
        val cleanTitle = title.trim().ifEmpty { "Custom Wellness Alarm" }
        val time24h = String.format("%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        viewModelScope.launch {
            val savedId = customAlarmRepository.saveAlarm(
                ReminderEntity(
                    id = existingId,
                    profileId = profileId,
                    title = cleanTitle,
                    moduleKey = moduleKey,
                    triggerTime24h = time24h,
                    daysOfWeekMask = if (daysOfWeekMask == 0) 127 else daysOfWeekMask,
                    isEnabled = isEnabled,
                    soundKey = soundKey,
                    customSoundUri = customSoundUri,
                    customSoundLabel = customSoundLabel
                )
            )
            val savedEntity = ReminderEntity(
                id = savedId,
                profileId = profileId,
                title = cleanTitle,
                moduleKey = moduleKey,
                triggerTime24h = time24h,
                daysOfWeekMask = if (daysOfWeekMask == 0) 127 else daysOfWeekMask,
                isEnabled = isEnabled,
                soundKey = soundKey,
                customSoundUri = customSoundUri,
                customSoundLabel = customSoundLabel
            )
            if (isEnabled) {
                alertSoundManager.scheduleCustomAlarm(savedEntity)
                statusMessage.value = "Alarm '$cleanTitle' set for $time24h."
            } else {
                alertSoundManager.cancelCustomAlarm(savedId)
                statusMessage.value = "Alarm '$cleanTitle' saved (paused)."
            }
        }
    }

    fun toggleCustomAlarm(alarm: ReminderEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = customAlarmRepository.toggleAlarm(alarm, enabled)
            if (enabled) {
                alertSoundManager.scheduleCustomAlarm(updated)
                statusMessage.value = "Alarm '${alarm.title}' enabled for ${alarm.triggerTime24h}."
            } else {
                alertSoundManager.cancelCustomAlarm(alarm.id)
                statusMessage.value = "Alarm '${alarm.title}' paused."
            }
        }
    }

    fun deleteCustomAlarm(alarm: ReminderEntity) {
        viewModelScope.launch {
            alertSoundManager.cancelCustomAlarm(alarm.id)
            customAlarmRepository.deleteAlarm(alarm.id)
            statusMessage.value = "Deleted alarm '${alarm.title}'."
        }
    }

    fun previewCustomAlarm(alarm: ReminderEntity) {
        val uri = alertSoundManager.resolveReminderSoundUri(alarm)
        alertSoundManager.previewUri(uri, "alarm_${alarm.id}")
    }

    fun testCustomAlarmNotification(alarm: ReminderEntity) {
        val sent = alertSoundManager.showCustomAlarmNotification(alarm)
        statusMessage.value = if (sent) {
            "Posted test alarm '${alarm.title}' (${alertSoundManager.describeReminderTone(alarm)})."
        } else {
            "Allow notifications to fire custom alarm alerts."
        }
    }

    fun verifyOfflineActivationKey(rawToken: String) {
        viewModelScope.launch {
            when (val result = moduleRepository.verifyAndActivateOffline(rawToken)) {
                is VerificationResult.Valid -> {
                    activationBanner.value = (
                        "Unlocked '${result.payload.moduleKey}' offline (ref ${result.payload.licenseReference}, sig ${result.signatureDigest})." to true
                    )
                }
                is VerificationResult.Invalid -> {
                    activationBanner.value = (result.reason to false)
                }
            }
        }
    }

    fun exportLocalJson(context: Context, uri: Uri) {
        viewModelScope.launch {
            val result = privacyBackupRepository.writeExportToUri(context, uri)
            statusMessage.value = result.fold(
                onSuccess = { bytes -> "Exported $bytes bytes of local JSON directly to your chosen folder." },
                onFailure = { err -> "Export failed: ${err.message ?: "unknown error"}" }
            )
        }
    }

    fun deleteAllLocalData(onComplete: () -> Unit) {
        viewModelScope.launch {
            AlertCategory.entries.forEach { alertSoundManager.cancelReminder(it) }
            uiState.value.customAlarms.forEach { alertSoundManager.cancelCustomAlarm(it.id) }
            privacyBackupRepository.deleteAllLocalData()
            statusMessage.value = "All local records and preferences erased from this device."
            onComplete()
        }
    }

    fun clearStatusMessage() {
        statusMessage.value = null
    }

    private fun takePersistablePermission(context: Context, uri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }

    private fun settingFor(category: AlertCategory): CategoryToneSetting {
        val prefs = uiState.value.preferences
        return when (category) {
            AlertCategory.WATER -> prefs.waterTone
            AlertCategory.PERIOD -> prefs.periodTone
            AlertCategory.WELLNESS -> prefs.wellnessTone
        }
    }
}
