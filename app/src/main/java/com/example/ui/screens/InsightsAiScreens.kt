package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.DocumentScanner
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.ai.SupportedVoiceLanguages
import com.example.ui.components.WeeklyHydrationBarChart
import com.example.ui.viewmodel.AiTaskStatus
import com.example.ui.viewmodel.ModulesHubUiState
import com.example.ui.viewmodel.ModulesHubViewModel

@Composable
fun ReportsAnalyticsScreen(
    uiState: ModulesHubUiState,
    onBack: () -> Unit
) {
    val latestHr = uiState.vitalMetrics.firstOrNull { it.metricType == "HEART_RATE" }
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
                    Text("📊 Reports & Analytics", style = MaterialTheme.typography.headlineSmall)
                    Text("Cross-module insights, synced wearables & weekly rhythms", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("💧 7-Day Hydration Consistency (${uiState.hydration.streakDays}d streak 🔥)", style = MaterialTheme.typography.titleMedium)
                    WeeklyHydrationBarChart(bars = uiState.hydration.weeklyBars, goalMl = uiState.hydration.goalMl)
                }
            }
        }

        item {
            val alertStats = uiState.alertMetrics
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .testTag("reports_alert_intervals_card"),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🔔 Custom Alert Intervals & Adherence", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = "${alertStats.adherencePercent}% Done",
                            style = MaterialTheme.typography.titleSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { (alertStats.adherencePercent / 100f).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(CircleShape)
                    )
                    Text(
                        text = "${alertStats.enabledAlarmsCount} active alarms (${alertStats.intervalAlarmsCount} hourly/interval • ${alertStats.dailyAlarmsCount} daily) • ${alertStats.totalTriggered} fired • ${alertStats.totalCompleted} completed • ${alertStats.totalSnoozed} snoozed",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (alertStats.cadenceBreakdown.isNotEmpty()) {
                        @OptIn(ExperimentalLayoutApi::class)
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            alertStats.cadenceBreakdown.forEach { bucket ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                                ) {
                                    Text(
                                        text = "🔁 ${bucket.label}: ${bucket.alarmCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                SummaryStatRow(
                    "⌚ Connected Devices",
                    if (uiState.connectedSources.isNotEmpty()) {
                        "${uiState.connectedSources.size} synced" + (latestHr?.let { " • ❤️ ${it.valuePrimary.toInt()} bpm" } ?: "")
                    } else {
                        "Tap Connected Devices to sync"
                    }
                )
                SummaryStatRow(
                    "🔔 Custom Alerts & Intervals",
                    if (uiState.alertMetrics.totalAlarmsCount > 0) {
                        "${uiState.alertMetrics.enabledAlarmsCount} active • ${uiState.alertMetrics.intervalAlarmsCount} repeating • ${uiState.alertMetrics.adherencePercent}% done"
                    } else {
                        "No custom alerts yet"
                    }
                )
                SummaryStatRow("💊 Medicines & Doctors", "${uiState.todayMedicineIntakes.count { it.status == "Taken" }} doses taken • ${uiState.consultations.count { it.followUpEpochDay != null && !it.isFollowUpCompleted }} follow-ups")
                SummaryStatRow("🏃 Movement & Steps", "${uiState.exercises.sumOf { it.durationMinutes }} active min • ${uiState.todaySteps?.stepCount ?: 0} steps")
                SummaryStatRow("🥗 Nutrition Logged", "${uiState.dietRecords.sumOf { it.calories }} kcal across ${uiState.dietRecords.size} meals")
                SummaryStatRow("😴 Sleep Entries", "${uiState.sleepRecords.size} recorded nights")
                SummaryStatRow("🌸 Cycle Care", uiState.cycleInsights?.currentCycleDay?.let { "Cycle Day $it • ${uiState.cycleInsights.currentPhase.label}" } ?: "Ready when enabled")
                SummaryStatRow("✨ Self-Care & Wellbeing", "${uiState.beautyRecords.size} beauty rituals • ${uiState.todayWellbeing?.focusSessionsCount ?: 0} focus sessions")
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun SummaryStatRow(title: String, value: String) {
    Surface(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(value, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun OnDeviceAiScreen(
    uiState: ModulesHubUiState,
    viewModel: ModulesHubViewModel,
    onBack: () -> Unit,
    onNavigateModuleKey: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var inputPrompt by remember { mutableStateOf("") }
    val selectedLang = SupportedVoiceLanguages.byTag(uiState.selectedVoiceLanguageTag)

    LaunchedEffect(uiState.pendingNavigationModuleKey) {
        val target = uiState.pendingNavigationModuleKey
        if (!target.isNullOrBlank()) {
            viewModel.consumePendingNavigation()
            onNavigateModuleKey(target)
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.startVoiceCapture()
        }
    }

    val prescriptionPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.scanAndImportPrescription(context, uri)
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("ai_back_button")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("🎙️ AI Voice & Smart Companion", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Multilingual voice guide • Prescription scanner • Instant metric logging",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Multilingual Voice Guide Card
        item {
            Card(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("🌍 Voice Guide Language", style = MaterialTheme.typography.titleMedium)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("🔊 Auto-Speak", style = MaterialTheme.typography.labelSmall)
                            Switch(
                                checked = uiState.autoSpeakReplies,
                                onCheckedChange = viewModel::setAutoSpeak
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SupportedVoiceLanguages.options.forEach { lang ->
                            FilterChip(
                                selected = uiState.selectedVoiceLanguageTag == lang.tag,
                                onClick = { viewModel.selectVoiceLanguage(lang.tag) },
                                label = { Text("${lang.emoji} ${lang.label}") },
                                modifier = Modifier.testTag("voice_lang_${lang.tag}")
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.playMultilingualAppGuide() },
                            enabled = uiState.aiTaskStatus !is AiTaskStatus.Loading,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("ai_voice_guide_button")
                        ) {
                            Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null)
                            Text(" Guide Me (${selectedLang.emoji})")
                        }
                        if (uiState.isSpeakingVoice) {
                            OutlinedButton(onClick = viewModel::stopVoicePlayback) {
                                Icon(Icons.Rounded.Stop, contentDescription = "Stop speaking")
                                Text(" Stop")
                            }
                        }
                    }
                }
            }
        }

        // Prescription Photo Scanner -> Auto-Add Medicines & Doctors
        item {
            Card(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("📄 Upload Prescription Photo", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Pick a photo of a prescription or clinic note. AI reads the medicines, doses, intervals, meal timing, and doctor follow-up and saves them straight to Doctors & Medicines.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            prescriptionPickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        enabled = uiState.aiTaskStatus !is AiTaskStatus.Loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_upload_prescription_button")
                    ) {
                        Icon(Icons.Rounded.DocumentScanner, contentDescription = null)
                        Text(" Upload & Extract Prescription 💊")
                    }

                    uiState.lastPrescriptionResult?.let { rx ->
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("✅ Added to Doctors & Medicines", style = MaterialTheme.typography.titleSmall)
                                Text(rx.summary, style = MaterialTheme.typography.bodySmall)
                                rx.medicines.forEach { med ->
                                    Text(
                                        "• 💊 ${med.name} (${med.dosage}) — ${med.dosesPerDay}x/day every ${med.intervalHours}h • ${med.mealRelation}",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                if (rx.doctorName.isNotBlank()) {
                                    Text(
                                        "• 🩺 Dr. ${rx.doctorName} (${rx.specialty})" +
                                            if (rx.followUpDaysFromToday > 0) " • Follow-up in ${rx.followUpDaysFromToday}d" else "",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                }
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onNavigateModuleKey("medical_care") }) {
                                        Text("Open Medicines & Doctors →")
                                    }
                                    TextButton(onClick = viewModel::clearPrescriptionPreview) {
                                        Text("Dismiss")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Loading / Voice / Error Feedback
        if (uiState.aiTaskStatus is AiTaskStatus.Loading) {
            item {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Text((uiState.aiTaskStatus as AiTaskStatus.Loading).label, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }

        if (uiState.isListeningVoice) {
            item {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.tertiaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Rounded.GraphicEq, contentDescription = null)
                            Text(
                                text = uiState.voicePartialTranscript.ifBlank { "Listening in ${selectedLang.label}..." },
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        TextButton(onClick = viewModel::stopVoiceCapture) { Text("Stop") }
                    }
                }
            }
        }

        uiState.voiceError?.let { vErr ->
            item {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.errorContainer
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(vErr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                        TextButton(onClick = viewModel::clearVoiceError) { Text("OK") }
                    }
                }
            }
        }

        // Quick Voice / Smart-Log Try-It Chips
        item {
            Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("⚡ Try Saying or Tapping (Auto-Logs to App)", style = MaterialTheme.typography.labelLarge)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        selectedLang.samplePrompt,
                        "☕ I just had a coffee",
                        "💧 I drank 200 ml water",
                        "🏃 Walked 4000 steps today",
                        "💊 Add Vitamin D3 1000IU once a day after breakfast",
                        "🧭 Open Connected Devices"
                    ).distinct().forEach { prompt ->
                        FilterChip(
                            selected = false,
                            onClick = { viewModel.askAiAssistant(prompt) },
                            label = { Text(prompt) }
                        )
                    }
                }
            }
        }

        // Chat Conversation with Executed Action Badges & Listen Replay
        items(uiState.aiChatMessages, key = { it.id }) { msg ->
            Column(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                horizontalAlignment = if (msg.isFromUser) Alignment.End else Alignment.Start
            ) {
                Surface(
                    shape = RoundedCornerShape(18.dp),
                    color = when {
                        msg.isError -> MaterialTheme.colorScheme.errorContainer
                        msg.isFromUser -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.surface
                    }
                ) {
                    Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = msg.text,
                            style = MaterialTheme.typography.bodyMedium,
                            color = when {
                                msg.isError -> MaterialTheme.colorScheme.onErrorContainer
                                msg.isFromUser -> MaterialTheme.colorScheme.onPrimary
                                else -> MaterialTheme.colorScheme.onSurface
                            }
                        )
                        if (msg.actionBadges.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                msg.actionBadges.forEach { badge ->
                                    Surface(
                                        shape = CircleShape,
                                        color = MaterialTheme.colorScheme.secondaryContainer
                                    ) {
                                        Text(
                                            text = "✅ $badge",
                                            style = MaterialTheme.typography.labelSmall,
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                        )
                                    }
                                }
                            }
                        }
                        if (!msg.isFromUser && !msg.isError) {
                            TextButton(
                                onClick = { viewModel.voiceManager.speak(msg.text, selectedLang.tag) }
                            ) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" Speak (${selectedLang.emoji})")
                            }
                        }
                    }
                }
            }
        }

        // Voice Mic + Text Input Bar
        item {
            Row(
                modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth().padding(bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = {
                        if (uiState.isListeningVoice) {
                            viewModel.stopVoiceCapture()
                        } else {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) {
                                viewModel.startVoiceCapture()
                            } else {
                                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                            }
                        }
                    },
                    modifier = Modifier.testTag("ai_voice_mic_button")
                ) {
                    Icon(
                        imageVector = if (uiState.isListeningVoice) Icons.Rounded.Stop else Icons.Rounded.Mic,
                        contentDescription = "Voice input",
                        tint = if (uiState.isListeningVoice) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }

                OutlinedTextField(
                    value = inputPrompt,
                    onValueChange = { inputPrompt = it },
                    placeholder = { Text("Say or type e.g. 'I had 200 ml water'...") },
                    singleLine = true,
                    modifier = Modifier.weight(1f).testTag("local_ai_input")
                )
                IconButton(
                    onClick = {
                        viewModel.askAiAssistant(inputPrompt)
                        inputPrompt = ""
                    },
                    enabled = uiState.aiTaskStatus !is AiTaskStatus.Loading,
                    modifier = Modifier.testTag("local_ai_send_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.Send, contentDescription = "Send prompt")
                }
            }
        }
    }
}
