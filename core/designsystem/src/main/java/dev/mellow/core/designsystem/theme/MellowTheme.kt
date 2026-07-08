package dev.mellow.core.designsystem.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

val LocalMellowColors = staticCompositionLocalOf { mellowDarkColorScheme }

private val MellowMaterial3Dark = darkColorScheme(
    background = MellowPalette.Stone950,
    surface = MellowPalette.Stone900,
    surfaceVariant = MellowPalette.Stone800,
    surfaceContainerLowest = MellowPalette.Stone950,
    surfaceContainerLow = MellowPalette.Stone900,
    surfaceContainer = MellowPalette.Stone800,
    surfaceContainerHigh = MellowPalette.Stone700,
    surfaceContainerHighest = MellowPalette.Stone600,
    onBackground = MellowPalette.Stone100,
    onSurface = MellowPalette.Stone100,
    onSurfaceVariant = MellowPalette.Stone400,
    primary = MellowPalette.Stone200,
    onPrimary = MellowPalette.Stone950,
    primaryContainer = MellowPalette.Stone800,
    onPrimaryContainer = MellowPalette.Stone200,
    secondary = MellowPalette.Stone400,
    onSecondary = MellowPalette.Stone950,
    outline = MellowPalette.Stone700,
    outlineVariant = MellowPalette.Stone800,
    inverseSurface = MellowPalette.Stone100,
    inverseOnSurface = MellowPalette.Stone900,
)

private val MellowMaterial3Light = lightColorScheme(
    background = MellowPalette.Stone50,
    surface = MellowPalette.Stone100,
    surfaceVariant = MellowPalette.Stone200,
    onBackground = MellowPalette.Stone900,
    onSurface = MellowPalette.Stone900,
    onSurfaceVariant = MellowPalette.Stone500,
    primary = MellowPalette.Stone800,
    onPrimary = MellowPalette.Stone50,
    primaryContainer = MellowPalette.Stone200,
    onPrimaryContainer = MellowPalette.Stone800,
    secondary = MellowPalette.Stone600,
    onSecondary = MellowPalette.Stone50,
    outline = MellowPalette.Stone400,
    outlineVariant = MellowPalette.Stone300,
    inverseSurface = MellowPalette.Stone900,
    inverseOnSurface = MellowPalette.Stone100,
)

@Composable
fun MellowTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val mellowColors = if (darkTheme) mellowDarkColorScheme else mellowLightColorScheme
    val materialScheme = if (darkTheme) MellowMaterial3Dark else MellowMaterial3Light

    CompositionLocalProvider(LocalMellowColors provides mellowColors) {
        MaterialTheme(
            colorScheme = materialScheme,
            typography = mellowTypography,
            content = content,
        )
    }
}

object MellowTheme {
    val colors: MellowColorScheme
        @Composable
        @ReadOnlyComposable
        get() = LocalMellowColors.current

    val spacing: MellowSpacing
        get() = MellowSpacing

    val shapes: MellowShapes
        get() = MellowShapes
}
