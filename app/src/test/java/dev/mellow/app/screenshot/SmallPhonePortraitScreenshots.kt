package dev.mellow.app.screenshot

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.core.designsystem.theme.WindowWidthClass

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class Player_SmallPhonePortrait : PlayerScreenshotTests() {
    override val deviceFolder = "small-phone-portrait"
    override val windowWidthClass = WindowWidthClass.Compact
}
