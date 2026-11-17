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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.domain.DateEpochUtils
import com.example.ui.components.DateTimeSelectorRow
import com.example.ui.components.QuickAmountChip
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.ModulesHubUiState
import com.example.ui.viewmodel.ModulesHubViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeautyCareScreen(
    uiState: ModulesHubUiState,
    viewModel: ModulesHubViewModel,
    onBack: () -> Unit
) {
    var customStep by remember { mutableStateOf("") }
    var routineType by remember { mutableStateOf("Skincare") }
    var selectedDay by remember { mutableLongStateOf(DateEpochUtils.todayEpochDay()) }
    val initHm = remember { DateEpochUtils.epochMsToHourMinute() }
    var selectedHour by remember { mutableIntStateOf(initHm.first) }
    var selectedMinute by remember { mutableIntStateOf(initHm.second) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Column {
                    Text("✨ Beauty & Self-Care", style = MaterialTheme.typography.headlineSmall)
                    Text("Daily glow rituals, skincare & haircare", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("🌸 One-Tap Ritual Check-In", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAmountChip("🧼 Cleanser") { viewModel.logBeautyCare("Morning", "🧼 Gentle Cleanser", "") }
                        QuickAmountChip("☀️ SPF 50") { viewModel.logBeautyCare("Morning", "☀️ Sunscreen SPF 50", "") }
                        QuickAmountChip("💆 Hair Oil") { viewModel.logBeautyCare("Haircare", "💆 Nourishing Hair Oil", "") }
                        QuickAmountChip("🌙 Night Cream") { viewModel.logBeautyCare("Evening", "🌙 Hydrating Night Serum", "") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Skincare", "Haircare", "Evening").forEach { t ->
                            FilterChip(selected = routineType == t, onClick = { routineType = t }, label = { Text(t) })
                        }
                    }
                    DateTimeSelectorRow(
                        selectedEpochDay = selectedDay,
                        selectedHour = selectedHour,
                        selectedMinute = selectedMinute,
                        onEpochDayChanged = { selectedDay = it },
                        onTimeChanged = { h, m ->
                            selectedHour = h
                            selectedMinute = m
                        },
                        dateLabel = "Ritual Date",
                        timeLabel = "Ritual Time",
                        testTagPrefix = "beauty_dt"
                    )
                    OutlinedTextField(
                        value = customStep,
                        onValueChange = { customStep = it },
                        label = { Text("Custom ritual or product") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (customStep.isNotBlank()) {
                                val ts = DateEpochUtils.combineEpochDayAndTime(selectedDay, selectedHour, selectedMinute)
                                viewModel.logBeautyCare(routineType, customStep, "", ts)
                                customStep = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Log Self-Care Step ✨") }
                }
            }
        }

        items(uiState.beautyRecords, key = { it.id }) { item ->
            Surface(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.productOrStep, style = MaterialTheme.typography.titleSmall)
                        Text("${item.routineType} • ${DateEpochUtils.formatEpochDayAndTime(item.completedAtEpochMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.deleteBeautyCare(item.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ritual")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
fun DigitalWellbeingScreen(
    uiState: ModulesHubUiState,
    viewModel: ModulesHubViewModel,
    onBack: () -> Unit
) {
    val current = uiState.todayWellbeing
    val screenMins = current?.screenTimeMinutes ?: 0
    val goalMins = current?.mindfulGoalMinutes ?: 120
    val focusCount = current?.focusSessionsCount ?: 0
    val ratio = (screenMins.toFloat() / goalMins.coerceAtLeast(30).toFloat()).coerceIn(0f, 1f)

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back") }
                Column {
                    Text("📵 Digital Wellbeing", style = MaterialTheme.typography.headlineSmall)
                    Text("Mindful screen balance & deep focus blocks", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        item {
            Card(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("📱 Mindful Screen Budget", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$screenMins / $goalMins min",
                            style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoMetricFont),
                            color = if (screenMins > goalMins) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ratio },
                        modifier = Modifier.fillMaxWidth().height(10.dp).clip(CircleShape)
                    )
                    Text("🎯 Focus Sessions Completed Today: $focusCount", style = MaterialTheme.typography.titleSmall)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAmountChip("+15m 📱", MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) {
                            viewModel.updateWellbeing(addScreenMin = 15)
                        }
                        QuickAmountChip("+30m 📱", MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) {
                            viewModel.updateWellbeing(addScreenMin = 30)
                        }
                        QuickAmountChip("+1 Focus 🎯", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                            viewModel.updateWellbeing(addFocus = 1)
                        }
                    }
                    Text("Daily Screen Cap: $goalMins min", style = MaterialTheme.typography.bodySmall)
                    Slider(
                        value = goalMins.toFloat(),
                        onValueChange = { viewModel.updateWellbeing(newGoalMin = (it / 15f).roundToInt() * 15) },
                        valueRange = 60f..360f
                    )
                }
            }
        }
    }
}
