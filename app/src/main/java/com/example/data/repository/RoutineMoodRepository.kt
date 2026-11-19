package com.example.data.repository

import com.example.data.local.AppDatabase
import com.example.data.local.entity.AchievementBadgeEntity
import com.example.data.local.entity.DailyRoutineEntity
import com.example.data.local.entity.MoodJournalEntryEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.RoutineCompletionEntity
import com.example.data.local.entity.WellnessChallengeEntity
import com.example.domain.DateEpochUtils
import com.example.domain.MoodPresetOption
import com.example.domain.RoutineMoodAnalyticsEngine
import com.example.domain.RoutineTimeSlot
import com.example.domain.SuggestedRoutineTemplate
import com.example.notifications.AlertCategory
import com.example.notifications.AlertSoundManager
import com.example.notifications.BundledAlertTone
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class RoutineMoodRepository(
    private val database: AppDatabase,
    private val customAlarmRepository: CustomAlarmRepository,
    private val alertSoundManager: AlertSoundManager
) {
    private val dao = database.wellnessEngagementDao()

    fun observeRoutines(profileId: Long): Flow<List<DailyRoutineEntity>> =
        dao.observeRoutines(profileId)

    fun observeRecentCompletions(profileId: Long, daysBack: Int = 30): Flow<List<RoutineCompletionEntity>> =
        dao.observeCompletionsSince(profileId, DateEpochUtils.dayKeyDaysAgo(daysBack))

    fun observeMoodEntries(profileId: Long): Flow<List<MoodJournalEntryEntity>> =
        dao.observeMoodEntries(profileId)

    fun observeChallenges(profileId: Long): Flow<List<WellnessChallengeEntity>> =
        dao.observeChallenges(profileId)

    fun observeBadges(profileId: Long): Flow<List<AchievementBadgeEntity>> =
        dao.observeBadges(profileId)

    suspend fun ensureDefaultCatalogSeeded(profileId: Long) = withContext(Dispatchers.IO) {
        val challenges = RoutineMoodAnalyticsEngine.optionalChallengeCatalog.map { spec ->
            WellnessChallengeEntity(
                profileId = profileId,
                challengeKey = spec.challengeKey,
                title = spec.title,
                emoji = spec.emoji,
                category = spec.category,
                description = spec.description,
                targetDays = spec.targetDays,
                currentProgressDays = 0,
                status = "AVAILABLE",
                isOptInOnly = true
            )
        }
        dao.insertChallengesIfMissing(challenges)

        val badges = RoutineMoodAnalyticsEngine.achievementCatalog.map { spec ->
            AchievementBadgeEntity(
                profileId = profileId,
                badgeKey = spec.badgeKey,
                title = spec.title,
                emoji = spec.emoji,
                description = spec.description,
                category = spec.category,
                earnedAtEpochMs = null,
                progressCurrent = 0,
                progressTarget = spec.target
            )
        }
        dao.insertBadgesIfMissing(badges)
        syncAchievements(profileId)
    }

    suspend fun createOrUpdateRoutine(
        profileId: Long,
        existingId: Long = 0L,
        title: String,
        emoji: String,
        timeSlot: RoutineTimeSlot,
        triggerTime24h: String,
        estimatedMinutes: Int,
        steps: List<String>,
        linkedModuleKey: String = "",
        reminderEnabled: Boolean = false,
        reminderSoundKey: String = BundledAlertTone.WELLNESS_GENTLE.id,
        repeatIntervalMinutes: Int = 1440
    ): Long = withContext(Dispatchers.IO) {
        val cleanTitle = title.trim().ifEmpty { "Mindful Routine" }
        val existing = if (existingId > 0L) dao.getRoutineById(existingId) else null
        var linkedReminderId = existing?.reminderId

        if (reminderEnabled) {
            val reminder = ReminderEntity(
                id = linkedReminderId ?: 0L,
                profileId = profileId,
                title = cleanTitle,
                moduleKey = AlertCategory.WELLNESS.key,
                triggerTime24h = triggerTime24h,
                daysOfWeekMask = 127,
                isEnabled = true,
                soundKey = reminderSoundKey,
                alarmEmoji = emoji.ifBlank { "🌿" },
                customMessage = "Gentle optional check-in: $cleanTitle",
                repeatIntervalMinutes = repeatIntervalMinutes.coerceIn(15, 10_080)
            )
            val savedId = customAlarmRepository.saveAlarm(reminder)
            linkedReminderId = savedId
            alertSoundManager.scheduleCustomAlarm(reminder.copy(id = savedId))
        } else if (linkedReminderId != null) {
            val existingAlarm = customAlarmRepository.getAllSnapshot(profileId)
                .firstOrNull { it.id == linkedReminderId }
            if (existingAlarm != null) {
                customAlarmRepository.toggleAlarm(existingAlarm, enabled = false)
            }
            alertSoundManager.cancelCustomAlarm(linkedReminderId)
        }

        val routine = DailyRoutineEntity(
            id = existing?.id ?: 0L,
            profileId = profileId,
            title = cleanTitle,
            emoji = emoji.ifBlank { timeSlot.emoji },
            timeSlot = timeSlot.label,
            triggerTime24h = triggerTime24h,
            estimatedMinutes = estimatedMinutes.coerceIn(1, 180),
            stepsSerialized = RoutineMoodAnalyticsEngine.serializeSteps(steps.ifEmpty { listOf("Check in gently") }),
            linkedModuleKey = linkedModuleKey,
            reminderId = linkedReminderId,
            reminderEnabled = reminderEnabled,
            isOptional = true,
            isActive = existing?.isActive ?: true,
            sortOrder = existing?.sortOrder ?: 0,
            createdAtEpochMs = existing?.createdAtEpochMs ?: System.currentTimeMillis()
        )
        val routineId = if (routine.id == 0L) {
            dao.insertRoutine(routine)
        } else {
            dao.updateRoutine(routine)
            routine.id
        }
        syncAchievements(profileId)
        routineId
    }

    suspend fun adoptSuggestedRoutine(
        profileId: Long,
        template: SuggestedRoutineTemplate,
        enableReminder: Boolean = false
    ): Long = createOrUpdateRoutine(
        profileId = profileId,
        title = template.title,
        emoji = template.emoji,
        timeSlot = template.timeSlot,
        triggerTime24h = template.triggerTime24h,
        estimatedMinutes = template.estimatedMinutes,
        steps = template.steps,
        linkedModuleKey = template.linkedModuleKey,
        reminderEnabled = enableReminder
    )

    suspend fun toggleRoutineReminder(
        routine: DailyRoutineEntity,
        enabled: Boolean,
        triggerTime24h: String = routine.triggerTime24h,
        soundKey: String = BundledAlertTone.WELLNESS_GENTLE.id,
        repeatIntervalMinutes: Int = 1440
    ) = withContext(Dispatchers.IO) {
        var reminderId = routine.reminderId
        if (enabled) {
            val reminder = ReminderEntity(
                id = reminderId ?: 0L,
                profileId = routine.profileId,
                title = routine.title,
                moduleKey = AlertCategory.WELLNESS.key,
                triggerTime24h = triggerTime24h,
                daysOfWeekMask = 127,
                isEnabled = true,
                soundKey = soundKey,
                alarmEmoji = routine.emoji,
                customMessage = "Gentle check-in for ${routine.title}",
                repeatIntervalMinutes = repeatIntervalMinutes.coerceIn(15, 10_080)
            )
            val savedId = customAlarmRepository.saveAlarm(reminder)
            reminderId = savedId
            alertSoundManager.scheduleCustomAlarm(reminder.copy(id = savedId))
        } else if (reminderId != null) {
            val existingAlarm = customAlarmRepository.getAllSnapshot(routine.profileId)
                .firstOrNull { it.id == reminderId }
            if (existingAlarm != null) {
                customAlarmRepository.toggleAlarm(existingAlarm, enabled = false)
            }
            alertSoundManager.cancelCustomAlarm(reminderId)
        }
        dao.updateRoutine(
            routine.copy(
                triggerTime24h = triggerTime24h,
                reminderId = reminderId,
                reminderEnabled = enabled
            )
        )
    }

    suspend fun toggleRoutineActive(routine: DailyRoutineEntity, active: Boolean) = withContext(Dispatchers.IO) {
        dao.updateRoutine(routine.copy(isActive = active))
        if (!active && routine.reminderId != null) {
            alertSoundManager.cancelCustomAlarm(routine.reminderId)
        } else if (active && routine.reminderEnabled && routine.reminderId != null) {
            val alarm = customAlarmRepository.getAllSnapshot(routine.profileId)
                .firstOrNull { it.id == routine.reminderId }
            if (alarm != null) {
                alertSoundManager.scheduleCustomAlarm(alarm)
            }
        }
    }

    suspend fun deleteRoutine(routine: DailyRoutineEntity) = withContext(Dispatchers.IO) {
        routine.reminderId?.let { rid ->
            alertSoundManager.cancelCustomAlarm(rid)
            customAlarmRepository.deleteAlarm(rid)
        }
        dao.deleteRoutine(routine.id)
        syncAchievements(routine.profileId)
    }

    suspend fun toggleRoutineStepForDay(
        routine: DailyRoutineEntity,
        stepIndex: Int,
        dayKey: String = DateEpochUtils.todayDayKey(),
        reflectionNote: String = ""
    ) = withContext(Dispatchers.IO) {
        val steps = RoutineMoodAnalyticsEngine.parseSteps(routine.stepsSerialized)
            .ifEmpty { listOf("Complete check-in") }
        val totalSteps = steps.size.coerceIn(1, 30)
        val safeIdx = stepIndex.coerceIn(0, totalSteps - 1)
        val existing = dao.getCompletionForDay(routine.profileId, routine.id, dayKey)
        val currentMask = existing?.completedStepsMask ?: 0
        val newMask = currentMask xor (1 shl safeIdx)
        val newCount = Integer.bitCount(newMask and ((1 shl totalSteps) - 1))

        if (newCount == 0) {
            dao.deleteCompletionForDay(routine.profileId, routine.id, dayKey)
        } else {
            dao.upsertCompletion(
                RoutineCompletionEntity(
                    id = existing?.id ?: 0L,
                    profileId = routine.profileId,
                    routineId = routine.id,
                    dayKey = dayKey,
                    completedStepsMask = newMask,
                    completedStepsCount = newCount,
                    totalStepsCount = totalSteps,
                    reflectionNote = reflectionNote.ifBlank { existing?.reflectionNote.orEmpty() },
                    completedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
        syncAchievements(routine.profileId)
    }

    suspend fun toggleCompleteWholeRoutineToday(
        routine: DailyRoutineEntity,
        dayKey: String = DateEpochUtils.todayDayKey()
    ) = withContext(Dispatchers.IO) {
        val steps = RoutineMoodAnalyticsEngine.parseSteps(routine.stepsSerialized)
            .ifEmpty { listOf("Complete check-in") }
        val totalSteps = steps.size.coerceIn(1, 30)
        val fullMask = (1 shl totalSteps) - 1
        val existing = dao.getCompletionForDay(routine.profileId, routine.id, dayKey)
        val isAlreadyDone = existing != null && existing.completedStepsCount >= totalSteps
        if (isAlreadyDone) {
            dao.deleteCompletionForDay(routine.profileId, routine.id, dayKey)
        } else {
            dao.upsertCompletion(
                RoutineCompletionEntity(
                    id = existing?.id ?: 0L,
                    profileId = routine.profileId,
                    routineId = routine.id,
                    dayKey = dayKey,
                    completedStepsMask = fullMask,
                    completedStepsCount = totalSteps,
                    totalStepsCount = totalSteps,
                    reflectionNote = existing?.reflectionNote.orEmpty(),
                    completedAtEpochMs = System.currentTimeMillis()
                )
            )
        }
        syncAchievements(routine.profileId)
    }

    suspend fun logMoodEntry(
        profileId: Long,
        preset: MoodPresetOption,
        energyLevel: Int,
        stressLevel: Int,
        tags: List<String>,
        reflectionNote: String,
        gratitudeText: String,
        loggedAtEpochMs: Long = System.currentTimeMillis()
    ): Long = withContext(Dispatchers.IO) {
        val id = dao.insertMoodEntry(
            MoodJournalEntryEntity(
                profileId = profileId,
                dayKey = DateEpochUtils.todayDayKey(loggedAtEpochMs),
                moodLabel = preset.label,
                moodEmoji = preset.emoji,
                moodScore = preset.defaultScore.coerceIn(1, 5),
                energyLevel = energyLevel.coerceIn(1, 5),
                stressLevel = stressLevel.coerceIn(1, 5),
                tagsCsv = tags.joinToString(", "),
                reflectionNote = reflectionNote.trim(),
                gratitudeText = gratitudeText.trim(),
                loggedAtEpochMs = loggedAtEpochMs
            )
        )
        syncAchievements(profileId)
        id
    }

    suspend fun deleteMoodEntry(profileId: Long, entryId: Long) = withContext(Dispatchers.IO) {
        dao.deleteMoodEntry(entryId)
        syncAchievements(profileId)
    }

    suspend fun configureMoodJournalReminder(
        profileId: Long,
        enabled: Boolean,
        triggerTime24h: String,
        soundKey: String = BundledAlertTone.SINGING_BOWL.id
    ) = withContext(Dispatchers.IO) {
        val existing = customAlarmRepository.getAllSnapshot(profileId)
            .firstOrNull { it.moduleKey == "mood_journal" }
        if (enabled) {
            val reminder = ReminderEntity(
                id = existing?.id ?: 0L,
                profileId = profileId,
                title = "Mood & Gratitude Reflection",
                moduleKey = "mood_journal",
                triggerTime24h = triggerTime24h,
                daysOfWeekMask = 127,
                isEnabled = true,
                soundKey = soundKey,
                alarmEmoji = "📓",
                customMessage = "Optional quiet pause: check in with your mood today.",
                repeatIntervalMinutes = 1440
            )
            val id = customAlarmRepository.saveAlarm(reminder)
            alertSoundManager.scheduleCustomAlarm(reminder.copy(id = id))
        } else if (existing != null) {
            customAlarmRepository.toggleAlarm(existing, enabled = false)
            alertSoundManager.cancelCustomAlarm(existing.id)
        }
    }

    suspend fun joinChallenge(challenge: WellnessChallengeEntity, todayKey: String = DateEpochUtils.todayDayKey()) =
        withContext(Dispatchers.IO) {
            dao.updateChallenge(
                challenge.copy(
                    status = "ACTIVE",
                    startDayKey = challenge.startDayKey.ifBlank { todayKey }
                )
            )
        }

    suspend fun pauseChallenge(challenge: WellnessChallengeEntity) = withContext(Dispatchers.IO) {
        if (challenge.status == "ACTIVE") {
            dao.updateChallenge(challenge.copy(status = "PAUSED"))
        }
    }

    suspend fun leaveOrResetChallenge(challenge: WellnessChallengeEntity) = withContext(Dispatchers.IO) {
        dao.updateChallenge(
            challenge.copy(
                status = "AVAILABLE",
                currentProgressDays = 0,
                startDayKey = "",
                lastCheckInDayKey = "",
                completedAtEpochMs = null
            )
        )
        syncAchievements(challenge.profileId)
    }

    /**
     * Records one cumulative mindful day for an active challenge without any streak-loss penalty.
     * Multiple taps on the same calendar day are idempotent so numbers remain honest.
     */
    suspend fun checkInChallengeDay(
        challenge: WellnessChallengeEntity,
        dayKey: String = DateEpochUtils.todayDayKey()
    ): Boolean = withContext(Dispatchers.IO) {
        if (challenge.status != "ACTIVE" && challenge.status != "PAUSED") return@withContext false
        if (challenge.lastCheckInDayKey == dayKey) return@withContext false

        val nextDays = (challenge.currentProgressDays + 1).coerceAtMost(challenge.targetDays)
        val completed = nextDays >= challenge.targetDays
        dao.updateChallenge(
            challenge.copy(
                status = if (completed) "COMPLETED" else "ACTIVE",
                currentProgressDays = nextDays,
                startDayKey = challenge.startDayKey.ifBlank { dayKey },
                lastCheckInDayKey = dayKey,
                completedAtEpochMs = if (completed) System.currentTimeMillis() else null
            )
        )
        syncAchievements(challenge.profileId)
        true
    }

    suspend fun syncAchievements(profileId: Long): List<AchievementBadgeEntity> = withContext(Dispatchers.IO) {
        val existingBadges = dao.getBadgesSnapshot(profileId)
        val previouslyEarnedKeys = existingBadges.filter { it.earnedAtEpochMs != null }.map { it.badgeKey }.toSet()
        val routines = dao.getRoutinesSnapshot(profileId)
        val completions = dao.getCompletionsSnapshot(profileId)
        val moodEntries = dao.getMoodEntriesSnapshot(profileId)
        val challenges = dao.getChallengesSnapshot(profileId)
        val waterRecords = database.waterDao().getAllForProfileSnapshot(profileId)

        val evaluated = RoutineMoodAnalyticsEngine.evaluateBadgeProgress(
            profileId = profileId,
            existingBadges = existingBadges,
            routines = routines,
            completions = completions,
            moodEntries = moodEntries,
            challenges = challenges,
            waterRecords = waterRecords
        )
        evaluated.forEach { badge ->
            dao.upsertBadge(badge)
        }
        evaluated.filter { it.earnedAtEpochMs != null && it.badgeKey !in previouslyEarnedKeys }
    }
}
