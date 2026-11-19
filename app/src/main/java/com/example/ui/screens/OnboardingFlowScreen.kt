package com.example.ui.screens

import android.app.Activity
import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Translate
import androidx.compose.material.icons.rounded.Watch
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.family.AuthenticatedUserSnapshot
import com.example.notifications.AlertSoundManager
import com.example.security.SupportedLanguage
import com.example.sync.AppAutoRefreshManager
import com.example.ui.components.PrivacyVaultBadge
import com.example.ui.theme.ThemeMode
import com.example.ui.viewmodel.OnboardingNotificationPrefs
import com.example.ui.viewmodel.ProfileFormState
import com.example.ui.viewmodel.ProfileSetupViewModel
import com.example.wellbeing.DeviceWellbeingManager
import kotlinx.coroutines.launch

enum class OnboardingStep(val index: Int, val label: String) {
    WELCOME(0, "Welcome"),
    FEATURES_TOUR(1, "Features"),
    SIGN_IN(2, "Account"),
    PROFILE_SETUP(3, "Profile"),
    PREFERENCES(4, "Preferences")
}

private data class FeatureTourSlide(
    val title: String,
    val badge: String,
    val description: String,
    val highlights: List<String>,
    val imageRes: Int,
    val icon: ImageVector
)

private val CoreFeatureSlides = listOf(
    FeatureTourSlide(
        title = "Hydration, Sleep Quality & Snore Monitor",
        badge = "Daily Vitals",
        description = "Track daily water intake with adaptive targets, and monitor overnight sleep stages, restlessness, and snoring entirely on-device.",
        highlights = listOf(
            "Personalized water target from your weight & activity level",
            "On-device acoustic snoring & restlessness DSP — zero audio stored",
            "Automatic morning sleep quality score & stage breakdown"
        ),
        imageRes = R.drawable.img_hydration_wave_art,
        icon = Icons.Rounded.Bedtime
    ),
    FeatureTourSlide(
        title = "Always-Free Cycle Care & Health Vault",
        badge = "Private Health",
        description = "Log cycles, symptoms, medicines, and medical PDFs in an encrypted local vault. Period tracking is 100% free forever.",
        highlights = listOf(
            "Cycle predictions, PMS insights & private local symptom logs",
            "Medicine schedules, refill reminders & doctor visit notes",
            "Encrypted vault with biometric lock for lab reports & prescriptions"
        ),
        imageRes = R.drawable.img_welcome_wellness,
        icon = Icons.Rounded.LocalHospital
    ),
    FeatureTourSlide(
        title = "Digital Wellbeing & Connected Wearables",
        badge = "Auto-Refresh",
        description = "See real screen-time balance, gentle overuse nudges, and Health Connect fitness sync — refreshed automatically in the background.",
        highlights = listOf(
            "Live screen time, unlock counts & daily budget balance alerts",
            "Syncs steps, workouts, heart rate & sleep from wearables",
            "Play-compliant automatic refresh every 15–60 minutes"
        ),
        imageRes = R.drawable.img_home_hero_art,
        icon = Icons.Rounded.Watch
    ),
    FeatureTourSlide(
        title = "Private AI Companion & Family Circle",
        badge = "Smart Care",
        description = "Get calm wellness briefings in 6 languages, log moods & routines, and optionally coordinate care with loved ones.",
        highlights = listOf(
            "Interactive voice & text summaries with strict privacy controls",
            "Daily routines, skincare/self-care & mood reflection journal",
            "Optional Family Circle sharing — or stay 100% offline forever"
        ),
        imageRes = R.drawable.img_ai_aurora_art,
        icon = Icons.Rounded.AutoAwesome
    )
)

