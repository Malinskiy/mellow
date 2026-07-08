package dev.mellow.app.screenshot

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.feature.player.PlayerScreen
import dev.mellow.feature.player.QueueScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h900dp-xxhdpi")
class PlayerScreenshots : ScreenshotCapture() {

    @Test
    fun nowPlayingScreen() = capture("now-playing") {
        PlayerScreen()
    }

    @Test
    fun queueScreen() = capture("queue") {
        QueueScreen(onBack = {})
    }
}
