package com.example.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.local.entity.SleepRecordEntity
import com.example.domain.DateEpochUtils
import com.example.sleep.SleepMonitorLiveState
import com.example.ui.theme.AiStudioMidnight
import com.example.ui.theme.AmberNotice
import com.example.ui.theme.FertileLeafGreen
import com.example.ui.theme.HeroTealDeep
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.theme.VioletFocus
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepQualitySnoringMonitorCard(
    liveState: SleepMonitorLiveState,
    bedtimePromptEnabled: Boolean,
    bedtimeHour: Int,
    bedtimeMinute: Int,
    wakeHour: Int,
    wakeMinute: Int,
    onStartMonitor: () -> Unit,
    onStopAndSaveMonitor: () -> Unit,
    onDismissBanner: () -> Unit,
    onUpdateBedtimeSchedule: (enabled: Boolean, bedH: Int, bedM: Int, wakeH: Int, wakeM: Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val micGranted = results[Manifest.permission.RECORD_AUDIO] == true ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        if (micGranted) {
            onStartMonitor()
        }
    }

    val requestAndStart = {
        val micGranted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        val notifNeeded = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED

        if (micGranted && !notifNeeded) {
            onStartMonitor()
        } else {
            val toRequest = buildList {
                if (!micGranted) add(Manifest.permission.RECORD_AUDIO)
                if (notifNeeded && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    add(Manifest.permission.POST_NOTIFICATIONS)
                }
            }.toTypedArray()
            permissionsLauncher.launch(toRequest)
        }
    }

    Card(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .testTag("sleep_monitor_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = AiStudioMidnight),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(
                            AiStudioMidnight,
                            HeroTealDeep.copy(alpha = 0.85f)
                        )
                    )
                )
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header
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
                    Surface(
                        shape = CircleShape,
                        color = Color.White.copy(alpha = 0.16f),
                        modifier = Modifier.size(42.dp)
                    ) {
                        Icon(
                            imageVector = if (liveState.isMonitoring) Icons.Rounded.GraphicEq else Icons.Rounded.Bedtime,
                            contentDescription = "Sleep and snoring monitor",
                            tint = Color(0xFF9DF7DF),
                            modifier = Modifier.padding(9.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Auto Sleep Quality & Snoring Monitor",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = if (liveState.isMonitoring) {
                                "${liveState.estimatedStage.emoji} ${liveState.estimatedStage.label} • ${liveState.elapsedMinutes / 60}h ${liveState.elapsedMinutes % 60}m"
                            } else {
                                "Acoustic snoring DSP + bed movement restlessness sensor"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFBEEFE3)
                        )
                    }
                }

                Surface(
                    shape = CircleShape,
                    color = if (liveState.isMonitoring) {
                        FertileLeafGreen.copy(alpha = 0.30f)
                    } else {
                        Color.White.copy(alpha = 0.14f)
                    },
                    border = BorderStroke(
                        1.dp,
                        if (liveState.isMonitoring) Color(0xFF66F2BA) else Color.White.copy(alpha = 0.25f)
                    )
                ) {
                    Text(
                        text = if (liveState.isMonitoring) "● LIVE" else "READY",
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontFamily = JetBrainsMonoMetricFont,
                            fontWeight = FontWeight.Bold
                        ),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            // Live acoustic level waveform bar
            val bars = if (liveState.recentDbWaveform.isNotEmpty()) {
                liveState.recentDbWaveform.takeLast(16)
            } else {
                List(16) { 0.14f }
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = liveState.lastEventLabel,
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFFE0FFF6),
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = String.format(
                                Locale.US,
                                "%.0f dB (floor %.0f dB)",
                                liveState.currentDb,
                                liveState.ambientFloorDb
                            ),
                            style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoMetricFont),
                            color = Color(0xFF9DF7DF)
                        )
                    }

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(34.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom
                    ) {
                        bars.forEach { level ->
                            val barColor = when {
                                !liveState.isMonitoring -> Color.White.copy(alpha = 0.24f)
                                level >= 0.65f -> AmberNotice
                                level >= 0.40f -> Color(0xFF66F2BA)
                                else -> Color(0xFF8FF3D8).copy(alpha = 0.72f)
                            }
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height((6f + level.coerceIn(0.05f, 1f) * 28f).dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(barColor)
                            )
                        }
                    }
                }
            }

            // Live 4-metric grid
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                SleepLiveMetricTile(
                    label = "Quality",
                    value = "${liveState.liveQualityScore}/100",
                    modifier = Modifier.weight(1f)
                )
                SleepLiveMetricTile(
                    label = "Snores",
                    value = "${liveState.snoreEventsCount} (${liveState.snoreDurationMinutes}m)",
                    modifier = Modifier.weight(1f)
                )
                SleepLiveMetricTile(
                    label = "Peak Snore",
                    value = if (liveState.peakSnoreDb > 0.0) "${liveState.peakSnoreDb.toInt()} dB" else "-- dB",
                    modifier = Modifier.weight(1f)
                )
                SleepLiveMetricTile(
                    label = "Restless",
                    value = "${liveState.restlessnessIndex}%",
                    modifier = Modifier.weight(1f)
                )
            }

            // Saved / Error Banner
            liveState.lastSavedBanner?.let { banner ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = FertileLeafGreen.copy(alpha = 0.22f),
                    border = BorderStroke(1.dp, Color(0xFF66F2BA).copy(alpha = 0.5f))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = banner,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissBanner) {
                            Text("OK", color = Color(0xFF9DF7DF))
                        }
                    }
                }
            }

            liveState.errorMessage?.let { err ->
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.90f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = err,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = onDismissBanner) {
                            Text("OK")
                        }
                    }
                }
            }

            // Start / Stop & Auto-Log CTA
            Button(
                onClick = {
                    if (liveState.isMonitoring) {
                        onStopAndSaveMonitor()
                    } else {
                        requestAndStart()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (liveState.isMonitoring) MaterialTheme.colorScheme.error else Color(0xFF2CE5B5),
                    contentColor = if (liveState.isMonitoring) MaterialTheme.colorScheme.onError else AiStudioMidnight
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .testTag("sleep_monitor_toggle_button")
            ) {
                Icon(
                    imageVector = if (liveState.isMonitoring) Icons.Rounded.Stop else Icons.Rounded.Mic,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (liveState.isMonitoring) {
                        "Stop & Auto-Log to Sleep Metrics"
                    } else {
                        "Start Overnight Sleep & Snoring Monitor"
                    },
                    fontWeight = FontWeight.Bold
                )
            }

            // Bedtime Auto-Prompt & Morning Auto-Stop Schedule
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = Color.White.copy(alpha = 0.08f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Bedtime Auto-Prompt & Morning Auto-Log",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.White
                            )
                            Text(
                                text = String.format(
                                    Locale.US,
                                    "Prompt at %02d:%02d • Auto-log on wake after %02d:%02d",
                                    bedtimeHour,
                                    bedtimeMinute,
                                    wakeHour,
                                    wakeMinute
                                ),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFFBEEFE3)
                            )
                        }
                        Switch(
                            checked = bedtimePromptEnabled,
                            onCheckedChange = { enabled ->
                                onUpdateBedtimeSchedule(enabled, bedtimeHour, bedtimeMinute, wakeHour, wakeMinute)
                            },
                            modifier = Modifier.testTag("sleep_bedtime_prompt_switch")
                        )
                    }

                    val schedulePresets = listOf(
                        Triple("Early (22:00–06:30)", 22 to 0, 6 to 30),
                        Triple("Standard (22:30–07:00)", 22 to 30, 7 to 0),
                        Triple("Late (23:30–07:30)", 23 to 30, 7 to 30)
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        schedulePresets.forEach { (label, bed, wake) ->
                            val selected = bedtimeHour == bed.first && bedtimeMinute == bed.second &&
                                wakeHour == wake.first && wakeMinute == wake.second
                            FilterChip(
                                selected = selected,
                                onClick = {
                                    onUpdateBedtimeSchedule(
                                        bedtimePromptEnabled,
                                        bed.first,
                                        bed.second,
                                        wake.first,
                                        wake.second
                                    )
                                },
                                label = { Text(label, style = MaterialTheme.typography.labelSmall) }
                            )
                        }
                    }
                }
            }

            // Privacy Guarantee footer
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Rounded.Shield,
                    contentDescription = null,
                    tint = Color(0xFF9DF7DF),
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = "100% On-Device DSP: Mic samples are analyzed in memory & discarded immediately. Zero audio stored.",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color(0xFFBEEFE3)
                )
            }
        }
    }
}

