package dev.mellow.app.library

import java.time.Duration
import android.graphics.Bitmap
import android.graphics.Canvas
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.SemanticsNodeInteractionCollection
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToIndexAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.app.screenshot.ScreenshotData
import dev.mellow.app.screenshot.pagingItemsOf
import dev.mellow.core.designsystem.theme.LocalBatterySaverActive
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.model.Album
import dev.mellow.core.model.Track
import dev.mellow.feature.home.FavoritesContent
import dev.mellow.feature.library.ArtistDetailLayout
import dev.mellow.feature.library.ArtistDetailScreen
import dev.mellow.feature.library.LibraryScreen
import dev.mellow.feature.library.TrackItem

/** Swiping between the library's and favorites' tabs, and the tab bar that follows the pages. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class TabSwipeTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    @Test
    fun `swiping the library's pages selects the next tab and shows its content`() {
        show {
            LibraryScreen(
                albumItems = pagingItemsOf(ScreenshotData.albumItems),
                artists = pagingItemsOf(ScreenshotData.artistItems),
            )
        }
        composeTestRule.onNodeWithText("OK Computer").assertIsDisplayed()

        swipePages(left = true)

        tab("Artists").assertIsSelected()
        tab("Albums").assertIsNotSelected()
        composeTestRule.onNodeWithText("The Strokes").assertIsDisplayed()
        composeTestRule.onNodeWithText("OK Computer").assertIsNotDisplayed()

        swipePages(left = false)

        tab("Albums").assertIsSelected()
        composeTestRule.onNodeWithText("OK Computer").assertIsDisplayed()
    }

    @Test
    fun `tapping a far tab ends on its page with the pill on its chip`() {
        show {
            LibraryScreen(
                tracks = pagingItemsOf(ScreenshotData.trackItems),
                playlists = ScreenshotData.libraryPlaylists,
                initialTab = 2,
            )
        }

        tab("Playlists").performClick()
        composeTestRule.waitForIdle()

        tab("Playlists").assertIsSelected()
        composeTestRule.onNodeWithText("Late Night Vibes").assertIsDisplayed()
        composeTestRule.onNodeWithText("Paranoid Android").assertDoesNotExist()
        assertPillOn("Playlists")
        assertNoPillOn("Tracks")

        // And all the way back, four tabs.
        tab("Albums").performClick()
        composeTestRule.waitForIdle()

        tab("Albums").assertIsSelected()
        composeTestRule.onNodeWithText("No albums yet").assertIsDisplayed()
        assertPillOn("Albums")
        assertNoPillOn("Playlists")
    }

    @Test
    fun `a jump over other tabs never shows the tabs in between`() {
        show {
            LibraryScreen(
                artists = pagingItemsOf(ScreenshotData.artistItems),
                tracks = pagingItemsOf(ScreenshotData.trackItems),
                genres = ScreenshotData.genres,
                playlists = ScreenshotData.libraryPlaylists,
                initialTab = 4,
            )
        }
        composeTestRule.mainClock.autoAdvance = false

        // Playlists to Albums: Artists, Tracks and Genres lie in between.
        tab("Albums").performClick()
        repeat(40) {
            composeTestRule.mainClock.advanceTimeByFrame()
            for (between in listOf("The Strokes", "Paranoid Android", "Shoegaze")) {
                composeTestRule.onAllNodesWithText(between).assertIsNotDisplayedOrAbsent()
            }
        }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()

        tab("Albums").assertIsSelected()
        composeTestRule.onNodeWithText("No albums yet").assertIsDisplayed()
    }

    @Test
    fun `a page keeps its scroll position while it is swiped away`() {
        val tracks = List(100) { TrackItem("t$it", "Track $it", "Artist", "Album", "3:00", null, null) }
        show {
            LibraryScreen(tracks = pagingItemsOf(tracks), genres = ScreenshotData.genres, initialTab = 2)
        }
        // The pager and the genres next door scroll to an index too: the track list scrolls down and has the tracks.
        val trackList = hasScrollToIndexAction() and hasAnyDescendant(hasText("Track 0")) and
            SemanticsMatcher.keyIsDefined(SemanticsProperties.VerticalScrollAxisRange)
        composeTestRule.onNode(trackList).performScrollToIndex(40)
        composeTestRule.onNodeWithText("Track 40").assertIsDisplayed()

        swipePages(left = true)
        composeTestRule.onNodeWithText("Shoegaze").assertIsDisplayed()
        swipePages(left = false)

        tab("Tracks").assertIsSelected()
        composeTestRule.onNodeWithText("Track 40").assertIsDisplayed()
        composeTestRule.onNodeWithText("Track 0").assertDoesNotExist()
    }

    @Test
    fun `the grid or list toggle only shows on the albums page`() {
        show {
            LibraryScreen(
                albumItems = pagingItemsOf(ScreenshotData.albumItems),
                artists = pagingItemsOf(ScreenshotData.artistItems),
            )
        }
        composeTestRule.onNodeWithContentDescription("List view").assertIsDisplayed()

        swipePages(left = true)
        composeTestRule.onNodeWithContentDescription("List view").assertDoesNotExist()

        swipePages(left = false)
        composeTestRule.onNodeWithContentDescription("List view").assertIsDisplayed()
    }

    @Test
    fun `favorites swipe between tracks, albums and artists and report the tab`() {
        var selected by mutableIntStateOf(0)
        show {
            FavoritesContent(
                tracks = pagingItemsOf(listOf(track())),
                albums = pagingItemsOf(listOf(album())),
                selectedTab = selected,
                onTabSelected = { selected = it },
            )
        }
        composeTestRule.onNodeWithText("Favorite track").assertIsDisplayed()

        swipePages(left = true)

        assertEquals(1, selected)
        tab("Albums").assertIsSelected()
        composeTestRule.onNodeWithText("Favorite album").assertIsDisplayed()

        tab("Artists").performClick()
        composeTestRule.waitForIdle()

        assertEquals(2, selected)
        composeTestRule.onNodeWithText("No favorite artists yet").assertIsDisplayed()
    }

    @Test
    fun `the selected tab set from outside moves the pages`() {
        var selected by mutableIntStateOf(0)
        show {
            FavoritesContent(
                tracks = pagingItemsOf(listOf(track())),
                albums = pagingItemsOf(listOf(album())),
                selectedTab = selected,
                onTabSelected = { selected = it },
            )
        }

        selected = 1
        composeTestRule.waitForIdle()

        tab("Albums").assertIsSelected()
        composeTestRule.onNodeWithText("Favorite album").assertIsDisplayed()
    }

    @Test
    @Config(qualifiers = "w900dp-h600dp-xxhdpi") // The artist's tabs are on the wide, split-screen layout.
    fun `the artist's tabs still switch by tap`() {
        show {
            ArtistDetailScreen(
                onBack = {},
                layout = ArtistDetailLayout.SplitScreen,
                artistName = "Radiohead",
                topTracks = ScreenshotData.artistTracks,
                albums = ScreenshotData.artistAlbums,
            )
        }
        composeTestRule.onNodeWithText("Paranoid Android").assertIsDisplayed()

        tab("Discography").performClick()
        composeTestRule.waitForIdle()

        tab("Discography").assertIsSelected()
        composeTestRule.onNodeWithText("Paranoid Android").assertDoesNotExist()
        assertPillOn("Discography")
    }

    @Test
    fun `with animations off a tapped tab shows at once`() {
        Settings.Global.putFloat(
            composeTestRule.activity.contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            0f,
        )
        show {
            LibraryScreen(
                tracks = pagingItemsOf(ScreenshotData.trackItems),
                playlists = ScreenshotData.libraryPlaylists,
                initialTab = 2,
            )
        }
        composeTestRule.mainClock.autoAdvance = false

        tab("Playlists").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("Late Night Vibes").assertIsDisplayed()
        assertPillOn("Playlists")
        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun `on battery saver a tapped tab shows at once`() {
        show {
            CompositionLocalProvider(LocalBatterySaverActive provides true) {
                LibraryScreen(
                    tracks = pagingItemsOf(ScreenshotData.trackItems),
                    playlists = ScreenshotData.libraryPlaylists,
                    initialTab = 2,
                )
            }
        }
        composeTestRule.mainClock.autoAdvance = false

        tab("Playlists").performClick()
        composeTestRule.mainClock.advanceTimeByFrame()
        composeTestRule.mainClock.advanceTimeByFrame()

        composeTestRule.onNodeWithText("Late Night Vibes").assertIsDisplayed()
        assertPillOn("Playlists")
        composeTestRule.mainClock.autoAdvance = true
    }

    @Test
    fun `tabs are tabs to accessibility services`() {
        show { LibraryScreen() }

        for (label in listOf("Albums", "Artists", "Tracks", "Genres", "Playlists")) {
            tab(label).assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Tab))
        }
        tab("Albums").assertIsSelected()
    }

    @Test
    fun `a tab can be tapped just below its chip, within a 48 dp target`() {
        show { LibraryScreen(artists = pagingItemsOf(ScreenshotData.artistItems)) }
        val chip = tab("Artists").fetchSemanticsNode().boundsInRoot
        val below = with(composeTestRule.density) { 20.dp.toPx() }
        val chipHeight = with(composeTestRule.density) { chip.height.toDp() }
        assertTrue("the chip itself ($chipHeight) is smaller than 48 dp", chipHeight < 40.dp)

        composeTestRule.onRoot().performTouchInput { click(Offset(chip.center.x, chip.center.y + below)) }
        composeTestRule.waitForIdle()

        tab("Artists").assertIsSelected()
    }

    private fun tab(label: String) = composeTestRule.onNode(hasText(label) and hasClickAction())

    /** Not on screen: either not composed at all, or composed on a page that is out of view. */
    private fun SemanticsNodeInteractionCollection.assertIsNotDisplayedOrAbsent() {
        val count = fetchSemanticsNodes().size
        for (i in 0 until count) get(i).assertIsNotDisplayed()
    }

    /** A fling across the pages, along the middle of the screen, where the content is. */
    private fun swipePages(left: Boolean) {
        composeTestRule.onRoot().performTouchInput {
            if (left) swipeLeft(startX = width * 0.9f, endX = width * 0.1f) else swipeRight(width * 0.1f, width * 0.9f)
        }
        composeTestRule.waitForIdle()
    }

    /** The pill's Stone200 fills the chip just inside its left end. */
    private fun assertPillOn(label: String) {
        val pixel = chipEdgePixel(label)
        assertTrue("pill on $label: $pixel", pixel.red > 0.8f && pixel.green > 0.8f)
    }

    private fun assertNoPillOn(label: String) {
        val pixel = chipEdgePixel(label)
        assertTrue("no pill on $label: $pixel", pixel.red < 0.2f)
    }

    /** The colour just inside the left end of [label]'s chip, drawn the way the screenshot tests draw the screen. */
    private fun chipEdgePixel(label: String): Color {
        val chip = tab(label).fetchSemanticsNode().boundsInWindow
        val root = composeTestRule.activity.window.decorView.rootView
        val bitmap = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        composeTestRule.runOnIdle { root.draw(Canvas(bitmap)) }
        val x = chip.left + with(composeTestRule.density) { 6.dp.toPx() }
        return Color(bitmap.getPixel(x.toInt(), chip.center.y.toInt()))
    }

    private fun show(content: @Composable () -> Unit) {
        composeTestRule.setContent { MellowTheme(darkTheme = true) { content() } }
        composeTestRule.waitForIdle()
    }

    private fun track() = Track(
        id = "t1", name = "Favorite track", albumId = null, albumName = null, artistId = null, artistName = null,
        trackNumber = null, discNumber = null, duration = Duration.ZERO, genres = emptyList(), imageId = null,
        isFavorite = true, playCount = 0, lastPlayedAt = 0, normalizationGain = null,
    )

    private fun album() = Album(
        id = "al1", name = "Favorite album", artistId = null, artistName = null, year = null, trackCount = 0,
        genres = emptyList(), imageId = null, isFavorite = true,
    )
}
