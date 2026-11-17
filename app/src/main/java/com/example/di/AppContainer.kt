package com.example.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.ai.LifeWeaveAiService
import com.example.ai.VoiceAssistantManager
import com.example.data.local.AppDatabase
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.CustomAlarmRepository
import com.example.data.repository.ModuleRepository
import com.example.data.repository.PeriodRepository
import com.example.data.repository.PrivacyBackupRepository
import com.example.data.repository.UserProfileRepository
import com.example.data.repository.WaterRepository
import com.example.healthsync.AppleAndFileFitnessImporter
import com.example.healthsync.FitnessSyncRepository
import com.example.healthsync.HealthConnectManager
import com.example.notifications.AlertSoundManager
import com.example.security.KeystoreVaultManager
import com.example.security.LocalPreferencesManager
import com.example.security.OfflineLicenseVerifier

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val preferencesManager = LocalPreferencesManager(appContext)
    val vaultManager = KeystoreVaultManager()
    val licenseVerifier = OfflineLicenseVerifier()
    val alertSoundManager = AlertSoundManager(appContext)

    val healthConnectManager = HealthConnectManager(appContext)
    val appleAndFileFitnessImporter = AppleAndFileFitnessImporter()
    val fitnessSyncRepository = FitnessSyncRepository(database)

    val lifeWeaveAiService = LifeWeaveAiService(appContext)
    val voiceAssistantManager = VoiceAssistantManager(appContext)

    val userProfileRepository = UserProfileRepository(database)
    val waterRepository = WaterRepository(database)
    val periodRepository = PeriodRepository(database)
    val customAlarmRepository = CustomAlarmRepository(database)
    val allModulesRepository = AllModulesRepository(database)
    val moduleRepository = ModuleRepository(database, licenseVerifier)
    val privacyBackupRepository = PrivacyBackupRepository(
        database = database,
        preferencesManager = preferencesManager,
        vaultManager = vaultManager
    )
}

class SimpleViewModelFactory<T : ViewModel>(
    private val creator: () -> T
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}
