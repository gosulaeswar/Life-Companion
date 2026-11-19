package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.PauseCircleOutline
import androidx.compose.material.icons.rounded.PlayCircleOutline
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.local.entity.AchievementBadgeEntity
import com.example.data.local.entity.DailyRoutineEntity
import com.example.data.local.entity.RoutineCompletionEntity
import com.example.data.local.entity.WellnessChallengeEntity
import com.example.domain.MoodJournalAnalytics
import com.example.domain.RoutineAdherenceSummary
import com.example.domain.RoutineMoodAnalyticsEngine
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.FertileLeafGreen
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.theme.VioletFocus
import java.util.Locale

@Composable
fun RoutineAdherenceRingCard(
    adherence: RoutineAdherenceSummary,
    modifier: Modifier = Modifier,
    title: String = "Today's Gentle Rhythm",
    subtitle: String = "Optional habits at your own pace — rest days are healthy too."
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("routine_adherence_ring_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CircularProgressRing(
                    progressFraction = adherence.todayFraction,
                    primaryColor = MaterialTheme.colorScheme.primary,
                    secondaryColor = MaterialTheme.colorScheme.secondary,
                    ringSize = 86.dp,
                    strokeWidth = 9.dp
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "${adherence.completedTodayCount}/${adherence.activeRoutinesCount}",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoMetricFont,
                                fontWeight = FontWeight.Bold
                            )
                        )
                        Text(
                            text = "done",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.8f)
                    ) {
                        Text(
                            text = "7-day check-ins: ${adherence.completionsLast7Days} (${adherence.weeklyConsistencyPercent}%)",
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (adherence.sevenDayRhythm.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    adherence.sevenDayRhythm.forEach { day ->
                        val hasDone = day.completedCount > 0
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(30.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            hasDone -> MaterialTheme.colorScheme.primary
                                            day.isToday -> MaterialTheme.colorScheme.primaryContainer
                                            else -> MaterialTheme.colorScheme.surfaceVariant
                                        }
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (hasDone) "${day.completedCount}" else "·",
                                    style = MaterialTheme.typography.labelSmall.copy(
                                        fontFamily = JetBrainsMonoMetricFont,
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = if (hasDone) {
                                        MaterialTheme.colorScheme.onPrimary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    }
                                )
                            }
                            Text(
                                text = day.shortLabel,
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = if (day.isToday) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (day.isToday) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MoodTrendChartCard(
    analytics: MoodJournalAnalytics,
    modifier: Modifier = Modifier
) {
    val moodLineColor = MaterialTheme.colorScheme.primary
    val energyLineColor = MaterialTheme.colorScheme.secondary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("mood_trend_chart_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
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
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "📈 7-Day Mood & Energy Rhythm",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = "${analytics.totalEntriesLast7Days} reflection(s) this week • ${analytics.totalEntriesAllTime} total",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                analytics.averageMood7d?.let { avg ->
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = "${"%.1f".format(Locale.US, avg)}/5 avg",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = JetBrainsMonoMetricFont,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            if (analytics.dailyTrend7d.any { it.averageMood != null }) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(110.dp)
                ) {
                    val points = analytics.dailyTrend7d
                    val count = points.size.coerceAtLeast(1)
                    val slotW = size.width / count
                    val topPad = 12.dp.toPx()
                    val botPad = 12.dp.toPx()
                    val usableH = (size.height - topPad - botPad).coerceAtLeast(20f)

                    listOf(0f, 0.5f, 1f).forEach { frac ->
                        val y = topPad + usableH * frac
                        drawLine(
                            color = gridColor.copy(alpha = 0.55f),
                            start = Offset(0f, y),
                            end = Offset(size.width, y),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    fun yForScore(score: Float): Float {
                        val norm = ((score - 1f) / 4f).coerceIn(0f, 1f)
                        return topPad + usableH * (1f - norm)
                    }

                    val moodPath = Path()
                    val energyPath = Path()
                    var moodStarted = false
                    var energyStarted = false

                    points.forEachIndexed { idx, pt ->
                        val cx = idx * slotW + slotW / 2f
                        pt.averageEnergy?.let { e ->
                            val ey = yForScore(e)
                            if (!energyStarted) {
                                energyPath.moveTo(cx, ey)
                                energyStarted = true
                            } else {
                                energyPath.lineTo(cx, ey)
                            }
                        }
                        pt.averageMood?.let { m ->
                            val my = yForScore(m)
                            if (!moodStarted) {
                                moodPath.moveTo(cx, my)
                                moodStarted = true
                            } else {
                                moodPath.lineTo(cx, my)
                            }
                        }
                    }

                    if (energyStarted) {
                        drawPath(
                            path = energyPath,
                            color = energyLineColor.copy(alpha = 0.7f),
                            style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }
                    if (moodStarted) {
                        drawPath(
                            path = moodPath,
                            color = moodLineColor,
                            style = Stroke(width = 3.5.dp.toPx(), cap = StrokeCap.Round)
                        )
                    }

                    points.forEachIndexed { idx, pt ->
                        val cx = idx * slotW + slotW / 2f
                        pt.averageEnergy?.let { e ->
                            drawCircle(
                                color = energyLineColor,
                                radius = 3.5.dp.toPx(),
                                center = Offset(cx, yForScore(e))
                            )
                        }
                        pt.averageMood?.let { m ->
                            drawCircle(
                                color = moodLineColor,
                                radius = 5.dp.toPx(),
                                center = Offset(cx, yForScore(m))
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    analytics.dailyTrend7d.forEach { pt ->
                        Text(
                            text = pt.shortLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("● Mood", style = MaterialTheme.typography.labelSmall, color = moodLineColor)
                    Text("● Energy", style = MaterialTheme.typography.labelSmall, color = energyLineColor)
                    analytics.averageStress7d?.let { s ->
                        Text(
                            "• Avg Stress: ${"%.1f".format(Locale.US, s)}/5",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (analytics.moodFrequency.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    analytics.moodFrequency.entries.take(5).forEach { (label, count) ->
                        val emoji = RoutineMoodAnalyticsEngine.moodPresets
                            .firstOrNull { it.label == label }?.emoji ?: "🌿"
                        Surface(
                            shape = CircleShape,
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                        ) {
                            Text(
                                text = "$emoji $label ×$count",
                                style = MaterialTheme.typography.labelSmall,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            analytics.reflectiveObservations.forEach { obs ->
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Text(
                        text = "💡 $obs",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DailyRoutineItemCard(
    routine: DailyRoutineEntity,
    todayCompletion: RoutineCompletionEntity?,
    onToggleWhole: () -> Unit,
    onToggleStep: (Int) -> Unit,
    onToggleReminder: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by rememberSaveable(routine.id) { mutableStateOf(false) }
    val steps = RoutineMoodAnalyticsEngine.parseSteps(routine.stepsSerialized)
        .ifEmpty { listOf("Complete gentle check-in") }
    val totalSteps = steps.size.coerceAtLeast(1)
    val mask = todayCompletion?.completedStepsMask ?: 0
    val doneCount = (todayCompletion?.completedStepsCount ?: 0).coerceIn(0, totalSteps)
    val isFullyDone = doneCount >= totalSteps
    val progress by animateFloatAsState(
        targetValue = doneCount.toFloat() / totalSteps.toFloat(),
        animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
        label = "routine_step_progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("routine_card_${routine.id}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isFullyDone) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isFullyDone) MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)
            else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onToggleWhole() }
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isFullyDone) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (isFullyDone) "Routine completed today" else "Mark routine done",
                        tint = if (isFullyDone) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                        modifier = Modifier.size(28.dp)
                    )
                    Column {
                        Text(
                            text = "${routine.emoji} ${routine.title}",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontWeight = FontWeight.Bold,
                                textDecoration = if (isFullyDone) TextDecoration.LineThrough else TextDecoration.None
                            ),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${routine.timeSlot} • ${routine.triggerTime24h} • ~${routine.estimatedMinutes}m • $doneCount/$totalSteps step(s)",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { onToggleReminder(!routine.reminderEnabled) },
                        modifier = Modifier.testTag("routine_reminder_toggle_${routine.id}")
                    ) {
                        Icon(
                            imageVector = if (routine.reminderEnabled) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                            contentDescription = if (routine.reminderEnabled) "Pause reminder" else "Enable reminder",
                            tint = if (routine.reminderEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = if (expanded) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore,
                            contentDescription = if (expanded) "Hide steps" else "Show steps"
                        )
                    }
                }
            }

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            AnimatedVisibility(visible = expanded) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    steps.forEachIndexed { idx, stepText ->
                        val checked = (mask and (1 shl idx)) != 0
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .clickable { onToggleStep(idx) }
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = checked,
                                onCheckedChange = { onToggleStep(idx) }
                            )
                            Text(
                                text = stepText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    textDecoration = if (checked) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (checked) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = onEdit) {
                            Icon(Icons.Rounded.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Customize")
                        }
                        TextButton(onClick = onDelete) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Remove")
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun OptionalChallengeCard(
    challenge: WellnessChallengeEntity,
    todayDayKey: String,
    onJoin: () -> Unit,
    onPause: () -> Unit,
    onCheckInToday: () -> Unit,
    onLeave: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isCheckedToday = challenge.lastCheckInDayKey == todayDayKey
    val progressFrac = (challenge.currentProgressDays.toFloat() / challenge.targetDays.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)
    val accent = when (challenge.status) {
        "COMPLETED" -> FertileLeafGreen
        "ACTIVE" -> MaterialTheme.colorScheme.primary
        "PAUSED" -> AmberNotice
        else -> MaterialTheme.colorScheme.outline
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("challenge_card_${challenge.challengeKey}"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.35f))
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
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = challenge.emoji, fontSize = 26.sp)
                    Column {
                        Text(
                            text = challenge.title,
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "${challenge.category} • ${challenge.currentProgressDays}/${challenge.targetDays} mindful days",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Surface(
                    shape = CircleShape,
                    color = accent.copy(alpha = 0.16f)
                ) {
                    val statusLabel = when (challenge.status) {
                        "COMPLETED" -> "Completed 🎉"
                        "ACTIVE" -> "Opted In"
                        "PAUSED" -> "Paused"
                        else -> "Optional"
                    }
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                        color = accent,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = challenge.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            LinearProgressIndicator(
                progress = { progressFrac },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .clip(CircleShape),
                color = accent,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (challenge.status) {
                    "AVAILABLE" -> {
                        Button(
                            onClick = onJoin,
                            modifier = Modifier.testTag("challenge_join_${challenge.challengeKey}")
                        ) {
                            Text("Join When Ready")
                        }
                    }
                    "ACTIVE" -> {
                        Button(
                            onClick = onCheckInToday,
                            enabled = !isCheckedToday,
                            modifier = Modifier.testTag("challenge_checkin_${challenge.challengeKey}")
                        ) {
                            Text(if (isCheckedToday) "Logged Today ✓" else "Check In Today")
                        }
                        OutlinedButton(onClick = onPause) {
                            Icon(Icons.Rounded.PauseCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Pause")
                        }
                        TextButton(onClick = onLeave) { Text("Leave") }
                    }
                    "PAUSED" -> {
                        Button(onClick = onJoin) {
                            Icon(Icons.Rounded.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Resume")
                        }
                        TextButton(onClick = onLeave) { Text("Leave") }
                    }
                    "COMPLETED" -> {
                        OutlinedButton(onClick = onLeave) { Text("Replay Anytime") }
                    }
                }
            }
        }
    }
}

@Composable
fun AchievementBadgeCard(
    badge: AchievementBadgeEntity,
    modifier: Modifier = Modifier
) {
    val isEarned = badge.earnedAtEpochMs != null
    val progressFrac = (badge.progressCurrent.toFloat() / badge.progressTarget.coerceAtLeast(1).toFloat()).coerceIn(0f, 1f)

    Card(
        modifier = modifier.testTag("badge_card_${badge.badgeKey}"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isEarned) {
                VioletFocus.copy(alpha = 0.12f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = BorderStroke(
            1.dp,
            if (isEarned) VioletFocus.copy(alpha = 0.45f) else MaterialTheme.colorScheme.outlineVariant
        )
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = badge.emoji, fontSize = 24.sp)
                Surface(
                    shape = CircleShape,
                    color = if (isEarned) VioletFocus.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        text = if (isEarned) "Earned ✓" else "${badge.progressCurrent}/${badge.progressTarget}",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = JetBrainsMonoMetricFont,
                            fontWeight = FontWeight.Bold
                        ),
                        color = if (isEarned) VioletFocus else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Text(
                text = badge.title,
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = badge.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            LinearProgressIndicator(
                progress = { progressFrac },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(CircleShape),
                color = if (isEarned) VioletFocus else MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }
}
