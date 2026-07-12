package dev.mellow.core.designsystem.theme

import androidx.compose.runtime.staticCompositionLocalOf

enum class WindowWidthClass {
    /** Phone portrait (<600dp) */
    Compact,
    /** Phone landscape, small tablet (600–839dp) */
    Medium,
    /** Tablet, desktop (≥840dp) */
    Expanded,
}

val LocalWindowWidthClass = staticCompositionLocalOf { WindowWidthClass.Compact }
