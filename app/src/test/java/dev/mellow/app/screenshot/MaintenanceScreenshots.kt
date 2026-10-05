package dev.mellow.app.screenshot

import androidx.compose.ui.geometry.Rect
import dev.mellow.core.designsystem.component.maintenance.DatabaseMaintenanceRepressing
import dev.mellow.core.designsystem.component.maintenance.DatabaseMaintenanceScreen
import dev.mellow.core.designsystem.component.maintenance.PLEXUS_LOGO_HOLD_END_SECONDS
import dev.mellow.core.designsystem.component.maintenance.PLEXUS_LOGO_HOLD_START_SECONDS
import dev.mellow.core.designsystem.theme.DevicePosture
import dev.mellow.core.designsystem.theme.FoldableState
import dev.mellow.core.designsystem.theme.WindowWidthClass
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** The middle of the plexus's logo hold. */
private const val HOLD_FRAME_SECONDS = (PLEXUS_LOGO_HOLD_START_SECONDS + PLEXUS_LOGO_HOLD_END_SECONDS) / 2f

/** Every point still drifting, before the first starts to settle. */
private const val DRIFT_FRAME_SECONDS = 0.5f

/** The re-pressing preview mid-cycle: the arm lowered, about half the grooves pressed. */
private const val REPRESSING_FRACTION = 0.45f

/**
 * The maintenance screen at the logo hold and mid-drift, and the re-pressing preview mid-cycle; ScreenshotCapture
 * provides the fold.
 */
abstract class MaintenanceScreenshotTests : ScreenshotCapture() {

    @Test
    fun maintenanceHold() = capture("maintenance-hold") {
        DatabaseMaintenanceScreen(fixedTimeSeconds = HOLD_FRAME_SECONDS)
    }

    @Test
    fun maintenanceDrift() = capture("maintenance-drift") {
        DatabaseMaintenanceScreen(fixedTimeSeconds = DRIFT_FRAME_SECONDS)
    }

    @Test
    fun maintenanceRepressing() = capture("maintenance-repressing") {
        DatabaseMaintenanceRepressing(fixedTimeFraction = REPRESSING_FRACTION)
    }
}

// The unfolded Pixel 10 Pro Fold, 2300 x 2685 px in portrait: its fold runs down (or, turned, across) the middle.
private val FOLD_FLAT_PORTRAIT = FoldableState(DevicePosture.Flat, Rect(1150f, 0f, 1150f, 2685f), isSeparating = false)
private val FOLD_FLAT_LANDSCAPE = FoldableState(DevicePosture.Flat, Rect(0f, 1150f, 2685f, 1150f), isSeparating = false)
private val FOLD_TABLETOP = FoldableState(DevicePosture.Tabletop, Rect(0f, 1150f, 2685f, 1150f), isSeparating = true)
private val FOLD_BOOK = FoldableState(DevicePosture.Book, Rect(1150f, 0f, 1150f, 2685f), isSeparating = true)

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class Maintenance_Pixel10Portrait : MaintenanceScreenshotTests() {
    override val deviceFolder = "pixel10-portrait"
    override val windowWidthClass = WindowWidthClass.Compact
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w915dp-h412dp-xxhdpi")
class Maintenance_Pixel10Landscape : MaintenanceScreenshotTests() {
    override val deviceFolder = "pixel10-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h640dp-xhdpi")
class Maintenance_SmallPhonePortrait : MaintenanceScreenshotTests() {
    override val deviceFolder = "small-phone-portrait"
    override val windowWidthClass = WindowWidthClass.Compact
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w411dp-h900dp-420dpi")
class Maintenance_Pixel10ProFoldOuter : MaintenanceScreenshotTests() {
    override val deviceFolder = "pixel10profold-outer"
    override val windowWidthClass = WindowWidthClass.Compact
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w876dp-h1023dp-420dpi")
class Maintenance_Pixel10ProFoldPortrait : MaintenanceScreenshotTests() {
    override val deviceFolder = "pixel10profold-portrait"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_FLAT_PORTRAIT
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1023dp-h876dp-420dpi")
class Maintenance_Pixel10ProFoldLandscape : MaintenanceScreenshotTests() {
    override val deviceFolder = "pixel10profold-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_FLAT_LANDSCAPE
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1023dp-h876dp-420dpi")
class Maintenance_Pixel10ProFoldTabletop : MaintenanceScreenshotTests() {
    override val deviceFolder = "pixel10profold-tabletop"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_TABLETOP
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w876dp-h1023dp-420dpi")
class Maintenance_Pixel10ProFoldBook : MaintenanceScreenshotTests() {
    override val deviceFolder = "pixel10profold-book"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val foldableState = FOLD_BOOK
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w800dp-h1280dp-xhdpi")
class Maintenance_PixelTabletPortrait : MaintenanceScreenshotTests() {
    override val deviceFolder = "pixel-tablet-portrait"
    override val windowWidthClass = WindowWidthClass.Expanded
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class Maintenance_PixelTabletLandscape : MaintenanceScreenshotTests() {
    override val deviceFolder = "pixel-tablet-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
}