@Composable
fun OnboardingFlowScreen(
    selectedLanguage: SupportedLanguage,
    selectedThemeMode: ThemeMode,
    signedInUser: AuthenticatedUserSnapshot?,
    formState: ProfileFormState,
    profileViewModel: ProfileSetupViewModel,
    alertSoundManager: AlertSoundManager? = null,
    deviceWellbeingManager: DeviceWellbeingManager? = null,
    onSelectLanguage: (SupportedLanguage) -> Unit,
    onSelectThemeMode: (ThemeMode) -> Unit,
    onSignInWithGoogle: suspend (Context) -> Result<AuthenticatedUserSnapshot>,
    onSignInWithGitHub: suspend (Activity) -> Result<AuthenticatedUserSnapshot>,
    onSignInWithMicrosoft: suspend (Activity) -> Result<AuthenticatedUserSnapshot>,
    onFetchSignedInDisplayName: () -> String? = { signedInUser?.displayName },
    onOnboardingCompleted: () -> Unit = {}
) {
    val context = LocalContext.current
    var currentStepOrdinal by rememberSaveable { mutableStateOf(OnboardingStep.WELCOME.ordinal) }
    val currentStep = OnboardingStep.entries[currentStepOrdinal]
    var notificationPrefs by remember { mutableStateOf(OnboardingNotificationPrefs()) }
    var showPermissionsPopup by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(selectedLanguage) {
        profileViewModel.updatePreferredLanguageCode(selectedLanguage.code)
    }

    LaunchedEffect(currentStep) {
        if (currentStep == OnboardingStep.PROFILE_SETUP) {
            profileViewModel.prefillNameIfBlank(onFetchSignedInDisplayName())
        }
    }

    val navigateBack: () -> Unit = {
        if (currentStepOrdinal > 0) {
            currentStepOrdinal -= 1
        }
    }

    BackHandler(enabled = currentStepOrdinal > 0) {
        navigateBack()
    }

    val targetProgress = (currentStep.index + 1f) / OnboardingStep.entries.size.toFloat()
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = tween(durationMillis = 320),
        label = "onboarding_step_progress"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .testTag("onboarding_flow_root")
    ) {
        // Persistent unified step progress header
        OnboardingTopProgressHeader(
            currentStep = currentStep,
            progress = animatedProgress,
            canGoBack = currentStepOrdinal > 0,
            onBack = navigateBack
        )

        // Smooth directional slide + fade step content
        AnimatedContent(
            targetState = currentStep,
            transitionSpec = {
                val forward = targetState.index >= initialState.index
                if (forward) {
                    (slideInHorizontally(animationSpec = tween(280)) { it / 4 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(220)) { -it / 5 } + fadeOut(tween(180)))
                } else {
                    (slideInHorizontally(animationSpec = tween(280)) { -it / 4 } + fadeIn(tween(260))) togetherWith
                        (slideOutHorizontally(animationSpec = tween(220)) { it / 5 } + fadeOut(tween(180)))
                }
            },
            modifier = Modifier.weight(1f),
            label = "onboarding_step_transition"
        ) { step ->
            when (step) {
                OnboardingStep.WELCOME -> {
                    OnboardingWelcomeStep(
                        selectedLanguage = selectedLanguage,
                        onSelectLanguage = onSelectLanguage,
                        onContinue = { currentStepOrdinal = OnboardingStep.FEATURES_TOUR.ordinal }
                    )
                }

                OnboardingStep.FEATURES_TOUR -> {
                    OnboardingFeaturesTourStep(
                        onContinue = { currentStepOrdinal = OnboardingStep.SIGN_IN.ordinal }
                    )
                }

                OnboardingStep.SIGN_IN -> {
                    PreHomeAuthScreen(
                        signedInUser = signedInUser,
                        canGoBackToWelcome = false,
                        onBackToWelcome = { currentStepOrdinal = OnboardingStep.FEATURES_TOUR.ordinal },
                        onSignInWithGoogle = onSignInWithGoogle,
                        onSignInWithGitHub = onSignInWithGitHub,
                        onSignInWithMicrosoft = onSignInWithMicrosoft,
                        onContinueToApp = {
                            profileViewModel.prefillNameIfBlank(onFetchSignedInDisplayName())
                            currentStepOrdinal = OnboardingStep.PROFILE_SETUP.ordinal
                        }
                    )
                }

                OnboardingStep.PROFILE_SETUP -> {
                    OnboardingProfileStep(
                        formState = formState,
                        profileViewModel = profileViewModel,
                        onContinueToPreferences = {
                            currentStepOrdinal = OnboardingStep.PREFERENCES.ordinal
                        }
                    )
                }

                OnboardingStep.PREFERENCES -> {
                    OnboardingPreferencesStep(
                        selectedThemeMode = selectedThemeMode,
                        onSelectThemeMode = onSelectThemeMode,
                        cycleTrackingEnabled = formState.cycleTrackingEnabled,
                        notificationPrefs = notificationPrefs,
                        onUpdateNotificationPrefs = { notificationPrefs = it },
                        isSaving = formState.isSaving,
                        onOpenPermissionsPopup = { showPermissionsPopup = true },
                        onFinishOnboarding = {
                            AppAutoRefreshManager.schedulePeriodicWork(
                                context = context,
                                enabled = notificationPrefs.autoRefreshEnabled,
                                intervalMinutes = notificationPrefs.autoRefreshIntervalMinutes
                            )
                            profileViewModel.completeOnboardingWithPreferences(
                                themeMode = selectedThemeMode,
                                notificationPrefs = notificationPrefs,
                                onCompleted = onOnboardingCompleted
                            )
                        }
                    )
                }
            }
        }
    }

    if (showPermissionsPopup && alertSoundManager != null && deviceWellbeingManager != null) {
        PermissionsPopupDialog(
            alertSoundManager = alertSoundManager,
            deviceWellbeingManager = deviceWellbeingManager,
            onDismissComplete = { showPermissionsPopup = false }
        )
    }
}

