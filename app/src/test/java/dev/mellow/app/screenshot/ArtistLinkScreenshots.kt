package dev.mellow.app.screenshot

import androidx.activity.ComponentDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.mellow.app.navigation.ALBUM_ROUTE
import dev.mellow.app.navigation.ARTIST_ROUTE
import dev.mellow.app.navigation.ArtistPickerHost
import dev.mellow.app.navigation.ExpandablePlayerSheet
import dev.mellow.app.navigation.ExpandableSheetState
import dev.mellow.app.navigation.rememberExpandableSheetState
import dev.mellow.app.navigation.rememberLibraryLinks
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.designsystem.component.MiniPlayer
import dev.mellow.core.designsystem.component.PickerArtist
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.WindowWidthClass
import dev.mellow.feature.library.AlbumDetailComponent
import dev.mellow.feature.library.AlbumDetailLayout
import dev.mellow.feature.library.ArtistDetailLayout
import dev.mellow.feature.library.ArtistDetailScreen
import dev.mellow.feature.library.LibraryScreen
import dev.mellow.feature.player.PlayerLayout
import dev.mellow.feature.player.PlayerScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowDialog

/**
 * The expanded player's artist line for a track with two artists: the picker over the player, then the picked artist's
 * page with the player collapsed; and Back on the picker, which leaves the player as it was.
 */
abstract class ArtistLinkScreenshots : ScreenshotCapture() {

    abstract val playerLayout: PlayerLayout

    private lateinit var sheet: ExpandableSheetState

    @Test
    fun pickArtist() {
        showPlayer()
        snapshot("artistlink-multi-1-expanded")

        openPicker()
        snapshot("artistlink-multi-2-picker")

        composeTestRule.onNodeWithText("Thom Yorke").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()
        snapshot("artistlink-multi-3-artist-page")
    }

    @Test
    fun openAlbum() {
        showPlayer()
        snapshot("artistlink-album-1-expanded")

        composeTestRule.onNodeWithContentDescription("Open album In Rainbows").performClick()
        composeTestRule.waitForIdle()
        snapshot("artistlink-album-2-album-page")
    }

    @Test
    fun dismissPicker() {
        showPlayer()
        openPicker()

        val picker = ShadowDialog.getLatestDialog() as ComponentDialog
        composeTestRule.runOnUiThread { picker.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        snapshot("artistlink-multi-4-back-on-picker")

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()
        snapshot("artistlink-multi-5-back-again")
    }

    private fun openPicker() {
        composeTestRule.onNodeWithContentDescription("Open artist Radiohead, Thom Yorke").performClick()
        composeTestRule.waitForIdle()
    }

    private fun showPlayer() {
        show {
            val nav = rememberNavController()
            sheet = rememberExpandableSheetState()
            val links = rememberLibraryLinks(nav, sheet) { PickerArtist(it.id, it.name, null, albumCount = 3) }
            Box(Modifier.fillMaxSize().background(MellowTheme.colors.background)) {
                Column(Modifier.fillMaxSize()) {
                    Box(Modifier.weight(1f)) {
                        NavHost(nav, startDestination = "home") {
                            composable("home") {
                                LibraryScreen(
                                    albumItems = pagingItemsOf(ScreenshotData.albumItems),
                                    artists = pagingItemsOf(ScreenshotData.artistItems),
                                )
                            }
                            composable(
                                ALBUM_ROUTE,
                                arguments = listOf(
                                    navArgument("albumId") { type = NavType.StringType },
                                    navArgument("source") { type = NavType.StringType; defaultValue = "library" },
                                ),
                            ) {
                                AlbumDetailComponent(
                                    onBack = {},
                                    layout = if (playerLayout == PlayerLayout.Compact) {
                                        AlbumDetailLayout.Stacked
                                    } else {
                                        AlbumDetailLayout.SplitScreen
                                    },
                                    albumName = "In Rainbows",
                                    artistName = "Radiohead",
                                    year = 2007,
                                    tracks = ScreenshotData.albumDetailTracks,
                                )
                            }
                            composable(ARTIST_ROUTE) { entry ->
                                ArtistDetailScreen(
                                    onBack = {},
                                    layout = if (playerLayout == PlayerLayout.Compact) {
                                        ArtistDetailLayout.Stacked
                                    } else {
                                        ArtistDetailLayout.SplitScreen
                                    },
                                    artistName = ARTISTS.first { it.id == entry.arguments?.getString("artistId") }.name,
                                    albumCount = 3,
                                    topTracks = ScreenshotData.artistTracks,
                                    albums = ScreenshotData.artistAlbums,
                                )
                            }
                        }
                    }
                    MiniPlayer(
                        title = "Reckoner",
                        artist = "Radiohead, Thom Yorke",
                        imageUrl = null,
                        isPlaying = true,
                        progress = 0.3f,
                        onPlayPauseClick = {},
                        onNextClick = {},
                        onClick = {},
                        modifier = Modifier
                            .windowInsetsPadding(WindowInsets.navigationBars)
                            .padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp1),
                    )
                }
                ExpandablePlayerSheet(
                    sheetState = sheet,
                    trackName = "Reckoner",
                    artistName = "Radiohead, Thom Yorke",
                    albumImageUrl = null,
                    isPlaying = true,
                    isBuffering = false,
                    progress = 0.3f,
                    onPlayPause = {},
                    onSkipNext = {},
                    playerContent = { onCollapse, onQueueClick, onLyricsClick ->
                        PlayerScreen(
                            layout = playerLayout,
                            embedded = true,
                            trackName = "Reckoner",
                            artistName = "Radiohead, Thom Yorke",
                            albumName = "In Rainbows",
                            isPlaying = true,
                            progress = 0.3f,
                            positionMs = 87_000L,
                            durationMs = 290_000L,
                            codec = "flac",
                            onCollapse = onCollapse,
                            onQueueClick = onQueueClick,
                            onLyricsClick = onLyricsClick,
                            onArtistClick = { links.openArtistOf("radiohead") { ARTISTS } },
                            onAlbumClick = { links.openAlbum("in-rainbows") },
                        )
                    },
                    queueContent = {},
                    lyricsContent = {},
                    bottomNavHeightPx = 0f,
                )
            }
            ArtistPickerHost(links)
        }
        composeTestRule.waitForIdle()
        composeTestRule.runOnIdle { sheet.expand() }
        composeTestRule.waitForIdle()
    }

    private companion object {
        val ARTISTS = listOf("radiohead" to "Radiohead", "thom" to "Thom Yorke").map { (id, name) ->
            ArtistEntity(
                id = id,
                serverId = "server",
                name = name,
                sortName = name,
                albumCount = 3,
                imageTag = null,
                isFavorite = false,
                overview = null,
                genres = emptyList(),
                cleanName = name.lowercase(),
                musicBrainzId = null,
                lastSynced = 0L,
            )
        }
    }
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class ArtistLink_Pixel10Portrait : ArtistLinkScreenshots() {
    override val deviceFolder = "pixel10-portrait"
    override val windowWidthClass = WindowWidthClass.Compact
    override val playerLayout = PlayerLayout.Compact
}

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1280dp-h800dp-xhdpi")
class ArtistLink_PixelTabletLandscape : ArtistLinkScreenshots() {
    override val deviceFolder = "pixel-tablet-landscape"
    override val windowWidthClass = WindowWidthClass.Expanded
    override val playerLayout = PlayerLayout.ExpandedWithQueue
}
