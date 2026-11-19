package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.AchievementBadgeEntity
import com.example.data.local.entity.DailyRoutineEntity
import com.example.data.local.entity.MoodJournalEntryEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.RoutineCompletionEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WellnessChallengeEntity
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.CustomAlarmRepository
import com.example.data.repository.RoutineMoodRepository
import com.example.data.repository.UserProfileRepository
import com.example.data.repository.WaterRepository
import com.example.domain.DateEpochUtils
import com.example.domain.MoodJournalAnalytics
import com.example.domain.MoodPresetOption
import com.example.domain.RoutineAdherenceSummary
import com.example.domain.RoutineMoodAnalyticsEngine
import com.example.domain.RoutineTimeSlot
import com.example.domain.SuggestedRoutineTemplate
import com.example.notifications.BundledAlertTone
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class RoutineMoodUiState(
    val profile: UserProfileEntity? = null,
    val routines: List<DailyRoutineEntity> = emptyList(),
    val todayCompletionsByRoutineId: Map<Long, RoutineCompletionEntity> = emptyMap(),
    val suggestedRoutines: List<SuggestedRoutineTemplate> = emptyList(),
    val routineAdherence: RoutineAdherenceSummary = RoutineAdherenceSummary(0, 0, 0f, 0, 0, emptyList()),
    val moodEntries: List<MoodJournalEntryEntity> = emptyList(),
    val moodAnalytics: MoodJournalAnalytics = MoodJournalAnalytics(0, 0, null, null, null, emptyMap(), null, emptyList(), emptyList()),
    val challenges: List<WellnessChallengeEntity> = emptyList(),
    val badges: List<AchievementBadgeEntity> = emptyList(),
    val moodReminder: ReminderEntity? = null,
    val todayDayKey: String = DateEpochUtils.todayDayKey(),
    val statusBanner: String? = null,
    val celebratoryBadge: AchievementBadgeEntity? = null
)

