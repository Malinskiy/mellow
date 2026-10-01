package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.ViewCompat
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun MellowNavigationRail(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Vertical extent of the destination group, in window coordinates. Only the horizontal
    // padding depends on it, so measuring it cannot feed back into itself.
    var itemsVerticalRange by remember { mutableStateOf<ClosedFloatingPointRange<Float>?>(null) }
    val cutoutHitsItems = startCutoutOverlaps(itemsVerticalRange)

    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxHeight()
                .background(MellowTheme.colors.surface)
                // Only the start edge belongs to the rail; a side navigation bar or cutout on the
                // end edge is handled by the content. The inset is added to the 80dp, not taken from it.
                // A start-edge cutout only pushes the rail in when it actually overlaps the
                // destinations (e.g. a centred hole-punch), not for a corner cutout above or below them.
                .windowInsetsPadding(
                    if (cutoutHitsItems) {
                        WindowInsets.systemBars.union(WindowInsets.displayCutout)
                    } else {
                        WindowInsets.systemBars.union(WindowInsets.displayCutout.only(WindowInsetsSides.Vertical))
                    }.only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
                )
                .width(80.dp),
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInWindow()
                    itemsVerticalRange = bounds.top..bounds.bottom
                },
            ) {
                MellowNavDestination.entries.forEach { dest ->
                    val isSelected = dest.route == selectedRoute
                    val tint = if (isSelected) MellowTheme.colors.foreground else MellowTheme.colors.muted

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .then(
                                if (isSelected) {
                                    Modifier.background(MellowPalette.Stone800)
                                } else {
                                    Modifier
                                }
                            )
                            .clickable { onNavigate(dest.route) }
                            .padding(vertical = 4.dp),
                    ) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = dest.label,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = dest.label,
                            fontSize = 10.sp,
                            color = tint,
                            letterSpacing = 0.02.sp,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.dp)
                .background(MellowTheme.colors.border),
        )
    }
}

/**
 * Whether a display cutout on the rail's start edge overlaps the given vertical range (window px).
 *
 * The cutout inset is a band along the whole edge, but the cutout itself is usually small; this checks
 * its real bounding rects. Defaults to `true` (inset applied) while the range is still unknown or the
 * bounding rects are unavailable, so controls never start out underneath the cutout.
 */
@Composable
private fun startCutoutOverlaps(verticalRange: ClosedFloatingPointRange<Float>?): Boolean {
    val view = LocalView.current
    val density = LocalDensity.current
    val layoutDirection = LocalLayoutDirection.current
    val cutout = WindowInsets.displayCutout
    // Reading the inset here subscribes to changes (rotation, cutout emulation toggles).
    val startInsetPx = if (layoutDirection == LayoutDirection.Ltr) {
        cutout.getLeft(density, layoutDirection)
    } else {
        cutout.getRight(density, layoutDirection)
    }
    if (startInsetPx == 0) return false

    val rects = remember(startInsetPx, view) {
        ViewCompat.getRootWindowInsets(view)?.displayCutout?.boundingRects.orEmpty()
    }
    if (verticalRange == null || rects.isEmpty()) return true

    val bandStart = if (layoutDirection == LayoutDirection.Ltr) 0 else view.width - startInsetPx
    val bandEnd = bandStart + startInsetPx
    return rects.any { r ->
        r.right > bandStart && r.left < bandEnd &&
            r.bottom > verticalRange.start && r.top < verticalRange.endInclusive
    }
}
