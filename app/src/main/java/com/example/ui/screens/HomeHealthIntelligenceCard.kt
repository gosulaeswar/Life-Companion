package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.domain.IntelligenceTimeframe
import com.example.domain.TrendDirection
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.FertileLeafGreen
import com.example.ui.theme.HeroForestDark
import com.example.ui.theme.HeroTealDeep
import com.example.ui.viewmodel.AiTaskStatus
import com.example.ui.viewmodel.ModulesHubUiState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeHealthIntelligenceCard(
    uiState: ModulesHubUiState,
    onSelectTimeframe: (IntelligenceTimeframe) -> Unit,
    onSynthesizeAi: (IntelligenceTimeframe) -> Unit,
    onSpeakBriefing: () -> Unit,
    onStopSpeaking: () -> Unit,
    onRunDiagnostics: () -> Unit,
    onOpenFullIntelligence: () -> Unit
) {
    val snapshot = uiState.healthIntelligence
    val homeTimeframe = if (uiState.selectedIntelligenceTimeframe == IntelligenceTimeframe.DAILY) {
        IntelligenceTimeframe.DAILY
    } else {
        IntelligenceTimeframe.WEEKLY
    }
    val cloudReport = uiState.generatedAiHealthReport?.takeIf { it.timeframe == homeTimeframe.name }
    val activeReport = cloudReport ?: snapshot.offlineReportFor(homeTimeframe)
    val isBusy = uiState.aiTaskStatus is AiTaskStatus.Loading || uiState.aiTaskStatus is AiTaskStatus.Streaming
    val cycleEnabled = uiState.profile?.cycleTrackingEnabled == true
    val maxModules = uiState.aiDataAuthorization.maxCount(cycleEnabled)

    Card(
        modifier = Modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .testTag("home_intel_hero_card"),
        shape = RoundedCornerShape(26.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(HeroForestDark, HeroTealDeep)
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "🧠 Personal Health Briefing",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Grounded in your authorized logs",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9DF7DF)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.25f))
                    ) {
                        Text(
                            text = "${snapshot.authorizedModuleCount}/$maxModules active",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        IntelligenceTimeframe.DAILY to "home_intel_tab_daily",
                        IntelligenceTimeframe.WEEKLY to "home_intel_tab_weekly"
                    ).forEach { (option, tag) ->
                        val selected = homeTimeframe == option
                        FilterChip(
                            selected = selected,
                            onClick = { onSelectTimeframe(option) },
                            label = { Text("${option.label} (${option.badgeText})") },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = Color(0xFF2CE5B5),
                                selectedLabelColor = Color(0xFF042920),
                                containerColor = Color.White.copy(alpha = 0.14f),
                                labelColor = Color.White
                            ),
                            border = FilterChipDefaults.filterChipBorder(
                                enabled = true,
                                selected = selected,
                                borderColor = Color.White.copy(alpha = 0.25f)
                            ),
                            modifier = Modifier.testTag(tag)
                        )
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.12f)
                    ) {
                        Text(
                            text = if (activeReport.isFromCloudAi) "✨ Cloud AI" else "📱 On-Device Facts",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = Color(0xFF9DF7DF),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when (val status = uiState.aiTaskStatus) {
                    is AiTaskStatus.Loading -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                Text(status.label, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    is AiTaskStatus.Streaming -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                        ) {
                            Text(
                                text = status.previewText.ifBlank { status.label },
                                style = MaterialTheme.typography.bodySmall,
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    is AiTaskStatus.Error -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.errorContainer
                        ) {
                            Column(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Showing grounded local briefing — cloud AI unavailable (${status.message})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    TextButton(
                                        onClick = { onSynthesizeAi(homeTimeframe) },
                                        modifier = Modifier.testTag("home_intel_retry_button")
                                    ) {
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
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                )

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "📋 RECORDED OBSERVATIONS",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = activeReport.recordedFactsSummary,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                activeReport.personalizedSuggestions.firstOrNull()?.let { topTip ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.36f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f))
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🌿 SUGGESTION",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = topTip,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (snapshot.trends.isNotEmpty()) {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        snapshot.trends.take(3).forEach { trend ->
                            val bg = when (trend.direction) {
                                TrendDirection.IMPROVED -> FertileLeafGreen.copy(alpha = 0.16f)
                                TrendDirection.DECLINED -> AmberNotice.copy(alpha = 0.22f)
                                TrendDirection.STEADY -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                                TrendDirection.INSUFFICIENT_DATA -> MaterialTheme.colorScheme.surfaceVariant
                            }
                            Surface(
                                shape = CircleShape,
                                color = bg,
                                modifier = Modifier
                                    .clip(CircleShape)
                                    .clickable(onClick = onOpenFullIntelligence)
                            ) {
                                Text(
                                    text = "${trend.emoji} ${trend.deltaBadge}",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }
                        }
                    }
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Button(
                        onClick = { onSynthesizeAi(homeTimeframe) },
                        enabled = !isBusy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier.testTag("home_intel_synthesize_button")
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Text(if (cloudReport != null) " Refresh AI" else " Synthesize AI")
                    }
                    OutlinedButton(
                        onClick = { if (uiState.isSpeakingVoice) onStopSpeaking() else onSpeakBriefing() },
                        modifier = Modifier.testTag("home_intel_speak_button")
                    ) {
                        Icon(
                            imageVector = if (uiState.isSpeakingVoice) Icons.Rounded.Stop else Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = if (uiState.isSpeakingVoice) "Stop speaking" else "Listen to briefing",
                            modifier = Modifier.size(16.dp)
                        )
                        Text(if (uiState.isSpeakingVoice) " Stop" else " Listen")
                    }
                    TextButton(
                        onClick = onOpenFullIntelligence,
                        modifier = Modifier.testTag("home_intel_open_full_button")
                    ) {
                        Text("Trends & Privacy →")
                    }
                }

                Text(
                    text = "ℹ️ ${activeReport.disclaimer}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }
        }
    }
}
