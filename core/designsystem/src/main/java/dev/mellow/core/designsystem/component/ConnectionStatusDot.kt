package dev.mellow.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlinx.coroutines.delay

@Composable
fun ConnectionStatusDot(
    isConnected: Boolean,
    isServerUnreachable: Boolean,
    modifier: Modifier = Modifier,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
) {
    val hasError = error != null

    val dotColor = when {
        hasError -> MellowPalette.Red500
        isConnected -> MellowPalette.Green500
        isServerUnreachable -> MellowPalette.Amber500
        else -> MellowPalette.Stone500
    }

    val label = when {
        hasError -> error
        isServerUnreachable -> "Unreachable"
        !isConnected -> "Offline"
        else -> null
    }

    var visibleLabel by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(label) {
        if (label != null) {
            visibleLabel = label
            delay(LABEL_DISPLAY_MS)
            visibleLabel = null
        } else {
            visibleLabel = null
        }
    }

    val dotSizeDp = 8.dp

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .layout { measurable, constraints ->
                val placeable = measurable.measure(constraints.copy(minWidth = 0))
                val dotPx = dotSizeDp.roundToPx()
                layout(dotPx, placeable.height) {
                    placeable.place(dotPx - placeable.width, 0)
                }
            },
    ) {
        AnimatedContent(
            targetState = visibleLabel,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "connectionLabel",
        ) { text ->
            if (text != null) {
                Row(
                    modifier = if (onRetry != null) {
                        Modifier
                            .clip(MellowShapes.Full)
                            .clickable(onClick = onRetry)
                            .padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp1)
                    } else {
                        Modifier
                    },
                ) {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.labelSmall,
                        color = MellowTheme.colors.muted,
                    )
                    Spacer(Modifier.width(MellowSpacing.Sp2))
                }
            }
        }
        Box(
            modifier = Modifier
                .size(dotSizeDp)
                .clip(CircleShape)
                .background(dotColor),
        )
    }
}

private const val LABEL_DISPLAY_MS = 4000L
