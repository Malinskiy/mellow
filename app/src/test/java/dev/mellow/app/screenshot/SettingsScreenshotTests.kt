package dev.mellow.app.screenshot

import org.junit.Test
import dev.mellow.core.network.ConnectionState
import dev.mellow.feature.settings.LoginScreen
import dev.mellow.feature.settings.SettingsScreen

abstract class SettingsScreenshotTests : ScreenshotCapture() {

    @Test
    fun loginDefault() = capture("login-default") {
        LoginScreen()
    }

    @Test
    fun loginLoading() = capture("login-loading") {
        LoginScreen(isLoading = true)
    }

    @Test
    fun loginError() = capture("login-error") {
        LoginScreen(error = "Invalid username or password")
    }

    @Test
    fun settingsDefault() = capture("settings-default") {
        SettingsScreen(
            serverUrl = "https://jellyfin.example.com",
            connectionState = ConnectionState.Connected,
        )
    }

    @Test
    fun settingsSyncing() = capture("settings-syncing") {
        SettingsScreen(
            serverUrl = "https://jellyfin.example.com",
            connectionState = ConnectionState.Connected,
            isSyncing = true,
        )
    }
}
