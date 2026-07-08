package dev.mellow.app.screenshot

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.feature.search.SearchScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h900dp-xxhdpi")
class SearchScreenshots : ScreenshotCapture() {

    @Test
    fun searchScreen() = capture("search") {
        SearchScreen()
    }
}
