package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "daily_routines",
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
        Index(value = ["timeSlot"])
    ]
)
data class DailyRoutineEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val title: String,
    val emoji: String = "🌿",
    val timeSlot: String = "Morning", // "Morning", "Midday", "Evening", "Anytime"
    val triggerTime24h: String = "08:00",
    val estimatedMinutes: Int = 10,
    val stepsSerialized: String = "",
    val linkedModuleKey: String = "",
    val reminderId: Long? = null,
    val reminderEnabled: Boolean = false,
    val isOptional: Boolean = true,
    val isActive: Boolean = true,
    val sortOrder: Int = 0,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "routine_completions",
    foreignKeys = [
        ForeignKey(
            entity = UserProfileEntity::class,
            parentColumns = ["id"],
            childColumns = ["profileId"],
            onDelete = ForeignKey.CASCADE
        ),
        ForeignKey(
            entity = DailyRoutineEntity::class,
            parentColumns = ["id"],
            childColumns = ["routineId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["profileId"]),
        Index(value = ["routineId"]),
        Index(value = ["dayKey"]),
        Index(value = ["profileId", "routineId", "dayKey"], unique = true)
    ]
)
data class RoutineCompletionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val routineId: Long,
    val dayKey: String,
    val completedStepsMask: Int = 1,
    val completedStepsCount: Int = 1,
    val totalStepsCount: Int = 1,
    val reflectionNote: String = "",
    val completedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "mood_journal_entries",
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
        Index(value = ["loggedAtEpochMs"])
    ]
)
data class MoodJournalEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val dayKey: String,
    val moodLabel: String, // "Joyful", "Calm", "Focused", "Grateful", "Tired", "Stressed", "Low"
    val moodEmoji: String = "😌",
    val moodScore: Int = 4, // 1..5
    val energyLevel: Int = 3, // 1..5
    val stressLevel: Int = 2, // 1..5
    val tagsCsv: String = "",
    val reflectionNote: String = "",
    val gratitudeText: String = "",
    val loggedAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "wellness_challenges",
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
        Index(value = ["profileId", "challengeKey"], unique = true)
    ]
)
data class WellnessChallengeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val challengeKey: String,
    val title: String,
    val emoji: String = "🌱",
    val category: String = "Hydration",
    val description: String,
    val targetDays: Int = 7,
    val currentProgressDays: Int = 0,
    val status: String = "AVAILABLE", // "AVAILABLE", "ACTIVE", "PAUSED", "COMPLETED"
    val isOptInOnly: Boolean = true,
    val startDayKey: String = "",
    val lastCheckInDayKey: String = "",
    val completedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "achievement_badges",
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
        Index(value = ["profileId", "badgeKey"], unique = true)
    ]
)
data class AchievementBadgeEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val profileId: Long,
    val badgeKey: String,
    val title: String,
    val emoji: String = "🏅",
    val description: String,
    val category: String = "Routines",
    val earnedAtEpochMs: Long? = null,
    val progressCurrent: Int = 0,
    val progressTarget: Int = 1
)
