package dev.mellow.app.screenshot

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.core.designsystem.theme.WindowWidthClass

/**
 * A short phone: 1080 × 2340 at 450 dpi (384 × 832 dp, e.g. a Samsung Galaxy), less 80 dp for the status and
 * navigation bars the player sheet keeps clear of.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w384dp-h752dp-450dpi")
class Player_ShortPhonePortrait : PlayerScreenshotTests() {
    override val deviceFolder = "short-phone-portrait"
    override val windowWidthClass = WindowWidthClass.Compact
}
