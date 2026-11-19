package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "nutrition_preferences",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"], unique = true)]
)
data class NutritionPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val preferredRegion: String = "SOUTH",
    val dietaryPreference: String = "VEGETARIAN",
    val nutritionGoal: String = "BALANCED",
    val customCalorieTarget: Int? = null,
    val avoidVaultAllergens: Boolean = true,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "nutrition_plans",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["createdAtEpochMs"])
    ]
)
data class NutritionPlanEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val planTitle: String,
    val regionLabel: String,
    val dietaryLabel: String,
    val goalLabel: String,
    val regionalNotes: String,
    val totalCalories: Int,
    val totalProteinGrams: Int,
    val totalCarbsGrams: Int,
    val totalFatGrams: Int,
    val mealsJson: String,
    val groceryHighlightsJson: String,
    val avoidedAllergensCsv: String = "",
    val isFromCloudAi: Boolean = false,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)
