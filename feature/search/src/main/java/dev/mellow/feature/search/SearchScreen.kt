package dev.mellow.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.component.GenreCard
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

@Composable
fun SearchScreen(modifier: Modifier = Modifier) {
    var query by rememberSaveable { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        Text(
            text = "Search",
            style = MaterialTheme.typography.headlineLarge,
            color = MellowTheme.colors.foreground,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
        )

        TextField(
            value = query,
            onValueChange = { query = it },
            placeholder = { Text("Search library…", color = MellowPalette.Stone600) },
            leadingIcon = { Icon(Icons.Filled.Search, null, tint = MellowTheme.colors.muted) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { query = "" }) {
                        Icon(Icons.Filled.Close, "Clear", tint = MellowTheme.colors.muted)
                    }
                }
            },
            shape = MellowShapes.Full,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = MellowTheme.colors.surface,
                unfocusedContainerColor = MellowTheme.colors.surface,
                focusedTextColor = MellowTheme.colors.foreground,
                unfocusedTextColor = MellowTheme.colors.foreground,
                cursorColor = MellowTheme.colors.foreground,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = MellowSpacing.Sp4)
                .border(1.dp, MellowTheme.colors.border, MellowShapes.Full),
        )

        Spacer(Modifier.height(MellowSpacing.Sp6))

        if (query.isEmpty()) {
            RecentSearches()
            BrowseCategories()
        }
    }
}

@Composable
private fun RecentSearches() {
    Column(modifier = Modifier.padding(horizontal = MellowSpacing.Sp4)) {
        Text("RECENT", style = MaterialTheme.typography.labelSmall, color = MellowTheme.colors.muted)
        Spacer(Modifier.height(MellowSpacing.Sp3))
        listOf("Radiohead", "dark side of the moon", "tame impala currents").forEach { search ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = MellowSpacing.Sp2),
            ) {
                Icon(Icons.Filled.History, null, tint = MellowTheme.colors.muted, modifier = Modifier.size(20.dp))
                Text(
                    text = search,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MellowTheme.colors.foreground,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = MellowSpacing.Sp3),
                )
            }
        }
    }
}

@Composable
private fun BrowseCategories() {
    Column(modifier = Modifier.padding(top = MellowSpacing.Sp6)) {
        Text(
            "BROWSE",
            style = MaterialTheme.typography.labelSmall,
            color = MellowTheme.colors.muted,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp4),
        )
        Spacer(Modifier.height(MellowSpacing.Sp3))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            contentPadding = PaddingValues(horizontal = MellowSpacing.Sp4),
            horizontalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
            verticalArrangement = Arrangement.spacedBy(MellowSpacing.Sp3),
        ) {
            items(listOf("Rock" to "42", "Electronic" to "28", "Hip-Hop" to "19", "Jazz" to "15", "Classical" to "11", "Ambient" to "8")) { (name, count) ->
                GenreCard(name = name, count = "$count albums", onClick = {})
            }
        }
    }
}
