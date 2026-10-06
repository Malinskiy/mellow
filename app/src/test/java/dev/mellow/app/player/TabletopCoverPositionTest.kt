package dev.mellow.app.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.player.PlayerLayout
import dev.mellow.feature.player.PlayerScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Tabletop: the cover keeps its place and size whatever the title, so a page turn (which changes the title at
 * release) never shifts it. Pixel 10 Pro Fold half-folded, landscape.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w1023dp-h876dp-420dpi")
class TabletopCoverPositionTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var title by mutableStateOf("Help!")
    private var favourite by mutableStateOf(false)

    @Test
    fun `the cover doesn't move when the title gets longer`() {
        show(topHeight = 395.dp)
        val short = coverBounds()

        title = "The Continuing Story of Bungalow Bill"
        composeTestRule.waitForIdle()
        val long = coverBounds()

        assertEquals(short, long)
    }

    @Test
    fun `the cover doesn't move when the track is favourited`() {
        title = "The Continuing Story of Bungalow Bill"
        show(topHeight = 395.dp)
        val before = coverBounds()

        favourite = true
        composeTestRule.waitForIdle()

        assertEquals(before, coverBounds())
    }

    @Test
    fun `the cover is as tall as before, 70 percent of the space below the top bar`() {
        show(topHeight = 395.dp)
        val cover = coverBounds()

        assertEquals("not square: $cover", (cover.right - cover.left).value, (cover.bottom - cover.top).value, 0.5f)
        assertTrue("cover ${cover.bottom - cover.top}", cover.bottom - cover.top > 200.dp)
    }

    @Test
    @Config(qualifiers = "w600dp-h700dp-420dpi")
    fun `on a narrow tabletop the text gives way, not the cover`() {
        show(topHeight = 395.dp)
        val short = coverBounds()

        title = "The Continuing Story of Bungalow Bill"
        composeTestRule.waitForIdle()
        val long = coverBounds()

        assertEquals(short, long)
        assertEquals("not square: $long", (long.right - long.left).value, (long.bottom - long.top).value, 0.5f)
    }

    private fun coverBounds(): DpRect = composeTestRule.onNodeWithTag("cover").getBoundsInRoot()

    private fun show(topHeight: Dp) {
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) {
                Box(Modifier.fillMaxSize()) {
                    PlayerScreen(
                        embedded = true,
                        layout = PlayerLayout.Tabletop,
                        tabletopTopHeight = topHeight,
                        artModifier = Modifier.testTag("cover"),
                        trackName = title,
                        artistName = "The Beatles",
                        albumName = "The Beatles (disc 1)",
                        isFavorite = favourite,
                        isDownloaded = true,
                    )
                }
            }
        }
        composeTestRule.waitForIdle()
    }
}
