package com.example.ui.viewmodel

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.billing.EffectiveEntitlement
import com.example.billing.SubscriptionRepository
import com.example.data.local.entity.ModuleActivationEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.BackupPreviewSummary
import com.example.data.repository.CustomAlarmRepository
import com.example.data.repository.ModuleRepository
import com.example.data.repository.PrivacyBackupRepository
import com.example.data.repository.UserProfileRepository
import com.example.notifications.AlertCategory
import com.example.notifications.AlertMetricsCalculator
import com.example.notifications.AlertMetricsSummary
import com.example.notifications.AlertSoundManager
import com.example.notifications.BundledAlertTone
import com.example.ai.LifeWeaveAiService
import com.example.security.AlertToneSource
import com.example.security.CategoryToneSetting
import com.example.security.KeystoreVaultManager
import com.example.security.LocalPreferencesManager
import com.example.security.OfflineLicenseVerifier
import com.example.security.PersonalAiKeyState
import com.example.security.PersonalAiKeyStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PopupPermissionsSnapshot(
    val hasNotifications: Boolean = true,
    val canUseFullScreenPopup: Boolean = true,
    val canScheduleExactAlarms: Boolean = true,
    val canDrawOverlays: Boolean = false,
    val isIgnoringBatteryOptimizations: Boolean = false
)

