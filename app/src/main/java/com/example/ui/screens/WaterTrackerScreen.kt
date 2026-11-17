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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.DateEpochUtils
import com.example.ui.components.DateTimeSelectorRow
import com.example.ui.components.HydrationRingGauge
import com.example.ui.components.QuickAmountChip
import com.example.ui.components.WeeklyHydrationBarChart
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.WaterUiState
import com.example.ui.viewmodel.WaterViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun WaterTrackerScreen(
    uiState: WaterUiState,
    activeToneLabel: String,
    viewModel: WaterViewModel,
    onOpenAlertSettings: () -> Unit
) {
    var showCustomDialog by remember { mutableStateOf(false) }
    var showGoalDialog by remember { mutableStateOf(false) }
    val drinkTypes = listOf("Water", "Herbal Tea", "Coconut Water", "Infused")

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.widthIn(max = 600.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(id = R.string.water_screen_title),
                            style = MaterialTheme.typography.headlineMedium
                        )
                        Text(
                            text = "🔔 Alert tone: $activeToneLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = onOpenAlertSettings,
                            modifier = Modifier.testTag("water_alert_tone_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = "Customize water alert tone"
                            )
                        }
                        IconButton(
                            onClick = { showGoalDialog = true },
                            modifier = Modifier.testTag("water_edit_goal_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Adjust daily water goal"
                            )
                        }
                    }
                }
            }
        }

        // Main Gauge & Quick Logging Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(26.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    HydrationRingGauge(
                        currentMl = uiState.summary.totalTodayMl,
                        goalMl = uiState.summary.goalMl,
                        progressFraction = uiState.summary.progressFraction
                    )

                    val milestoneBadge = when {
                        uiState.summary.remainingMl == 0 -> "🐳 Goal Crushed! 🎉"
                        uiState.summary.progressFraction >= 0.5f -> "🌊 Halfway Flow!"
                        else -> "🌱 Getting Started"
                    }
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = milestoneBadge,
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }

                    val statusLine = if (uiState.summary.remainingMl == 0) {
                        "Daily hydration target reached! Keep glowing ✨"
                    } else {
                        "${uiState.summary.remainingMl} ml remaining for today's goal 💦"
                    }
                    Text(
                        text = statusLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    val drinkEmojiMap = mapOf(
                        "Water" to "💧 Water",
                        "Herbal Tea" to "🍵 Herbal Tea",
                        "Coconut Water" to "🥥 Coconut Water",
                        "Infused" to "🍋 Infused"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        drinkTypes.forEach { drink ->
                            FilterChip(
                                selected = uiState.selectedDrinkType == drink,
                                onClick = { viewModel.selectDrinkType(drink) },
                                label = { Text(drinkEmojiMap[drink] ?: drink) },
                                modifier = Modifier.testTag("drink_chip_${drink.lowercase().replace(" ", "_")}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickAmountChip(
                            label = "💧 +150 ml",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("water_add_150"),
                            onClick = { viewModel.logWater(150) }
                        )
                        QuickAmountChip(
                            label = "🥤 +250 ml",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("water_add_250"),
                            onClick = { viewModel.logWater(250) }
                        )
                        QuickAmountChip(
                            label = "🫗 +500 ml",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("water_add_500"),
                            onClick = { viewModel.logWater(500) }
                        )
                    }

                    OutlinedButton(
                        onClick = { showCustomDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("water_add_custom_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                        Spacer(modifier = Modifier.size(6.dp))
                        Text("Log Custom Amount")
                    }
                }
            }
        }

        // 7-Day Hydration Chart
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
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📊 7-Day Intake Trend",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "🎯 Goal: ${uiState.summary.goalMl} ml",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    WeeklyHydrationBarChart(
                        bars = uiState.summary.weeklyBars,
                        goalMl = uiState.summary.goalMl
                    )
                }
            }
        }

        // Today's Intake History Header
        item {
            Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                Text(
                    text = "📝 Today's Entries (${uiState.todayRecords.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (uiState.todayRecords.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.WaterDrop,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(32.dp)
                        )
                        Text(
                            text = "No hydration logged today yet",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "Tap any quick amount above to persist your first drink locally.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            items(uiState.todayRecords, key = { it.id }) { record ->
                Surface(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.WaterDrop,
                                contentDescription = record.drinkType,
                                tint = MaterialTheme.colorScheme.secondary
                            )
                            Column {
                                Text(
                                    text = "${record.amountMl} ml • ${record.drinkType}",
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontFamily = JetBrainsMonoMetricFont
                                    )
                                )
                                val subtitle = buildString {
                                    append(DateEpochUtils.formatEpochDayAndTime(record.loggedAtEpochMs))
                                    if (record.note.isNotBlank()) append(" • ${record.note}")
                                }
                                Text(
                                    text = subtitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        IconButton(
                            onClick = { viewModel.deleteRecord(record.id) },
                            modifier = Modifier.testTag("delete_water_${record.id}")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = "Delete hydration entry"
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    if (showCustomDialog) {
        var amountText by remember { mutableStateOf("350") }
        var noteText by remember { mutableStateOf("") }
        var selectedDay by remember { mutableLongStateOf(DateEpochUtils.todayEpochDay()) }
        val initHm = remember { DateEpochUtils.epochMsToHourMinute() }
        var selectedHour by remember { mutableIntStateOf(initHm.first) }
        var selectedMinute by remember { mutableIntStateOf(initHm.second) }
        AlertDialog(
            onDismissRequest = { showCustomDialog = false },
            title = { Text("Log ${uiState.selectedDrinkType}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DateTimeSelectorRow(
                        selectedEpochDay = selectedDay,
                        selectedHour = selectedHour,
                        selectedMinute = selectedMinute,
                        onEpochDayChanged = { selectedDay = it },
                        onTimeChanged = { h, m ->
                            selectedHour = h
                            selectedMinute = m
                        },
                        dateLabel = "Drink Date",
                        timeLabel = "Drink Time",
                        testTagPrefix = "water_dt"
                    )
                    OutlinedTextField(
                        value = amountText,
                        onValueChange = { if (it.all(Char::isDigit) && it.length <= 4) amountText = it },
                        label = { Text("Volume (ml)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.testTag("custom_water_amount_input")
                    )
                    OutlinedTextField(
                        value = noteText,
                        onValueChange = { noteText = it },
                        label = { Text("Optional note (e.g., After walk)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val ml = amountText.toIntOrNull()?.takeIf { it in 25..3000 } ?: 250
                        val ts = DateEpochUtils.combineEpochDayAndTime(selectedDay, selectedHour, selectedMinute)
                        viewModel.logWater(ml, noteText, ts)
                        showCustomDialog = false
                    },
                    modifier = Modifier.testTag("confirm_custom_water_button")
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showGoalDialog) {
        var draftGoal by remember { mutableIntStateOf(uiState.summary.goalMl) }
        AlertDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Daily Water Target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "$draftGoal ml / day",
                        style = MaterialTheme.typography.headlineSmall.copy(
                            fontFamily = JetBrainsMonoMetricFont
                        ),
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Slider(
                        value = draftGoal.toFloat(),
                        onValueChange = { draftGoal = (it / 50f).roundToInt() * 50 },
                        valueRange = 1200f..4500f
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.updateDailyGoal(draftGoal)
                        showGoalDialog = false
                    }
                ) { Text("Update Goal") }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text("Cancel") }
            }
        )
    }
}
