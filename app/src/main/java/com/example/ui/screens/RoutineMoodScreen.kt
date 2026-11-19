package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.NotificationsNone
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
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.data.local.entity.DailyRoutineEntity
import com.example.data.local.entity.MoodJournalEntryEntity
import com.example.domain.RoutineMoodAnalyticsEngine
import com.example.notifications.BundledAlertTone
import com.example.ui.components.AchievementBadgeCard
import com.example.ui.components.DailyRoutineItemCard
import com.example.ui.components.MoodTrendChartCard
import com.example.ui.components.OptionalChallengeCard
import com.example.ui.components.RoutineAdherenceRingCard
import com.example.ui.viewmodel.RoutineMoodUiState
import com.example.ui.viewmodel.RoutineMoodViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun RoutineMoodScreen(
    uiState: RoutineMoodUiState,
    viewModel: RoutineMoodViewModel,
    initialTabIndex: Int = 0,
    onBack: () -> Unit
) {
    var selectedTab by rememberSaveable(initialTabIndex) { mutableIntStateOf(initialTabIndex.coerceIn(0, 2)) }
    var showRoutineDialog by remember { mutableStateOf(false) }
    var editingRoutine by remember { mutableStateOf<DailyRoutineEntity?>(null) }
    var showMoodReminderDialog by remember { mutableStateOf(false) }

    val tabLabels = listOf("🌿 Routines", "📓 Mood Journal", "🏆 Challenges")

    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("routine_mood_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text(
                        text = stringResource(id = R.string.routines_mood_title),
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = stringResource(id = R.string.routines_mood_subtitle),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        TabRow(
            selectedTabIndex = selectedTab,
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxWidth()
        ) {
            tabLabels.forEachIndexed { idx, label ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = { Text(label, maxLines = 1) },
                    modifier = Modifier.testTag("routine_mood_tab_$idx")
                )
            }
        }

        uiState.statusBanner?.let { banner ->
            Surface(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 8.dp),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = banner,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = viewModel::clearStatusBanner) {
                        Text("OK")
                    }
                }
            }
        }

        LazyColumn(
            modifier = Modifier
                .widthIn(max = 620.dp)
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item { Spacer(modifier = Modifier.height(4.dp)) }

            when (selectedTab) {
                0 -> {
                    item {
                        RoutineAdherenceRingCard(adherence = uiState.routineAdherence)
                    }

                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Your Daily Routines (${uiState.routines.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Button(
                                onClick = {
                                    editingRoutine = null
                                    showRoutineDialog = true
                                },
                                modifier = Modifier.testTag("create_custom_routine_button")
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("New Routine")
                            }
                        }
                    }

                    if (uiState.routines.isEmpty()) {
                        item {
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "🌱 Start light — pick a gentle anchor or build your own",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "Routines are 100% optional tools to reduce mental load, never strict chores.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    } else {
                        items(uiState.routines, key = { it.id }) { routine ->
                            DailyRoutineItemCard(
                                routine = routine,
                                todayCompletion = uiState.todayCompletionsByRoutineId[routine.id],
                                onToggleWhole = { viewModel.toggleWholeRoutine(routine) },
                                onToggleStep = { stepIdx -> viewModel.toggleRoutineStep(routine, stepIdx) },
                                onToggleReminder = { enabled -> viewModel.toggleRoutineReminder(routine, enabled) },
                                onEdit = {
                                    editingRoutine = routine
                                    showRoutineDialog = true
                                },
                                onDelete = { viewModel.deleteRoutine(routine) }
                            )
                        }
                    }

                    if (uiState.suggestedRoutines.isNotEmpty()) {
                        item {
                            Text(
                                text = "✨ Optional Smart Ideas (Tailored to Your Logs)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        items(uiState.suggestedRoutines, key = { it.templateKey }) { suggestion ->
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("suggested_routine_${suggestion.templateKey}"),
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.28f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(14.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${suggestion.emoji} ${suggestion.title}",
                                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                            modifier = Modifier.weight(1f)
                                        )
                                        OutlinedButton(
                                            onClick = { viewModel.adoptSuggestedRoutine(suggestion) },
                                            modifier = Modifier.testTag("adopt_suggested_${suggestion.templateKey}")
                                        ) {
                                            Text("Add")
                                        }
                                    }
                                    Text(
                                        text = suggestion.reason,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "${suggestion.timeSlot.label} (${suggestion.triggerTime24h}) • ${suggestion.steps.joinToString(" → ")}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }
                }

                1 -> {
                    item {
                        MoodTrendChartCard(analytics = uiState.moodAnalytics)
                    }

                    item {
                        MoodEntryComposerCard(
                            reminderSummary = uiState.moodReminder?.takeIf { it.isEnabled }
                                ?.let { "Daily nudge at ${it.triggerTime24h}" } ?: "Reminder off",
                            onOpenReminderConfig = { showMoodReminderDialog = true },
                            onSaveMood = { preset, energy, stress, tags, note, gratitude ->
                                viewModel.logMoodEntry(preset, energy, stress, tags, note, gratitude)
                            }
                        )
                    }

                    if (uiState.moodEntries.isNotEmpty()) {
                        item {
                            Text(
                                text = "Recent Reflections (${uiState.moodEntries.size})",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                        items(uiState.moodEntries.take(25), key = { it.id }) { entry ->
                            MoodEntryHistoryCard(
                                entry = entry,
                                onDelete = { viewModel.deleteMoodEntry(entry.id) }
                            )
                        }
                    }
                }

                2 -> {
                    item {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "🕊️ Optional, Cumulative & Non-Manipulative",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Join only the challenges that inspire you. Pause or leave anytime — missed days never erase your progress.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    item {
                        Text(
                            text = "Optional Wellness Challenges",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    items(uiState.challenges, key = { it.id }) { challenge ->
                        OptionalChallengeCard(
                            challenge = challenge,
                            todayDayKey = uiState.todayDayKey,
                            onJoin = { viewModel.joinChallenge(challenge) },
                            onPause = { viewModel.pauseChallenge(challenge) },
                            onCheckInToday = { viewModel.checkInChallenge(challenge) },
                            onLeave = { viewModel.leaveChallenge(challenge) }
                        )
                    }

                    item {
                        val earnedCount = uiState.badges.count { it.earnedAtEpochMs != null }
                        Text(
                            text = "🏅 Achievement Milestones ($earnedCount/${uiState.badges.size} Unlocked)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    items(uiState.badges.chunked(2)) { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            pair.forEach { badge ->
                                AchievementBadgeCard(
                                    badge = badge,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (pair.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(24.dp)) }
        }
    }

    if (showRoutineDialog) {
        RoutineEditorDialog(
            editingRoutine = editingRoutine,
            onDismiss = { showRoutineDialog = false },
            onSave = { existingId, title, emoji, slot, time, mins, steps, remEnabled, soundKey, repeatMins ->
                viewModel.saveRoutine(
                    existingId = existingId,
                    title = title,
                    emoji = emoji,
                    timeSlot = slot,
                    triggerTime24h = time,
                    estimatedMinutes = mins,
                    steps = steps,
                    reminderEnabled = remEnabled,
                    reminderSoundKey = soundKey,
                    repeatIntervalMinutes = repeatMins
                )
                showRoutineDialog = false
            }
        )
    }

    if (showMoodReminderDialog) {
        MoodReminderConfigDialog(
            initialEnabled = uiState.moodReminder?.isEnabled ?: false,
            initialTime24h = uiState.moodReminder?.triggerTime24h ?: "20:30",
            initialSoundKey = uiState.moodReminder?.soundKey ?: BundledAlertTone.SINGING_BOWL.id,
            onDismiss = { showMoodReminderDialog = false },
            onSave = { enabled, time, sound ->
                viewModel.configureMoodReminder(enabled, time, sound)
                showMoodReminderDialog = false
            }
        )
    }

    uiState.celebratoryBadge?.let { unlocked ->
        AlertDialog(
            onDismissRequest = viewModel::dismissCelebratoryBadge,
            title = { Text("${unlocked.emoji} Milestone Reached!") },
            text = {
                Text("You unlocked \"${unlocked.title}\": ${unlocked.description}")
            },
            confirmButton = {
                Button(onClick = viewModel::dismissCelebratoryBadge) { Text("Nice!") }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodEntryComposerCard(
    reminderSummary: String,
    onOpenReminderConfig: () -> Unit,
    onSaveMood: (
        preset: com.example.domain.MoodPresetOption,
        energy: Int,
        stress: Int,
        tags: List<String>,
        note: String,
        gratitude: String
    ) -> Unit
) {
    var selectedPreset by remember { mutableStateOf(RoutineMoodAnalyticsEngine.moodPresets[1]) }
    var energySlider by remember { mutableFloatStateOf(selectedPreset.defaultEnergy.toFloat()) }
    var stressSlider by remember { mutableFloatStateOf(selectedPreset.defaultStress.toFloat()) }
    var selectedTags by remember { mutableStateOf(setOf<String>()) }
    var reflectionNote by remember { mutableStateOf("") }
    var gratitudeText by remember { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("mood_composer_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                Text(
                    text = "How are you feeling right now?",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier.weight(1f)
                )
                TextButton(
                    onClick = onOpenReminderConfig,
                    modifier = Modifier.testTag("mood_reminder_config_button")
                ) {
                    Icon(Icons.Rounded.NotificationsNone, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(reminderSummary)
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RoutineMoodAnalyticsEngine.moodPresets.forEach { preset ->
                    FilterChip(
                        selected = selectedPreset.label == preset.label,
                        onClick = {
                            selectedPreset = preset
                            energySlider = preset.defaultEnergy.toFloat()
                            stressSlider = preset.defaultStress.toFloat()
                        },
                        label = { Text("${preset.emoji} ${preset.label}") },
                        modifier = Modifier.testTag("mood_preset_${preset.label}")
                    )
                }
            }

            Column {
                Text("⚡ Energy: ${energySlider.roundToInt()}/5", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = energySlider,
                    onValueChange = { energySlider = it },
                    valueRange = 1f..5f,
                    steps = 3
                )
                Text("🌊 Stress Load: ${stressSlider.roundToInt()}/5", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = stressSlider,
                    onValueChange = { stressSlider = it },
                    valueRange = 1f..5f,
                    steps = 3
                )
            }

            Text("Context Tags (Optional)", style = MaterialTheme.typography.labelLarge)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RoutineMoodAnalyticsEngine.moodContextTags.forEach { tag ->
                    val chosen = tag in selectedTags
                    FilterChip(
                        selected = chosen,
                        onClick = {
                            selectedTags = if (chosen) selectedTags - tag else selectedTags + tag
                        },
                        label = { Text(tag) }
                    )
                }
            }

            OutlinedTextField(
                value = reflectionNote,
                onValueChange = { reflectionNote = it },
                label = { Text("Private Reflection (Optional)") },
                placeholder = { Text("What feels helpful or present right now?") },
                minLines = 2,
                maxLines = 4,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mood_reflection_input")
            )

            OutlinedTextField(
                value = gratitudeText,
                onValueChange = { gratitudeText = it },
                label = { Text("One Small Gratitude (Optional)") },
                placeholder = { Text("Warm tea, a quiet walk, a kind call...") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("mood_gratitude_input")
            )

            Button(
                onClick = {
                    onSaveMood(
                        selectedPreset,
                        energySlider.roundToInt(),
                        stressSlider.roundToInt(),
                        selectedTags.toList(),
                        reflectionNote,
                        gratitudeText
                    )
                    reflectionNote = ""
                    gratitudeText = ""
                    selectedTags = emptySet()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("save_mood_entry_button")
            ) {
                Text("Save Reflection Privately")
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MoodEntryHistoryCard(
    entry: MoodJournalEntryEntity,
    onDelete: () -> Unit
) {
    val timeText = remember(entry.loggedAtEpochMs) {
        SimpleDateFormat("MMM d • h:mm a", Locale.getDefault()).format(Date(entry.loggedAtEpochMs))
    }
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(entry.moodEmoji, fontSize = 22.sp)
                    Column {
                        Text(
                            text = "${entry.moodLabel} • Energy ${entry.energyLevel}/5 • Stress ${entry.stressLevel}/5",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Text(
                            text = timeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete mood entry")
                }
            }
            if (entry.tagsCsv.isNotBlank()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    entry.tagsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { tag ->
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = tag,
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
            }
            if (entry.reflectionNote.isNotBlank()) {
                Text(entry.reflectionNote, style = MaterialTheme.typography.bodySmall)
            }
            if (entry.gratitudeText.isNotBlank()) {
                Text(
                    text = "✨ Grateful for: ${entry.gratitudeText}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}
