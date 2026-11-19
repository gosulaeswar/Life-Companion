package com.example

import android.content.Context
import android.os.Looper
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.AppDatabase
import com.example.data.repository.ModuleRepository
import com.example.data.repository.UserProfileRepository
import com.example.security.LocalPreferencesManager
import com.example.security.OfflineLicenseVerifier
import com.example.security.SupportedLanguage
import com.example.ui.screens.OnboardingFlowScreen
import com.example.ui.theme.LifeCompanionTheme
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.AppLaunchDestination
import com.example.ui.viewmodel.AppSessionViewModel
import com.example.ui.viewmodel.OnboardingNotificationPrefs
import com.example.ui.viewmodel.ProfileSetupViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class Phase9OnboardingFlowTest {

    @get:Rule
    val composeRule = createComposeRule()

    private fun createInMemoryDb(context: Context): AppDatabase =
        Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()

    private fun pumpMainLooperUntil(
        timeoutMs: Long = 5_000L,
        condition: () -> Boolean
    ) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Shadows.shadowOf(Looper.getMainLooper()).idle()
            Thread.sleep(25)
        }
        Shadows.shadowOf(Looper.getMainLooper()).idle()
    }

    @Test
    fun profileSetupViewModel_validatesInputsAndComputesBmiAndWaterGoal() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createInMemoryDb(context)
        try {
            val prefs = LocalPreferencesManager(context)
            val vm = ProfileSetupViewModel(
                userProfileRepository = UserProfileRepository(db, prefs),
                moduleRepository = ModuleRepository(db, OfflineLicenseVerifier(), prefs),
                preferencesManager = prefs
            )

            // Blank name should fail validation
            assertFalse(vm.validateProfileInputs())
            assertNotNull(vm.formState.value.errorMessage)

            // Invalid age should fail validation
            vm.updateName("Ananya")
            vm.updateAge("5")
            assertFalse(vm.validateProfileInputs())
            assertTrue(vm.formState.value.errorMessage.orEmpty().contains("10 and 120"))

            // Custom gender without label should fail validation
            vm.updateAge("29")
            vm.updateGenderIdentity("Custom")
            assertFalse(vm.validateProfileInputs())
            assertTrue(vm.formState.value.errorMessage.orEmpty().contains("custom gender"))

            // Valid inputs + BMI preview calculation
            vm.updateCustomGender("Agender")
            vm.updateHeightCm("170")
            vm.updateWeightKg("65")
            vm.updateActivityLevel("Active")
            assertTrue(vm.validateProfileInputs())

            val bmi = vm.formState.value.computeBmiPreview()
            assertNotNull(bmi)
            assertEquals("Healthy range", bmi!!.categoryLabel)
            assertTrue(bmi.bmiValue in 22.0f..23.0f)
            assertTrue(vm.formState.value.dailyWaterGoalMl >= 2500)
        } finally {
            db.close()
        }
    }

    @Test
    fun completeOnboardingWithPreferences_persistsProfileThemeAndRemindersAtomically() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createInMemoryDb(context)
        try {
            val prefs = LocalPreferencesManager(context)
            runBlocking(Dispatchers.IO) { prefs.resetAll() }
            val userRepo = UserProfileRepository(db, prefs)
            val moduleRepo = ModuleRepository(db, OfflineLicenseVerifier(), prefs)
            val vm = ProfileSetupViewModel(
                userProfileRepository = userRepo,
                moduleRepository = moduleRepo,
                preferencesManager = prefs,
                database = db
            )

            vm.updateName("Meera")
            vm.updateAge("27")
            vm.updateGenderIdentity("Female")
            vm.updateHeightCm("164")
            vm.updateWeightKg("58")
            vm.updateActivityLevel("Moderate")
            vm.updatePreferredLanguageCode(SupportedLanguage.HINDI.code)

            var callbackInvoked = false
            vm.completeOnboardingWithPreferences(
                themeMode = ThemeMode.DARK,
                notificationPrefs = OnboardingNotificationPrefs(
                    waterRemindersEnabled = true,
                    waterReminderIntervalMinutes = 120,
                    wellnessRemindersEnabled = false,
                    cycleRemindersEnabled = true,
                    wellbeingOveruseAlertsEnabled = true,
                    sleepBedtimePromptEnabled = true,
                    autoRefreshEnabled = true,
                    autoRefreshIntervalMinutes = 30
                ),
                onCompleted = { callbackInvoked = true }
            )

            pumpMainLooperUntil { callbackInvoked }

            assertTrue("Expected onboarding completion callback", callbackInvoked)
            val savedProfile = runBlocking(Dispatchers.IO) {
                withTimeout(5_000) { userRepo.observeActiveProfile().first() }
            }
            assertNotNull(savedProfile)
            assertEquals("Meera", savedProfile!!.fullName)
            assertEquals(27, savedProfile.age)
            assertEquals(164, savedProfile.heightCm)
            assertEquals(58f, savedProfile.weightKg)
            assertEquals("hi", savedProfile.preferredLanguageCode)

            val savedPrefs = runBlocking(Dispatchers.IO) {
                withTimeout(5_000) { prefs.preferencesFlow.first() }
            }
            assertEquals(ThemeMode.DARK, savedPrefs.themeMode)
            assertTrue(savedPrefs.welcomeCompleted)
            assertTrue(savedPrefs.authGateCompleted)
            assertTrue(savedPrefs.permissionsSetupCompleted)
            assertTrue(savedPrefs.waterTone.reminderEnabled)
            assertFalse(savedPrefs.wellnessTone.reminderEnabled)
            assertTrue(savedPrefs.periodTone.reminderEnabled)
            assertTrue(savedPrefs.wellbeingOveruseAlertsEnabled)
            assertTrue(savedPrefs.sleepAutoBedtimePromptEnabled)
            assertTrue(savedPrefs.autoRefreshEnabled)
            assertEquals(30, savedPrefs.autoRefreshIntervalMinutes)
        } finally {
            db.close()
        }
    }

    @Test
    fun appSessionViewModel_routesNewUserToOnboardingAndCompletedUserToDashboard() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createInMemoryDb(context)
        try {
            val prefs = LocalPreferencesManager(context)
            runBlocking(Dispatchers.IO) { prefs.resetAll() }
            val userRepo = UserProfileRepository(db, prefs)
            val moduleRepo = ModuleRepository(db, OfflineLicenseVerifier(), prefs)

            val sessionVm = AppSessionViewModel(
                userProfileRepository = userRepo,
                moduleRepository = moduleRepo,
                preferencesManager = prefs
            )

            // Collect stateIn via Compose so SharingStarted.WhileSubscribed is active and MainLooper pumps cleanly
            var observedDestination: AppLaunchDestination = AppLaunchDestination.Loading
            composeRule.setContent {
                val state by sessionVm.uiState.collectAsState()
                observedDestination = state.launchDestination
            }

            pumpMainLooperUntil(timeoutMs = 8_000L) {
                composeRule.waitForIdle()
                observedDestination == AppLaunchDestination.Onboarding
            }
            assertEquals(AppLaunchDestination.Onboarding, observedDestination)

            // Complete onboarding via ProfileSetupViewModel -> should transition to Dashboard
            val profileVm = ProfileSetupViewModel(userRepo, moduleRepo, prefs)
            profileVm.updateName("Rohan")
            profileVm.updateAge("31")
            profileVm.updateGenderIdentity("Male")
            profileVm.completeOnboardingWithPreferences(
                themeMode = ThemeMode.LIGHT,
                notificationPrefs = OnboardingNotificationPrefs()
            )

            pumpMainLooperUntil(timeoutMs = 8_000L) {
                composeRule.waitForIdle()
                observedDestination == AppLaunchDestination.Dashboard
            }
            assertEquals(AppLaunchDestination.Dashboard, observedDestination)
        } finally {
            db.close()
        }
    }

    @Test
    fun onboardingFlowScreen_endToEndFiveStepJourney_lightAndDarkThemes() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = createInMemoryDb(context)
        try {
            val prefs = LocalPreferencesManager(context)
            runBlocking(Dispatchers.IO) { prefs.resetAll() }
            val userRepo = UserProfileRepository(db, prefs)
            val moduleRepo = ModuleRepository(db, OfflineLicenseVerifier(), prefs)
            val profileVm = ProfileSetupViewModel(userRepo, moduleRepo, prefs)

            var completed = false

            composeRule.setContent {
                var selectedLanguage by remember { mutableStateOf(SupportedLanguage.ENGLISH) }
                var selectedTheme by remember { mutableStateOf(ThemeMode.LIGHT) }
                val formState by profileVm.formState.collectAsState()

                LifeCompanionTheme(themeMode = selectedTheme) {
                    OnboardingFlowScreen(
                        selectedLanguage = selectedLanguage,
                        selectedThemeMode = selectedTheme,
                        signedInUser = null,
                        formState = formState,
                        profileViewModel = profileVm,
                        onSelectLanguage = { selectedLanguage = it },
                        onSelectThemeMode = { selectedTheme = it },
                        onSignInWithGoogle = { Result.failure(IllegalStateException("Test offline")) },
                        onSignInWithGitHub = { Result.failure(IllegalStateException("Test offline")) },
                        onSignInWithMicrosoft = { Result.failure(IllegalStateException("Test offline")) },
                        onOnboardingCompleted = { completed = true }
                    )
                }
            }

            // Step 1: Welcome & Language
            composeRule.onNodeWithTag("onboarding_step_welcome").assertIsDisplayed()
            composeRule.onNodeWithText("Step 1 of 5").assertIsDisplayed()
            composeRule.onNodeWithTag("onboarding_welcome_next_button")
                .performScrollTo()
                .performClick()

            // Step 2: Core Features Tour
            composeRule.onNodeWithTag("onboarding_step_features").assertIsDisplayed()
            composeRule.onNodeWithText("Step 2 of 5").assertIsDisplayed()
            composeRule.onNodeWithTag("onboarding_features_skip_button")
                .performScrollTo()
                .performClick()

            // Step 3: Account / Continue Offline
            composeRule.onNodeWithText("Step 3 of 5").assertIsDisplayed()
            composeRule.onNodeWithTag("prehome_offline_continue_button")
                .performScrollTo()
                .performClick()

            // Step 4: Profile & Basic Health Info
            composeRule.onNodeWithTag("onboarding_step_profile").assertIsDisplayed()
            composeRule.onNodeWithText("Step 4 of 5").assertIsDisplayed()
            composeRule.onNodeWithTag("onboarding_name_input")
                .performScrollTo()
                .performTextInput("Kavya")
            composeRule.onNodeWithTag("onboarding_age_input")
                .performScrollTo()
                .performTextClearance()
            composeRule.onNodeWithTag("onboarding_age_input")
                .performTextInput("28")
            composeRule.onNodeWithTag("onboarding_height_input")
                .performScrollTo()
                .performTextInput("165")
            composeRule.onNodeWithTag("onboarding_weight_input")
                .performScrollTo()
                .performTextInput("60")
            composeRule.onNodeWithTag("onboarding_bmi_preview")
                .performScrollTo()
                .assertIsDisplayed()
            composeRule.onNodeWithTag("onboarding_profile_next_button")
                .performScrollTo()
                .performClick()

            // Step 5: Theme (switch live to Dark) & Notification Preferences
            composeRule.onNodeWithTag("onboarding_step_preferences").assertIsDisplayed()
            composeRule.onNodeWithText("Step 5 of 5").assertIsDisplayed()
            composeRule.onNodeWithTag("onboarding_theme_dark")
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithTag("onboarding_pref_sleep_switch")
                .performScrollTo()
                .performClick()
            composeRule.onNodeWithTag("onboarding_finish_button")
                .performScrollTo()
                .performClick()

            pumpMainLooperUntil(timeoutMs = 8_000L) { completed }
            assertTrue("Expected onboarding journey to complete cleanly", completed)
        } finally {
            db.close()
        }
    }
}
