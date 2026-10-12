package dev.mellow.app.screenshot

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.core.designsystem.theme.WindowWidthClass

/**
 * A car head unit (e.g. Joying 8.8"/10.25"): 1280 × 480 at 160 dpi (1280 × 480 dp), less 80 dp for the status and
 * navigation bars a dialog keeps clear of.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h400dp-mdpi")
class UpdateDialog_HeadUnitLandscape : UpdateDialogScreenshotTests() {
    override val deviceFolder = "head-unit-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}
