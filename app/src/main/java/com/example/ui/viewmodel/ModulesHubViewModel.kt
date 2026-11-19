package com.example.ui.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.ai.AiActionType
import com.example.ai.AiDiagnosticReport
import com.example.ai.AiHealthIntelligenceReport
import com.example.ai.AiParsedAction
import com.example.ai.ExtractedPrescriptionResult
import com.example.ai.LifeWeaveAiService
import com.example.ai.SupportedVoiceLanguages
import com.example.ai.VoiceAssistantManager
import com.example.data.local.entity.AchievementBadgeEntity
import com.example.data.local.entity.AiPreferenceEntity
import com.example.data.local.entity.AllergyRecordEntity
import com.example.data.local.entity.BeautyCareRecordEntity
import com.example.data.local.entity.ConnectedDeviceSourceEntity
import com.example.data.local.entity.DailyRoutineEntity
import com.example.data.local.entity.DietRecordEntity
import com.example.data.local.entity.DigitalWellbeingRecordEntity
import com.example.data.local.entity.DoctorConsultationEntity
import com.example.data.local.entity.ExerciseRecordEntity
import com.example.data.local.entity.HealthVitalMetricEntity
import com.example.data.local.entity.MedicineEntity
import com.example.data.local.entity.MedicineIntakeEntity
import com.example.data.local.entity.ModuleActivationEntity
import com.example.data.local.entity.MoodJournalEntryEntity
import com.example.data.local.entity.NutritionPlanEntity
import com.example.data.local.entity.NutritionPreferenceEntity
import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.local.entity.ReminderEntity
import com.example.data.local.entity.RoutineCompletionEntity
import com.example.data.local.entity.SleepRecordEntity
import com.example.data.local.entity.StepRecordEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.local.entity.WaterRecordEntity
import com.example.data.local.entity.WellnessChallengeEntity
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.CustomAlarmRepository
import com.example.data.repository.HealthVaultRepository
import com.example.data.repository.ModuleRepository
import com.example.data.repository.PeriodRepository
import com.example.data.repository.RoutineMoodRepository
import com.example.data.repository.UserProfileRepository
import com.example.data.repository.WaterRepository
import com.example.domain.CycleCalculator
import com.example.domain.CycleInsights
import com.example.domain.DailyNutritionProgress
import com.example.domain.DailyNutritionTarget
import com.example.domain.DateEpochUtils
import com.example.domain.HydrationCalculator
import com.example.domain.HydrationSummary
import com.example.domain.IndianDietaryPreference
import com.example.domain.IndianFoodItem
import com.example.domain.IndianMealPlanResult
import com.example.domain.IndianNutritionEngine
import com.example.domain.IndianRegion
import com.example.domain.IntelligenceTimeframe
import com.example.domain.LocalAiMessage
import com.example.domain.MoodJournalAnalytics
import com.example.domain.MoodPresetOption
import com.example.domain.NutritionGoalType
import com.example.domain.OnDeviceWellnessAdvisor
import com.example.domain.PersonalHealthIntelligenceEngine
import com.example.domain.PersonalHealthIntelligenceSnapshot
import com.example.domain.PlannedIndianMealSlot
import com.example.domain.RoutineAdherenceSummary
import com.example.domain.RoutineMoodAnalyticsEngine
import com.example.healthsync.AppleAndFileFitnessImporter
import com.example.healthsync.FitnessSyncRepository
import com.example.healthsync.HealthConnectAvailability
import com.example.healthsync.HealthConnectDataCategory
import com.example.healthsync.HealthConnectManager
import com.example.notifications.AlertMetricsCalculator
import com.example.notifications.AlertMetricsSummary
import com.example.notifications.AlertSoundManager
import com.example.notifications.BundledAlertTone
import com.example.security.AiDataAuthorization
import com.example.security.CategoryToneSetting
import com.example.security.HealthConnectImportAuthorization
import com.example.security.LocalPreferencesManager
import com.example.security.PersonalAiKeyState
import com.example.security.PersonalAiKeyStore
import com.example.sleep.SleepMonitorLiveState
import com.example.sleep.SleepMonitorStateStore
import com.example.sleep.SleepMonitoringService
import com.example.sync.AppAutoRefreshManager
import com.example.sync.AppAutoRefreshState
import com.example.wellbeing.AppUsageMetric
import com.example.wellbeing.DeviceWellbeingManager
import com.example.wellbeing.OveruseSeverity
import com.example.wellbeing.WellbeingMetricsCalculator
import com.example.wellbeing.WellbeingOveruseAlert
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AiTaskStatus {
    data object Idle : AiTaskStatus
    data class Loading(val label: String) : AiTaskStatus
    data class Streaming(val label: String, val previewText: String = "") : AiTaskStatus
    data class Error(val message: String) : AiTaskStatus
}

data class ModulesHubUiState(
    val profile: UserProfileEntity? = null,
    val modules: List<ModuleActivationEntity> = emptyList(),
    val hydration: HydrationSummary = HydrationSummary(0, 2500, 0f, 2500, 0, emptyList()),
    val cycleInsights: CycleInsights? = null,
    val exercises: List<ExerciseRecordEntity> = emptyList(),
    val sleepRecords: List<SleepRecordEntity> = emptyList(),
    val todaySteps: StepRecordEntity? = null,
    val dietRecords: List<DietRecordEntity> = emptyList(),
    val medicines: List<MedicineEntity> = emptyList(),
    val todayMedicineIntakes: List<MedicineIntakeEntity> = emptyList(),
    val recentMedicineIntakes: List<MedicineIntakeEntity> = emptyList(),
    val consultations: List<DoctorConsultationEntity> = emptyList(),
    val beautyRecords: List<BeautyCareRecordEntity> = emptyList(),
    val todayWellbeing: DigitalWellbeingRecordEntity? = null,
    val aiPreference: AiPreferenceEntity? = null,
    val dailyBriefing: String = "",
    val aiChatMessages: List<LocalAiMessage> = emptyList(),
    val selectedVoiceLanguageTag: String = "en-US",
    val autoSpeakReplies: Boolean = true,
    val aiTaskStatus: AiTaskStatus = AiTaskStatus.Idle,
    val isSpeakingVoice: Boolean = false,
    val isListeningVoice: Boolean = false,
    val voicePartialTranscript: String = "",
    val voiceError: String? = null,
    val lastPrescriptionResult: ExtractedPrescriptionResult? = null,
    val pendingNavigationModuleKey: String? = null,
    val lastAiPrompt: String? = null,
    val aiDiagnosticReport: AiDiagnosticReport? = null,
    val isRunningDiagnostics: Boolean = false,
    val connectedSources: List<ConnectedDeviceSourceEntity> = emptyList(),
    val vitalMetrics: List<HealthVitalMetricEntity> = emptyList(),
    val healthConnectAvailability: HealthConnectAvailability = HealthConnectAvailability.UNAVAILABLE,
    val grantedHealthPermissions: Set<String> = emptySet(),
    val isSyncingFitness: Boolean = false,
    val fitnessSyncBanner: String? = null,
    val hasUsageAccessPermission: Boolean = false,
    val hasNotificationPermission: Boolean = true,
    val wellbeingHistory: List<DigitalWellbeingRecordEntity> = emptyList(),
    val topAppsUsage: List<AppUsageMetric> = emptyList(),
    val longUsedApps: List<AppUsageMetric> = emptyList(),
    val categoryScreenMinutes: Map<String, Int> = emptyMap(),
    val mindfulBalanceScore: Int = 100,
    val wellbeingOveruseAlert: WellbeingOveruseAlert? = null,
    val isWellbeingAlertSnoozed: Boolean = false,
    val wellbeingOveruseAlertsEnabled: Boolean = true,
    val wellbeingWarningPercent: Int = 80,
    val isSyncingWellbeing: Boolean = false,
    val wellbeingSyncBanner: String? = null,
    val customAlarms: List<ReminderEntity> = emptyList(),
    val alertMetrics: AlertMetricsSummary = AlertMetricsSummary(),
    val healthIntelligence: PersonalHealthIntelligenceSnapshot = PersonalHealthIntelligenceSnapshot.empty(),
    val aiDataAuthorization: AiDataAuthorization = AiDataAuthorization(),
    val selectedIntelligenceTimeframe: IntelligenceTimeframe = IntelligenceTimeframe.WEEKLY,
    val generatedAiHealthReport: AiHealthIntelligenceReport? = null,
    val nutritionPreference: NutritionPreferenceEntity? = null,
    val selectedIndianRegion: IndianRegion = IndianRegion.SOUTH,
    val selectedDietaryPreference: IndianDietaryPreference = IndianDietaryPreference.VEGETARIAN,
    val selectedNutritionGoal: NutritionGoalType = NutritionGoalType.BALANCED,
    val avoidVaultAllergens: Boolean = true,
    val nutritionTarget: DailyNutritionTarget = IndianNutritionEngine.calculateTargets(null),
    val todayNutritionProgress: DailyNutritionProgress = IndianNutritionEngine.evaluateTodayIntake(emptyList(), nutritionTarget),
    val vaultAllergies: List<AllergyRecordEntity> = emptyList(),
    val activeIndianMealPlan: IndianMealPlanResult? = null,
    val isGeneratingMealPlan: Boolean = false,
    val mealPlanStatusMessage: String? = null,
    val healthConnectImportAuth: HealthConnectImportAuthorization = HealthConnectImportAuthorization(),
    val routines: List<DailyRoutineEntity> = emptyList(),
    val routineAdherence: RoutineAdherenceSummary = RoutineAdherenceSummary(0, 0, 0f, 0, 0, emptyList()),
    val moodEntries: List<MoodJournalEntryEntity> = emptyList(),
    val moodAnalytics: MoodJournalAnalytics = MoodJournalAnalytics(0, 0, null, null, null, emptyMap(), null, emptyList(), emptyList()),
    val wellnessChallenges: List<WellnessChallengeEntity> = emptyList(),
    val achievementBadges: List<AchievementBadgeEntity> = emptyList(),
    val latestMoodEntry: MoodJournalEntryEntity? = null,
    val autoRefreshEnabled: Boolean = true,
    val autoRefreshIntervalMinutes: Int = 30,
    val isAutoRefreshingAll: Boolean = false,
    val lastAutoRefreshEpochMs: Long = 0L,
    val lastAutoRefreshSummary: String = "",
    val sleepMonitorLiveState: SleepMonitorLiveState = SleepMonitorLiveState(),
    val sleepAutoBedtimePromptEnabled: Boolean = false,
    val sleepBedtimeHour: Int = 22,
    val sleepBedtimeMinute: Int = 30,
    val sleepWakeHour: Int = 7,
    val sleepWakeMinute: Int = 0,
    val personalAiKeyState: PersonalAiKeyState = PersonalAiKeyState(),
    val isTestingPersonalAiKey: Boolean = false,
    val personalAiKeyFeedback: Pair<Boolean, String>? = null
)

