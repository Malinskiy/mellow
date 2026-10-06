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
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Swiping the now-playing cover: which drags turn the page, which ones are left to the sheet underneath. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class PageTurnCoverGestureTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()
    private var parentDrag = 0f

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
    fun `a vertical drag is left to the sheet and never skips`() {
        show()

        cover { swipe(Offset(centerX, height * 0.2f), Offset(centerX + 40f, height * 0.9f), durationMillis = 200) }

        assertEquals(emptyList<String>(), calls)
        assertTrue("the sheet didn't get the drag: $parentDrag", parentDrag > 300f)
    }

    @Test
    fun `a diagonal drag that is not clearly sideways is left to the sheet`() {
        show()

        cover { swipe(Offset(width * 0.8f, height * 0.2f), Offset(width * 0.3f, height * 0.85f), durationMillis = 200) }

        assertEquals(emptyList<String>(), calls)
        assertTrue("the sheet didn't get the drag: $parentDrag", parentDrag > 300f)
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

    private fun show(canGoNext: Boolean = true, canGoPrevious: Boolean = true) {
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
                    )
                }
            }
        }
    }
}
