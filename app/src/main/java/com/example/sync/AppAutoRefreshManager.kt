package com.example.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.example.billing.SubscriptionRepository
import com.example.data.local.AppDatabase
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.RoutineMoodRepository
import com.example.domain.DateEpochUtils
import com.example.healthsync.FitnessSyncRepository
import com.example.healthsync.HealthConnectAvailability
import com.example.healthsync.HealthConnectManager
import com.example.notifications.AlertSoundManager
import com.example.security.LocalPreferencesManager
import com.example.wellbeing.DeviceWellbeingManager
import com.example.wellbeing.OveruseSeverity
import com.example.wellbeing.WellbeingMetricsCalculator
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

data class AppAutoRefreshState(
    val isRefreshing: Boolean = false,
    val lastRefreshedEpochMs: Long = 0L,
    val lastSummary: String = "",
    val wellbeingSynced: Boolean = false,
    val fitnessSyncedCount: Int = 0,
    val lastReason: String = "",
    val errorMessage: String? = null
)

data class AutoRefreshReport(
    val completedAtEpochMs: Long,
    val wellbeingUpdated: Boolean,
    val wellbeingScreenMinutes: Int?,
    val fitnessRecordsUpdated: Int,
    val routinesChecked: Boolean,
    val entitlementChecked: Boolean,
    val summary: String,
    val skippedDueToThrottle: Boolean = false
)

/**
 * Coordinates automatic, non-blocking refresh across ALL LifeWeave modules:
 * 1. Digital Wellbeing (Android UsageStatsManager live screen time, unlocks, top apps, overuse alert)
 * 2. Connected Fitness Devices & Wearables (Android Health Connect: steps, workouts, sleep, HR, water, diet, weight)
 * 3. Daily Routines, Medicines & Badges freshness
 * 4. Subscription Entitlement expiry validation
 *
 * Uses WorkManager for periodic background refreshes (Play-policy compliant, minimum 15m interval)
 * and throttled foreground refreshes on app resume / active session ticks.
 */
