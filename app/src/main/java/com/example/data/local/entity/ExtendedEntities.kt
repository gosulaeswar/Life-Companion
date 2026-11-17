package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "medicines",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class MedicineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val name: String,
    val dosage: String,
    val scheduleTime24h: String,
    val frequency: String,
    val dosesPerDay: Int = 1,
    val intervalHours: Int = 24,
    val mealRelation: String = "After Meal",
    val categoryEmoji: String = "💊",
    val isActive: Boolean = true,
    val notes: String = ""
)

@Entity(
    tableName = "medicine_intakes",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = MedicineEntity::class,
            parentColumns = ["id"],
            childColumns = ["medicineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["medicineId"]),
        Index(value = ["dayKey"])
    ]
)
data class MedicineIntakeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val medicineId: Long,
    val medicineName: String,
    val dosageTaken: String,
    val slotLabel: String,
    val status: String = "Taken", // "Taken", "Skipped"
    val dayKey: String,
    val takenAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "exercise_records",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class ExerciseRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val activityType: String,
    val category: String, // "Exercise", "Yoga"
    val durationMinutes: Int,
    val intensity: String,
    val caloriesBurned: Int = 0,
    val loggedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "diet_records",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class DietRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val mealType: String, // "Breakfast", "Lunch", "Dinner", "Snack"
    val foodSummary: String,
    val calories: Int = 0,
    val proteinGrams: Int = 0,
    val carbsGrams: Int = 0,
    val fatGrams: Int = 0,
    val loggedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "sleep_records",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class SleepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val bedtimeEpochMs: Long,
    val wakeEpochMs: Long,
    val qualityRating: Int = 3,
    val notes: String = ""
)

@Entity(
    tableName = "step_records",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"]), Index(value = ["dayKey"])]
)
data class StepRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val dayKey: String,
    val stepCount: Int,
    val distanceMeters: Int = 0,
    val updatedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "doctor_consultations",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class DoctorConsultationEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val doctorName: String,
    val specialty: String,
    val clinicOrHospital: String = "",
    val consultationEpochMs: Long,
    val summary: String = "",
    val followUpEpochDay: Long? = null,
    val followUpNotes: String = "",
    val isFollowUpCompleted: Boolean = false
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val moduleKey: String,
    val triggerTime24h: String,
    val daysOfWeekMask: Int = 127,
    val isEnabled: Boolean = true,
    val soundKey: String = "water_drop",
    val customSoundUri: String? = null,
    val customSoundLabel: String? = null
)

@Entity(
    tableName = "beauty_care_records",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"])]
)
data class BeautyCareRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val routineType: String, // "Morning", "Evening", "Haircare", "Skincare"
    val productOrStep: String,
    val completedAtEpochMs: Long = System.currentTimeMillis(),
    val notes: String = ""
)

@Entity(
    tableName = "digital_wellbeing_records",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["profileId"]), Index(value = ["dayKey"])]
)
data class DigitalWellbeingRecordEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val dayKey: String,
    val screenTimeMinutes: Int,
    val focusSessionsCount: Int = 0,
    val mindfulGoalMinutes: Int = 120,
    val unlockCount: Int = 0,
    val appLaunchCount: Int = 0,
    val nightScreenMinutes: Int = 0,
    val longestSessionMinutes: Int = 0,
    val topAppName: String = "",
    val topAppMinutes: Int = 0,
    val topAppsJson: String = "",
    val sourceLabel: String = "Manual",
    val lastSyncedEpochMs: Long = 0L
)

@Entity(
    tableName = "connected_fitness_sources",
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
        Index(value = ["profileId", "sourceId"], unique = true)
    ]
)
data class ConnectedDeviceSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val sourceId: String,
    val displayName: String,
    val emoji: String,
    val syncChannel: String, // "Health Connect" or "File Import"
    val lastSyncedEpochMs: Long = System.currentTimeMillis(),
    val recordsImportedCount: Int = 0,
    val latestSummary: String = ""
)

@Entity(
    tableName = "health_vital_metrics",
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
        Index(value = ["dayKey"]),
        Index(value = ["metricType"])
    ]
)
data class HealthVitalMetricEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val metricType: String, // "HEART_RATE", "DISTANCE", "ACTIVE_CALORIES", "WEIGHT"
    val valuePrimary: Double,
    val unit: String, // "bpm", "m", "kcal", "kg"
    val sourceId: String,
    val sourceDisplayName: String,
    val dayKey: String,
    val recordedAtEpochMs: Long = System.currentTimeMillis()
)
