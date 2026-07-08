package dev.mellow.app.screenshot

import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.feature.settings.LoginScreen
import dev.mellow.feature.settings.SettingsScreen

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h900dp-xxhdpi")
class SettingsScreenshots : ScreenshotCapture() {

    @Test
    fun loginScreen() = capture("login") {
        LoginScreen()
    }

    @Test
    fun settingsScreen() = capture("settings") {
        SettingsScreen()
    }
}
