package dev.mellow.app.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.TouchInjectionScope
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.component.PageTurnCover
import dev.mellow.core.designsystem.theme.MellowTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Swiping the now-playing cover: which drags turn the page, which ones go to the sheet. The cover sits in a parent
 * with its own vertical drag, standing in for the sheet's: it must never start on a gesture that began on the cover.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class PageTurnCoverGestureTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private var parentDrag = 0f
    private var sheetDrag = 0f
    private val sheetDragEnds = mutableListOf<Float>()

    @Test
    fun `a swipe left turns to the next track once`() {
        show()

        cover { swipe(Offset(width * 0.85f, centerY), Offset(width * 0.15f, centerY), durationMillis = 180) }

        assertEquals(listOf("next"), calls)
    }

    @Test
    fun `a swipe right turns back to the previous track once`() {
        show()

        cover { swipe(Offset(width * 0.15f, centerY), Offset(width * 0.9f, centerY), durationMillis = 180) }

        assertEquals(listOf("previous"), calls)
    }

    @Test
    fun `a short drag let go slowly falls back without skipping`() {
        show()

        cover {
            down(Offset(width * 0.8f, centerY))
            repeat(10) { moveBy(Offset(-8f, 0f), delayMillis = 30) }
            advanceEventTime(250)
            up()
        }

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a wobble that ends where it started never skips`() {
        show()

        cover {
            down(Offset(width * 0.6f, centerY))
            repeat(6) { moveBy(Offset(-25f, 0f), delayMillis = 16) }
            repeat(6) { moveBy(Offset(25f, 0f), delayMillis = 16) }
            repeat(4) { moveBy(Offset(-10f, 0f), delayMillis = 16) }
            repeat(4) { moveBy(Offset(10f, 0f), delayMillis = 16) }
            up()
        }

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a right-thumb arc 45 degrees down and to the left turns to the next track, not the sheet`() {
        show()

        cover { thumbArc(start = Offset(width * 0.85f, height * 0.3f), angleBelow = 45f, lengthPx = 450f) }

        assertEquals(listOf("next"), calls)
        assertEquals(0f, sheetDrag, 0f)
        assertEquals(0, sheetDragEnds.size)
        assertEquals("the sheet's own drag started", 0f, parentDrag, 0f)
    }

    @Test
    fun `a drag straight down goes to the sheet, all of it, and never skips`() {
        show()

        cover {
            down(Offset(centerX, height * 0.2f))
            repeat(20) { moveBy(Offset(0f, 30f), delayMillis = 10) }
            up()
        }

        assertEquals(emptyList<String>(), calls)
        assertEquals("the sheet didn't follow the finger all the way", 600f, sheetDrag, 0.5f)
        assertEquals(1, sheetDragEnds.size)
        assertTrue("let go at ${sheetDragEnds.single()} px/s", sheetDragEnds.single() > 1000f)
        assertEquals("the sheet's own drag started", 0f, parentDrag, 0f)
    }

    @Test
    fun `while undecided the cover holds the gesture so the sheet's own drag never starts`() {
        show()

        cover {
            down(Offset(centerX, height * 0.3f))
            // 45 px (~16 dp) down: past the sheet's touch slop, short of the cover's decision.
            repeat(5) { moveBy(Offset(0f, 9f), delayMillis = 16) }
            up()
        }

        assertEquals(0f, parentDrag, 0f)
        assertEquals(0f, sheetDrag, 0f)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `without a sheet a drag down is let go and turns nothing`() {
        show(withSheet = false)

        cover { swipe(Offset(centerX, height * 0.2f), Offset(centerX, height * 0.9f), durationMillis = 200) }

        assertEquals(emptyList<String>(), calls)
        assertEquals(0f, sheetDrag, 0f)
    }

    @Test
    fun `at the end of the queue a swipe left does nothing`() {
        show(canGoNext = false)

        cover { swipe(Offset(width * 0.85f, centerY), Offset(width * 0.05f, centerY), durationMillis = 120) }

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `at the start of the queue a swipe right does nothing`() {
        show(canGoPrevious = false)

        cover { swipe(Offset(width * 0.1f, centerY), Offset(width * 0.95f, centerY), durationMillis = 120) }

        assertEquals(emptyList<String>(), calls)
    }

    private fun cover(block: TouchInjectionScope.() -> Unit) {
        composeTestRule.onNodeWithTag("cover").performTouchInput(block)
        composeTestRule.waitForIdle()
    }

    /** A right thumb swiping left: heading [angleBelow]° below horizontal, steepening around a pivot below. */
    private fun TouchInjectionScope.thumbArc(start: Offset, angleBelow: Float, lengthPx: Float) {
        val radius = 1400f // ≈470 dp, about a thumb's reach
        val phi = Math.toRadians(angleBelow.toDouble())
        val pivot = Offset(start.x + (radius * sin(phi)).toFloat(), start.y + (radius * cos(phi)).toFloat())
        val startAngle = atan2((start.y - pivot.y).toDouble(), (start.x - pivot.x).toDouble())
        down(start)
        val steps = 15
        for (i in 1..steps) {
            val a = startAngle - lengthPx / radius * i / steps
            val point = Offset(pivot.x + (radius * cos(a)).toFloat(), pivot.y + (radius * sin(a)).toFloat())
            moveTo(point, delayMillis = 10)
        }
        up()
    }

    private fun show(canGoNext: Boolean = true, canGoPrevious: Boolean = true, withSheet: Boolean = true) {
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) {
                Box(
                    Modifier
                        .size(412.dp, 800.dp)
                        .draggable(rememberDraggableState { parentDrag += it }, Orientation.Vertical),
                ) {
                    PageTurnCover(
                        current = null,
                        modifier = Modifier.size(320.dp).testTag("cover"),
                        trackKey = "current",
                        canGoNext = canGoNext,
                        canGoPrevious = canGoPrevious,
                        onNext = { calls += "next" },
                        onPrevious = { calls += "previous" },
                        onSheetDrag = if (withSheet) { delta -> sheetDrag += delta } else null,
                        onSheetDragEnd = { sheetDragEnds += it },
                    )
                }
            }
        }
    }
}
