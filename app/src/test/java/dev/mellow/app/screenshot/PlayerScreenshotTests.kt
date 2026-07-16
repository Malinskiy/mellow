package dev.mellow.app.screenshot

import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.junit.Test
import dev.mellow.feature.player.LyricsScreen
import dev.mellow.feature.player.PlayerScreen
import dev.mellow.feature.player.QueueScreen

abstract class PlayerScreenshotTests : ScreenshotCapture() {

    @Test
    fun playerEmpty() = capture("player-empty") {
        PlayerScreen()
    }

    @Test
    fun playerPlaying() = capture("player-playing") {
        PlayerScreen(
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

    @Test
    fun playerPlayingWithQueue() = capture("player-playing-queue") {
        PlayerScreen(
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

    @Test
    fun playerError() = capture("player-error") {
        PlayerScreen(error = "Connection timed out")
    }

    @Test
    fun queueEmpty() = capture("queue-empty") {
        QueueScreen(onBack = {})
    }

    @Test
    fun queuePopulated() = capture("queue-populated") {
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
