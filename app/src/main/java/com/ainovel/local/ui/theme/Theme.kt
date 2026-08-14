package com.ainovel.local.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val LightScheme = lightColorScheme(
    primary = MineralBlue,
    onPrimary = OnMineralBlue,
    primaryContainer = MineralBlueContainer,
    onPrimaryContainer = OnMineralBlueContainer,
    secondary = QuietTeal,
    onSecondary = OnQuietTeal,
    secondaryContainer = QuietTealContainer,
    onSecondaryContainer = OnQuietTealContainer,
    tertiary = SignalAmber,
    tertiaryContainer = SignalAmberContainer,
    error = ErrorRed,
    background = CoolCanvas,
    onBackground = Ink,
    surface = CoolSurface,
    onSurface = Ink,
    surfaceDim = CoolSurfaceDim,
    surfaceBright = CoolSurfaceBright,
    surfaceContainerLowest = CoolSurfaceContainerLowest,
    surfaceContainerLow = CoolSurfaceContainerLow,
    surfaceContainer = CoolSurfaceContainer,
    surfaceContainerHigh = CoolSurfaceContainerHigh,
    surfaceContainerHighest = CoolSurfaceContainerHighest,
    surfaceVariant = CoolSurfaceVariant,
    onSurfaceVariant = CoolOnSurfaceVariant,
    outline = CoolOutline,
    outlineVariant = CoolOutlineVariant,
)

private val DarkScheme = darkColorScheme(
    primary = DarkMineralBlue,
    onPrimary = DarkOnMineralBlue,
    primaryContainer = DarkMineralBlueContainer,
    onPrimaryContainer = DarkOnMineralBlueContainer,
    secondary = DarkQuietTeal,
    onSecondary = DarkOnQuietTeal,
    background = DarkCanvas,
    onBackground = DarkInk,
    surface = DarkSurface,
    onSurface = DarkInk,
    surfaceDim = DarkSurfaceDim,
    surfaceBright = DarkSurfaceBright,
    surfaceContainerLowest = DarkSurfaceContainerLowest,
    surfaceContainerLow = DarkSurfaceContainerLow,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    surfaceContainerHighest = DarkSurfaceContainerHighest,
    surfaceVariant = DarkSurfaceContainerHighest,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
)

@Composable
fun LocalNovelTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkScheme else LightScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = AppTypography,
        content = content,
    )
}
