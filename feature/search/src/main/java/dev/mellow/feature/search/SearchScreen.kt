package dev.mellow.feature.search

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.component.EmptyContent
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

        EmptyContent("Search your music library")
    }
}
