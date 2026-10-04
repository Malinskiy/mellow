package dev.mellow.app.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.getBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.player.PlayerPlaybackControls

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w800dp-h915dp")
class PlayerPlaybackControlsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun repeatButtonFitsOnSmallPhone() = assertAllButtonsFit(360.dp)

    @Test
    fun repeatButtonFitsOnLargePhone() = assertAllButtonsFit(412.dp)

    private fun assertAllButtonsFit(screenWidth: Dp) {
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) {
                Box(Modifier.width(screenWidth)) {
                    PlayerPlaybackControls(
                        isPlaying = false,
                        onPlayPauseClick = {},
                        onSkipPreviousClick = {},
                        onSkipNextClick = {},
                    )
                }
            }
        }

        for (button in listOf("Shuffle", "Previous", "Next", "Repeat")) {
            val bounds = composeTestRule.onNodeWithContentDescription(button, useUnmergedTree = true)
                .getBoundsInRoot()
            val width = bounds.right - bounds.left
            assertTrue("$button is squeezed: $width wide", width >= 22.dp)
            assertTrue("$button ends past the screen edge: ${bounds.right}", bounds.right <= screenWidth)
        }
    }
}
