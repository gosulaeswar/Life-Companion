package com.example.notifications

import android.app.KeyguardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AlarmOff
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Snooze
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.example.MainActivity
import com.example.data.local.AppDatabase
import com.example.security.LocalPreferencesManager
import com.example.security.ProvideLocalizedLocale
import com.example.security.SupportedLanguage
import com.example.ui.theme.JetBrainsMonoMetricFont
import com.example.ui.theme.LifeCompanionTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

class AlarmPopupActivity : ComponentActivity() {

    private var currentPayload by mutableStateOf(
        AlarmPopupPayload(notificationId = 1003, title = "LifeWeave Alarm", message = "")
    )
    private var currentLanguage by mutableStateOf(SupportedLanguage.ENGLISH)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableLockScreenPopupFlags()
        enableEdgeToEdge()
        currentPayload = AlarmPopupPayload.fromIntent(intent)

        setContent {
            LaunchedEffect(Unit) {
                currentLanguage = runCatching {
                    LocalPreferencesManager(applicationContext).preferencesFlow.first().language
                }.getOrDefault(SupportedLanguage.ENGLISH)
            }
            ProvideLocalizedLocale(language = currentLanguage) {
                LifeCompanionTheme {
                    AlarmPopupOverlayScreen(
                        payload = currentPayload,
                        onDone = {
                            silenceAndCancelNotification(currentPayload.notificationId)
                            val alarmId = currentPayload.alarmId
                            if (alarmId > 0L) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    runCatching {
                                        AppDatabase.getInstance(applicationContext)
                                            .reminderDao()
                                            .recordAlarmCompleted(alarmId)
                                    }
                                }
                            }
                            Toast.makeText(
                                this,
                                "${currentPayload.emoji} '${currentPayload.title}' marked complete!",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        },
                        onSnooze = { minutes ->
                            silenceAndCancelNotification(currentPayload.notificationId)
                            val alarmId = currentPayload.alarmId
                            if (alarmId > 0L) {
                                CoroutineScope(Dispatchers.IO).launch {
                                    runCatching {
                                        AppDatabase.getInstance(applicationContext)
                                            .reminderDao()
                                            .recordAlarmSnoozed(alarmId)
                                    }
                                }
                            }
                            AlertSoundManager(this).scheduleSnoozeAlarm(currentPayload, minutes)
                            Toast.makeText(
                                this,
                                "😴 Snoozed '${currentPayload.title}' for ${minutes}m",
                                Toast.LENGTH_SHORT
                            ).show()
                            finish()
                        },
                        onDismiss = {
                            silenceAndCancelNotification(currentPayload.notificationId)
                            finish()
                        },
                        onOpenApp = {
                            silenceAndCancelNotification(currentPayload.notificationId)
                            val openIntent = Intent(this, MainActivity::class.java).apply {
                                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                            }
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val kg = getSystemService(Context.KEYGUARD_SERVICE) as? KeyguardManager
                                if (kg?.isKeyguardLocked == true) {
                                    kg.requestDismissKeyguard(this, null)
                                }
                            }
                            startActivity(openIntent)
                            finish()
                        }
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        currentPayload = AlarmPopupPayload.fromIntent(intent)
    }

    private fun enableLockScreenPopupFlags() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
        } else {
            @Suppress("DEPRECATION")
            window.addFlags(
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
            )
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    }

    private fun silenceAndCancelNotification(notificationId: Int) {
        AlarmRingerManager.stopRinging()
        runCatching {
            NotificationManagerCompat.from(this).cancel(notificationId)
        }
    }
}

