package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

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
    outlineVariant = OatSurfaceVariant,
    error = ErrorCoralLight
)

private val DarkColorScheme = darkColorScheme(
    primary = SoftSagePrimary,
    onPrimary = Color(0xFF0A2E23),
    primaryContainer = DarkSageContainer,
    onPrimaryContainer = OnDarkSageContainer,
    secondary = MistAquaSecondary,
    onSecondary = Color(0xFF082E3B),
    secondaryContainer = DarkTealContainer,
    onSecondaryContainer = OnDarkTealContainer,
    tertiary = WarmCoralTertiary,
    onTertiary = Color(0xFF471812),
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
    error = ErrorCoralDark
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

    MaterialTheme(
        colorScheme = colorScheme,
        typography = LifeCompanionTypography,
        shapes = LifeCompanionShapes,
        content = content
    )
}
