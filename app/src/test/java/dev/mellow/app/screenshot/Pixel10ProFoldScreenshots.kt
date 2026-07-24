package dev.mellow.app.screenshot

import androidx.compose.ui.geometry.Rect
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.core.designsystem.theme.DevicePosture
import dev.mellow.core.designsystem.theme.FoldableState
import dev.mellow.core.designsystem.theme.WindowWidthClass

private val FOLD_PORTRAIT_FLAT = FoldableState(
    posture = DevicePosture.Flat,
    hingeBounds = Rect(1038f, 0f, 1038f, 2424f),
    isSeparating = false,
)

private val FOLD_LANDSCAPE_TABLETOP = FoldableState(
    posture = DevicePosture.Tabletop,
    hingeBounds = Rect(0f, 1038f, 2424f, 1038f),
    isSeparating = true,
)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w876dp-h1023dp-420dpi")
class Home_Pixel10ProFoldPortrait : HomeScreenshotTests() {
    override val deviceFolder = "pixel10profold-portrait"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_PORTRAIT_FLAT
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w876dp-h1023dp-420dpi")
class Library_Pixel10ProFoldPortrait : LibraryScreenshotTests() {
    override val deviceFolder = "pixel10profold-portrait"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_PORTRAIT_FLAT
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w876dp-h1023dp-420dpi")
class Player_Pixel10ProFoldPortrait : PlayerScreenshotTests() {
    override val deviceFolder = "pixel10profold-portrait"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_PORTRAIT_FLAT
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w876dp-h1023dp-420dpi")
class Search_Pixel10ProFoldPortrait : SearchScreenshotTests() {
    override val deviceFolder = "pixel10profold-portrait"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_PORTRAIT_FLAT
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w876dp-h1023dp-420dpi")
class Settings_Pixel10ProFoldPortrait : SettingsScreenshotTests() {
    override val deviceFolder = "pixel10profold-portrait"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_PORTRAIT_FLAT
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1023dp-h876dp-420dpi")
class Home_Pixel10ProFoldLandscape : HomeScreenshotTests() {
    override val deviceFolder = "pixel10profold-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_LANDSCAPE_TABLETOP
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1023dp-h876dp-420dpi")
class Library_Pixel10ProFoldLandscape : LibraryScreenshotTests() {
    override val deviceFolder = "pixel10profold-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_LANDSCAPE_TABLETOP
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1023dp-h876dp-420dpi")
class Player_Pixel10ProFoldLandscape : PlayerScreenshotTests() {
    override val deviceFolder = "pixel10profold-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_LANDSCAPE_TABLETOP
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1023dp-h876dp-420dpi")
class Search_Pixel10ProFoldLandscape : SearchScreenshotTests() {
    override val deviceFolder = "pixel10profold-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_LANDSCAPE_TABLETOP
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1023dp-h876dp-420dpi")
class Settings_Pixel10ProFoldLandscape : SettingsScreenshotTests() {
    override val deviceFolder = "pixel10profold-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_LANDSCAPE_TABLETOP
}