@Composable
private fun AlarmPopupOverlayScreen(
    payload: AlarmPopupPayload,
    onDone: () -> Unit,
    onSnooze: (Int) -> Unit,
    onDismiss: () -> Unit,
    onOpenApp: () -> Unit
) {
    val haptics = LocalHapticFeedback.current
    var visible by remember { mutableStateOf(false) }
    var currentClockText by remember {
        mutableStateOf(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()))
    }

    LaunchedEffect(Unit) {
        visible = true
        while (true) {
            currentClockText = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())
            delay(10_000L)
        }
    }

    DisposableEffect(Unit) {
        onDispose { AlarmRingerManager.stopRinging() }
    }

    BackHandler { onDismiss() }

    val pulseTransition = rememberInfiniteTransition(label = "alarm_popup_pulse")
    val pulseScale by pulseTransition.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.09f,
        animationSpec = infiniteRepeatable(
            animation = tween(780, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "emoji_scale"
    )
    val glowAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(820, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glow_alpha"
    )

    val accentColor = when (payload.categoryKey.lowercase()) {
        "water" -> MaterialTheme.colorScheme.primary
        "period" -> MaterialTheme.colorScheme.tertiary
        "wellbeing", "digital_wellbeing" -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.secondary
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.52f))
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(horizontal = 20.dp, vertical = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        AnimatedVisibility(
            visible = visible,
            enter = fadeIn(tween(220)) + scaleIn(
                initialScale = 0.88f,
                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy)
            )
        ) {
            Card(
                modifier = Modifier
                    .widthIn(max = 368.dp)
                    .fillMaxWidth()
                    .testTag("alarm_popup_card"),
                shape = RoundedCornerShape(30.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(2.dp, accentColor.copy(alpha = glowAlpha)),
                elevation = CardDefaults.cardElevation(defaultElevation = 14.dp)
            ) {
                Column(
                    modifier = Modifier
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(
                                    accentColor.copy(alpha = 0.16f),
                                    MaterialTheme.colorScheme.surface,
                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                )
                            )
                        )
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Header status pill & live clock
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = accentColor.copy(alpha = 0.16f)
                        ) {
                            Text(
                                text = "🔔 MINI ALARM POPUP",
                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                color = accentColor,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                        Text(
                            text = payload.timeLabel.ifBlank { currentClockText },
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = JetBrainsMonoMetricFont,
                                fontWeight = FontWeight.Bold
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Pulsing Emoji Orb
                    Box(
                        modifier = Modifier
                            .size(86.dp)
                            .scale(pulseScale)
                            .clip(CircleShape)
                            .background(accentColor.copy(alpha = 0.18f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = payload.emoji,
                            fontSize = 42.sp,
                            modifier = Modifier.testTag("alarm_popup_emoji")
                        )
                    }

                    Text(
                        text = payload.title,
                        style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Text(
                        text = payload.message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.MusicNote,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp),
                                tint = accentColor
                            )
                            Text(
                                text = "${payload.toneLabel} • ${payload.gesturePreset.shortBadge}",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Interactive Emoji Gesture Deck based on chosen alarm gesture preset
                    EmojiGestureDeck(
                        emoji = payload.emoji,
                        preset = payload.gesturePreset,
                        accentColor = accentColor,
                        onCompleteGesture = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onDone()
                        },
                        onSnoozeGesture = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            onSnooze(10)
                        }
                    )

                    // Direct Fallback Action Controls (always 48dp touch-friendly)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = { onSnooze(10) },
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("alarm_popup_snooze_button"),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Snooze,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("😴 10m")
                        }

                        Button(
                            onClick = onDone,
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                                .testTag("alarm_popup_done_button"),
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = accentColor)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CheckCircle,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("✅ Done")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onOpenApp,
                            modifier = Modifier.testTag("alarm_popup_open_app_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.OpenInNew,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Open LifeWeave")
                        }
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("alarm_popup_dismiss_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.AlarmOff,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.size(4.dp))
                            Text("Silence")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmojiGestureDeck(
    emoji: String,
    preset: AlarmEmojiGesturePreset,
    accentColor: Color,
    onCompleteGesture: () -> Unit,
    onSnoozeGesture: () -> Unit
) {
    val haptics = LocalHapticFeedback.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = preset.instruction,
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            when (preset) {
                AlarmEmojiGesturePreset.SWIPE_SLIDE -> {
                    BoxWithConstraints(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surface)
                            .padding(horizontal = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        val density = LocalDensity.current
                        val maxSlidePx = with(density) { (maxWidth - 64.dp).coerceAtLeast(120.dp).toPx() * 0.48f }
                        var dragX by remember { mutableFloatStateOf(0f) }
                        val animatedX by animateFloatAsState(
                            targetValue = dragX,
                            animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                            label = "swipe_emoji_thumb"
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "⬅️ 😴",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (dragX < -maxSlidePx * 0.4f) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "✅ ➡️",
                                style = MaterialTheme.typography.titleMedium,
                                color = if (dragX > maxSlidePx * 0.4f) accentColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Surface(
                            modifier = Modifier
                                .offset { IntOffset(animatedX.roundToInt(), 0) }
                                .size(50.dp)
                                .pointerInput(maxSlidePx) {
                                    detectDragGestures(
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragX = (dragX + dragAmount.x).coerceIn(-maxSlidePx, maxSlidePx)
                                        },
                                        onDragEnd = {
                                            when {
                                                dragX >= maxSlidePx * 0.65f -> onCompleteGesture()
                                                dragX <= -maxSlidePx * 0.65f -> onSnoozeGesture()
                                                else -> dragX = 0f
                                            }
                                        },
                                        onDragCancel = { dragX = 0f }
                                    )
                                }
                                .testTag("emoji_swipe_thumb"),
                            shape = CircleShape,
                            color = accentColor,
                            shadowElevation = 6.dp
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 24.sp)
                            }
                        }
                    }
                }

                AlarmEmojiGesturePreset.HOLD_RING -> {
                    var isHolding by remember { mutableStateOf(false) }
                    var holdProgress by remember { mutableFloatStateOf(0f) }

                    LaunchedEffect(isHolding) {
                        if (isHolding) {
                            val stepMs = 30L
                            val totalMs = 850L
                            var elapsed = 0L
                            while (isHolding && elapsed < totalMs) {
                                delay(stepMs)
                                elapsed += stepMs
                                holdProgress = (elapsed.toFloat() / totalMs.toFloat()).coerceIn(0f, 1f)
                            }
                            if (isHolding && holdProgress >= 1f) {
                                onCompleteGesture()
                            }
                        } else {
                            holdProgress = 0f
                        }
                    }

                    Box(
                        modifier = Modifier
                            .size(84.dp)
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onPress = {
                                        isHolding = true
                                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        tryAwaitRelease()
                                        isHolding = false
                                    }
                                )
                            }
                            .testTag("emoji_hold_orb"),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            progress = { holdProgress },
                            modifier = Modifier.size(80.dp),
                            strokeWidth = 6.dp,
                            color = accentColor,
                            trackColor = MaterialTheme.colorScheme.surface
                        )
                        Surface(
                            modifier = Modifier.size(62.dp),
                            shape = CircleShape,
                            color = if (isHolding) accentColor else MaterialTheme.colorScheme.surface
                        ) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 28.sp)
                            }
                        }
                    }
                }

                AlarmEmojiGesturePreset.DOUBLE_TAP_POP -> {
                    var tapCount by remember { mutableIntStateOf(0) }
                    val burstScale by animateFloatAsState(
                        targetValue = if (tapCount == 1) 1.18f else 1.0f,
                        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
                        label = "tap_burst_scale"
                    )

                    FilledTonalButton(
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            tapCount += 1
                            if (tapCount >= 2) {
                                onCompleteGesture()
                            }
                        },
                        modifier = Modifier
                            .scale(burstScale)
                            .height(56.dp)
                            .testTag("emoji_double_tap_button"),
                        shape = CircleShape
                    ) {
                        Text(text = "$emoji  ", fontSize = 22.sp)
                        Text(
                            text = if (tapCount == 0) "Tap Twice ⚡" else "Tap Once More! ✅",
                            style = MaterialTheme.typography.titleSmall
                        )
                    }
                }
            }
        }
    }
}
