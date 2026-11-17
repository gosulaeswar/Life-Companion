package com.example.ui.navigation

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.R
import com.example.di.AppContainer
import com.example.di.SimpleViewModelFactory
import com.example.notifications.AlertCategory
import com.example.security.ProvideLocalizedLocale
import com.example.ui.screens.BeautyCareScreen
import com.example.ui.screens.ConnectedDevicesScreen
import com.example.ui.screens.DietNutritionScreen
import com.example.ui.screens.DigitalWellbeingScreen
import com.example.ui.screens.FitnessYogaScreen
import com.example.ui.screens.HomeDashboardScreen
import com.example.ui.screens.MedicalCareScreen
import com.example.ui.screens.ModuleSubRoutes
import com.example.ui.screens.ModulesHubScreen
import com.example.ui.screens.OnDeviceAiScreen
import com.example.ui.screens.PeriodTrackerScreen
import com.example.ui.screens.ProfileSetupScreen
import com.example.ui.screens.ReportsAnalyticsScreen
import com.example.ui.screens.SettingsPrivacyScreen
import com.example.ui.screens.WaterTrackerScreen
import com.example.ui.screens.WelcomeScreen
import com.example.ui.theme.LifeCompanionTheme
import com.example.ui.viewmodel.AppLaunchDestination
import com.example.ui.viewmodel.AppSessionViewModel
import com.example.ui.viewmodel.HomeViewModel
import com.example.ui.viewmodel.ModulesHubViewModel
import com.example.ui.viewmodel.PeriodViewModel
import com.example.ui.viewmodel.ProfileSetupViewModel
import com.example.ui.viewmodel.SettingsPrivacyViewModel
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
    val route: String,
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
            when (sessionState.launchDestination) {
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

            AppLaunchDestination.Welcome -> {
                WelcomeScreen(
                    selectedLanguage = sessionState.preferences.language,
                    onSelectLanguage = sessionViewModel::setLanguage,
                    onGetStarted = sessionViewModel::completeWelcome
                )
            }

            AppLaunchDestination.ProfileSetup -> {
                val profileViewModel: ProfileSetupViewModel = viewModel(
                    factory = SimpleViewModelFactory {
                        ProfileSetupViewModel(
                            userProfileRepository = container.userProfileRepository,
                            moduleRepository = container.moduleRepository,
                            preferencesManager = container.preferencesManager
                        )
                    }
                )
                val formState by profileViewModel.formState.collectAsStateWithLifecycle()
                ProfileSetupScreen(
                    formState = formState,
                    viewModel = profileViewModel,
                    canNavigateBack = false,
                    onBack = {},
                    onSaved = {}
                )
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
                preferencesManager = container.preferencesManager
            )
        }
    )
    val modulesHubState by modulesHubViewModel.uiState.collectAsStateWithLifecycle()

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
                modulesHubViewModel.refreshWellbeingFromDevice(includePastWeek = false)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val tabs = buildList {
        add(
            NavTabItem(
                route = LifeWeaveRoutes.ROUTE_HOME,
                labelRes = R.string.nav_home,
                selectedIcon = Icons.Rounded.Home,
                unselectedIcon = Icons.Outlined.Home,
                testTag = "nav_tab_home"
            )
        )
        add(
            NavTabItem(
                route = LifeWeaveRoutes.ROUTE_WATER,
                labelRes = R.string.nav_water,
                selectedIcon = Icons.Rounded.WaterDrop,
                unselectedIcon = Icons.Outlined.WaterDrop,
                testTag = "nav_tab_water"
            )
        )
        if (cycleTabVisible) {
            add(
                NavTabItem(
                    route = LifeWeaveRoutes.ROUTE_PERIOD,
                    labelRes = R.string.nav_period,
                    selectedIcon = Icons.Rounded.Favorite,
                    unselectedIcon = Icons.Outlined.FavoriteBorder,
                    testTag = "nav_tab_period"
                )
            )
        }
        add(
            NavTabItem(
                route = LifeWeaveRoutes.ROUTE_MODULES,
                labelRes = R.string.nav_modules,
                selectedIcon = Icons.Rounded.GridView,
                unselectedIcon = Icons.Outlined.GridView,
                testTag = "nav_tab_modules"
            )
        )
        add(
            NavTabItem(
                route = LifeWeaveRoutes.ROUTE_SETTINGS,
                labelRes = R.string.nav_settings,
                selectedIcon = Icons.Rounded.Shield,
                unselectedIcon = Icons.Outlined.Shield,
                testTag = "nav_tab_settings"
            )
        )
    }

    fun navigateToTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    fun navigateByModuleKey(moduleKey: String) {
        when (moduleKey) {
            "water_tracker" -> navigateToTab(LifeWeaveRoutes.ROUTE_WATER)
            "period_tracker" -> navigateToTab(LifeWeaveRoutes.ROUTE_PERIOD)
            "daily_reminders" -> navigateToTab(LifeWeaveRoutes.ROUTE_SETTINGS)
            "exercise_tracker", "yoga_tracker" -> navController.navigate(ModuleSubRoutes.ROUTE_FITNESS_YOGA)
            "diet_tracker" -> navController.navigate(ModuleSubRoutes.ROUTE_DIET)
            "medical_care" -> navController.navigate(ModuleSubRoutes.ROUTE_MEDICAL)
            "beauty_care" -> navController.navigate(ModuleSubRoutes.ROUTE_BEAUTY)
            "digital_wellbeing" -> navController.navigate(ModuleSubRoutes.ROUTE_WELLBEING)
            "reports_analytics" -> navController.navigate(ModuleSubRoutes.ROUTE_REPORTS)
            "on_device_ai" -> navController.navigate(ModuleSubRoutes.ROUTE_ON_DEVICE_AI)
            "connected_devices" -> navController.navigate(ModuleSubRoutes.ROUTE_CONNECTED_DEVICES)
            else -> navigateToTab(LifeWeaveRoutes.ROUTE_MODULES)
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
        currentDestination?.hierarchy?.any { it.route == tab.route } == true
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
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface
                    ) {
                        tabs.forEach { tab ->
                            val selected = currentDestination?.hierarchy?.any {
                                it.route == tab.route
                            } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(id = tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
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
                                it.route == tab.route
                            } == true
                            NavigationRailItem(
                                selected = selected,
                                onClick = { navigateToTab(tab.route) },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(id = tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(id = tab.labelRes)) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                NavHost(
                    navController = navController,
                    startDestination = LifeWeaveRoutes.ROUTE_HOME,
                    modifier = Modifier.weight(1f)
                ) {
                    composable(LifeWeaveRoutes.ROUTE_HOME) {
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
                            onOpenWaterTracker = { navigateToTab(LifeWeaveRoutes.ROUTE_WATER) },
                            onOpenPeriodTracker = { navigateToTab(LifeWeaveRoutes.ROUTE_PERIOD) },
                            onEnableCycleTracking = {
                                homeViewModel.enableCycleTracking()
                                navigateToTab(LifeWeaveRoutes.ROUTE_PERIOD)
                            },
                            onOpenProfileEdit = {
                                navController.navigate(LifeWeaveRoutes.ROUTE_PROFILE_EDIT)
                            },
                            onOpenSettingsPrivacy = { navigateToTab(LifeWeaveRoutes.ROUTE_SETTINGS) },
                            onOpenModulesHub = { navigateToTab(LifeWeaveRoutes.ROUTE_MODULES) },
                            onOpenModuleKey = { key -> navigateByModuleKey(key) },
                            onStartVoiceCapture = modulesHubViewModel::startVoiceCapture,
                            onStopVoiceCapture = modulesHubViewModel::stopVoiceCapture,
                            onStopVoicePlayback = modulesHubViewModel::stopVoicePlayback,
                            onClearVoiceError = modulesHubViewModel::clearVoiceError,
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
                            }
                        )
                    }

                    composable(LifeWeaveRoutes.ROUTE_WATER) {
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
                            onOpenAlertSettings = { navigateToTab(LifeWeaveRoutes.ROUTE_SETTINGS) }
                        )
                    }

                    composable(LifeWeaveRoutes.ROUTE_PERIOD) {
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
                            onOpenAlertSettings = { navigateToTab(LifeWeaveRoutes.ROUTE_SETTINGS) }
                        )
                    }

                    composable(LifeWeaveRoutes.ROUTE_MODULES) {
                        ModulesHubScreen(
                            uiState = modulesHubState,
                            onOpenWater = { navigateToTab(LifeWeaveRoutes.ROUTE_WATER) },
                            onOpenPeriod = { navigateToTab(LifeWeaveRoutes.ROUTE_PERIOD) },
                            onOpenReminders = { navigateToTab(LifeWeaveRoutes.ROUTE_SETTINGS) },
                            onNavigateModule = { subRoute -> navController.navigate(subRoute) }
                        )
                    }

                    composable(ModuleSubRoutes.ROUTE_FITNESS_YOGA) {
                        FitnessYogaScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenConnectedDevices = {
                                navController.navigate(ModuleSubRoutes.ROUTE_CONNECTED_DEVICES)
                            }
                        )
                    }

                    composable(ModuleSubRoutes.ROUTE_DIET) {
                        DietNutritionScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(ModuleSubRoutes.ROUTE_MEDICAL) {
                        MedicalCareScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(ModuleSubRoutes.ROUTE_BEAUTY) {
                        BeautyCareScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(ModuleSubRoutes.ROUTE_WELLBEING) {
                        DigitalWellbeingScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(ModuleSubRoutes.ROUTE_REPORTS) {
                        ReportsAnalyticsScreen(
                            uiState = modulesHubState,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(ModuleSubRoutes.ROUTE_ON_DEVICE_AI) {
                        OnDeviceAiScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            onBack = { navController.popBackStack() },
                            onNavigateModuleKey = ::navigateByModuleKey
                        )
                    }

                    composable(ModuleSubRoutes.ROUTE_CONNECTED_DEVICES) {
                        ConnectedDevicesScreen(
                            uiState = modulesHubState,
                            viewModel = modulesHubViewModel,
                            healthConnectManager = container.healthConnectManager,
                            onBack = { navController.popBackStack() }
                        )
                    }

                    composable(LifeWeaveRoutes.ROUTE_SETTINGS) {
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
                                    licenseVerifier = container.licenseVerifier
                                )
                            }
                        )
                        val settingsState by settingsViewModel.uiState.collectAsStateWithLifecycle()
                        SettingsPrivacyScreen(
                            uiState = settingsState,
                            viewModel = settingsViewModel,
                            onEditProfile = {
                                navController.navigate(LifeWeaveRoutes.ROUTE_PROFILE_EDIT)
                            },
                            onAllDataWiped = {
                                // SessionViewModel reacts to DB & DataStore clear and routes back to Welcome
                            }
                        )
                    }

                    composable(LifeWeaveRoutes.ROUTE_PROFILE_EDIT) {
                        val profileViewModel: ProfileSetupViewModel = viewModel(
                            factory = SimpleViewModelFactory {
                                ProfileSetupViewModel(
                                    userProfileRepository = container.userProfileRepository,
                                    moduleRepository = container.moduleRepository,
                                    preferencesManager = container.preferencesManager
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
