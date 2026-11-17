package com.example.ui.components

import android.net.Uri
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAlarm
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.AudioFile
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.data.local.entity.ReminderEntity
import com.example.notifications.AlarmEmojiCatalog
import com.example.notifications.AlarmEmojiGesturePreset
import com.example.notifications.AlertCategory
import com.example.notifications.AlertMetricsCalculator
import com.example.notifications.AlertMetricsSummary
import com.example.notifications.BundledAlertTone
import com.example.security.AlertToneSource
import com.example.security.CategoryToneSetting
import com.example.ui.theme.JetBrainsMonoMetricFont
import androidx.compose.ui.unit.sp

/**
 * Dedicated Settings component allowing users to assign bundled local audio files
 * (previewed live via Android MediaPlayer) or local device storage audio to each
 * LifeWeave alert category (Hydration, Cycle Care, Daily Wellness).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CategorySoundPickerCard(
    waterTone: CategoryToneSetting,
    periodTone: CategoryToneSetting,
    wellnessTone: CategoryToneSetting,
    playingToneKey: String?,
    onSelectBundledTone: (AlertCategory, BundledAlertTone) -> Unit,
    onPreviewBundledTone: (BundledAlertTone) -> Unit,
    onStopPreview: () -> Unit,
    onSelectSystemTone: (AlertCategory, AlertToneSource) -> Unit,
    onPickLocalStorageAudio: (AlertCategory) -> Unit,
    onToggleDailyReminder: (AlertCategory, Boolean, Int, Int) -> Unit,
    onUpdateCategoryInterval: (AlertCategory, Int) -> Unit = { _, _ -> },
    onUpdateCategoryStyle: (AlertCategory, String, String, String) -> Unit = { _, _, _, _ -> },
    onTestCategoryNotification: (AlertCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedCategory by remember { mutableStateOf(AlertCategory.WATER) }

    val activeSetting = when (selectedCategory) {
        AlertCategory.WATER -> waterTone
        AlertCategory.PERIOD -> periodTone
        AlertCategory.WELLNESS -> wellnessTone
    }

    val activeToneSummary = when (activeSetting.source) {
        AlertToneSource.CATEGORY_DEFAULT ->
            (BundledAlertTone.fromId(activeSetting.bundledToneId) ?: selectedCategory.defaultTone).displayName
        AlertToneSource.SYSTEM_ALARM -> "Device Default Alarm"
        AlertToneSource.SYSTEM_NOTIFICATION -> "Device Default Notification"
        AlertToneSource.CUSTOM_LOCAL_FILE -> activeSetting.customFileName ?: "Custom Local Audio"
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MusicNote,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Category Notification Sounds",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "Pick a bundled audio chime or local device file per alert category (MediaPlayer preview).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Category Switcher Pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AlertCategory.entries.forEach { category ->
                    FilterChip(
                        selected = selectedCategory == category,
                        onClick = { selectedCategory = category },
                        label = { Text(category.title) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("sound_category_tab_${category.key}")
                    )
                }
            }

            // Active Category Summary & Quick Daily Reminder Row
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${selectedCategory.title} Active Sound",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = activeToneSummary,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        TextButton(
                            onClick = { onTestCategoryNotification(selectedCategory) },
                            modifier = Modifier.testTag("test_alert_${selectedCategory.key}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Test Alert")
                        }
                    }

                    var showDailyCheckInClock by remember(selectedCategory) { mutableStateOf(false) }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val timeText = String.format(
                            "%02d:%02d",
                            activeSetting.reminderHour,
                            activeSetting.reminderMinute
                        )
                        Text(
                            text = "Daily ${selectedCategory.title} Check-In ($timeText)",
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = JetBrainsMonoMetricFont
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .clickable { showDailyCheckInClock = true }
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            TextButton(
                                onClick = { showDailyCheckInClock = true },
                                modifier = Modifier.testTag("pick_time_${selectedCategory.key}")
                            ) {
                                Text("Set Time")
                            }
                            Switch(
                                checked = activeSetting.reminderEnabled,
                                onCheckedChange = { enabled ->
                                    onToggleDailyReminder(
                                        selectedCategory,
                                        enabled,
                                        activeSetting.reminderHour,
                                        activeSetting.reminderMinute
                                    )
                                },
                                modifier = Modifier.testTag("toggle_reminder_${selectedCategory.key}")
                            )
                        }
                    }
                    if (showDailyCheckInClock) {
                        ClockTimePickerDialog(
                            initialHour = activeSetting.reminderHour,
                            initialMinute = activeSetting.reminderMinute,
                            title = "Daily ${selectedCategory.title} Time",
                            onDismiss = { showDailyCheckInClock = false },
                            onTimeSelected = { h, m ->
                                onToggleDailyReminder(
                                    selectedCategory,
                                    activeSetting.reminderEnabled,
                                    h,
                                    m
                                )
                                showDailyCheckInClock = false
                            }
                        )
                    }

                    // Quick Interval Cadence Row for Category Reminder
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Repeat Cadence: ${AlertMetricsCalculator.formatIntervalLabel(activeSetting.repeatIntervalMinutes)}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(60, 120, 240, 1440).forEach { mins ->
                                FilterChip(
                                    selected = activeSetting.repeatIntervalMinutes == mins,
                                    onClick = { onUpdateCategoryInterval(selectedCategory, mins) },
                                    label = { Text(AlertMetricsCalculator.formatIntervalShort(mins)) },
                                    modifier = Modifier.testTag("category_interval_${selectedCategory.key}_$mins")
                                )
                            }
                        }
                    }

                    var editCategoryStyle by remember(selectedCategory) { mutableStateOf(false) }
                    val activeEmoji = activeSetting.customEmoji?.takeIf { it.isNotBlank() }
                        ?: AlarmEmojiCatalog.defaultEmojiForCategory(selectedCategory.key)
                    val activeGesture = AlarmEmojiGesturePreset.fromId(activeSetting.gesturePreset)

                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Popup Style: $activeEmoji • ${activeGesture.shortBadge}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = { editCategoryStyle = !editCategoryStyle }) {
                            Text(if (editCategoryStyle) "Hide" else "Customize Emoji & Gesture")
                        }
                    }

                    if (editCategoryStyle) {
                        var draftEmoji by remember(selectedCategory, activeSetting.customEmoji) {
                            mutableStateOf(activeEmoji)
                        }
                        var draftMsg by remember(selectedCategory, activeSetting.customMessage) {
                            mutableStateOf(activeSetting.customMessage.orEmpty())
                        }
                        var draftGesture by remember(selectedCategory, activeSetting.gesturePreset) {
                            mutableStateOf(activeSetting.gesturePreset)
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                AlarmEmojiCatalog.curatedEmojis.take(8).forEach { emojiChoice ->
                                    FilterChip(
                                        selected = draftEmoji == emojiChoice,
                                        onClick = { draftEmoji = emojiChoice },
                                        label = { Text(emojiChoice, fontSize = 16.sp) }
                                    )
                                }
                            }
                            OutlinedTextField(
                                value = draftMsg,
                                onValueChange = { draftMsg = it },
                                label = { Text("Custom Popup Message (optional)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                AlarmEmojiGesturePreset.entries.forEach { preset ->
                                    FilterChip(
                                        selected = draftGesture == preset.id,
                                        onClick = { draftGesture = preset.id },
                                        label = { Text(preset.shortBadge) }
                                    )
                                }
                            }
                            Button(
                                onClick = {
                                    onUpdateCategoryStyle(selectedCategory, draftEmoji, draftMsg, draftGesture)
                                    editCategoryStyle = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Popup Style")
                            }
                        }
                    }
                }
            }

            // Bundled Local Audio Library
            Text(
                text = "Bundled Local Audio Files",
                style = MaterialTheme.typography.labelLarge
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                BundledAlertTone.entries.forEach { tone ->
                    val isSelected = activeSetting.source == AlertToneSource.CATEGORY_DEFAULT &&
                        (activeSetting.bundledToneId ?: selectedCategory.defaultTone.id) == tone.id
                    val isPlaying = playingToneKey == "bundled_${tone.id}"

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onSelectBundledTone(selectedCategory, tone) }
                            .testTag("bundled_tone_${selectedCategory.key}_${tone.id}"),
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                        },
                        border = if (isSelected) {
                            BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary)
                        } else {
                            null
                        }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isSelected) Icons.Rounded.CheckCircle else Icons.Rounded.MusicNote,
                                    contentDescription = if (isSelected) "Selected tone" else "Bundled tone",
                                    tint = if (isSelected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                                Column {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(
                                            text = tone.displayName,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = tone.durationLabel,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = tone.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            IconButton(
                                onClick = {
                                    if (isPlaying) onStopPreview() else onPreviewBundledTone(tone)
                                },
                                modifier = Modifier.testTag("preview_bundled_${tone.id}")
                            ) {
                                Icon(
                                    imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                    contentDescription = if (isPlaying) "Stop ${tone.displayName}" else "Play ${tone.displayName}",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }

            // Device Storage & System Tone Options
            Text(
                text = "Or Choose From Device Storage / System",
                style = MaterialTheme.typography.labelLarge
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                FilterChip(
                    selected = activeSetting.source == AlertToneSource.CUSTOM_LOCAL_FILE,
                    onClick = { onPickLocalStorageAudio(selectedCategory) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.AudioFile,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    },
                    label = { Text("Pick Local Audio File") },
                    modifier = Modifier.testTag("pick_local_audio_${selectedCategory.key}")
                )
                FilterChip(
                    selected = activeSetting.source == AlertToneSource.SYSTEM_NOTIFICATION,
                    onClick = { onSelectSystemTone(selectedCategory, AlertToneSource.SYSTEM_NOTIFICATION) },
                    label = { Text("Device Notification") }
                )
                FilterChip(
                    selected = activeSetting.source == AlertToneSource.SYSTEM_ALARM,
                    onClick = { onSelectSystemTone(selectedCategory, AlertToneSource.SYSTEM_ALARM) },
                    label = { Text("Device Alarm") }
                )
            }
        }
    }
}

/**
 * Custom Alarms manager card & creator/editor modal backed by Room SQLite `reminders` table.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomAlarmsSettingsCard(
    alarms: List<ReminderEntity>,
    alertMetrics: AlertMetricsSummary = AlertMetricsCalculator.summarize(alarms),
    playingToneKey: String?,
    onPreviewBundledTone: (BundledAlertTone) -> Unit,
    onStopPreview: () -> Unit,
    onPreviewAlarm: (ReminderEntity) -> Unit,
    onToggleAlarm: (ReminderEntity, Boolean) -> Unit,
    onDeleteAlarm: (ReminderEntity) -> Unit,
    onTestAlarm: (ReminderEntity) -> Unit,
    onSaveAlarm: (
        existingId: Long,
        title: String,
        moduleKey: String,
        hour: Int,
        minute: Int,
        daysMask: Int,
        soundKey: String,
        customUri: String?,
        customLabel: String?,
        alarmEmoji: String,
        customMessage: String,
        gesturePreset: String,
        repeatIntervalMinutes: Int
    ) -> Unit,
    onLaunchAudioPickerForDialog: ((Uri) -> Unit) -> Unit,
    resolveAudioDisplayName: (Uri) -> String,
    modifier: Modifier = Modifier
) {
    var showAlarmDialog by remember { mutableStateOf(false) }
    var editingAlarm by remember { mutableStateOf<ReminderEntity?>(null) }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Alarm,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Column {
                        Text(
                            text = "Custom Alarms",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "Create local scheduled alarms with custom times, days, and sounds.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = {
                    editingAlarm = null
                    showAlarmDialog = true
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("add_custom_alarm_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(imageVector = Icons.Rounded.AddAlarm, contentDescription = null)
                Spacer(modifier = Modifier.size(6.dp))
                Text("Add Custom Alarm")
            }

            if (alarms.isNotEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    modifier = Modifier.testTag("custom_alarms_metrics_strip")
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📊 Alert Interval & Adherence Metrics",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = "⏰ ${alertMetrics.enabledAlarmsCount} active",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = "🔁 ${alertMetrics.intervalAlarmsCount} hourly/interval",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface
                            ) {
                                Text(
                                    text = "✅ ${alertMetrics.adherencePercent}% done (${alertMetrics.totalCompleted}/${alertMetrics.totalTriggered})",
                                    style = MaterialTheme.typography.labelSmall,
                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                )
                            }
                            if (alertMetrics.totalSnoozed > 0) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.surface
                                ) {
                                    Text(
                                        text = "😴 ${alertMetrics.totalSnoozed} snoozed",
                                        style = MaterialTheme.typography.labelSmall,
                                        modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (alarms.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                ) {
                    Text(
                        text = "No custom alarms yet. Tap 'Add Custom Alarm' to schedule hourly, every 2 hours, or custom-interval reminders with popups & metrics.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(14.dp)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    alarms.forEach { alarm ->
                        val soundLabel = if (alarm.soundKey == "custom_uri" && !alarm.customSoundLabel.isNullOrBlank()) {
                            alarm.customSoundLabel
                        } else {
                            BundledAlertTone.fromId(alarm.soundKey)?.displayName
                                ?: BundledAlertTone.WELLNESS_GENTLE.displayName
                        }
                        val isPlaying = playingToneKey == "alarm_${alarm.id}"

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(18.dp))
                                .clickable {
                                    editingAlarm = alarm
                                    showAlarmDialog = true
                                }
                                .testTag("custom_alarm_row_${alarm.id}"),
                            shape = RoundedCornerShape(18.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val gesturePreset = AlarmEmojiGesturePreset.fromId(alarm.gesturePreset)
                                    val intervalShort = AlertMetricsCalculator.formatIntervalShort(alarm.repeatIntervalMinutes)
                                    Column(modifier = Modifier.weight(1f)) {
                                        FlowRow(
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                            verticalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                                            ) {
                                                Text(text = alarm.alarmEmoji, fontSize = 24.sp)
                                                Text(
                                                    text = alarm.triggerTime24h,
                                                    style = MaterialTheme.typography.headlineSmall.copy(
                                                        fontFamily = JetBrainsMonoMetricFont,
                                                        fontWeight = FontWeight.Bold
                                                    ),
                                                    color = MaterialTheme.colorScheme.primary
                                                )
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = if (alarm.repeatIntervalMinutes in 1 until 1440) {
                                                    MaterialTheme.colorScheme.primaryContainer
                                                } else {
                                                    MaterialTheme.colorScheme.secondaryContainer
                                                }
                                            ) {
                                                Text(
                                                    text = "🔁 $intervalShort",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (alarm.repeatIntervalMinutes in 1 until 1440) {
                                                        MaterialTheme.colorScheme.onPrimaryContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.onSecondaryContainer
                                                    },
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = gesturePreset.shortBadge,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = alarm.title,
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        if (alarm.customMessage.isNotBlank()) {
                                            Text(
                                                text = alarm.customMessage,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }
                                        Text(
                                            text = "${formatDaysMask(alarm.daysOfWeekMask)} • $soundLabel",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            text = "📊 Metrics: 🔔 ${alarm.triggeredCount} fired • ✅ ${alarm.completedCount} done • 😴 ${alarm.snoozedCount} snoozed",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    Switch(
                                        checked = alarm.isEnabled,
                                        onCheckedChange = { enabled -> onToggleAlarm(alarm, enabled) },
                                        modifier = Modifier.testTag("toggle_custom_alarm_${alarm.id}")
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.End,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    TextButton(
                                        onClick = {
                                            if (isPlaying) onStopPreview() else onPreviewAlarm(alarm)
                                        }
                                    ) {
                                        Icon(
                                            imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.size(4.dp))
                                        Text(if (isPlaying) "Stop" else "Play")
                                    }
                                    TextButton(
                                        onClick = { onTestAlarm(alarm) },
                                        modifier = Modifier.testTag("test_popup_alarm_${alarm.id}")
                                    ) {
                                        Text("Test Popup")
                                    }
                                    IconButton(
                                        onClick = { onDeleteAlarm(alarm) },
                                        modifier = Modifier.testTag("delete_custom_alarm_${alarm.id}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.DeleteOutline,
                                            contentDescription = "Delete custom alarm"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAlarmDialog) {
        CustomAlarmEditorDialog(
            existing = editingAlarm,
            playingToneKey = playingToneKey,
            onPreviewBundledTone = onPreviewBundledTone,
            onStopPreview = onStopPreview,
            onLaunchAudioPicker = onLaunchAudioPickerForDialog,
            resolveAudioDisplayName = resolveAudioDisplayName,
            onDismiss = {
                onStopPreview()
                showAlarmDialog = false
            },
            onSave = { title, moduleKey, hour, minute, mask, soundKey, customUri, customLabel, alarmEmoji, customMsg, gesturePreset, repeatIntervalMinutes ->
                onStopPreview()
                onSaveAlarm(
                    editingAlarm?.id ?: 0L,
                    title,
                    moduleKey,
                    hour,
                    minute,
                    mask,
                    soundKey,
                    customUri,
                    customLabel,
                    alarmEmoji,
                    customMsg,
                    gesturePreset,
                    repeatIntervalMinutes
                )
                showAlarmDialog = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CustomAlarmEditorDialog(
    existing: ReminderEntity?,
    playingToneKey: String?,
    onPreviewBundledTone: (BundledAlertTone) -> Unit,
    onStopPreview: () -> Unit,
    onLaunchAudioPicker: ((Uri) -> Unit) -> Unit,
    resolveAudioDisplayName: (Uri) -> String,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        moduleKey: String,
        hour: Int,
        minute: Int,
        mask: Int,
        soundKey: String,
        customUri: String?,
        customLabel: String?,
        alarmEmoji: String,
        customMessage: String,
        gesturePreset: String,
        repeatIntervalMinutes: Int
    ) -> Unit
) {
    val parsedParts = existing?.triggerTime24h?.split(":")
    var title by remember { mutableStateOf(existing?.title ?: "Hydration Break") }
    var moduleKey by remember { mutableStateOf(existing?.moduleKey ?: AlertCategory.WATER.key) }
    var hour by remember { mutableIntStateOf(parsedParts?.getOrNull(0)?.toIntOrNull() ?: 9) }
    var minute by remember { mutableIntStateOf(parsedParts?.getOrNull(1)?.toIntOrNull() ?: 0) }
    var daysMask by remember { mutableIntStateOf(existing?.daysOfWeekMask ?: 127) }
    var soundKey by remember { mutableStateOf(existing?.soundKey ?: BundledAlertTone.WATER_DROP.id) }
    var customUri by remember { mutableStateOf(existing?.customSoundUri) }
    var customLabel by remember { mutableStateOf(existing?.customSoundLabel) }
    var alarmEmoji by remember { mutableStateOf(existing?.alarmEmoji ?: AlarmEmojiCatalog.defaultEmojiForCategory(moduleKey)) }
    var customMessage by remember { mutableStateOf(existing?.customMessage ?: "") }
    var gesturePreset by remember { mutableStateOf(existing?.gesturePreset ?: AlarmEmojiGesturePreset.SWIPE_SLIDE.id) }

    val initialInterval = existing?.repeatIntervalMinutes ?: 1440
    val isPreset = initialInterval in AlertMetricsCalculator.intervalPresetsMinutes
    var repeatIntervalMinutes by remember { mutableIntStateOf(initialInterval) }
    var showCustomIntervalInput by remember { mutableStateOf(!isPreset) }
    var customUnitHours by remember { mutableStateOf(initialInterval >= 60 && initialInterval % 60 == 0) }
    var customIntervalInputText by remember {
        mutableStateOf(
            if (initialInterval >= 60 && initialInterval % 60 == 0) {
                (initialInterval / 60).toString()
            } else {
                initialInterval.toString()
            }
        )
    }

    val dayLabels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "Set Custom Alarm" else "Edit Custom Alarm") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Alarm Label") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_alarm_title_input")
                )

                OutlinedTextField(
                    value = customMessage,
                    onValueChange = { customMessage = it },
                    label = { Text("Custom Popup Message (optional)") },
                    placeholder = { Text("e.g. Drink 250 ml & stretch your back!") },
                    maxLines = 2,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("custom_alarm_message_input")
                )

                Text("Notification Emoji", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AlarmEmojiCatalog.curatedEmojis.forEach { emojiOption ->
                        FilterChip(
                            selected = alarmEmoji == emojiOption,
                            onClick = { alarmEmoji = emojiOption },
                            label = { Text(emojiOption, fontSize = 16.sp) },
                            modifier = Modifier.testTag("alarm_emoji_$emojiOption")
                        )
                    }
                }

                Text("Emoji Popup Gesture", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlarmEmojiGesturePreset.entries.forEach { preset ->
                        FilterChip(
                            selected = gesturePreset == preset.id,
                            onClick = { gesturePreset = preset.id },
                            label = { Text(preset.shortBadge) },
                            modifier = Modifier.testTag("alarm_gesture_${preset.id}")
                        )
                    }
                }
                Text(
                    text = AlarmEmojiGesturePreset.fromId(gesturePreset).instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Text("Category", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    AlertCategory.entries.forEach { cat ->
                        FilterChip(
                            selected = moduleKey == cat.key,
                            onClick = {
                                moduleKey = cat.key
                                if (existing == null) {
                                    alarmEmoji = AlarmEmojiCatalog.defaultEmojiForCategory(cat.key)
                                }
                                if (soundKey != "custom_uri") {
                                    soundKey = cat.defaultTone.id
                                }
                            },
                            label = { Text(cat.title) }
                        )
                    }
                }

                // Time Picker Row
                var showAlarmClockPicker by remember { mutableStateOf(false) }
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = String.format("%02d:%02d", hour, minute),
                            style = MaterialTheme.typography.displaySmall.copy(
                                fontFamily = JetBrainsMonoMetricFont
                            ),
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { showAlarmClockPicker = true }
                                .padding(horizontal = 10.dp, vertical = 2.dp)
                                .testTag("alarm_time_display")
                        )
                        OutlinedButton(
                            onClick = { showAlarmClockPicker = true },
                            modifier = Modifier.testTag("alarm_clock_picker_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Alarm,
                                contentDescription = "Pick exact alarm time",
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Clock Picker")
                        }
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedButton(
                                onClick = { hour = (hour + 23) % 24 },
                                modifier = Modifier.testTag("alarm_hour_minus")
                            ) { Text("-1h") }
                            OutlinedButton(
                                onClick = { hour = (hour + 1) % 24 },
                                modifier = Modifier.testTag("alarm_hour_plus")
                            ) { Text("+1h") }
                            OutlinedButton(
                                onClick = { minute = (minute + 55) % 60 },
                                modifier = Modifier.testTag("alarm_min_minus")
                            ) { Text("-5m") }
                            OutlinedButton(
                                onClick = { minute = (minute + 5) % 60 },
                                modifier = Modifier.testTag("alarm_min_plus")
                            ) { Text("+5m") }
                        }
                    }
                }
                if (showAlarmClockPicker) {
                    ClockTimePickerDialog(
                        initialHour = hour,
                        initialMinute = minute,
                        title = "Alarm Time",
                        onDismiss = { showAlarmClockPicker = false },
                        onTimeSelected = { h, m ->
                            hour = h
                            minute = m
                            showAlarmClockPicker = false
                        }
                    )
                }

                Text("Repeat Interval (Hourly, Every 2h, Custom)", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AlertMetricsCalculator.intervalPresetsMinutes.forEach { presetMins ->
                        FilterChip(
                            selected = !showCustomIntervalInput && repeatIntervalMinutes == presetMins,
                            onClick = {
                                showCustomIntervalInput = false
                                repeatIntervalMinutes = presetMins
                            },
                            label = { Text(AlertMetricsCalculator.formatIntervalShort(presetMins)) },
                            modifier = Modifier.testTag("alarm_interval_preset_$presetMins")
                        )
                    }
                    FilterChip(
                        selected = showCustomIntervalInput,
                        onClick = { showCustomIntervalInput = true },
                        label = { Text("Custom") },
                        modifier = Modifier.testTag("alarm_interval_custom_chip")
                    )
                }

                if (showCustomIntervalInput) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = customIntervalInputText,
                            onValueChange = { raw ->
                                val digits = raw.filter { it.isDigit() }.take(4)
                                customIntervalInputText = digits
                                val parsed = digits.toIntOrNull()
                                if (parsed != null && parsed > 0) {
                                    val mins = if (customUnitHours) parsed * 60 else parsed
                                    repeatIntervalMinutes = AlertMetricsCalculator.normalizeIntervalMinutes(mins)
                                }
                            },
                            label = { Text(if (customUnitHours) "Every N hours" else "Every N mins") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("alarm_custom_interval_input")
                        )
                        FilterChip(
                            selected = !customUnitHours,
                            onClick = {
                                customUnitHours = false
                                val parsed = customIntervalInputText.toIntOrNull()
                                if (parsed != null && parsed > 0) {
                                    repeatIntervalMinutes = AlertMetricsCalculator.normalizeIntervalMinutes(parsed)
                                }
                            },
                            label = { Text("Min") }
                        )
                        FilterChip(
                            selected = customUnitHours,
                            onClick = {
                                customUnitHours = true
                                val parsed = customIntervalInputText.toIntOrNull()
                                if (parsed != null && parsed > 0) {
                                    repeatIntervalMinutes = AlertMetricsCalculator.normalizeIntervalMinutes(parsed * 60)
                                }
                            },
                            label = { Text("Hours") }
                        )
                    }
                    Text(
                        text = "Active cadence: ${AlertMetricsCalculator.formatIntervalLabel(repeatIntervalMinutes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Text("Repeat Days", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    dayLabels.forEachIndexed { idx, label ->
                        val bit = 1 shl idx
                        val selected = (daysMask and bit) != 0
                        FilterChip(
                            selected = selected,
                            onClick = {
                                val next = daysMask xor bit
                                if (next != 0) daysMask = next
                            },
                            label = { Text(label) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                Text("Alarm Sound (Tap Play to Preview)", style = MaterialTheme.typography.labelLarge)
                BundledAlertTone.entries.forEach { tone ->
                    val selected = soundKey == tone.id
                    val isPlaying = playingToneKey == "bundled_${tone.id}"
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(
                                if (selected) {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
                                }
                            )
                            .clickable {
                                soundKey = tone.id
                                customUri = null
                                customLabel = null
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = if (selected) Icons.Rounded.CheckCircle else Icons.Rounded.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(text = tone.displayName, style = MaterialTheme.typography.bodyMedium)
                        }
                        IconButton(
                            onClick = {
                                if (isPlaying) onStopPreview() else onPreviewBundledTone(tone)
                            }
                        ) {
                            Icon(
                                imageVector = if (isPlaying) Icons.Rounded.Stop else Icons.Rounded.PlayArrow,
                                contentDescription = "Preview ${tone.displayName}"
                            )
                        }
                    }
                }

                OutlinedButton(
                    onClick = {
                        onLaunchAudioPicker { pickedUri ->
                            soundKey = "custom_uri"
                            customUri = pickedUri.toString()
                            customLabel = resolveAudioDisplayName(pickedUri)
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Rounded.AudioFile, contentDescription = null)
                    Spacer(modifier = Modifier.size(6.dp))
                    Text(
                        if (soundKey == "custom_uri" && !customLabel.isNullOrBlank()) {
                            "Local File: $customLabel"
                        } else {
                            "Choose Audio from Local Storage"
                        }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val resolvedInterval = if (showCustomIntervalInput) {
                        val parsed = customIntervalInputText.toIntOrNull() ?: 60
                        val rawMins = if (customUnitHours) parsed * 60 else parsed
                        AlertMetricsCalculator.normalizeIntervalMinutes(rawMins)
                    } else {
                        AlertMetricsCalculator.normalizeIntervalMinutes(repeatIntervalMinutes)
                    }
                    onSave(
                        title,
                        moduleKey,
                        hour,
                        minute,
                        daysMask,
                        soundKey,
                        customUri,
                        customLabel,
                        alarmEmoji,
                        customMessage,
                        gesturePreset,
                        resolvedInterval
                    )
                },
                modifier = Modifier.testTag("save_custom_alarm_button")
            ) {
                Text("Save Alarm")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

private fun formatDaysMask(mask: Int): String {
    if (mask == 127) return "Every day"
    if (mask == 62) return "Mon–Fri"
    if (mask == 65) return "Weekends"
    val labels = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    val active = labels.filterIndexed { idx, _ -> (mask and (1 shl idx)) != 0 }
    return if (active.isEmpty()) "Every day" else active.joinToString(", ")
}
