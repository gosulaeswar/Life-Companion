package com.example.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.offset
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.core.content.ContextCompat
import com.example.ai.SupportedVoiceLanguages
import com.example.domain.DateEpochUtils
import com.example.ui.components.ModuleStatusPill
import com.example.ui.components.PrivacyVaultBadge
import com.example.ui.components.QuickAmountChip
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.FertileLeafGreen
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.AiTaskStatus
import com.example.ui.viewmodel.HomeUiState
import com.example.ui.viewmodel.ModulesHubUiState
import com.example.wellbeing.OveruseSeverity
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private data class HomeCategoryVisual(
    val key: String,
    val emoji: String,
    val title: String,
    val subtitle: String,
    val badgeBg: Color,
    val cardTint: Color
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeDashboardScreen(
    uiState: HomeUiState,
    modulesState: ModulesHubUiState = ModulesHubUiState(),
    onQuickAddWater: (Int) -> Unit,
    onOpenWaterTracker: () -> Unit,
    onOpenPeriodTracker: () -> Unit,
    onEnableCycleTracking: () -> Unit,
    onOpenProfileEdit: () -> Unit,
    onOpenSettingsPrivacy: () -> Unit,
    onOpenModulesHub: () -> Unit = {},
    onOpenModuleKey: (String) -> Unit = {},
    onStartVoiceCapture: () -> Unit = {},
    onStopVoiceCapture: () -> Unit = {},
    onStopVoicePlayback: () -> Unit = {},
    onClearVoiceError: () -> Unit = {},
    onSnoozeWellbeingAlert: () -> Unit = {},
    onStartFocusBreak: () -> Unit = {},
    onOpenUsageAccessSettings: () -> Unit = {},
    onRequestNotificationPermission: () -> Unit = {}
) {
    val context = LocalContext.current
    val profile = uiState.profile
    var dailyVibe by rememberSaveable { mutableStateOf("🌿 Balanced") }
    val todayEpochDay = remember { DateEpochUtils.todayEpochDay() }
    val selectedLang = SupportedVoiceLanguages.byTag(modulesState.selectedVoiceLanguageTag)
    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStartVoiceCapture()
        }
    }

    val greetingEmoji = when {
        uiState.greeting.contains("morning", ignoreCase = true) -> "🌅"
        uiState.greeting.contains("afternoon", ignoreCase = true) -> "☀️"
        uiState.greeting.contains("evening", ignoreCase = true) -> "🌆"
        else -> "🌙"
    }

    val activeMeds = modulesState.medicines.filter { it.isActive }
    val expectedDoses = activeMeds.sumOf { it.dosesPerDay.coerceAtLeast(1) }
    val takenDoses = modulesState.todayMedicineIntakes.count { it.status == "Taken" }
    val nextFollowUp = modulesState.consultations
        .filter { it.followUpEpochDay != null && !it.isFollowUpCompleted }
        .minByOrNull { it.followUpEpochDay ?: Long.MAX_VALUE }
    val totalSteps = modulesState.todaySteps?.stepCount ?: 0
    val activeMinutes = modulesState.exercises.sumOf { it.durationMinutes }
    val totalCalories = modulesState.dietRecords.sumOf { it.calories }
    val checkInWins = (if (uiState.hydration.totalTodayMl > 0) 1 else 0) +
        takenDoses +
        modulesState.exercises.size +
        modulesState.dietRecords.size +
        modulesState.beautyRecords.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Colorful Welcome & Identity Hero Header
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(28.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(52.dp)
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = greetingEmoji, fontSize = 26.sp)
                            }
                            Column {
                                Text(
                                    text = "${uiState.greeting} ✨",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = profile?.fullName ?: "Companion",
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onBackground,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        IconButton(
                            onClick = onOpenProfileEdit,
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                                .testTag("home_edit_profile_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = "Edit local profile",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        PrivacyVaultBadge(text = "🛡️ 100% On-Device")
                        Surface(
                            shape = CircleShape,
                            color = AmberNotice.copy(alpha = 0.2f)
                        ) {
                            Text(
                                text = "🏆 $checkInWins logs today",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                        Surface(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable(onClick = onOpenSettingsPrivacy),
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                            shape = CircleShape
                        ) {
                            Text(
                                text = "🔔 Tones & Alarms",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

        // Quick AI Voice Listener Mic Card
        item {
            val isListening = modulesState.isListeningVoice
            val isThinking = modulesState.aiTaskStatus is AiTaskStatus.Loading
            val latestAiReply = modulesState.aiChatMessages.lastOrNull { !it.isFromUser }

            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .testTag("home_ai_listener_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.30f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = {
                                if (isListening) {
                                    onStopVoiceCapture()
                                } else {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) {
                                        onStartVoiceCapture()
                                    } else {
                                        micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isListening) MaterialTheme.colorScheme.error
                                    else MaterialTheme.colorScheme.primary
                                )
                                .testTag("home_ai_mic_button")
                        ) {
                            if (isThinking) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    strokeWidth = 2.5.dp
                                )
                            } else {
                                Icon(
                                    imageVector = if (isListening) Icons.Rounded.Stop else Icons.Rounded.Mic,
                                    contentDescription = if (isListening) "Stop AI voice listener" else "Speak to AI voice listener",
                                    tint = if (isListening) MaterialTheme.colorScheme.onError else MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        }

                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (isListening) "🎧 Listening (${selectedLang.emoji})..." else "🎙️ AI Voice Listener",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "${selectedLang.emoji} ${selectedLang.label.substringBefore(" ")}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            Text(
                                text = if (isListening && modulesState.voicePartialTranscript.isNotBlank()) {
                                    "“${modulesState.voicePartialTranscript}”"
                                } else {
                                    "Tap the mic to speak in your language—log coffee, 200 ml water, steps, or ask how to use any feature."
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    modulesState.voiceError?.let { vErr ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = vErr,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = onClearVoiceError) { Text("OK") }
                            }
                        }
                    }

                    if (latestAiReply != null) {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = if (latestAiReply.isError) {
                                MaterialTheme.colorScheme.errorContainer
                            } else {
                                MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
                            }
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = latestAiReply.text,
                                    style = MaterialTheme.typography.bodySmall,
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis
                                )
                                if (latestAiReply.actionBadges.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        latestAiReply.actionBadges.forEach { badge ->
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.secondaryContainer
                                            ) {
                                                Text(
                                                    text = "✅ $badge",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = { onOpenModuleKey("on_device_ai") }) {
                            Text("📄 Scan Rx / Languages →")
                        }
                        if (modulesState.isSpeakingVoice) {
                            TextButton(onClick = onStopVoicePlayback) {
                                Icon(Icons.AutoMirrored.Rounded.VolumeUp, contentDescription = null, modifier = Modifier.size(16.dp))
                                Text(" Stop Audio")
                            }
                        }
                    }
                }
            }
        }

        // Today's Live Metrics Card (Steps, Hydration, Meds, Heart Rate, Calories, Sleep, Screen)
        item {
            val stepGoal = 8000
            val stepProgress = (totalSteps.toFloat() / stepGoal.toFloat()).coerceIn(0f, 1f)
            val latestHr = modulesState.vitalMetrics.firstOrNull { it.metricType == "HEART_RATE" }
            val latestSleepHrs = modulesState.sleepRecords.firstOrNull()?.let { s ->
                (s.wakeEpochMs - s.bedtimeEpochMs) / 3_600_000f
            }
            val latestStepsRecord = modulesState.todaySteps
            val distanceKm = ((latestStepsRecord?.distanceMeters ?: (totalSteps * 0.74).toInt()) / 1000f)

            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .testTag("home_live_metrics_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "📊 Today's Live Metrics",
                            style = MaterialTheme.typography.titleMedium
                        )
                        TextButton(onClick = { onOpenModuleKey("connected_devices") }) {
                            Text("⌚ Sync Watch")
                        }
                    }

                    // Prominent Steps Bar
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .clickable { onOpenModuleKey("exercise_tracker") }
                            .testTag("home_steps_metric_tile"),
                        shape = RoundedCornerShape(16.dp),
                        color = AmberNotice.copy(alpha = 0.16f)
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "👟 Daily Steps",
                                    style = MaterialTheme.typography.titleSmall
                                )
                                Text(
                                    text = "$totalSteps / $stepGoal  (${String.format("%.1f", distanceKm)} km)",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontFamily = JetBrainsMonoMetricFont
                                    ),
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            LinearProgressIndicator(
                                progress = { stepProgress },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(CircleShape)
                            )
                        }
                    }

                    // Compact Live Metrics Grid
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        HomeMiniMetricChip(
                            emoji = "💧",
                            label = "Water",
                            value = "${uiState.hydration.totalTodayMl} ml",
                            onClick = onOpenWaterTracker
                        )
                        HomeMiniMetricChip(
                            emoji = "❤️",
                            label = "Heart",
                            value = latestHr?.let { "${it.valuePrimary.toInt()} bpm" } ?: "Sync HR",
                            onClick = { onOpenModuleKey("connected_devices") }
                        )
                        HomeMiniMetricChip(
                            emoji = "💊",
                            label = "Meds",
                            value = if (expectedDoses > 0) "$takenDoses/$expectedDoses" else "${activeMeds.size} meds",
                            onClick = { onOpenModuleKey("medical_care") }
                        )
                        HomeMiniMetricChip(
                            emoji = "🥗",
                            label = "Calories",
                            value = "$totalCalories kcal",
                            onClick = { onOpenModuleKey("diet_tracker") }
                        )
                        HomeMiniMetricChip(
                            emoji = "😴",
                            label = "Sleep",
                            value = latestSleepHrs?.let { String.format("%.1fh", it) } ?: "Log rest",
                            onClick = { onOpenModuleKey("exercise_tracker") }
                        )
                        val wb = modulesState.todayWellbeing
                        val wbMins = wb?.screenTimeMinutes ?: 0
                        val wbCap = wb?.mindfulGoalMinutes ?: 120
                        val wbSeverity = modulesState.wellbeingOveruseAlert?.severity ?: OveruseSeverity.BALANCED
                        val screenBadge = when (wbSeverity) {
                            OveruseSeverity.CRITICAL, OveruseSeverity.EXCEEDED -> "${wbMins}m ⚠️"
                            OveruseSeverity.APPROACHING -> "${wbMins}/${wbCap}m"
                            OveruseSeverity.BALANCED -> "${wbMins}m"
                        }
                        HomeMiniMetricChip(
                            emoji = "📵",
                            label = "Screen",
                            value = screenBadge,
                            onClick = { onOpenModuleKey("digital_wellbeing") }
                        )
                    }

                    val activeOveruse = modulesState.wellbeingOveruseAlert
                    val hasLongApps = modulesState.longUsedApps.isNotEmpty()
                    val showGestureAlert = !modulesState.isWellbeingAlertSnoozed &&
                        activeOveruse != null &&
                        (activeOveruse.severity != OveruseSeverity.BALANCED || hasLongApps)

                    if (!modulesState.hasUsageAccessPermission || !modulesState.hasNotificationPermission) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            if (!modulesState.hasUsageAccessPermission) {
                                QuickAmountChip(
                                    label = "🔓 Grant Screen Time Access",
                                    accentColor = MaterialTheme.colorScheme.primary
                                ) {
                                    onOpenUsageAccessSettings()
                                }
                            }
                            if (!modulesState.hasNotificationPermission) {
                                QuickAmountChip(
                                    label = "🔔 Enable Mobile Alerts",
                                    accentColor = MaterialTheme.colorScheme.error
                                ) {
                                    onRequestNotificationPermission()
                                }
                            }
                        }
                    }

                    if (showGestureAlert && activeOveruse != null) {
                        val isOverCap = activeOveruse.severity == OveruseSeverity.EXCEEDED ||
                            activeOveruse.severity == OveruseSeverity.CRITICAL
                        val haptic = LocalHapticFeedback.current
                        val coroutineScope = rememberCoroutineScope()
                        val swipeOffsetX = remember { Animatable(0f) }
                        var gestureBannerFeedback by remember { mutableStateOf<String?>(null) }

                        val infinitePulse = rememberInfiniteTransition(label = "overuse_pulse")
                        val pulseScale by infinitePulse.animateFloat(
                            initialValue = 1f,
                            targetValue = 1.08f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(750),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "pulse_scale"
                        )
                        val borderAlpha by infinitePulse.animateFloat(
                            initialValue = 0.35f,
                            targetValue = 0.95f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(900),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "border_alpha"
                        )

                        val containerColor = when {
                            swipeOffsetX.value > 60f -> FertileLeafGreen.copy(alpha = 0.24f)
                            swipeOffsetX.value < -60f -> MaterialTheme.colorScheme.surfaceVariant
                            isOverCap -> MaterialTheme.colorScheme.errorContainer
                            else -> AmberNotice.copy(alpha = 0.24f)
                        }
                        val accentBorder = if (isOverCap) {
                            MaterialTheme.colorScheme.error.copy(alpha = borderAlpha)
                        } else {
                            AmberNotice.copy(alpha = borderAlpha)
                        }

                        Surface(
                            modifier = Modifier
                                .fillMaxWidth()
                                .offset { IntOffset(swipeOffsetX.value.roundToInt(), 0) }
                                .pointerInput(activeOveruse.stageKey) {
                                    detectHorizontalDragGestures(
                                        onDragEnd = {
                                            coroutineScope.launch {
                                                when {
                                                    swipeOffsetX.value > 140f -> {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        onStartFocusBreak()
                                                        gestureBannerFeedback = "🎯 Focus Break logged! Rest your eyes."
                                                        swipeOffsetX.animateTo(0f, spring())
                                                    }
                                                    swipeOffsetX.value < -140f -> {
                                                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                                        onSnoozeWellbeingAlert()
                                                        swipeOffsetX.animateTo(0f, spring())
                                                    }
                                                    else -> swipeOffsetX.animateTo(0f, spring())
                                                }
                                            }
                                        },
                                        onDragCancel = {
                                            coroutineScope.launch { swipeOffsetX.animateTo(0f, spring()) }
                                        }
                                    ) { change, dragAmount ->
                                        change.consume()
                                        coroutineScope.launch {
                                            val next = (swipeOffsetX.value + dragAmount).coerceIn(-260f, 260f)
                                            swipeOffsetX.snapTo(next)
                                        }
                                    }
                                }
                                .pointerInput(activeOveruse.stageKey) {
                                    detectTapGestures(
                                        onLongPress = {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            onStartFocusBreak()
                                            gestureBannerFeedback = "✋ Long-press recognized • Focus Break +1!"
                                        },
                                        onTap = { onOpenModuleKey("digital_wellbeing") }
                                    )
                                }
                                .testTag("home_wellbeing_overuse_alert"),
                            shape = RoundedCornerShape(16.dp),
                            color = containerColor,
                            border = BorderStroke(1.5.dp, accentBorder)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = if (isOverCap) "🚨" else "⚠️",
                                            fontSize = 20.sp,
                                            modifier = Modifier.graphicsLayer {
                                                scaleX = pulseScale
                                                scaleY = pulseScale
                                            }
                                        )
                                        Text(
                                            text = activeOveruse.title,
                                            style = MaterialTheme.typography.titleSmall,
                                            color = if (isOverCap) {
                                                MaterialTheme.colorScheme.onErrorContainer
                                            } else {
                                                MaterialTheme.colorScheme.onSurface
                                            }
                                        )
                                    }
                                    TextButton(onClick = { onOpenModuleKey("digital_wellbeing") }) {
                                        Text("Apps →")
                                    }
                                }

                                Text(
                                    text = gestureBannerFeedback ?: activeOveruse.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isOverCap) {
                                        MaterialTheme.colorScheme.onErrorContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )

                                if (modulesState.longUsedApps.isNotEmpty()) {
                                    FlowRow(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        modulesState.longUsedApps.take(3).forEach { app ->
                                            Surface(
                                                shape = CircleShape,
                                                color = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.clickable { onOpenModuleKey("digital_wellbeing") }
                                            ) {
                                                Text(
                                                    text = "🔥 ${app.appName}: ${app.minutesUsed}m",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = MaterialTheme.colorScheme.onError,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                )
                                            }
                                        }
                                    }
                                }

                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
                                ) {
                                    Text(
                                        text = when {
                                            swipeOffsetX.value > 60f -> "🎯 Release right to log Focus Break"
                                            swipeOffsetX.value < -60f -> "😴 Release left to snooze 30m"
                                            else -> "↔️ Swipe Left: Snooze 30m • Swipe Right / Hold: Focus Break • Tap: Apps"
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Playful Mood / Vibe Check-In Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "🎭 Daily Vibe Check-In",
                        style = MaterialTheme.typography.titleSmall
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "🌿 Balanced",
                            "⚡ Energized",
                            "😌 Calm",
                            "😴 Sleepy",
                            "💆 Self-Care"
                        ).forEach { vibe ->
                            FilterChip(
                                selected = dailyVibe == vibe,
                                onClick = { dailyVibe = vibe },
                                label = { Text(vibe) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                                )
                            )
                        }
                    }
                }
            }
        }

        // Colorful 2x2 Spotlight Grid (Water, Meds, Doctors, Fitness)
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SpotlightEmojiCard(
                        emoji = if (uiState.hydration.remainingMl == 0) "🐳" else "💧",
                        title = "Water",
                        metric = "${uiState.hydration.totalTodayMl} ml",
                        subtitle = if (uiState.hydration.remainingMl == 0) {
                            "🎉 Goal reached!"
                        } else {
                            "${uiState.hydration.remainingMl} ml to go"
                        },
                        tint = MaterialTheme.colorScheme.secondaryContainer,
                        accentColor = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_spotlight_water"),
                        onClick = onOpenWaterTracker
                    )
                    SpotlightEmojiCard(
                        emoji = "💊",
                        title = "Meds",
                        metric = if (expectedDoses > 0) "$takenDoses/$expectedDoses" else "${activeMeds.size} active",
                        subtitle = if (expectedDoses > 0) "doses today" else "Tap to add meds",
                        tint = MaterialTheme.colorScheme.primaryContainer,
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_spotlight_meds"),
                        onClick = { onOpenModuleKey("medical_care") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    val doctorMetric = nextFollowUp?.followUpEpochDay?.let { fDay ->
                        val diff = fDay - todayEpochDay
                        when {
                            diff < 0 -> "⚠️ ${-diff}d late"
                            diff == 0L -> "🔔 Today"
                            else -> "📅 In ${diff}d"
                        }
                    } ?: "${modulesState.consultations.size} visits"
                    val doctorSub = nextFollowUp?.let { "Dr. ${it.doctorName}" } ?: "Tap to log visit"

                    SpotlightEmojiCard(
                        emoji = "🩺",
                        title = "Doctors",
                        metric = doctorMetric,
                        subtitle = doctorSub,
                        tint = MaterialTheme.colorScheme.tertiaryContainer,
                        accentColor = MaterialTheme.colorScheme.tertiary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_spotlight_doctors"),
                        onClick = { onOpenModuleKey("medical_care") }
                    )
                    SpotlightEmojiCard(
                        emoji = "🏃",
                        title = "Movement",
                        metric = "$totalSteps steps",
                        subtitle = "$activeMinutes min active",
                        tint = AmberNotice.copy(alpha = 0.22f),
                        accentColor = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("home_spotlight_fitness"),
                        onClick = { onOpenModuleKey("exercise_tracker") }
                    )
                }
            }
        }

        // Hydration Quick-Log Hero Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .clickable(onClick = onOpenWaterTracker)
                    .testTag("home_hydration_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EmojiIconTile(
                                emoji = if (uiState.hydration.remainingMl == 0) "🐳" else "💧",
                                backgroundColor = MaterialTheme.colorScheme.secondaryContainer
                            )
                            Column {
                                Text(
                                    text = if (uiState.hydration.remainingMl == 0) {
                                        "Hydration Goal Crushed! 🎉"
                                    } else {
                                        "Daily Hydration"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "🔥 ${uiState.hydration.streakDays}-day streak",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "${uiState.hydration.totalTodayMl} / ${uiState.hydration.goalMl} ml",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoMetricFont
                            ),
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { uiState.hydration.progressFraction.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape),
                        color = MaterialTheme.colorScheme.secondary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        QuickAmountChip(
                            label = "💧 +150 ml",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_quick_150ml"),
                            onClick = { onQuickAddWater(150) }
                        )
                        QuickAmountChip(
                            label = "🥤 +250 ml",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_quick_250ml"),
                            onClick = { onQuickAddWater(250) }
                        )
                        QuickAmountChip(
                            label = "🫗 +500 ml",
                            modifier = Modifier
                                .weight(1f)
                                .testTag("home_quick_500ml"),
                            onClick = { onQuickAddWater(500) }
                        )
                    }
                }
            }
        }

        // Cycle Care Hero Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
                    .clickable(
                        onClick = {
                            if (profile?.cycleTrackingEnabled == true) {
                                onOpenPeriodTracker()
                            } else {
                                onEnableCycleTracking()
                            }
                        }
                    )
                    .testTag("home_cycle_card"),
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.55f),
                                    MaterialTheme.colorScheme.surface
                                )
                            )
                        )
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            EmojiIconTile(
                                emoji = "🌸",
                                backgroundColor = MaterialTheme.colorScheme.tertiaryContainer
                            )
                            Column {
                                Text(
                                    text = "Cycle & Period Care",
                                    style = MaterialTheme.typography.titleMedium
                                )
                                Text(
                                    text = "Always Free • Private Local Log",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        ModuleStatusPill(
                            isAlwaysFree = true,
                            isActivated = true,
                            isPhaseOneBuiltIn = true
                        )
                    }

                    if (profile?.cycleTrackingEnabled == true) {
                        val insights = uiState.cycleInsights
                        if (insights?.currentCycleDay != null) {
                            val phaseEmoji = when {
                                insights.currentPhase.label.contains("Menstrual", ignoreCase = true) -> "🌺"
                                insights.currentPhase.label.contains("Follicular", ignoreCase = true) -> "🌱"
                                insights.currentPhase.label.contains("Ovul", ignoreCase = true) -> "✨"
                                else -> "🌙"
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = "$phaseEmoji Day ${insights.currentCycleDay} • ${insights.currentPhase.label}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                                insights.nextPredictedStartEpochDay?.let { nextDay ->
                                    Text(
                                        text = "📅 Next: ${DateEpochUtils.formatEpochDayShort(nextDay)}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = insights.currentPhase.guidance,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        } else {
                            Text(
                                text = "🌸 Tap to log your first period date for private on-device predictions.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Cycle tracking is hidden in your profile preferences.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(onClick = onEnableCycleTracking) {
                                Text("Enable 🌸")
                            }
                        }
                    }
                }
            }
        }

        // Complete Colorful Emoji Category Directory (Every Wellness Category)
        item {
            val categoryVisuals = listOf(
                HomeCategoryVisual(
                    key = "water_tracker",
                    emoji = "💧",
                    title = "Water & Hydration",
                    subtitle = "${uiState.hydration.totalTodayMl}/${uiState.hydration.goalMl} ml • ${uiState.hydration.streakDays}d streak",
                    badgeBg = MaterialTheme.colorScheme.secondaryContainer,
                    cardTint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                ),
                HomeCategoryVisual(
                    key = "period_tracker",
                    emoji = "🌸",
                    title = "Cycle & Period Care",
                    subtitle = uiState.cycleInsights?.currentCycleDay?.let { "Cycle Day $it • ${uiState.cycleInsights.currentPhase.label}" }
                        ?: "Inclusive period, flow & symptom logs",
                    badgeBg = MaterialTheme.colorScheme.tertiaryContainer,
                    cardTint = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.25f)
                ),
                HomeCategoryVisual(
                    key = "medical_care",
                    emoji = "💊",
                    title = "Medicines & Daily Doses",
                    subtitle = if (expectedDoses > 0) "$takenDoses/$expectedDoses doses taken today • Interval tracker" else "Track doses, intervals & meal timing",
                    badgeBg = MaterialTheme.colorScheme.primaryContainer,
                    cardTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ),
                HomeCategoryVisual(
                    key = "medical_care",
                    emoji = "🩺",
                    title = "Doctors & Follow-Ups",
                    subtitle = nextFollowUp?.let { "Next: Dr. ${it.doctorName} (${DateEpochUtils.formatEpochDayShort(it.followUpEpochDay ?: todayEpochDay)})" }
                        ?: "${modulesState.consultations.size} consultation notes saved",
                    badgeBg = MaterialTheme.colorScheme.tertiaryContainer,
                    cardTint = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.22f)
                ),
                HomeCategoryVisual(
                    key = "exercise_tracker",
                    emoji = "🏃",
                    title = "Fitness & Steps",
                    subtitle = "$totalSteps steps • $activeMinutes min workouts",
                    badgeBg = AmberNotice.copy(alpha = 0.24f),
                    cardTint = AmberNotice.copy(alpha = 0.12f)
                ),
                HomeCategoryVisual(
                    key = "yoga_tracker",
                    emoji = "🧘",
                    title = "Yoga, Breath & Sleep",
                    subtitle = "${modulesState.sleepRecords.size} sleep logs • Restorative flows",
                    badgeBg = FertileLeafGreen.copy(alpha = 0.24f),
                    cardTint = FertileLeafGreen.copy(alpha = 0.12f)
                ),
                HomeCategoryVisual(
                    key = "diet_tracker",
                    emoji = "🥗",
                    title = "Diet & Nutrition",
                    subtitle = "$totalCalories kcal across ${modulesState.dietRecords.size} meal(s)",
                    badgeBg = MaterialTheme.colorScheme.primaryContainer,
                    cardTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f)
                ),
                HomeCategoryVisual(
                    key = "beauty_care",
                    emoji = "✨",
                    title = "Beauty & Personal Care",
                    subtitle = "${modulesState.beautyRecords.size} skincare & haircare rituals",
                    badgeBg = MaterialTheme.colorScheme.tertiaryContainer,
                    cardTint = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.20f)
                ),
                HomeCategoryVisual(
                    key = "digital_wellbeing",
                    emoji = "📵",
                    title = "Digital Wellbeing",
                    subtitle = run {
                        val wb = modulesState.todayWellbeing
                        val mins = wb?.screenTimeMinutes ?: 0
                        val unlocks = wb?.unlockCount ?: 0
                        val top = wb?.topAppName?.takeIf { it.isNotBlank() }
                        when {
                            !modulesState.hasUsageAccessPermission && mins == 0 ->
                                "Tap to connect Android Usage Access"
                            top != null ->
                                "${mins}m screen • $unlocks unlocks • Top: $top"
                            else ->
                                "${mins}m screen • ${wb?.focusSessionsCount ?: 0} focus blocks"
                        }
                    },
                    badgeBg = MaterialTheme.colorScheme.secondaryContainer,
                    cardTint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.20f)
                ),
                HomeCategoryVisual(
                    key = "daily_reminders",
                    emoji = "⏰",
                    title = "Custom Alarms & Alert Tones",
                    subtitle = "Dedicated category tones & local audio files",
                    badgeBg = AmberNotice.copy(alpha = 0.22f),
                    cardTint = AmberNotice.copy(alpha = 0.10f)
                ),
                HomeCategoryVisual(
                    key = "reports_analytics",
                    emoji = "📊",
                    title = "Reports & Analytics",
                    subtitle = "Weekly streaks, doses & wellness trends",
                    badgeBg = MaterialTheme.colorScheme.secondaryContainer,
                    cardTint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.22f)
                ),
                HomeCategoryVisual(
                    key = "connected_devices",
                    emoji = "⌚",
                    title = "Connected Apps & Devices",
                    subtitle = if (modulesState.connectedSources.isNotEmpty()) {
                        val hr = modulesState.vitalMetrics.firstOrNull { it.metricType == "HEART_RATE" }
                        "${modulesState.connectedSources.size} synced source(s)" + (hr?.let { " • ❤️ ${it.valuePrimary.toInt()} bpm" } ?: "")
                    } else {
                        "Sync Fitbit, Google Fit, Samsung, Garmin & Apple Health"
                    },
                    badgeBg = MaterialTheme.colorScheme.secondaryContainer,
                    cardTint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.25f)
                ),
                HomeCategoryVisual(
                    key = "on_device_ai",
                    emoji = "🎙️",
                    title = "AI Voice Guide & Rx Scanner",
                    subtitle = "Speak in any language, scan prescriptions, log coffee/water",
                    badgeBg = MaterialTheme.colorScheme.primaryContainer,
                    cardTint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                )
            )

            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "🌈 All Wellness Categories",
                        style = MaterialTheme.typography.titleLarge
                    )
                    TextButton(onClick = onOpenModulesHub) {
                        Text("Grid View 🎛️")
                    }
                }

                categoryVisuals.forEach { cat ->
                    ColorfulCategoryRow(
                        visual = cat,
                        onClick = { onOpenModuleKey(cat.key) }
                    )
                }

                Spacer(modifier = Modifier.height(18.dp))
            }
        }
    }
}

