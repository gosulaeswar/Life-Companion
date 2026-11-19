package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.ui.components.ModuleStatusPill
import com.example.ui.theme.AiStudioMidnight
import com.example.ui.theme.HeroTealDeep
import com.example.ui.viewmodel.ModulesHubUiState

private data class ModuleTileSpec(
    val key: String,
    val emoji: String,
    val title: String,
    val liveSummary: String,
    val badgeText: String,
    val isFree: Boolean,
    val tint: Color,
    val route: String
)

object ModuleSubRoutes {
    const val ROUTE_FITNESS_YOGA = "module_fitness_yoga"
    const val ROUTE_DIET = "module_diet"
    const val ROUTE_MEDICAL = "module_medical"
    const val ROUTE_BEAUTY = "module_beauty"
    const val ROUTE_WELLBEING = "module_wellbeing"
    const val ROUTE_REPORTS = "module_reports"
    const val ROUTE_ON_DEVICE_AI = "module_ai"
    const val ROUTE_CONNECTED_DEVICES = "module_connected_devices"
    const val ROUTE_HEALTH_VAULT = "module_health_vault"
    const val ROUTE_FAMILY_CIRCLE = "module_family_circle"
    const val ROUTE_SUBSCRIPTION = "module_subscription"
    const val ROUTE_ROUTINES_MOOD = "module_routines_mood"
    const val ROUTE_MOOD_JOURNAL = "module_mood_journal"
    const val ROUTE_CHALLENGES = "module_challenges"
}

