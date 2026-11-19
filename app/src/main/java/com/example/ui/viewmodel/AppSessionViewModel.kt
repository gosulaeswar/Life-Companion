package com.example.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.entity.UserProfileEntity
import com.example.data.repository.ModuleRepository
import com.example.data.repository.UserProfileRepository
import com.example.security.LocalPreferencesManager
import com.example.security.SupportedLanguage
import com.example.security.UiPreferencesState
import com.example.ui.theme.ThemeMode
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface AppLaunchDestination {
    data object Loading : AppLaunchDestination
    data object Onboarding : AppLaunchDestination
    data object Welcome : AppLaunchDestination
    data object SignIn : AppLaunchDestination
    data object PermissionsSetup : AppLaunchDestination
    data object ProfileSetup : AppLaunchDestination
    data object Dashboard : AppLaunchDestination
}

data class AppSessionUiState(
    val launchDestination: AppLaunchDestination = AppLaunchDestination.Loading,
    val activeProfile: UserProfileEntity? = null,
    val preferences: UiPreferencesState = UiPreferencesState()
)

class AppSessionViewModel(
    private val userProfileRepository: UserProfileRepository,
    private val moduleRepository: ModuleRepository,
    private val preferencesManager: LocalPreferencesManager
) : ViewModel() {

    init {
        viewModelScope.launch {
            moduleRepository.ensureSeeded()
        }
    }

    val uiState: StateFlow<AppSessionUiState> = combine(
        userProfileRepository.observeActiveProfile(),
        preferencesManager.preferencesFlow
    ) { profile, prefs ->
        val destination = when {
            profile == null -> AppLaunchDestination.Onboarding
            !prefs.authGateCompleted -> AppLaunchDestination.SignIn
            else -> AppLaunchDestination.Dashboard
        }
        AppSessionUiState(
            launchDestination = destination,
            activeProfile = profile,
            preferences = prefs
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = AppSessionUiState()
    )

    fun completeWelcome() {
        viewModelScope.launch {
            preferencesManager.setWelcomeCompleted(true)
        }
    }

    fun reopenWelcome() {
        viewModelScope.launch {
            preferencesManager.setWelcomeCompleted(false)
        }
    }

    fun completeAuthGate() {
        viewModelScope.launch {
            preferencesManager.setAuthGateCompleted(true)
        }
    }

    fun reopenAuthGate() {
        viewModelScope.launch {
            preferencesManager.setAuthGateCompleted(false)
        }
    }

    fun completePermissionsSetup() {
        viewModelScope.launch {
            preferencesManager.setPermissionsSetupCompleted(true)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            preferencesManager.setThemeMode(mode)
        }
    }

    fun setLanguage(language: SupportedLanguage) {
        viewModelScope.launch {
            preferencesManager.setLanguage(language)
        }
    }
}