@Composable
private fun OnboardingTopProgressHeader(
    currentStep: OnboardingStep,
    progress: Float,
    canGoBack: Boolean,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (canGoBack) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("onboarding_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                            contentDescription = "Previous onboarding step"
                        )
                    }
                } else {
                    Spacer(modifier = Modifier.width(8.dp))
                }
                Text(
                    text = currentStep.label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }

            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
            ) {
                Text(
                    text = "Step ${currentStep.index + 1} of ${OnboardingStep.entries.size}",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 5.dp)
                        .testTag("onboarding_step_counter")
                )
            }
        }

        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(999.dp))
                .testTag("onboarding_progress_bar"),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingWelcomeStep(
    selectedLanguage: SupportedLanguage,
    onSelectLanguage: (SupportedLanguage) -> Unit,
    onContinue: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .testTag("onboarding_step_welcome"),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(210.dp)
                    .clip(RoundedCornerShape(28.dp))
            ) {
                Image(
                    painter = painterResource(id = R.drawable.img_welcome_wellness),
                    contentDescription = "Botanical wellness companion illustration",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    MaterialTheme.colorScheme.background.copy(alpha = 0.85f)
                                )
                            )
                        )
                )
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.welcome_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = stringResource(id = R.string.tagline_privacy),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            PrivacyVaultBadge(modifier = Modifier.fillMaxWidth())

            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(id = R.string.welcome_subtitle),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    WelcomeBulletRow(
                        icon = Icons.Rounded.WaterDrop,
                        text = "Explore daily vitals, automatic sleep & snore tracking, and always-free cycle care"
                    )
                    WelcomeBulletRow(
                        icon = Icons.Rounded.Favorite,
                        text = "Personalize your age, gender identity, and hydration goal privately on your phone"
                    )
                    WelcomeBulletRow(
                        icon = Icons.Rounded.PhoneAndroid,
                        text = "Pick your Light or Dark theme and gentle notification reminders"
                    )
                }
            }

            // Language picker
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Translate,
                            contentDescription = "Choose app language",
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(id = R.string.welcome_language_title),
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        SupportedLanguage.entries.forEach { language ->
                            FilterChip(
                                selected = selectedLanguage == language,
                                onClick = { onSelectLanguage(language) },
                                label = { Text("${language.nativeName} • ${language.displayLabel}") },
                                modifier = Modifier.testTag("onboarding_lang_${language.code}")
                            )
                        }
                    }
                }
            }

            Button(
                onClick = onContinue,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("onboarding_welcome_next_button"),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = "Explore Core Features",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = "Explore features"
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun WelcomeBulletRow(
    icon: ImageVector,
    text: String
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(18.dp)
        )
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun OnboardingFeaturesTourStep(
    onContinue: () -> Unit
) {
    val pagerState = rememberPagerState(pageCount = { CoreFeatureSlides.size })
    val coroutineScope = rememberCoroutineScope()
    val isLastSlide = pagerState.currentPage == CoreFeatureSlides.lastIndex

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 600.dp)
                .verticalScroll(rememberScrollState())
                .padding(vertical = 8.dp)
                .testTag("onboarding_step_features"),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Everything you need in one calm place",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Swipe through LifeWeave's core wellness modules.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            HorizontalPager(
                state = pagerState,
                contentPadding = PaddingValues(horizontal = 20.dp),
                pageSpacing = 14.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("onboarding_features_pager")
            ) { pageIndex ->
                val slide = CoreFeatureSlides[pageIndex]
                FeatureTourSlideCard(slide = slide)
            }

            // Animated page indicator dots
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                CoreFeatureSlides.indices.forEach { idx ->
                    val selected = pagerState.currentPage == idx
                    val dotWidth by animateDpAsState(
                        targetValue = if (selected) 26.dp else 8.dp,
                        animationSpec = tween(220),
                        label = "pager_dot_$idx"
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 4.dp)
                            .height(8.dp)
                            .width(dotWidth)
                            .clip(CircleShape)
                            .background(
                                if (selected) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.outlineVariant
                            )
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isLastSlide) {
                    TextButton(
                        onClick = onContinue,
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("onboarding_features_skip_button")
                    ) {
                        Text("Skip tour")
                    }
                    FilledTonalButton(
                        onClick = {
                            coroutineScope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .height(52.dp)
                            .testTag("onboarding_features_slide_next_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Next Feature",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Next feature slide"
                        )
                    }
                } else {
                    Button(
                        onClick = onContinue,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(54.dp)
                            .testTag("onboarding_features_next_button"),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Text(
                            text = "Continue to Account Setup",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = "Continue to account setup"
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
        }
    }
}

@Composable
private fun FeatureTourSlideCard(slide: FeatureTourSlide) {
    Card(
        shape = RoundedCornerShape(26.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(172.dp)
            ) {
                Image(
                    painter = painterResource(id = slide.imageRes),
                    contentDescription = slide.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    Color.Black.copy(alpha = 0.08f),
                                    Color.Black.copy(alpha = 0.45f)
                                )
                            )
                        )
                )
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.92f),
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = slide.icon,
                            contentDescription = slide.badge,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = slide.badge,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = slide.title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = slide.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    slide.highlights.forEach { bullet ->
                        Row(
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier
                                    .padding(top = 2.dp)
                                    .size(16.dp)
                            )
                            Text(
                                text = bullet,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }
        }
    }
}
