package dev.mellow.app.screenshot

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.core.designsystem.theme.WindowWidthClass

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w915dp-h412dp-xxhdpi")
class Home_Pixel10Landscape : HomeScreenshotTests() {
    override val deviceFolder = "pixel10-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w915dp-h412dp-xxhdpi")
class Library_Pixel10Landscape : LibraryScreenshotTests() {
    override val deviceFolder = "pixel10-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w915dp-h412dp-xxhdpi")
class Player_Pixel10Landscape : PlayerScreenshotTests() {
    override val deviceFolder = "pixel10-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w915dp-h412dp-xxhdpi")
class Search_Pixel10Landscape : SearchScreenshotTests() {
    override val deviceFolder = "pixel10-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w915dp-h412dp-xxhdpi")
class Settings_Pixel10Landscape : SettingsScreenshotTests() {
    override val deviceFolder = "pixel10-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}
