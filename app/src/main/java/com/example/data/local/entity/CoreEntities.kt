package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "user_profiles")
data class UserProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fullName: String,
    val age: Int,
    val genderIdentity: String, // "Female", "Male", "Non-Binary", "Prefer not to say", "Custom"
    val customGenderLabel: String = "",
    val heightCm: Int? = null,
    val weightKg: Float? = null,
    val activityLevel: String = "Moderate", // "Light", "Moderate", "Active"
    val dailyWaterGoalMl: Int = 2500,
    val cycleTrackingEnabled: Boolean = true,
    val averageCycleLengthDays: Int = 28,
    val averagePeriodDurationDays: Int = 5,
    val preferredLanguageCode: String = "en",
    val createdAtEpochMs: Long = System.currentTimeMillis(),
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "water_records",
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
        Index(value = ["dayKey"])
    ]
)
data class WaterRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val amountMl: Int,
    val drinkType: String = "Water", // "Water", "Herbal Tea", "Coconut Water", "Infused"
    val note: String = "",
    val dayKey: String, // Format: yyyy-MM-dd
    val loggedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "period_records",
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
        Index(value = ["startDateEpochDay"])
    ]
)
data class PeriodRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val startDateEpochDay: Long,
    val endDateEpochDay: Long? = null,
    val flowIntensity: String = "Medium", // "Spotting", "Light", "Medium", "Heavy"
    val symptomsCsv: String = "",
    val mood: String = "Calm",
    val notes: String = "",
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "ai_preferences",
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
data class AiPreferenceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val onDeviceAgentEnabled: Boolean = false,
    val proactiveTipsEnabled: Boolean = true,
    val preferredTone: String = "Gentle",
    val voiceInputEnabled: Boolean = false,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(tableName = "module_activation_status")
data class ModuleActivationEntity(
    @PrimaryKey val moduleKey: String,
    val displayName: String,
    val description: String,
    val category: String,
    val isAlwaysFree: Boolean,
    val isBuiltInPhaseOne: Boolean,
    val isActivated: Boolean,
    val activatedAtEpochMs: Long? = null,
    val licenseReference: String? = null,
    val signatureDigest: String? = null
)
