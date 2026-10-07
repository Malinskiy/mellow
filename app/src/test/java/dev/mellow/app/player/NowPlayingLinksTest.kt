package dev.mellow.app.player

import androidx.activity.ComponentActivity
import androidx.activity.ComponentDialog
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithContentDescription
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
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
import dev.mellow.app.navigation.LibraryLinks
import dev.mellow.app.navigation.rememberExpandableSheetState
import dev.mellow.app.navigation.rememberLibraryLinks
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.designsystem.component.PickerArtist
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.player.PlayerLayout
import dev.mellow.feature.player.PlayerScreen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowDialog

/**
 * Tapping the expanded player's artist line or album name opens that page, with the player out of the way; several
 * artists ask which one first, over the player.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class NowPlayingLinksTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var nav: NavHostController
    private lateinit var sheet: ExpandableSheetState
    private lateinit var links: LibraryLinks
    private val calls = mutableListOf<String>()

    @Test
    fun `tapping the artist of a one-artist track opens the artist and collapses the player`() {
        show(artists = listOf(RADIOHEAD))

        composeTestRule.onNodeWithContentDescription("Open artist Radiohead").performClick()
        composeTestRule.waitForIdle()

        assertEquals("artist/radiohead", page())
        assertFalse("the player is still expanded", sheet.isExpanded)
    }

    @Test
    fun `an artist the track has no artist rows for opens its own artist id`() {
        show(artists = emptyList())

        composeTestRule.onNodeWithContentDescription("Open artist Radiohead").performClick()
        composeTestRule.waitForIdle()

        assertEquals("artist/$FALLBACK_ARTIST", page())
    }

    @Test
    fun `tapping the album opens it and collapses the player`() {
        show()

        composeTestRule.onNodeWithContentDescription("Open album In Rainbows").performClick()
        composeTestRule.waitForIdle()

        assertEquals("album/in-rainbows", page())
        assertFalse("the player is still expanded", sheet.isExpanded)
    }

    @Test
    fun `tapping the album of the album page showing only collapses the player`() {
        show()
        composeTestRule.runOnIdle { nav.navigate("album/in-rainbows") }
        expand()

        composeTestRule.onNodeWithContentDescription("Open album In Rainbows").performClick()
        composeTestRule.waitForIdle()

        assertEquals("album/in-rainbows", page())
        assertFalse(sheet.isExpanded)
        // No second copy of the page: one Back leaves it.
        composeTestRule.runOnIdle { nav.popBackStack() }
        assertEquals("home", page())
    }

    @Test
    fun `tapping the artist of the artist page showing only collapses the player`() {
        show(artists = listOf(RADIOHEAD))
        composeTestRule.runOnIdle { nav.navigate("artist/radiohead") }
        expand()

        composeTestRule.onNodeWithContentDescription("Open artist Radiohead").performClick()
        composeTestRule.waitForIdle()

        assertFalse(sheet.isExpanded)
        composeTestRule.runOnIdle { nav.popBackStack() }
        assertEquals("home", page())
    }

    @Test
    fun `another album opens on top of the album page showing`() {
        show()
        composeTestRule.runOnIdle { nav.navigate("album/kid-a") }
        expand()

        composeTestRule.onNodeWithContentDescription("Open album In Rainbows").performClick()
        composeTestRule.waitForIdle()

        assertEquals("album/in-rainbows", page())
        composeTestRule.runOnIdle { nav.popBackStack() }
        assertEquals("album/kid-a", page())
    }

    @Test
    fun `tapping the artist of a two-artist track asks which one over the still expanded player`() {
        show(artists = listOf(RADIOHEAD, THOM))

        composeTestRule.onNodeWithContentDescription("Open artist Radiohead, Thom Yorke").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Go to Artist").assertExists()
        composeTestRule.onNodeWithText("Thom Yorke").assertExists()
        assertTrue("the player collapsed under the picker", sheet.isExpanded)
        assertEquals("home", page())
    }

    @Test
    fun `picking an artist closes the picker, collapses the player and opens the artist`() {
        show(artists = listOf(RADIOHEAD, THOM))
        composeTestRule.onNodeWithContentDescription("Open artist Radiohead, Thom Yorke").performClick()
        composeTestRule.waitForIdle()

        // The picker is a dialog window, where Robolectric's injected taps don't reach the rows; its own action does.
        composeTestRule.onNodeWithText("Thom Yorke").performSemanticsAction(SemanticsActions.OnClick)
        composeTestRule.waitForIdle()

        assertEquals("artist/thom", page())
        assertFalse(sheet.isExpanded)
        assertNull(links.pickerArtists)
        composeTestRule.onNodeWithText("Go to Artist").assertDoesNotExist()
    }

    @Test
    fun `back on the picker closes only the picker, and back again collapses the player`() {
        show(artists = listOf(RADIOHEAD, THOM))
        composeTestRule.onNodeWithContentDescription("Open artist Radiohead, Thom Yorke").performClick()
        composeTestRule.waitForIdle()

        val picker = ShadowDialog.getLatestDialog() as ComponentDialog
        composeTestRule.runOnUiThread { picker.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithText("Go to Artist").assertDoesNotExist()
        assertNull(links.pickerArtists)
        assertTrue("back on the picker collapsed the player", sheet.isExpanded)
        assertEquals("home", page())

        composeTestRule.runOnUiThread { composeTestRule.activity.onBackPressedDispatcher.onBackPressed() }
        composeTestRule.waitForIdle()

        assertFalse(sheet.isExpanded)
        assertEquals("home", page())
        composeTestRule.onNodeWithText("Go to Artist").assertDoesNotExist()
    }

    @Test
    fun `tapping the picker's scrim leaves the player expanded and goes nowhere`() {
        show(artists = listOf(RADIOHEAD, THOM))
        composeTestRule.onNodeWithContentDescription("Open artist Radiohead, Thom Yorke").performClick()
        composeTestRule.waitForIdle()

        composeTestRule.onNodeWithContentDescription("Close sheet").performClick()
        composeTestRule.waitForIdle()

        assertNull(links.pickerArtists)
        assertTrue(sheet.isExpanded)
        assertEquals("home", page())
    }

    @Test
    fun `a drag down that starts on the artist collapses the player and opens nothing`() {
        show(artists = listOf(RADIOHEAD))

        composeTestRule.onNodeWithContentDescription("Open artist Radiohead").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, 60f), delayMillis = 16) }
            up()
        }
        composeTestRule.waitForIdle()

        assertFalse("the player is still expanded", sheet.isExpanded)
        assertEquals("home", page())
    }

    @Test
    fun `a drag down that starts on the album collapses the player and opens nothing`() {
        show()

        composeTestRule.onNodeWithContentDescription("Open album In Rainbows").performTouchInput {
            down(center)
            repeat(20) { moveBy(Offset(0f, 60f), delayMillis = 16) }
            up()
        }
        composeTestRule.waitForIdle()

        assertFalse(sheet.isExpanded)
        assertEquals("home", page())
    }

    @Test
    fun `the collapse and queue buttons beside the album still work`() {
        show()

        composeTestRule.onNodeWithContentDescription("Queue").performClick()
        composeTestRule.waitForIdle()
        assertEquals(listOf("queue"), calls)

        composeTestRule.onNodeWithContentDescription("Collapse").performClick()
        composeTestRule.waitForIdle()
        assertFalse(sheet.isExpanded)
        assertEquals("home", page())
    }

    @Test
    fun `an album or artist that isn't in the library is plain text`() {
        show(albumId = null, canOpenArtist = false)

        composeTestRule.onNodeWithContentDescription("Open album", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithContentDescription("Open artist", substring = true).assertDoesNotExist()
        composeTestRule.onNodeWithText("Radiohead").assertHasNoClickAction()
        composeTestRule.onNodeWithText("In Rainbows").assertHasNoClickAction()
    }

    @Test
    fun `the artist and the album are buttons named for TalkBack`() {
        show()

        composeTestRule.onNodeWithContentDescription("Open artist Radiohead")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
        composeTestRule.onNodeWithContentDescription("Open album In Rainbows")
            .assertHasClickAction()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.Role, Role.Button))
            // One button read as its label, not the label and then "Playing from" and the album name again.
            .assert(SemanticsMatcher.keyNotDefined(SemanticsProperties.Text))
    }

    @Test
    @Config(qualifiers = "w915dp-h412dp-xxhdpi")
    fun `landscape - the artist and the Playing from album open them`() {
        show(layout = PlayerLayout.Landscape)
        assertOpensBoth()
    }

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `tablet - the artist and the top bar's album open them`() {
        show(layout = PlayerLayout.ExpandedWithQueue)
        assertOpensBoth()
    }

    @Test
    @Config(qualifiers = "w1023dp-h876dp-420dpi")
    fun `tabletop - the artist and the album line open them`() {
        show(layout = PlayerLayout.Tabletop)
        assertOpensBoth()
    }

    @Test
    @Config(qualifiers = "w1023dp-h876dp-420dpi")
    fun `tabletop - the album in the top bar opens it too`() {
        show(layout = PlayerLayout.Tabletop)

        // The top bar's "Playing from" and the line under the artist both name the album.
        val albums = composeTestRule.onAllNodesWithContentDescription("Open album In Rainbows")
        assertEquals(2, albums.fetchSemanticsNodes().size)
        albums[0].performClick()
        composeTestRule.waitForIdle()

        assertEquals("album/in-rainbows", page())
        assertFalse(sheet.isExpanded)
    }

    private fun assertOpensBoth() {
        composeTestRule.onNodeWithContentDescription("Open artist Radiohead").performClick()
        composeTestRule.waitForIdle()
        assertEquals("artist/radiohead", page())
        assertFalse(sheet.isExpanded)

        expand()
        composeTestRule.onAllNodesWithContentDescription("Open album In Rainbows")[0].performClick()
        composeTestRule.waitForIdle()
        assertEquals("album/in-rainbows", page())
        assertFalse(sheet.isExpanded)
    }

    /** The page on top: "home", "album/<id>" or "artist/<id>". */
    private fun page(): String = composeTestRule.runOnIdle {
        val entry = nav.currentBackStackEntry!!
        when (entry.destination.route) {
            ALBUM_ROUTE -> "album/${entry.arguments?.getString("albumId")}"
            ARTIST_ROUTE -> "artist/${entry.arguments?.getString("artistId")}"
            else -> entry.destination.route!!
        }
    }

    private fun expand() {
        composeTestRule.runOnIdle { sheet.expand() }
        composeTestRule.waitForIdle()
        assertTrue("the player didn't open", sheet.isExpanded)
    }

    private fun show(
        layout: PlayerLayout = PlayerLayout.Compact,
        artists: List<ArtistEntity> = listOf(RADIOHEAD),
        albumId: String? = "in-rainbows",
        canOpenArtist: Boolean = true,
    ) {
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) {
                nav = rememberNavController()
                sheet = rememberExpandableSheetState()
                links = rememberLibraryLinks(nav, sheet) {
                    PickerArtist(it.id, it.name, imageUrl = null, albumCount = 1)
                }
                Box(Modifier.fillMaxSize()) {
                    NavHost(nav, startDestination = "home") {
                        composable("home") { Text("Home page") }
                        composable(
                            ALBUM_ROUTE,
                            arguments = listOf(
                                navArgument("albumId") { type = NavType.StringType },
                                navArgument("source") { type = NavType.StringType; defaultValue = "library" },
                            ),
                        ) { Text("Album page") }
                        composable(ARTIST_ROUTE) { Text("Artist page") }
                    }
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
                        playerContent = { onCollapse, onQueueClick, onLyricsClick ->
                            PlayerScreen(
                                layout = layout,
                                tabletopTopHeight = if (layout == PlayerLayout.Tabletop) 400.dp else 0.dp,
                                embedded = true,
                                trackName = "Reckoner",
                                artistName = artists.joinToString { it.name }.ifEmpty { "Radiohead" },
                                albumName = "In Rainbows",
                                onCollapse = onCollapse,
                                onQueueClick = {
                                    calls += "queue"
                                    onQueueClick()
                                },
                                onLyricsClick = onLyricsClick,
                                onArtistClick = if (canOpenArtist) {
                                    { links.openArtistOf(FALLBACK_ARTIST) { artists } }
                                } else {
                                    null
                                },
                                onAlbumClick = albumId?.let { id -> { links.openAlbum(id) } },
                            )
                        },
                        queueContent = {},
                        lyricsContent = {},
                        bottomNavHeightPx = 0f,
                    )
                }
                ArtistPickerHost(links)
            }
        }
        composeTestRule.waitForIdle()
        expand()
    }

    private companion object {
        const val FALLBACK_ARTIST = "radiohead-alias"
        val RADIOHEAD = artist("radiohead", "Radiohead")
        val THOM = artist("thom", "Thom Yorke")

        fun artist(id: String, name: String) = ArtistEntity(
            id = id,
            serverId = "server",
            name = name,
            sortName = name,
            albumCount = 1,
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
