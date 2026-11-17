package com.example.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.domain.DailyHydrationBar
import com.example.ui.theme.JetBrainsMonoMetricFont
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

@Composable
fun PrivacyVaultBadge(
    text: String = "100% On-Device • Zero Cloud",
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Rounded.VerifiedUser,
                contentDescription = "Local privacy verified",
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
fun CircularProgressRing(
    progressFraction: Float,
    primaryColor: Color,
    secondaryColor: Color,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    ringSize: Dp = 84.dp,
    strokeWidth: Dp = 10.dp,
    modifier: Modifier = Modifier,
    centerContent: @Composable () -> Unit
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "circular_metric_ring"
    )

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val strokePx = strokeWidth.toPx()
            val inset = strokePx / 2f
            val arcSize = Size(size.width - strokePx, size.height - strokePx)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            if (animatedProgress > 0.005f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor, secondaryColor, primaryColor)
                    ),
                    startAngle = -90f,
                    sweepAngle = 360f * animatedProgress,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )
            }
        }
        centerContent()
    }
}

@Composable
fun BentoMetricTile(
    emoji: String,
    label: String,
    value: String,
    accentColor: Color,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.22f))
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.11f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(accentColor.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 16.sp)
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall.copy(
                    fontFamily = JetBrainsMonoMetricFont,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = accentColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun HydrationRingGauge(
    currentMl: Int,
    goalMl: Int,
    progressFraction: Float,
    modifier: Modifier = Modifier,
    ringSize: Dp = 196.dp
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progressFraction.coerceIn(0f, 1f),
        animationSpec = spring(stiffness = Spring.StiffnessLow),
        label = "hydration_ring_progress"
    )
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val primaryColor = MaterialTheme.colorScheme.secondary
    val accentColor = MaterialTheme.colorScheme.primary
    val glowColor = primaryColor.copy(alpha = 0.14f)

    Box(
        modifier = modifier.size(ringSize),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(ringSize)) {
            val strokePx = 19.dp.toPx()
            val inset = strokePx / 2f + 4.dp.toPx()
            val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)

            // Soft outer aura
            drawArc(
                color = glowColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx + 8.dp.toPx(), cap = StrokeCap.Round)
            )

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = strokePx, cap = StrokeCap.Round)
            )

            // Subtle inner dashed rhythm ring
            val innerInset = inset + strokePx * 0.85f
            val innerSize = Size(size.width - innerInset * 2f, size.height - innerInset * 2f)
            drawArc(
                color = primaryColor.copy(alpha = 0.2f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(innerInset, innerInset),
                size = innerSize,
                style = Stroke(
                    width = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 10f), 0f)
                )
            )

            if (animatedProgress > 0.005f) {
                val sweep = 360f * animatedProgress
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(primaryColor, accentColor, primaryColor)
                    ),
                    startAngle = -90f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(inset, inset),
                    size = arcSize,
                    style = Stroke(width = strokePx, cap = StrokeCap.Round)
                )

                // Bright tip dot at progress head
                val endRad = Math.toRadians((-90f + sweep).toDouble())
                val radius = arcSize.width / 2f
                val cx = size.width / 2f + (radius * cos(endRad)).toFloat()
                val cy = size.height / 2f + (radius * sin(endRad)).toFloat()
                drawCircle(
                    color = Color.White,
                    radius = 4.5.dp.toPx(),
                    center = Offset(cx, cy)
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$currentMl",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontFamily = JetBrainsMonoMetricFont,
                    fontWeight = FontWeight.Bold
                ),
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "of $goalMl ml",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            val pct = (progressFraction * 100).toInt().coerceAtMost(999)
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Text(
                    text = "$pct% hydrated",
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp)
                )
            }
        }
    }
}

@Composable
fun WeeklyHydrationBarChart(
    bars: List<DailyHydrationBar>,
    goalMl: Int,
    modifier: Modifier = Modifier
) {
    val maxVal = max(goalMl, bars.maxOfOrNull { it.totalMl } ?: goalMl).coerceAtLeast(500)
    val barActiveColor = MaterialTheme.colorScheme.secondary
    val barActiveEndColor = MaterialTheme.colorScheme.primary
    val barMutedColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.48f)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
    val goalLineColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.78f)

    Column(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(152.dp)
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(152.dp)
            ) {
                val count = bars.size.coerceAtLeast(1)
                val slotWidth = size.width / count
                val barWidth = (slotWidth * 0.48f).coerceAtMost(30.dp.toPx())
                val chartHeight = size.height - 12.dp.toPx()

                // Goal threshold dashed line
                val goalRatio = (goalMl.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                val goalY = size.height - (chartHeight * goalRatio)
                drawLine(
                    color = goalLineColor,
                    start = Offset(0f, goalY),
                    end = Offset(size.width, goalY),
                    strokeWidth = 1.5.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 8f), 0f)
                )

                bars.forEachIndexed { idx, bar ->
                    val left = idx * slotWidth + (slotWidth - barWidth) / 2f
                    val radius = CornerRadius(10.dp.toPx(), 10.dp.toPx())

                    drawRoundRect(
                        color = trackColor,
                        topLeft = Offset(left, 12.dp.toPx()),
                        size = Size(barWidth, chartHeight),
                        cornerRadius = radius
                    )

                    val ratio = (bar.totalMl.toFloat() / maxVal.toFloat()).coerceIn(0f, 1f)
                    val filledHeight = chartHeight * ratio
                    if (filledHeight > 2f) {
                        val topY = size.height - filledHeight
                        val brush = if (bar.isToday || bar.totalMl >= goalMl) {
                            Brush.verticalGradient(
                                colors = listOf(barActiveEndColor, barActiveColor),
                                startY = topY,
                                endY = size.height
                            )
                        } else {
                            Brush.verticalGradient(
                                colors = listOf(barMutedColor, barMutedColor.copy(alpha = 0.25f)),
                                startY = topY,
                                endY = size.height
                            )
                        }
                        drawRoundRect(
                            brush = brush,
                            topLeft = Offset(left, topY),
                            size = Size(barWidth, filledHeight),
                            cornerRadius = radius
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            bars.forEach { bar ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = bar.label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = if (bar.isToday) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = if (bar.isToday) {
                            MaterialTheme.colorScheme.secondary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                    Text(
                        text = if (bar.totalMl > 0) "${bar.totalMl / 100 / 10.0}L" else "-",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun ModuleStatusPill(
    isAlwaysFree: Boolean,
    isActivated: Boolean,
    isPhaseOneBuiltIn: Boolean
) {
    val (label, bg, fg) = when {
        isAlwaysFree && isPhaseOneBuiltIn -> Triple(
            "Active • Free",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        isAlwaysFree -> Triple(
            "Free Roadmap",
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer
        )
        isActivated -> Triple(
            "Activated",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        else -> Triple(
            "Key Required",
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant
        )
    }

    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (!isAlwaysFree && !isActivated) {
            Icon(
                imageVector = Icons.Rounded.Lock,
                contentDescription = "Activation key required",
                modifier = Modifier.size(12.dp),
                tint = fg
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg
        )
    }
}

@Composable
fun QuickAmountChip(
    label: String,
    accentColor: Color = MaterialTheme.colorScheme.secondary,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(RoundedCornerShape(16.dp))
            .border(
                width = 1.2.dp,
                color = accentColor.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick),
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp)
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.18f),
                            accentColor.copy(alpha = 0.08f)
                        )
                    )
                )
                .padding(horizontal = 14.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoMetricFont,
                    fontWeight = FontWeight.Bold
                ),
                color = accentColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
