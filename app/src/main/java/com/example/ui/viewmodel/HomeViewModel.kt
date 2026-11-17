package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.ModuleActivationEntity
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.ModuleRepository
import com.example.data.repository.PeriodRepository
import com.example.data.repository.UserProfileRepository
import com.example.data.repository.WaterRepository
import com.example.domain.CycleCalculator
import com.example.domain.CycleInsights
import com.example.domain.HydrationCalculator
import com.example.domain.HydrationSummary
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Calendar

data class HomeUiState(
    val isLoading: Boolean = true,
    val greeting: String = "Welcome back",
    val profile: UserProfileEntity? = null,
    val hydration: HydrationSummary = HydrationSummary(0, 2500, 0f, 2500, 0, emptyList()),
    val cycleInsights: CycleInsights? = null,
    val modules: List<ModuleActivationEntity> = emptyList()
)

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val waterRepository: WaterRepository,
    private val periodRepository: PeriodRepository,
    moduleRepository: ModuleRepository
) : ViewModel() {

    private val profileFlow = userProfileRepository.observeActiveProfile()

    val uiState: StateFlow<HomeUiState> = profileFlow.flatMapLatest { profile ->
        if (profile == null) {
            flowOf(HomeUiState(isLoading = false))
        } else {
            combine(
                waterRepository.observeLast7Days(profile.id),
                periodRepository.observeRecords(profile.id),
                moduleRepository.observeModules()
            ) { waterRecords, periodRecords, modules ->
                val hydration = HydrationCalculator.summarize(
                    recordsLast7Days = waterRecords,
                    goalMl = profile.dailyWaterGoalMl
                )
                val cycle = CycleCalculator.analyze(
                    recordsDesc = periodRecords,
                    configuredCycleLength = profile.averageCycleLengthDays,
                    configuredPeriodLength = profile.averagePeriodDurationDays
                )
                HomeUiState(
                    isLoading = false,
                    greeting = currentTimeGreeting(),
                    profile = profile,
                    hydration = hydration,
                    cycleInsights = cycle,
                    modules = modules
                )
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState()
    )

    fun quickLogWater(amountMl: Int) {
        val profileId = uiState.value.profile?.id ?: return
        viewModelScope.launch {
            waterRepository.logWater(profileId = profileId, amountMl = amountMl, drinkType = "Water")
        }
    }

    fun enableCycleTracking() {
        viewModelScope.launch {
            userProfileRepository.setCycleTrackingEnabled(true)
        }
    }

    private fun currentTimeGreeting(): String {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..11 -> "Good morning"
            in 12..16 -> "Good afternoon"
            in 17..21 -> "Good evening"
            else -> "Peaceful night"
        }
    }
}
