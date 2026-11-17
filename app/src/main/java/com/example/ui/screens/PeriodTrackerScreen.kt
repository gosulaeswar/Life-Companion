package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.PeriodRecordEntity
import com.example.domain.DateEpochUtils
import com.example.ui.components.ModuleStatusPill
import com.example.ui.theme.FertileLeafGreen
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.PeriodUiState
import com.example.ui.viewmodel.PeriodViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PeriodTrackerScreen(
    uiState: PeriodUiState,
    activeToneLabel: String,
    viewModel: PeriodViewModel,
    onOpenAlertSettings: () -> Unit
) {
    var editingRecord by remember { mutableStateOf<PeriodRecordEntity?>(null) }
    var showLogDialog by remember { mutableStateOf(false) }
    var prefilledStartEpochDay by remember { mutableLongStateOf(DateEpochUtils.todayEpochDay()) }

    val todayEpochDay = remember { DateEpochUtils.todayEpochDay() }

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
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "🌸 Cycle & Period Care",
                                style = MaterialTheme.typography.headlineMedium
                            )
                        }
                        Text(
                            text = "🔔 Alert tone: $activeToneLabel",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.tertiary
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = onOpenAlertSettings,
                            modifier = Modifier.testTag("period_alert_tone_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.NotificationsActive,
                                contentDescription = "Customize period alert tone"
                            )
                        }
                        ModuleStatusPill(
                            isAlwaysFree = true,
                            isActivated = true,
                            isPhaseOneBuiltIn = true
                        )
                    }
                }
            }
        }

        if (!uiState.cycleTrackingEnabled) {
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(22.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Favorite,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(36.dp)
                        )
                        Text(
                            text = "Cycle Tracking Is Currently Hidden",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "LifeWeave adapts to your personal preferences. Period care is always free whenever you choose to enable it.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                        Button(
                            onClick = { viewModel.setCycleTrackingEnabled(true) },
                            modifier = Modifier.testTag("period_enable_button")
                        ) {
                            Text("Enable Period Tracker")
                        }
                    }
                }
            }
        } else {
            // Cycle Insights Card
            item {
                val insights = uiState.insights
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
                            val phaseEmoji = when {
                                insights.currentPhase.label.contains("Menstrual", ignoreCase = true) -> "🌺"
                                insights.currentPhase.label.contains("Follicular", ignoreCase = true) -> "🌱"
                                insights.currentPhase.label.contains("Ovul", ignoreCase = true) -> "✨"
                                else -> "🌙"
                            }
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "$phaseEmoji ${insights.currentPhase.label}",
                                    style = MaterialTheme.typography.titleLarge,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                Text(
                                    text = insights.currentPhase.guidance,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (insights.currentCycleDay != null) {
                                Surface(
                                    shape = RoundedCornerShape(16.dp),
                                    color = MaterialTheme.colorScheme.tertiaryContainer
                                ) {
                                    Column(
                                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally
                                    ) {
                                        Text(
                                            text = "DAY ${insights.currentCycleDay}",
                                            style = MaterialTheme.typography.labelLarge.copy(
                                                fontFamily = JetBrainsMonoMetricFont
                                            ),
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                        Text(
                                            text = "${insights.averageCycleLengthDays}d avg",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            MetricTile(
                                title = "Next Predicted",
                                value = insights.nextPredictedStartEpochDay?.let {
                                    DateEpochUtils.formatEpochDayShort(it)
                                } ?: "Log first cycle",
                                subtitle = insights.daysUntilNextPeriod?.let {
                                    when {
                                        it > 0 -> "in $it days"
                                        it == 0 -> "expected today"
                                        else -> "${-it} days past avg"
                                    }
                                } ?: "Offline estimate",
                                modifier = Modifier.weight(1f)
                            )
                            MetricTile(
                                title = "Fertile Window",
                                value = if (insights.fertileWindowStartEpochDay != null &&
                                    insights.fertileWindowEndEpochDay != null
                                ) {
                                    "${DateEpochUtils.formatEpochDayShort(insights.fertileWindowStartEpochDay)} – ${DateEpochUtils.formatEpochDayShort(insights.fertileWindowEndEpochDay)}"
                                } else {
                                    "—"
                                },
                                subtitle = "Avg period: ${insights.averagePeriodLengthDays}d",
                                modifier = Modifier.weight(1f)
                            )
                        }

                        Button(
                            onClick = {
                                editingRecord = null
                                prefilledStartEpochDay = todayEpochDay
                                showLogDialog = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                                .testTag("period_log_button"),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.tertiary
                            )
                        ) {
                            Icon(imageVector = Icons.Rounded.Add, contentDescription = null)
                            Spacer(modifier = Modifier.size(6.dp))
                            Text("Log Period & Symptoms")
                        }
                    }
                }
            }

            // Visual Cycle Calendar Grid
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { viewModel.shiftMonth(-1) },
                                modifier = Modifier.testTag("period_prev_month")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                                    contentDescription = "Previous month"
                                )
                            }
                            Text(
                                text = DateEpochUtils.formatMonthYear(
                                    uiState.displayYear,
                                    uiState.displayMonthZeroBased
                                ),
                                style = MaterialTheme.typography.titleMedium
                            )
                            IconButton(
                                onClick = { viewModel.shiftMonth(1) },
                                modifier = Modifier.testTag("period_next_month")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                                    contentDescription = "Next month"
                                )
                            }
                        }

                        CycleMonthCalendar(
                            year = uiState.displayYear,
                            monthZeroBased = uiState.displayMonthZeroBased,
                            todayEpochDay = todayEpochDay,
                            records = uiState.records,
                            insights = uiState.insights,
                            onDayClicked = { clickedDay ->
                                val existing = uiState.records.firstOrNull { rec ->
                                    val end = rec.endDateEpochDay ?: (rec.startDateEpochDay + uiState.insights.averagePeriodLengthDays - 1)
                                    clickedDay in rec.startDateEpochDay..end
                                }
                                editingRecord = existing
                                prefilledStartEpochDay = existing?.startDateEpochDay ?: clickedDay
                                showLogDialog = true
                            }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly
                        ) {
                            LegendDot(color = MaterialTheme.colorScheme.tertiary, label = "Logged")
                            LegendDot(
                                color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.28f),
                                label = "Predicted"
                            )
                            LegendDot(color = FertileLeafGreen.copy(alpha = 0.65f), label = "Fertile")
                        }
                    }
                }
            }

            // History Header
            item {
                Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                    Text(
                        text = "Logged Cycles (${uiState.records.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            if (uiState.records.isEmpty()) {
                item {
                    Surface(
                        modifier = Modifier
                            .widthIn(max = 600.dp)
                            .fillMaxWidth(),
                        shape = RoundedCornerShape(18.dp),
                        color = MaterialTheme.colorScheme.surface
                    ) {
                        Text(
                            text = "No cycles recorded yet. Tap 'Log Period & Symptoms' or tap any date on the calendar.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                items(uiState.records, key = { it.id }) { record ->
                    PeriodHistoryRow(
                        record = record,
                        onEdit = {
                            editingRecord = record
                            prefilledStartEpochDay = record.startDateEpochDay
                            showLogDialog = true
                        },
                        onDelete = { viewModel.deleteRecord(record.id) }
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    if (showLogDialog) {
        PeriodLogDialog(
            existing = editingRecord,
            initialStartEpochDay = prefilledStartEpochDay,
            defaultPeriodDays = uiState.insights.averagePeriodLengthDays,
            onDismiss = { showLogDialog = false },
            onSave = { start, end, flow, symptoms, mood, notes ->
                viewModel.savePeriodRecord(
                    existingId = editingRecord?.id ?: 0L,
                    startEpochDay = start,
                    endEpochDay = end,
                    flowIntensity = flow,
                    symptoms = symptoms,
                    mood = mood,
                    notes = notes
                )
                showLogDialog = false
            }
        )
    }
}

@Composable
private fun MetricTile(
    title: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(fontFamily = JetBrainsMonoMetricFont)
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CycleMonthCalendar(
    year: Int,
    monthZeroBased: Int,
    todayEpochDay: Long,
    records: List<PeriodRecordEntity>,
    insights: com.example.domain.CycleInsights,
    onDayClicked: (Long) -> Unit
) {
    val weekdays = listOf("S", "M", "T", "W", "T", "F", "S")
    val firstOffset = DateEpochUtils.firstDayOfWeekOffset(year, monthZeroBased)
    val daysCount = DateEpochUtils.daysInMonth(year, monthZeroBased)
    val totalCells = ((firstOffset + daysCount + 6) / 7) * 7

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            weekdays.forEach { d ->
                Text(
                    text = d,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        for (rowStart in 0 until totalCells step 7) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for (col in 0 until 7) {
                    val cellIdx = rowStart + col
                    val dayNum = cellIdx - firstOffset + 1
                    if (dayNum in 1..daysCount) {
                        val epochDay = DateEpochUtils.epochDayFromYmd(year, monthZeroBased, dayNum)
                        val isLoggedPeriod = records.any { rec ->
                            val end = rec.endDateEpochDay
                                ?: (rec.startDateEpochDay + insights.averagePeriodLengthDays - 1)
                            epochDay in rec.startDateEpochDay..end
                        }
                        val predictedStart = insights.nextPredictedStartEpochDay
                        val isPredicted = predictedStart != null &&
                            epochDay in predictedStart..(predictedStart + insights.averagePeriodLengthDays - 1)
                        val isFertile = insights.fertileWindowStartEpochDay != null &&
                            insights.fertileWindowEndEpochDay != null &&
                            epochDay in insights.fertileWindowStartEpochDay..insights.fertileWindowEndEpochDay
                        val isToday = epochDay == todayEpochDay

                        val bg = when {
                            isLoggedPeriod -> MaterialTheme.colorScheme.tertiary
                            isPredicted -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.22f)
                            isFertile -> FertileLeafGreen.copy(alpha = 0.24f)
                            else -> Color.Transparent
                        }
                        val fg = when {
                            isLoggedPeriod -> Color.White
                            else -> MaterialTheme.colorScheme.onSurface
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .aspectRatio(1f)
                                .clip(CircleShape)
                                .background(bg)
                                .then(
                                    if (isToday) {
                                        Modifier.border(
                                            1.5.dp,
                                            MaterialTheme.colorScheme.primary,
                                            CircleShape
                                        )
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onDayClicked(epochDay) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = dayNum.toString(),
                                style = MaterialTheme.typography.bodySmall.copy(
                                    fontWeight = if (isToday || isLoggedPeriod) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = fg
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun PeriodHistoryRow(
    record: PeriodRecordEntity,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onEdit),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val endLabel = record.endDateEpochDay?.let {
                    " – ${DateEpochUtils.formatEpochDayShort(it)}"
                } ?: " (Ongoing)"
                Text(
                    text = "${DateEpochUtils.formatEpochDayFull(record.startDateEpochDay)}$endLabel",
                    style = MaterialTheme.typography.titleSmall
                )
                val details = buildString {
                    append("Flow: ${record.flowIntensity} • Mood: ${record.mood}")
                    if (record.symptomsCsv.isNotBlank()) {
                        append("\nSymptoms: ${record.symptomsCsv.replace(",", ", ")}")
                    }
                }
                Text(
                    text = details,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.testTag("delete_period_${record.id}")
            ) {
                Icon(
                    imageVector = Icons.Rounded.DeleteOutline,
                    contentDescription = "Delete period entry"
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PeriodLogDialog(
    existing: PeriodRecordEntity?,
    initialStartEpochDay: Long,
    defaultPeriodDays: Int,
    onDismiss: () -> Unit,
    onSave: (Long, Long?, String, Set<String>, String, String) -> Unit
) {
    var startDay by remember { mutableLongStateOf(existing?.startDateEpochDay ?: initialStartEpochDay) }
    var hasEndDate by remember { mutableStateOf(existing?.endDateEpochDay != null) }
    var endDay by remember {
        mutableLongStateOf(existing?.endDateEpochDay ?: (initialStartEpochDay + defaultPeriodDays - 1))
    }
    var flow by remember { mutableStateOf(existing?.flowIntensity ?: "Medium") }
    var selectedSymptoms by remember {
        mutableStateOf(
            existing?.symptomsCsv?.split(",")?.filter { it.isNotBlank() }?.toSet() ?: emptySet()
        )
    }
    var mood by remember { mutableStateOf(existing?.mood ?: "Calm") }
    var notes by remember { mutableStateOf(existing?.notes ?: "") }

    val flowOptions = listOf(
        "Spotting" to "🩸 Spotting",
        "Light" to "💧 Light",
        "Medium" to "🌸 Medium",
        "Heavy" to "🌊 Heavy"
    )
    val symptomOptions = listOf(
        "Cramps" to "😣 Cramps",
        "Fatigue" to "😴 Fatigue",
        "Headache" to "🤕 Headache",
        "Bloating" to "🎈 Bloating",
        "Backache" to "💆 Backache",
        "Clear" to "✨ Clear"
    )
    val moodOptions = listOf(
        "Calm" to "😌 Calm",
        "Sensitive" to "🥺 Sensitive",
        "Energized" to "⚡ Energized",
        "Low" to "☁️ Low"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (existing == null) "🌸 Log Period" else "🌸 Edit Period Entry") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Start: ${DateEpochUtils.formatEpochDayFull(startDay)}", style = MaterialTheme.typography.titleSmall)
                    Row {
                        OutlinedButton(onClick = { startDay -= 1 }) { Text("-1d") }
                        Spacer(modifier = Modifier.size(4.dp))
                        OutlinedButton(onClick = { startDay += 1 }) { Text("+1d") }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Log End Date", style = MaterialTheme.typography.bodyMedium)
                    Switch(
                        checked = hasEndDate,
                        onCheckedChange = {
                            hasEndDate = it
                            if (endDay < startDay) endDay = startDay + defaultPeriodDays - 1
                        }
                    )
                }

                if (hasEndDate) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("End: ${DateEpochUtils.formatEpochDayFull(endDay)}", style = MaterialTheme.typography.titleSmall)
                        Row {
                            OutlinedButton(onClick = { if (endDay > startDay) endDay -= 1 }) { Text("-1d") }
                            Spacer(modifier = Modifier.size(4.dp))
                            OutlinedButton(onClick = { endDay += 1 }) { Text("+1d") }
                        }
                    }
                }

                Text("💧 Flow Intensity", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    flowOptions.forEach { (option, label) ->
                        FilterChip(
                            selected = flow == option,
                            onClick = { flow = option },
                            label = { Text(label) }
                        )
                    }
                }

                Text("🩺 Symptoms", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    symptomOptions.forEach { (s, label) ->
                        FilterChip(
                            selected = selectedSymptoms.contains(s),
                            onClick = {
                                selectedSymptoms = if (selectedSymptoms.contains(s)) {
                                    selectedSymptoms - s
                                } else {
                                    selectedSymptoms + s
                                }
                            },
                            label = { Text(label) }
                        )
                    }
                }

                Text("🎭 Mood", style = MaterialTheme.typography.labelLarge)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    moodOptions.forEach { (m, label) ->
                        FilterChip(
                            selected = mood == m,
                            onClick = { mood = m },
                            label = { Text(label) }
                        )
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (optional)") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        startDay,
                        if (hasEndDate) endDay.coerceAtLeast(startDay) else null,
                        flow,
                        selectedSymptoms,
                        mood,
                        notes
                    )
                },
                modifier = Modifier.testTag("period_dialog_save_button")
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
