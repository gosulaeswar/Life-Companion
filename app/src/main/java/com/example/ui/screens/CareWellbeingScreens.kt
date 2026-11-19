package com.example.ui.screens

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.domain.DateEpochUtils
import com.example.ui.components.DateTimeSelectorRow
import com.example.ui.components.QuickAmountChip
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.ModulesHubUiState
import com.example.ui.viewmodel.ModulesHubViewModel
import kotlin.math.roundToInt

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BeautyCareScreen(
    uiState: ModulesHubUiState,
    viewModel: ModulesHubViewModel,
    onBack: () -> Unit
) {
    var customStep by remember { mutableStateOf("") }
    var routineType by remember { mutableStateOf("Skincare") }
    var selectedDay by remember { mutableLongStateOf(DateEpochUtils.todayEpochDay()) }
    val initHm = remember { DateEpochUtils.epochMsToHourMinute() }
    var selectedHour by remember { mutableIntStateOf(initHm.first) }
    var selectedMinute by remember { mutableIntStateOf(initHm.second) }

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
                    Text("✨ Beauty & Self-Care", style = MaterialTheme.typography.headlineSmall)
                    Text("Daily glow rituals, skincare & haircare", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text("🌸 One-Tap Ritual Check-In", style = MaterialTheme.typography.titleMedium)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAmountChip("🧼 Cleanser") { viewModel.logBeautyCare("Morning", "🧼 Gentle Cleanser", "") }
                        QuickAmountChip("☀️ SPF 50") { viewModel.logBeautyCare("Morning", "☀️ Sunscreen SPF 50", "") }
                        QuickAmountChip("💆 Hair Oil") { viewModel.logBeautyCare("Haircare", "💆 Nourishing Hair Oil", "") }
                        QuickAmountChip("🌙 Night Cream") { viewModel.logBeautyCare("Evening", "🌙 Hydrating Night Serum", "") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("Skincare", "Haircare", "Evening").forEach { t ->
                            FilterChip(selected = routineType == t, onClick = { routineType = t }, label = { Text(t) })
                        }
                    }
                    DateTimeSelectorRow(
                        selectedEpochDay = selectedDay,
                        selectedHour = selectedHour,
                        selectedMinute = selectedMinute,
                        onEpochDayChanged = { selectedDay = it },
                        onTimeChanged = { h, m ->
                            selectedHour = h
                            selectedMinute = m
                        },
                        dateLabel = "Ritual Date",
                        timeLabel = "Ritual Time",
                        testTagPrefix = "beauty_dt"
                    )
                    OutlinedTextField(
                        value = customStep,
                        onValueChange = { customStep = it },
                        label = { Text("Custom ritual or product") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (customStep.isNotBlank()) {
                                val ts = DateEpochUtils.combineEpochDayAndTime(selectedDay, selectedHour, selectedMinute)
                                viewModel.logBeautyCare(routineType, customStep, "", ts)
                                customStep = ""
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Log Self-Care Step ✨") }
                }
            }
        }

        items(uiState.beautyRecords, key = { it.id }) { item ->
            Surface(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface) {
                Row(modifier = Modifier.padding(14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(item.productOrStep, style = MaterialTheme.typography.titleSmall)
                        Text("${item.routineType} • ${DateEpochUtils.formatEpochDayAndTime(item.completedAtEpochMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = { viewModel.deleteBeautyCare(item.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete ritual")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DigitalWellbeingScreen(
    uiState: ModulesHubUiState,
    viewModel: ModulesHubViewModel,
    onBack: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshWellbeingFromDevice(includePastWeek = true)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        contract = androidx.activity.result.contract.ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.sendTestWellbeingOveruseAlert()
        }
    }

    val current = uiState.todayWellbeing
    val screenMins = current?.screenTimeMinutes ?: 0
    val goalMins = current?.mindfulGoalMinutes ?: 120
    val focusCount = current?.focusSessionsCount ?: 0
    val unlocks = current?.unlockCount ?: 0
    val launches = current?.appLaunchCount ?: 0
    val nightMins = current?.nightScreenMinutes ?: 0
    val longestMins = current?.longestSessionMinutes ?: 0
    val ratio = (screenMins.toFloat() / goalMins.coerceAtLeast(30).toFloat()).coerceIn(0f, 1f)

    val overuseAlert = uiState.wellbeingOveruseAlert
    val balanceScore = uiState.mindfulBalanceScore
    val topApps = uiState.topAppsUsage
    val categoryMap = uiState.categoryScreenMinutes
    val maxAppMins = (topApps.maxOfOrNull { it.minutesUsed } ?: 1).coerceAtLeast(1)

    fun formatHoursMins(mins: Int): String {
        val h = mins / 60
        val m = mins % 60
        return if (h > 0) "${h}h ${m}m" else "${m}m"
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("📵 Digital Wellbeing", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Live screen time, pickups, top apps & overuse alerts",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // Usage Access Connection & Sync Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (uiState.hasUsageAccessPermission) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
                    }
                )
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (uiState.hasUsageAccessPermission) {
                                "✅ Android Usage Access Connected"
                            } else {
                                "🔓 Connect Android Screen Time"
                            },
                            style = MaterialTheme.typography.titleSmall
                        )
                        if (uiState.isSyncingWellbeing) {
                            androidx.compose.material3.CircularProgressIndicator(
                                modifier = Modifier.height(18.dp).widthIn(max = 18.dp),
                                strokeWidth = 2.dp
                            )
                        }
                    }

                    Text(
                        text = if (uiState.hasUsageAccessPermission) {
                            uiState.wellbeingSyncBanner
                                ?: "Source: ${current?.sourceLabel ?: "Android Usage Access"} • Automatically reads today's app screen time, unlocks & late-night sessions locally."
                        } else {
                            "To fetch real screen time, app breakdown, phone unlocks, and late-night use from Android Wellbeing, enable Usage Access for LifeWeave in System Settings."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val openUsageSettings = {
                            val opened = runCatching {
                                context.startActivity(viewModel.usageAccessSettingsIntent(true))
                                true
                            }.getOrDefault(false)
                            if (!opened) {
                                runCatching {
                                    context.startActivity(viewModel.usageAccessSettingsIntent(false))
                                }
                            }
                        }
                        if (!uiState.hasUsageAccessPermission) {
                            Button(
                                onClick = { openUsageSettings() },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Grant Usage Access")
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick = {
                                    viewModel.refreshWellbeingFromDevice(
                                        includePastWeek = true,
                                        userTriggered = true
                                    )
                                }
                            ) {
                                Text("Re-check")
                            }
                        } else {
                            Button(
                                onClick = {
                                    viewModel.refreshWellbeingFromDevice(
                                        includePastWeek = true,
                                        userTriggered = true
                                    )
                                },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("🔄 Sync Live Usage")
                            }
                            androidx.compose.material3.OutlinedButton(
                                onClick = { openUsageSettings() }
                            ) {
                                Text("Settings")
                            }
                        }
                    }
                }
            }
        }

        // Automatic App, Digital Wellbeing & All-Modules Refresh Control Card
        item {
            com.example.ui.components.AutoRefreshControlCard(
                autoRefreshEnabled = uiState.autoRefreshEnabled,
                intervalMinutes = uiState.autoRefreshIntervalMinutes,
                isRefreshing = uiState.isAutoRefreshingAll,
                lastRefreshedEpochMs = uiState.lastAutoRefreshEpochMs,
                lastSummary = uiState.lastAutoRefreshSummary,
                onToggleAutoRefresh = viewModel::setAutoRefreshEnabled,
                onSelectIntervalMinutes = viewModel::setAutoRefreshIntervalMinutes,
                onRefreshAllNow = {
                    viewModel.triggerAutoRefresh(
                        force = true,
                        reason = "wellbeing_manual",
                        includePastWeek = true
                    )
                },
                compact = false
            )
        }

        // Overuse Alert Banner Card
        if (overuseAlert != null) {
            item {
                val severity = overuseAlert.severity
                val bgColor = when (severity) {
                    com.example.wellbeing.OveruseSeverity.CRITICAL,
                    com.example.wellbeing.OveruseSeverity.EXCEEDED ->
                        MaterialTheme.colorScheme.errorContainer
                    com.example.wellbeing.OveruseSeverity.APPROACHING ->
                        com.example.ui.theme.AmberNotice.copy(alpha = 0.24f)
                    com.example.wellbeing.OveruseSeverity.BALANCED ->
                        com.example.ui.theme.FertileLeafGreen.copy(alpha = 0.16f)
                }
                val titleColor = when (severity) {
                    com.example.wellbeing.OveruseSeverity.CRITICAL,
                    com.example.wellbeing.OveruseSeverity.EXCEEDED ->
                        MaterialTheme.colorScheme.onErrorContainer
                    else -> MaterialTheme.colorScheme.onSurface
                }
                val iconPrefix = when (severity) {
                    com.example.wellbeing.OveruseSeverity.CRITICAL -> "🚨"
                    com.example.wellbeing.OveruseSeverity.EXCEEDED -> "🔴"
                    com.example.wellbeing.OveruseSeverity.APPROACHING -> "⚠️"
                    com.example.wellbeing.OveruseSeverity.BALANCED -> "🌿"
                }

                Card(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = bgColor)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "$iconPrefix ${overuseAlert.title}",
                            style = MaterialTheme.typography.titleMedium,
                            color = titleColor
                        )
                        Text(
                            text = overuseAlert.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = titleColor
                        )
                        Text(
                            text = "💡 ${overuseAlert.recommendation}",
                            style = MaterialTheme.typography.labelMedium,
                            color = titleColor
                        )
                        if (severity != com.example.wellbeing.OveruseSeverity.BALANCED) {
                            Button(
                                onClick = { viewModel.updateWellbeing(addFocus = 1) },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("🎯 Log Screen-Free Focus Break")
                            }
                        }
                    }
                }
            }
        }

        // Main Screen Budget + Mindful Balance Score Card
        item {
            val isExceeded = screenMins >= goalMins
            val isApproaching = !isExceeded &&
                (screenMins * 100f / goalMins.coerceAtLeast(30).toFloat()) >= uiState.wellbeingWarningPercent
            val progressTint = when {
                isExceeded -> MaterialTheme.colorScheme.error
                isApproaching -> com.example.ui.theme.AmberNotice
                else -> MaterialTheme.colorScheme.primary
            }

            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("📱 Today's Screen Time", style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${formatHoursMins(screenMins)} / ${formatHoursMins(goalMins)} cap",
                                style = MaterialTheme.typography.headlineSmall.copy(fontFamily = JetBrainsMonoMetricFont),
                                color = progressTint
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Column(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "$balanceScore",
                                    style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoMetricFont),
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text("Balance Score", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }

                    LinearProgressIndicator(
                        progress = { ratio },
                        color = progressTint,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                    )

                    // 4 Rich Wellbeing Metrics Tiles
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WellbeingStatTile(
                            emoji = "🔓",
                            label = "Unlocks",
                            value = "$unlocks",
                            subtitle = "Pickups",
                            modifier = Modifier.weight(1f)
                        )
                        WellbeingStatTile(
                            emoji = "🚀",
                            label = "Launches",
                            value = "$launches",
                            subtitle = "App opens",
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        WellbeingStatTile(
                            emoji = "⏱️",
                            label = "Longest Streak",
                            value = "${longestMins}m",
                            subtitle = if (longestMins >= 45) "Eye break due!" else "Healthy span",
                            highlightWarn = longestMins >= 45,
                            modifier = Modifier.weight(1f)
                        )
                        WellbeingStatTile(
                            emoji = "🌙",
                            label = "Late-Night Use",
                            value = "${nightMins}m",
                            subtitle = "10 PM – 6 AM",
                            highlightWarn = nightMins >= 20,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = "🎯 Focus Sessions Completed Today: $focusCount",
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }
        }

        // Category Breakdown & Top Used Apps Card
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text("📊 App Categories & Most Used Apps", style = MaterialTheme.typography.titleMedium)

                    if (categoryMap.isNotEmpty()) {
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            categoryMap.forEach { (cat, mins) ->
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)
                                ) {
                                    Text(
                                        text = "$cat • ${mins}m",
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (topApps.isEmpty()) {
                        Text(
                            text = if (uiState.hasUsageAccessPermission) {
                                "No app foreground usage recorded yet for today—open a few apps or tap Sync Live Usage."
                            } else {
                                "Grant Android Usage Access above to see per-app screen time (YouTube, Instagram, WhatsApp, etc.) and highlight long-used apps."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        val longApps = uiState.longUsedApps
                        val normalApps = topApps.filterNot { candidate ->
                            longApps.any { it.packageName == candidate.packageName && it.appName == candidate.appName }
                        }

                        if (longApps.isNotEmpty()) {
                            Surface(
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.55f),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.5.dp,
                                    MaterialTheme.colorScheme.error.copy(alpha = 0.7f)
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
                                        Text(
                                            text = "🔥 Long-Used Apps (${longApps.size})",
                                            style = MaterialTheme.typography.titleSmall,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                        Text(
                                            text = "High Screen Share",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer
                                        )
                                    }
                                    longApps.forEach { app ->
                                        val capSharePct = ((app.minutesUsed * 100f) / goalMins.coerceAtLeast(30).toFloat()).roundToInt()
                                        Surface(
                                            shape = RoundedCornerShape(14.dp),
                                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
                                            border = androidx.compose.foundation.BorderStroke(
                                                1.dp,
                                                MaterialTheme.colorScheme.error.copy(alpha = 0.35f)
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
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Row(
                                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                                            verticalAlignment = Alignment.CenterVertically
                                                        ) {
                                                            Text(
                                                                text = "🔥 ${app.appName}",
                                                                style = MaterialTheme.typography.titleSmall,
                                                                color = MaterialTheme.colorScheme.error
                                                            )
                                                            Surface(
                                                                shape = CircleShape,
                                                                color = MaterialTheme.colorScheme.error
                                                            ) {
                                                                Text(
                                                                    text = "${capSharePct}% of cap",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    color = MaterialTheme.colorScheme.onError,
                                                                    modifier = Modifier.padding(horizontal = 7.dp, vertical = 2.dp)
                                                                )
                                                            }
                                                        }
                                                        Text(
                                                            text = "${app.categoryLabel} • ${app.launchCount} opens today",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                                        )
                                                    }
                                                    Text(
                                                        text = formatHoursMins(app.minutesUsed),
                                                        style = MaterialTheme.typography.titleMedium.copy(fontFamily = JetBrainsMonoMetricFont),
                                                        color = MaterialTheme.colorScheme.error
                                                    )
                                                }
                                                LinearProgressIndicator(
                                                    progress = { (app.minutesUsed.toFloat() / maxAppMins.toFloat()).coerceIn(0.08f, 1f) },
                                                    color = MaterialTheme.colorScheme.error,
                                                    trackColor = MaterialTheme.colorScheme.errorContainer,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .height(8.dp)
                                                        .clip(CircleShape)
                                                )
                                            }
                                        }
                                    }
                                    androidx.compose.material3.OutlinedButton(
                                        onClick = { viewModel.updateWellbeing(addFocus = 1) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("🎯 Pause Long-Used Apps • Log 15m Break")
                                    }
                                }
                            }
                        }

                        normalApps.forEach { app ->
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(app.appName, style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            text = "${app.categoryLabel} • ${app.launchCount} launches",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Text(
                                        text = formatHoursMins(app.minutesUsed),
                                        style = MaterialTheme.typography.labelLarge.copy(fontFamily = JetBrainsMonoMetricFont),
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { (app.minutesUsed.toFloat() / maxAppMins.toFloat()).coerceIn(0.05f, 1f) },
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(6.dp)
                                        .clip(CircleShape)
                                )
                            }
                        }
                    }
                }
            }
        }

        // 7-Day Screen Time Trend Card
        if (uiState.wellbeingHistory.isNotEmpty()) {
            item {
                Card(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text("📈 Recent Screen Time History", style = MaterialTheme.typography.titleMedium)
                        val maxHist = (uiState.wellbeingHistory.maxOfOrNull { it.screenTimeMinutes } ?: goalMins)
                            .coerceAtLeast(goalMins)
                            .coerceAtLeast(60)
                        uiState.wellbeingHistory.forEach { dayRec ->
                            val over = dayRec.screenTimeMinutes > dayRec.mindfulGoalMinutes
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (dayRec.dayKey == DateEpochUtils.todayDayKey()) {
                                        "Today"
                                    } else {
                                        DateEpochUtils.shortWeekdayForDayKey(dayRec.dayKey)
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    modifier = Modifier.widthIn(min = 44.dp)
                                )
                                LinearProgressIndicator(
                                    progress = {
                                        (dayRec.screenTimeMinutes.toFloat() / maxHist.toFloat()).coerceIn(0.03f, 1f)
                                    },
                                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(8.dp)
                                        .clip(CircleShape)
                                )
                                Text(
                                    text = "${dayRec.screenTimeMinutes}m" + if (over) " ⚠️" else "",
                                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = JetBrainsMonoMetricFont),
                                    color = if (over) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }

        // Overuse Alert Settings & Screen Budget Controls
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier.padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text("🔔 Overuse Alerts & Budget Controls", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Alert Me on Screen Overuse", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "Notify when approaching warning % or exceeding your daily cap",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        androidx.compose.material3.Switch(
                            checked = uiState.wellbeingOveruseAlertsEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setWellbeingAlertSettings(enabled, uiState.wellbeingWarningPercent)
                                if (enabled && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                    val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                    if (!granted) {
                                        notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                }
                            }
                        )
                    }

                    Text(
                        text = "Early Warning Threshold (${uiState.wellbeingWarningPercent}% of cap)",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(70, 80, 90).forEach { pct ->
                            FilterChip(
                                selected = uiState.wellbeingWarningPercent == pct,
                                onClick = {
                                    viewModel.setWellbeingAlertSettings(
                                        uiState.wellbeingOveruseAlertsEnabled,
                                        pct
                                    )
                                },
                                label = { Text("$pct%") }
                            )
                        }
                    }

                    Text("Daily Screen Cap: ${formatHoursMins(goalMins)} ($goalMins min)", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = goalMins.toFloat(),
                        onValueChange = { viewModel.updateWellbeing(newGoalMin = (it / 15f).roundToInt() * 15) },
                        valueRange = 30f..480f
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        QuickAmountChip("+15m 📱", MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) {
                            viewModel.updateWellbeing(addScreenMin = 15)
                        }
                        QuickAmountChip("+30m 📱", MaterialTheme.colorScheme.secondary, Modifier.weight(1f)) {
                            viewModel.updateWellbeing(addScreenMin = 30)
                        }
                        QuickAmountChip("+1 Focus 🎯", MaterialTheme.colorScheme.primary, Modifier.weight(1f)) {
                            viewModel.updateWellbeing(addFocus = 1)
                        }
                    }

                    androidx.compose.material3.OutlinedButton(
                        onClick = {
                            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                                val granted = androidx.core.content.ContextCompat.checkSelfPermission(
                                    context,
                                    android.Manifest.permission.POST_NOTIFICATIONS
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (granted) {
                                    viewModel.sendTestWellbeingOveruseAlert()
                                } else {
                                    notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                }
                            } else {
                                viewModel.sendTestWellbeingOveruseAlert()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔔 Test Overuse Alert Notification")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun WellbeingStatTile(
    emoji: String,
    label: String,
    value: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    highlightWarn: Boolean = false
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (highlightWarn) {
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        }
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("$emoji $label", style = MaterialTheme.typography.labelMedium)
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge.copy(fontFamily = JetBrainsMonoMetricFont),
                color = if (highlightWarn) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
