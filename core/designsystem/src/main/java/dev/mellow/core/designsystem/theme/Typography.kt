package dev.mellow.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

object MellowFonts {
    val Display: FontFamily = FontFamily.SansSerif
    val Body: FontFamily = FontFamily.SansSerif
    val Mono: FontFamily = FontFamily.Monospace
}

val mellowTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = MellowFonts.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 36.sp,
        letterSpacing = (-0.03).em,
    ),
    displayMedium = TextStyle(
        fontFamily = MellowFonts.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        letterSpacing = (-0.02).em,
    ),
    displaySmall = TextStyle(
        fontFamily = MellowFonts.Display,
        fontWeight = FontWeight.SemiBold,
        fontSize = 24.sp,
        letterSpacing = (-0.02).em,
    ),

    headlineLarge = TextStyle(
        fontFamily = MellowFonts.Display,
        fontWeight = FontWeight.Medium,
        fontSize = 22.sp,
        letterSpacing = (-0.02).em,
    ),
    headlineMedium = TextStyle(
        fontFamily = MellowFonts.Display,
        fontWeight = FontWeight.Medium,
        fontSize = 18.sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = MellowFonts.Display,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),

    titleLarge = TextStyle(
        fontFamily = MellowFonts.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 16.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = MellowFonts.Body,
        fontWeight = FontWeight(450),
        fontSize = 15.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = MellowFonts.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
    ),

    bodyLarge = TextStyle(
        fontFamily = MellowFonts.Body,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = MellowFonts.Body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = MellowFonts.Body,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),

    labelLarge = TextStyle(
        fontFamily = MellowFonts.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 15.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = MellowFonts.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = MellowFonts.Body,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
    ),
)

private val Float.em: androidx.compose.ui.unit.TextUnit
    get() = (this * 16).sp

private val Double.em: androidx.compose.ui.unit.TextUnit
    get() = (this * 16).sp
