package dev.mellow.app.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.component.PageTurnTarget
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.player.CoverSwipe
import dev.mellow.feature.player.PlayerLayout
import dev.mellow.feature.player.PlayerScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The expanded player's Next and Previous buttons skip through the cover's page turn, press by press. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class PlayerButtonsPageTurnTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var index by mutableIntStateOf(3)
    private val calls = mutableListOf<String>()

    @Test
    fun `five quick Next presses skip five times and end on the fifth track`() {
        show()
        composeTestRule.mainClock.autoAdvance = false

        repeat(5) {
            composeTestRule.onNodeWithContentDescription("Next").performClick()
            composeTestRule.mainClock.advanceTimeBy(48)
        }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        assertEquals(List(5) { "next" }, calls)
        assertEquals(8, index)
    }

    @Test
    fun `Previous that restarts the track skips once and changes nothing`() {
        show(previousRestarts = true)

        composeTestRule.onNodeWithContentDescription("Previous").performClick()
        composeTestRule.waitForIdle()

        assertEquals(listOf("previous"), calls)
        assertEquals(3, index)
    }

    @Test
    fun `a button press while a finger turns the page skips and leaves the page to the finger`() {
        show()

        composeTestRule.onNodeWithTag("art").performTouchInput {
            down(Offset(width * 0.8f, centerY))
            repeat(8) { moveBy(Offset(-25f, 0f), delayMillis = 16) }
        }
        // A click action, not a tap: the finger is still down on the cover.
        composeTestRule.onNodeWithContentDescription("Next").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.onNodeWithTag("art").performTouchInput {
            repeat(4) { moveBy(Offset(-25f, 0f), delayMillis = 16) }
            up()
        }
        composeTestRule.waitForIdle()

        // The button skipped; the swipe saw the track change under it and didn't skip again.
        assertEquals(listOf("next"), calls)
        assertEquals(4, index)
        composeTestRule.onNodeWithContentDescription("Next").performClick()
        composeTestRule.waitForIdle()
        assertEquals(5, index)
    }

    private fun show(previousRestarts: Boolean = false) {
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) {
                Box(Modifier.size(412.dp, 915.dp)) {
                    PlayerScreen(
                        embedded = true,
                        layout = PlayerLayout.Compact,
                        artModifier = Modifier.testTag("art"),
                        trackName = "Track $index",
                        albumImageUrl = "cover-$index",
                        onSkipNextClick = { calls += "next (no turn)" },
                        onSkipPreviousClick = { calls += "previous (no turn)" },
                        coverSwipe = CoverSwipe(
                            trackKey = "track-$index",
                            nextImageUrl = "cover-${index + 1}",
                            previousImageUrl = "cover-${index - 1}",
                            canGoNext = true,
                            canGoPrevious = true,
                            onNext = { calls += "swipe next" },
                            onPrevious = { calls += "swipe previous" },
                            buttonNext = {
                                calls += "next"
                                index++
                                PageTurnTarget("track-$index", "cover-$index")
                            },
                            buttonPrevious = {
                                calls += "previous"
                                if (previousRestarts) {
                                    null
                                } else {
                                    index--
                                    PageTurnTarget("track-$index", "cover-$index")
                                }
                            },
                        ),
                    )
                }
            }
        }
    }
}
