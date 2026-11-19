package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.R
import com.example.di.AppContainer
import com.example.di.SimpleViewModelFactory
import com.example.notifications.AlertCategory
import com.example.security.ProvideLocalizedLocale
import com.example.ui.screens.BeautyCareScreen
import com.example.ui.screens.ConnectedDevicesScreen
import com.example.ui.screens.DietNutritionScreen
import com.example.ui.screens.DigitalWellbeingScreen
import com.example.ui.screens.FamilyCircleRoute
import com.example.ui.screens.FitnessYogaScreen
import com.example.ui.screens.HealthVaultScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MedicalCareScreen
import com.example.ui.screens.ModuleSubRoutes
import com.example.ui.screens.ModulesHubScreen
import com.example.ui.screens.OnDeviceAiScreen
import com.example.ui.screens.OnboardingFlowScreen
import com.example.ui.screens.PeriodTrackerScreen
import com.example.ui.screens.PermissionsPopupDialog
import com.example.ui.screens.PreHomeAuthScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.ReportsAnalyticsScreen
import com.example.ui.screens.RoutineMoodScreen
import com.example.ui.screens.SettingsPrivacyScreen
import com.example.ui.screens.SubscriptionScreen
import com.example.ui.screens.WaterTrackerScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.LifeCompanionTheme
import com.example.ui.viewmodel.AppLaunchDestination
import com.example.ui.viewmodel.AppSessionViewModel
import com.example.ui.viewmodel.HealthVaultViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.ModulesHubViewModel
import com.example.ui.viewmodel.PeriodViewModel
import com.example.ui.viewmodel.ProfileSetupViewModel
import com.example.ui.viewmodel.RoutineMoodViewModel
import com.example.ui.viewmodel.SettingsPrivacyViewModel
import com.example.ui.viewmodel.SubscriptionViewModel
import com.example.ui.viewmodel.WaterViewModel

object LifeWeaveRoutes {
    const val ROUTE_HOME = "home"
    const val ROUTE_WATER = "water"
    const val ROUTE_PERIOD = "period"
    const val ROUTE_MODULES = "modules"
    const val ROUTE_SETTINGS = "settings"
    const val ROUTE_PROFILE_EDIT = "profile_edit"
}