@Composable
private fun SleepLiveMetricTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = Color.White.copy(alpha = 0.10f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge.copy(
                    fontFamily = JetBrainsMonoMetricFont,
                    fontWeight = FontWeight.Bold
                ),
                color = Color.White,
                maxLines = 1
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFFBEEFE3),
                maxLines = 1
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SleepRecordDetailCard(
    record: SleepRecordEntity,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val totalMinutes = ((record.wakeEpochMs - record.bedtimeEpochMs).coerceAtLeast(60_000L) / 60_000L).toInt()
    val hrs = totalMinutes / 60
    val mins = totalMinutes % 60
    val qualityScore = if (record.sleepQualityScore > 0) record.sleepQualityScore else (record.qualityRating * 20).coerceIn(20, 100)
    val scoreColor = when {
        qualityScore >= 80 -> FertileLeafGreen
        qualityScore >= 60 -> AmberNotice
        else -> MaterialTheme.colorScheme.error
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
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
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = scoreColor.copy(alpha = 0.18f)
                    ) {
                        Text(
                            text = "$qualityScore",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoMetricFont,
                                fontWeight = FontWeight.Bold
                            ),
                            color = scoreColor,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "🌙 ${hrs}h ${mins}m • ${"⭐".repeat(record.qualityRating.coerceIn(1, 5))}",
                            style = MaterialTheme.typography.titleSmall
                        )
                        Text(
                            text = "${DateEpochUtils.formatEpochDayAndTime(record.wakeEpochMs)} • ${record.trackingSource}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete sleep record")
                }
            }

            // Telemetry chips for snoring, restlessness, and stages
            val hasSensorTelemetry = record.trackingSource == "Auto Sensor" ||
                record.snoreEventsCount > 0 ||
                record.deepSleepMinutes > 0 ||
                record.restlessnessIndex > 0
            if (hasSensorTelemetry) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    SleepMetricChip(
                        text = if (record.snoreEventsCount > 0) {
                            String.format(
                                Locale.US,
                                "🔊 %d snore(s) • %dm • %.0f dB",
                                record.snoreEventsCount,
                                record.snoreDurationMinutes,
                                record.peakSnoreDb
                            )
                        } else {
                            "🤫 Quiet breathing (0 snores)"
                        }
                    )
                    if (record.deepSleepMinutes > 0 || record.lightSleepMinutes > 0) {
                        SleepMetricChip(
                            text = "🌌 Deep ${record.deepSleepMinutes}m • 🌙 Light ${record.lightSleepMinutes}m"
                        )
                    }
                    if (record.restlessnessIndex > 0) {
                        SleepMetricChip(
                            text = "🛌 Restlessness ${record.restlessnessIndex}/100"
                        )
                    }
                    if (record.awakeInterruptionsCount > 0) {
                        SleepMetricChip(
                            text = "⏰ ${record.awakeInterruptionsCount} wake interruption(s)"
                        )
                    }
                }

                // Visual Deep / Light / Awake proportion bar
                val deep = record.deepSleepMinutes.coerceAtLeast(0)
                val light = record.lightSleepMinutes.coerceAtLeast(0)
                val awake = (totalMinutes - deep - light).coerceAtLeast(0)
                val barSum = (deep + light + awake).coerceAtLeast(1).toFloat()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(CircleShape),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    if (deep > 0) {
                        Box(
                            modifier = Modifier
                                .weight((deep / barSum).coerceAtLeast(0.04f))
                                .height(8.dp)
                                .background(VioletFocus)
                        )
                    }
                    if (light > 0) {
                        Box(
                            modifier = Modifier
                                .weight((light / barSum).coerceAtLeast(0.04f))
                                .height(8.dp)
                                .background(MaterialTheme.colorScheme.primary)
                        )
                    }
                    if (awake > 0) {
                        Box(
                            modifier = Modifier
                                .weight((awake / barSum).coerceAtLeast(0.04f))
                                .height(8.dp)
                                .background(AmberNotice)
                        )
                    }
                }
            }

            if (record.notes.isNotBlank()) {
                Text(
                    text = record.notes,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SleepMetricChip(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
        )
    }
}
