package dev.mellow.core.designsystem.component.maintenance

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowTheme

private const val CAPTION = "Tidying up your library\u2026"

/** The plexus keeps only this share of the window's shorter side clear around it: its network spreads out. */
private const val PLEXUS_PADDING_FRACTION = 0.02f
private val MIN_PLEXUS_PADDING = 8.dp

/**
 * The screen shown while the library's database is migrated: the plexus settling into the Mellow logo, and a caption,
 * laid out by [MaintenanceLayout] (around the hinge of a half-folded device, from LocalFoldableState).
 *
 * @param clock the plexus's time, for a screen that follows the animation.
 * @param fixedTimeSeconds draw this moment of the plexus and don't animate (screenshot tests).
 */
@Composable
fun DatabaseMaintenanceScreen(
    modifier: Modifier = Modifier,
    clock: PlexusClock = rememberPlexusClock(),
    fixedTimeSeconds: Float? = null,
) {
    Box(modifier = modifier.fillMaxSize().background(MellowTheme.colors.background)) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
            MaintenanceLayout(
                modifier = Modifier.fillMaxSize(),
                graphicPadding = maxOf(MIN_PLEXUS_PADDING, minOf(maxWidth, maxHeight) * PLEXUS_PADDING_FRACTION),
                graphic = { graphicModifier ->
                    DatabaseMaintenancePlexus(
                        // A layer of its own: it redraws every frame, the caption doesn't.
                        modifier = graphicModifier.graphicsLayer(),
                        clock = clock,
                        fixedTimeSeconds = fixedTimeSeconds,
                    )
                },
                caption = { Text(text = CAPTION, color = MellowTheme.colors.foreground) },
            )
        }
    }
}

@Preview(widthDp = 412, heightDp = 915)
@Composable
private fun DatabaseMaintenanceScreenPortraitPreview() {
    MellowTheme(darkTheme = true) {
        DatabaseMaintenanceScreen(fixedTimeSeconds = PLEXUS_LOGO_HOLD_START_SECONDS + 0.5f)
    }
}

@Preview(widthDp = 915, heightDp = 412)
@Composable
private fun DatabaseMaintenanceScreenLandscapePreview() {
    MellowTheme(darkTheme = true) {
        DatabaseMaintenanceScreen(fixedTimeSeconds = PLEXUS_LOGO_HOLD_START_SECONDS + 0.5f)
    }
}
