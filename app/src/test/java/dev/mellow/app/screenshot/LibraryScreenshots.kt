package dev.mellow.app.screenshot

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.feature.library.LibraryScreen
import dev.mellow.feature.library.AlbumDetailScreen
import dev.mellow.feature.library.ArtistDetailScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h900dp-xxhdpi")
class LibraryScreenshots : ScreenshotCapture() {

    @Test
    fun libraryScreen() = capture("library") {
        LibraryScreen()
    }

    @Test
    fun albumDetailScreen() = capture("album-detail") {
        AlbumDetailScreen(onBack = {})
    }

    @Test
    fun artistDetailScreen() = capture("artist-detail") {
        ArtistDetailScreen(onBack = {})
    }
}
