package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ai.AiHealthIntelligenceReport
import com.example.domain.DailyWellnessPlanItem
import com.example.domain.IntelligenceTimeframe
import com.example.domain.PersonalHealthIntelligenceSnapshot
import com.example.domain.RoutineGapItem
import com.example.domain.TrendDirection
import com.example.domain.TrendMetricComparison
import com.example.security.AiDataAuthorization
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.FertileLeafGreen
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.theme.SolarOrangePrimary
import com.example.ui.theme.SpatialGrokAccent
import com.example.ui.theme.SpatialHeroBlueDeep
import com.example.ui.theme.SpatialHeroCobalt
import com.example.ui.theme.SpatialHeroOrangeGlow
import com.example.ui.viewmodel.AiTaskStatus
import com.example.ui.viewmodel.ModulesHubUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PersonalHealthIntelligenceCard(
    uiState: ModulesHubUiState,
    onSelectTimeframe: (IntelligenceTimeframe) -> Unit,
    onGenerateAiInsights: (IntelligenceTimeframe) -> Unit,
    onClearAiReport: () -> Unit,
    onSpeakSummary: () -> Unit,
    onStopSpeaking: () -> Unit,
    onRunDiagnostics: () -> Unit
) {
    val snapshot = uiState.healthIntelligence
    val timeframe = uiState.selectedIntelligenceTimeframe
    val cloudReport: AiHealthIntelligenceReport? = uiState.generatedAiHealthReport
        ?.takeIf { it.timeframe == timeframe.name }
    val activeReport = cloudReport ?: snapshot.offlineReportFor(timeframe)
    val isBusy = uiState.aiTaskStatus is AiTaskStatus.Loading || uiState.aiTaskStatus is AiTaskStatus.Streaming
    val cycleEnabled = uiState.profile?.cycleTrackingEnabled == true
    val maxModules = uiState.aiDataAuthorization.maxCount(cycleEnabled)

    Card(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .testTag("intel_hero_card"),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Spatial Cobalt & Solar Orange Studio Header
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                SpatialHeroBlueDeep,
                                SpatialHeroCobalt,
                                SpatialHeroOrangeGlow.copy(alpha = 0.65f)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🧠 Personal Health Intelligence",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Grounded in your recorded wellness history",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFFDFC0)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.28f))
                    ) {
                        Text(
                            text = "${snapshot.authorizedModuleCount}/$maxModules active",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                // Timeframe Selector Chips (Daily / Weekly / Monthly)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    IntelligenceTimeframe.entries.forEach { option ->
                        val selected = timeframe == option
                        val tag = when (option) {
                            IntelligenceTimeframe.DAILY -> "intel_tab_daily"
                            IntelligenceTimeframe.WEEKLY -> "intel_tab_weekly"
                            IntelligenceTimeframe.MONTHLY -> "intel_tab_monthly"
                        }
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectTimeframe(option) },
                            label = { Text("${option.label} (${option.badgeText})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SolarOrangePrimary,
                                selectedLabelColor = Color.White,
                                containerColor = Color.White.copy(alpha = 0.14f),
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = Color.White.copy(alpha = 0.28f)
                            ),
                            modifier = Modifier.testTag(tag)
                        )
                    }
                }

                // Action Buttons Row
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onGenerateAiInsights(timeframe) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SpatialGrokAccent,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.testTag("intel_generate_ai_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.AutoAwesome,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = if (cloudReport != null) " Refresh Grok Synthesis" else " Synthesize with Grok",
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    OutlinedButton(
                        onClick = {
                            if (uiState.isSpeakingVoice) onStopSpeaking() else onSpeakSummary()
                        },
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.45f)),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier.testTag("intel_speak_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isSpeakingVoice) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (uiState.isSpeakingVoice) "Stop speaking" else "Listen to briefing",
                            modifier = Modifier.size(18.dp)
                        )
                        Text(if (uiState.isSpeakingVoice) " Stop" else " Listen")
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Provenance Pill + Reset
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = CircleShape,
                        color = if (activeReport.isFromCloudAi) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                        }
                    ) {
                        Text(
                            text = if (activeReport.isFromCloudAi) "✨ Grok Cloud Synthesis" else "📱 On-Device Grounded Analysis",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = if (activeReport.isFromCloudAi) {
                                MaterialTheme.colorScheme.onPrimaryContainer
                            } else {
                                MaterialTheme.colorScheme.onSecondaryContainer
                            },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                    if (cloudReport != null) {
                        TextButton(onClick = onClearAiReport) {
                            Text("View Local Facts")
                        }
                    }
                }

                // AI Streaming / Error State Feedback
                when (val status = uiState.aiTaskStatus) {
                    is AiTaskStatus.Loading -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                Text(status.label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    is AiTaskStatus.Streaming -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                    Text(status.label, style = MaterialTheme.typography.labelMedium)
                                }
                                if (status.previewText.isNotBlank()) {
                                    Text(status.previewText, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    is AiTaskStatus.Error -> {
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = status.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    TextButton(onClick = { onGenerateAiInsights(timeframe) }) {
                                        Text("🔄 Retry")
                                    }
                                    TextButton(onClick = onRunDiagnostics) {
                                        Text("🩺 Diagnose")
                                    }
                                }
                            }
                        }
                    }
                    AiTaskStatus.Idle -> Unit
                }

                Text(
                    text = activeReport.headline,
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                // Recorded Observations Box (Strictly separated from suggestions)
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "📋 RECORDED OBSERVATIONS",
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = activeReport.recordedFactsSummary,
                            style = MaterialTheme.typography.bodySmall
                        )
                        if (activeReport.observedChanges.isNotEmpty()) {
                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 2.dp),
                                color = MaterialTheme.colorScheme.outlineVariant
                            )
                            activeReport.observedChanges.forEach { line ->
                                Text(
                                    text = "• $line",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Wellness Suggestions Box (Distinct visual layer)
                if (activeReport.personalizedSuggestions.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.22f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "🌿 PERSONALIZED WELLNESS SUGGESTIONS",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            activeReport.personalizedSuggestions.forEach { tip ->
                                Text(
                                    text = "• $tip",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }
                    }
                }

                // Coverage, Uncertainty & Non-Diagnostic Notice
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "🔎 Data Coverage: ${activeReport.uncertaintyAndCoverageNote}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "ℹ️ ${activeReport.disclaimer}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun TrendComparisonsSection(
    trends: List<TrendMetricComparison>,
    onOpenModuleKey: (String) -> Unit
) {
    if (trends.isEmpty()) return
    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = "📈 7-Day Trend Comparisons",
            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
        )
        trends.forEach { trend ->
            val directionBg = when (trend.direction) {
                TrendDirection.IMPROVED -> FertileLeafGreen.copy(alpha = 0.16f)
                TrendDirection.DECLINED -> AmberNotice.copy(alpha = 0.22f)
                TrendDirection.STEADY -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                TrendDirection.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.surfaceVariant
            }
            val directionFg = when (trend.direction) {
                TrendDirection.IMPROVED -> FertileLeafGreen
                TrendDirection.DECLINED -> AmberNotice
                TrendDirection.STEADY -> MaterialTheme.colorScheme.secondary
                TrendDirection.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.onSurfaceVariant
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(20.dp))
                    .clickable { onOpenModuleKey(trend.moduleKey) }
                    .testTag("intel_trend_card_${trend.id}"),
                shape = RoundedCornerShape(20.dp),
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
                        Text(
                            text = "${trend.emoji} ${trend.title}",
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        )
                        Surface(shape = CircleShape, color = directionBg) {
                            Text(
                                text = "${trend.direction.emoji} ${trend.deltaBadge}",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                color = directionFg,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = trend.currentValueLabel,
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoMetricFont,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "Prior: ${trend.previousValueLabel}",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = trend.provenanceNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun RoutineGapsAndPlanSection(
    snapshot: PersonalHealthIntelligenceSnapshot,
    onOpenModuleKey: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (snapshot.routineGaps.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🔎 Missed Routines & Attention Items",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                snapshot.routineGaps.forEach { gap ->
                    RoutineGapCard(gap = gap, onOpenModuleKey = onOpenModuleKey)
                }
            }
        }

        if (snapshot.dailyPlan.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "🗓️ Personalized Daily Wellness Plan",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
                snapshot.dailyPlan.forEach { item ->
                    DailyWellnessPlanRow(item = item, onOpenModuleKey = onOpenModuleKey)
                }
            }
        }
    }
}

@Composable
private fun RoutineGapCard(
    gap: RoutineGapItem,
    onOpenModuleKey: (String) -> Unit
) {
    val accent = if (gap.priority == 1) AmberNotice else MaterialTheme.colorScheme.primary
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("intel_gap_card_${gap.id}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
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
                    text = "${gap.emoji} ${gap.title}",
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { onOpenModuleKey(gap.moduleKey) }) {
                    Text("Open →")
                }
            }
            Text(
                text = "Fact: ${gap.recordedFact}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Suggestion: ${gap.wellnessSuggestion}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun DailyWellnessPlanRow(
    item: DailyWellnessPlanItem,
    onOpenModuleKey: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .clickable { onOpenModuleKey(item.moduleKey) },
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(text = item.emoji, fontSize = 20.sp)
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.title,
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f)
                    ) {
                        Text(
                            text = item.timeBlock,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
                Text(
                    text = item.actionSuggestion,
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = item.recordedBasis,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
fun AiPrivacyControlsCard(
    authorization: AiDataAuthorization,
    cycleTrackingEnabled: Boolean,
    onToggleModuleSharing: (String, Boolean) -> Unit
) {
    Card(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .testTag("intel_privacy_controls_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Lock,
                    contentDescription = "AI data privacy",
                    tint = MaterialTheme.colorScheme.primary
                )
                Column {
                    Text(
                        text = "AI Data Authorization",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "Choose which modules Personal Health Intelligence & AI Voice can reference.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            AiPrivacySwitchRow(
                label = "💧 Hydration & Water Logs",
                checked = authorization.includeHydration,
                testTag = "ai_privacy_switch_hydration",
                onCheckedChange = { onToggleModuleSharing(AiDataAuthorization.MODULE_HYDRATION, it) }
            )
            AiPrivacySwitchRow(
                label = "😴 Sleep & Bedtime Consistency",
                checked = authorization.includeSleep,
                testTag = "ai_privacy_switch_sleep",
                onCheckedChange = { onToggleModuleSharing(AiDataAuthorization.MODULE_SLEEP, it) }
            )
            AiPrivacySwitchRow(
                label = "🏃 Workouts, Steps & Watch Vitals",
                checked = authorization.includeFitnessAndVitals,
                testTag = "ai_privacy_switch_fitness",
                onCheckedChange = { onToggleModuleSharing(AiDataAuthorization.MODULE_FITNESS, it) }
            )
            AiPrivacySwitchRow(
                label = "🥗 Nutrition & Meals",
                checked = authorization.includeNutrition,
                testTag = "ai_privacy_switch_nutrition",
                onCheckedChange = { onToggleModuleSharing(AiDataAuthorization.MODULE_NUTRITION, it) }
            )
            AiPrivacySwitchRow(
                label = "💊 Medicines & Doctor Follow-Ups",
                checked = authorization.includeMedicinesAndVisits,
                testTag = "ai_privacy_switch_medicines",
                onCheckedChange = { onToggleModuleSharing(AiDataAuthorization.MODULE_MEDICINES, it) }
            )
            AiPrivacySwitchRow(
                label = "📵 Screen Time & Self-Care",
                checked = authorization.includeWellbeingAndSelfCare,
                testTag = "ai_privacy_switch_wellbeing",
                onCheckedChange = { onToggleModuleSharing(AiDataAuthorization.MODULE_WELLBEING, it) }
            )
            if (cycleTrackingEnabled) {
                AiPrivacySwitchRow(
                    label = "🌸 Cycle & Period Insights",
                    checked = authorization.includeCycle,
                    testTag = "ai_privacy_switch_cycle",
                    onCheckedChange = { onToggleModuleSharing(AiDataAuthorization.MODULE_CYCLE, it) }
                )
            }
        }
    }
}

@Composable
private fun AiPrivacySwitchRow(
    label: String,
    checked: Boolean,
    testTag: String,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag)
        )
    }
}
