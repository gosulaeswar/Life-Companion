package com.example.ui.screens

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.MailOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.family.AuthenticatedUserSnapshot
import com.example.ui.components.PrivacyVaultBadge
import kotlinx.coroutines.launch

private fun Context.findActivityOrNull(): Activity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is Activity) return current
        current = current.baseContext
    }
    return null
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PreHomeAuthScreen(
    signedInUser: AuthenticatedUserSnapshot?,
    canGoBackToWelcome: Boolean,
    onBackToWelcome: () -> Unit,
    onSignInWithGoogle: suspend (Context) -> Result<AuthenticatedUserSnapshot>,
    onSignInWithGitHub: suspend (Activity) -> Result<AuthenticatedUserSnapshot>,
    onSignInWithMicrosoft: suspend (Activity) -> Result<AuthenticatedUserSnapshot>,
    onContinueToApp: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var activeProviderLoading by remember { mutableStateOf<String?>(null) }
    var statusError by remember { mutableStateOf<String?>(null) }
    var signedInSnapshot by remember(signedInUser) { mutableStateOf(signedInUser) }

    if (canGoBackToWelcome) {
        BackHandler(onBack = onBackToWelcome)
    }

    val isBusy = activeProviderLoading != null

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.background,
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
                        MaterialTheme.colorScheme.background
                    )
                )
            )
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Column(
                modifier = Modifier.widthIn(max = 560.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header with optional Back to App Guide
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (canGoBackToWelcome) {
                            IconButton(
                                onClick = onBackToWelcome,
                                modifier = Modifier.testTag("prehome_back_to_welcome_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                    contentDescription = "Back to App Guide"
                                )
                            }
                        }
                        Text(
                            text = stringResource(id = R.string.app_name),
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    PrivacyVaultBadge(text = stringResource(id = R.string.auth_privacy_pill))
                }

                // Visual Welcome Header Banner
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(144.dp)
                        .clip(RoundedCornerShape(24.dp))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.img_welcome_wellness),
                        contentDescription = "LifeWeave wellness overview banner",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.15f),
                                        MaterialTheme.colorScheme.scrim.copy(alpha = 0.62f)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomStart)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.auth_screen_title),
                            style = MaterialTheme.typography.headlineSmall,
                            color = MaterialTheme.colorScheme.onPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(id = R.string.auth_screen_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.92f)
                        )
                    }
                }

                // Simple Plain-Language "What LifeWeave Does" Summary Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("prehome_app_overview_card"),
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = stringResource(id = R.string.auth_what_is_title),
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = stringResource(id = R.string.auth_what_is_body),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OverviewPill("💧 Water & Habits")
                            OverviewPill("💊 Medicines & Vault")
                            OverviewPill("🌸 Free Period Care")
                            OverviewPill("📓 Mood & Sleep")
                            OverviewPill("👨‍👩‍👧 Family Circle")
                        }
                    }
                }

                // Signed-In Banner OR Prominent Provider Sign-In Card
                val activeUser = signedInSnapshot
                if (activeUser != null) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("prehome_signed_in_card"),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        )
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.CheckCircle,
                                    contentDescription = "Signed in",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Column {
                                    Text(
                                        text = "Signed in via ${activeUser.providerLabel}",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Text(
                                        text = "${activeUser.displayName}${if (activeUser.email.isNotBlank()) " • ${activeUser.email}" else ""}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                    )
                                }
                            }
                            Button(
                                onClick = onContinueToApp,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("prehome_continue_signed_in_button"),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Text(stringResource(id = R.string.auth_continue_to_home))
                                Spacer(modifier = Modifier.size(8.dp))
                                Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
                            }
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(22.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(18.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = stringResource(id = R.string.auth_choose_login_title),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = stringResource(id = R.string.auth_choose_login_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            // 1. Google / Gmail Sign-In (Primary)
                            Button(
                                onClick = {
                                    statusError = null
                                    activeProviderLoading = "google"
                                    scope.launch {
                                        val res = onSignInWithGoogle(context)
                                        activeProviderLoading = null
                                        res.onSuccess { user ->
                                            signedInSnapshot = user
                                            onContinueToApp()
                                        }.onFailure { err ->
                                            statusError = err.message ?: "Unable to sign in with Google."
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(54.dp)
                                    .testTag("prehome_google_signin_button"),
                                shape = RoundedCornerShape(16.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary
                                )
                            ) {
                                if (activeProviderLoading == "google") {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = MaterialTheme.colorScheme.onPrimary
                                    )
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Text("Connecting Google (Gmail)...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.MailOutline,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Text(
                                        text = stringResource(id = R.string.auth_btn_google),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }

                            // 2. GitHub Sign-In (Firebase OAuthProvider)
                            FilledTonalButton(
                                onClick = {
                                    val activity = context.findActivityOrNull()
                                    if (activity == null) {
                                        statusError = "Unable to open GitHub Sign-In from this screen context."
                                        return@FilledTonalButton
                                    }
                                    statusError = null
                                    activeProviderLoading = "github"
                                    scope.launch {
                                        val res = onSignInWithGitHub(activity)
                                        activeProviderLoading = null
                                        res.onSuccess { user ->
                                            signedInSnapshot = user
                                            onContinueToApp()
                                        }.onFailure { err ->
                                            statusError = err.message ?: "Unable to sign in with GitHub."
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("prehome_github_signin_button"),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                if (activeProviderLoading == "github") {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Text("Connecting GitHub...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.Code,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Text(
                                        text = stringResource(id = R.string.auth_btn_github),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }

                            // 3. Microsoft Sign-In (Firebase OAuthProvider)
                            OutlinedButton(
                                onClick = {
                                    val activity = context.findActivityOrNull()
                                    if (activity == null) {
                                        statusError = "Unable to open Microsoft Sign-In from this screen context."
                                        return@OutlinedButton
                                    }
                                    statusError = null
                                    activeProviderLoading = "microsoft"
                                    scope.launch {
                                        val res = onSignInWithMicrosoft(activity)
                                        activeProviderLoading = null
                                        res.onSuccess { user ->
                                            signedInSnapshot = user
                                            onContinueToApp()
                                        }.onFailure { err ->
                                            statusError = err.message ?: "Unable to sign in with Microsoft."
                                        }
                                    }
                                },
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(52.dp)
                                    .testTag("prehome_microsoft_signin_button"),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                if (activeProviderLoading == "microsoft") {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Text("Connecting Microsoft...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Rounded.GridView,
                                        contentDescription = null,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.size(10.dp))
                                    Text(
                                        text = stringResource(id = R.string.auth_btn_microsoft),
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                }
                            }

                            statusError?.let { errorMsg ->
                                Surface(
                                    shape = RoundedCornerShape(14.dp),
                                    color = MaterialTheme.colorScheme.errorContainer
                                ) {
                                    Text(
                                        text = errorMsg,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                        modifier = Modifier.padding(12.dp)
                                    )
                                }
                            }

                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

                            // 4. Private On-Device Option (Offline-First)
                            TextButton(
                                onClick = onContinueToApp,
                                enabled = !isBusy,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp)
                                    .testTag("prehome_offline_continue_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.Shield,
                                    contentDescription = null,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = stringResource(id = R.string.auth_btn_continue_offline),
                                    style = MaterialTheme.typography.labelLarge
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun OverviewPill(text: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}
