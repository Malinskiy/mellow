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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun MellowNavigationRail(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxHeight()
                .background(MellowTheme.colors.surface)
                // Only the start edge belongs to the rail; a side navigation bar or cutout on the
                // end edge is handled by the content. The inset is added to the 80dp, not taken from it.
                .windowInsetsPadding(
                    WindowInsets.systemBars
                        .union(WindowInsets.displayCutout)
                        .only(WindowInsetsSides.Start + WindowInsetsSides.Vertical),
                )
                .width(80.dp),
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

        Box(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(1.dp)
                .background(MellowTheme.colors.border),
        )
    }
}
