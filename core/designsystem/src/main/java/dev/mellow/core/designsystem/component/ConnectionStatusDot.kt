package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowPalette

@Composable
fun ConnectionStatusDot(
    isConnected: Boolean,
    isServerUnreachable: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = when {
        isConnected -> MellowPalette.Green500
        isServerUnreachable -> MellowPalette.Amber500
        else -> MellowPalette.Stone500
    }
    Box(
        modifier = modifier
            .size(8.dp)
            .clip(CircleShape)
            .background(color),
    )
}
