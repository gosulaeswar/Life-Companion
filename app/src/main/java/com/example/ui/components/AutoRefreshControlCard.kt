package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.Autorenew
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FertileLeafGreen
import com.example.ui.theme.JetBrainsMonoMetricFont

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AutoRefreshControlCard(
    autoRefreshEnabled: Boolean,
    intervalMinutes: Int,
    isRefreshing: Boolean,
    lastRefreshedEpochMs: Long,
    lastSummary: String,
    onToggleAutoRefresh: (Boolean) -> Unit,
    onSelectIntervalMinutes: (Int) -> Unit,
    onRefreshAllNow: () -> Unit,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var expandedSettings by rememberSaveable { mutableStateOf(!compact) }
    val intervalOptions = listOf(15 to "15m", 30 to "30m", 60 to "1h")
    val intervalLabel = intervalOptions.firstOrNull { it.first == intervalMinutes }?.second ?: "${intervalMinutes}m"
    val lastUpdatedLabel = formatRelativeRefreshTime(lastRefreshedEpochMs)

    Card(
        modifier = modifier
            .widthIn(max = 600.dp)
            .fillMaxWidth()
            .testTag("auto_refresh_control_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(
            width = 1.dp,
            color = if (autoRefreshEnabled) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
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
                    Surface(
                        shape = CircleShape,
                        color = if (autoRefreshEnabled) {
                            FertileLeafGreen.copy(alpha = 0.18f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier.size(38.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Autorenew,
                            contentDescription = "Auto-refresh status",
                            tint = if (autoRefreshEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(8.dp)
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (autoRefreshEnabled) {
                                "Auto-Refresh Active • Every $intervalLabel"
                            } else {
                                "Auto-Refresh Paused"
                            },
                            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (isRefreshing) {
                                "Syncing Digital Wellbeing, Fitness Devices & all modules…"
                            } else {
                                "$lastUpdatedLabel • ${lastSummary.ifBlank { "Wellbeing, Wearables, Sleep & Care" }}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (compact) {
                        IconButton(
                            onClick = { expandedSettings = !expandedSettings },
                            modifier = Modifier.testTag("auto_refresh_expand_settings_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Tune,
                                contentDescription = "Configure auto-refresh interval"
                            )
                        }
                    }
                    if (isRefreshing) {
                        CircularProgressIndicator(
                            modifier = Modifier
                                .padding(horizontal = 10.dp)
                                .size(22.dp),
                            strokeWidth = 2.5.dp
                        )
                    } else if (compact) {
                        OutlinedButton(
                            onClick = onRefreshAllNow,
                            modifier = Modifier.testTag("auto_refresh_now_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync")
                        }
                    }
                }
            }

            if (expandedSettings) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Background & Foreground Auto-Sync",
                            style = MaterialTheme.typography.labelLarge
                        )
                        Text(
                            text = "Keeps Digital Wellbeing, Health Connect wearables, sleep, steps & subscriptions up to date automatically.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Switch(
                        checked = autoRefreshEnabled,
                        onCheckedChange = onToggleAutoRefresh,
                        modifier = Modifier.testTag("auto_refresh_toggle_switch")
                    )
                }

                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    intervalOptions.forEach { (mins, label) ->
                        FilterChip(
                            selected = intervalMinutes == mins,
                            onClick = { onSelectIntervalMinutes(mins) },
                            enabled = autoRefreshEnabled,
                            label = {
                                Text(
                                    text = "Every $label",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoMetricFont)
                                )
                            },
                            modifier = Modifier.testTag("auto_refresh_interval_${mins}m")
                        )
                    }
                }

                if (!compact) {
                    Button(
                        onClick = onRefreshAllNow,
                        enabled = !isRefreshing,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                            .testTag("auto_refresh_now_button")
                    ) {
                        if (isRefreshing) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refreshing All Modules…")
                        } else {
                            Icon(Icons.Rounded.Refresh, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Refresh All Modules Now")
                        }
                    }
                }
            }
        }
    }
}

private fun formatRelativeRefreshTime(epochMs: Long): String {
    if (epochMs <= 0L) return "Not synced yet"
    val diffSec = ((System.currentTimeMillis() - epochMs).coerceAtLeast(0L)) / 1000L
    return when {
        diffSec < 60L -> "Updated just now"
        diffSec < 3600L -> "Updated ${diffSec / 60L}m ago"
        diffSec < 86_400L -> "Updated ${diffSec / 3600L}h ago"
        else -> "Updated ${diffSec / 86_400L}d ago"
    }
}