data class SettingsPrivacyUiState(
    val preferences: UiPreferencesState = UiPreferencesState(),
    val profile: UserProfileEntity? = null,
    val customAlarms: List<ReminderEntity> = emptyList(),
    val alertMetrics: AlertMetricsSummary = AlertMetricsSummary(),
    val modules: List<ModuleActivationEntity> = emptyList(),
    val playingToneKey: String? = null,
    val vaultStatus: VaultSecurityStatus = VaultSecurityStatus(false, KeystoreVaultManager.DATA_KEY_ALIAS),
    val publicKeyReady: Boolean = true,
    val canUseFullScreenPopup: Boolean = true,
    val popupPermissions: PopupPermissionsSnapshot = PopupPermissionsSnapshot(),
    val activationBanner: String? = null,
    val activationSuccess: Boolean? = null,
    val statusMessage: String? = null,
    val pendingRestorePreview: BackupPreviewSummary? = null,
    val isRestoringOrExporting: Boolean = false,
    val effectiveEntitlement: EffectiveEntitlement = EffectiveEntitlement(),
    val personalAiKeyState: PersonalAiKeyState = PersonalAiKeyState(),
    val isTestingPersonalAiKey: Boolean = false,
    val personalAiKeyFeedback: Pair<Boolean, String>? = null
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
    licenseVerifier: OfflineLicenseVerifier,
    private val subscriptionRepository: SubscriptionRepository? = null,
    private val personalAiKeyStore: PersonalAiKeyStore? = null,
    private val aiService: LifeWeaveAiService? = null
) : ViewModel() {

    private fun readPopupPermissions(): PopupPermissionsSnapshot = PopupPermissionsSnapshot(
        hasNotifications = alertSoundManager.hasNotificationPermission(),
        canUseFullScreenPopup = alertSoundManager.canUseFullScreenIntent(),
        canScheduleExactAlarms = alertSoundManager.canScheduleExactAlarms(),
        canDrawOverlays = alertSoundManager.canDrawOverlays(),
        isIgnoringBatteryOptimizations = alertSoundManager.isIgnoringBatteryOptimizations()
    )

    private val activationBanner = MutableStateFlow<Pair<String, Boolean>?>(null)
    private val statusMessage = MutableStateFlow<String?>(null)
    private val pendingRestoreState = MutableStateFlow<Pair<BackupPreviewSummary, String>?>(null)
    private val isVaultBusy = MutableStateFlow(false)
    private val popupPermissionsFlow = MutableStateFlow(readPopupPermissions())
    private val vaultStatus = vaultManager.inspectVaultStatus()
    private val publicKeyReady = licenseVerifier.isPublicKeyReady()

    private val profileFlow = userProfileRepository.observeActiveProfile()
    private val alarmsFlow = profileFlow.flatMapLatest { profile ->
        if (profile == null) flowOf(emptyList()) else customAlarmRepository.observeAlarms(profile.id)
    }

    private val isTestingPersonalKeyFlow = MutableStateFlow(false)
    private val personalKeyFeedbackFlow = MutableStateFlow<Pair<Boolean, String>?>(null)

    private val entitlementFlow = subscriptionRepository?.observeEffectiveEntitlement()
        ?: flowOf(EffectiveEntitlement())
    private val personalAiKeyStateFlow = personalAiKeyStore?.keyState
        ?: flowOf(PersonalAiKeyState())
    private val personalAiOverlayFlow = combine(
        personalAiKeyStateFlow,
        isTestingPersonalKeyFlow,
        personalKeyFeedbackFlow
    ) { keyState, testing, feedback ->
        Triple(keyState, testing, feedback)
    }

    val uiState: StateFlow<SettingsPrivacyUiState> = combine(
        combine(
            preferencesManager.preferencesFlow,
            profileFlow,
            alarmsFlow,
            moduleRepository.observeModules(),
            AlertSoundManager.playingToneKey
        ) { prefs, profile, alarms, modules, playingKey ->
            listOf(prefs, profile, alarms, modules, playingKey)
        },
        combine(
            activationBanner,
            statusMessage,
            popupPermissionsFlow,
            pendingRestoreState,
            isVaultBusy,
            entitlementFlow
        ) { metaArray ->
            metaArray
        },
        personalAiOverlayFlow
    ) { mainList, meta, aiKeyOverlay ->
        val prefs = mainList[0] as UiPreferencesState
        val profile = mainList[1] as UserProfileEntity?
        @Suppress("UNCHECKED_CAST")
        val alarms = mainList[2] as List<ReminderEntity>
        @Suppress("UNCHECKED_CAST")
        val modules = mainList[3] as List<ModuleActivationEntity>
        val playingKey = mainList[4] as String?
        @Suppress("UNCHECKED_CAST")
        val act = meta[0] as Pair<String, Boolean>?
        val msg = meta[1] as String?
        val perms = meta[2] as PopupPermissionsSnapshot
        @Suppress("UNCHECKED_CAST")
        val pendingRestore = meta[3] as Pair<BackupPreviewSummary, String>?
        val busy = meta[4] as Boolean
        val entitlement = meta[5] as EffectiveEntitlement

        SettingsPrivacyUiState(
            preferences = prefs,
            profile = profile,
            customAlarms = alarms,
            alertMetrics = AlertMetricsCalculator.summarize(alarms),
            modules = modules,
            playingToneKey = playingKey,
            vaultStatus = vaultStatus,
            publicKeyReady = publicKeyReady,
            canUseFullScreenPopup = perms.canUseFullScreenPopup,
            popupPermissions = perms,
            activationBanner = act?.first,
            activationSuccess = act?.second,
            statusMessage = msg,
            pendingRestorePreview = pendingRestore?.first,
            isRestoringOrExporting = busy,
            effectiveEntitlement = entitlement,
            personalAiKeyState = aiKeyOverlay.first,
            isTestingPersonalAiKey = aiKeyOverlay.second,
            personalAiKeyFeedback = aiKeyOverlay.third
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SettingsPrivacyUiState(
            vaultStatus = vaultStatus,
            publicKeyReady = publicKeyReady,
            canUseFullScreenPopup = popupPermissionsFlow.value.canUseFullScreenPopup,
            popupPermissions = popupPermissionsFlow.value,
            personalAiKeyState = personalAiKeyStore?.currentState() ?: PersonalAiKeyState()
        )
    )

    fun refreshFullScreenPopupStatus() {
        popupPermissionsFlow.value = readPopupPermissions()
    }

    fun openFullScreenPopupSettings(context: Context) {
        runCatching {
            context.startActivity(alertSoundManager.createFullScreenIntentSettingsIntent())
        }
    }

    fun openExactAlarmSettings(context: Context) {
        runCatching {
            context.startActivity(alertSoundManager.createExactAlarmSettingsIntent())
        }
    }

    fun openOverlayPopupSettings(context: Context) {
        runCatching {
            context.startActivity(alertSoundManager.createOverlayPermissionIntent())
        }
    }

    fun openBatteryOptimizationSettings(context: Context) {
        runCatching {
            context.startActivity(alertSoundManager.createBatteryOptimizationIntent())
        }
    }

    fun scheduleTestBackgroundPopup() {
        alertSoundManager.scheduleTestPopupInSeconds(seconds = 5, category = AlertCategory.WATER)
        statusMessage.value =
            "⏰ 5-second background alarm armed! Lock your screen or switch apps now — your Water & Medicine popup will appear in 5s."
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferencesManager.setThemeMode(mode) }
    }

    fun setLanguage(language: SupportedLanguage) {
        viewModelScope.launch {
            preferencesManager.setLanguage(language)
            statusMessage.value = "Language switched to ${language.nativeName} (${language.displayLabel})."
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

    fun toggleReminder(
        category: AlertCategory,
        enabled: Boolean,
        hour: Int,
        minute: Int,
        repeatIntervalMinutes: Int = settingFor(category).repeatIntervalMinutes
    ) {
        val safeInterval = AlertMetricsCalculator.normalizeIntervalMinutes(repeatIntervalMinutes)
        viewModelScope.launch {
            preferencesManager.updateReminderSchedule(category.key, enabled, hour, minute, safeInterval)
            val setting = settingFor(category).copy(
                reminderEnabled = enabled,
                reminderHour = hour,
                reminderMinute = minute,
                repeatIntervalMinutes = safeInterval
            )
            alertSoundManager.ensureNotificationChannel(category, setting)
            if (enabled) {
                alertSoundManager.scheduleDailyReminder(category, hour, minute, safeInterval)
                statusMessage.value = String.format(
                    "%s reminder scheduled (%s, from %02d:%02d).",
                    category.title,
                    AlertMetricsCalculator.formatIntervalShort(safeInterval),
                    hour,
                    minute
                )
            } else {
                alertSoundManager.cancelReminder(category)
                statusMessage.value = "${category.title} reminder paused."
            }
        }
    }

    fun updateCategoryInterval(category: AlertCategory, repeatIntervalMinutes: Int) {
        val current = settingFor(category)
        toggleReminder(
            category = category,
            enabled = current.reminderEnabled,
            hour = current.reminderHour,
            minute = current.reminderMinute,
            repeatIntervalMinutes = repeatIntervalMinutes
        )
    }

    fun updateCategoryNotificationStyle(
        category: AlertCategory,
        emoji: String,
        customMessage: String,
        gesturePreset: String
    ) {
        viewModelScope.launch {
            preferencesManager.updateCategoryNotificationStyle(
                categoryKey = category.key,
                emoji = emoji,
                customMessage = customMessage,
                gesturePreset = gesturePreset
            )
            statusMessage.value = "${category.title} popup emoji & gesture updated ($emoji)."
        }
    }

    fun sendTestNotification(category: AlertCategory) {
        val setting = settingFor(category)
        val (title, body) = when (category) {
            AlertCategory.WATER ->
                "Hydration Alert" to "Sound: ${alertSoundManager.describeCurrentTone(category, setting)}"
            AlertCategory.PERIOD ->
                "Cycle Care Alert" to "Sound: ${alertSoundManager.describeCurrentTone(category, setting)}"
            AlertCategory.WELLNESS ->
                "Wellness Alert" to "Sound: ${alertSoundManager.describeCurrentTone(category, setting)}"
        }
        val sent = alertSoundManager.showAlertNotification(
            category = category,
            setting = setting,
            title = title,
            body = body,
            launchPopupImmediately = true
        )
        statusMessage.value = if (sent) {
            "Mini-alarm popup & notification launched for ${category.title}."
        } else {
            "Notification permission not granted yet. Allow notifications for screen-off alerts."
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
        alarmEmoji: String = "⏰",
        customMessage: String = "",
        gesturePreset: String = "SWIPE_SLIDE",
        repeatIntervalMinutes: Int = 1440,
        isEnabled: Boolean = true
    ) {
        val profileId = uiState.value.profile?.id ?: return
        val cleanTitle = title.trim().ifEmpty { "Custom Wellness Alarm" }
        val cleanEmoji = alarmEmoji.trim().ifEmpty { "⏰" }
        val cleanMsg = customMessage.trim()
        val safeInterval = AlertMetricsCalculator.normalizeIntervalMinutes(repeatIntervalMinutes)
        val time24h = String.format("%02d:%02d", hour.coerceIn(0, 23), minute.coerceIn(0, 59))
        val prior = if (existingId > 0L) {
            uiState.value.customAlarms.firstOrNull { it.id == existingId }
        } else {
            null
        }
        viewModelScope.launch {
            val baseEntity = ReminderEntity(
                id = existingId,
                profileId = profileId,
                title = cleanTitle,
                moduleKey = moduleKey,
                triggerTime24h = time24h,
                daysOfWeekMask = if (daysOfWeekMask == 0) 127 else daysOfWeekMask,
                isEnabled = isEnabled,
                soundKey = soundKey,
                customSoundUri = customSoundUri,
                customSoundLabel = customSoundLabel,
                alarmEmoji = cleanEmoji,
                customMessage = cleanMsg,
                gesturePreset = gesturePreset,
                repeatIntervalMinutes = safeInterval,
                triggeredCount = prior?.triggeredCount ?: 0,
                completedCount = prior?.completedCount ?: 0,
                snoozedCount = prior?.snoozedCount ?: 0,
                lastTriggeredEpochMs = prior?.lastTriggeredEpochMs ?: 0L
            )
            val savedId = customAlarmRepository.saveAlarm(baseEntity)
            val savedEntity = baseEntity.copy(id = savedId)
            if (isEnabled) {
                alertSoundManager.scheduleCustomAlarm(savedEntity)
                val intervalBadge = AlertMetricsCalculator.formatIntervalShort(safeInterval)
                statusMessage.value = "$cleanEmoji Alarm '$cleanTitle' set for $time24h ($intervalBadge)."
            } else {
                alertSoundManager.cancelCustomAlarm(savedId)
                statusMessage.value = "$cleanEmoji Alarm '$cleanTitle' saved (paused)."
            }
        }
    }

    fun toggleCustomAlarm(alarm: ReminderEntity, enabled: Boolean) {
        viewModelScope.launch {
            val updated = customAlarmRepository.toggleAlarm(alarm, enabled)
            if (enabled) {
                alertSoundManager.scheduleCustomAlarm(updated)
                statusMessage.value = "${alarm.alarmEmoji} Alarm '${alarm.title}' enabled for ${alarm.triggerTime24h}."
            } else {
                alertSoundManager.cancelCustomAlarm(alarm.id)
                statusMessage.value = "${alarm.alarmEmoji} Alarm '${alarm.title}' paused."
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
        val sent = alertSoundManager.showCustomAlarmNotification(
            reminder = alarm,
            launchPopupImmediately = true
        )
        statusMessage.value = if (sent) {
            "Launched mini-alarm popup for ${alarm.alarmEmoji} '${alarm.title}'."
        } else {
            "Allow notifications so alarms also pop up when screen is off."
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
            isVaultBusy.value = true
            try {
                val result = privacyBackupRepository.writeExportToUri(context, uri)
                statusMessage.value = result.fold(
                    onSuccess = { bytes -> "✅ Exported full 17-table vault ($bytes bytes JSON) cleanly." },
                    onFailure = { err -> "Export failed: ${err.message ?: "unknown error"}" }
                )
            } finally {
                isVaultBusy.value = false
            }
        }
    }

    fun exportEncryptedVault(context: Context, uri: Uri) {
        viewModelScope.launch {
            isVaultBusy.value = true
            try {
                val result = privacyBackupRepository.writeEncryptedExportToUri(context, uri)
                statusMessage.value = result.fold(
                    onSuccess = { bytes -> "🔐 Exported AES-256-GCM encrypted vault ($bytes bytes, LCENC1)." },
                    onFailure = { err -> "Encrypted export failed: ${err.message ?: "unknown error"}" }
                )
            } finally {
                isVaultBusy.value = false
            }
        }
    }

    fun prepareRestoreFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            isVaultBusy.value = true
            try {
                val rawResult = privacyBackupRepository.readPayloadFromUri(context, uri)
                rawResult.onSuccess { raw ->
                    privacyBackupRepository.parseBackupPreview(raw)
                        .onSuccess { preview ->
                            pendingRestoreState.value = preview to raw
                        }
                        .onFailure { err ->
                            statusMessage.value = err.message ?: "Selected file is not a valid LifeWeave backup."
                        }
                }.onFailure { err ->
                    statusMessage.value = err.message ?: "Unable to open selected backup file."
                }
            } finally {
                isVaultBusy.value = false
            }
        }
    }

    fun confirmRestoreBackup() {
        val pending = pendingRestoreState.value ?: return
        pendingRestoreState.value = null
        viewModelScope.launch {
            isVaultBusy.value = true
            try {
                val existingAlarms = uiState.value.customAlarms
                val result = privacyBackupRepository.restoreFromRawPayload(pending.second)
                result.onSuccess { summary ->
                    existingAlarms.forEach { alertSoundManager.cancelCustomAlarm(it.id) }
                    val restoredPrefs = preferencesManager.preferencesFlow.first()
                    listOf(
                        AlertCategory.WATER to restoredPrefs.waterTone,
                        AlertCategory.PERIOD to restoredPrefs.periodTone,
                        AlertCategory.WELLNESS to restoredPrefs.wellnessTone
                    ).forEach { (cat, tone) ->
                        alertSoundManager.ensureNotificationChannel(cat, tone)
                        if (tone.reminderEnabled) {
                            alertSoundManager.scheduleDailyReminder(
                                cat,
                                tone.reminderHour,
                                tone.reminderMinute,
                                tone.repeatIntervalMinutes
                            )
                        } else {
                            alertSoundManager.cancelReminder(cat)
                        }
                    }
                    if (restoredPrefs.wellbeingOveruseAlertsEnabled) {
                        alertSoundManager.scheduleWellbeingMonitor()
                    } else {
                        alertSoundManager.cancelWellbeingMonitor()
                    }
                    summary.restoredReminders.filter { it.isEnabled }.forEach { alarm ->
                        alertSoundManager.scheduleCustomAlarm(alarm)
                    }
                    statusMessage.value = "✅ Restored ${summary.restoredRecordsCount} records for ${summary.profileName} across all local modules."
                }.onFailure { err ->
                    statusMessage.value = "Restore failed safely (existing data preserved): ${err.message ?: "invalid data"}"
                }
            } finally {
                isVaultBusy.value = false
            }
        }
    }

    fun dismissRestorePreview() {
        pendingRestoreState.value = null
    }

    fun saveAndTestPersonalAiKey(rawKey: String) {
        val store = personalAiKeyStore ?: return
        val saveResult = store.savePersonalKey(rawKey)
        saveResult.onFailure { err ->
            personalKeyFeedbackFlow.value = false to (err.message ?: "Unable to save API key.")
            return
        }
        testSavedPersonalAiKey()
    }

    fun testSavedPersonalAiKey() {
        val service = aiService ?: return
        if (isTestingPersonalKeyFlow.value) return
        viewModelScope.launch {
            isTestingPersonalKeyFlow.value = true
            personalKeyFeedbackFlow.value = null
            try {
                service.testPersonalApiKey().fold(
                    onSuccess = { ping ->
                        personalKeyFeedbackFlow.value = true to
                            "Connected to xAI Grok via ${ping.modelUsed} (${ping.latencyMs}ms). All AI features are now active!"
                    },
                    onFailure = { err ->
                        personalKeyFeedbackFlow.value = false to
                            (err.localizedMessage ?: "Saved key could not connect to xAI Grok API.")
                    }
                )
            } finally {
                isTestingPersonalKeyFlow.value = false
            }
        }
    }

    fun removePersonalAiKey() {
        personalAiKeyStore?.clearPersonalKey()
        personalKeyFeedbackFlow.value = true to
            "Personal xAI Grok key removed. Active engine: ${PersonalAiKeyState.defaultEngineLabel()}."
    }

    fun clearPersonalAiKeyFeedback() {
        personalKeyFeedbackFlow.value = null
    }

    fun deleteAllLocalData(onComplete: () -> Unit) {
        viewModelScope.launch {
            AlertCategory.entries.forEach { alertSoundManager.cancelReminder(it) }
            uiState.value.customAlarms.forEach { alertSoundManager.cancelCustomAlarm(it.id) }
            personalAiKeyStore?.clearPersonalKey()
            privacyBackupRepository.deleteAllLocalData()
            statusMessage.value = "All local records, encrypted keys, and preferences erased from this device."
            onComplete()
        }
    }

    fun restoreGooglePlayPurchases() {
        val repo = subscriptionRepository ?: return
        viewModelScope.launch {
            isVaultBusy.value = true
            try {
                val res = repo.restorePurchases()
                statusMessage.value = res.message
            } finally {
                isVaultBusy.value = false
            }
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
