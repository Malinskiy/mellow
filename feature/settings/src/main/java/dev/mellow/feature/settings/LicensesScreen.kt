package dev.mellow.feature.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.mikepenz.aboutlibraries.entity.Library
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.MellowSpacing

@Composable
fun LicensesScreen(
    communityLibraries: List<Library>,
    platformLibraries: List<Library>,
    modifier: Modifier = Modifier,
    onBack: () -> Unit = {},
) {
    val context = LocalContext.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MellowTheme.colors.background),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp3),
        ) {
            IconButton(onClick = onBack) {
                Icon(PhosphorIcons.ArrowLeft, "Back", tint = MellowTheme.colors.foreground)
            }
            Text(
                "Open Source Licenses",
                style = MaterialTheme.typography.headlineLarge,
                color = MellowTheme.colors.foreground,
            )
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(communityLibraries, key = { it.uniqueId }) { library ->
                LibraryRow(
                    name = library.name,
                    version = library.artifactVersion,
                    licenseName = library.licenses.firstOrNull()?.name,
                    onClick = libraryUrl(library)?.let { url ->
                        { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    },
                )
                HorizontalDivider(color = MellowTheme.colors.border)
            }
            item {
                SectionHeader("Platform")
            }
            items(platformLibraries, key = { it.uniqueId }) { library ->
                LibraryRow(
                    name = library.name,
                    version = library.artifactVersion,
                    licenseName = library.licenses.firstOrNull()?.name,
                    onClick = libraryUrl(library)?.let { url ->
                        { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                    },
                )
                HorizontalDivider(color = MellowTheme.colors.border)
            }
        }
    }
}

private fun libraryUrl(library: Library): String? =
    library.website?.takeIf { it.isNotBlank() }
        ?: library.scm?.url?.takeIf { it.isNotBlank() }

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MellowTheme.colors.muted,
        modifier = Modifier.padding(start = MellowSpacing.Sp4, top = MellowSpacing.Sp6, bottom = MellowSpacing.Sp2),
    )
}

@Composable
private fun LibraryRow(
    name: String,
    version: String?,
    licenseName: String?,
    onClick: (() -> Unit)?,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = MellowTheme.colors.foreground,
                )
                if (!version.isNullOrBlank()) {
                    Spacer(Modifier.width(MellowSpacing.Sp2))
                    Text(
                        version,
                        style = MaterialTheme.typography.bodySmall,
                        color = MellowTheme.colors.muted,
                    )
                }
            }
            if (!licenseName.isNullOrBlank()) {
                Spacer(Modifier.height(2.dp))
                Text(
                    licenseName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MellowTheme.colors.muted,
                )
            }
        }
        if (onClick != null) {
            Icon(
                PhosphorIcons.CaretRight,
                contentDescription = null,
                tint = MellowTheme.colors.muted,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