class AppAutoRefreshManager(
    private val appContext: Context,
    private val database: AppDatabase,
    private val preferencesManager: LocalPreferencesManager,
    private val deviceWellbeingManager: DeviceWellbeingManager,
    private val healthConnectManager: HealthConnectManager,
    private val fitnessSyncRepository: FitnessSyncRepository,
    private val allModulesRepository: AllModulesRepository,
    private val alertSoundManager: AlertSoundManager,
    private val routineMoodRepository: RoutineMoodRepository? = null,
    private val subscriptionRepositoryProvider: (() -> SubscriptionRepository?)? = null
) {
    companion object {
        const val UNIQUE_PERIODIC_WORK_NAME = "lifeweave_auto_refresh_all_modules"
        private const val MIN_FOREGROUND_THROTTLE_MS = 2 * 60_000L

        fun schedulePeriodicWork(
            context: Context,
            enabled: Boolean,
            intervalMinutes: Int
        ) {
            runCatching {
                val wm = WorkManager.getInstance(context.applicationContext)
                if (!enabled) {
                    wm.cancelUniqueWork(UNIQUE_PERIODIC_WORK_NAME)
                    return@runCatching
                }
                val safeMinutes = intervalMinutes.coerceIn(15, 240).toLong()
                // Do not require network so offline Digital Wellbeing & local modules still auto-refresh
                val constraints = Constraints.Builder()
                    .setRequiresBatteryNotLow(true)
                    .build()
                val request = PeriodicWorkRequestBuilder<ModuleAutoRefreshWorker>(
                    safeMinutes,
                    TimeUnit.MINUTES
                )
                    .setConstraints(constraints)
                    .build()
                wm.enqueueUniquePeriodicWork(
                    UNIQUE_PERIODIC_WORK_NAME,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    request
                )
            }
        }

        suspend fun syncPeriodicWorkFromPreferences(context: Context) {
            val prefs = LocalPreferencesManager(context.applicationContext).preferencesFlow.first()
            schedulePeriodicWork(
                context = context,
                enabled = prefs.autoRefreshEnabled,
                intervalMinutes = prefs.autoRefreshIntervalMinutes
            )
        }
    }

    private val mutex = Mutex()
    private val _state = MutableStateFlow(AppAutoRefreshState())
    val state: StateFlow<AppAutoRefreshState> = _state.asStateFlow()

    suspend fun syncWorkScheduleFromPreferences() {
        val prefs = preferencesManager.preferencesFlow.first()
        _state.update { current ->
            if (current.lastRefreshedEpochMs == 0L && prefs.lastAutoRefreshEpochMs > 0L) {
                current.copy(
                    lastRefreshedEpochMs = prefs.lastAutoRefreshEpochMs,
                    lastSummary = prefs.lastAutoRefreshSummary
                )
            } else {
                current
            }
        }
        schedulePeriodicWork(
            context = appContext,
            enabled = prefs.autoRefreshEnabled,
            intervalMinutes = prefs.autoRefreshIntervalMinutes
        )
    }

    suspend fun updateAutoRefreshConfig(enabled: Boolean, intervalMinutes: Int) {
        val clamped = intervalMinutes.coerceIn(15, 240)
        preferencesManager.setAutoRefreshSettings(enabled, clamped)
        schedulePeriodicWork(appContext, enabled, clamped)
    }

    suspend fun refreshAllModules(
        reason: String = "manual",
        force: Boolean = false,
        includePastWeekWellbeing: Boolean = false,
        nowMs: Long = System.currentTimeMillis()
    ): AutoRefreshReport {
        val prefs = preferencesManager.preferencesFlow.first()
        if (!force) {
            if (!prefs.autoRefreshEnabled) {
                return AutoRefreshReport(
                    completedAtEpochMs = prefs.lastAutoRefreshEpochMs,
                    wellbeingUpdated = false,
                    wellbeingScreenMinutes = null,
                    fitnessRecordsUpdated = 0,
                    routinesChecked = false,
                    entitlementChecked = false,
                    summary = prefs.lastAutoRefreshSummary.ifBlank { "Auto-refresh paused" },
                    skippedDueToThrottle = true
                )
            }
            val lastDone = maxOf(_state.value.lastRefreshedEpochMs, prefs.lastAutoRefreshEpochMs)
            if (lastDone > 0L && (nowMs - lastDone) < MIN_FOREGROUND_THROTTLE_MS) {
                return AutoRefreshReport(
                    completedAtEpochMs = lastDone,
                    wellbeingUpdated = _state.value.wellbeingSynced,
                    wellbeingScreenMinutes = null,
                    fitnessRecordsUpdated = _state.value.fitnessSyncedCount,
                    routinesChecked = false,
                    entitlementChecked = false,
                    summary = _state.value.lastSummary.ifBlank { prefs.lastAutoRefreshSummary },
                    skippedDueToThrottle = true
                )
            }
        }

        if (!mutex.tryLock()) {
            return AutoRefreshReport(
                completedAtEpochMs = _state.value.lastRefreshedEpochMs,
                wellbeingUpdated = false,
                wellbeingScreenMinutes = null,
                fitnessRecordsUpdated = 0,
                routinesChecked = false,
                entitlementChecked = false,
                summary = "Refresh already in progress…",
                skippedDueToThrottle = true
            )
        }

        _state.update { it.copy(isRefreshing = true, lastReason = reason, errorMessage = null) }

        return try {
            withContext(Dispatchers.IO) {
                val activeProfile = resolveActiveProfile(prefs.activeProfileId)
                val summaryParts = mutableListOf<String>()
                var wellbeingUpdated = false
                var screenMinutes: Int? = null
                var fitnessUpdated = 0
                var routinesChecked = false
                var entitlementChecked = false

                // 1. Digital Wellbeing live device sync
                if (activeProfile != null && deviceWellbeingManager.hasUsageAccessPermission()) {
                    val wbRes = deviceWellbeingManager.fetchDayUsageSnapshot(daysAgo = 0, nowMs = nowMs)
                    wbRes.onSuccess { snapshot ->
                        val existingToday = allModulesRepository.observeTodayWellbeing(activeProfile.id).first()
                        allModulesRepository.syncWellbeingSnapshot(
                            profileId = activeProfile.id,
                            existing = existingToday,
                            snapshot = snapshot
                        )
                        if (includePastWeekWellbeing) {
                            for (daysAgo in 1..6) {
                                deviceWellbeingManager.fetchDayUsageSnapshot(daysAgo = daysAgo, nowMs = nowMs)
                                    .getOrNull()
                                    ?.takeIf { it.totalScreenMinutes > 0 }
                                    ?.let { histSnap ->
                                        allModulesRepository.syncWellbeingSnapshotForDay(
                                            profileId = activeProfile.id,
                                            snapshot = histSnap,
                                            defaultGoalMinutes = existingToday?.mindfulGoalMinutes ?: 120
                                        )
                                    }
                            }
                        }
                        wellbeingUpdated = true
                        screenMinutes = snapshot.totalScreenMinutes
                        summaryParts += "Wellbeing (${snapshot.totalScreenMinutes}m)"

                        // Evaluate overuse threshold and notify if needed
                        if (prefs.wellbeingOveruseAlertsEnabled) {
                            val updatedRecord = allModulesRepository.observeTodayWellbeing(activeProfile.id).first()
                            val alert = WellbeingMetricsCalculator.evaluateOveruse(
                                record = updatedRecord,
                                warningPercent = prefs.wellbeingWarningPercent,
                                topApps = snapshot.topApps
                            )
                            val todayKey = DateEpochUtils.todayDayKey(nowMs)
                            val alreadyDispatched = prefs.lastOveruseAlertDayKey == todayKey &&
                                prefs.lastOveruseAlertStage == alert.stageKey
                            if (alert.severity != OveruseSeverity.BALANCED && !alreadyDispatched) {
                                alertSoundManager.showWellbeingOveruseNotification(
                                    title = "📵 ${alert.title}",
                                    body = "${alert.message} ${alert.recommendation}",
                                    wellnessToneSetting = prefs.wellnessTone
                                )
                                preferencesManager.recordOveruseAlertDispatched(todayKey, alert.stageKey)
                            }
                        }
                    }
                } else {
                    summaryParts += "Wellbeing (grant Usage Access)"
                }

                // 2. Connected Fitness Devices / Health Connect live sync
                if (activeProfile != null && healthConnectManager.checkAvailability() == HealthConnectAvailability.AVAILABLE) {
                    val enabledCategories = prefs.healthConnectImportAuth.enabledCategories()
                    if (enabledCategories.isNotEmpty()) {
                        val granted = healthConnectManager.getGrantedPermissions()
                        val allowedPerms = HealthConnectManager.permissionsForCategories(enabledCategories)
                        if (granted.intersect(allowedPerms).isNotEmpty()) {
                            healthConnectManager.readRecentMetrics(
                                daysBack = 7,
                                enabledCategories = enabledCategories
                            ).onSuccess { payload ->
                                fitnessUpdated = fitnessSyncRepository.ingestSyncPayload(activeProfile.id, payload)
                                summaryParts += if (fitnessUpdated > 0) {
                                    "Fitness Devices (+$fitnessUpdated)"
                                } else {
                                    "Fitness Devices (up to date)"
                                }
                            }
                        }
                    }
                }

                // 3. Routines, Medicines & Badges check
                if (activeProfile != null && routineMoodRepository != null) {
                    runCatching {
                        routineMoodRepository.ensureDefaultCatalogSeeded(activeProfile.id)
                        routinesChecked = true
                    }
                }

                // 4. Subscription Entitlement expiry validation
                runCatching {
                    subscriptionRepositoryProvider?.invoke()?.let { subRepo ->
                        subRepo.checkAndEnforceExpiry()
                        entitlementChecked = true
                    }
                }

                if (summaryParts.isEmpty()) {
                    summaryParts += "All local modules synced"
                } else {
                    summaryParts += "Sleep & Care active"
                }

                val summaryText = summaryParts.joinToString(" • ")
                preferencesManager.recordAutoRefreshCompleted(nowMs, summaryText)

                _state.update {
                    it.copy(
                        isRefreshing = false,
                        lastRefreshedEpochMs = nowMs,
                        lastSummary = summaryText,
                        wellbeingSynced = wellbeingUpdated,
                        fitnessSyncedCount = fitnessUpdated,
                        lastReason = reason,
                        errorMessage = null
                    )
                }

                AutoRefreshReport(
                    completedAtEpochMs = nowMs,
                    wellbeingUpdated = wellbeingUpdated,
                    wellbeingScreenMinutes = screenMinutes,
                    fitnessRecordsUpdated = fitnessUpdated,
                    routinesChecked = routinesChecked,
                    entitlementChecked = entitlementChecked,
                    summary = summaryText
                )
            }
        } catch (e: Exception) {
            val errMsg = e.localizedMessage ?: "Auto-refresh encountered an error."
            _state.update {
                it.copy(
                    isRefreshing = false,
                    errorMessage = errMsg
                )
            }
            AutoRefreshReport(
                completedAtEpochMs = nowMs,
                wellbeingUpdated = false,
                wellbeingScreenMinutes = null,
                fitnessRecordsUpdated = 0,
                routinesChecked = false,
                entitlementChecked = false,
                summary = errMsg
            )
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun resolveActiveProfile(preferredId: Long) =
        if (preferredId > 0L) {
            database.userProfileDao().getProfileById(preferredId)
                ?: database.userProfileDao().getActiveProfileSnapshot()
        } else {
            database.userProfileDao().getActiveProfileSnapshot()
        }
}
