package com.example.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.health.connect.client.PermissionController
import com.example.R
import com.example.domain.DateEpochUtils
import com.example.domain.IndianNutritionEngine
import com.example.healthsync.FitnessSourceCatalog
import com.example.healthsync.HealthConnectAvailability
import com.example.healthsync.HealthConnectDataCategory
import com.example.healthsync.HealthConnectManager
import com.example.healthsync.HealthPermissionsRationaleActivity
import com.example.ui.theme.FertileLeafGreen
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.ModulesHubUiState
import com.example.ui.viewmodel.ModulesHubViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConnectedDevicesScreen(
    uiState: ModulesHubUiState,
    viewModel: ModulesHubViewModel,
    healthConnectManager: HealthConnectManager,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.refreshHealthConnectState()
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        viewModel.onHealthPermissionsResult(granted)
    }

    val openFitnessFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            viewModel.importFitnessDocument(context, uri)
        }
    }

    val enabledCategories = uiState.healthConnectImportAuth.enabledCategories()
    val enabledPermissions = HealthConnectManager.permissionsForCategories(enabledCategories)
    val hasAnyHcPermission = uiState.grantedHealthPermissions
        .intersect(HealthConnectManager.REQUIRED_READ_PERMISSIONS)
        .isNotEmpty()

    val latestHr = uiState.vitalMetrics.firstOrNull { it.metricType == "HEART_RATE" }
    val latestDistMeters = uiState.vitalMetrics.firstOrNull { it.metricType == "DISTANCE" }?.valuePrimary?.toInt()
        ?: uiState.todaySteps?.distanceMeters ?: 0
    val latestBurnedKcal = uiState.vitalMetrics.firstOrNull { it.metricType == "ACTIVE_CALORIES" }?.valuePrimary?.toInt()
        ?: uiState.exercises.sumOf { it.caloriesBurned }
    val latestWeightKg = uiState.profile?.weightKg
    val syncedMealsCount = uiState.dietRecords.count { IndianNutritionEngine.isHealthConnectSyncedRecord(it) }

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
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("connected_devices_back")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("⌚ Connected Apps & Devices", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Collect steps, heart rate, sleep & workouts from your wearables",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        uiState.fitnessSyncBanner?.let { banner ->
            item {
                Surface(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = banner,
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(onClick = viewModel::clearFitnessBanner) {
                            Text("OK")
                        }
                    }
                }
            }
        }

        // Automatic Fitness Devices & All-Modules Refresh Card
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
                        reason = "connected_devices_manual",
                        includePastWeek = false
                    )
                },
                compact = false
            )
        }

        // Health Connect Live Integration Card (Fitbit, Google Fit, Samsung Health, Garmin, Oura, Strava)
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
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
                        .padding(18.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("💚 Android Health Connect", style = MaterialTheme.typography.titleMedium)
                        val statusBadge = when {
                            uiState.healthConnectAvailability == HealthConnectAvailability.AVAILABLE && hasAnyHcPermission ->
                                "✅ Connected"
                            uiState.healthConnectAvailability == HealthConnectAvailability.AVAILABLE ->
                                "🔐 Access Needed"
                            uiState.healthConnectAvailability == HealthConnectAvailability.UPDATE_OR_INSTALL_REQUIRED ->
                                "📲 Setup Needed"
                            else -> "ℹ️ Unavailable"
                        }
                        Surface(
                            shape = CircleShape,
                            color = if (hasAnyHcPermission) {
                                FertileLeafGreen.copy(alpha = 0.22f)
                            } else {
                                MaterialTheme.colorScheme.secondaryContainer
                            }
                        ) {
                            Text(
                                text = statusBadge,
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                            )
                        }
                    }

                    Text(
                        text = when (uiState.healthConnectAvailability) {
                            HealthConnectAvailability.AVAILABLE ->
                                "Connect Fitbit, Google Fit, Samsung Health, Garmin, Oura, Polar, or Strava through Android Health Connect to pull live metrics privately into LifeWeave."
                            HealthConnectAvailability.UPDATE_OR_INSTALL_REQUIRED ->
                                "Install or update the official Health Connect app from Google Play to sync Fitbit, Samsung Health, Google Fit, and Garmin data."
                            HealthConnectAvailability.REQUIRES_ANDROID_8 ->
                                "Health Connect requires Android 8.0 or newer. You can still import Apple Health XML, JSON, or CSV exports below."
                            HealthConnectAvailability.UNAVAILABLE ->
                                "Health Connect service isn't active on this device/emulator yet. You can install Health Connect or import your Fitbit / Apple Health export file below."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    if (uiState.isSyncingFitness) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            Text("Syncing wearable metrics...", style = MaterialTheme.typography.bodySmall)
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        when (uiState.healthConnectAvailability) {
                            HealthConnectAvailability.AVAILABLE -> {
                                if (!hasAnyHcPermission) {
                                    Button(
                                        onClick = {
                                            val perms = enabledPermissions.ifEmpty { HealthConnectManager.REQUIRED_READ_PERMISSIONS }
                                            permissionLauncher.launch(perms)
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("health_connect_grant_button")
                                    ) {
                                        Text("Connect & Grant Access 🔗")
                                    }
                                } else {
                                    Button(
                                        onClick = { viewModel.syncFromHealthConnect() },
                                        enabled = !uiState.isSyncingFitness,
                                        modifier = Modifier
                                            .weight(1f)
                                            .testTag("health_connect_sync_button")
                                    ) {
                                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                                        Text(" Sync Enabled")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val perms = enabledPermissions.ifEmpty { HealthConnectManager.REQUIRED_READ_PERMISSIONS }
                                            permissionLauncher.launch(perms)
                                        },
                                        modifier = Modifier.testTag("health_connect_permissions_button")
                                    ) {
                                        Text("Permissions")
                                    }
                                }
                            }

                            HealthConnectAvailability.UPDATE_OR_INSTALL_REQUIRED -> {
                                Button(
                                    onClick = {
                                        runCatching {
                                            context.startActivity(healthConnectManager.buildInstallOrUpdateIntent())
                                        }.onFailure {
                                            runCatching {
                                                context.startActivity(
                                                    Intent(
                                                        Intent.ACTION_VIEW,
                                                        Uri.parse("https://play.google.com/store/apps/details?id=${HealthConnectManager.HEALTH_CONNECT_PACKAGE}")
                                                    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Install / Update Health Connect 📲")
                                }
                            }

                            else -> Unit
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.healthConnectAvailability == HealthConnectAvailability.AVAILABLE) {
                            TextButton(
                                onClick = {
                                    runCatching {
                                        context.startActivity(healthConnectManager.buildSettingsIntent())
                                    }
                                }
                            ) {
                                Text("Health Connect Settings ⚙️")
                            }
                        }
                        TextButton(
                            onClick = {
                                context.startActivity(
                                    Intent(context, HealthPermissionsRationaleActivity::class.java)
                                )
                            },
                            modifier = Modifier.testTag("hc_view_rationale_button")
                        ) {
                            Icon(Icons.Rounded.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                            Text(" Privacy Rationale")
                        }
                    }
                }
            }
        }

        // Explicit Per-Category Health Connect Authorization & Permission Status
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
                    Text(
                        "🔐 ${stringResource(R.string.hc_permissions_categories_title)}",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.hc_manual_preserved_note),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    HealthConnectDataCategory.entries.forEach { category ->
                        val isEnabled = uiState.healthConnectImportAuth.isCategoryEnabled(category)
                        val isOsGranted = HealthConnectManager.isCategoryOsGranted(
                            category = category,
                            grantedPermissions = uiState.grantedHealthPermissions
                        )
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${category.emoji} ${category.label}", style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        category.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = if (isOsGranted) "✅ OS Permission Granted" else "🔒 Requires Health Connect Grant",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isOsGranted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = isEnabled,
                                    onCheckedChange = { checked ->
                                        viewModel.setHealthConnectCategoryEnabled(category, checked)
                                    },
                                    modifier = Modifier.testTag("hc_category_toggle_${category.name}")
                                )
                            }
                        }
                    }

                    if (uiState.healthConnectAvailability == HealthConnectAvailability.AVAILABLE) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    if (enabledPermissions.isNotEmpty()) {
                                        permissionLauncher.launch(enabledPermissions)
                                    }
                                },
                                enabled = enabledPermissions.isNotEmpty(),
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("hc_request_selected_permissions_button")
                            ) {
                                Text("Grant Enabled (${enabledCategories.size})")
                            }
                            OutlinedButton(
                                onClick = {
                                    permissionLauncher.launch(HealthConnectManager.REQUIRED_READ_PERMISSIONS)
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("hc_request_all_permissions_button")
                            ) {
                                Text("Grant All (${HealthConnectDataCategory.entries.size})")
                            }
                        }
                    }
                }
            }
        }

        // Prominent Devices & Apps Supported Strip
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
                    Text("🌐 Compatible Apps & Wearables", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FitnessSourceCatalog.supportedBrands.forEach { brand ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                            ) {
                                Text(
                                    text = "${brand.emoji} ${brand.name}",
                                    style = MaterialTheme.typography.labelLarge,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        // Apple Health / Fitbit File Import Card
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
                    Text("🍎 Apple Health & Watch File Import", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Since Apple HealthKit runs only on iOS, export your data on iPhone (Health App → Profile → Export All Health Data → export.xml) or pick a Fitbit JSON / CSV export to import real steps, heart rate, sleep, hydration, weight & workouts directly here.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            openFitnessFileLauncher.launch(
                                arrayOf(
                                    "text/xml",
                                    "application/xml",
                                    "application/json",
                                    "text/csv",
                                    "text/comma-separated-values",
                                    "*/*"
                                )
                            )
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("import_fitness_file_button")
                    ) {
                        Icon(Icons.Rounded.UploadFile, contentDescription = null)
                        Text(" Import Apple Health XML / JSON / CSV")
                    }
                }
            }
        }

        // Live Collected Metrics Overview
        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text("📊 Collected Device Metrics", style = MaterialTheme.typography.titleMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SyncedMetricChip(
                        emoji = "👟",
                        label = "Today Steps",
                        value = "${uiState.todaySteps?.stepCount ?: 0}",
                        modifier = Modifier.weight(1f)
                    )
                    SyncedMetricChip(
                        emoji = "❤️",
                        label = "Heart Rate",
                        value = latestHr?.let { "${it.valuePrimary.toInt()} bpm" } ?: "--",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SyncedMetricChip(
                        emoji = "📍",
                        label = "Distance",
                        value = String.format("%.2f km", latestDistMeters / 1000f),
                        modifier = Modifier.weight(1f)
                    )
                    SyncedMetricChip(
                        emoji = "🔥",
                        label = "Burned Energy",
                        value = "$latestBurnedKcal kcal",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SyncedMetricChip(
                        emoji = "😴",
                        label = "Sleep Logs",
                        value = "${uiState.sleepRecords.size} nights",
                        modifier = Modifier.weight(1f)
                    )
                    SyncedMetricChip(
                        emoji = "⚖️",
                        label = "Body Weight",
                        value = latestWeightKg?.let { String.format("%.1f kg", it) } ?: "--",
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SyncedMetricChip(
                        emoji = "🥗",
                        label = "Synced Meals",
                        value = "$syncedMealsCount logged",
                        modifier = Modifier.weight(1f)
                    )
                    SyncedMetricChip(
                        emoji = "💧",
                        label = "Today Water",
                        value = "${uiState.hydration.totalTodayMl} ml",
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // Connected Sources History
        item {
            Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                Text(
                    "🔌 Connected Sources (${uiState.connectedSources.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        if (uiState.connectedSources.isEmpty()) {
            item {
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Text(
                        text = "No apps synced yet. Connect Android Health Connect or import an Apple Health / Fitbit export file above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(uiState.connectedSources, key = { it.id }) { source ->
                Surface(
                    modifier = Modifier
                        .widthIn(max = 600.dp)
                        .fillMaxWidth(),
                    shape = RoundedCornerShape(18.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
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
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(source.emoji, fontSize = 22.sp)
                            }
                            Column {
                                Text(source.displayName, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${source.syncChannel} • ${source.recordsImportedCount} metrics • ${DateEpochUtils.formatTime(source.lastSyncedEpochMs)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                if (source.latestSummary.isNotBlank()) {
                                    Text(
                                        source.latestSummary,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                        IconButton(onClick = { viewModel.deleteConnectedSource(source.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Remove source")
                        }
                    }
                }
            }
        }

        if (uiState.vitalMetrics.isNotEmpty()) {
            item {
                Column(modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth()) {
                    Text(
                        "💓 Recent Device Readings (${uiState.vitalMetrics.size})",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            items(uiState.vitalMetrics.take(15), key = { it.id }) { vital ->
                val emoji = when (vital.metricType) {
                    "HEART_RATE" -> "❤️"
                    "DISTANCE" -> "📍"
                    "ACTIVE_CALORIES" -> "🔥"
                    "WEIGHT" -> "⚖️"
                    else -> "📈"
                }
                val formattedVal = when (vital.metricType) {
                    "DISTANCE" -> String.format("%.2f km", vital.valuePrimary / 1000.0)
                    "WEIGHT" -> String.format("%.1f kg", vital.valuePrimary)
                    else -> "${vital.valuePrimary.toInt()} ${vital.unit}"
                }
                Surface(
                    modifier = Modifier.widthIn(max = 600.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("$emoji $formattedVal", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "${vital.sourceDisplayName} • ${vital.dayKey} ${DateEpochUtils.formatTime(vital.recordedAtEpochMs)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { viewModel.deleteVitalMetric(vital.id) }) {
                            Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete reading")
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(22.dp)) }
    }
}

@Composable
private fun SyncedMetricChip(
    emoji: String,
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("$emoji $label", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium.copy(
                    fontFamily = JetBrainsMonoMetricFont,
                    fontWeight = FontWeight.Bold
                )
            )
        }
    }
}
