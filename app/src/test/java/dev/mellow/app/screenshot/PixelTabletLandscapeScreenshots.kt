package dev.mellow.app.screenshot

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.core.designsystem.theme.WindowWidthClass

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class Home_PixelTabletLandscape : HomeScreenshotTests() {
    override val deviceFolder = "pixel-tablet-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class Library_PixelTabletLandscape : LibraryScreenshotTests() {
    override val deviceFolder = "pixel-tablet-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class Player_PixelTabletLandscape : PlayerScreenshotTests() {
    override val deviceFolder = "pixel-tablet-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class Search_PixelTabletLandscape : SearchScreenshotTests() {
    override val deviceFolder = "pixel-tablet-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class Settings_PixelTabletLandscape : SettingsScreenshotTests() {
    override val deviceFolder = "pixel-tablet-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}