private data class AiAndSyncOverlayState(
    val chat: List<LocalAiMessage>,
    val languageTag: String,
    val autoSpeak: Boolean,
    val aiStatus: AiTaskStatus,
    val isSpeaking: Boolean,
    val isListening: Boolean,
    val partialTranscript: String,
    val voiceError: String?,
    val prescription: ExtractedPrescriptionResult?,
    val navTarget: String?,
    val lastAiPrompt: String?,
    val diagnosticReport: AiDiagnosticReport?,
    val isRunningDiagnostics: Boolean,
    val hcAvailability: HealthConnectAvailability,
    val grantedPerms: Set<String>,
    val isSyncingFitness: Boolean,
    val syncBanner: String?,
    val hasUsageAccess: Boolean,
    val hasNotificationPermission: Boolean,
    val snoozedUntilMs: Long,
    val alertsEnabled: Boolean,
    val warningPercent: Int,
    val wellnessTone: CategoryToneSetting,
    val isSyncingWellbeing: Boolean,
    val wellbeingBanner: String?,
    val selectedTimeframe: IntelligenceTimeframe,
    val generatedHealthReport: AiHealthIntelligenceReport?,
    val isGeneratingMealPlan: Boolean,
    val mealPlanStatusMessage: String?
)

@OptIn(ExperimentalCoroutinesApi::class)
class ModulesHubViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val waterRepository: WaterRepository,
    periodRepository: PeriodRepository,
    moduleRepository: ModuleRepository,
    private val allModulesRepo: AllModulesRepository,
    private val fitnessSyncRepo: FitnessSyncRepository,
    private val healthConnectManager: HealthConnectManager,
    private val appleImporter: AppleAndFileFitnessImporter,
    private val aiService: LifeWeaveAiService,
    val voiceManager: VoiceAssistantManager,
    private val deviceWellbeingManager: DeviceWellbeingManager,
    private val alertSoundManager: AlertSoundManager,
    private val preferencesManager: LocalPreferencesManager,
    private val customAlarmRepository: CustomAlarmRepository,
    private val healthVaultRepository: HealthVaultRepository? = null,
    private val routineMoodRepository: RoutineMoodRepository? = null,
    private val appAutoRefreshManager: AppAutoRefreshManager? = null,
    private val personalAiKeyStore: PersonalAiKeyStore? = null
) : ViewModel() {

    private val isTestingPersonalKeyFlow = MutableStateFlow(false)
    private val personalKeyFeedbackFlow = MutableStateFlow<Pair<Boolean, String>?>(null)

    private val aiMessages = MutableStateFlow<List<LocalAiMessage>>(emptyList())
    private val selectedLanguageTag = MutableStateFlow("en-US")
    private val autoSpeakReplies = MutableStateFlow(true)
    private val aiTaskStatus = MutableStateFlow<AiTaskStatus>(AiTaskStatus.Idle)
    private val lastPrescription = MutableStateFlow<ExtractedPrescriptionResult?>(null)
    private val pendingNavModuleKey = MutableStateFlow<String?>(null)
    private val lastAiPromptFlow = MutableStateFlow<String?>(null)
    private val diagnosticReportFlow = MutableStateFlow<AiDiagnosticReport?>(null)
    private val isRunningDiagFlow = MutableStateFlow(false)
    private val selectedTimeframeFlow = MutableStateFlow(IntelligenceTimeframe.WEEKLY)
    private val generatedHealthReportFlow = MutableStateFlow<AiHealthIntelligenceReport?>(null)
    private val isGeneratingMealPlanFlow = MutableStateFlow(false)
    private val mealPlanStatusFlow = MutableStateFlow<String?>(null)
    private val seededPhase6ProfileIds = mutableSetOf<Long>()

    private val hcAvailability = MutableStateFlow(healthConnectManager.checkAvailability())
    private val grantedHealthPerms = MutableStateFlow<Set<String>>(emptySet())
    private val isSyncingFitness = MutableStateFlow(false)
    private val fitnessSyncBanner = MutableStateFlow<String?>(null)

    private val hasUsageAccess = MutableStateFlow(deviceWellbeingManager.hasUsageAccessPermission())
    private val hasNotificationPerm = MutableStateFlow(alertSoundManager.hasNotificationPermission())
    private val snoozedUntilMs = MutableStateFlow(0L)
    private val isSyncingWellbeing = MutableStateFlow(false)
    private val wellbeingSyncBanner = MutableStateFlow<String?>(null)
    private var nextMsgId = 1L

    init {
        refreshHealthConnectState()
        refreshWellbeingFromDevice(includePastWeek = true)
        viewModelScope.launch {
            val prefs = preferencesManager.preferencesFlow.first()
            if (prefs.wellbeingOveruseAlertsEnabled) {
                alertSoundManager.scheduleWellbeingMonitor()
            }
            appAutoRefreshManager?.syncWorkScheduleFromPreferences()
        }
    }

    private val aiFlow = combine(
        aiMessages,
        selectedLanguageTag,
        autoSpeakReplies,
        aiTaskStatus,
        voiceManager.isSpeaking
    ) { chat, lang, autoSpeak, status, speaking ->
        AiAndSyncOverlayState(
            chat = chat,
            languageTag = lang,
            autoSpeak = autoSpeak,
            aiStatus = status,
            isSpeaking = speaking,
            isListening = false,
            partialTranscript = "",
            voiceError = null,
            prescription = null,
            navTarget = null,
            lastAiPrompt = null,
            diagnosticReport = null,
            isRunningDiagnostics = false,
            hcAvailability = HealthConnectAvailability.UNAVAILABLE,
            grantedPerms = emptySet(),
            isSyncingFitness = false,
            syncBanner = null,
            hasUsageAccess = false,
            hasNotificationPermission = true,
            snoozedUntilMs = 0L,
            alertsEnabled = true,
            warningPercent = 80,
            wellnessTone = CategoryToneSetting(),
            isSyncingWellbeing = false,
            wellbeingBanner = null,
            selectedTimeframe = IntelligenceTimeframe.WEEKLY,
            generatedHealthReport = null,
            isGeneratingMealPlan = false,
            mealPlanStatusMessage = null
        )
    }.combine(
        combine(
            voiceManager.isListening,
            voiceManager.partialTranscript,
            voiceManager.voiceError,
            lastPrescription,
            pendingNavModuleKey
        ) { listening, partial, vErr, presc, nav ->
            listOf(listening, partial, vErr, presc, nav)
        }
    ) { base, arr ->
        @Suppress("UNCHECKED_CAST")
        base.copy(
            isListening = arr[0] as Boolean,
            partialTranscript = arr[1] as String,
            voiceError = arr[2] as String?,
            prescription = arr[3] as ExtractedPrescriptionResult?,
            navTarget = arr[4] as String?
        )
    }.combine(
        combine(
            hcAvailability,
            grantedHealthPerms,
            isSyncingFitness,
            fitnessSyncBanner,
            combine(isGeneratingMealPlanFlow, mealPlanStatusFlow) { gen, msg -> gen to msg }
        ) { avail, perms, syncing, banner, mealPair ->
            listOf(avail, perms, syncing, banner, mealPair)
        }
    ) { base, arr ->
        @Suppress("UNCHECKED_CAST")
        val mealPair = arr[4] as Pair<Boolean, String?>
        base.copy(
            hcAvailability = arr[0] as HealthConnectAvailability,
            grantedPerms = arr[1] as Set<String>,
            isSyncingFitness = arr[2] as Boolean,
            syncBanner = arr[3] as String?,
            isGeneratingMealPlan = mealPair.first,
            mealPlanStatusMessage = mealPair.second
        )
    }.combine(
        combine(
            hasUsageAccess,
            preferencesManager.preferencesFlow,
            isSyncingWellbeing,
            wellbeingSyncBanner,
            combine(
                combine(hasNotificationPerm, snoozedUntilMs) { n, s -> n to s },
                combine(lastAiPromptFlow, diagnosticReportFlow, isRunningDiagFlow) { p, r, d -> Triple(p, r, d) },
                combine(selectedTimeframeFlow, generatedHealthReportFlow) { tf, rep -> tf to rep }
            ) { notifPair, diagTriple, intelPair -> Triple(notifPair, diagTriple, intelPair) }
        ) { usagePerm, prefs, syncingWb, wbBanner, bundled ->
            listOf(usagePerm, prefs, syncingWb, wbBanner, bundled)
        }
    ) { base, arr ->
        val prefs = arr[1] as com.example.security.UiPreferencesState
        @Suppress("UNCHECKED_CAST")
        val bundled = arr[4] as Triple<Pair<Boolean, Long>, Triple<String?, AiDiagnosticReport?, Boolean>, Pair<IntelligenceTimeframe, AiHealthIntelligenceReport?>>
        val notifAndSnooze = bundled.first
        val diagTriple = bundled.second
        val intelPair = bundled.third
        base.copy(
            hasUsageAccess = arr[0] as Boolean,
            hasNotificationPermission = notifAndSnooze.first,
            snoozedUntilMs = notifAndSnooze.second,
            lastAiPrompt = diagTriple.first,
            diagnosticReport = diagTriple.second,
            isRunningDiagnostics = diagTriple.third,
            alertsEnabled = prefs.wellbeingOveruseAlertsEnabled,
            warningPercent = prefs.wellbeingWarningPercent,
            wellnessTone = prefs.wellnessTone,
            isSyncingWellbeing = arr[2] as Boolean,
            wellbeingBanner = arr[3] as String?,
            selectedTimeframe = intelPair.first,
            generatedHealthReport = intelPair.second
        )
    }

    val uiState: StateFlow<ModulesHubUiState> = userProfileRepository.observeActiveProfile()
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(ModulesHubUiState())
            } else {
                val coreFlow = combine(
                    moduleRepository.observeModules(),
                    waterRepository.observeLast60Days(profile.id),
                    periodRepository.observeRecords(profile.id),
                    allModulesRepo.observeExercises(profile.id),
                    allModulesRepo.observeSleep(profile.id),
                    allModulesRepo.observeAllSteps(profile.id),
                    allModulesRepo.observeDiet(profile.id),
                    allModulesRepo.observeMedicines(profile.id),
                    allModulesRepo.observeTodayMedicineIntakes(profile.id),
                    allModulesRepo.observeConsultations(profile.id),
                    allModulesRepo.observeBeautyCare(profile.id),
                    allModulesRepo.observeTodayWellbeing(profile.id),
                    allModulesRepo.observeAiPreference(profile.id),
                    allModulesRepo.observeAllWellbeing(profile.id)
                ) { arr -> arr }

                val settingsSnapshotFlow = preferencesManager.preferencesFlow
                    .map { Triple(it.wellbeingWarningPercent, it.aiDataAuth, it.healthConnectImportAuth) }
                    .distinctUntilChanged()

                val nutritionAndVaultFlow = combine(
                    allModulesRepo.observeNutritionPreference(profile.id),
                    allModulesRepo.observeLatestNutritionPlan(profile.id),
                    healthVaultRepository?.observeAllergies(profile.id) ?: flowOf(emptyList())
                ) { pref, plan, allergies -> Triple(pref, plan, allergies) }

                if (routineMoodRepository != null && seededPhase6ProfileIds.add(profile.id)) {
                    viewModelScope.launch {
                        routineMoodRepository.ensureDefaultCatalogSeeded(profile.id)
                    }
                }
                val phase6Flow = combine(
                    routineMoodRepository?.observeRoutines(profile.id) ?: flowOf(emptyList()),
                    routineMoodRepository?.observeRecentCompletions(profile.id, 30) ?: flowOf(emptyList()),
                    routineMoodRepository?.observeMoodEntries(profile.id) ?: flowOf(emptyList()),
                    routineMoodRepository?.observeChallenges(profile.id) ?: flowOf(emptyList()),
                    routineMoodRepository?.observeBadges(profile.id) ?: flowOf(emptyList())
                ) { routines, completions, moods, challenges, badges ->
                    listOf(routines, completions, moods, challenges, badges)
                }

                val dataSummaryFlow = combine(
                    coreFlow,
                    combine(
                        allModulesRepo.observeRecentMedicineIntakes(profile.id),
                        allModulesRepo.observeMedicineIntakesLast60Days(profile.id),
                        customAlarmRepository.observeAlarms(profile.id)
                    ) { recent, intakes60d, alarms -> Triple(recent, intakes60d, alarms) },
                    combine(
                        fitnessSyncRepo.observeConnectedSources(profile.id),
                        fitnessSyncRepo.observeVitalMetrics(profile.id),
                        nutritionAndVaultFlow,
                        phase6Flow
                    ) { sources, vitals, nv, p6 -> listOf(sources, vitals, nv, p6) },
                    settingsSnapshotFlow
                ) { arr, medicineAndAlarms, syncAndNutritionAndP6, settingsTriple ->
                    val recentIntakes = medicineAndAlarms.first
                    val intakes60d = medicineAndAlarms.second
                    val customAlarms = medicineAndAlarms.third
                    @Suppress("UNCHECKED_CAST")
                    val sources = syncAndNutritionAndP6[0] as List<ConnectedDeviceSourceEntity>
                    @Suppress("UNCHECKED_CAST")
                    val vitals = syncAndNutritionAndP6[1] as List<HealthVitalMetricEntity>
                    @Suppress("UNCHECKED_CAST")
                    val nv = syncAndNutritionAndP6[2] as Triple<NutritionPreferenceEntity?, NutritionPlanEntity?, List<AllergyRecordEntity>>
                    val nutritionPref = nv.first
                    val latestPlanEntity = nv.second
                    val vaultAllergies = nv.third
                    @Suppress("UNCHECKED_CAST")
                    val p6 = syncAndNutritionAndP6[3] as List<Any>
                    @Suppress("UNCHECKED_CAST")
                    val routinesList = p6[0] as List<DailyRoutineEntity>
                    @Suppress("UNCHECKED_CAST")
                    val routineCompletions = p6[1] as List<RoutineCompletionEntity>
                    @Suppress("UNCHECKED_CAST")
                    val moodEntriesList = p6[2] as List<MoodJournalEntryEntity>
                    @Suppress("UNCHECKED_CAST")
                    val challengesList = p6[3] as List<WellnessChallengeEntity>
                    @Suppress("UNCHECKED_CAST")
                    val badgesList = p6[4] as List<AchievementBadgeEntity>
                    val warningPercent = settingsTriple.first
                    val aiDataAuth = settingsTriple.second
                    val hcImportAuth = settingsTriple.third

                    @Suppress("UNCHECKED_CAST")
                    val modules = arr[0] as List<ModuleActivationEntity>
                    @Suppress("UNCHECKED_CAST")
                    val water60d = arr[1] as List<WaterRecordEntity>
                    @Suppress("UNCHECKED_CAST")
                    val period = arr[2] as List<PeriodRecordEntity>
                    @Suppress("UNCHECKED_CAST")
                    val exercises = arr[3] as List<ExerciseRecordEntity>
                    @Suppress("UNCHECKED_CAST")
                    val sleep = arr[4] as List<SleepRecordEntity>
                    @Suppress("UNCHECKED_CAST")
                    val allSteps = arr[5] as List<StepRecordEntity>
                    val todaySteps = allSteps.firstOrNull { it.dayKey == DateEpochUtils.todayDayKey() }
                    @Suppress("UNCHECKED_CAST")
                    val diet = arr[6] as List<DietRecordEntity>
                    @Suppress("UNCHECKED_CAST")
                    val medicines = arr[7] as List<MedicineEntity>
                    @Suppress("UNCHECKED_CAST")
                    val medicineIntakes = arr[8] as List<MedicineIntakeEntity>
                    @Suppress("UNCHECKED_CAST")
                    val consultations = arr[9] as List<DoctorConsultationEntity>
                    @Suppress("UNCHECKED_CAST")
                    val beauty = arr[10] as List<BeautyCareRecordEntity>
                    val wellbeing = arr[11] as DigitalWellbeingRecordEntity?
                    val aiPref = arr[12] as AiPreferenceEntity?
                    @Suppress("UNCHECKED_CAST")
                    val wellbeingHistory = arr[13] as List<DigitalWellbeingRecordEntity>

                    val topApps = wellbeing?.let {
                        WellbeingMetricsCalculator.decodeTopApps(it.topAppsJson)
                    }.orEmpty()
                    val longApps = WellbeingMetricsCalculator.longUsedApps(
                        apps = topApps,
                        dailyCapMinutes = wellbeing?.mindfulGoalMinutes ?: 120
                    )
                    val categories = WellbeingMetricsCalculator.categoryBreakdown(topApps)
                    val balanceScore = WellbeingMetricsCalculator.computeMindfulBalanceScore(wellbeing)
                    val overuseAlert = WellbeingMetricsCalculator.evaluateOveruse(
                        record = wellbeing,
                        warningPercent = warningPercent,
                        topApps = topApps
                    )

                    val water7dStart = DateEpochUtils.dayKeyDaysAgo(6)
                    val hydration = HydrationCalculator.summarize(
                        recordsLast7Days = water60d.filter { it.dayKey >= water7dStart },
                        goalMl = profile.dailyWaterGoalMl
                    )
                    val cycle = CycleCalculator.analyze(
                        period,
                        profile.averageCycleLengthDays,
                        profile.averagePeriodDurationDays
                    )
                    val alertMetrics = AlertMetricsCalculator.summarize(customAlarms)
                    val briefing = OnDeviceWellnessAdvisor.buildDailyBriefing(
                        profile = profile,
                        hydration = hydration,
                        cycle = cycle,
                        exercises = exercises,
                        diet = diet,
                        sleep = sleep,
                        steps = todaySteps,
                        wellbeing = wellbeing,
                        aiPref = aiPref,
                        medicines = medicines,
                        todayIntakes = medicineIntakes,
                        consultations = consultations,
                        connectedSources = sources,
                        vitalMetrics = vitals
                    )
                    val healthIntelligence = PersonalHealthIntelligenceEngine.analyze(
                        profile = profile,
                        authorization = aiDataAuth,
                        waterRecords60d = water60d,
                        sleepRecords = sleep,
                        exercises = exercises,
                        stepRecords = allSteps,
                        dietRecords = diet,
                        medicines = medicines,
                        medicineIntakes60d = intakes60d,
                        consultations = consultations,
                        beautyRecords = beauty,
                        wellbeingRecords = wellbeingHistory,
                        cycleInsights = cycle,
                        connectedSources = sources,
                        vitalMetrics = vitals,
                        customAlarms = customAlarms,
                        alertMetrics = alertMetrics,
                        aiPref = aiPref
                    )
                    val selectedRegion = IndianRegion.entries.firstOrNull {
                        it.name == nutritionPref?.preferredRegion
                    } ?: IndianRegion.SOUTH
                    val selectedDietary = IndianDietaryPreference.entries.firstOrNull {
                        it.name == nutritionPref?.dietaryPreference
                    } ?: IndianDietaryPreference.VEGETARIAN
                    val selectedGoal = NutritionGoalType.entries.firstOrNull {
                        it.name == nutritionPref?.nutritionGoal
                    } ?: NutritionGoalType.BALANCED
                    val avoidAllergens = nutritionPref?.avoidVaultAllergens ?: true
                    val nutritionTarget = IndianNutritionEngine.calculateTargets(
                        profile = profile,
                        goal = selectedGoal,
                        dietaryPreference = selectedDietary,
                        customCalorieTarget = nutritionPref?.customCalorieTarget
                    )
                    val todayNutritionProgress = IndianNutritionEngine.evaluateTodayIntake(
                        dietRecords = diet,
                        target = nutritionTarget
                    )
                    val activeMealPlan = latestPlanEntity?.let { AllModulesRepository.entityToMealPlanResult(it) }
                    val routineAdherence = RoutineMoodAnalyticsEngine.summarizeRoutineAdherence(
                        routines = routinesList,
                        completions = routineCompletions
                    )
                    val moodAnalytics = RoutineMoodAnalyticsEngine.analyzeMoodJournal(
                        entries = moodEntriesList,
                        sleepRecords = sleep,
                        waterRecords = water60d,
                        routineCompletions = routineCompletions
                    )

                    ModulesHubUiState(
                        profile = profile,
                        modules = modules,
                        hydration = hydration,
                        cycleInsights = cycle,
                        exercises = exercises,
                        sleepRecords = sleep,
                        todaySteps = todaySteps,
                        dietRecords = diet,
                        medicines = medicines,
                        todayMedicineIntakes = medicineIntakes,
                        recentMedicineIntakes = recentIntakes,
                        consultations = consultations,
                        beautyRecords = beauty,
                        todayWellbeing = wellbeing,
                        aiPreference = aiPref,
                        dailyBriefing = briefing,
                        connectedSources = sources,
                        vitalMetrics = vitals,
                        wellbeingHistory = wellbeingHistory.take(7),
                        topAppsUsage = topApps,
                        longUsedApps = longApps,
                        categoryScreenMinutes = categories,
                        mindfulBalanceScore = balanceScore,
                        wellbeingOveruseAlert = overuseAlert,
                        wellbeingWarningPercent = warningPercent,
                        customAlarms = customAlarms,
                        alertMetrics = alertMetrics,
                        healthIntelligence = healthIntelligence,
                        aiDataAuthorization = aiDataAuth,
                        nutritionPreference = nutritionPref,
                        selectedIndianRegion = selectedRegion,
                        selectedDietaryPreference = selectedDietary,
                        selectedNutritionGoal = selectedGoal,
                        avoidVaultAllergens = avoidAllergens,
                        nutritionTarget = nutritionTarget,
                        todayNutritionProgress = todayNutritionProgress,
                        vaultAllergies = vaultAllergies,
                        activeIndianMealPlan = activeMealPlan,
                        healthConnectImportAuth = hcImportAuth,
                        routines = routinesList,
                        routineAdherence = routineAdherence,
                        moodEntries = moodEntriesList,
                        moodAnalytics = moodAnalytics,
                        wellnessChallenges = challengesList,
                        achievementBadges = badgesList,
                        latestMoodEntry = moodEntriesList.firstOrNull()
                    )
                }

                val autoRefreshAndSleepFlow = combine(
                    preferencesManager.preferencesFlow,
                    appAutoRefreshManager?.state ?: flowOf(AppAutoRefreshState()),
                    SleepMonitorStateStore.state
                ) { prefs, refreshState, sleepLive ->
                    Triple(prefs, refreshState, sleepLive)
                }

                val personalAiKeyOverlayFlow = combine(
                    personalAiKeyStore?.keyState ?: flowOf(PersonalAiKeyState()),
                    isTestingPersonalKeyFlow,
                    personalKeyFeedbackFlow
                ) { keyState, testing, feedback ->
                    Triple(keyState, testing, feedback)
                }

                combine(dataSummaryFlow, aiFlow, autoRefreshAndSleepFlow, personalAiKeyOverlayFlow) { base, overlay, syncSleep, aiKeyOverlay ->
                    val isSnoozed = System.currentTimeMillis() < overlay.snoozedUntilMs
                    val prefs = syncSleep.first
                    val refreshState = syncSleep.second
                    val sleepLive = syncSleep.third
                    val effectiveRefreshEpoch = maxOf(refreshState.lastRefreshedEpochMs, prefs.lastAutoRefreshEpochMs)
                    val effectiveRefreshSummary = refreshState.lastSummary.ifBlank { prefs.lastAutoRefreshSummary }
                    base.copy(
                        aiChatMessages = overlay.chat,
                        selectedVoiceLanguageTag = overlay.languageTag,
                        autoSpeakReplies = overlay.autoSpeak,
                        aiTaskStatus = overlay.aiStatus,
                        isSpeakingVoice = overlay.isSpeaking,
                        isListeningVoice = overlay.isListening,
                        voicePartialTranscript = overlay.partialTranscript,
                        voiceError = overlay.voiceError,
                        lastPrescriptionResult = overlay.prescription,
                        pendingNavigationModuleKey = overlay.navTarget,
                        lastAiPrompt = overlay.lastAiPrompt,
                        aiDiagnosticReport = overlay.diagnosticReport,
                        isRunningDiagnostics = overlay.isRunningDiagnostics,
                        personalAiKeyState = aiKeyOverlay.first,
                        isTestingPersonalAiKey = aiKeyOverlay.second,
                        personalAiKeyFeedback = aiKeyOverlay.third,
                        healthConnectAvailability = overlay.hcAvailability,
                        grantedHealthPermissions = overlay.grantedPerms,
                        isSyncingFitness = overlay.isSyncingFitness,
                        fitnessSyncBanner = overlay.syncBanner,
                        hasUsageAccessPermission = overlay.hasUsageAccess,
                        hasNotificationPermission = overlay.hasNotificationPermission,
                        isWellbeingAlertSnoozed = isSnoozed,
                        wellbeingOveruseAlertsEnabled = overlay.alertsEnabled,
                        wellbeingWarningPercent = overlay.warningPercent,
                        isSyncingWellbeing = overlay.isSyncingWellbeing,
                        wellbeingSyncBanner = overlay.wellbeingBanner,
                        selectedIntelligenceTimeframe = overlay.selectedTimeframe,
                        generatedAiHealthReport = overlay.generatedHealthReport,
                        isGeneratingMealPlan = overlay.isGeneratingMealPlan,
                        mealPlanStatusMessage = overlay.mealPlanStatusMessage,
                        autoRefreshEnabled = prefs.autoRefreshEnabled,
                        autoRefreshIntervalMinutes = prefs.autoRefreshIntervalMinutes,
                        isAutoRefreshingAll = refreshState.isRefreshing || overlay.isSyncingWellbeing || overlay.isSyncingFitness,
                        lastAutoRefreshEpochMs = effectiveRefreshEpoch,
                        lastAutoRefreshSummary = effectiveRefreshSummary,
                        sleepMonitorLiveState = sleepLive,
                        sleepAutoBedtimePromptEnabled = prefs.sleepAutoBedtimePromptEnabled,
                        sleepBedtimeHour = prefs.sleepBedtimeHour,
                        sleepBedtimeMinute = prefs.sleepBedtimeMinute,
                        sleepWakeHour = prefs.sleepWakeHour,
                        sleepWakeMinute = prefs.sleepWakeMinute
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = ModulesHubUiState()
        )

    fun refreshHealthConnectState() {
        hcAvailability.value = healthConnectManager.checkAvailability()
        viewModelScope.launch {
            grantedHealthPerms.value = healthConnectManager.getGrantedPermissions()
        }
    }

    fun onHealthPermissionsResult(granted: Set<String>) {
        grantedHealthPerms.value = granted
        if (granted.isNotEmpty()) {
            syncFromHealthConnect()
        } else {
            fitnessSyncBanner.value = "Health Connect permissions were not granted yet."
        }
    }

    fun setHealthConnectCategoryEnabled(category: HealthConnectDataCategory, enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setHealthConnectCategoryEnabled(category, enabled)
        }
    }

    fun syncFromHealthConnect() {
        val snapshot = uiState.value
        val pid = snapshot.profile?.id ?: return
        val enabledCategories = snapshot.healthConnectImportAuth.enabledCategories()
        viewModelScope.launch {
            isSyncingFitness.value = true
            fitnessSyncBanner.value = null
            hcAvailability.value = healthConnectManager.checkAvailability()
            grantedHealthPerms.value = healthConnectManager.getGrantedPermissions()
            val result = healthConnectManager.readRecentMetrics(
                daysBack = 7,
                enabledCategories = enabledCategories
            )
            result.onSuccess { payload ->
                val saved = fitnessSyncRepo.ingestSyncPayload(pid, payload)
                val breakdown = buildList {
                    if (payload.dailySteps.isNotEmpty()) add("${payload.dailySteps.size}d steps")
                    if (payload.workouts.isNotEmpty()) add("${payload.workouts.size} workouts")
                    if (payload.sleepSessions.isNotEmpty()) add("${payload.sleepSessions.size} sleep")
                    if (payload.waterEntries.isNotEmpty()) add("${payload.waterEntries.size} hydration")
                    if (payload.nutritionEntries.isNotEmpty()) add("${payload.nutritionEntries.size} meals")
                }.joinToString(", ")
                val sourceNames = payload.sources.joinToString(", ") { "${it.emoji} ${it.displayName}" }
                    .ifBlank { "Health Connect" }
                fitnessSyncBanner.value = if (saved > 0) {
                    "✅ Synced $saved updates ($breakdown) from $sourceNames"
                } else {
                    "ℹ️ Connected to $sourceNames! No new records found in the last 7 days for enabled categories."
                }
            }.onFailure { err ->
                fitnessSyncBanner.value = err.localizedMessage ?: "Unable to read Health Connect data."
            }
            isSyncingFitness.value = false
        }
    }

    fun importFitnessDocument(context: Context, uri: Uri) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            isSyncingFitness.value = true
            fitnessSyncBanner.value = null
            val parsed = withContext(Dispatchers.IO) {
                appleImporter.importFromUri(context, uri)
            }
            parsed.onSuccess { payload ->
                val saved = fitnessSyncRepo.ingestSyncPayload(pid, payload)
                fitnessSyncBanner.value = if (saved > 0) {
                    "🍎 Imported $saved metric updates from ${payload.sources.firstOrNull()?.displayName ?: "your file"}!"
                } else {
                    "ℹ️ File parsed, but no supported step/heart/workout/sleep entries were found."
                }
            }.onFailure { err ->
                fitnessSyncBanner.value = err.localizedMessage ?: "Failed to import the selected file."
            }
            isSyncingFitness.value = false
        }
    }

    fun deleteConnectedSource(id: Long) = viewModelScope.launch { fitnessSyncRepo.deleteSource(id) }

    fun deleteVitalMetric(id: Long) = viewModelScope.launch { fitnessSyncRepo.deleteVitalMetric(id) }

    fun clearFitnessBanner() {
        fitnessSyncBanner.value = null
    }

    fun logQuickHomeMood(preset: MoodPresetOption) {
        val pid = uiState.value.profile?.id ?: return
        val repo = routineMoodRepository ?: return
        viewModelScope.launch {
            repo.logMoodEntry(
                profileId = pid,
                preset = preset,
                energyLevel = preset.defaultEnergy,
                stressLevel = preset.defaultStress,
                tags = emptyList(),
                reflectionNote = "Quick check-in from Home",
                gratitudeText = ""
            )
        }
    }

    // --- Voice & Multilingual AI Guide + Prescription Extraction ---

    fun selectVoiceLanguage(tag: String) {
        selectedLanguageTag.value = tag
    }

    fun setAutoSpeak(enabled: Boolean) {
        autoSpeakReplies.value = enabled
        if (!enabled) voiceManager.stopSpeaking()
    }

    fun stopVoicePlayback() {
        voiceManager.stopSpeaking()
    }

    fun startVoiceCapture() {
        voiceManager.startListening(selectedLanguageTag.value) { spokenText ->
            askAiAssistant(spokenText)
        }
    }

    fun stopVoiceCapture() {
        voiceManager.stopListening()
    }

    fun clearVoiceError() {
        voiceManager.clearError()
    }

    fun consumePendingNavigation() {
        pendingNavModuleKey.value = null
    }

    fun clearPrescriptionPreview() {
        lastPrescription.value = null
    }

    fun retryLastAiPrompt() {
        val last = lastAiPromptFlow.value
        if (!last.isNullOrBlank()) {
            askAiAssistant(last)
        } else {
            playMultilingualAppGuide()
        }
    }

    fun runAiDiagnostics() {
        if (isRunningDiagFlow.value) return
        viewModelScope.launch {
            isRunningDiagFlow.value = true
            try {
                diagnosticReportFlow.value = aiService.runDiagnostics()
            } finally {
                isRunningDiagFlow.value = false
            }
        }
    }

    fun dismissDiagnostics() {
        diagnosticReportFlow.value = null
    }

    fun saveAndTestPersonalAiKey(rawKey: String) {
        val store = personalAiKeyStore ?: return
        val saved = store.savePersonalKey(rawKey)
        saved.onFailure { err ->
            personalKeyFeedbackFlow.value = false to (err.message ?: "Unable to save API key.")
            return
        }
        testSavedPersonalAiKey()
    }

    fun testSavedPersonalAiKey() {
        if (isTestingPersonalKeyFlow.value) return
        viewModelScope.launch {
            isTestingPersonalKeyFlow.value = true
            personalKeyFeedbackFlow.value = null
            try {
                aiService.testPersonalApiKey().fold(
                    onSuccess = { ping ->
                        personalKeyFeedbackFlow.value = true to
                            "Connected via ${ping.modelUsed} (${ping.latencyMs}ms). All AI features are now active!"
                        diagnosticReportFlow.value = aiService.runDiagnostics()
                    },
                    onFailure = { err ->
                        personalKeyFeedbackFlow.value = false to
                            (err.localizedMessage ?: "Saved key could not connect to Gemini API.")
                    }
                )
            } finally {
                isTestingPersonalKeyFlow.value = false
            }
        }
    }

    fun removePersonalAiKey() {
        personalAiKeyStore?.clearPersonalKey()
        diagnosticReportFlow.value = null
        personalKeyFeedbackFlow.value = true to "Personal API key removed. Switched back to Firebase AI Logic (Default)."
    }

    fun clearPersonalAiKeyFeedback() {
        personalKeyFeedbackFlow.value = null
    }

    fun selectIntelligenceTimeframe(timeframe: IntelligenceTimeframe) {
        selectedTimeframeFlow.value = timeframe
        val current = generatedHealthReportFlow.value
        if (current != null && current.timeframe != timeframe.name) {
            generatedHealthReportFlow.value = null
        }
    }

    fun setAiModuleSharing(moduleKey: String, enabled: Boolean) {
        viewModelScope.launch {
            preferencesManager.setAiModuleSharing(moduleKey, enabled)
            generatedHealthReportFlow.value = null
        }
    }

    fun clearAiHealthIntelligenceReport() {
        generatedHealthReportFlow.value = null
    }

    fun generateAiHealthIntelligence(
        timeframe: IntelligenceTimeframe = uiState.value.selectedIntelligenceTimeframe
    ) {
        selectedTimeframeFlow.value = timeframe
        val lang = SupportedVoiceLanguages.byTag(selectedLanguageTag.value)
        val snapshot = uiState.value.healthIntelligence
        viewModelScope.launch {
            aiTaskStatus.value = AiTaskStatus.Loading("Checking App Check...")
            val result = aiService.generatePersonalHealthIntelligence(
                timeframeName = timeframe.name,
                languageOption = lang,
                groundedWellnessContext = snapshot.groundedAiPromptContext,
                onStageUpdate = { stage -> aiTaskStatus.value = AiTaskStatus.Loading(stage) },
                onPartialPreview = { preview ->
                    aiTaskStatus.value = AiTaskStatus.Streaming("Synthesizing ${timeframe.label} briefing...", preview)
                }
            )
            result.onSuccess { report ->
                aiTaskStatus.value = AiTaskStatus.Idle
                generatedHealthReportFlow.value = report
                if (autoSpeakReplies.value) {
                    val spoken = buildString {
                        append(report.headline).append(". ")
                        append(report.recordedFactsSummary).append(" ")
                        report.personalizedSuggestions.firstOrNull()?.let { append(it) }
                    }
                    voiceManager.speak(spoken, lang.tag)
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val msg = err.localizedMessage ?: "AI synthesis unavailable. Showing your local grounded insights."
                aiTaskStatus.value = AiTaskStatus.Error(msg)
            }
        }
    }

    fun speakIntelligenceSummary() {
        val state = uiState.value
        val lang = SupportedVoiceLanguages.byTag(state.selectedVoiceLanguageTag)
        val activeReport = state.generatedAiHealthReport
            ?.takeIf { it.timeframe == state.selectedIntelligenceTimeframe.name }
            ?: state.healthIntelligence.offlineReportFor(state.selectedIntelligenceTimeframe)
        val spoken = buildString {
            append(activeReport.headline).append(". ")
            append(activeReport.recordedFactsSummary).append(" ")
            activeReport.personalizedSuggestions.take(2).forEach { append(it).append(" ") }
            append(activeReport.disclaimer)
        }
        voiceManager.speak(spoken, lang.tag)
    }

    fun answerOfflineHealthQuestion(prompt: String? = lastAiPromptFlow.value) {
        val clean = prompt?.trim().orEmpty()
        if (clean.isEmpty()) return
        val answer = PersonalHealthIntelligenceEngine.answerGroundedQuestion(
            prompt = clean,
            snapshot = uiState.value.healthIntelligence
        )
        val lang = SupportedVoiceLanguages.byTag(selectedLanguageTag.value)
        aiTaskStatus.value = AiTaskStatus.Idle
        aiMessages.update { list ->
            list + LocalAiMessage(
                id = nextMsgId++,
                isFromUser = false,
                text = answer,
                actionBadges = listOf("📊 Grounded On-Device Summary")
            )
        }
        if (autoSpeakReplies.value) {
            voiceManager.speak(answer, lang.tag)
        }
    }

    fun playMultilingualAppGuide() {
        val lang = SupportedVoiceLanguages.byTag(selectedLanguageTag.value)
        val contextSummary = uiState.value.healthIntelligence.groundedAiPromptContext
        viewModelScope.launch {
            aiTaskStatus.value = AiTaskStatus.Loading("Checking App Check...")
            val result = aiService.generateMultilingualAppGuide(
                languageOption = lang,
                wellnessContextSummary = contextSummary,
                onStageUpdate = { stage -> aiTaskStatus.value = AiTaskStatus.Loading(stage) },
                onPartialPreview = { preview ->
                    aiTaskStatus.value = AiTaskStatus.Streaming("Streaming ${lang.label} guide...", preview)
                }
            )
            result.onSuccess { outcome ->
                aiTaskStatus.value = AiTaskStatus.Idle
                aiMessages.update { list ->
                    list + LocalAiMessage(
                        id = nextMsgId++,
                        isFromUser = false,
                        text = outcome.replyText
                    )
                }
                if (autoSpeakReplies.value) {
                    voiceManager.speak(outcome.replyText, lang.tag)
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val msg = err.localizedMessage ?: "Unable to generate cloud AI guide."
                val offlineDaily = uiState.value.healthIntelligence.offlineReportFor(IntelligenceTimeframe.DAILY)
                val localSummary = "${offlineDaily.headline}\n${offlineDaily.recordedFactsSummary}"
                aiTaskStatus.value = AiTaskStatus.Error(msg)
                aiMessages.update { list ->
                    list + LocalAiMessage(
                        id = nextMsgId++,
                        isFromUser = false,
                        text = msg,
                        actionBadges = listOf("📊 Auto Local Summary"),
                        isError = true,
                        fallbackSummary = localSummary
                    )
                }
            }
        }
    }

    fun askAiAssistant(prompt: String) {
        val clean = prompt.trim()
        if (clean.isEmpty()) return
        val lang = SupportedVoiceLanguages.byTag(selectedLanguageTag.value)
        val snapshot = uiState.value
        lastAiPromptFlow.value = clean

        aiMessages.update { list ->
            list + LocalAiMessage(nextMsgId++, isFromUser = true, text = clean)
        }

        viewModelScope.launch {
            aiTaskStatus.value = AiTaskStatus.Loading("Checking App Check...")
            val result = aiService.processVoiceOrTextCommand(
                userMessage = clean,
                languageOption = lang,
                wellnessContextSummary = snapshot.healthIntelligence.groundedAiPromptContext,
                onStageUpdate = { stage -> aiTaskStatus.value = AiTaskStatus.Loading(stage) },
                onPartialPreview = { preview ->
                    aiTaskStatus.value = AiTaskStatus.Streaming("Streaming in ${lang.label}...", preview)
                }
            )
            result.onSuccess { outcome ->
                val badges = executeAiActions(outcome.actions)
                aiTaskStatus.value = AiTaskStatus.Idle
                aiMessages.update { list ->
                    list + LocalAiMessage(
                        id = nextMsgId++,
                        isFromUser = false,
                        text = outcome.replyText,
                        actionBadges = badges
                    )
                }
                if (autoSpeakReplies.value) {
                    voiceManager.speak(outcome.replyText, lang.tag)
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val msg = err.localizedMessage ?: "Cloud AI unavailable. Check connection or retry."
                val localFallback = PersonalHealthIntelligenceEngine.answerGroundedQuestion(
                    prompt = clean,
                    snapshot = snapshot.healthIntelligence
                )
                aiTaskStatus.value = AiTaskStatus.Error(msg)
                aiMessages.update { list ->
                    list + LocalAiMessage(
                        id = nextMsgId++,
                        isFromUser = false,
                        text = msg,
                        actionBadges = listOf("📊 Auto Local Summary"),
                        isError = true,
                        fallbackSummary = localFallback
                    )
                }
            }
        }
    }

    fun scanAndImportPrescription(context: Context, imageUri: Uri) {
        val pid = uiState.value.profile?.id ?: return
        val lang = SupportedVoiceLanguages.byTag(selectedLanguageTag.value)

        viewModelScope.launch {
            aiTaskStatus.value = AiTaskStatus.Loading("Checking App Check...")
            val result = aiService.extractPrescriptionFromUri(
                context = context,
                imageUri = imageUri,
                languageOption = lang,
                onStageUpdate = { stage -> aiTaskStatus.value = AiTaskStatus.Loading(stage) },
                onPartialPreview = { preview ->
                    aiTaskStatus.value = AiTaskStatus.Streaming("Reading prescription...", preview)
                }
            )
            result.onSuccess { extracted ->
                val badges = mutableListOf<String>()
                extracted.medicines.forEach { med ->
                    allModulesRepo.addMedicine(
                        profileId = pid,
                        name = med.name,
                        dosage = med.dosage,
                        scheduleTime24h = med.scheduleTime24h,
                        frequency = if (med.intervalHours < 24) {
                            "Every ${med.intervalHours}h (${med.dosesPerDay}x/day)"
                        } else {
                            "Once Daily"
                        },
                        dosesPerDay = med.dosesPerDay,
                        intervalHours = med.intervalHours,
                        mealRelation = med.mealRelation,
                        categoryEmoji = "💊",
                        notes = med.notes.ifBlank { "Added from prescription photo" }
                    )
                    scheduleAutoAlarmForMedicine(
                        profileId = pid,
                        name = med.name,
                        dosage = med.dosage,
                        time24h = med.scheduleTime24h,
                        intervalHours = med.intervalHours,
                        mealRelation = med.mealRelation,
                        emoji = "💊",
                        notes = med.notes.ifBlank { "From prescription" }
                    )
                    badges += "💊 ${med.name} (${med.dosage})"
                }
                if (extracted.doctorName.isNotBlank()) {
                    val followDay = if (extracted.followUpDaysFromToday > 0) {
                        DateEpochUtils.todayEpochDay() + extracted.followUpDaysFromToday.toLong()
                    } else {
                        null
                    }
                    allModulesRepo.addConsultation(
                        profileId = pid,
                        doctorName = extracted.doctorName,
                        specialty = extracted.specialty,
                        clinicOrHospital = extracted.clinicOrHospital,
                        summary = extracted.summary,
                        followUpEpochDay = followDay,
                        followUpNotes = extracted.followUpNotes
                    )
                    badges += "🩺 Dr. ${extracted.doctorName}"
                }

                lastPrescription.value = extracted
                aiTaskStatus.value = AiTaskStatus.Idle
                aiMessages.update { list ->
                    list + LocalAiMessage(
                        id = nextMsgId++,
                        isFromUser = false,
                        text = "📄 ${extracted.summary}",
                        actionBadges = badges
                    )
                }
                if (autoSpeakReplies.value) {
                    voiceManager.speak(extracted.summary, lang.tag)
                }
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val msg = err.localizedMessage ?: "Could not extract prescription details."
                aiTaskStatus.value = AiTaskStatus.Error(msg)
                aiMessages.update { list ->
                    list + LocalAiMessage(nextMsgId++, isFromUser = false, text = msg, isError = true)
                }
            }
        }
    }

    private suspend fun executeAiActions(actions: List<AiParsedAction>): List<String> {
        val pid = uiState.value.profile?.id ?: return emptyList()
        val badges = mutableListOf<String>()
        for (action in actions) {
            when (action.actionType) {
                AiActionType.LOG_WATER -> {
                    val ml = if (action.amountOrValue > 0) action.amountOrValue else 250
                    val drink = action.secondaryText.ifBlank { action.titleOrName.ifBlank { "Water" } }
                    waterRepository.logWater(pid, ml, drink, "Voice/AI log")
                    badges += "💧 +$ml ml ($drink)"
                }

                AiActionType.LOG_MEAL -> {
                    val food = action.titleOrName.ifBlank { "Logged Snack" }
                    val mealType = action.secondaryText.ifBlank { "Snack" }
                    val kcal = if (action.calories > 0) action.calories else 60
                    allModulesRepo.logMeal(pid, mealType, food, kcal, 2, 6, 2)
                    badges += "🥗 $food ($kcal kcal)"
                }

                AiActionType.LOG_MEDICINE -> {
                    val medName = action.titleOrName.ifBlank { "Supplement" }
                    val dosage = action.secondaryText.ifBlank { "1 dose" }
                    allModulesRepo.addMedicine(
                        profileId = pid,
                        name = medName,
                        dosage = dosage,
                        scheduleTime24h = "08:00",
                        frequency = if (action.intervalHours < 24) {
                            "Every ${action.intervalHours}h (${action.dosesPerDay}x/day)"
                        } else {
                            "Once Daily"
                        },
                        dosesPerDay = action.dosesPerDay,
                        intervalHours = action.intervalHours,
                        mealRelation = action.mealRelation,
                        categoryEmoji = "💊",
                        notes = "Added via AI Assistant"
                    )
                    scheduleAutoAlarmForMedicine(
                        profileId = pid,
                        name = medName,
                        dosage = dosage,
                        time24h = "08:00",
                        intervalHours = action.intervalHours,
                        mealRelation = action.mealRelation,
                        emoji = "💊",
                        notes = "Added via AI Assistant"
                    )
                    badges += "💊 Added $medName ($dosage)"
                }

                AiActionType.LOG_DOCTOR_VISIT -> {
                    val doc = action.titleOrName.ifBlank { "Doctor" }
                    val followDay = if (action.amountOrValue > 0) {
                        DateEpochUtils.todayEpochDay() + action.amountOrValue.toLong()
                    } else {
                        null
                    }
                    allModulesRepo.addConsultation(
                        profileId = pid,
                        doctorName = doc,
                        specialty = action.secondaryText.ifBlank { "General Care" },
                        summary = "Logged via AI Assistant",
                        followUpEpochDay = followDay
                    )
                    badges += "🩺 Added Dr. $doc"
                }

                AiActionType.LOG_EXERCISE -> {
                    val mins = action.amountOrValue.coerceIn(5, 360)
                    val title = action.titleOrName.ifBlank { "🏃 Workout" }
                    val isYoga = title.contains("yoga", true)
                    allModulesRepo.logExercise(
                        profileId = pid,
                        activityType = title,
                        category = if (isYoga) "Yoga" else "Exercise",
                        durationMinutes = mins,
                        intensity = "Moderate",
                        caloriesBurned = if (action.calories > 0) action.calories else mins * 5
                    )
                    badges += "🏃 $title (${mins}m)"
                }

                AiActionType.LOG_SLEEP -> {
                    val hrs = (action.amountOrValue / 10f).coerceIn(1f, 16f)
                    allModulesRepo.logSleep(pid, hrs, 4, "Logged via AI Assistant")
                    badges += "😴 ${String.format("%.1f", hrs)}h Sleep"
                }

                AiActionType.LOG_STEPS -> {
                    val delta = action.amountOrValue.coerceIn(100, 50_000)
                    allModulesRepo.addTodaySteps(pid, uiState.value.todaySteps, delta)
                    badges += "👟 +$delta steps"
                }

                AiActionType.OPEN_MODULE -> {
                    val target = action.secondaryText.ifBlank { action.titleOrName }
                    if (target.isNotBlank()) {
                        pendingNavModuleKey.value = target
                        badges += "🧭 Opening $target"
                    }
                }

                AiActionType.NONE -> Unit
            }
        }
        return badges
    }

    // --- Existing Module Actions ---

    fun logExercise(
        activityType: String,
        category: String,
        durationMinutes: Int,
        intensity: String,
        caloriesBurned: Int,
        loggedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            allModulesRepo.logExercise(pid, activityType, category, durationMinutes, intensity, caloriesBurned, loggedAtEpochMs)
        }
    }

    fun deleteExercise(id: Long) = viewModelScope.launch { allModulesRepo.deleteExercise(id) }

    fun addSteps(delta: Int) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            allModulesRepo.addTodaySteps(pid, uiState.value.todaySteps, delta)
        }
    }

    fun logSleep(
        hours: Float,
        quality: Int,
        notes: String,
        wakeEpochMs: Long = System.currentTimeMillis()
    ) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch { allModulesRepo.logSleep(pid, hours, quality, notes, wakeEpochMs) }
    }

    fun deleteSleep(id: Long) = viewModelScope.launch { allModulesRepo.deleteSleep(id) }

    fun logMeal(
        mealType: String,
        foodSummary: String,
        calories: Int,
        protein: Int,
        carbs: Int,
        fat: Int,
        loggedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            allModulesRepo.logMeal(pid, mealType, foodSummary, calories, protein, carbs, fat, loggedAtEpochMs)
        }
    }

    fun deleteMeal(id: Long) = viewModelScope.launch { allModulesRepo.deleteMeal(id) }

    fun updateNutritionPreferences(
        region: IndianRegion = uiState.value.selectedIndianRegion,
        dietary: IndianDietaryPreference = uiState.value.selectedDietaryPreference,
        goal: NutritionGoalType = uiState.value.selectedNutritionGoal,
        customCalorieTarget: Int? = uiState.value.nutritionPreference?.customCalorieTarget,
        avoidVaultAllergens: Boolean = uiState.value.avoidVaultAllergens
    ) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            allModulesRepo.saveNutritionPreference(
                NutritionPreferenceEntity(
                    id = uiState.value.nutritionPreference?.id ?: 0L,
                    profileId = pid,
                    preferredRegion = region.name,
                    dietaryPreference = dietary.name,
                    nutritionGoal = goal.name,
                    customCalorieTarget = customCalorieTarget,
                    avoidVaultAllergens = avoidVaultAllergens
                )
            )
        }
    }

    fun logIndianFood(
        item: IndianFoodItem,
        servings: Float = 1f,
        mealType: String = item.mealSlots.firstOrNull() ?: "Lunch",
        loggedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        val pid = uiState.value.profile?.id ?: return
        val scaled = IndianNutritionEngine.scaleFood(item, servings)
        viewModelScope.launch {
            allModulesRepo.logMeal(
                profileId = pid,
                mealType = mealType,
                foodSummary = scaled.formattedName,
                calories = scaled.calories,
                proteinGrams = scaled.proteinGrams,
                carbsGrams = scaled.carbsGrams,
                fatGrams = scaled.fatGrams,
                loggedAtEpochMs = loggedAtEpochMs
            )
            mealPlanStatusFlow.value = "✅ Logged ${scaled.formattedName} (${scaled.calories} kcal, ${scaled.proteinGrams}g P)."
        }
    }

    fun logPlannedMealSlot(slot: PlannedIndianMealSlot) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            allModulesRepo.logMeal(
                profileId = pid,
                mealType = slot.mealType,
                foodSummary = "🇮🇳 ${slot.dishName} (${slot.portionLabel})",
                calories = slot.calories,
                proteinGrams = slot.proteinGrams,
                carbsGrams = slot.carbsGrams,
                fatGrams = slot.fatGrams
            )
            mealPlanStatusFlow.value = "✅ Logged ${slot.dishName} to ${slot.mealType}."
        }
    }

    fun logEntireMealPlan(plan: IndianMealPlanResult) {
        val pid = uiState.value.profile?.id ?: return
        if (plan.meals.isEmpty()) return
        viewModelScope.launch {
            plan.meals.forEach { slot ->
                allModulesRepo.logMeal(
                    profileId = pid,
                    mealType = slot.mealType,
                    foodSummary = "🇮🇳 ${slot.dishName} (${slot.portionLabel})",
                    calories = slot.calories,
                    proteinGrams = slot.proteinGrams,
                    carbsGrams = slot.carbsGrams,
                    fatGrams = slot.fatGrams
                )
            }
            mealPlanStatusFlow.value = "✅ Logged all ${plan.meals.size} meals (${plan.totalCalories} kcal total)."
        }
    }

    fun clearMealPlanStatus() {
        mealPlanStatusFlow.value = null
    }

    fun generateIndianMealPlan() {
        val state = uiState.value
        val pid = state.profile?.id ?: return
        if (isGeneratingMealPlanFlow.value) return
        val lang = SupportedVoiceLanguages.byTag(state.selectedVoiceLanguageTag)
        val region = state.selectedIndianRegion
        val dietary = state.selectedDietaryPreference
        val target = state.nutritionTarget
        val allergiesToAvoid = if (state.avoidVaultAllergens) state.vaultAllergies else emptyList()
        val allergenNames = allergiesToAvoid.map { it.allergen.trim() }.filter { it.isNotBlank() }.distinct()
        val loggedTodaySummary = if (state.todayNutritionProgress.todayMealCount == 0) {
            "No meals logged yet today."
        } else {
            "${state.todayNutritionProgress.consumedCalories} kcal consumed (${state.todayNutritionProgress.consumedProteinGrams}g protein across ${state.todayNutritionProgress.todayMealCount} meals)."
        }

        viewModelScope.launch {
            isGeneratingMealPlanFlow.value = true
            mealPlanStatusFlow.value = "Checking App Check & calibrating ${region.label} plan..."
            val result = aiService.generateIndianNutritionPlan(
                regionLabel = region.label,
                dietaryLabel = dietary.label,
                goalLabel = target.goal.label,
                targetCalories = target.targetCalories,
                targetProteinGrams = target.targetProteinGrams,
                targetCarbsGrams = target.targetCarbsGrams,
                targetFatGrams = target.targetFatGrams,
                alreadyLoggedTodaySummary = loggedTodaySummary,
                avoidAllergens = allergenNames,
                languageOption = lang,
                onStageUpdate = { stage -> mealPlanStatusFlow.value = stage },
                onPartialPreview = { title -> mealPlanStatusFlow.value = "✨ Designing: $title..." }
            )
            result.onSuccess { plan ->
                allModulesRepo.saveNutritionPlan(pid, plan)
                mealPlanStatusFlow.value = "✨ Gemini AI generated & saved your ${region.label} meal plan."
            }.onFailure { err ->
                if (err is CancellationException) throw err
                val offlinePlan = IndianNutritionEngine.generateOfflineIndianMealPlan(
                    region = region,
                    dietaryPreference = dietary,
                    target = target,
                    allergies = allergiesToAvoid
                )
                allModulesRepo.saveNutritionPlan(pid, offlinePlan)
                mealPlanStatusFlow.value = "🌿 Offline ${region.label} plan generated (cloud AI unavailable: ${err.localizedMessage ?: "offline"})."
            }
            isGeneratingMealPlanFlow.value = false
        }
    }

    fun addMedicine(
        name: String,
        dosage: String,
        time24h: String,
        frequency: String,
        dosesPerDay: Int = 1,
        intervalHours: Int = 24,
        mealRelation: String = "After Meal",
        categoryEmoji: String = "💊",
        notes: String = "",
        stockQuantity: Float = 0f,
        stockUnit: String = "tablets",
        unitsPerDose: Float = 1f,
        lowStockThresholdDays: Int = 5,
        expiryEpochDay: Long? = null
    ) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            allModulesRepo.addMedicine(
                profileId = pid,
                name = name,
                dosage = dosage,
                scheduleTime24h = time24h,
                frequency = frequency,
                dosesPerDay = dosesPerDay,
                intervalHours = intervalHours,
                mealRelation = mealRelation,
                categoryEmoji = categoryEmoji,
                notes = notes,
                stockQuantity = stockQuantity,
                stockUnit = stockUnit,
                unitsPerDose = unitsPerDose,
                lowStockThresholdDays = lowStockThresholdDays,
                expiryEpochDay = expiryEpochDay
            )
            scheduleAutoAlarmForMedicine(
                profileId = pid,
                name = name,
                dosage = dosage,
                time24h = time24h,
                intervalHours = intervalHours,
                mealRelation = mealRelation,
                emoji = categoryEmoji,
                notes = notes
            )
        }
    }

    private suspend fun scheduleAutoAlarmForMedicine(
        profileId: Long,
        name: String,
        dosage: String,
        time24h: String,
        intervalHours: Int = 24,
        mealRelation: String,
        emoji: String,
        notes: String
    ) {
        val cleanName = name.trim()
        if (cleanName.isEmpty()) return
        val alarmTitle = "Take $cleanName"
        val existing = customAlarmRepository.getAllSnapshot(profileId).firstOrNull {
            it.moduleKey == "medicine" &&
                it.title.equals(alarmTitle, ignoreCase = true) &&
                it.triggerTime24h == time24h
        }
        if (existing != null) return

        val noteDetail = buildString {
            append("Take $cleanName")
            if (dosage.isNotBlank()) append(" (${dosage.trim()})")
            if (mealRelation.isNotBlank()) append(" • $mealRelation")
            if (notes.isNotBlank()) append(" • Note: ${notes.trim()}")
            else append(" • Drink a glass of water with your dose!")
        }
        val entity = ReminderEntity(
            profileId = profileId,
            title = alarmTitle,
            moduleKey = "medicine",
            triggerTime24h = time24h,
            daysOfWeekMask = 127,
            isEnabled = true,
            soundKey = BundledAlertTone.WELLNESS_GENTLE.id,
            alarmEmoji = emoji.ifBlank { "💊" },
            customMessage = noteDetail,
            repeatIntervalMinutes = (intervalHours.coerceIn(1, 24)) * 60
        )
        val savedId = customAlarmRepository.saveAlarm(entity)
        alertSoundManager.scheduleCustomAlarm(entity.copy(id = savedId))
    }

    fun toggleMedicine(med: MedicineEntity, active: Boolean) =
        viewModelScope.launch {
            allModulesRepo.toggleMedicine(med, active)
            val alarmTitle = "Take ${med.name.trim()}"
            customAlarmRepository.getAllSnapshot(med.profileId)
                .filter { it.moduleKey == "medicine" && it.title.equals(alarmTitle, ignoreCase = true) }
                .forEach { alarm ->
                    val updated = customAlarmRepository.toggleAlarm(alarm, active)
                    if (active) {
                        alertSoundManager.scheduleCustomAlarm(updated)
                    } else {
                        alertSoundManager.cancelCustomAlarm(alarm.id)
                    }
                }
        }

    fun deleteMedicine(id: Long) = viewModelScope.launch {
        val target = uiState.value.medicines.firstOrNull { it.id == id }
        allModulesRepo.deleteMedicine(id)
        if (target != null) {
            val alarmTitle = "Take ${target.name.trim()}"
            customAlarmRepository.getAllSnapshot(target.profileId)
                .filter { it.moduleKey == "medicine" && it.title.equals(alarmTitle, ignoreCase = true) }
                .forEach { alarm ->
                    alertSoundManager.cancelCustomAlarm(alarm.id)
                    customAlarmRepository.deleteAlarm(alarm.id)
                }
        }
    }

    fun logMedicineIntake(
        med: MedicineEntity,
        slotLabel: String,
        status: String = "Taken",
        takenAtEpochMs: Long = System.currentTimeMillis()
    ) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            allModulesRepo.logMedicineIntake(pid, med, slotLabel, status, takenAtEpochMs)
        }
    }

    fun deleteMedicineIntake(id: Long) = viewModelScope.launch {
        allModulesRepo.deleteMedicineIntake(id)
    }

    fun addConsultation(
        doctorName: String,
        specialty: String,
        clinicOrHospital: String = "",
        summary: String,
        followUpEpochDay: Long? = null,
        followUpNotes: String = "",
        consultationEpochMs: Long = System.currentTimeMillis()
    ) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            allModulesRepo.addConsultation(
                profileId = pid,
                doctorName = doctorName,
                specialty = specialty,
                clinicOrHospital = clinicOrHospital,
                summary = summary,
                followUpEpochDay = followUpEpochDay,
                followUpNotes = followUpNotes,
                consultationEpochMs = consultationEpochMs
            )
        }
    }

    fun toggleConsultationFollowUp(consultation: DoctorConsultationEntity, completed: Boolean) =
        viewModelScope.launch { allModulesRepo.toggleConsultationFollowUp(consultation, completed) }

    fun deleteConsultation(id: Long) = viewModelScope.launch { allModulesRepo.deleteConsultation(id) }

    fun logBeautyCare(
        routineType: String,
        productOrStep: String,
        notes: String,
        completedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch { allModulesRepo.logBeautyCare(pid, routineType, productOrStep, notes, completedAtEpochMs) }
    }

    fun deleteBeautyCare(id: Long) = viewModelScope.launch { allModulesRepo.deleteBeautyCare(id) }

    fun updateWellbeing(addScreenMin: Int = 0, addFocus: Int = 0, newGoalMin: Int? = null) {
        val pid = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            allModulesRepo.updateDigitalWellbeing(
                profileId = pid,
                existing = uiState.value.todayWellbeing,
                addScreenMinutes = addScreenMin,
                addFocusSession = addFocus,
                goalMinutes = newGoalMin
            )
            evaluateAndNotifyOveruse()
        }
    }

    fun refreshNotificationPermissionState() {
        hasNotificationPerm.value = alertSoundManager.hasNotificationPermission()
    }

    fun snoozeWellbeingHomeAlert(minutes: Int = 30) {
        snoozedUntilMs.value = System.currentTimeMillis() + minutes * 60_000L
    }

    fun usageAccessSettingsIntent(includePackageUri: Boolean = true) =
        deviceWellbeingManager.createUsageAccessSettingsIntent(includePackageUri)

    fun refreshWellbeingFromDevice(
        includePastWeek: Boolean = false,
        userTriggered: Boolean = false
    ) {
        hasNotificationPerm.value = alertSoundManager.hasNotificationPermission()
        val permitted = deviceWellbeingManager.hasUsageAccessPermission()
        hasUsageAccess.value = permitted
        if (!permitted) {
            if (userTriggered) {
                wellbeingSyncBanner.value = "Enable Usage Access for LifeWeave in Android Settings to auto-fetch screen time."
            }
            return
        }
        viewModelScope.launch {
            val profile = userProfileRepository.observeActiveProfile().first() ?: return@launch
            isSyncingWellbeing.value = true
            try {
                val todayResult = withContext(Dispatchers.IO) {
                    deviceWellbeingManager.fetchDayUsageSnapshot(daysAgo = 0)
                }
                todayResult.onSuccess { snapshot ->
                    val existingToday = allModulesRepo.observeTodayWellbeing(profile.id).first()
                    allModulesRepo.syncWellbeingSnapshot(
                        profileId = profile.id,
                        existing = existingToday,
                        snapshot = snapshot
                    )
                    if (includePastWeek) {
                        withContext(Dispatchers.IO) {
                            for (daysAgo in 1..6) {
                                deviceWellbeingManager.fetchDayUsageSnapshot(daysAgo = daysAgo)
                                    .getOrNull()
                                    ?.takeIf { it.totalScreenMinutes > 0 }
                                    ?.let { daySnap ->
                                        allModulesRepo.syncWellbeingSnapshotForDay(
                                            profileId = profile.id,
                                            snapshot = daySnap,
                                            defaultGoalMinutes = existingToday?.mindfulGoalMinutes ?: 120
                                        )
                                    }
                            }
                        }
                    }
                    wellbeingSyncBanner.value = if (snapshot.totalScreenMinutes > 0) {
                        "${snapshot.syncDiagnostic} • ${snapshot.totalScreenMinutes}m & ${snapshot.unlockCount} unlocks."
                    } else {
                        snapshot.syncDiagnostic
                    }
                    evaluateAndNotifyOveruse()
                }.onFailure { err ->
                    if (userTriggered) {
                        wellbeingSyncBanner.value = err.message ?: "Unable to read Usage Stats."
                    }
                }
            } finally {
                isSyncingWellbeing.value = false
            }
        }
    }

    fun setWellbeingAlertSettings(enabled: Boolean, warningPercent: Int) {
        viewModelScope.launch {
            preferencesManager.updateWellbeingAlertSettings(enabled, warningPercent)
            if (enabled) {
                alertSoundManager.scheduleWellbeingMonitor()
                evaluateAndNotifyOveruse()
            } else {
                alertSoundManager.cancelWellbeingMonitor()
            }
        }
    }

    fun sendTestWellbeingOveruseAlert() {
        viewModelScope.launch {
            val prefs = preferencesManager.preferencesFlow.first()
            val record = uiState.value.todayWellbeing
            val alert = WellbeingMetricsCalculator.evaluateOveruse(
                record = record,
                warningPercent = prefs.wellbeingWarningPercent,
                topApps = uiState.value.topAppsUsage
            )
            val title = if (alert.severity == OveruseSeverity.BALANCED) {
                "⚠️ Screen Overuse Alert Preview"
            } else {
                "⚠️ ${alert.title}"
            }
            val body = if (alert.severity == OveruseSeverity.BALANCED) {
                "You'll get notified like this whenever daily screen time reaches ${prefs.wellbeingWarningPercent}% or exceeds your ${record?.mindfulGoalMinutes ?: 120}m cap."
            } else {
                "${alert.message} ${alert.recommendation}"
            }
            val posted = alertSoundManager.showWellbeingOveruseNotification(
                title = title,
                body = body,
                wellnessToneSetting = prefs.wellnessTone
            )
            wellbeingSyncBanner.value = if (posted) {
                "Sent overuse alert notification with your Daily Wellness chime."
            } else {
                "Notification permission is off — in-app overuse alert banner remains active."
            }
        }
    }

    private suspend fun evaluateAndNotifyOveruse() {
        val prefs = preferencesManager.preferencesFlow.first()
        if (!prefs.wellbeingOveruseAlertsEnabled) return
        val profile = userProfileRepository.observeActiveProfile().first() ?: return
        val record = allModulesRepo.observeTodayWellbeing(profile.id).first() ?: return
        val topApps = WellbeingMetricsCalculator.decodeTopApps(record.topAppsJson)
        val alert = WellbeingMetricsCalculator.evaluateOveruse(
            record = record,
            warningPercent = prefs.wellbeingWarningPercent,
            topApps = topApps
        )
        if (alert.severity == OveruseSeverity.BALANCED) return

        val todayKey = DateEpochUtils.todayDayKey()
        val alreadyNotifiedSameOrHigher = prefs.lastOveruseAlertDayKey == todayKey &&
            prefs.lastOveruseAlertStage == alert.stageKey
        if (!alreadyNotifiedSameOrHigher) {
            alertSoundManager.showWellbeingOveruseNotification(
                title = "📵 ${alert.title}",
                body = "${alert.message} ${alert.recommendation}",
                wellnessToneSetting = prefs.wellnessTone
            )
            preferencesManager.recordOveruseAlertDispatched(todayKey, alert.stageKey)
        }
    }

    fun updateAiTone(tone: String) {
        val pid = uiState.value.profile?.id ?: return
        val current = uiState.value.aiPreference ?: AiPreferenceEntity(profileId = pid)
        viewModelScope.launch {
            allModulesRepo.updateAiPreference(current.copy(preferredTone = tone, onDeviceAgentEnabled = true))
        }
    }

    // --- Automatic All-Modules Refresh & Sleep/Snoring Monitor Controls ---

    fun triggerAutoRefresh(
        force: Boolean = true,
        reason: String = "manual",
        includePastWeek: Boolean = false
    ) {
        hasUsageAccess.value = deviceWellbeingManager.hasUsageAccessPermission()
        hasNotificationPerm.value = alertSoundManager.hasNotificationPermission()
        hcAvailability.value = healthConnectManager.checkAvailability()
        viewModelScope.launch {
            grantedHealthPerms.value = healthConnectManager.getGrantedPermissions()
            val manager = appAutoRefreshManager
            if (manager != null) {
                manager.refreshAllModules(
                    reason = reason,
                    force = force,
                    includePastWeekWellbeing = includePastWeek
                )
            } else {
                refreshWellbeingFromDevice(includePastWeek = includePastWeek, userTriggered = force)
            }
        }
    }

    fun setAutoRefreshEnabled(enabled: Boolean) {
        val currentInterval = uiState.value.autoRefreshIntervalMinutes
        viewModelScope.launch {
            val manager = appAutoRefreshManager
            if (manager != null) {
                manager.updateAutoRefreshConfig(enabled, currentInterval)
            } else {
                preferencesManager.setAutoRefreshSettings(enabled, currentInterval)
            }
        }
    }

    fun setAutoRefreshIntervalMinutes(intervalMinutes: Int) {
        val currentEnabled = uiState.value.autoRefreshEnabled
        viewModelScope.launch {
            val manager = appAutoRefreshManager
            if (manager != null) {
                manager.updateAutoRefreshConfig(currentEnabled, intervalMinutes)
            } else {
                preferencesManager.setAutoRefreshSettings(currentEnabled, intervalMinutes)
            }
        }
    }

    fun startSleepAndSnoringMonitor(context: Context) {
        val snapshot = uiState.value
        val pid = snapshot.profile?.id ?: 0L
        SleepMonitoringService.startMonitoring(
            context = context,
            profileId = pid,
            wakeHour = snapshot.sleepWakeHour,
            wakeMinute = snapshot.sleepWakeMinute
        )
    }

    fun stopAndSaveSleepMonitor(context: Context) {
        SleepMonitoringService.stopAndSave(context)
    }

    fun dismissSleepMonitorBanner() {
        SleepMonitorStateStore.clearBanners()
    }

    fun updateSleepBedtimeSchedule(
        enabled: Boolean,
        bedtimeHour: Int,
        bedtimeMinute: Int,
        wakeHour: Int,
        wakeMinute: Int
    ) {
        viewModelScope.launch {
            preferencesManager.setSleepAutoSchedule(
                enabled = enabled,
                bedtimeHour = bedtimeHour,
                bedtimeMinute = bedtimeMinute,
                wakeHour = wakeHour,
                wakeMinute = wakeMinute
            )
            // Ensure background WorkManager schedule is active if bedtime prompt is enabled
            if (enabled) {
                appAutoRefreshManager?.syncWorkScheduleFromPreferences()
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        voiceManager.release()
    }
}
