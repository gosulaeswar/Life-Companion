package com.example.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Layers
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.QueryStats
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.notifications.AlertSoundManager
import com.example.ui.components.PrivacyVaultBadge
import com.example.wellbeing.DeviceWellbeingManager

@Composable
fun PermissionsSetupScreen(
    alertSoundManager: AlertSoundManager,
    deviceWellbeingManager: DeviceWellbeingManager,
    onContinueToLogin: () -> Unit
) {
    val context = LocalContext.current
    var refreshTick by remember { mutableIntStateOf(0) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                refreshTick++
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val multiRuntimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        refreshTick++
    }

    val singleRuntimeLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        refreshTick++
    }

    val hasNotifications = remember(refreshTick) { alertSoundManager.hasNotificationPermission() }
    val hasExactAlarms = remember(refreshTick) { alertSoundManager.canScheduleExactAlarms() }
    val hasFullScreen = remember(refreshTick) { alertSoundManager.canUseFullScreenIntent() }
    val hasOverlayPopup = remember(refreshTick) { alertSoundManager.canDrawOverlays() }
    val hasUnrestrictedBattery = remember(refreshTick) { alertSoundManager.isIgnoringBatteryOptimizations() }
    val hasUsageAccess = remember(refreshTick) { deviceWellbeingManager.hasUsageAccessPermission() }
    val hasMicrophone = remember(refreshTick) { alertSoundManager.hasMicrophonePermission() }

    val grantedCount = listOf(
        hasNotifications,
        hasExactAlarms,
        hasFullScreen,
        hasOverlayPopup,
        hasUnrestrictedBattery,
        hasUsageAccess,
        hasMicrophone
    ).count { it }

    fun requestAllRuntimeDialogs() {
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
            add(Manifest.permission.RECORD_AUDIO)
        }.toTypedArray()
        multiRuntimeLauncher.launch(perms)
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 580.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PrivacyVaultBadge(text = stringResource(id = R.string.welcome_badge_on_device))
                    Surface(
                        shape = CircleShape,
                        color = if (grantedCount >= 5) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.tertiaryContainer
                        }
                    ) {
                        Text(
                            text = "$grantedCount / 7 Ready",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                        )
                    }
                }

                Text(
                    text = stringResource(id = R.string.permissions_setup_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = stringResource(id = R.string.permissions_setup_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (!hasNotifications || !hasMicrophone) {
                    FilledTonalButton(
                        onClick = { requestAllRuntimeDialogs() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                            .testTag("grant_runtime_permissions_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.Rounded.NotificationsActive, contentDescription = null)
                        Spacer(modifier = Modifier.size(8.dp))
                        Text(stringResource(id = R.string.permissions_grant_prompts_cta))
                    }
                }

                PermissionAccessRowCard(
                    icon = Icons.Rounded.NotificationsActive,
                    title = "Notifications & Care Notes",
                    description = "Required for background water intake notes, medicine dose alerts & doctor reminders.",
                    isGranted = hasNotifications,
                    testTag = "perm_row_notifications",
                    onAllowClick = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            singleRuntimeLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            refreshTick++
                        }
                    }
                )

                PermissionAccessRowCard(
                    icon = Icons.Rounded.Alarm,
                    title = "Exact Time Alarms",
                    description = "Ensures medicine & hydration alarms fire at the exact selected minute, even in sleep/Doze mode.",
                    isGranted = hasExactAlarms,
                    testTag = "perm_row_exact_alarms",
                    onAllowClick = {
                        runCatching {
                            context.startActivity(alertSoundManager.createExactAlarmSettingsIntent())
                        }
                    }
                )

                PermissionAccessRowCard(
                    icon = Icons.Rounded.PhoneAndroid,
                    title = "Screen-Off Full-Screen Popups",
                    description = "Allows your mini-alarm & emoji gesture card to wake and appear when the screen is off or locked.",
                    isGranted = hasFullScreen,
                    testTag = "perm_row_fullscreen",
                    onAllowClick = {
                        runCatching {
                            context.startActivity(alertSoundManager.createFullScreenIntentSettingsIntent())
                        }
                    }
                )

                PermissionAccessRowCard(
                    icon = Icons.Rounded.Layers,
                    title = "Floating Popup Over Apps",
                    description = "Lets the mini-alarm card pop up immediately even while using other apps with screen ON.",
                    isGranted = hasOverlayPopup,
                    testTag = "perm_row_overlay",
                    onAllowClick = {
                        runCatching {
                            context.startActivity(alertSoundManager.createOverlayPermissionIntent())
                        }
                    }
                )

                PermissionAccessRowCard(
                    icon = Icons.Rounded.BatteryChargingFull,
                    title = "Unrestricted Background Running",
                    description = "Keeps alarms, hydration notes & screen-overuse checks running when the app is closed.",
                    isGranted = hasUnrestrictedBattery,
                    testTag = "perm_row_battery",
                    onAllowClick = {
                        runCatching {
                            context.startActivity(alertSoundManager.createBatteryOptimizationIntent())
                        }
                    }
                )

                PermissionAccessRowCard(
                    icon = Icons.Rounded.QueryStats,
                    title = "Digital Wellbeing Screen Time",
                    description = "Reads device app usage stats locally to highlight heavy apps and warn on overuse.",
                    isGranted = hasUsageAccess,
                    testTag = "perm_row_usage",
                    onAllowClick = {
                        val opened = runCatching {
                            context.startActivity(deviceWellbeingManager.createUsageAccessSettingsIntent(true))
                            true
                        }.getOrDefault(false)
                        if (!opened) {
                            runCatching {
                                context.startActivity(deviceWellbeingManager.createUsageAccessSettingsIntent(false))
                            }
                        }
                    }
                )

                PermissionAccessRowCard(
                    icon = Icons.Rounded.Mic,
                    title = "Microphone (Voice Logging)",
                    description = "Optional: speak naturally to log water, coffee, meals, steps & medicines.",
                    isGranted = hasMicrophone,
                    testTag = "perm_row_mic",
                    onAllowClick = {
                        singleRuntimeLauncher.launch(Manifest.permission.RECORD_AUDIO)
                    }
                )

                if (!hasNotifications || !hasFullScreen || !hasOverlayPopup) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = "Tip: Granting Notifications + Screen-Off Popups + Floating Over Apps guarantees mini-alarms appear whether your phone screen is locked or unlocked.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(14.dp)
                        )
                    }
                }

                Button(
                    onClick = onContinueToLogin,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("permissions_continue_button"),
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.permissions_continue_cta),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                        contentDescription = null
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun PermissionAccessRowCard(
    icon: ImageVector,
    title: String,
    description: String,
    isGranted: Boolean,
    testTag: String,
    onAllowClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isGranted) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.24f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isGranted) {
            BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.35f))
        } else {
            null
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (isGranted) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = if (isGranted) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    }
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isGranted) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = "Allowed",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Allowed",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                OutlinedButton(
                    onClick = onAllowClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("${testTag}_allow")
                ) {
                    Text("Allow")
                }
            }
        }
    }
}
