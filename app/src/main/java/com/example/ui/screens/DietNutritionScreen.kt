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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.HealthAndSafety
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.DateEpochUtils
import com.example.domain.IndianDietaryPreference
import com.example.domain.IndianNutritionEngine
import com.example.domain.IndianRegion
import com.example.domain.NutritionGoalType
import com.example.ui.components.DateTimeSelectorRow
import com.example.ui.components.QuickAmountChip
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.viewmodel.ModulesHubUiState
import com.example.ui.viewmodel.ModulesHubViewModel

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DietNutritionScreen(
    uiState: ModulesHubUiState,
    viewModel: ModulesHubViewModel,
    onBack: () -> Unit,
    onOpenConnectedDevices: () -> Unit = {}
) {
    var showMealDialog by remember { mutableStateOf(false) }
    var showCalorieTargetDialog by remember { mutableStateOf(false) }

    var catalogSearchQuery by remember { mutableStateOf("") }
    var catalogRegionFilter by remember(uiState.selectedIndianRegion) {
        mutableStateOf(uiState.selectedIndianRegion)
    }
    var onlyHighProtein by remember { mutableStateOf(false) }
    var onlyLowGi by remember { mutableStateOf(false) }
    var selectedMealSlot by remember { mutableStateOf("Lunch") }
    var servingMultiplier by remember { mutableFloatStateOf(1f) }

    val progress = uiState.todayNutritionProgress
    val target = uiState.nutritionTarget
    val activeAllergies = if (uiState.avoidVaultAllergens) uiState.vaultAllergies else emptyList()

    val filteredCatalog = remember(
        catalogSearchQuery,
        catalogRegionFilter,
        uiState.selectedDietaryPreference,
        onlyHighProtein,
        onlyLowGi,
        activeAllergies
    ) {
        IndianNutritionEngine.filterFoods(
            query = catalogSearchQuery,
            region = catalogRegionFilter,
            dietaryPreference = uiState.selectedDietaryPreference,
            onlyHighProtein = onlyHighProtein,
            onlyLowGi = onlyLowGi,
            allergies = activeAllergies
        )
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
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("diet_back_button")
                ) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("🥗 Diet & Indian Nutrition", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Regional Indian meal planning, macro targets & manual tracking",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(
                    onClick = { showCalorieTargetDialog = true },
                    modifier = Modifier.testTag("diet_calibrate_target_button")
                ) {
                    Icon(Icons.Rounded.Tune, contentDescription = "Calibrate calorie target")
                }
            }
        }

        // Daily Nutrition Target & Today's Progress Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("🔥 Today's Nutrition Target", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${target.goal.emoji} ${target.goal.label} • BMR ${target.bmrCalories} / TDEE ${target.tdeeCalories}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "${progress.consumedCalories} / ${target.targetCalories} kcal",
                            style = MaterialTheme.typography.titleSmall.copy(
                                fontFamily = JetBrainsMonoMetricFont,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    LinearProgressIndicator(
                        progress = { progress.calorieProgress.coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                            .clip(CircleShape)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        MacroBarTile(
                            label = "💪 Protein",
                            consumed = progress.consumedProteinGrams,
                            target = target.targetProteinGrams,
                            ratio = progress.proteinProgress,
                            modifier = Modifier.weight(1f)
                        )
                        MacroBarTile(
                            label = "🌾 Carbs",
                            consumed = progress.consumedCarbsGrams,
                            target = target.targetCarbsGrams,
                            ratio = progress.carbsProgress,
                            modifier = Modifier.weight(1f)
                        )
                        MacroBarTile(
                            label = "🥑 Fat",
                            consumed = progress.consumedFatGrams,
                            target = target.targetFatGrams,
                            ratio = progress.fatProgress,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text(
                        text = target.summaryNote,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Today: ${progress.manualMealCount} manual • ${progress.syncedMealCount} Health Connect (${progress.remainingCalories} kcal left)",
                                style = MaterialTheme.typography.labelMedium,
                                modifier = Modifier.weight(1f)
                            )
                            TextButton(
                                onClick = onOpenConnectedDevices,
                                modifier = Modifier.testTag("diet_open_hc_button")
                            ) {
                                Text("Sync HC")
                            }
                        }
                    }
                }
            }
        }

        // AI Indian Nutrition Planner Card
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
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "✨ ${stringResource(R.string.nutrition_indian_planner_title)}",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = "Regional thali & ghar-ka-khana planner with allergy safety",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Text("Region", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IndianRegion.entries.forEach { region ->
                            FilterChip(
                                selected = uiState.selectedIndianRegion == region,
                                onClick = {
                                    catalogRegionFilter = region
                                    viewModel.updateNutritionPreferences(region = region)
                                },
                                label = { Text("${region.emoji} ${region.label}") },
                                modifier = Modifier.testTag("region_chip_${region.name}")
                            )
                        }
                    }

                    Text("Dietary Style", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IndianDietaryPreference.entries.forEach { pref ->
                            FilterChip(
                                selected = uiState.selectedDietaryPreference == pref,
                                onClick = { viewModel.updateNutritionPreferences(dietary = pref) },
                                label = { Text("${pref.emoji} ${pref.label}") },
                                modifier = Modifier.testTag("dietary_chip_${pref.name}")
                            )
                        }
                    }

                    Text("Wellness Goal", style = MaterialTheme.typography.labelLarge)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        NutritionGoalType.entries.forEach { goal ->
                            FilterChip(
                                selected = uiState.selectedNutritionGoal == goal,
                                onClick = { viewModel.updateNutritionPreferences(goal = goal) },
                                label = { Text("${goal.emoji} ${goal.label}") },
                                modifier = Modifier.testTag("goal_chip_${goal.name}")
                            )
                        }
                    }

                    // Vault Allergen Avoidance Switch
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    Icons.Rounded.HealthAndSafety,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text("Exclude Health Vault Allergies", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        text = if (uiState.vaultAllergies.isEmpty()) {
                                            "No allergies recorded in Vault"
                                        } else {
                                            "Avoiding: ${uiState.vaultAllergies.joinToString(", ") { it.allergen }}"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Switch(
                                checked = uiState.avoidVaultAllergens,
                                onCheckedChange = { checked ->
                                    viewModel.updateNutritionPreferences(avoidVaultAllergens = checked)
                                },
                                modifier = Modifier.testTag("avoid_vault_allergens_switch")
                            )
                        }
                    }

                    uiState.mealPlanStatusMessage?.let { status ->
                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = status,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(onClick = viewModel::clearMealPlanStatus) {
                                    Text("OK")
                                }
                            }
                        }
                    }

                    Button(
                        onClick = viewModel::generateIndianMealPlan,
                        enabled = !uiState.isGeneratingMealPlan,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("generate_indian_plan_button")
                    ) {
                        if (uiState.isGeneratingMealPlan) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(18.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                            Text(" Planning Regional Menu...")
                        } else {
                            Icon(Icons.Rounded.AutoAwesome, contentDescription = null)
                            Text(" Generate AI Indian Meal Plan")
                        }
                    }

                    uiState.activeIndianMealPlan?.let { plan ->
                        HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(plan.planTitle, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${if (plan.isFromCloudAi) "✨ Grok AI" else "🌿 Offline Regional Plan"} • ${plan.totalCalories} kcal • P:${plan.totalProteinGrams}g C:${plan.totalCarbsGrams}g F:${plan.totalFatGrams}g",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                            OutlinedButton(
                                onClick = { viewModel.logEntireMealPlan(plan) },
                                modifier = Modifier.testTag("log_full_day_plan_button")
                            ) {
                                Text("Log All")
                            }
                        }
                        if (plan.regionalNotes.isNotBlank()) {
                            Text(
                                text = plan.regionalNotes,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        plan.meals.forEachIndexed { idx, slot ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            "${slot.slotLabel}: ${slot.dishName}",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            "${slot.portionLabel} • ${slot.calories} kcal • P:${slot.proteinGrams}g C:${slot.carbsGrams}g F:${slot.fatGrams}g",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        if (slot.prepTip.isNotBlank()) {
                                            Text(
                                                "💡 ${slot.prepTip}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                    }
                                    TextButton(
                                        onClick = { viewModel.logPlannedMealSlot(slot) },
                                        modifier = Modifier.testTag("log_planned_meal_$idx")
                                    ) {
                                        Text("+ Log")
                                    }
                                }
                            }
                        }
                        if (plan.groceryHighlights.isNotEmpty()) {
                            Text(
                                text = "🛒 Pantry Highlights: ${plan.groceryHighlights.joinToString(" • ")}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = plan.disclaimer.ifBlank { stringResource(R.string.nutrition_disclaimer) },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        // Regional Indian Food Picker Card
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
                        "🍛 Regional Indian Food Log (${filteredCatalog.size} dishes)",
                        style = MaterialTheme.typography.titleMedium
                    )
                    OutlinedTextField(
                        value = catalogSearchQuery,
                        onValueChange = { catalogSearchQuery = it },
                        label = { Text("Search dosa, dal, paneer, fish, millet, idli...") },
                        leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("indian_food_search_input")
                    )

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IndianRegion.entries.forEach { region ->
                            FilterChip(
                                selected = catalogRegionFilter == region,
                                onClick = { catalogRegionFilter = region },
                                label = { Text("${region.emoji} ${region.label.substringBefore(" ")}") }
                            )
                        }
                        FilterChip(
                            selected = onlyHighProtein,
                            onClick = { onlyHighProtein = !onlyHighProtein },
                            label = { Text("💪 High Protein") },
                            modifier = Modifier.testTag("filter_high_protein_chip")
                        )
                        FilterChip(
                            selected = onlyLowGi,
                            onClick = { onlyLowGi = !onlyLowGi },
                            label = { Text("🌾 Low GI") },
                            modifier = Modifier.testTag("filter_low_gi_chip")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Log into:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf("Breakfast", "Lunch", "Dinner", "Snack").forEach { slot ->
                                FilterChip(
                                    selected = selectedMealSlot == slot,
                                    onClick = { selectedMealSlot = slot },
                                    label = { Text(slot) }
                                )
                            }
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Serving size:", style = MaterialTheme.typography.labelLarge)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            listOf(0.5f to "0.5×", 1f to "1×", 1.5f to "1.5×", 2f to "2×").forEach { (factor, label) ->
                                FilterChip(
                                    selected = kotlin.math.abs(servingMultiplier - factor) < 0.05f,
                                    onClick = { servingMultiplier = factor },
                                    label = { Text(label) },
                                    modifier = Modifier.testTag("serving_multiplier_${label}")
                                )
                            }
                        }
                    }

                    if (filteredCatalog.isEmpty()) {
                        Text(
                            "No Indian dishes match the current search/filter or allergy filters. Adjust filters or log a custom meal below.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        filteredCatalog.take(12).forEach { food ->
                            val scaled = IndianNutritionEngine.scaleFood(food, servingMultiplier)
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("${food.region.emoji} ${food.name}", style = MaterialTheme.typography.titleSmall)
                                        Text(
                                            "${food.portionSummary} (${scaled.calories} kcal • P:${scaled.proteinGrams}g C:${scaled.carbsGrams}g F:${scaled.fatGrams}g)",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Text(
                                            food.nutritionHighlight,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.logIndianFood(
                                                item = food,
                                                servings = servingMultiplier,
                                                mealType = selectedMealSlot
                                            )
                                        },
                                        modifier = Modifier.testTag("log_indian_food_${food.id}")
                                    ) {
                                        Text("+ Log")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Manual Meal Logging Card (Preserved 100%)
        item {
            Card(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("✍️ Quick Manual Meal Log", style = MaterialTheme.typography.titleMedium)
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        QuickAmountChip("🥣 Oats & Berries") {
                            viewModel.logMeal("Breakfast", "🥣 Oats, chia & fruit", 340, 12, 54, 8)
                        }
                        QuickAmountChip("🍛 Dal & Bowl") {
                            viewModel.logMeal("Lunch", "🍛 Dal, brown rice & greens", 520, 22, 74, 12)
                        }
                        QuickAmountChip("🍎 Nuts & Fruit") {
                            viewModel.logMeal("Snack", "🍎 Apple & almonds", 210, 6, 24, 11)
                        }
                    }
                    Button(
                        onClick = { showMealDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("diet_add_meal_button")
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Text("Log Custom Meal")
                    }
                }
            }
        }

        item {
            Column(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth()
            ) {
                Text(
                    "🍽️ Logged Meals (${uiState.dietRecords.size})",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        items(uiState.dietRecords, key = { it.id }) { meal ->
            val isHcSynced = IndianNutritionEngine.isHealthConnectSyncedRecord(meal)
            Surface(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${meal.mealType}: ${meal.foodSummary}", style = MaterialTheme.typography.titleSmall)
                        Text(
                            "${if (isHcSynced) "Health Connect" else "Manual"} • ${meal.calories} kcal • P:${meal.proteinGrams}g C:${meal.carbsGrams}g F:${meal.fatGrams}g • ${DateEpochUtils.formatEpochDayAndTime(meal.loggedAtEpochMs)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(onClick = { viewModel.deleteMeal(meal.id) }) {
                        Icon(Icons.Rounded.DeleteOutline, contentDescription = "Delete meal")
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }

    if (showMealDialog) {
        var mealType by remember { mutableStateOf("Lunch") }
        var food by remember { mutableStateOf("") }
        var cals by remember { mutableStateOf("450") }
        var prot by remember { mutableStateOf("20") }
        var carbs by remember { mutableStateOf("45") }
        var fat by remember { mutableStateOf("12") }
        var selectedDay by remember { mutableLongStateOf(DateEpochUtils.todayEpochDay()) }
        val initHm = remember { DateEpochUtils.epochMsToHourMinute() }
        var selectedHour by remember { mutableIntStateOf(initHm.first) }
        var selectedMinute by remember { mutableIntStateOf(initHm.second) }
        AlertDialog(
            onDismissRequest = { showMealDialog = false },
            title = { Text("🥗 Log Custom Meal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DateTimeSelectorRow(
                        selectedEpochDay = selectedDay,
                        selectedHour = selectedHour,
                        selectedMinute = selectedMinute,
                        onEpochDayChanged = { selectedDay = it },
                        onTimeChanged = { h, m ->
                            selectedHour = h
                            selectedMinute = m
                        },
                        dateLabel = "Meal Date",
                        timeLabel = "Meal Time",
                        testTagPrefix = "diet_dt"
                    )
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("🍳 Breakfast", "🥗 Lunch", "🍲 Dinner", "🍎 Snack").forEach { label ->
                            val clean = label.substringAfter(" ")
                            FilterChip(selected = mealType == clean, onClick = { mealType = clean }, label = { Text(label) })
                        }
                    }
                    OutlinedTextField(
                        value = food,
                        onValueChange = { food = it },
                        label = { Text("What did you enjoy?") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = cals,
                        onValueChange = { if (it.all(Char::isDigit)) cals = it },
                        label = { Text("Calories (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = prot,
                            onValueChange = { if (it.all(Char::isDigit)) prot = it },
                            label = { Text("Protein (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = carbs,
                            onValueChange = { if (it.all(Char::isDigit)) carbs = it },
                            label = { Text("Carbs (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = fat,
                            onValueChange = { if (it.all(Char::isDigit)) fat = it },
                            label = { Text("Fat (g)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    val ts = DateEpochUtils.combineEpochDayAndTime(selectedDay, selectedHour, selectedMinute)
                    viewModel.logMeal(
                        mealType = mealType,
                        foodSummary = food.ifBlank { "Balanced meal" },
                        calories = cals.toIntOrNull() ?: 350,
                        protein = prot.toIntOrNull() ?: 15,
                        carbs = carbs.toIntOrNull() ?: 45,
                        fat = fat.toIntOrNull() ?: 12,
                        loggedAtEpochMs = ts
                    )
                    showMealDialog = false
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showMealDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCalorieTargetDialog) {
        var customKcalText by remember {
            mutableStateOf(uiState.nutritionPreference?.customCalorieTarget?.toString() ?: target.targetCalories.toString())
        }
        AlertDialog(
            onDismissRequest = { showCalorieTargetDialog = false },
            title = { Text("Calibrate Calorie Target") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Your estimated TDEE is ${target.tdeeCalories} kcal. Set a custom daily calorie goal or reset to auto.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    OutlinedTextField(
                        value = customKcalText,
                        onValueChange = { if (it.all(Char::isDigit)) customKcalText = it },
                        label = { Text("Daily Target (kcal)") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val parsed = customKcalText.toIntOrNull()?.coerceIn(1200, 4500)
                    viewModel.updateNutritionPreferences(customCalorieTarget = parsed)
                    showCalorieTargetDialog = false
                }) { Text("Save Target") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = {
                        viewModel.updateNutritionPreferences(customCalorieTarget = null)
                        showCalorieTargetDialog = false
                    }) { Text("Auto") }
                    TextButton(onClick = { showCalorieTargetDialog = false }) { Text("Cancel") }
                }
            }
        )
    }
}

@Composable
private fun MacroBarTile(
    label: String,
    consumed: Int,
    target: Int,
    ratio: Float,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                "${consumed}g / ${target}g",
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = JetBrainsMonoMetricFont)
            )
            LinearProgressIndicator(
                progress = { ratio.coerceIn(0f, 1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
            )
        }
    }
}
