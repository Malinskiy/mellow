package dev.mellow.app.ui.theme

import androidx.compose.runtime.Composable
import dev.mellow.core.designsystem.theme.MellowTheme as DesignSystemTheme

@Composable
fun MellowTheme(
    darkTheme: Boolean = true,
    content: @Composable () -> Unit,
) {
    DesignSystemTheme(
        darkTheme = darkTheme,
        content = content,
    )
}
