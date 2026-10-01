package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.indication
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

enum class MellowNavDestination(
    val route: String,
    val label: String,
    val icon: ImageVector,
) {
    Home("home", "Home", PhosphorIcons.House),
    Library("library", "Library", PhosphorIcons.VinylRecord),
    Search("search", "Search", PhosphorIcons.MagnifyingGlass),
    Favorites("favorites", "Favorites", PhosphorIcons.Heart),
}

@Composable
fun MellowBottomNavBar(
    selectedRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.navigationBarsPadding()) {
        HorizontalDivider(color = MellowTheme.colors.border, thickness = 1.dp)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(MellowSpacing.BottomNavHeight)
                .background(MellowTheme.colors.background)
                .padding(horizontal = MellowSpacing.Sp6)
                .selectableGroup(),
        ) {
            MellowNavDestination.entries.forEach { dest ->
                val isSelected = dest.route == selectedRoute
                val tint = if (isSelected) MellowTheme.colors.foreground else MellowTheme.colors.muted
                val interactionSource = remember { MutableInteractionSource() }

                // The whole cell (full bar height, equal share of the width) is the touch target;
                // the ripple stays on the icon + label pill. Like M3's NavigationBarItem, the cell is a
                // selectable tab, and the icon carries no description because the label already says it.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .selectable(
                            selected = isSelected,
                            onClick = { onNavigate(dest.route) },
                            role = Role.Tab,
                            interactionSource = interactionSource,
                            indication = null,
                        ),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .indication(interactionSource, ripple())
                            .padding(horizontal = MellowSpacing.Sp3, vertical = MellowSpacing.Sp1),
                    ) {
                        Icon(
                            imageVector = dest.icon,
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(22.dp),
                        )
                        Box(modifier = Modifier.height(4.dp))
                        Text(
                            text = dest.label,
                            fontSize = 11.sp,
                            color = tint,
                        )
                    }
                }
            }
        }
    }
}
