package com.example.domain

import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.DietRecordEntity
import com.example.data.local.entity.UserProfileEntity
import kotlin.math.roundToInt

enum class NutritionGoalType(val label: String, val emoji: String, val calorieDelta: Int) {
    BALANCED("Balanced Everyday", "⚖️", 0),
    WEIGHT_LOSS("Gentle Fat Loss", "🌿", -350),
    MUSCLE_PROTEIN("High Protein & Strength", "💪", 200),
    SUGAR_BALANCE("Low-GI Sugar Balance", "🌾", -100),
    IRON_PCOS_SUPPORT("Iron & Cycle Support", "🌺", 0)
}

data class DailyNutritionTarget(
    val bmrCalories: Int,
    val tdeeCalories: Int,
    val targetCalories: Int,
    val targetProteinGrams: Int,
    val targetCarbsGrams: Int,
    val targetFatGrams: Int,
    val targetFiberGrams: Int,
    val goal: NutritionGoalType,
    val dietaryPreference: IndianDietaryPreference,
    val summaryNote: String
)

data class DailyNutritionProgress(
    val dayKey: String,
    val consumedCalories: Int,
    val consumedProteinGrams: Int,
    val consumedCarbsGrams: Int,
    val consumedFatGrams: Int,
    val todayMealCount: Int,
    val syncedMealCount: Int,
    val manualMealCount: Int,
    val target: DailyNutritionTarget
) {
    val remainingCalories: Int get() = (target.targetCalories - consumedCalories).coerceAtLeast(0)
    val remainingProteinGrams: Int get() = (target.targetProteinGrams - consumedProteinGrams).coerceAtLeast(0)
    val calorieProgress: Float
        get() = if (target.targetCalories > 0) (consumedCalories.toFloat() / target.targetCalories).coerceIn(0f, 1.5f) else 0f
    val proteinProgress: Float
        get() = if (target.targetProteinGrams > 0) (consumedProteinGrams.toFloat() / target.targetProteinGrams).coerceIn(0f, 1.5f) else 0f
    val carbsProgress: Float
        get() = if (target.targetCarbsGrams > 0) (consumedCarbsGrams.toFloat() / target.targetCarbsGrams).coerceIn(0f, 1.5f) else 0f
    val fatProgress: Float
        get() = if (target.targetFatGrams > 0) (consumedFatGrams.toFloat() / target.targetFatGrams).coerceIn(0f, 1.5f) else 0f
}

data class ScaledIndianMeal(
    val item: IndianFoodItem,
    val multiplier: Float,
    val formattedName: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val fiberGrams: Int
)

data class PlannedIndianMealSlot(
    val mealType: String, // "Breakfast", "Snack", "Lunch", "Dinner"
    val slotLabel: String, // e.g., "Breakfast", "Mid-Morning", "Lunch", "Evening Snack", "Dinner"
    val dishName: String,
    val portionLabel: String,
    val calories: Int,
    val proteinGrams: Int,
    val carbsGrams: Int,
    val fatGrams: Int,
    val prepTip: String = ""
)

data class IndianMealPlanResult(
    val planTitle: String,
    val regionLabel: String,
    val dietaryLabel: String,
    val goalLabel: String,
    val regionalNotes: String,
    val meals: List<PlannedIndianMealSlot>,
    val totalCalories: Int,
    val totalProteinGrams: Int,
    val totalCarbsGrams: Int,
    val totalFatGrams: Int,
    val groceryHighlights: List<String>,
    val avoidedAllergens: List<String>,
    val disclaimer: String,
    val isFromCloudAi: Boolean
)

object IndianNutritionEngine {

    const val STANDARD_NUTRITION_DISCLAIMER =
        "Informational Indian wellness & meal planning guidance only — not medical nutrition therapy. Check ingredients against personal allergies."

