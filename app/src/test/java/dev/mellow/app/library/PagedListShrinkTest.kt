package dev.mellow.app.library

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.paging.LoadState
import androidx.paging.LoadStates
import androidx.paging.PagingData
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import dev.mellow.feature.home.FavoritesContent
import dev.mellow.feature.home.PlaylistDetailScreen
import dev.mellow.feature.home.PlaylistDetailTrack
import dev.mellow.feature.library.AlbumItem
import dev.mellow.feature.library.ArtistItem
import dev.mellow.feature.library.LibraryScreen
import dev.mellow.feature.library.TrackItem
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Duration

/**
 * A paged list can shrink while it's on screen, as when "Downloaded only" turns 51,132 tracks into 17. Every paged
 * list must redraw with the shorter list instead of asking it for rows it no longer has.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp")
class PagedListShrinkTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `the library's tracks tab survives its list shrinking`() =
        assertSurvivesShrinking(::trackItem, "Track 0") { LibraryScreen(tracks = it, initialTab = 2) }

    @Test
    fun `the library's artists tab survives its list shrinking`() =
        assertSurvivesShrinking(::artistItem, "Artist 0") { LibraryScreen(artists = it, initialTab = 1) }

    @Test
    fun `the library's album grid survives its list shrinking`() =
        assertSurvivesShrinking(::albumItem, "Album 0") { LibraryScreen(albumItems = it, initialTab = 0) }

    @Test
    fun `the library's album list survives its list shrinking`() =
        assertSurvivesShrinking(::albumItem, "Album 0") {
            // Too narrow for two grid columns, so the albums show as a list.
            Box(Modifier.width(320.dp)) { LibraryScreen(albumItems = it, initialTab = 0) }
        }

    @Test
    fun `favorite tracks survive their list shrinking`() =
        assertSurvivesShrinking(::track, "Track 0") { FavoritesContent(tracks = it, selectedTab = 0) }

    @Test
    fun `favorite albums survive their list shrinking`() =
        assertSurvivesShrinking(::album, "Album 0") { FavoritesContent(albums = it, selectedTab = 1) }

    @Test
    fun `favorite artists survive their list shrinking`() =
        assertSurvivesShrinking(::artist, "Artist 0") { FavoritesContent(artists = it, selectedTab = 2) }

    @Test
    fun `a playlist survives its list shrinking`() =
        assertSurvivesShrinking(::playlistTrack, "Track 0") { PlaylistDetailScreen(onBack = {}, tracks = it) }

    /** Shows [MANY] items made by [item], then swaps in [FEW] of them, as a filter or a sync would. */
    private fun <T : Any> assertSurvivesShrinking(
        item: (Int) -> T,
        firstItemText: String,
        content: @Composable (LazyPagingItems<T>) -> Unit,
    ) {
        val pages = MutableStateFlow(PagingData.from(List(MANY, item), LOADED))
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) { content(pages.collectAsLazyPagingItems()) }
        }
        composeTestRule.waitForIdle()

        composeTestRule.runOnIdle { pages.value = PagingData.from(List(FEW, item), LOADED) }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText(firstItemText).assertExists()
    }

    private companion object {
        const val MANY = 1_000
        const val FEW = 17

        val LOADED = LoadStates(
            refresh = LoadState.NotLoading(endOfPaginationReached = false),
            prepend = LoadState.NotLoading(endOfPaginationReached = true),
            append = LoadState.NotLoading(endOfPaginationReached = true),
        )

        fun trackItem(i: Int) = TrackItem("t$i", "Track $i", "Artist", "Album", "3:00", null, null)

        fun artistItem(i: Int) = ArtistItem("ar$i", "Artist $i", 1, null)

        fun albumItem(i: Int) = AlbumItem("al$i", "Album $i", "Artist", null)

        fun playlistTrack(i: Int) = PlaylistDetailTrack("t$i", "Track $i", "Artist", "3:00", null)

        fun track(i: Int) = Track(
            id = "t$i", name = "Track $i", albumId = null, albumName = null, artistId = null, artistName = null,
            trackNumber = null, discNumber = null, duration = Duration.ZERO, genres = emptyList(), imageId = null,
            isFavorite = true, playCount = 0, lastPlayedAt = 0, normalizationGain = null,
        )

        fun album(i: Int) = Album(
            id = "al$i", name = "Album $i", artistId = null, artistName = null, year = null, trackCount = 0,
            genres = emptyList(), imageId = null, isFavorite = true,
        )

        fun artist(i: Int) = Artist(
            id = "ar$i", name = "Artist $i", albumCount = 0, imageId = null, isFavorite = true, overview = null,
            genres = emptyList(),
        )
    }
}
