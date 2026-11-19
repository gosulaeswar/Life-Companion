package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.local.entity.MoodJournalEntryEntity
import com.example.domain.DateEpochUtils
import com.example.domain.MoodJournalAnalytics
import com.example.domain.MoodPresetOption
import com.example.domain.RoutineAdherenceSummary
import com.example.domain.RoutineMoodAnalyticsEngine
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.theme.VioletFocus

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeRoutineMoodCard(
    adherence: RoutineAdherenceSummary,
    moodAnalytics: MoodJournalAnalytics,
    latestMood: MoodJournalEntryEntity?,
    activeChallengesCount: Int,
    earnedBadgesCount: Int,
    totalBadgesCount: Int,
    onQuickMoodTap: (MoodPresetOption) -> Unit,
    onOpenRoutines: () -> Unit,
    onOpenMoodJournal: () -> Unit,
    onOpenChallenges: () -> Unit,
    modifier: Modifier = Modifier
) {
    val todayKey = DateEpochUtils.todayDayKey()
    val loggedTodayMoodLabel = latestMood?.takeIf { it.dayKey == todayKey }?.moodLabel

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("home_routine_mood_card"),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "🌿 Rhythm, Mood & Milestones",
                        style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = if (loggedTodayMoodLabel != null) {
                            "Latest today: ${latestMood.moodEmoji} $loggedTodayMoodLabel • Tap any pill to check in"
                        } else {
                            "100% optional check-in — saves to your private journal"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                TextButton(onClick = onOpenMoodJournal) {
                    Text("Journal →")
                }
            }

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                RoutineMoodAnalyticsEngine.moodPresets.take(5).forEach { preset ->
                    val selected = loggedTodayMoodLabel == preset.label
                    FilterChip(
                        selected = selected,
                        onClick = { onQuickMoodTap(preset) },
                        label = { Text("${preset.emoji} ${preset.label}") },
                        modifier = Modifier.testTag("home_mood_chip_${preset.label}"),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Phase6QuickSummaryPill(
                    emoji = "🌅",
                    headline = if (adherence.activeRoutinesCount > 0) {
                        "${adherence.completedTodayCount}/${adherence.activeRoutinesCount}"
                    } else {
                        "Build"
                    },
                    label = "Routines",
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onClick = onOpenRoutines,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_pill_routines")
                )
                Phase6QuickSummaryPill(
                    emoji = latestMood?.moodEmoji ?: "📓",
                    headline = "${moodAnalytics.totalEntriesLast7Days} logs",
                    label = "Mood 7d",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.7f),
                    contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    onClick = onOpenMoodJournal,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_pill_mood")
                )
                Phase6QuickSummaryPill(
                    emoji = "🏅",
                    headline = "$earnedBadgesCount/${totalBadgesCount.coerceAtLeast(6)}",
                    label = if (activeChallengesCount > 0) "$activeChallengesCount active" else "Opt-in",
                    containerColor = VioletFocus.copy(alpha = 0.14f),
                    contentColor = VioletFocus,
                    onClick = onOpenChallenges,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("home_pill_badges")
                )
            }
        }
    }
}

@Composable
private fun Phase6QuickSummaryPill(
    emoji: String,
    headline: String,
    label: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = emoji, style = MaterialTheme.typography.titleSmall)
                Text(
                    text = headline,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontFamily = JetBrainsMonoMetricFont,
                        fontWeight = FontWeight.Bold
                    ),
                    color = contentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = contentColor.copy(alpha = 0.85f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
