package dev.mellow.app.screenshot

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import dev.mellow.feature.library.LibraryScreen
import org.junit.Test

/** The library's tab bar: at rest, held mid-swipe, and mid-way through a tap's jump across tabs. */
abstract class TabBarScreenshotTests : ScreenshotCapture() {

    @Test
    fun atRest() = capture("library-tabs-rest") {
        LibraryScreen(albumItems = pagingItemsOf(ScreenshotData.albumItems))
    }

    @Test
    fun heldMidSwipe() {
        show { LibraryScreen(albumItems = pagingItemsOf(ScreenshotData.albumItems)) }
        composeTestRule.waitForIdle()
        // A finger 40 % of the way from Albums to Artists, still down: the pill stretched across both.
        composeTestRule.onRoot().performTouchInput {
            down(Offset(width * 0.8f, height * 0.6f))
            moveBy(Offset(-width * 0.2f, 0f))
            moveBy(Offset(-width * 0.2f, 0f))
        }
        composeTestRule.waitForIdle()
        snapshot("library-tabs-mid-swipe-40")
        composeTestRule.onRoot().performTouchInput { up() }
    }

    @Test
    fun midTapJump() {
        show { LibraryScreen(tracks = pagingItemsOf(ScreenshotData.trackItems), initialTab = 2) }
        composeTestRule.waitForIdle()
        composeTestRule.mainClock.autoAdvance = false
        // Tracks to Playlists, 120 of its 320 ms in: the head has nearly arrived, the tail is still on Tracks.
        composeTestRule.onNodeWithText("Playlists").performClick()
        composeTestRule.mainClock.advanceTimeBy(120)
        snapshot("library-tabs-mid-tap-jump")
        composeTestRule.mainClock.autoAdvance = true
    }
}
