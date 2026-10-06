package dev.mellow.app.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import dev.mellow.app.navigation.ExpandablePlayerSheet
import dev.mellow.app.navigation.ExpandableSheetState
import dev.mellow.app.navigation.rememberExpandableSheetState
import dev.mellow.core.designsystem.component.PageTurnCover
import dev.mellow.core.designsystem.theme.MellowTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A drag down that starts on the expanded cover collapses the real player sheet, as one that starts elsewhere does. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class CoverSheetCollapseTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var sheet: ExpandableSheetState
    private val calls = mutableListOf<String>()

    @Test
    fun `a drag down on the cover collapses the sheet, following the finger`() {
        showExpanded()
        val start = sheet.offset()

        composeTestRule.onNodeWithTag("cover").performTouchInput {
            down(Offset(centerX, height * 0.2f))
            repeat(10) { moveBy(Offset(0f, 20f), delayMillis = 16) }
        }
        composeTestRule.waitForIdle()
        // Past the 20 dp decision the sheet has caught up with the finger: 200 px down, none of it lost.
        assertEquals(start + 200f, sheet.offset(), 1f)

        composeTestRule.onNodeWithTag("cover").performTouchInput {
            repeat(10) { moveBy(Offset(0f, 40f), delayMillis = 16) }
            up()
        }
        composeTestRule.waitForIdle()

        assertFalse("the sheet is still expanded", sheet.isExpanded)
        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a short slow drag down on the cover lets the sheet spring back open`() {
        showExpanded()

        composeTestRule.onNodeWithTag("cover").performTouchInput {
            down(Offset(centerX, height * 0.2f))
            repeat(10) { moveBy(Offset(0f, 8f), delayMillis = 16) }
            advanceEventTime(300)
            up()
        }
        composeTestRule.waitForIdle()

        assertTrue(sheet.isExpanded)
        assertEquals(0f, sheet.offset(), 0.5f)
    }

    private fun ExpandableSheetState.offset() = anchoredState.offset

    private fun showExpanded() {
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) {
                sheet = rememberExpandableSheetState()
                ExpandablePlayerSheet(
                    sheetState = sheet,
                    trackName = "Reckoner",
                    artistName = "Radiohead",
                    albumImageUrl = null,
                    isPlaying = false,
                    isBuffering = false,
                    progress = 0f,
                    onPlayPause = {},
                    onSkipNext = {},
                    playerContent = { _, _, _ ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            PageTurnCover(
                                current = null,
                                modifier = Modifier.size(320.dp).testTag("cover"),
                                trackKey = "reckoner",
                                canGoNext = true,
                                canGoPrevious = true,
                                onNext = { calls += "next" },
                                onPrevious = { calls += "previous" },
                                onSheetDrag = sheet::dragBy,
                                onSheetDragEnd = sheet::endDrag,
                            )
                        }
                    },
                    queueContent = {},
                    lyricsContent = {},
                    bottomNavHeightPx = 0f,
                )
            }
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { sheet.expand() }
        composeTestRule.waitForIdle()
        assertTrue("the sheet didn't open", sheet.isExpanded)
    }
}
