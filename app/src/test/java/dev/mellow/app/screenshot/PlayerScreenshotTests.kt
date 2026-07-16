package dev.mellow.app.screenshot

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Test
import dev.mellow.core.designsystem.component.ArtworkBackground
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.player.LyricsScreen
import dev.mellow.feature.player.PlayerScreen
import dev.mellow.feature.player.QueueScreen

abstract class PlayerScreenshotTests : ScreenshotCapture() {

    @Composable
    private fun ExpandedSheet(content: @Composable () -> Unit) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MellowTheme.colors.background),
        ) {
            ArtworkBackground(
                artworkKey = "sheet-bg",
                imageUrl = null,
                modifier = Modifier.fillMaxSize(),
                blurRadius = 120.dp,
                imageAlpha = 0.35f,
                overlayColors = listOf(0.5f to 0f, 0.85f to 1f),
            )
            content()
        }
    }

    @Test
    fun playerExpanded() = capture("player-expanded") {
        ExpandedSheet {
            PlayerScreen(
                embedded = true,
                trackName = "Reckoner",
                artistName = "Radiohead",
                albumName = "In Rainbows",
                albumImageUrl = "https://example.com/art.jpg",
                isPlaying = true,
                progress = 0.4f,
                positionMs = 120000L,
                durationMs = 300000L,
                codec = "flac",
            )
        }
    }

    @Test
    fun playerExpandedWithQueue() = capture("player-expanded-queue") {
        ExpandedSheet {
            PlayerScreen(
                embedded = true,
                trackName = "Reckoner",
                artistName = "Radiohead",
                albumName = "In Rainbows",
                albumImageUrl = "https://example.com/art.jpg",
                isPlaying = true,
                progress = 0.4f,
                positionMs = 120000L,
                durationMs = 300000L,
                codec = "flac",
                sidePanelContent = {
                    QueueScreen(
                        onBack = {},
                        embedded = true,
                        nowPlaying = ScreenshotData.queueNowPlaying,
                        upNext = ScreenshotData.queueUpNext,
                        currentAlbumName = "In Rainbows",
                        modifier = Modifier.width(400.dp).fillMaxHeight(),
                    )
                },
            )
        }
    }

    @Test
    fun playerError() = capture("player-error") {
        ExpandedSheet {
            PlayerScreen(
                embedded = true,
                trackName = "Reckoner",
                artistName = "Radiohead",
                albumName = "In Rainbows",
                error = "Connection timed out",
            )
        }
    }

    @Test
    fun queuePage() = capture("queue-page") {
        QueueScreen(onBack = {})
    }

    @Test
    fun queuePagePopulated() = capture("queue-page-populated") {
        QueueScreen(
            onBack = {},
            nowPlaying = ScreenshotData.queueNowPlaying,
            upNext = ScreenshotData.queueUpNext,
            currentAlbumName = "In Rainbows",
        )
    }

    @Test
    fun lyricsLoading() = capture("lyrics-loading") {
        LyricsScreen(
            isLoadingLyrics = true,
            trackName = "Mr. Tambourine Man",
            artistName = "Bob Dylan",
        )
    }

    @Test
    fun lyricsEmpty() = capture("lyrics-empty") {
        LyricsScreen(
            trackName = "Mr. Tambourine Man",
            artistName = "Bob Dylan",
        )
    }

    @Test
    fun lyricsSynced() = capture("lyrics-synced") {
        LyricsScreen(
            lyrics = ScreenshotData.syncedLyrics,
            trackName = "Mr. Tambourine Man",
            artistName = "Bob Dylan",
            positionMs = 28000L,
            durationMs = 180000L,
            isPlaying = true,
        )
    }
}
