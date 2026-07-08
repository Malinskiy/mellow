package dev.mellow.core.designsystem.theme

import androidx.compose.ui.graphics.Color

object MellowPalette {
    val Stone50 = Color(0xFFFAFAF9)
    val Stone100 = Color(0xFFF5F5F4)
    val Stone200 = Color(0xFFE7E5E4)
    val Stone300 = Color(0xFFD6D3D1)
    val Stone400 = Color(0xFFA8A29E)
    val Stone500 = Color(0xFF78716C)
    val Stone600 = Color(0xFF57534E)
    val Stone700 = Color(0xFF44403C)
    val Stone800 = Color(0xFF292524)
    val Stone900 = Color(0xFF1C1917)
    val Stone950 = Color(0xFF0C0A09)

    val Amber500 = Color(0xFFF59E0B)
    val Red500 = Color(0xFFEF4444)
    val Green500 = Color(0xFF22C55E)
}

data class MellowColorScheme(
    val background: Color,
    val surface: Color,
    val surfaceElevated: Color,
    val foreground: Color,
    val muted: Color,
    val border: Color,
    val accent: Color,
    val accentStrong: Color,
    val favorite: Color,
    val online: Color,
)

val mellowDarkColorScheme = MellowColorScheme(
    background = MellowPalette.Stone950,
    surface = MellowPalette.Stone900,
    surfaceElevated = Color(0xFF231F1D),
    foreground = MellowPalette.Stone100,
    muted = MellowPalette.Stone400,
    border = MellowPalette.Stone800,
    accent = MellowPalette.Stone400,
    accentStrong = MellowPalette.Stone300,
    favorite = MellowPalette.Red500,
    online = MellowPalette.Green500,
)

val mellowLightColorScheme = MellowColorScheme(
    background = MellowPalette.Stone50,
    surface = MellowPalette.Stone100,
    surfaceElevated = MellowPalette.Stone200,
    foreground = MellowPalette.Stone900,
    muted = MellowPalette.Stone500,
    border = MellowPalette.Stone300,
    accent = MellowPalette.Stone500,
    accentStrong = MellowPalette.Stone700,
    favorite = MellowPalette.Red500,
    online = MellowPalette.Green500,
)
