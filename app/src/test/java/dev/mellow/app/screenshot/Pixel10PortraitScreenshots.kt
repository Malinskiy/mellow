package dev.mellow.app.screenshot

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.core.designsystem.theme.WindowWidthClass

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class Home_Pixel10Portrait : HomeScreenshotTests() {
    override val deviceFolder = "pixel10-portrait"
    override val windowWidthClass = WindowWidthClass.Compact
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class Library_Pixel10Portrait : LibraryScreenshotTests() {
    override val deviceFolder = "pixel10-portrait"
    override val windowWidthClass = WindowWidthClass.Compact
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class Player_Pixel10Portrait : PlayerScreenshotTests() {
    override val deviceFolder = "pixel10-portrait"
    override val windowWidthClass = WindowWidthClass.Compact
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class Search_Pixel10Portrait : SearchScreenshotTests() {
    override val deviceFolder = "pixel10-portrait"
    override val windowWidthClass = WindowWidthClass.Compact
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class Settings_Pixel10Portrait : SettingsScreenshotTests() {
    override val deviceFolder = "pixel10-portrait"
    override val windowWidthClass = WindowWidthClass.Compact
}
