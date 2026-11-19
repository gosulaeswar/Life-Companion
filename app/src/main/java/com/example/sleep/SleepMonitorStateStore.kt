package com.example.sleep

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class SleepMonitorLiveState(
    val isMonitoring: Boolean = false,
    val profileId: Long = 0L,
    val startedAtEpochMs: Long = 0L,
    val elapsedMinutes: Int = 0,
    val currentDb: Double = 30.0,
    val ambientFloorDb: Double = 32.0,
    val snoreEventsCount: Int = 0,
    val snoreDurationMinutes: Int = 0,
    val peakSnoreDb: Double = 0.0,
    val restlessnessIndex: Int = 0,
    val estimatedStage: EstimatedSleepStage = EstimatedSleepStage.LIGHT,
    val liveQualityScore: Int = 88,
    val lastEventLabel: String = "Ready for overnight monitoring",
    val recentDbWaveform: List<Float> = emptyList(),
    val lastSavedBanner: String? = null,
    val errorMessage: String? = null
)

object SleepMonitorStateStore {
    private val _state = MutableStateFlow(SleepMonitorLiveState())
    val state: StateFlow<SleepMonitorLiveState> = _state.asStateFlow()

    fun update(transform: (SleepMonitorLiveState) -> SleepMonitorLiveState) {
        _state.update(transform)
    }

    fun clearBanners() {
        _state.update { it.copy(lastSavedBanner = null, errorMessage = null) }
    }

    fun reset() {
        _state.value = SleepMonitorLiveState()
    }
}
