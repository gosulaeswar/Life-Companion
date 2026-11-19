package com.example.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.BuildConfig
import com.example.ai.LifeWeaveAiService
import com.example.ai.VoiceAssistantManager
import com.example.billing.GooglePlayBillingGateway
import com.example.billing.PlayBillingGateway
import com.example.billing.ServerBillingVerificationClient
import com.example.billing.SubscriptionRepository
import com.example.data.local.AppDatabase
import com.example.data.repository.AllModulesRepository
import com.example.data.repository.CustomAlarmRepository
import com.example.data.repository.HealthVaultRepository
import com.example.data.repository.ModuleRepository
import com.example.data.repository.PeriodRepository
import com.example.data.repository.PrivacyBackupRepository
import com.example.data.repository.RoutineMoodRepository
import com.example.data.repository.UserProfileRepository
import com.example.data.repository.WaterRepository
import com.example.family.FamilyCircleRepository
import com.example.family.GoogleAuthManager
import com.example.R
import com.example.healthsync.AppleAndFileFitnessImporter
import com.example.healthsync.FitnessSyncRepository
import com.example.healthsync.HealthConnectManager
import com.example.notifications.AlertSoundManager
import com.example.security.EncryptedDocumentVault
import com.example.security.EntitlementPolicy
import com.example.security.KeystoreVaultManager
import com.example.security.LocalDefaultEntitlementPolicy
import com.example.security.LocalPreferencesManager
import com.example.security.OfflineLicenseVerifier
import com.example.security.PersonalAiKeyStore
import com.example.security.SubscriptionTier
import com.example.sync.AppAutoRefreshManager
import com.example.wellbeing.DeviceWellbeingManager
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val database: AppDatabase = AppDatabase.getInstance(appContext)
    val preferencesManager = LocalPreferencesManager(appContext)
    val vaultManager = KeystoreVaultManager()
    val encryptedDocumentVault = EncryptedDocumentVault(appContext, vaultManager)
    val licenseVerifier = OfflineLicenseVerifier()
    val entitlementPolicy: EntitlementPolicy = LocalDefaultEntitlementPolicy(
        activeTierProvider = { subscriptionRepository.currentCachedTier() }
    )
    val alertSoundManager = AlertSoundManager(appContext)

    val healthConnectManager = HealthConnectManager(appContext)
    val appleAndFileFitnessImporter = AppleAndFileFitnessImporter()
    val fitnessSyncRepository = FitnessSyncRepository(database)

    val personalAiKeyStore = PersonalAiKeyStore(appContext, vaultManager)
    val lifeWeaveAiService = LifeWeaveAiService(appContext, personalAiKeyStore)
    val voiceAssistantManager = VoiceAssistantManager(appContext)
    val deviceWellbeingManager = DeviceWellbeingManager(appContext)

    val userProfileRepository = UserProfileRepository(database, preferencesManager)
    val waterRepository = WaterRepository(database)
    val periodRepository = PeriodRepository(database)
    val customAlarmRepository = CustomAlarmRepository(database)
    val allModulesRepository = AllModulesRepository(database)
    val healthVaultRepository = HealthVaultRepository(database, encryptedDocumentVault)
    val routineMoodRepository = RoutineMoodRepository(
        database = database,
        customAlarmRepository = customAlarmRepository,
        alertSoundManager = alertSoundManager
    )
    val moduleRepository = ModuleRepository(database, licenseVerifier, preferencesManager)
    val privacyBackupRepository = PrivacyBackupRepository(
        database = database,
        preferencesManager = preferencesManager,
        vaultManager = vaultManager,
        encryptedDocumentVault = encryptedDocumentVault
    )

    val googleAuthManager: GoogleAuthManager by lazy {
        GoogleAuthManager(
            appContext = appContext,
            firebaseAuth = FirebaseAuth.getInstance()
        )
    }

    val familyCircleRepository: FamilyCircleRepository by lazy {
        val dbId = appContext.getString(R.string.firestore_database_id)
        FamilyCircleRepository(
            firestore = FirebaseFirestore.getInstance(dbId),
            firebaseAuth = FirebaseAuth.getInstance()
        )
    }

    val serverBillingVerificationClient = ServerBillingVerificationClient()
    val playBillingGateway: PlayBillingGateway by lazy {
        GooglePlayBillingGateway(appContext)
    }
    val subscriptionRepository: SubscriptionRepository by lazy {
        SubscriptionRepository(
            appContext = appContext,
            database = database,
            preferencesManager = preferencesManager,
            playBillingGateway = playBillingGateway,
            verificationClient = serverBillingVerificationClient,
            firebaseAuthProvider = { runCatching { FirebaseAuth.getInstance() }.getOrNull() },
            familyCircleRepositoryProvider = { runCatching { familyCircleRepository }.getOrNull() },
            allowLocalDebugLoopbackUrls = BuildConfig.DEBUG,
            allowRuntimeEndpointOverrides = BuildConfig.DEBUG
        )
    }

    val appAutoRefreshManager: AppAutoRefreshManager by lazy {
        AppAutoRefreshManager(
            appContext = appContext,
            database = database,
            preferencesManager = preferencesManager,
            deviceWellbeingManager = deviceWellbeingManager,
            healthConnectManager = healthConnectManager,
            fitnessSyncRepository = fitnessSyncRepository,
            allModulesRepository = allModulesRepository,
            alertSoundManager = alertSoundManager,
            routineMoodRepository = routineMoodRepository,
            subscriptionRepositoryProvider = { runCatching { subscriptionRepository }.getOrNull() }
        )
    }
}

class SimpleViewModelFactory<T : ViewModel>(
    private val creator: () -> T
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <VM : ViewModel> create(modelClass: Class<VM>): VM = creator() as VM
}
