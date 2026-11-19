package com.example.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.DailyRoutineEntity
import com.example.domain.DateEpochUtils
import com.example.domain.RoutineMoodAnalyticsEngine
import com.example.domain.RoutineTimeSlot
import com.example.notifications.BundledAlertTone
import com.example.ui.components.ClockTimePickerDialog
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineEditorDialog(
    editingRoutine: DailyRoutineEntity?,
    onDismiss: () -> Unit,
    onSave: (
        existingId: Long,
        title: String,
        emoji: String,
        timeSlot: RoutineTimeSlot,
        triggerTime24h: String,
        estimatedMinutes: Int,
        steps: List<String>,
        reminderEnabled: Boolean,
        reminderSoundKey: String,
        repeatIntervalMinutes: Int
    ) -> Unit
) {
    val initialSlot = editingRoutine?.let { RoutineTimeSlot.fromLabel(it.timeSlot) } ?: RoutineTimeSlot.MORNING
    val initialSteps = editingRoutine?.let {
        RoutineMoodAnalyticsEngine.parseSteps(it.stepsSerialized).joinToString("\n")
    } ?: "Drink 250 ml water\nTake 3 calm breaths"

    var title by remember { mutableStateOf(editingRoutine?.title.orEmpty()) }
    var selectedEmoji by remember { mutableStateOf(editingRoutine?.emoji ?: "🌿") }
    var selectedSlot by remember { mutableStateOf(initialSlot) }
    var time24h by remember { mutableStateOf(editingRoutine?.triggerTime24h ?: initialSlot.defaultTime24h) }
    var durationMinutes by remember { mutableIntStateOf(editingRoutine?.estimatedMinutes ?: 10) }
    var stepsMultiline by remember { mutableStateOf(initialSteps) }
    var reminderEnabled by remember { mutableStateOf(editingRoutine?.reminderEnabled ?: false) }
    var selectedSoundKey by remember { mutableStateOf(BundledAlertTone.WELLNESS_GENTLE.id) }
    var repeatMinutes by remember { mutableIntStateOf(1440) }
    var showTimePicker by remember { mutableStateOf(false) }

    val emojiChoices = listOf("🌿", "🌅", "💧", "🧘", "📓", "🌙", "🌸", "🚶", "🍵")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (editingRoutine == null) "Build Custom Routine" else "Customize Routine")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Routine Title") },
                    placeholder = { Text("e.g. Morning Sunlight & Water") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_title_input")
                )

                Text("Icon", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    emojiChoices.forEach { emoji ->
                        FilterChip(
                            selected = selectedEmoji == emoji,
                            onClick = { selectedEmoji = emoji },
                            label = { Text(emoji) }
                        )
                    }
                }

                Text("Time of Day", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    RoutineTimeSlot.entries.forEach { slot ->
                        FilterChip(
                            selected = selectedSlot == slot,
                            onClick = {
                                selectedSlot = slot
                                if (editingRoutine == null) {
                                    time24h = slot.defaultTime24h
                                }
                            },
                            label = { Text("${slot.emoji} ${slot.label}") }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Preferred Time: $time24h", style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(onClick = { showTimePicker = true }) {
                        Text("Pick Clock")
                    }
                }

                Text("Estimated Duration: $durationMinutes min", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(5, 10, 15, 20, 30).forEach { mins ->
                        FilterChip(
                            selected = durationMinutes == mins,
                            onClick = { durationMinutes = mins },
                            label = { Text("${mins}m") }
                        )
                    }
                }

                OutlinedTextField(
                    value = stepsMultiline,
                    onValueChange = { stepsMultiline = it },
                    label = { Text("Checklist Steps (one per line)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("routine_steps_input")
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Gentle Optional Reminder", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "Opt-in only — pause or silence anytime",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = reminderEnabled,
                        onCheckedChange = { reminderEnabled = it },
                        modifier = Modifier.testTag("routine_dialog_reminder_switch")
                    )
                }

                if (reminderEnabled) {
                    Text("Alert Tone", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        BundledAlertTone.entries.take(4).forEach { tone ->
                            FilterChip(
                                selected = selectedSoundKey == tone.id,
                                onClick = { selectedSoundKey = tone.id },
                                label = { Text(tone.displayName) }
                            )
                        }
                    }

                    Text("Cadence", style = MaterialTheme.typography.labelMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            1440 to "Daily",
                            120 to "Every 2h",
                            240 to "Every 4h"
                        ).forEach { (mins, label) ->
                            FilterChip(
                                selected = repeatMinutes == mins,
                                onClick = { repeatMinutes = mins },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedSteps = stepsMultiline.lines()
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                    onSave(
                        editingRoutine?.id ?: 0L,
                        title.trim().ifEmpty { "${selectedSlot.label} Routine" },
                        selectedEmoji,
                        selectedSlot,
                        time24h,
                        durationMinutes,
                        parsedSteps,
                        reminderEnabled,
                        selectedSoundKey,
                        repeatMinutes
                    )
                },
                modifier = Modifier.testTag("save_routine_dialog_button")
            ) {
                Text("Save Routine")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )

    if (showTimePicker) {
        val (h, m) = DateEpochUtils.parseHourMinute(time24h)
        ClockTimePickerDialog(
            initialHour = h,
            initialMinute = m,
            title = "Routine Reminder Time",
            onDismiss = { showTimePicker = false },
            onTimeSelected = { hour24, minute ->
                time24h = String.format(Locale.US, "%02d:%02d", hour24, minute)
                showTimePicker = false
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodReminderConfigDialog(
    initialEnabled: Boolean,
    initialTime24h: String,
    initialSoundKey: String,
    onDismiss: () -> Unit,
    onSave: (enabled: Boolean, time24h: String, soundKey: String) -> Unit
) {
    var enabled by remember { mutableStateOf(initialEnabled) }
    var time24h by remember { mutableStateOf(initialTime24h.ifBlank { "20:30" }) }
    var soundKey by remember { mutableStateOf(initialSoundKey.ifBlank { BundledAlertTone.SINGING_BOWL.id }) }
    var showClock by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Mood Reflection Reminder") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Set a quiet daily nudge if you enjoy evening journaling, or leave it off completely.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Enable Reflection Nudge", style = MaterialTheme.typography.titleSmall)
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
                if (enabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Check-in Time: $time24h", style = MaterialTheme.typography.bodyMedium)
                        OutlinedButton(onClick = { showClock = true }) { Text("Change") }
                    }
                    Text("Gentle Sound", style = MaterialTheme.typography.labelMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            BundledAlertTone.SINGING_BOWL,
                            BundledAlertTone.WELLNESS_GENTLE,
                            BundledAlertTone.MORNING_DEW
                        ).forEach { tone ->
                            FilterChip(
                                selected = soundKey == tone.id,
                                onClick = { soundKey = tone.id },
                                label = { Text(tone.displayName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(enabled, time24h, soundKey) },
                modifier = Modifier.testTag("save_mood_reminder_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )

    if (showClock) {
        val (h, m) = DateEpochUtils.parseHourMinute(time24h)
        ClockTimePickerDialog(
            initialHour = h,
            initialMinute = m,
            title = "Mood Check-In Time",
            onDismiss = { showClock = false },
            onTimeSelected = { hour24, minute ->
                time24h = String.format(Locale.US, "%02d:%02d", hour24, minute)
                showClock = false
            }
        )
    }
}