    fun calculateTargets(
        profile: UserProfileEntity?,
        goal: NutritionGoalType = NutritionGoalType.BALANCED,
        dietaryPreference: IndianDietaryPreference = IndianDietaryPreference.VEGETARIAN,
        customCalorieTarget: Int? = null
    ): DailyNutritionTarget {
        val age = (profile?.age ?: 28).coerceIn(13, 100)
        val weightKg = (profile?.weightKg ?: 64f).coerceIn(35f, 180f)
        val heightCm = (profile?.heightCm ?: 166).coerceIn(120, 220)
        val genderOffset = when {
            profile?.genderIdentity.equals("Male", ignoreCase = true) -> 5.0
            profile?.genderIdentity.equals("Female", ignoreCase = true) -> -161.0
            else -> -78.0
        }
        val bmr = ((10.0 * weightKg) + (6.25 * heightCm) - (5.0 * age) + genderOffset)
            .roundToInt()
            .coerceIn(1100, 3200)

        val activityMultiplier = when (profile?.activityLevel) {
            "Active" -> 1.65
            "Moderate" -> 1.45
            else -> 1.3
        }
        val tdee = (bmr * activityMultiplier).roundToInt().coerceIn(1300, 4200)
        val rawTarget = customCalorieTarget?.takeIf { it in 1200..4500 }
            ?: (tdee + goal.calorieDelta).coerceIn(1350, 3600)

        // Macro splits tailored for Indian diets and wellness goals
        val (proteinRatio, fatRatio, fiberTarget) = when (goal) {
            NutritionGoalType.MUSCLE_PROTEIN -> Triple(0.26, 0.25, 32)
            NutritionGoalType.WEIGHT_LOSS -> Triple(0.24, 0.27, 32)
            NutritionGoalType.SUGAR_BALANCE -> Triple(0.22, 0.28, 35)
            NutritionGoalType.IRON_PCOS_SUPPORT -> Triple(0.22, 0.27, 34)
            NutritionGoalType.BALANCED -> Triple(0.18, 0.27, 28)
        }
        val proteinCalories = rawTarget * proteinRatio
        val fatCalories = rawTarget * fatRatio
        val carbCalories = (rawTarget - proteinCalories - fatCalories).coerceAtLeast(rawTarget * 0.35)

        val proteinG = (proteinCalories / 4.0).roundToInt().coerceAtLeast(45)
        val fatG = (fatCalories / 9.0).roundToInt().coerceAtLeast(35)
        val carbsG = (carbCalories / 4.0).roundToInt().coerceAtLeast(100)

        val hasProfileStats = profile?.heightCm != null && profile.weightKg != null
        val note = if (hasProfileStats) {
            "Mifflin-St Jeor estimate (${heightCm}cm, ${weightKg.toInt()}kg, ${profile?.activityLevel ?: "Moderate"}) adjusted for ${goal.label}."
        } else {
            "Baseline wellness estimate (add height & weight in Profile for tighter BMR calibration)."
        }

        return DailyNutritionTarget(
            bmrCalories = bmr,
            tdeeCalories = tdee,
            targetCalories = rawTarget,
            targetProteinGrams = proteinG,
            targetCarbsGrams = carbsG,
            targetFatGrams = fatG,
            targetFiberGrams = fiberTarget,
            goal = goal,
            dietaryPreference = dietaryPreference,
            summaryNote = note
        )
    }

    fun evaluateTodayIntake(
        dietRecords: List<DietRecordEntity>,
        target: DailyNutritionTarget,
        dayKey: String = DateEpochUtils.todayDayKey()
    ): DailyNutritionProgress {
        val todaysRecords = dietRecords.filter {
            DateEpochUtils.todayDayKey(it.loggedAtEpochMs) == dayKey
        }
        val syncedCount = todaysRecords.count { isHealthConnectSyncedRecord(it) }
        return DailyNutritionProgress(
            dayKey = dayKey,
            consumedCalories = todaysRecords.sumOf { it.calories },
            consumedProteinGrams = todaysRecords.sumOf { it.proteinGrams },
            consumedCarbsGrams = todaysRecords.sumOf { it.carbsGrams },
            consumedFatGrams = todaysRecords.sumOf { it.fatGrams },
            todayMealCount = todaysRecords.size,
            syncedMealCount = syncedCount,
            manualMealCount = todaysRecords.size - syncedCount,
            target = target
        )
    }

    fun isHealthConnectSyncedRecord(record: DietRecordEntity): Boolean {
        return record.foodSummary.contains("[Health Connect", ignoreCase = true) ||
            record.foodSummary.contains("⌚")
    }

    fun scaleFood(item: IndianFoodItem, servings: Float): ScaledIndianMeal {
        val factor = servings.coerceIn(0.25f, 4f)
        val ratioLabel = when {
            kotlin.math.abs(factor - 0.5f) < 0.05f -> "0.5×"
            kotlin.math.abs(factor - 1f) < 0.05f -> "1×"
            kotlin.math.abs(factor - 1.5f) < 0.05f -> "1.5×"
            kotlin.math.abs(factor - 2f) < 0.05f -> "2×"
            else -> String.format("%.1f×", factor)
        }
        val title = if (ratioLabel == "1×") {
            "🇮🇳 ${item.name} (${item.portionSummary})"
        } else {
            "🇮🇳 $ratioLabel ${item.name}"
        }
        return ScaledIndianMeal(
            item = item,
            multiplier = factor,
            formattedName = title,
            calories = (item.calories * factor).roundToInt(),
            proteinGrams = (item.proteinGrams * factor).roundToInt(),
            carbsGrams = (item.carbsGrams * factor).roundToInt(),
            fatGrams = (item.fatGrams * factor).roundToInt(),
            fiberGrams = (item.fiberGrams * factor).roundToInt()
        )
    }

