package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Keyboard
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.DateEpochUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarDatePickerDialog(
    initialEpochDay: Long,
    onDismiss: () -> Unit,
    onDateSelected: (Long) -> Unit
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = DateEpochUtils.epochDayToUtcPickerMillis(initialEpochDay)
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(
                onClick = {
                    val chosen = datePickerState.selectedDateMillis?.let {
                        DateEpochUtils.utcPickerMillisToEpochDay(it)
                    } ?: initialEpochDay
                    onDateSelected(chosen)
                }
            ) {
                Text("Select Date")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    ) {
        DatePicker(state = datePickerState)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockTimePickerDialog(
    initialHour: Int,
    initialMinute: Int,
    title: String = "Select Time",
    onDismiss: () -> Unit,
    onTimeSelected: (hour24: Int, minute: Int) -> Unit
) {
    var draftHour by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableIntStateOf(initialHour.coerceIn(0, 23))
    }
    var draftMinute by androidx.compose.runtime.remember {
        androidx.compose.runtime.mutableIntStateOf(initialMinute.coerceIn(0, 59))
    }
    var is24HourMode by remember { mutableStateOf(true) }
    var useKeyboardInput by remember { mutableStateOf(false) }

    androidx.compose.runtime.key(is24HourMode) {
        val timePickerState = rememberTimePickerState(
            initialHour = draftHour,
            initialMinute = draftMinute,
            is24Hour = is24HourMode
        )

        androidx.compose.runtime.LaunchedEffect(timePickerState.hour, timePickerState.minute) {
            draftHour = timePickerState.hour
            draftMinute = timePickerState.minute
        }

        AlertDialog(
            onDismissRequest = onDismiss,
            title = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(title, style = MaterialTheme.typography.titleMedium)
                        IconButton(
                            onClick = { useKeyboardInput = !useKeyboardInput },
                            modifier = Modifier.testTag("clock_input_mode_toggle")
                        ) {
                            Icon(
                                imageVector = if (useKeyboardInput) Icons.Rounded.Schedule else Icons.Rounded.Keyboard,
                                contentDescription = if (useKeyboardInput) "Switch to clock dial" else "Type time manually"
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            androidx.compose.material3.FilterChip(
                                selected = is24HourMode,
                                onClick = { is24HourMode = true },
                                label = { Text("24h (14:55)") },
                                modifier = Modifier.testTag("clock_mode_24h")
                            )
                            androidx.compose.material3.FilterChip(
                                selected = !is24HourMode,
                                onClick = { is24HourMode = false },
                                label = { Text("12h AM/PM") },
                                modifier = Modifier.testTag("clock_mode_12h")
                            )
                        }
                    }
                    Text(
                        text = "${DateEpochUtils.formatTime24h(timePickerState.hour, timePickerState.minute)} (${DateEpochUtils.formatTime12h(timePickerState.hour, timePickerState.minute)})",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    if (useKeyboardInput) {
                        TimeInput(state = timePickerState)
                    } else {
                        TimePicker(state = timePickerState)
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onTimeSelected(timePickerState.hour, timePickerState.minute)
                    },
                    modifier = Modifier.testTag("clock_confirm_button")
                ) {
                    Text("Set Time")
                }
            },
            dismissButton = {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun DateTimeSelectorRow(
    selectedEpochDay: Long,
    selectedHour: Int,
    selectedMinute: Int,
    onEpochDayChanged: (Long) -> Unit,
    onTimeChanged: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    dateLabel: String = "Date",
    timeLabel: String = "Time",
    showDate: Boolean = true,
    showTime: Boolean = true,
    testTagPrefix: String = "dt_picker"
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        if (showDate) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showDatePicker = true }
                    .testTag("${testTagPrefix}_date_button"),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CalendarMonth,
                        contentDescription = "Choose $dateLabel",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = dateLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = DateEpochUtils.formatEpochDayShort(selectedEpochDay),
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }

        if (showTime) {
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .clickable { showTimePicker = true }
                    .testTag("${testTagPrefix}_time_button"),
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Schedule,
                        contentDescription = "Choose $timeLabel",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = timeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${DateEpochUtils.formatTime24h(selectedHour, selectedMinute)} (${DateEpochUtils.formatTime12h(selectedHour, selectedMinute)})",
                            style = MaterialTheme.typography.labelLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        CalendarDatePickerDialog(
            initialEpochDay = selectedEpochDay,
            onDismiss = { showDatePicker = false },
            onDateSelected = { day ->
                onEpochDayChanged(day)
                showDatePicker = false
            }
        )
    }

    if (showTimePicker) {
        ClockTimePickerDialog(
            initialHour = selectedHour,
            initialMinute = selectedMinute,
            title = "Select $timeLabel",
            onDismiss = { showTimePicker = false },
            onTimeSelected = { h, m ->
                onTimeChanged(h, m)
                showTimePicker = false
            }
        )
    }
}
