package dev.mellow.app.screenshot

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.core.designsystem.theme.WindowWidthClass

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class Home_PixelTabletPortrait : HomeScreenshotTests() {
    override val deviceFolder = "pixel-tablet-portrait"
    override val windowWidthClass = WindowWidthClass.Medium
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class Library_PixelTabletPortrait : LibraryScreenshotTests() {
    override val deviceFolder = "pixel-tablet-portrait"
    override val windowWidthClass = WindowWidthClass.Medium
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class Player_PixelTabletPortrait : PlayerScreenshotTests() {
    override val deviceFolder = "pixel-tablet-portrait"
    override val windowWidthClass = WindowWidthClass.Medium
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class Search_PixelTabletPortrait : SearchScreenshotTests() {
    override val deviceFolder = "pixel-tablet-portrait"
    override val windowWidthClass = WindowWidthClass.Medium
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class Settings_PixelTabletPortrait : SettingsScreenshotTests() {
    override val deviceFolder = "pixel-tablet-portrait"
    override val windowWidthClass = WindowWidthClass.Medium
}
