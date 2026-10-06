package dev.mellow.app

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import dev.mellow.core.designsystem.component.TrackContextMenuContent
import dev.mellow.core.designsystem.component.TrackMenuData
import dev.mellow.core.designsystem.component.TrackMenuDownload
import dev.mellow.core.designsystem.theme.MellowTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The track menu's download entry: disabled when it can't download, and each enabled state runs its own action. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class TrackContextMenuSemanticsTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private val calls = mutableListOf<String>()

    @Test
    fun `offline, the download entry is disabled and a tap does nothing`() {
        show(TrackMenuDownload.Offline)

        composeTestRule.onNodeWithText("Download").assertIsNotEnabled().performClick()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `at the storage limit, the download entry is disabled and a tap does nothing`() {
        show(TrackMenuDownload.StorageFull)

        composeTestRule.onNodeWithText("Download").assertIsNotEnabled().performClick()

        assertEquals(emptyList<String>(), calls)
    }

    @Test
    fun `a tap on Download downloads the track and closes the menu`() {
        show(TrackMenuDownload.Available)

        composeTestRule.onNodeWithText("Download").assertIsEnabled().performClick()

        assertEquals(listOf("download", "dismiss"), calls)
    }

    @Test
    fun `a tap on Cancel download cancels it and closes the menu`() {
        show(TrackMenuDownload.Downloading)

        composeTestRule.onNodeWithText("Cancel download").assertIsEnabled().performClick()

        assertEquals(listOf("cancel", "dismiss"), calls)
    }

    @Test
    fun `a tap on Remove download removes it and closes the menu`() {
        show(TrackMenuDownload.Downloaded)

        composeTestRule.onNodeWithText("Remove download").assertIsEnabled().performClick()

        assertEquals(listOf("remove", "dismiss"), calls)
    }

    private fun show(download: TrackMenuDownload) {
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) {
                TrackContextMenuContent(
                    track = TrackMenuData(
                        id = "track",
                        title = "Midnight City",
                        artist = "M83",
                        album = "Hurry Up, We're Dreaming",
                        albumId = "album",
                        artistId = "artist",
                        imageUrl = null,
                        isFavorite = false,
                        download = download,
                    ),
                    onDismiss = { calls += "dismiss" },
                    onPlayNext = { calls += "other" },
                    onAddToQueue = { calls += "other" },
                    onAddToPlaylist = { calls += "other" },
                    onGoToAlbum = { calls += "other" },
                    onGoToArtist = { calls += "other" },
                    onStartMix = { calls += "other" },
                    onToggleFavorite = { calls += "other" },
                    onDownload = { calls += "download" },
                    onCancelDownload = { calls += "cancel" },
                    onRemoveDownload = { calls += "remove" },
                    onTrackInfo = { calls += "other" },
                )
            }
        }
    }
}
