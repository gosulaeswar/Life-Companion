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
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

enum class ThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark")
}

private val LightColorScheme = lightColorScheme(
    primary = BotanicalSagePrimary,
    onPrimary = Color.White,
    primaryContainer = BotanicalSageContainer,
    onPrimaryContainer = OnBotanicalSageContainer,
    secondary = MineralTealSecondary,
    onSecondary = Color.White,
    secondaryContainer = MineralTealContainer,
    onSecondaryContainer = OnMineralTealContainer,
    tertiary = TerracottaRoseTertiary,
    onTertiary = Color.White,
    tertiaryContainer = TerracottaRoseContainer,
    onTertiaryContainer = OnTerracottaRoseContainer,
    background = WarmLinenBackground,
    onBackground = DeepForestInk,
    surface = SoftAlabasterSurface,
    onSurface = DeepForestInk,
    surfaceVariant = OatSurfaceVariant,
    onSurfaceVariant = MutedPineInk,
    outline = SandOutline,
    outlineVariant = CardHairlineLight,
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8FBFA),
    surfaceContainer = Color(0xFFF1F6F5),
    error = ErrorCoralLight,
    onError = Color.White,
    errorContainer = Color(0xFFFEE4E2),
    onErrorContainer = Color(0xFF7A271A)
)

private val DarkColorScheme = darkColorScheme(
    primary = SoftSagePrimary,
    onPrimary = Color(0xFF042920),
    primaryContainer = DarkSageContainer,
    onPrimaryContainer = OnDarkSageContainer,
    secondary = MistAquaSecondary,
    onSecondary = Color(0xFF032838),
    secondaryContainer = DarkTealContainer,
    onSecondaryContainer = OnDarkTealContainer,
    tertiary = WarmCoralTertiary,
    onTertiary = Color(0xFF47120D),
    tertiaryContainer = DarkTerracottaContainer,
    onTertiaryContainer = OnDarkTerracottaContainer,
    background = DeepPineBackground,
    onBackground = WarmIvoryInk,
    surface = MutedMossSurface,
    onSurface = WarmIvoryInk,
    surfaceVariant = SlateOliveSurfaceVariant,
    onSurfaceVariant = SoftSageMutedInk,
    outline = PineOutline,
    outlineVariant = SlateOliveSurfaceVariant,
    surfaceContainerLowest = Color(0xFF08100E),
    surfaceContainerLow = Color(0xFF0F1B18),
    surfaceContainer = Color(0xFF152420),
    error = ErrorCoralDark,
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF6B1D18),
    onErrorContainer = Color(0xFFFFDAD6)
)

val LifeCompanionShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(32.dp)
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LifeCompanionTypography,
        shapes = LifeCompanionShapes,
        content = content
    )
}

private tailrec fun Context.findActivityWindow(): android.view.Window? = when (this) {
    is Activity -> window
    is ContextWrapper -> baseContext.findActivityWindow()
    else -> null
}
