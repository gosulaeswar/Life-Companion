package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.WaterRecordEntity
import com.example.data.repository.UserProfileRepository
import com.example.data.repository.WaterRepository
import com.example.domain.HydrationCalculator
import com.example.domain.HydrationSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class WaterUiState(
    val profileId: Long? = null,
    val summary: HydrationSummary = HydrationSummary(0, 2500, 0f, 2500, 0, emptyList()),
    val todayRecords: List<WaterRecordEntity> = emptyList(),
    val selectedDrinkType: String = "Water"
)

@OptIn(ExperimentalCoroutinesApi::class)
class WaterViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val waterRepository: WaterRepository
) : ViewModel() {

    private val selectedDrinkType = MutableStateFlow("Water")

    val uiState: StateFlow<WaterUiState> = userProfileRepository.observeActiveProfile()
        .flatMapLatest { profile ->
            if (profile == null) {
                flowOf(WaterUiState())
            } else {
                combine(
                    waterRepository.observeTodayRecords(profile.id),
                    waterRepository.observeLast7Days(profile.id),
                    selectedDrinkType
                ) { todayList, weekList, drinkType ->
                    WaterUiState(
                        profileId = profile.id,
                        summary = HydrationCalculator.summarize(weekList, profile.dailyWaterGoalMl),
                        todayRecords = todayList,
                        selectedDrinkType = drinkType
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = WaterUiState()
        )

    fun selectDrinkType(type: String) {
        selectedDrinkType.value = type
    }

    fun logWater(
        amountMl: Int,
        note: String = "",
        loggedAtEpochMs: Long = System.currentTimeMillis()
    ) {
        val profileId = uiState.value.profileId ?: return
        val drink = selectedDrinkType.value
        viewModelScope.launch {
            waterRepository.logWater(
                profileId = profileId,
                amountMl = amountMl,
                drinkType = drink,
                note = note,
                loggedAtEpochMs = loggedAtEpochMs
            )
        }
    }

    fun deleteRecord(recordId: Long) {
        viewModelScope.launch {
            waterRepository.deleteRecord(recordId)
        }
    }

    fun updateDailyGoal(goalMl: Int) {
        viewModelScope.launch {
            userProfileRepository.updateWaterGoal(goalMl)
        }
    }
}
