package com.example.ui.theme

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

val LocalIsSpatialDarkTheme = compositionLocalOf { false }

private val LightColorScheme = lightColorScheme(
    primary = SolarOrangePrimary,
    onPrimary = Color.White,
    primaryContainer = SolarOrangeContainer,
    onPrimaryContainer = OnSolarOrangeContainer,
    secondary = CobaltBlueSecondary,
    onSecondary = Color.White,
    secondaryContainer = CobaltBlueContainer,
    onSecondaryContainer = OnCobaltBlueContainer,
    tertiary = ElectricAzureTertiary,
    onTertiary = Color.White,
    tertiaryContainer = ElectricAzureContainer,
    onTertiaryContainer = OnElectricAzureContainer,
    background = SpatialLightBase,
    onBackground = DeepCobaltInk,
    surface = SpatialGlassLight,
    onSurface = DeepCobaltInk,
    surfaceVariant = SpatialGlassVariantLight,
    onSurfaceVariant = MutedSlateBlueInk,
    outline = SpatialGlassOutlineLight,
    outlineVariant = SpatialGlassOutlineLight.copy(alpha = 0.72f),
    surfaceContainerLowest = Color(0xF5FFFFFF),
    surfaceContainerLow = Color(0xE0F6FAFF),
    surfaceContainer = Color(0xD8ECF3FF),
    error = ErrorCoralLight,
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A)
)

private val DarkColorScheme = darkColorScheme(
    primary = RadiantOrangePrimary,
    onPrimary = Color(0xFF3A1200),
    primaryContainer = DarkOrangeContainer,
    onPrimaryContainer = OnDarkOrangeContainer,
    secondary = LuminousBlueSecondary,
    onSecondary = Color(0xFF061B42),
    secondaryContainer = DarkCobaltContainer,
    onSecondaryContainer = OnDarkCobaltContainer,
    tertiary = SunsetAmberTertiary,
    onTertiary = Color(0xFF3C2300),
    tertiaryContainer = DarkAmberContainer,
    onTertiaryContainer = OnDarkAmberContainer,
    background = SpatialDarkBase,
    onBackground = SpatialSnowInk,
    surface = SpatialGlassDark,
    onSurface = SpatialSnowInk,
    surfaceVariant = SpatialGlassVariantDark,
    onSurfaceVariant = SpatialMistInk,
    outline = SpatialGlassOutlineDark,
    outlineVariant = SpatialGlassOutlineDark.copy(alpha = 0.78f),
    surfaceContainerLowest = Color(0xD908132B),
    surfaceContainerLow = Color(0xCC0D1B38),
    surfaceContainer = Color(0xC8132447),
    error = ErrorCoralDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF6B1D18),
    onErrorContainer = Color(0xFFFFDAD6)
)

val LifeCompanionShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(22.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(34.dp)
)

@Composable
fun LifeCompanionTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    content: @Composable () -> Unit
) {
    val darkTheme = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
    }

    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = view.context.findActivityWindow()
            if (window != null) {
                val controller = WindowCompat.getInsetsController(window, view)
                controller.isAppearanceLightStatusBars = !darkTheme
                controller.isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(LocalIsSpatialDarkTheme provides darkTheme) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = LifeCompanionTypography,
            shapes = LifeCompanionShapes,
            content = content
        )
    }
}

private tailrec fun Context.findActivityWindow(): android.view.Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findActivityWindow()
    else -> null
}
