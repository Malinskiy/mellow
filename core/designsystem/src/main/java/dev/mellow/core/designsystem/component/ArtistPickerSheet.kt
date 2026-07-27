package dev.mellow.core.designsystem.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.mellow.core.designsystem.icon.PhosphorIcons
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme

data class PickerArtist(
    val id: String,
    val name: String,
    val imageUrl: String?,
    val albumCount: Int,
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistPickerSheet(
    artists: List<PickerArtist>,
    onArtistClick: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MellowTheme.colors.surfaceElevated,
        contentColor = MellowTheme.colors.foreground,
        dragHandle = {
            Spacer(
                modifier = Modifier
                    .padding(vertical = MellowSpacing.Sp3)
                    .size(width = 36.dp, height = 4.dp)
                    .background(MellowTheme.colors.muted.copy(alpha = 0.4f), MellowShapes.Full),
            )
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = MellowSpacing.Sp8),
        ) {
            Text(
                "Go to Artist",
                style = MaterialTheme.typography.titleMedium,
                color = MellowTheme.colors.foreground,
                modifier = Modifier.padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp2),
            )

            LazyColumn {
                items(artists, key = { it.id }) { artist ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onArtistClick(artist.id)
                                onDismiss()
                            }
                            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(MellowShapes.Full)
                                .background(MellowTheme.colors.surface),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                PhosphorIcons.User,
                                contentDescription = null,
                                tint = MellowTheme.colors.muted,
                                modifier = Modifier.size(20.dp),
                            )
                            AsyncImage(
                                model = artist.imageUrl,
                                contentDescription = artist.name,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(MellowShapes.Full),
                            )
                        }
                        Spacer(Modifier.width(MellowSpacing.Sp3))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = artist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                color = MellowTheme.colors.foreground,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                text = "${artist.albumCount} ${if (artist.albumCount == 1) "album" else "albums"}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MellowTheme.colors.muted,
                            )
                        }
                        Icon(
                            PhosphorIcons.CaretRight,
                            contentDescription = null,
                            tint = MellowTheme.colors.muted,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}
