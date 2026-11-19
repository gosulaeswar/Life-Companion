package com.example.ui.screens

import android.Manifest
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.notifications.AlertCategory
import com.example.security.SupportedLanguage
import com.example.ui.components.CategorySoundPickerCard
import com.example.ui.components.CustomAlarmsSettingsCard
import com.example.ui.components.ModuleStatusPill
import com.example.ui.components.PrivacyVaultBadge
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.SettingsPrivacyUiState
import com.example.ui.viewmodel.SettingsPrivacyViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsPrivacyScreen(
    uiState: SettingsPrivacyUiState,
    viewModel: SettingsPrivacyViewModel,
    onEditProfile: () -> Unit,
    onOpenFamilyCircle: () -> Unit = {},
    onOpenSubscription: () -> Unit = {},
    onAllDataWiped: () -> Unit
) {
    val context = LocalContext.current
    var activationTokenInput by remember { mutableStateOf("") }
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var pendingAudioCategory by remember { mutableStateOf<AlertCategory?>(null) }
    var pendingAlarmAudioCallback by remember { mutableStateOf<((Uri) -> Unit)?>(null) }
    var pendingNotifyAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshFullScreenPopupStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.stopAudioPreview()
        }
    }

    val exportJsonLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            viewModel.exportLocalJson(context, uri)
        }
    }

    val exportEncryptedLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.exportEncryptedVault(context, uri)
        }
    }

    val restoreBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.prepareRestoreFromUri(context, uri)
        }
    }

    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val category = pendingAudioCategory
        val alarmCallback = pendingAlarmAudioCallback
        if (uri != null) {
            if (alarmCallback != null) {
                alarmCallback(uri)
            } else if (category != null) {
                viewModel.selectCustomLocalAudio(context, category, uri)
            }
        }
        pendingAudioCategory = null
        pendingAlarmAudioCallback = null
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            pendingNotifyAction?.invoke()
        }
        pendingNotifyAction = null
    }

    fun runWithNotificationPermission(action: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            pendingNotifyAction = action
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                Text(
                    text = stringResource(id = R.string.settings_screen_title),
                    style = MaterialTheme.typography.headlineMedium
                )
                Spacer(modifier = Modifier.height(6.dp))
                PrivacyVaultBadge()
            }
        }

        if (uiState.statusMessage != null) {
            item {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.statusMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearStatusMessage) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        item {
            val perms = uiState.popupPermissions
            val allPopupPermsGranted = perms.hasNotifications &&
                perms.canScheduleExactAlarms &&
                perms.canUseFullScreenPopup &&
                perms.canDrawOverlays &&
                perms.isIgnoringBatteryOptimizations

            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (allPopupPermsGranted) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.7f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = if (allPopupPermsGranted) {
                            "✅ Background Alerts & Screen-Off Popups Ready"
                        } else {
                            stringResource(id = R.string.settings_screen_off_popup_title)
                        },
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = "Allow these permissions so water & medicine alerts can appear prominently when supported by your device OEM (standard high-priority notifications remain active as fallback):",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (!perms.hasNotifications) {
                            Button(
                                onClick = { runWithNotificationPermission { viewModel.refreshFullScreenPopupStatus() } },
                                modifier = Modifier.testTag("fix_notifications_perm_button")
                            ) { Text("🔔 Allow Notifications") }
                        }
                        if (!perms.canScheduleExactAlarms) {
                            Button(
                                onClick = { viewModel.openExactAlarmSettings(context) },
                                modifier = Modifier.testTag("fix_exact_alarm_button")
                            ) { Text("⏰ Allow Exact Alarms") }
                        }
                        if (!perms.canUseFullScreenPopup) {
                            Button(
                                onClick = { viewModel.openFullScreenPopupSettings(context) },
                                modifier = Modifier.testTag("enable_fullscreen_alarm_button")
                            ) { Text(stringResource(id = R.string.settings_screen_off_popup_cta)) }
                        }
                        if (!perms.canDrawOverlays) {
                            Button(
                                onClick = { viewModel.openOverlayPopupSettings(context) },
                                modifier = Modifier.testTag("fix_overlay_popup_button")
                            ) { Text("🪟 Allow Popup Over Apps") }
                        }
                        if (!perms.isIgnoringBatteryOptimizations) {
                            OutlinedButton(
                                onClick = { viewModel.openBatteryOptimizationSettings(context) },
                                modifier = Modifier.testTag("fix_battery_opt_button")
                            ) { Text("🔋 Allow Background Run") }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            runWithNotificationPermission {
                                viewModel.scheduleTestBackgroundPopup()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("test_5s_background_popup_button")
                    ) {
                        Text("⏱️ Test Background Popup in 5s (Lock Screen Now)")
                    }
                }
            }
        }

        // Dedicated Category Sound Picker Component (Bundled local audio files + MediaPlayer)
        item {
            CategorySoundPickerCard(
                modifier = Modifier.widthIn(max = 600.dp),
                waterTone = uiState.preferences.waterTone,
                periodTone = uiState.preferences.periodTone,
                wellnessTone = uiState.preferences.wellnessTone,
                playingToneKey = uiState.playingToneKey,
                onSelectBundledTone = { category, tone ->
                    viewModel.selectBundledToneForCategory(category, tone)
                },
                onPreviewBundledTone = viewModel::previewBundledTone,
                onStopPreview = viewModel::stopAudioPreview,
                onSelectSystemTone = { category, source ->
                    viewModel.selectBuiltInOrSystemTone(category, source)
                },
                onPickLocalStorageAudio = { category ->
                    pendingAlarmAudioCallback = null
                    pendingAudioCategory = category
                    audioPickerLauncher.launch(arrayOf("audio/*"))
                },
                onToggleDailyReminder = { category, enabled, hour, min ->
                    if (enabled) {
                        runWithNotificationPermission {
                            viewModel.toggleReminder(category, true, hour, min)
                        }
                    } else {
                        viewModel.toggleReminder(category, false, hour, min)
                    }
                },
                onUpdateCategoryInterval = { category, intervalMinutes ->
                    viewModel.updateCategoryInterval(category, intervalMinutes)
                },
                onUpdateCategoryStyle = { category, emoji, message, gesturePreset ->
                    viewModel.updateCategoryNotificationStyle(category, emoji, message, gesturePreset)
                },
                onTestCategoryNotification = { category ->
                    runWithNotificationPermission {
                        viewModel.sendTestNotification(category)
                    }
                }
            )
        }

        // Dedicated Custom Alarms Component (Room SQLite backed)
        item {
            CustomAlarmsSettingsCard(
                modifier = Modifier.widthIn(max = 600.dp),
                alarms = uiState.customAlarms,
                alertMetrics = uiState.alertMetrics,
                playingToneKey = uiState.playingToneKey,
                onPreviewBundledTone = viewModel::previewBundledTone,
                onStopPreview = viewModel::stopAudioPreview,
                onPreviewAlarm = viewModel::previewCustomAlarm,
                onToggleAlarm = { alarm, enabled ->
                    if (enabled) {
                        runWithNotificationPermission {
                            viewModel.toggleCustomAlarm(alarm, true)
                        }
                    } else {
                        viewModel.toggleCustomAlarm(alarm, false)
                    }
                },
                onDeleteAlarm = viewModel::deleteCustomAlarm,
                onTestAlarm = { alarm ->
                    runWithNotificationPermission {
                        viewModel.testCustomAlarmNotification(alarm)
                    }
                },
                onSaveAlarm = { existingId, title, moduleKey, hour, minute, mask, soundKey, customUri, customLabel, alarmEmoji, customMsg, gesturePreset, repeatIntervalMinutes ->
                    runWithNotificationPermission {
                        viewModel.saveCustomAlarm(
                            existingId = existingId,
                            title = title,
                            moduleKey = moduleKey,
                            hour = hour,
                            minute = minute,
                            daysOfWeekMask = mask,
                            soundKey = soundKey,
                            customSoundUri = customUri,
                            customSoundLabel = customLabel,
                            alarmEmoji = alarmEmoji,
                            customMessage = customMsg,
                            gesturePreset = gesturePreset,
                            repeatIntervalMinutes = repeatIntervalMinutes
                        )
                    }
                },
                onLaunchAudioPickerForDialog = { callback ->
                    pendingAudioCategory = null
                    pendingAlarmAudioCallback = callback
                    audioPickerLauncher.launch(arrayOf("audio/*"))
                },
                resolveAudioDisplayName = { uri ->
                    viewModel.resolvePickedAudioDisplayName(context, uri)
                }
            )
        }

        // Theme, Language & Profile Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.settings_appearance_title),
                        style = MaterialTheme.typography.titleMedium
                    )

                    Text(stringResource(id = R.string.settings_theme_mode), style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ThemeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = uiState.preferences.themeMode == mode,
                                onClick = { viewModel.setThemeMode(mode) },
                                label = { Text(mode.label) },
                                modifier = Modifier.testTag("theme_chip_${mode.name.lowercase()}")
                            )
                        }
                    }

                    Text(stringResource(id = R.string.settings_regional_language), style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SupportedLanguage.entries.forEach { lang ->
                            FilterChip(
                                selected = uiState.preferences.language == lang,
                                onClick = { viewModel.setLanguage(lang) },
                                label = {
                                    val label = if (lang == SupportedLanguage.ENGLISH) {
                                        lang.nativeName
                                    } else {
                                        "${lang.nativeName} (${lang.displayLabel})"
                                    }
                                    Text(label)
                                },
                                modifier = Modifier.testTag("settings_lang_chip_${lang.code}")
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Show Period & Cycle Tracker", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "Always free • Hide or show anytime",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiState.profile?.cycleTrackingEnabled ?: true,
                            onCheckedChange = viewModel::setCycleVisibility
                        )
                    }

                    OutlinedButton(
                        onClick = onEditProfile,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_edit_profile_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Edit, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Edit Local Profile (${uiState.profile?.fullName ?: "User"})")
                    }

                    OutlinedButton(
                        onClick = onOpenFamilyCircle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_open_family_circle_button")
                    ) {
                        Text("👨\u200D👩\u200D👧 Manage Family Circle & Multi-Profile (Up to 5)")
                    }
                }
            }
        }

        // Google Play Billing Subscription & Entitlement Card
        item {
            val entitlement = uiState.effectiveEntitlement
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "💎 LifeWeave Plans & Google Play Billing",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "${entitlement.tier.displayName} • ${entitlement.lifecycleStatus.label} (Max ${entitlement.maxAllowedProfiles} profiles). Period Tracker is always ₹0 Free Forever.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenSubscription,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_manage_plans_button")
                        ) {
                            Text("Manage Plans")
                        }
                        OutlinedButton(
                            onClick = viewModel::restoreGooglePlayPurchases,
                            enabled = !uiState.isRestoringOrExporting,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("settings_restore_play_purchases_button")
                        ) {
                            Text("Restore Purchases")
                        }
                    }
                }
            }
        }

        // Data Sovereignty: Export, Encrypted Keystore Info, Wipe
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Security,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Local Data Vault & Portability",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "Device Protection: ${if (uiState.vaultStatus.keystoreAvailable) "Hardware-backed local encryption active" else "On-device protection active"}",
                                style = MaterialTheme.typography.labelLarge
                            )
                            Text(
                                text = "Portable JSON exports all 17 wellness tables for moving between devices. Encrypted LCENC1 archives use hardware Keystore AES-256-GCM bound to this device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Button(
                        onClick = {
                            exportJsonLauncher.launch("lifeweave_local_backup.json")
                        },
                        enabled = !uiState.isRestoringOrExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_export_json_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Download, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(id = R.string.privacy_export_json))
                    }

                    OutlinedButton(
                        onClick = {
                            exportEncryptedLauncher.launch("lifeweave_encrypted_vault.lwvault")
                        },
                        enabled = !uiState.isRestoringOrExporting && uiState.vaultStatus.keystoreAvailable,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_export_encrypted_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Lock, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(id = R.string.privacy_export_encrypted))
                    }

                    OutlinedButton(
                        onClick = {
                            restoreBackupLauncher.launch(arrayOf("application/json", "application/octet-stream", "*/*"))
                        },
                        enabled = !uiState.isRestoringOrExporting,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_restore_backup_button")
                    ) {
                        Icon(imageVector = Icons.Rounded.Restore, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(id = R.string.privacy_restore_backup))
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirmDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_delete_all_button"),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(imageVector = Icons.Rounded.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text(stringResource(id = R.string.privacy_delete_all))
                    }
                }
            }
        }

        // All Modules Free & Optional License Info
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Key,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "🎉 All Wellness Modules Free",
                            style = MaterialTheme.typography.titleMedium
                        )
                    }

                    Text(
                        text = "Every module—including Doctors & Medicines, Fitness, Yoga, Diet, Beauty, Digital Wellbeing, and On-Device AI—is completely unlocked and free with zero key needed.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = activationTokenInput,
                        onValueChange = { activationTokenInput = it },
                        label = { Text("Optional Owner Key (Not Required)") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activation_token_input")
                    )

                    Button(
                        onClick = { viewModel.verifyOfflineActivationKey(activationTokenInput) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("activation_verify_button")
                    ) {
                        Text(stringResource(id = R.string.activation_verify_button))
                    }

                    if (uiState.activationBanner != null) {
                        val isOk = uiState.activationSuccess == true
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (isOk) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.error.copy(alpha = 0.14f)
                            }
                        ) {
                            Text(
                                text = uiState.activationBanner,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (isOk) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.error
                                },
                                modifier = Modifier.padding(12.dp)
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    uiState.modules.forEach { mod ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = mod.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            ModuleStatusPill(
                                isAlwaysFree = mod.isAlwaysFree,
                                isActivated = mod.isActivated,
                                isPhaseOneBuiltIn = mod.isBuiltInPhaseOne
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    if (showDeleteConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmDialog = false },
            title = { Text("Delete All Local Data?") },
            text = {
                Text(
                    "This permanently erases your local profile, hydration records, period history, custom alarms, and alert preferences from this device. Export a JSON backup first if needed."
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmDialog = false
                        viewModel.deleteAllLocalData(onAllDataWiped)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error
                    ),
                    modifier = Modifier.testTag("confirm_delete_all_button")
                ) {
                    Text("Erase Everything")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmDialog = false }) {
                    Text("Keep Data")
                }
            }
        )
    }

    uiState.pendingRestorePreview?.let { preview ->
        AlertDialog(
            onDismissRequest = viewModel::dismissRestorePreview,
            title = {
                Text(if (preview.isEncrypted) "🔐 Restore Encrypted Vault?" else "📥 Restore JSON Backup?")
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "Profile: ${preview.profileName} • ${preview.totalRecords} records",
                        style = MaterialTheme.typography.titleSmall
                    )
                    Text(
                        text = preview.breakdownText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Schema: ${preview.schemaVersion}. Confirming will atomically replace current local records; if anything fails, existing data stays untouched.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = viewModel::confirmRestoreBackup,
                    modifier = Modifier.testTag("confirm_restore_backup_button")
                ) {
                    Text("Restore Vault")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissRestorePreview) {
                    Text("Cancel")
                }
            }
        )
    }
}