@Composable
private fun SpotlightEmojiCard(
    emoji: String,
    title: String,
    metric: String,
    subtitle: String,
    tint: Color,
    accentColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            tint.copy(alpha = 0.65f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiIconTile(emoji = emoji, backgroundColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f))
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelLarge,
                    color = accentColor
                )
            }
            Text(
                text = metric,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoMetricFont,
                    fontWeight = FontWeight.Bold
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmojiIconTile(
    emoji: String,
    backgroundColor: Color
) {
    Box(
        modifier = Modifier
            .size(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        Text(text = emoji, fontSize = 22.sp)
    }
}

@Composable
private fun ColorfulCategoryRow(
    visual: HomeCategoryVisual,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .testTag("home_category_${visual.key}_${visual.emoji}"),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Row(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            visual.cardTint,
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .padding(end = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                EmojiIconTile(
                    emoji = visual.emoji,
                    backgroundColor = visual.badgeBg
                )
                Column {
                    Text(
                        text = visual.title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = visual.subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            ModuleStatusPill(
                isAlwaysFree = true,
                isActivated = true,
                isPhaseOneBuiltIn = true
            )
        }
    }
}

@Composable
private fun HomeMiniMetricChip(
    emoji: String,
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(text = emoji, fontSize = 16.sp)
            Column {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoMetricFont
                    )
                )
            }
        }
    }
}
