package dev.mellow.feature.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import dev.mellow.core.designsystem.component.EmptyContent
import dev.mellow.core.designsystem.component.MellowTabBar
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

private val TABS = listOf("Tracks", "Albums", "Artists")

@Composable
fun FavoritesScreen(modifier: Modifier = Modifier) {
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        Text(
            "Favorites",
            style = MaterialTheme.typography.headlineLarge,
            color = MellowTheme.colors.foreground,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
        )

        MellowTabBar(
            tabs = TABS,
            selectedIndex = selectedTab,
            onTabSelected = { selectedTab = it },
            modifier = Modifier.padding(bottom = MellowSpacing.Sp4),
        )

        when (selectedTab) {
            0 -> EmptyContent("No favorite tracks yet")
            1 -> EmptyContent("No favorite albums yet")
            2 -> EmptyContent("No favorite artists yet")
        }
    }
}