    fun matchesDietaryPreference(item: IndianFoodItem, pref: IndianDietaryPreference): Boolean {
        return when (pref) {
            IndianDietaryPreference.ALL_NON_VEG -> true
            IndianDietaryPreference.EGGETARIAN -> IndianFoodTag.NON_VEG !in item.tags
            IndianDietaryPreference.VEGETARIAN ->
                IndianFoodTag.NON_VEG !in item.tags && IndianFoodTag.EGGETARIAN !in item.tags
            IndianDietaryPreference.VEGAN -> IndianFoodTag.VEGAN in item.tags
            IndianDietaryPreference.JAIN -> IndianFoodTag.JAIN_FRIENDLY in item.tags
        }
    }

    fun findAllergenMatches(item: IndianFoodItem, allergies: List<AllergyRecordEntity>): List<String> {
        if (allergies.isEmpty()) return emptyList()
        val allergenTokens = allergies
            .map { it.allergen.trim().lowercase() }
            .filter { it.isNotBlank() }
        if (allergenTokens.isEmpty()) return emptyList()

        val haystacks = (item.commonAllergens.map { it.lowercase() } + item.name.lowercase() + item.portionSummary.lowercase())
        return allergies.mapNotNull { record ->
            val token = record.allergen.trim()
            if (token.isBlank()) return@mapNotNull null
            val lower = token.lowercase()
            val matched = haystacks.any { h ->
                h.contains(lower) || lower.contains(h) ||
                    (lower.contains("milk") && h.contains("dairy")) ||
                    (lower.contains("lactose") && h.contains("dairy")) ||
                    (lower.contains("nut") && (h.contains("peanut") || h.contains("cashew")))
            }
            if (matched) token else null
        }.distinct()
    }

    fun filterFoods(
        query: String = "",
        region: IndianRegion = IndianRegion.ALL,
        dietaryPreference: IndianDietaryPreference = IndianDietaryPreference.ALL_NON_VEG,
        onlyHighProtein: Boolean = false,
        onlyLowGi: Boolean = false,
        mealSlot: String? = null,
        allergies: List<AllergyRecordEntity> = emptyList()
    ): List<IndianFoodItem> {
        val cleanQuery = query.trim().lowercase()
        return IndianFoodCatalog.foods.filter { item ->
            val regionOk = region == IndianRegion.ALL || item.region == region
            val dietOk = matchesDietaryPreference(item, dietaryPreference)
            val proteinOk = !onlyHighProtein || IndianFoodTag.HIGH_PROTEIN in item.tags || item.proteinGrams >= 14
            val lowGiOk = !onlyLowGi || IndianFoodTag.LOW_GI in item.tags
            val slotOk = mealSlot.isNullOrBlank() || mealSlot == "All" || mealSlot in item.mealSlots
            val queryOk = cleanQuery.isEmpty() ||
                item.name.lowercase().contains(cleanQuery) ||
                item.portionSummary.lowercase().contains(cleanQuery) ||
                item.nutritionHighlight.lowercase().contains(cleanQuery)
            val safeFromAllergies = findAllergenMatches(item, allergies).isEmpty()
            regionOk && dietOk && proteinOk && lowGiOk && slotOk && queryOk && safeFromAllergies
        }
    }

