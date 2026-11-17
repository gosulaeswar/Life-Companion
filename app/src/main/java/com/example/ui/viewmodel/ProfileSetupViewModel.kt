package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.ModuleRepository
import com.example.data.repository.UserProfileRepository
import com.example.domain.HydrationCalculator
import com.example.security.LocalPreferencesManager
import com.example.security.SupportedLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileFormState(
    val isEditing: Boolean = false,
    val fullName: String = "",
    val ageText: String = "26",
    val genderIdentity: String = "Female",
    val customGenderLabel: String = "",
    val heightCmText: String = "",
    val weightKgText: String = "",
    val activityLevel: String = "Moderate",
    val dailyWaterGoalMl: Int = 2500,
    val customGoalOverridden: Boolean = false,
    val cycleTrackingEnabled: Boolean = true,
    val averageCycleLengthDays: Int = 28,
    val averagePeriodDurationDays: Int = 5,
    val preferredLanguageCode: String = SupportedLanguage.ENGLISH.code,
    val errorMessage: String? = null,
    val isSaving: Boolean = false
)

class ProfileSetupViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val moduleRepository: ModuleRepository,
    private val preferencesManager: LocalPreferencesManager
) : ViewModel() {

    private val _formState = MutableStateFlow(ProfileFormState())
    val formState: StateFlow<ProfileFormState> = _formState.asStateFlow()

    fun loadExistingProfile(profile: UserProfileEntity?) {
        if (profile == null) return
        _formState.update {
            it.copy(
                isEditing = true,
                fullName = profile.fullName,
                ageText = profile.age.toString(),
                genderIdentity = profile.genderIdentity,
                customGenderLabel = profile.customGenderLabel,
                heightCmText = profile.heightCm?.toString().orEmpty(),
                weightKgText = profile.weightKg?.let { w -> w.toInt().toString() }.orEmpty(),
                activityLevel = profile.activityLevel,
                dailyWaterGoalMl = profile.dailyWaterGoalMl,
                customGoalOverridden = true,
                cycleTrackingEnabled = profile.cycleTrackingEnabled,
                averageCycleLengthDays = profile.averageCycleLengthDays,
                averagePeriodDurationDays = profile.averagePeriodDurationDays,
                preferredLanguageCode = profile.preferredLanguageCode,
                errorMessage = null
            )
        }
    }

    fun updateName(value: String) {
        _formState.update { it.copy(fullName = value, errorMessage = null) }
    }

    fun updateAge(value: String) {
        if (value.all { ch -> ch.isDigit() } && value.length <= 3) {
            _formState.update { it.copy(ageText = value, errorMessage = null) }
        }
    }

    fun updateGenderIdentity(gender: String) {
        val defaultCycleEnabled = when (gender) {
            "Male" -> false
            "Female" -> true
            else -> _formState.value.cycleTrackingEnabled
        }
        _formState.update {
            it.copy(
                genderIdentity = gender,
                cycleTrackingEnabled = if (it.isEditing) it.cycleTrackingEnabled else defaultCycleEnabled,
                errorMessage = null
            )
        }
    }

    fun updateCustomGender(value: String) {
        _formState.update { it.copy(customGenderLabel = value, errorMessage = null) }
    }

    fun updateHeightCm(value: String) {
        if (value.all { ch -> ch.isDigit() } && value.length <= 3) {
            _formState.update { it.copy(heightCmText = value) }
        }
    }

    fun updateWeightKg(value: String) {
        val filtered = value.filter { it.isDigit() || it == '.' }
        if (filtered.length <= 5) {
            _formState.update { state ->
                val weight = filtered.toFloatOrNull()
                val goal = if (state.customGoalOverridden) {
                    state.dailyWaterGoalMl
                } else {
                    HydrationCalculator.suggestedDailyGoalMl(weight, state.activityLevel)
                }
                state.copy(weightKgText = filtered, dailyWaterGoalMl = goal)
            }
        }
    }

    fun updateActivityLevel(level: String) {
        _formState.update { state ->
            val weight = state.weightKgText.toFloatOrNull()
            val goal = if (state.customGoalOverridden) {
                state.dailyWaterGoalMl
            } else {
                HydrationCalculator.suggestedDailyGoalMl(weight, level)
            }
            state.copy(activityLevel = level, dailyWaterGoalMl = goal)
        }
    }

    fun updateDailyWaterGoal(goalMl: Int) {
        _formState.update {
            it.copy(
                dailyWaterGoalMl = goalMl.coerceIn(1000, 5000),
                customGoalOverridden = true
            )
        }
    }

    fun updateCycleTrackingEnabled(enabled: Boolean) {
        _formState.update { it.copy(cycleTrackingEnabled = enabled) }
    }

    fun updateAverageCycleLength(days: Int) {
        _formState.update { it.copy(averageCycleLengthDays = days.coerceIn(21, 45)) }
    }

    fun updateAveragePeriodDuration(days: Int) {
        _formState.update { it.copy(averagePeriodDurationDays = days.coerceIn(2, 10)) }
    }

    fun saveProfile(onSaved: () -> Unit) {
        val state = _formState.value
        val trimmedName = state.fullName.trim()
        if (trimmedName.isEmpty()) {
            _formState.update { it.copy(errorMessage = "Please enter your name or preferred nickname.") }
            return
        }
        val age = state.ageText.toIntOrNull()
        if (age == null || age !in 10..120) {
            _formState.update { it.copy(errorMessage = "Please enter a valid age between 10 and 120.") }
            return
        }
        if (state.genderIdentity == "Custom" && state.customGenderLabel.trim().isEmpty()) {
            _formState.update { it.copy(errorMessage = "Please enter your custom gender label or pick another option.") }
            return
        }

        _formState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val entity = UserProfileEntity(
                fullName = trimmedName,
                age = age,
                genderIdentity = state.genderIdentity,
                customGenderLabel = if (state.genderIdentity == "Custom") state.customGenderLabel.trim() else "",
                heightCm = state.heightCmText.toIntOrNull()?.takeIf { it in 80..250 },
                weightKg = state.weightKgText.toFloatOrNull()?.takeIf { it in 25f..300f },
                activityLevel = state.activityLevel,
                dailyWaterGoalMl = state.dailyWaterGoalMl,
                cycleTrackingEnabled = state.cycleTrackingEnabled,
                averageCycleLengthDays = state.averageCycleLengthDays,
                averagePeriodDurationDays = state.averagePeriodDurationDays,
                preferredLanguageCode = state.preferredLanguageCode
            )
            userProfileRepository.saveOrUpdateProfile(entity)
            moduleRepository.ensureSeeded()
            preferencesManager.setWelcomeCompleted(true)
            _formState.update { it.copy(isSaving = false) }
            onSaved()
        }
    }
}