@OptIn(ExperimentalCoroutinesApi::class)
class RoutineMoodViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val routineMoodRepository: RoutineMoodRepository,
    private val waterRepository: WaterRepository,
    private val allModulesRepository: AllModulesRepository,
    private val customAlarmRepository: CustomAlarmRepository
) : ViewModel() {

    private val statusBannerFlow = MutableStateFlow<String?>(null)
    private val celebratoryBadgeFlow = MutableStateFlow<AchievementBadgeEntity?>(null)
    private val seededProfileIds = mutableSetOf<Long>()

    val uiState: StateFlow<RoutineMoodUiState> = userProfileRepository.observeActiveProfile()
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(RoutineMoodUiState())
            } else {
                if (seededProfileIds.add(profile.id)) {
                    viewModelScope.launch {
                        routineMoodRepository.ensureDefaultCatalogSeeded(profile.id)
                    }
                }
                val phase6Flow = combine(
                    routineMoodRepository.observeRoutines(profile.id),
                    routineMoodRepository.observeRecentCompletions(profile.id, 30),
                    routineMoodRepository.observeMoodEntries(profile.id),
                    routineMoodRepository.observeChallenges(profile.id),
                    routineMoodRepository.observeBadges(profile.id)
                ) { routines, completions, moods, challenges, badges ->
                    listOf(routines, completions, moods, challenges, badges)
                }

                val contextDataFlow = combine(
                    allModulesRepository.observeMedicines(profile.id),
                    allModulesRepository.observeSleep(profile.id),
                    allModulesRepository.observeAllWellbeing(profile.id),
                    waterRepository.observeLast60Days(profile.id),
                    customAlarmRepository.observeAlarms(profile.id)
                ) { meds, sleep, wellbeing, water, alarms ->
                    listOf(meds, sleep, wellbeing, water, alarms)
                }

                combine(
                    phase6Flow,
                    contextDataFlow,
                    statusBannerFlow,
                    celebratoryBadgeFlow
                ) { p6, ctx, banner, celeb ->
                    @Suppress("UNCHECKED_CAST")
                    val routines = p6[0] as List<DailyRoutineEntity>
                    @Suppress("UNCHECKED_CAST")
                    val completions = p6[1] as List<RoutineCompletionEntity>
                    @Suppress("UNCHECKED_CAST")
                    val moods = p6[2] as List<MoodJournalEntryEntity>
                    @Suppress("UNCHECKED_CAST")
                    val challenges = p6[3] as List<WellnessChallengeEntity>
                    @Suppress("UNCHECKED_CAST")
                    val badges = p6[4] as List<AchievementBadgeEntity>

                    @Suppress("UNCHECKED_CAST")
                    val meds = ctx[0] as List<com.example.data.local.entity.MedicineEntity>
                    @Suppress("UNCHECKED_CAST")
                    val sleep = ctx[1] as List<com.example.data.local.entity.SleepRecordEntity>
                    @Suppress("UNCHECKED_CAST")
                    val wellbeing = ctx[2] as List<com.example.data.local.entity.DigitalWellbeingRecordEntity>
                    @Suppress("UNCHECKED_CAST")
                    val water = ctx[3] as List<com.example.data.local.entity.WaterRecordEntity>
                    @Suppress("UNCHECKED_CAST")
                    val alarms = ctx[4] as List<ReminderEntity>

                    val todayKey = DateEpochUtils.todayDayKey()
                    val todayCompletions = completions
                        .filter { it.dayKey == todayKey }
                        .associateBy { it.routineId }

                    val suggested = RoutineMoodAnalyticsEngine.suggestOptionalRoutines(
                        profile = profile,
                        existingRoutines = routines,
                        medicines = meds,
                        sleepRecords = sleep,
                        wellbeingRecords = wellbeing,
                        waterRecords = water
                    )
                    val adherence = RoutineMoodAnalyticsEngine.summarizeRoutineAdherence(
                        routines = routines,
                        completions = completions
                    )
                    val moodAnalytics = RoutineMoodAnalyticsEngine.analyzeMoodJournal(
                        entries = moods,
                        sleepRecords = sleep,
                        waterRecords = water,
                        routineCompletions = completions
                    )
                    val moodReminder = alarms.firstOrNull { it.moduleKey == "mood_journal" }

                    RoutineMoodUiState(
                        profile = profile,
                        routines = routines,
                        todayCompletionsByRoutineId = todayCompletions,
                        suggestedRoutines = suggested,
                        routineAdherence = adherence,
                        moodEntries = moods,
                        moodAnalytics = moodAnalytics,
                        challenges = challenges,
                        badges = badges,
                        moodReminder = moodReminder,
                        todayDayKey = todayKey,
                        statusBanner = banner,
                        celebratoryBadge = celeb
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = RoutineMoodUiState()
        )

    fun saveRoutine(
        existingId: Long = 0L,
        title: String,
        emoji: String,
        timeSlot: RoutineTimeSlot,
        triggerTime24h: String,
        estimatedMinutes: Int,
        steps: List<String>,
        reminderEnabled: Boolean,
        reminderSoundKey: String = BundledAlertTone.WELLNESS_GENTLE.id,
        repeatIntervalMinutes: Int = 1440
    ) {
        val pid = uiState.value.profile?.id ?: return
        val beforeEarned = uiState.value.badges.filter { it.earnedAtEpochMs != null }.map { it.badgeKey }.toSet()
        viewModelScope.launch {
            routineMoodRepository.createOrUpdateRoutine(
                profileId = pid,
                existingId = existingId,
                title = title,
                emoji = emoji,
                timeSlot = timeSlot,
                triggerTime24h = triggerTime24h,
                estimatedMinutes = estimatedMinutes,
                steps = steps,
                reminderEnabled = reminderEnabled,
                reminderSoundKey = reminderSoundKey,
                repeatIntervalMinutes = repeatIntervalMinutes
            )
            statusBannerFlow.value = if (existingId > 0L) {
                "Updated routine \"$title\"."
            } else {
                "Added optional routine \"$title\"."
            }
            detectNewBadgeUnlock(pid, beforeEarned)
        }
    }

    fun adoptSuggestedRoutine(template: SuggestedRoutineTemplate, enableReminder: Boolean = false) {
        val pid = uiState.value.profile?.id ?: return
        val beforeEarned = uiState.value.badges.filter { it.earnedAtEpochMs != null }.map { it.badgeKey }.toSet()
        viewModelScope.launch {
            routineMoodRepository.adoptSuggestedRoutine(pid, template, enableReminder)
            statusBannerFlow.value = "Added \"${template.title}\" to your routines."
            detectNewBadgeUnlock(pid, beforeEarned)
        }
    }

    fun toggleRoutineStep(routine: DailyRoutineEntity, stepIndex: Int) {
        val beforeEarned = uiState.value.badges.filter { it.earnedAtEpochMs != null }.map { it.badgeKey }.toSet()
        viewModelScope.launch {
            routineMoodRepository.toggleRoutineStepForDay(routine, stepIndex)
            detectNewBadgeUnlock(routine.profileId, beforeEarned)
        }
    }

    fun toggleWholeRoutine(routine: DailyRoutineEntity) {
        val beforeEarned = uiState.value.badges.filter { it.earnedAtEpochMs != null }.map { it.badgeKey }.toSet()
        viewModelScope.launch {
            routineMoodRepository.toggleCompleteWholeRoutineToday(routine)
            detectNewBadgeUnlock(routine.profileId, beforeEarned)
        }
    }

    fun toggleRoutineReminder(
        routine: DailyRoutineEntity,
        enabled: Boolean,
        triggerTime24h: String = routine.triggerTime24h,
        soundKey: String = BundledAlertTone.WELLNESS_GENTLE.id,
        repeatIntervalMinutes: Int = 1440
    ) {
        viewModelScope.launch {
            routineMoodRepository.toggleRoutineReminder(
                routine = routine,
                enabled = enabled,
                triggerTime24h = triggerTime24h,
                soundKey = soundKey,
                repeatIntervalMinutes = repeatIntervalMinutes
            )
            statusBannerFlow.value = if (enabled) {
                "Gentle reminder enabled for \"${routine.title}\" at $triggerTime24h."
            } else {
                "Reminder paused for \"${routine.title}\"."
            }
        }
    }

    fun toggleRoutineActive(routine: DailyRoutineEntity, active: Boolean) {
        viewModelScope.launch {
            routineMoodRepository.toggleRoutineActive(routine, active)
        }
    }

    fun deleteRoutine(routine: DailyRoutineEntity) {
        viewModelScope.launch {
            routineMoodRepository.deleteRoutine(routine)
            statusBannerFlow.value = "Removed routine \"${routine.title}\"."
        }
    }

    fun logMoodEntry(
        preset: MoodPresetOption,
        energyLevel: Int,
        stressLevel: Int,
        tags: List<String>,
        reflectionNote: String,
        gratitudeText: String
    ) {
        val pid = uiState.value.profile?.id ?: return
        val beforeEarned = uiState.value.badges.filter { it.earnedAtEpochMs != null }.map { it.badgeKey }.toSet()
        viewModelScope.launch {
            routineMoodRepository.logMoodEntry(
                profileId = pid,
                preset = preset,
                energyLevel = energyLevel,
                stressLevel = stressLevel,
                tags = tags,
                reflectionNote = reflectionNote,
                gratitudeText = gratitudeText
            )
            statusBannerFlow.value = "Saved ${preset.emoji} ${preset.label} reflection to your private journal."
            detectNewBadgeUnlock(pid, beforeEarned)
        }
    }

    fun deleteMoodEntry(entryId: Long) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            routineMoodRepository.deleteMoodEntry(pid, entryId)
            statusBannerFlow.value = "Removed mood journal entry."
        }
    }

    fun configureMoodReminder(enabled: Boolean, time24h: String, soundKey: String = BundledAlertTone.SINGING_BOWL.id) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            routineMoodRepository.configureMoodJournalReminder(pid, enabled, time24h, soundKey)
            statusBannerFlow.value = if (enabled) {
                "Optional mood check-in reminder set for $time24h."
            } else {
                "Mood journal reminder turned off."
            }
        }
    }

    fun joinChallenge(challenge: WellnessChallengeEntity) {
        viewModelScope.launch {
            routineMoodRepository.joinChallenge(challenge)
            statusBannerFlow.value = "Joined \"${challenge.title}\" — progress moves only when you choose."
        }
    }

    fun pauseChallenge(challenge: WellnessChallengeEntity) {
        viewModelScope.launch {
            routineMoodRepository.pauseChallenge(challenge)
            statusBannerFlow.value = "Paused \"${challenge.title}\". Your ${challenge.currentProgressDays} day(s) remain saved."
        }
    }

    fun leaveChallenge(challenge: WellnessChallengeEntity) {
        viewModelScope.launch {
            routineMoodRepository.leaveOrResetChallenge(challenge)
            statusBannerFlow.value = "Stepped back from \"${challenge.title}\" — join anytime with zero penalty."
        }
    }

    fun checkInChallenge(challenge: WellnessChallengeEntity) {
        val beforeEarned = uiState.value.badges.filter { it.earnedAtEpochMs != null }.map { it.badgeKey }.toSet()
        viewModelScope.launch {
            val recorded = routineMoodRepository.checkInChallengeDay(challenge)
            statusBannerFlow.value = if (recorded) {
                val next = (challenge.currentProgressDays + 1).coerceAtMost(challenge.targetDays)
                if (next >= challenge.targetDays) {
                    "🎉 Completed \"${challenge.title}\" (${challenge.targetDays}/${challenge.targetDays} days)!"
                } else {
                    "Checked in for \"${challenge.title}\" ($next/${challenge.targetDays} days)."
                }
            } else {
                "Already checked in for \"${challenge.title}\" today — rest well!"
            }
            detectNewBadgeUnlock(challenge.profileId, beforeEarned)
        }
    }

    fun clearStatusBanner() {
        statusBannerFlow.value = null
    }

    fun dismissCelebratoryBadge() {
        celebratoryBadgeFlow.value = null
    }

    private suspend fun detectNewBadgeUnlock(profileId: Long, beforeEarnedKeys: Set<String>) {
        val newlyFromSync = routineMoodRepository.syncAchievements(profileId)
        val freshBadges = routineMoodRepository.observeBadges(profileId).first()
        val newlyEarned = newlyFromSync.firstOrNull() ?: freshBadges.firstOrNull {
            it.earnedAtEpochMs != null && it.badgeKey !in beforeEarnedKeys
        }
        if (newlyEarned != null) {
            celebratoryBadgeFlow.value = newlyEarned
        }
    }
}