@Composable
fun ModulesHubScreen(
    uiState: ModulesHubUiState,
    onOpenWater: () -> Unit,
    onOpenPeriod: () -> Unit,
    onOpenReminders: () -> Unit,
    onNavigateModule: (String) -> Unit
) {
    val activeMap = uiState.modules.associateBy { it.moduleKey }

    val totalExpectedDoses = uiState.medicines.filter { it.isActive }.sumOf { it.dosesPerDay.coerceAtLeast(1) }
    val takenDosesToday = uiState.todayMedicineIntakes.count { it.status == "Taken" }
    val pendingFollowUps = uiState.consultations.count { it.followUpEpochDay != null && !it.isFollowUpCompleted }

    val tiles = listOf(
        ModuleTileSpec(
            key = "water_tracker",
            emoji = "💧",
            title = "Water & Hydration",
            liveSummary = "${uiState.hydration.totalTodayMl}/${uiState.hydration.goalMl} ml today",
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.secondaryContainer,
            route = "water"
        ),
        ModuleTileSpec(
            key = "period_tracker",
            emoji = "🌸",
            title = "Cycle & Period",
            liveSummary = uiState.cycleInsights?.currentCycleDay?.let { "Day $it • ${uiState.cycleInsights.currentPhase.label}" }
                ?: "Always free & inclusive",
            badgeText = "Always Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.tertiaryContainer,
            route = "period"
        ),
        ModuleTileSpec(
            key = "medical_care",
            emoji = "💊",
            title = "Doctors & Medicines",
            liveSummary = if (totalExpectedDoses > 0 || pendingFollowUps > 0) {
                "$takenDosesToday/$totalExpectedDoses doses • $pendingFollowUps follow-ups"
            } else {
                "Doses, intervals & follow-ups"
            },
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.85f),
            route = ModuleSubRoutes.ROUTE_MEDICAL
        ),
        ModuleTileSpec(
            key = "health_vault",
            emoji = "🗂️",
            title = "Health Records Vault",
            liveSummary = "Encrypted Rx, labs, allergies & Doctor PDF",
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
            route = ModuleSubRoutes.ROUTE_HEALTH_VAULT
        ),
        ModuleTileSpec(
            key = "exercise_tracker",
            emoji = "🏃",
            title = "Fitness, Yoga & Sleep",
            liveSummary = "${uiState.exercises.sumOf { it.durationMinutes }}m active • ${uiState.todaySteps?.stepCount ?: 0} steps",
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.primaryContainer,
            route = ModuleSubRoutes.ROUTE_FITNESS_YOGA
        ),
        ModuleTileSpec(
            key = "diet_tracker",
            emoji = "🥗",
            title = "Diet & Nutrition",
            liveSummary = "${uiState.dietRecords.sumOf { it.calories }} kcal • ${uiState.dietRecords.size} meals",
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.75f),
            route = ModuleSubRoutes.ROUTE_DIET
        ),
        ModuleTileSpec(
            key = "daily_reminders",
            emoji = "⏰",
            title = "Alarms & Tones",
            liveSummary = "Custom sounds & daily alarms",
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.surfaceVariant,
            route = "settings"
        ),
        ModuleTileSpec(
            key = "reports_analytics",
            emoji = "🧠",
            title = "Health Intelligence & Reports",
            liveSummary = "${uiState.healthIntelligence.loggedModuleCount} active module(s) • Daily & weekly briefings",
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
            route = ModuleSubRoutes.ROUTE_REPORTS
        ),
        ModuleTileSpec(
            key = "beauty_care",
            emoji = "✨",
            title = "Beauty & Self-Care",
            liveSummary = "${uiState.beautyRecords.size} rituals completed",
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f),
            route = ModuleSubRoutes.ROUTE_BEAUTY
        ),
        ModuleTileSpec(
            key = "digital_wellbeing",
            emoji = "📵",
            title = "Digital Wellbeing",
            liveSummary = "${uiState.todayWellbeing?.screenTimeMinutes ?: 0}m screen • ${uiState.todayWellbeing?.focusSessionsCount ?: 0} focus",
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.surfaceVariant,
            route = ModuleSubRoutes.ROUTE_WELLBEING
        ),
        ModuleTileSpec(
            key = "connected_devices",
            emoji = "⌚",
            title = "Connected Devices",
            liveSummary = if (uiState.connectedSources.isNotEmpty()) {
                "${uiState.connectedSources.size} source(s) • Fitbit / Health Connect / Apple"
            } else {
                "Fitbit, Samsung, Garmin & Apple Health"
            },
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.secondaryContainer,
            route = ModuleSubRoutes.ROUTE_CONNECTED_DEVICES
        ),
        ModuleTileSpec(
            key = "on_device_ai",
            emoji = "🎙️",
            title = "AI Voice & Scanner",
            liveSummary = "Multilingual voice, Rx scan & quick log",
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.primaryContainer,
            route = ModuleSubRoutes.ROUTE_ON_DEVICE_AI
        ),
        ModuleTileSpec(
            key = "daily_routines",
            emoji = "🌅",
            title = "Smart Daily Routines",
            liveSummary = if (uiState.routineAdherence.activeRoutinesCount > 0) {
                "${uiState.routineAdherence.completedTodayCount}/${uiState.routineAdherence.activeRoutinesCount} done today • ${uiState.routineAdherence.weeklyConsistencyPercent}% 7d"
            } else {
                "Gentle checklists, optional smart ideas & reminders"
            },
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
            route = ModuleSubRoutes.ROUTE_ROUTINES_MOOD
        ),
        ModuleTileSpec(
            key = "mood_journal",
            emoji = "📓",
            title = "Mood Journal & Badges",
            liveSummary = if (uiState.moodEntries.isNotEmpty() || uiState.achievementBadges.any { it.earnedAtEpochMs != null }) {
                "${uiState.moodAnalytics.totalEntriesLast7Days} logs (7d) • ${uiState.achievementBadges.count { it.earnedAtEpochMs != null }} badges earned"
            } else {
                "Reflective logs, trends & optional challenges"
            },
            badgeText = "Free",
            isFree = true,
            tint = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f),
            route = ModuleSubRoutes.ROUTE_MOOD_JOURNAL
        ),
        ModuleTileSpec(
            key = "family_circle",
            emoji = "👨\u200D👩\u200D👧",
            title = "Family Circle",
            liveSummary = "Up to 5 profiles, caregiver alerts & consent sharing",
            badgeText = "Family Plan",
            isFree = true,
            tint = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.8f),
            route = ModuleSubRoutes.ROUTE_FAMILY_CIRCLE
        ),
        ModuleTileSpec(
            key = "subscription_plans",
            emoji = "💎",
            title = "LifeWeave Plans",
            liveSummary = "Free, Plus & Family • Period Tracker Always Free",
            badgeText = "Play Billing",
            isFree = true,
            tint = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.78f),
            route = ModuleSubRoutes.ROUTE_SUBSCRIPTION
        )
    )

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Column(modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth()) {
                Text(
                    text = "✨ ${stringResource(id = R.string.modules_hub_title)}",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "Explore every tracker, routine, and insight—kept 100% private on your phone.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Featured AI Voice & Scanner Studio Banner
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(24.dp))
                    .clickable { onNavigateModule(ModuleSubRoutes.ROUTE_ON_DEVICE_AI) },
                shape = RoundedCornerShape(24.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
                colors = CardDefaults.cardColors(containerColor = AiStudioMidnight)
            ) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    Image(
                        painter = painterResource(id = R.drawable.img_ai_aurora_art),
                        contentDescription = null,
                        modifier = Modifier.matchParentSize(),
                        contentScale = ContentScale.Crop,
                        alpha = 0.55f
                    )
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        AiStudioMidnight.copy(alpha = 0.85f),
                                        HeroTealDeep.copy(alpha = 0.55f)
                                    )
                                )
                            )
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "🎙️ AI Voice & Rx Studio",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White
                            )
                            Text(
                                text = "Log meals, water & prescriptions by voice or camera.",
                                style = MaterialTheme.typography.bodySmall,
                                color = Color.White.copy(alpha = 0.86f)
                            )
                        }
                        Surface(
                            shape = CircleShape,
                            color = Color(0xFF2CE5B5).copy(alpha = 0.24f),
                            border = BorderStroke(1.dp, Color(0xFF2CE5B5).copy(alpha = 0.5f))
                        ) {
                            Text(
                                text = "Open →",
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color(0xFF9DF7DF),
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp)
                            )
                        }
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier.widthIn(max = 620.dp).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                tiles.chunked(2).forEach { pair ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        pair.forEach { tile ->
                            ModuleEmojiTileCard(
                                tile = tile,
                                isActivated = activeMap[tile.key]?.isActivated ?: tile.isFree,
                                isAlwaysFree = activeMap[tile.key]?.isAlwaysFree ?: tile.isFree,
                                modifier = Modifier.weight(1f),
                                onClick = {
                                    when (tile.route) {
                                        "water" -> onOpenWater()
                                        "period" -> onOpenPeriod()
                                        "settings" -> onOpenReminders()
                                        else -> onNavigateModule(tile.route)
                                    }
                                }
                            )
                        }
                        if (pair.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun ModuleEmojiTileCard(
    tile: ModuleTileSpec,
    isActivated: Boolean,
    isAlwaysFree: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .testTag("module_tile_${tile.key}"),
        shape = RoundedCornerShape(24.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            tile.tint.copy(alpha = 0.55f),
                            MaterialTheme.colorScheme.surface
                        )
                    )
                )
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = tile.emoji, fontSize = 24.sp)
                }
                ModuleStatusPill(
                    isAlwaysFree = isAlwaysFree,
                    isActivated = isActivated,
                    isPhaseOneBuiltIn = true
                )
            }
            Text(
                text = tile.title,
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = tile.liveSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                minLines = 2
            )
        }
    }
}
