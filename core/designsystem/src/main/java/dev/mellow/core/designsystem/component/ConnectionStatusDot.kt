package dev.mellow.core.designsystem.component

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlinx.coroutines.delay

private const val LABEL_DISPLAY_MS = 4000L

@Composable
fun ConnectionCloudIcon(
    isConnected: Boolean,
    isServerUnreachable: Boolean,
    modifier: Modifier = Modifier,
    error: String? = null,
    onRetry: (() -> Unit)? = null,
    isFilterActive: Boolean = false,
    onToggleFilter: (() -> Unit)? = null,
) {
    val hasError = error != null

    val targetColor = when {
        hasError -> MellowTheme.colors.error
        isServerUnreachable -> MellowTheme.colors.warning
        !isConnected -> MellowTheme.colors.muted
        else -> MellowTheme.colors.online
    }

    val iconColor by animateColorAsState(
        targetValue = targetColor,
        animationSpec = tween(durationMillis = 300),
        label = "cloudIconColor",
    )

    val transientLabel = when {
        hasError -> error
        isServerUnreachable -> "Unreachable"
        !isConnected -> "Offline"
        else -> null
    }

    val labelColor = when {
        hasError -> MellowTheme.colors.error
        isServerUnreachable -> MellowTheme.colors.warning
        !isConnected -> MellowTheme.colors.muted
        else -> MellowTheme.colors.online
    }

    val currentFilterActive by rememberUpdatedState(isFilterActive)

    var showLabel by remember { mutableStateOf(false) }
    var labelText by remember { mutableStateOf("") }
    var labelColorState by remember { mutableStateOf(Color.Unspecified) }
    var previousTransient by remember { mutableStateOf(transientLabel) }

    LaunchedEffect(transientLabel) {
        if (transientLabel != previousTransient) {
            previousTransient = transientLabel
            if (transientLabel != null) {
                labelText = transientLabel
                labelColorState = labelColor
                showLabel = true
                delay(LABEL_DISPLAY_MS)
                showLabel = false
            } else {
                showLabel = false
            }
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        AnimatedContent(
            targetState = if (showLabel) labelText else "",
            transitionSpec = { fadeIn(tween(400)) togetherWith fadeOut(tween(400)) },
            label = "cloudLabel",
        ) { text ->
            if (text.isNotEmpty()) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = labelColorState,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(end = 4.dp),
                )
            }
        }

        IconButton(
            onClick = {
                when {
                    hasError && onRetry != null -> onRetry()
                    onToggleFilter != null -> onToggleFilter()
                }
            },
        ) {
            AnimatedContent(
                targetState = currentFilterActive,
                transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                label = "cloudShape",
            ) { filterOn ->
                Icon(
                    imageVector = if (filterOn) PhosphorIcons.CloudSlash else PhosphorIcons.CloudCheck,
                    contentDescription = if (filterOn) "Downloaded only" else "All content",
                    tint = iconColor,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
    }
}
