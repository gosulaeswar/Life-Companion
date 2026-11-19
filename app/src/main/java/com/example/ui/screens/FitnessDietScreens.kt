package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.domain.DateEpochUtils
import com.example.ui.components.DateTimeSelectorRow
import com.example.ui.components.QuickAmountChip
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.ModulesHubUiState
import com.example.ui.viewmodel.ModulesHubViewModel
import kotlin.math.roundToInt

@Composable
fun FitnessYogaScreen(
    uiState: ModulesHubUiState,
    viewModel: ModulesHubViewModel,
    onBack: () -> Unit,
    onOpenConnectedDevices: () -> Unit = {}
) {
    var showWorkoutDialog by remember { mutableStateOf(false) }
    var showSleepDialog by remember { mutableStateOf(false) }

    val steps = uiState.todaySteps?.stepCount ?: 0
    val stepGoal = 8000
    val stepProgress = (steps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text("🏃 Fitness, Yoga & Sleep", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Move joyfully, flow mindfully, and rest deeply",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Connected Fitness Wearables & Apps Shortcut
        item {
            Surface(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("⌚ Sync Fitbit, Garmin, Samsung & Apple Health", style = MaterialTheme.typography.titleSmall)
                        Text(
                            if (uiState.connectedSources.isNotEmpty()) {
                                "${uiState.connectedSources.size} connected source(s) syncing steps & HR"
                            } else {
                                "Import live steps, heart rate, sleep & workouts"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(onClick = onOpenConnectedDevices) {
                        Text("Connect")
                    }
                }
            }
        }

        // Steps Counter Card
        item {
            Card(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("👟 Today's Steps", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$steps / $stepGoal",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoMetricFont),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { stepProgress },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickAmountChip("+500 👟", modifier = Modifier.weight(1f)) { viewModel.addSteps(500) }
                        QuickAmountChip("+1,500 🚶", modifier = Modifier.weight(1f)) { viewModel.addSteps(1500) }
                        QuickAmountChip("+3,000 🏃", modifier = Modifier.weight(1f)) { viewModel.addSteps(3000) }
                    }
                }
            }
        }

        // Quick Exercise & Yoga Actions
        item {
            Card(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("🧘 Quick Activity Log", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickAmountChip("🧘 20m Yoga", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                            viewModel.logExercise("🧘 Yoga Flow", "Yoga", 20, "Calm", 75)
                        }
                        QuickAmountChip("🏃 30m Run", MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) {
                            viewModel.logExercise("🏃 Jogging", "Exercise", 30, "Moderate", 240)
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickAmountChip("🌬️ 10m Breath", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                            viewModel.logExercise("🌬️ Pranayama", "Yoga", 10, "Gentle", 25)
                        }
                        OutlinedButton(
                            onClick = { showWorkoutDialog = true },
                            modifier = Modifier.weight(1f).testTag("fitness_custom_button")
                        ) {
                            Icon(Icons.Rounded.Add, contentDescription = null)
                            Text("Custom")
                        }
                    }
                }
            }
        }

        // Sleep Card
        item {
            Card(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("😴 Sleep Rest Log", style = MaterialTheme.typography.titleMedium)
                        Button(onClick = { showSleepDialog = true }) { Text("Log Sleep") }
                    }
                    if (uiState.sleepRecords.isEmpty()) {
                        Text(
                            "Log your rest hours & morning energy rating.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        uiState.sleepRecords.take(3).forEach { s ->
                            val hrs = (s.wakeEpochMs - s.bedtimeEpochMs) / 3_600_000f
                            val stars = "⭐".repeat(s.qualityRating)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    String.format("🌙 %.1f hrs • %s (%s) %s", hrs, stars, DateEpochUtils.formatEpochDayAndTime(s.wakeEpochMs), s.notes),
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                IconButton(onClick = { viewModel.deleteSleep(s.id) }) {
                                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete sleep")
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                Text("🏅 Activity History (${uiState.exercises.size})", style = MaterialTheme.typography.titleMedium)
            }
        }

        items(uiState.exercises, key = { it.id }) { ex ->
            Surface(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${ex.activityType} • ${ex.durationMinutes} min", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${ex.category} • ${ex.intensity} • ${ex.caloriesBurned} kcal • ${DateEpochUtils.formatEpochDayAndTime(ex.loggedAtEpochMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.deleteExercise(ex.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete workout")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    if (showWorkoutDialog) {
        var activity by remember { mutableStateOf("🚴 Cycling") }
        var category by remember { mutableStateOf("Exercise") }
        var mins by remember { mutableStateOf("30") }
        var cals by remember { mutableStateOf("210") }
        var selectedDay by remember { mutableLongStateOf(DateEpochUtils.todayEpochDay()) }
        val initHm = remember { DateEpochUtils.epochMsToHourMinute() }
        var selectedHour by remember { mutableIntStateOf(initHm.first) }
        var selectedMinute by remember { mutableIntStateOf(initHm.second) }
        AlertDialog(
            onDismissRequest = { showWorkoutDialog = false },
            title = { Text("Log Movement or Yoga") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateTimeSelectorRow(
                        selectedEpochDay = selectedDay,
                        selectedHour = selectedHour,
                        selectedMinute = selectedMinute,
                        onEpochDayChanged = { selectedDay = it },
                        onTimeChanged = { h, m ->
                            selectedHour = h
                            selectedMinute = m
                        },
                        dateLabel = "Date",
                        timeLabel = "Time",
                        testTagPrefix = "fitness_dt"
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = category == "Exercise", onClick = { category = "Exercise" }, label = { Text("🏃 Exercise") })
                        FilterChip(selected = category == "Yoga", onClick = { category = "Yoga" }, label = { Text("🧘 Yoga") })
                    }
                    OutlinedTextField(value = activity, onValueChange = { activity = it }, label = { Text("Activity") }, singleLine = true)
                    OutlinedTextField(
                        value = mins,
                        onValueChange = { if (it.all(Char::isDigit)) mins = it },
                        label = { Text("Minutes") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = cals,
                        onValueChange = { if (it.all(Char::isDigit)) cals = it },
                        label = { Text("Calories (approx)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val ts = DateEpochUtils.combineEpochDayAndTime(selectedDay, selectedHour, selectedMinute)
                    viewModel.logExercise(
                        activityType = activity.ifBlank { "🏃 Workout" },
                        category = category,
                        durationMinutes = mins.toIntOrNull() ?: 25,
                        intensity = "Moderate",
                        caloriesBurned = cals.toIntOrNull() ?: 120,
                        loggedAtEpochMs = ts
                    )
                    showWorkoutDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showWorkoutDialog = false }) { Text("Cancel") } }
        )
    }

    if (showSleepDialog) {
        var hours by remember { mutableFloatStateOf(7.5f) }
        var quality by remember { mutableIntStateOf(4) }
        var notes by remember { mutableStateOf("") }
        var wakeDay by remember { mutableLongStateOf(DateEpochUtils.todayEpochDay()) }
        val initHm = remember { DateEpochUtils.epochMsToHourMinute() }
        var wakeHour by remember { mutableIntStateOf(initHm.first) }
        var wakeMinute by remember { mutableIntStateOf(initHm.second) }
        AlertDialog(
            onDismissRequest = { showSleepDialog = false },
            title = { Text("😴 Log Sleep") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateTimeSelectorRow(
                        selectedEpochDay = wakeDay,
                        selectedHour = wakeHour,
                        selectedMinute = wakeMinute,
                        onEpochDayChanged = { wakeDay = it },
                        onTimeChanged = { h, m ->
                            wakeHour = h
                            wakeMinute = m
                        },
                        dateLabel = "Wake Date",
                        timeLabel = "Wake Time",
                        testTagPrefix = "sleep_dt"
                    )
                    Text(String.format("Duration: %.1f hours", hours), style = MaterialTheme.typography.titleSmall)
                    Slider(value = hours, onValueChange = { hours = (it * 2).roundToInt() / 2f }, valueRange = 3f..12f)
                    Text("Quality: ${"⭐".repeat(quality)}", style = MaterialTheme.typography.titleSmall)
                    Slider(value = quality.toFloat(), onValueChange = { quality = it.roundToInt() }, valueRange = 1f..5f)
                    OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Note (optional)") }, singleLine = true)
                }
            },
            confirmButton = {
                Button(onClick = {
                    val wakeMs = DateEpochUtils.combineEpochDayAndTime(wakeDay, wakeHour, wakeMinute)
                    viewModel.logSleep(hours, quality, notes, wakeMs)
                    showSleepDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showSleepDialog = false }) { Text("Cancel") } }
        )
    }
}
