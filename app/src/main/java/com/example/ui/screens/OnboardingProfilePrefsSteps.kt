package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.BrightnessAuto
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Medication
import androidx.compose.material.icons.rounded.MonitorHeart
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Sync
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ui.theme.BotanicalSagePrimary
import com.example.ui.theme.DeepPineBackground
import com.example.ui.theme.MineralTealSecondary
import com.example.ui.theme.SoftSagePrimary
import com.example.ui.theme.ThemeMode
import com.example.ui.theme.WarmLinenBackground
import com.example.ui.viewmodel.OnboardingNotificationPrefs
import com.example.ui.viewmodel.ProfileFormState
import com.example.ui.viewmodel.ProfileSetupViewModel
import java.util.Locale
import kotlin.math.roundToInt

private val OnboardingGenderOptions = listOf(
    "Female",
    "Male",
    "Non-binary",
    "Prefer not to say",
    "Custom"
)

private val OnboardingActivityOptions = listOf(
    "Low",
    "Moderate",
    "Active"
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun OnboardingProfileStep(
    formState: ProfileFormState,
    profileViewModel: ProfileSetupViewModel,
    onContinueToPreferences: () -> Unit
) {
    val bmiPreview = remember(formState.heightCmText, formState.weightKgText) {
        formState.computeBmiPreview()
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("onboarding_step_profile"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Personalize your companion",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Your name, age, gender identity, and basic health metrics stay private on your device to tailor hydration, wellness insights, and optional always-free cycle tracking.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Personal identity card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "About You",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    OutlinedTextField(
                        value = formState.fullName,
                        onValueChange = profileViewModel::updateName,
                        label = { Text("Name or preferred nickname") },
                        placeholder = { Text("e.g., Ananya or Alex") },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("onboarding_name_input")
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedTextField(
                            value = formState.ageText,
                            onValueChange = profileViewModel::updateAge,
                            label = { Text("Age") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_age_input")
                        )
                        OutlinedTextField(
                            value = formState.heightCmText,
                            onValueChange = profileViewModel::updateHeightCm,
                            label = { Text("Height (cm)") },
                            placeholder = { Text("Optional") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_height_input")
                        )
                        OutlinedTextField(
                            value = formState.weightKgText,
                            onValueChange = profileViewModel::updateWeightKg,
                            label = { Text("Weight (kg)") },
                            placeholder = { Text("Optional") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_weight_input")
                        )
                    }

                    if (bmiPreview != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_bmi_preview")
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.MonitorHeart,
                                    contentDescription = "Health snapshot",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = String.format(
                                            Locale.US,
                                            "BMI %.1f • %s",
                                            bmiPreview.bmiValue,
                                            bmiPreview.categoryLabel
                                        ),
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                    Text(
                                        text = "Suggested daily water goal updated automatically (${formState.dailyWaterGoalMl} ml).",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "Gender Identity",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OnboardingGenderOptions.forEach { option ->
                            FilterChip(
                                selected = formState.genderIdentity == option,
                                onClick = { profileViewModel.updateGenderIdentity(option) },
                                label = { Text(option) },
                                modifier = Modifier.testTag(
                                    "onboarding_gender_${option.lowercase().replace(" ", "_")}"
                                )
                            )
                        }
                    }

                    if (formState.genderIdentity == "Custom") {
                        OutlinedTextField(
                            value = formState.customGenderLabel,
                            onValueChange = profileViewModel::updateCustomGender,
                            label = { Text("How would you like to identify?") },
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("onboarding_custom_gender_input")
                        )
                    }
                }
            }

            // Basic health & hydration goals card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Activity & Daily Hydration Goal",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OnboardingActivityOptions.forEach { level ->
                            FilterChip(
                                selected = formState.activityLevel == level,
                                onClick = { profileViewModel.updateActivityLevel(level) },
                                label = { Text(level) },
                                modifier = Modifier.testTag("onboarding_activity_${level.lowercase()}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Daily Water Goal",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${formState.dailyWaterGoalMl} ml",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    Slider(
                        value = formState.dailyWaterGoalMl.toFloat(),
                        onValueChange = { value ->
                            val snapped = ((value / 100f).roundToInt() * 100).coerceIn(1000, 5000)
                            profileViewModel.updateDailyWaterGoal(snapped)
                        },
                        valueRange = 1000f..5000f,
                        modifier = Modifier.testTag("onboarding_water_goal_slider")
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Cycle & Period Care",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Surface(
                                    shape = RoundedCornerShape(999.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Text(
                                        text = "Always Free",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onTertiaryContainer,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Private cycle predictions, PMS & symptom logs on your device.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Switch(
                            checked = formState.cycleTrackingEnabled,
                            onCheckedChange = profileViewModel::updateCycleTrackingEnabled,
                            modifier = Modifier
                                .minimumInteractiveComponentSize()
                                .testTag("onboarding_cycle_switch")
                        )
                    }

                    if (formState.cycleTrackingEnabled) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = "Typical cycle length: ${formState.averageCycleLengthDays} days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = formState.averageCycleLengthDays.toFloat(),
                                onValueChange = { profileViewModel.updateAverageCycleLength(it.roundToInt()) },
                                valueRange = 21f..45f
                            )
                            Text(
                                text = "Typical period duration: ${formState.averagePeriodDurationDays} days",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Slider(
                                value = formState.averagePeriodDurationDays.toFloat(),
                                onValueChange = { profileViewModel.updateAveragePeriodDuration(it.roundToInt()) },
                                valueRange = 2f..10f
                            )
                        }
                    }
                }
            }

            if (formState.errorMessage != null) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("onboarding_profile_error")
                ) {
                    Text(
                        text = formState.errorMessage,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            Button(
                onClick = {
                    if (profileViewModel.validateProfileInputs()) {
                        onContinueToPreferences()
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_profile_next_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Next: Theme & Notifications",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Continue to theme and notifications"
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
internal fun OnboardingPreferencesStep(
    selectedThemeMode: ThemeMode,
    onSelectThemeMode: (ThemeMode) -> Unit,
    cycleTrackingEnabled: Boolean,
    notificationPrefs: OnboardingNotificationPrefs,
    onUpdateNotificationPrefs: (OnboardingNotificationPrefs) -> Unit,
    isSaving: Boolean,
    onOpenPermissionsPopup: () -> Unit,
    onFinishOnboarding: () -> Unit
) {
    val context = LocalContext.current
    var notificationsGranted by remember {
        mutableStateOf(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
            } else {
                true
            }
        )
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        notificationsGranted = granted
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp)
                .testTag("onboarding_step_preferences"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "Make LifeWeave feel like yours",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Preview light or dark mode live and choose the gentle reminders you want from day one. You can change any of these anytime in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Live Theme Picker Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Appearance & Theme",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        ThemePreviewTile(
                            mode = ThemeMode.SYSTEM,
                            label = "Auto",
                            icon = Icons.Rounded.BrightnessAuto,
                            selected = selectedThemeMode == ThemeMode.SYSTEM,
                            previewBrush = Brush.linearGradient(
                                listOf(WarmLinenBackground, DeepPineBackground)
                            ),
                            accentColor = BotanicalSagePrimary,
                            onClick = { onSelectThemeMode(ThemeMode.SYSTEM) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_theme_system")
                        )
                        ThemePreviewTile(
                            mode = ThemeMode.LIGHT,
                            label = "Light",
                            icon = Icons.Rounded.LightMode,
                            selected = selectedThemeMode == ThemeMode.LIGHT,
                            previewBrush = Brush.verticalGradient(
                                listOf(WarmLinenBackground, Color.White)
                            ),
                            accentColor = BotanicalSagePrimary,
                            onClick = { onSelectThemeMode(ThemeMode.LIGHT) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_theme_light")
                        )
                        ThemePreviewTile(
                            mode = ThemeMode.DARK,
                            label = "Dark",
                            icon = Icons.Rounded.DarkMode,
                            selected = selectedThemeMode == ThemeMode.DARK,
                            previewBrush = Brush.verticalGradient(
                                listOf(DeepPineBackground, Color(0xFF1B3A7A))
                            ),
                            accentColor = SoftSagePrimary,
                            onClick = { onSelectThemeMode(ThemeMode.DARK) },
                            modifier = Modifier
                                .weight(1f)
                                .testTag("onboarding_theme_dark")
                        )
                    }
                }
            }

            // Notification Permission & Reminder Preferences Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Gentle Reminders & Alerts",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (notificationsGranted) {
                                    "Notifications permission is active."
                                } else {
                                    "Allow notifications so your water & medicine alerts arrive on time."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (notificationsGranted) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = "Notifications enabled",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }

                    if (!notificationsGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        FilledTonalButton(
                            onClick = {
                                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("onboarding_request_notif_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = "Allow notifications"
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Allow Notifications")
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                    PreferenceSwitchRow(
                        icon = Icons.Rounded.WaterDrop,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "Hydration Reminders",
                        subtitle = "Friendly water check-ins every 2 hours (10:00 AM onwards)",
                        checked = notificationPrefs.waterRemindersEnabled,
                        onCheckedChange = {
                            onUpdateNotificationPrefs(notificationPrefs.copy(waterRemindersEnabled = it))
                        },
                        testTag = "onboarding_pref_water_switch"
                    )

                    PreferenceSwitchRow(
                        icon = Icons.Rounded.Medication,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Evening Wellness & Medicine Check-in",
                        subtitle = "Daily 8:00 PM prompt for medicines & daily goal review",
                        checked = notificationPrefs.wellnessRemindersEnabled,
                        onCheckedChange = {
                            onUpdateNotificationPrefs(notificationPrefs.copy(wellnessRemindersEnabled = it))
                        },
                        testTag = "onboarding_pref_wellness_switch"
                    )

                    if (cycleTrackingEnabled) {
                        PreferenceSwitchRow(
                            icon = Icons.Rounded.Favorite,
                            iconTint = MaterialTheme.colorScheme.tertiary,
                            title = "Cycle Care Reminders",
                            subtitle = "Private morning heads-up before predicted period days",
                            checked = notificationPrefs.cycleRemindersEnabled,
                            onCheckedChange = {
                                onUpdateNotificationPrefs(notificationPrefs.copy(cycleRemindersEnabled = it))
                            },
                            testTag = "onboarding_pref_cycle_switch"
                        )
                    }

                    PreferenceSwitchRow(
                        icon = Icons.Rounded.PhoneAndroid,
                        iconTint = MaterialTheme.colorScheme.secondary,
                        title = "Digital Wellbeing Balance Alerts",
                        subtitle = "Notify at 80% of your daily screen-time budget",
                        checked = notificationPrefs.wellbeingOveruseAlertsEnabled,
                        onCheckedChange = {
                            onUpdateNotificationPrefs(notificationPrefs.copy(wellbeingOveruseAlertsEnabled = it))
                        },
                        testTag = "onboarding_pref_wellbeing_switch"
                    )

                    PreferenceSwitchRow(
                        icon = Icons.Rounded.Bedtime,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Bedtime Sleep & Snore Monitor Prompt",
                        subtitle = "Optional 10:30 PM prompt to start overnight acoustic sleep tracking",
                        checked = notificationPrefs.sleepBedtimePromptEnabled,
                        onCheckedChange = {
                            onUpdateNotificationPrefs(notificationPrefs.copy(sleepBedtimePromptEnabled = it))
                        },
                        testTag = "onboarding_pref_sleep_switch"
                    )

                    PreferenceSwitchRow(
                        icon = Icons.Rounded.Sync,
                        iconTint = MaterialTheme.colorScheme.primary,
                        title = "Auto-Refresh Wellbeing & Wearables",
                        subtitle = "Keep screen time, Health Connect & modules fresh every 30 min",
                        checked = notificationPrefs.autoRefreshEnabled,
                        onCheckedChange = {
                            onUpdateNotificationPrefs(notificationPrefs.copy(autoRefreshEnabled = it))
                        },
                        testTag = "onboarding_pref_autorefresh_switch"
                    )
                }
            }

            // Optional Device Permissions Popup Trigger
            OutlinedButton(
                onClick = onOpenPermissionsPopup,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("onboarding_optional_permissions_button"),
                shape = RoundedCornerShape(14.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = "Optional device permissions"
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Configure Optional Permissions (Usage Access, Voice)")
            }

            Button(
                onClick = onFinishOnboarding,
                enabled = !isSaving,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("onboarding_finish_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.5.dp,
                        color = MaterialTheme.colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Setting up your dashboard…")
                } else {
                    Icon(
                        imageVector = Icons.Rounded.AutoAwesome,
                        contentDescription = "Finish onboarding"
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Finish Setup & Open Dashboard",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun ThemePreviewTile(
    mode: ThemeMode,
    label: String,
    icon: ImageVector,
    selected: Boolean,
    previewBrush: Brush,
    accentColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
        },
        border = BorderStroke(if (selected) 2.dp else 1.dp, borderColor),
        modifier = modifier
            .heightIn(min = 104.dp)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(previewBrush),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(12.dp)
                            .clip(CircleShape)
                            .background(accentColor)
                    )
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(MineralTealSecondary)
                    )
                }
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "${mode.label} theme",
                    tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun PreferenceSwitchRow(
    icon: ImageVector,
    iconTint: Color,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = iconTint.copy(alpha = 0.14f),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = iconTint,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier
                .minimumInteractiveComponentSize()
                .testTag(testTag)
        )
    }
}
