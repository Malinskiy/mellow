package dev.mellow.app.library

import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import dev.mellow.app.screenshot.ScreenshotData
import dev.mellow.app.screenshot.pagingItemsOf
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.home.FavoritesContent
import dev.mellow.feature.home.PlaylistDetailScreen
import dev.mellow.feature.library.LibraryScreen
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Screens whose lists are paged still tell loading, empty and loaded apart. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class PagedListStatesTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `a library with nothing in it says so instead of loading forever`() {
        show { LibraryScreen() }

        composeTestRule.onNodeWithText("No albums yet").assertExists()
    }

    @Test
    fun `a syncing library with nothing yet says it's syncing`() {
        show { LibraryScreen(isSyncing = true) }

        composeTestRule.onNodeWithText("Syncing albums…").assertExists()
    }

    @Test
    fun `the tracks tab shows its tracks`() {
        show { LibraryScreen(tracks = pagingItemsOf(ScreenshotData.trackItems), initialTab = 2) }

        composeTestRule.onNodeWithText("Paranoid Android").assertExists()
    }

    @Test
    fun `favorites with no tracks say so`() {
        show { FavoritesContent(selectedTab = 0) }

        composeTestRule.onNodeWithText("No favorite tracks yet").assertExists()
    }

    @Test
    fun `a playlist with no tracks says so`() {
        show { PlaylistDetailScreen(onBack = {}, playlistName = "Late Night Vibes") }

        composeTestRule.onNodeWithText("No tracks in this playlist").assertExists()
    }

    private fun show(content: @Composable () -> Unit) {
        composeTestRule.setContent { MellowTheme(darkTheme = true) { content() } }
        composeTestRule.waitForIdle()
    }
}