    fun generateOfflineIndianMealPlan(
        region: IndianRegion,
        dietaryPreference: IndianDietaryPreference,
        target: DailyNutritionTarget,
        allergies: List<AllergyRecordEntity> = emptyList()
    ): IndianMealPlanResult {
        val allergenNames = allergies.map { it.allergen.trim() }.filter { it.isNotBlank() }.distinct()
        val eligible = IndianFoodCatalog.foods.filter { item ->
            matchesDietaryPreference(item, dietaryPreference) && findAllergenMatches(item, allergies).isEmpty()
        }.ifEmpty {
            IndianFoodCatalog.foods.filter { findAllergenMatches(it, allergies).isEmpty() }
        }.ifEmpty {
            IndianFoodCatalog.foods
        }

        fun pickForSlot(slot: String, excludeIds: Set<String>, preferHighProtein: Boolean): IndianFoodItem {
            val slotMatches = eligible.filter { slot in it.mealSlots && it.id !in excludeIds }
            val regional = if (region == IndianRegion.ALL) {
                slotMatches
            } else {
                slotMatches.filter { it.region == region }.ifEmpty {
                    slotMatches.filter { it.region == IndianRegion.PAN_INDIAN }
                }.ifEmpty { slotMatches }
            }
            val prioritized = when {
                target.goal == NutritionGoalType.SUGAR_BALANCE ->
                    regional.sortedByDescending { (if (IndianFoodTag.LOW_GI in it.tags) 10 else 0) + it.fiberGrams }
                target.goal == NutritionGoalType.IRON_PCOS_SUPPORT ->
                    regional.sortedByDescending { (if (IndianFoodTag.IRON_RICH in it.tags) 10 else 0) + it.proteinGrams }
                preferHighProtein || target.goal == NutritionGoalType.MUSCLE_PROTEIN ->
                    regional.sortedByDescending { it.proteinGrams }
                target.goal == NutritionGoalType.WEIGHT_LOSS ->
                    regional.sortedBy { kotlin.math.abs(it.calories - 320) - (it.proteinGrams * 4) }
                else ->
                    regional.sortedByDescending { it.proteinGrams + it.fiberGrams }
            }
            return prioritized.firstOrNull()
                ?: eligible.firstOrNull { it.id !in excludeIds }
                ?: eligible.first()
        }

        val used = mutableSetOf<String>()
        val breakfast = pickForSlot("Breakfast", used, preferHighProtein = false).also { used += it.id }
        val midMorning = pickForSlot("Snack", used, preferHighProtein = false).also { used += it.id }
        val lunch = pickForSlot("Lunch", used, preferHighProtein = true).also { used += it.id }
        val eveningSnack = pickForSlot("Snack", used, preferHighProtein = true).also { used += it.id }
        val dinner = pickForSlot("Dinner", used, preferHighProtein = true).also { used += it.id }

        // Scale portions mildly to bring total closer to targetCalories when needed
        val baseTotal = breakfast.calories + midMorning.calories + lunch.calories + eveningSnack.calories + dinner.calories
        val scaleFactor = if (baseTotal > 0) {
            (target.targetCalories.toFloat() / baseTotal.toFloat()).coerceIn(0.8f, 1.35f)
        } else 1f

        fun toSlot(item: IndianFoodItem, slotLabel: String, mealType: String, factor: Float): PlannedIndianMealSlot {
            val cals = (item.calories * factor).roundToInt()
            val p = (item.proteinGrams * factor).roundToInt()
            val c = (item.carbsGrams * factor).roundToInt()
            val f = (item.fatGrams * factor).roundToInt()
            val portionSuffix = when {
                factor >= 1.2f -> " (~1.25× portion)"
                factor <= 0.85f -> " (~0.85× light portion)"
                else -> ""
            }
            return PlannedIndianMealSlot(
                mealType = mealType,
                slotLabel = slotLabel,
                dishName = item.name,
                portionLabel = "${item.portionSummary}$portionSuffix",
                calories = cals,
                proteinGrams = p,
                carbsGrams = c,
                fatGrams = f,
                prepTip = item.nutritionHighlight
            )
        }

        val slots = listOf(
            toSlot(breakfast, "Breakfast", "Breakfast", scaleFactor),
            toSlot(midMorning, "Mid-Morning", "Snack", 1f),
            toSlot(lunch, "Lunch", "Lunch", scaleFactor),
            toSlot(eveningSnack, "Evening Snack", "Snack", 1f),
            toSlot(dinner, "Dinner", "Dinner", scaleFactor)
        )

        val groceryHighlights = listOfNotNull(
            breakfast.name.substringBefore("&").trim(),
            lunch.name.substringBefore("&").trim(),
            dinner.name.substringBefore("&").trim(),
            "Fresh seasonal greens, lemon, ginger & spices"
        ).distinct()

        return IndianMealPlanResult(
            planTitle = "${region.label} ${target.goal.label} Plan",
            regionLabel = region.label,
            dietaryLabel = dietaryPreference.label,
            goalLabel = target.goal.label,
            regionalNotes = "Offline regional plan calibrated for ~${target.targetCalories} kcal & ${target.targetProteinGrams}g protein using ${region.label} staples (${dietaryPreference.label}).",
            meals = slots,
            totalCalories = slots.sumOf { it.calories },
            totalProteinGrams = slots.sumOf { it.proteinGrams },
            totalCarbsGrams = slots.sumOf { it.carbsGrams },
            totalFatGrams = slots.sumOf { it.fatGrams },
            groceryHighlights = groceryHighlights,
            avoidedAllergens = allergenNames,
            disclaimer = STANDARD_NUTRITION_DISCLAIMER,
            isFromCloudAi = false
        )
    }
}
