package dev.mellow.app.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.player.PlayerLayout
import dev.mellow.feature.player.PlayerScreen

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h915dp")
class PlayerCompactArtTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun artClearsTitleOnNarrowShortPhone() = assertArtAboveTitle(320.dp, 569.dp)

    @Test
    fun artClearsTitleOnSmallPhone() = assertArtAboveTitle(360.dp, 640.dp)

    @Test
    fun artClearsTitleOnLargePhone() = assertArtAboveTitle(412.dp, 915.dp)

    private fun assertArtAboveTitle(width: Dp, height: Dp) {
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) {
                Box(Modifier.size(width, height)) {
                    PlayerScreen(
                        embedded = true,
                        layout = PlayerLayout.Compact,
                        trackName = "Reckoner",
                        artistName = "Radiohead",
                        albumName = "In Rainbows",
                        artModifier = Modifier.testTag("album-art"),
                    )
                }
            }
        }

        val art = composeTestRule.onNodeWithTag("album-art", useUnmergedTree = true).unclippedBounds()
        val title = composeTestRule.onNodeWithText("Reckoner", useUnmergedTree = true).unclippedBounds()
        // Compose coerces a node's reported size to its constraints, so art that is too tall for its slot
        // shows up here as a non-square box while the image still draws as a centered square over its neighbours.
        assertEquals("Album art is not square: $art", art.right - art.left, art.bottom - art.top)
        assertTrue("Album art bottom ${art.bottom} overlaps title top ${title.top}", art.bottom <= title.top)
    }

    private fun SemanticsNodeInteraction.unclippedBounds(): DpRect {
        val node = fetchSemanticsNode()
        return with(composeTestRule.density) {
            DpRect(
                left = node.positionInRoot.x.toDp(),
                top = node.positionInRoot.y.toDp(),
                right = (node.positionInRoot.x + node.size.width).toDp(),
                bottom = (node.positionInRoot.y + node.size.height).toDp(),
            )
        }
    }
}
