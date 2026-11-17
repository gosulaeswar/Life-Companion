package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.PeriodRecordEntity
import com.example.data.repository.PeriodRepository
import com.example.data.repository.UserProfileRepository
import com.example.domain.CycleCalculator
import com.example.domain.CycleInsights
import com.example.domain.CyclePhase
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class PeriodUiState(
    val profileId: Long? = null,
    val cycleTrackingEnabled: Boolean = true,
    val records: List<PeriodRecordEntity> = emptyList(),
    val insights: CycleInsights = CycleInsights(
        averageCycleLengthDays = 28,
        averagePeriodLengthDays = 5,
        currentCycleDay = null,
        currentPhase = CyclePhase.UNLOGGED,
        nextPredictedStartEpochDay = null,
        daysUntilNextPeriod = null,
        fertileWindowStartEpochDay = null,
        fertileWindowEndEpochDay = null
    ),
    val displayYear: Int = Calendar.getInstance().get(Calendar.YEAR),
    val displayMonthZeroBased: Int = Calendar.getInstance().get(Calendar.MONTH)
)

@OptIn(ExperimentalCoroutinesApi::class)
class PeriodViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val periodRepository: PeriodRepository
) : ViewModel() {

    private val currentCal = Calendar.getInstance()
    private val displayedMonth = MutableStateFlow(
        currentCal.get(Calendar.YEAR) to currentCal.get(Calendar.MONTH)
    )

    val uiState: StateFlow<PeriodUiState> = userProfileRepository.observeActiveProfile()
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(PeriodUiState())
            } else {
                combine(
                    periodRepository.observeRecords(profile.id),
                    displayedMonth
                ) { records, (year, month) ->
                    val insights = CycleCalculator.analyze(
                        recordsDesc = records,
                        configuredCycleLength = profile.averageCycleLengthDays,
                        configuredPeriodLength = profile.averagePeriodDurationDays
                    )
                    PeriodUiState(
                        profileId = profile.id,
                        cycleTrackingEnabled = profile.cycleTrackingEnabled,
                        records = records,
                        insights = insights,
                        displayYear = year,
                        displayMonthZeroBased = month
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = PeriodUiState()
        )

    fun shiftMonth(delta: Int) {
        val (year, month) = displayedMonth.value
        val cal = Calendar.getInstance().apply {
            clear()
            set(year, month, 1)
            add(Calendar.MONTH, delta)
        }
        displayedMonth.value = cal.get(Calendar.YEAR) to cal.get(Calendar.MONTH)
    }

    fun savePeriodRecord(
        existingId: Long = 0L,
        startEpochDay: Long,
        endEpochDay: Long?,
        flowIntensity: String,
        symptoms: Set<String>,
        mood: String,
        notes: String
    ) {
        val profileId = uiState.value.profileId ?: return
        viewModelScope.launch {
            periodRepository.saveRecord(
                PeriodRecordEntity(
                    id = existingId,
                    profileId = profileId,
                    startDateEpochDay = startEpochDay,
                    endDateEpochDay = endEpochDay?.takeIf { it >= startEpochDay },
                    flowIntensity = flowIntensity,
                    symptomsCsv = symptoms.joinToString(","),
                    mood = mood,
                    notes = notes.trim()
                )
            )
        }
    }

    fun deleteRecord(recordId: Long) {
        viewModelScope.launch {
            periodRepository.deleteRecord(recordId)
        }
    }

    fun setCycleTrackingEnabled(enabled: Boolean) {
        viewModelScope.launch {
            userProfileRepository.setCycleTrackingEnabled(enabled)
        }
    }
}
