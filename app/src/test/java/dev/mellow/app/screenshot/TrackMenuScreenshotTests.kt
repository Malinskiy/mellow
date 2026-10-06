package dev.mellow.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import dev.mellow.core.designsystem.component.TrackContextMenuContent
import dev.mellow.core.designsystem.component.TrackMenuData
import dev.mellow.core.designsystem.component.TrackMenuDownload
import dev.mellow.core.designsystem.theme.MellowTheme
import org.junit.Test

/** The track menu's body (the bottom sheet's content) in each state of its download entry. */
abstract class TrackMenuScreenshotTests : ScreenshotCapture() {

    @Test
    fun downloadAvailable() = captureMenu("track-menu-download-available", TrackMenuDownload.Available)

    @Test
    fun downloaded() = captureMenu("track-menu-downloaded", TrackMenuDownload.Downloaded)

    @Test
    fun downloading() = captureMenu("track-menu-downloading", TrackMenuDownload.Downloading)

    @Test
    fun offline() = captureMenu("track-menu-offline", TrackMenuDownload.Offline)

    @Test
    fun storageFull() = captureMenu("track-menu-storage-full", TrackMenuDownload.StorageFull)

    private fun captureMenu(targetId: String, download: TrackMenuDownload) = capture(targetId) {
        Box(
            contentAlignment = Alignment.BottomCenter,
            modifier = Modifier
                .fillMaxSize()
                .background(MellowTheme.colors.background),
        ) {
            TrackContextMenuContent(
                track = TrackMenuData(
                    id = "track",
                    title = "Midnight City",
                    artist = "M83",
                    album = "Hurry Up, We're Dreaming",
                    albumId = "album",
                    artistId = "artist",
                    imageUrl = null,
                    isFavorite = true,
                    download = download,
                ),
                onDismiss = {},
                onPlayNext = {},
                onAddToQueue = {},
                onAddToPlaylist = {},
                onGoToAlbum = {},
                onGoToArtist = {},
                onStartMix = {},
                onToggleFavorite = {},
                onDownload = {},
                onCancelDownload = {},
                onRemoveDownload = {},
                onTrackInfo = {},
                modifier = Modifier.background(MellowTheme.colors.surfaceElevated),
            )
        }
    }
}