private data class NavTabItem(
    val dest: LifeWeaveDest,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

@Composable
fun LifeCompanionApp(container: AppContainer) {
    val sessionViewModel: AppSessionViewModel = viewModel(
        factory = SimpleViewModelFactory {
            AppSessionViewModel(
                userProfileRepository = container.userProfileRepository,
                moduleRepository = container.moduleRepository,
                preferencesManager = container.preferencesManager
            )
        }
    )
    val sessionState by sessionViewModel.uiState.collectAsStateWithLifecycle()

    ProvideLocalizedLocale(language = sessionState.preferences.language) {
        LifeCompanionTheme(themeMode = sessionState.preferences.themeMode) {
            AnimatedContent(
                targetState = sessionState.launchDestination,
                transitionSpec = {
                    (fadeIn(animationSpec = tween(340)) + scaleIn(initialScale = 0.985f, animationSpec = tween(340))) togetherWith
                        fadeOut(animationSpec = tween(220))
                },
                label = "app_launch_transition"
            ) { destination ->
                when (destination) {
                    AppLaunchDestination.Loading -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(MaterialTheme.colorScheme.background),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    AppLaunchDestination.Onboarding -> {
                        val profileViewModel: ProfileSetupViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                ProfileSetupViewModel(
                                    userProfileRepository = container.userProfileRepository,
                                    moduleRepository = container.moduleRepository,
                                    preferencesManager = container.preferencesManager,
                                    alertSoundManager = container.alertSoundManager,
                                    database = container.database
                                )
                            }
                        )
                        val formState by profileViewModel.formState.collectAsStateWithLifecycle()
                        val signedInUser = runCatching { container.googleAuthManager.currentUserSnapshot() }.getOrNull()
                        OnboardingFlowScreen(
                            selectedLanguage = sessionState.preferences.language,
                            selectedThemeMode = sessionState.preferences.themeMode,
                            signedInUser = signedInUser,
                            formState = formState,
                            profileViewModel = profileViewModel,
                            alertSoundManager = container.alertSoundManager,
                            deviceWellbeingManager = container.deviceWellbeingManager,
                            onSelectLanguage = sessionViewModel::setLanguage,
                            onSelectThemeMode = sessionViewModel::setThemeMode,
                            onSignInWithGoogle = { activityCtx ->
                                runCatching { container.googleAuthManager.signInWithGoogleInteractive(activityCtx) }
                                    .getOrElse { Result.failure(IllegalStateException("Firebase Auth is not initialized in this build. You can continue privately offline.")) }
                            },
                            onSignInWithGitHub = { activity ->
                                runCatching { container.googleAuthManager.signInWithGitHub(activity) }
                                    .getOrElse { Result.failure(IllegalStateException("Firebase Auth is not initialized in this build. You can continue privately offline.")) }
                            },
                            onSignInWithMicrosoft = { activity ->
                                runCatching { container.googleAuthManager.signInWithMicrosoft(activity) }
                                    .getOrElse { Result.failure(IllegalStateException("Firebase Auth is not initialized in this build. You can continue privately offline.")) }
                            },
                            onFetchSignedInDisplayName = {
                                runCatching { container.googleAuthManager.currentUserSnapshot()?.displayName }.getOrNull()
                            }
                        )
                    }

                    AppLaunchDestination.Welcome -> {
                        WelcomeScreen(
                            selectedLanguage = sessionState.preferences.language,
                            onSelectLanguage = sessionViewModel::setLanguage,
                            onGetStarted = sessionViewModel::completeWelcome
                        )
                    }

                    AppLaunchDestination.SignIn -> {
                        val signedInUser = runCatching { container.googleAuthManager.currentUserSnapshot() }.getOrNull()
                        PreHomeAuthScreen(
                            signedInUser = signedInUser,
                            canGoBackToWelcome = sessionState.activeProfile == null,
                            onBackToWelcome = sessionViewModel::reopenWelcome,
                            onSignInWithGoogle = { activityCtx ->
                                runCatching { container.googleAuthManager.signInWithGoogleInteractive(activityCtx) }
                                    .getOrElse { Result.failure(IllegalStateException("Firebase Auth is not initialized in this build. You can continue privately offline.")) }
                            },
                            onSignInWithGitHub = { activity ->
                                runCatching { container.googleAuthManager.signInWithGitHub(activity) }
                                    .getOrElse { Result.failure(IllegalStateException("Firebase Auth is not initialized in this build. You can continue privately offline.")) }
                            },
                            onSignInWithMicrosoft = { activity ->
                                runCatching { container.googleAuthManager.signInWithMicrosoft(activity) }
                                    .getOrElse { Result.failure(IllegalStateException("Firebase Auth is not initialized in this build. You can continue privately offline.")) }
                            },
                            onContinueToApp = sessionViewModel::completeAuthGate
                        )
                    }

                    AppLaunchDestination.PermissionsSetup,
                    AppLaunchDestination.ProfileSetup -> {
                        val profileViewModel: ProfileSetupViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                ProfileSetupViewModel(
                                    userProfileRepository = container.userProfileRepository,
                                    moduleRepository = container.moduleRepository,
                                    preferencesManager = container.preferencesManager,
                                    alertSoundManager = container.alertSoundManager,
                                    database = container.database
                                )
                            }
                        )
                        LaunchedEffect(Unit) {
                            val signedInName = runCatching {
                                container.googleAuthManager.currentUserSnapshot()?.displayName
                            }.getOrNull()
                            profileViewModel.prefillNameIfBlank(signedInName)
                        }
                        val formState by profileViewModel.formState.collectAsStateWithLifecycle()
                        ProfileSetupScreen(
                            formState = formState,
                            viewModel = profileViewModel,
                            canNavigateBack = false,
                            onBack = {},
                            onSaved = {}
                        )
                        if (!sessionState.preferences.permissionsSetupCompleted) {
                            PermissionsPopupDialog(
                                alertSoundManager = container.alertSoundManager,
                                deviceWellbeingManager = container.deviceWellbeingManager,
                                onDismissComplete = sessionViewModel::completePermissionsSetup
                            )
                        }
                    }

                    AppLaunchDestination.Dashboard -> {
                        MainShell(
                            container = container,
                            cycleTabVisible = sessionState.activeProfile?.cycleTrackingEnabled ?: true,
                            waterToneLabel = container.alertSoundManager.describeCurrentTone(
                                AlertCategory.WATER,
                                sessionState.preferences.waterTone
                            ),
                            periodToneLabel = container.alertSoundManager.describeCurrentTone(
                                AlertCategory.PERIOD,
                                sessionState.preferences.periodTone
                            )
                        )
                        if (!sessionState.preferences.permissionsSetupCompleted) {
                            PermissionsPopupDialog(
                                alertSoundManager = container.alertSoundManager,
                                deviceWellbeingManager = container.deviceWellbeingManager,
                                onDismissComplete = sessionViewModel::completePermissionsSetup
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MainShell(
    container: AppContainer,
    cycleTabVisible: Boolean,
    waterToneLabel: String,
    periodToneLabel: String
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val modulesHubViewModel: ModulesHubViewModel = viewModel(
        factory = SimpleViewModelFactory {
            ModulesHubViewModel(
                userProfileRepository = container.userProfileRepository,
                waterRepository = container.waterRepository,
                periodRepository = container.periodRepository,
                moduleRepository = container.moduleRepository,
                allModulesRepo = container.allModulesRepository,
                fitnessSyncRepo = container.fitnessSyncRepository,
                healthConnectManager = container.healthConnectManager,
                appleImporter = container.appleAndFileFitnessImporter,
                aiService = container.lifeWeaveAiService,
                voiceManager = container.voiceAssistantManager,
                deviceWellbeingManager = container.deviceWellbeingManager,
                alertSoundManager = container.alertSoundManager,
                preferencesManager = container.preferencesManager,
                customAlarmRepository = container.customAlarmRepository,
                healthVaultRepository = container.healthVaultRepository,
                routineMoodRepository = container.routineMoodRepository,
                appAutoRefreshManager = container.appAutoRefreshManager,
                personalAiKeyStore = container.personalAiKeyStore
            )
        }
    )
    val modulesHubState by modulesHubViewModel.uiState.collectAsStateWithLifecycle()

    val healthVaultViewModel: HealthVaultViewModel = viewModel(
        factory = SimpleViewModelFactory {
            HealthVaultViewModel(
                userProfileRepository = container.userProfileRepository,
                healthVaultRepository = container.healthVaultRepository,
                allModulesRepository = container.allModulesRepository,
                encryptedDocumentVault = container.encryptedDocumentVault,
                aiService = container.lifeWeaveAiService,
                alertSoundManager = container.alertSoundManager,
                preferencesManager = container.preferencesManager
            )
        }
    )
    val healthVaultState by healthVaultViewModel.uiState.collectAsStateWithLifecycle()

    val effectiveEntitlement by container.subscriptionRepository.observeEffectiveEntitlement()
        .collectAsStateWithLifecycle(initialValue = com.example.billing.EffectiveEntitlement())

    val shellContext = LocalContext.current
    var askedNotifOnce by rememberSaveable { mutableStateOf(false) }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        modulesHubViewModel.refreshNotificationPermissionState()
        if (granted) {
            container.alertSoundManager.scheduleWellbeingMonitor()
        }
    }

    LaunchedEffect(Unit) {
        if (!askedNotifOnce && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            askedNotifOnce = true
            val granted = ContextCompat.checkSelfPermission(
                shellContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) {
                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                modulesHubViewModel.triggerAutoRefresh(
                    force = false,
                    reason = "app_resume",
                    includePastWeek = false
                )
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    // Active-session periodic foreground auto-refresh while the app remains open
    LaunchedEffect(
        modulesHubState.autoRefreshEnabled,
        modulesHubState.autoRefreshIntervalMinutes
    ) {
        if (!modulesHubState.autoRefreshEnabled) return@LaunchedEffect
        val foregroundIntervalMs = (modulesHubState.autoRefreshIntervalMinutes.coerceIn(15, 240) * 60_000L)
        while (true) {
            delay(foregroundIntervalMs)
            modulesHubViewModel.triggerAutoRefresh(
                force = false,
                reason = "foreground_timer",
                includePastWeek = false
            )
        }
    }

    val tabs = buildList {
        add(
            NavTabItem(
                dest = LifeWeaveDest.Home,
                labelRes = R.string.nav_home,
                selectedIcon = Icons.Rounded.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_tab_home"
            )
        )
        add(
            NavTabItem(
                dest = LifeWeaveDest.Water,
                labelRes = R.string.nav_water,
                selectedIcon = Icons.Rounded.WaterDrop,
                unselectedIcon = Icons.Outlined.WaterDrop,
                testTag = "nav_tab_water"
            )
        )
        if (cycleTabVisible) {
            add(
                NavTabItem(
                    dest = LifeWeaveDest.Period,
                    labelRes = R.string.nav_period,
                    selectedIcon = Icons.Rounded.Favorite,
                    unselectedIcon = Icons.Outlined.FavoriteBorder,
                    testTag = "nav_tab_period"
                )
            )
        }
        add(
            NavTabItem(
                dest = LifeWeaveDest.Modules,
                labelRes = R.string.nav_modules,
                selectedIcon = Icons.Rounded.GridView,
                unselectedIcon = Icons.Outlined.GridView,
                testTag = "nav_tab_modules"
            )
        )
        add(
            NavTabItem(
                dest = LifeWeaveDest.Settings,
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Rounded.Shield,
                unselectedIcon = Icons.Outlined.Shield,
                testTag = "nav_tab_settings"
            )
        )
    }

    fun navigateToTab(dest: LifeWeaveDest) {
        navController.navigate(dest) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateByModuleKey(moduleKey: String) {
        when (moduleKey) {
            "water_tracker" -> navigateToTab(LifeWeaveDest.Water)
            "period_tracker" -> navigateToTab(LifeWeaveDest.Period)
            "daily_reminders" -> navigateToTab(LifeWeaveDest.Settings)
            "exercise_tracker", "yoga_tracker" -> navController.navigate(LifeWeaveDest.FitnessYoga)
            "diet_tracker" -> navController.navigate(LifeWeaveDest.Diet)
            "medical_care" -> navController.navigate(LifeWeaveDest.Medical)
            "health_vault" -> navController.navigate(LifeWeaveDest.HealthVault)
            "beauty_care" -> navController.navigate(LifeWeaveDest.Beauty)
            "digital_wellbeing" -> navController.navigate(LifeWeaveDest.Wellbeing)
            "reports_analytics" -> navController.navigate(LifeWeaveDest.Reports)
            "on_device_ai" -> navController.navigate(LifeWeaveDest.OnDeviceAi)
            "connected_devices" -> navController.navigate(LifeWeaveDest.ConnectedDevices)
            "family_circle" -> navController.navigate(LifeWeaveDest.FamilyCircle)
            "subscription_plans" -> navController.navigate(LifeWeaveDest.Subscription)
            "daily_routines" -> navController.navigate(LifeWeaveDest.RoutinesMood(0))
            "mood_journal" -> navController.navigate(LifeWeaveDest.RoutinesMood(1))
            "wellness_challenges" -> navController.navigate(LifeWeaveDest.RoutinesMood(2))
            else -> navigateToTab(LifeWeaveDest.Modules)
        }
    }

    LaunchedEffect(modulesHubState.pendingNavigationModuleKey) {
        val target = modulesHubState.pendingNavigationModuleKey
        if (!target.isNullOrBlank()) {
            modulesHubViewModel.consumePendingNavigation()
            navigateByModuleKey(target)
        }
    }

    val isTopLevelRoute = tabs.any { tab ->
        currentDestination?.hierarchy?.any { it.hasRoute(tab.dest::class) } == true
    }

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        val useNavigationRail = maxWidth >= 600.dp

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.safeDrawing,
            bottomBar = {
                if (!useNavigationRail && isTopLevelRoute) {
                    androidx.compose.foundation.layout.Column {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 4.dp
                        ) {
                            tabs.forEach { tab ->
                                val selected = currentDestination?.hierarchy?.any {
                                    it.hasRoute(tab.dest::class)
                                } == true
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateToTab(tab.dest) },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(id = tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(id = tab.labelRes)) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary,
                                        indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                                        unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                        unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavigationRail && isTopLevelRoute) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier.statusBarsPadding()
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.hasRoute(tab.dest::class)
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.dest) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(id = tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                colors = NavigationRailItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    indicatorColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = LifeWeaveDest.Home,
                    modifier = Modifier.weight(1f)
                ) {
                    composable<LifeWeaveDest.Home> {
                        val homeViewModel: HomeViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                HomeViewModel(
                                    userProfileRepository = container.userProfileRepository,
                                    waterRepository = container.waterRepository,
                                    periodRepository = container.periodRepository,
                                    moduleRepository = container.moduleRepository
                                )
                            }
                        )
                        val homeState by homeViewModel.uiState.collectAsStateWithLifecycle()
                        HomeDashboardScreen(
                            uiState = homeState,
                            modulesState = modulesHubState,
                            onQuickAddWater = homeViewModel::quickLogWater,
                            onOpenWaterTracker = { navigateToTab(LifeWeaveDest.Water) },
                            onOpenPeriodTracker = { navigateToTab(LifeWeaveDest.Period) },
                            onEnableCycleTracking = {
                                homeViewModel.enableCycleTracking()
                                navigateToTab(LifeWeaveDest.Period)
                            },
                            onOpenProfileEdit = {
                                navController.navigate(LifeWeaveDest.ProfileEdit)
                            },
                            onOpenSettingsPrivacy = { navigateToTab(LifeWeaveDest.Settings) },
                            onOpenModulesHub = { navigateToTab(LifeWeaveDest.Modules) },
                            onOpenModuleKey = { key -> navigateByModuleKey(key) },
                            onStartVoiceCapture = modulesHubViewModel::startVoiceCapture,
                            onStopVoiceCapture = modulesHubViewModel::stopVoiceCapture,
                            onStopVoicePlayback = modulesHubViewModel::stopVoicePlayback,
                            onClearVoiceError = modulesHubViewModel::clearVoiceError,
                            onQuickAiPrompt = modulesHubViewModel::askAiAssistant,
                            onRetryAiPrompt = modulesHubViewModel::retryLastAiPrompt,
                            onRunAiDiagnostics = modulesHubViewModel::runAiDiagnostics,
                            onSnoozeWellbeingAlert = { modulesHubViewModel.snoozeWellbeingHomeAlert(30) },
                            onStartFocusBreak = { modulesHubViewModel.updateWellbeing(addFocus = 1) },
                            onOpenUsageAccessSettings = {
                                val opened = runCatching {
                                    shellContext.startActivity(modulesHubViewModel.usageAccessSettingsIntent(true))
                                    true
                                }.getOrDefault(false)
                                if (!opened) {
                                    runCatching {
                                        shellContext.startActivity(modulesHubViewModel.usageAccessSettingsIntent(false))
                                    }
                                }
                            },
                            onRequestNotificationPermission = {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                            },
                            onSelectIntelligenceTimeframe = modulesHubViewModel::selectIntelligenceTimeframe,
                            onSynthesizeIntelligence = modulesHubViewModel::generateAiHealthIntelligence,
                            onSpeakIntelligence = modulesHubViewModel::speakIntelligenceSummary,
                            onOpenFamilyCircle = {
                                navController.navigate(LifeWeaveDest.FamilyCircle)
                            },
                            onQuickMoodTap = modulesHubViewModel::logQuickHomeMood,
                            currentPlanLabel = when (effectiveEntitlement.tier) {
                                com.example.security.SubscriptionTier.FREE -> "Free"
                                com.example.security.SubscriptionTier.PLUS -> "Plus"
                                com.example.security.SubscriptionTier.FAMILY -> "Family"
                                com.example.security.SubscriptionTier.LIFETIME -> "Lifetime"
                            },
                            onOpenSubscription = {
                                navController.navigate(LifeWeaveDest.Subscription)
                            },
                            onToggleAutoRefresh = modulesHubViewModel::setAutoRefreshEnabled,
                            onSelectAutoRefreshInterval = modulesHubViewModel::setAutoRefreshIntervalMinutes,
                            onRefreshAllNow = {
                                modulesHubViewModel.triggerAutoRefresh(
                                    force = true,
                                    reason = "home_manual",
                                    includePastWeek = true
                                )
                            }
                        )
                    }

                    composable<LifeWeaveDest.Water> {
                        val waterViewModel: WaterViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                WaterViewModel(
                                    userProfileRepository = container.userProfileRepository,
                                    waterRepository = container.waterRepository
                                )
                            }
                        )
                        val waterState by waterViewModel.uiState.collectAsStateWithLifecycle()
                        WaterTrackerScreen(
                            uiState = waterState,
                            activeToneLabel = waterToneLabel,
                            viewModel = waterViewModel,
                            onOpenAlertSettings = { navigateToTab(LifeWeaveDest.Settings) }
                        )
                    }

                    composable<LifeWeaveDest.Period> {
                        val periodViewModel: PeriodViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                PeriodViewModel(
                                    userProfileRepository = container.userProfileRepository,
                                    periodRepository = container.periodRepository
                                )
                            }
                        )
                        val periodState by periodViewModel.uiState.collectAsStateWithLifecycle()
                        PeriodTrackerScreen(
                            uiState = periodState,
                            activeToneLabel = periodToneLabel,
                            viewModel = periodViewModel,
                            onOpenAlertSettings = { navigateToTab(LifeWeaveDest.Settings) }
                        )
                    }

                    composable<LifeWeaveDest.Modules> {
                        ModulesHubScreen(
                            uiState = modulesHubState,
                            onOpenWater = { navigateToTab(LifeWeaveDest.Water) },
                            onOpenPeriod = { navigateToTab(LifeWeaveDest.Period) },
                            onOpenReminders = { navigateToTab(LifeWeaveDest.Settings) },
                            onNavigateModule = { subRoute ->
                                navController.navigate(LifeWeaveDest.fromSubRouteString(subRoute))
                            }
                        )
                    }

                    composable<LifeWeaveDest.FitnessYoga> {
                        FitnessYogaScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenConnectedDevices = {
                                navController.navigate(LifeWeaveDest.ConnectedDevices)
                            }
                        )
                    }

                    composable<LifeWeaveDest.Diet> {
                        DietNutritionScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenConnectedDevices = {
                                navController.navigate(LifeWeaveDest.ConnectedDevices)
                            }
                        )
                    }

                    composable<LifeWeaveDest.Medical> {
                        MedicalCareScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() },
                            vaultState = healthVaultState,
                            onOpenHealthVault = { navController.navigate(LifeWeaveDest.HealthVault) },
                            onRestockMedicine = healthVaultViewModel::logMedicinePurchaseAndRefill
                        )
                    }

                    composable<LifeWeaveDest.HealthVault> {
                        HealthVaultScreen(
                            uiState = healthVaultState,
                            viewModel = healthVaultViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenMedicines = { navController.navigate(LifeWeaveDest.Medical) }
                        )
                    }

                    composable<LifeWeaveDest.Beauty> {
                        BeautyCareScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<LifeWeaveDest.Wellbeing> {
                        DigitalWellbeingScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<LifeWeaveDest.Reports> {
                        ReportsAnalyticsScreen(
                            uiState = modulesHubState,
                            onBack = { navController.popBackStack() },
                            onSelectTimeframe = modulesHubViewModel::selectIntelligenceTimeframe,
                            onGenerateAiInsights = modulesHubViewModel::generateAiHealthIntelligence,
                            onClearAiReport = modulesHubViewModel::clearAiHealthIntelligenceReport,
                            onSpeakSummary = modulesHubViewModel::speakIntelligenceSummary,
                            onStopSpeaking = modulesHubViewModel::stopVoicePlayback,
                            onToggleAiModuleSharing = modulesHubViewModel::setAiModuleSharing,
                            onOpenModuleKey = ::navigateByModuleKey,
                            onRunDiagnostics = modulesHubViewModel::runAiDiagnostics
                        )
                    }

                    composable<LifeWeaveDest.OnDeviceAi> {
                        OnDeviceAiScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateModuleKey = ::navigateByModuleKey
                        )
                    }

                    composable<LifeWeaveDest.ConnectedDevices> {
                        ConnectedDevicesScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            healthConnectManager = container.healthConnectManager,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<LifeWeaveDest.Settings> {
                        val settingsViewModel: SettingsPrivacyViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                SettingsPrivacyViewModel(
                                    preferencesManager = container.preferencesManager,
                                    userProfileRepository = container.userProfileRepository,
                                    customAlarmRepository = container.customAlarmRepository,
                                    moduleRepository = container.moduleRepository,
                                    privacyBackupRepository = container.privacyBackupRepository,
                                    alertSoundManager = container.alertSoundManager,
                                    vaultManager = container.vaultManager,
                                    licenseVerifier = container.licenseVerifier,
                                    subscriptionRepository = container.subscriptionRepository,
                                    personalAiKeyStore = container.personalAiKeyStore,
                                    aiService = container.lifeWeaveAiService
                                )
                            }
                        )
                        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                        SettingsPrivacyScreen(
                            uiState = settingsState,
                            viewModel = settingsViewModel,
                            onEditProfile = {
                                navController.navigate(LifeWeaveDest.ProfileEdit)
                            },
                            onOpenFamilyCircle = {
                                navController.navigate(LifeWeaveDest.FamilyCircle)
                            },
                            onOpenSubscription = {
                                navController.navigate(LifeWeaveDest.Subscription)
                            },
                            onAllDataWiped = {
                                // SessionViewModel reacts to DB & DataStore clear and routes back to Welcome
                            }
                        )
                    }

                    composable<LifeWeaveDest.FamilyCircle> {
                        FamilyCircleRoute(
                            container = container,
                            onBack = { navController.popBackStack() },
                            onOpenSubscriptions = {
                                navController.navigate(LifeWeaveDest.Subscription)
                            }
                        )
                    }

                    composable<LifeWeaveDest.Subscription> {
                        val subscriptionViewModel: SubscriptionViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                SubscriptionViewModel(
                                    subscriptionRepository = container.subscriptionRepository,
                                    preferencesManager = container.preferencesManager
                                )
                            }
                        )
                        val subscriptionState by subscriptionViewModel.uiState.collectAsStateWithLifecycle()
                        SubscriptionScreen(
                            uiState = subscriptionState,
                            viewModel = subscriptionViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<LifeWeaveDest.RoutinesMood> { backStack ->
                        val dest = backStack.toRoute<LifeWeaveDest.RoutinesMood>()
                        val routineMoodViewModel: RoutineMoodViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                RoutineMoodViewModel(
                                    userProfileRepository = container.userProfileRepository,
                                    routineMoodRepository = container.routineMoodRepository,
                                    waterRepository = container.waterRepository,
                                    allModulesRepository = container.allModulesRepository,
                                    customAlarmRepository = container.customAlarmRepository
                                )
                            }
                        )
                        val routineMoodState by routineMoodViewModel.uiState.collectAsStateWithLifecycle()
                        RoutineMoodScreen(
                            uiState = routineMoodState,
                            viewModel = routineMoodViewModel,
                            initialTabIndex = dest.initialTab,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable<LifeWeaveDest.ProfileEdit> {
                        val profileViewModel: ProfileSetupViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                ProfileSetupViewModel(
                                    userProfileRepository = container.userProfileRepository,
                                    moduleRepository = container.moduleRepository,
                                    preferencesManager = container.preferencesManager,
                                    alertSoundManager = container.alertSoundManager,
                                    database = container.database
                                )
                            }
                        )
                        val activeProfile by container.userProfileRepository
                            .observeActiveProfile()
                            .collectAsStateWithLifecycle(initialValue = null)
                        LaunchedEffect(activeProfile?.id) {
                            profileViewModel.loadExistingProfile(activeProfile)
                        }
                        val formState by profileViewModel.formState.collectAsStateWithLifecycle()
                        ProfileSetupScreen(
                            formState = formState,
                            viewModel = profileViewModel,
                            canNavigateBack = true,
                            onBack = { navController.popBackStack() },
                            onSaved = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
