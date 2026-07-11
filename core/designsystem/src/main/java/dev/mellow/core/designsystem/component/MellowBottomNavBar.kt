package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import dev.mellow.core.designsystem.icon.PhosphorIcons
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
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
    Column(modifier = modifier) {
        HorizontalDivider(color = MellowTheme.colors.border, thickness = 1.dp)
        Row(
            horizontalArrangement = Arrangement.SpaceAround,
            modifier = Modifier
                .fillMaxWidth()
                .height(MellowSpacing.BottomNavHeight)
                .background(MellowTheme.colors.background)
                .padding(top = MellowSpacing.Sp2, start = MellowSpacing.Sp6, end = MellowSpacing.Sp6),
        ) {
            MellowNavDestination.entries.forEach { dest ->
                val isSelected = dest.route == selectedRoute
                val tint = if (isSelected) MellowTheme.colors.foreground else MellowTheme.colors.muted

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onNavigate(dest.route) }
                        .padding(horizontal = MellowSpacing.Sp3, vertical = MellowSpacing.Sp1),
                ) {
                    Icon(
                        imageVector = dest.icon,
                        contentDescription = dest.label,
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
