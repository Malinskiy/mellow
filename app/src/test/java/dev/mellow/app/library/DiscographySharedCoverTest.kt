package dev.mellow.app.library

import androidx.activity.ComponentActivity
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import dev.mellow.app.navigation.ALBUM_ROUTE
import dev.mellow.app.navigation.artistAlbumRoute
import dev.mellow.app.screenshot.ScreenshotData
import dev.mellow.core.designsystem.component.LocalNavAnimatedVisibilityScope
import dev.mellow.core.designsystem.component.LocalSharedTransitionScope
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.library.AlbumDetailComponent
import dev.mellow.feature.library.AlbumDetailLayout
import dev.mellow.feature.library.ArtistDetailLayout
import dev.mellow.feature.library.ArtistDetailScreen

/**
 * An album opened from the artist's discography grid takes its cover along to the album page, and back into the grid,
 * the way the library's grid does. The pages sit in a NavHost wired like the app's: one [SharedTransitionLayout] around
 * it, each destination providing its own AnimatedVisibilityScope.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w412dp-h915dp-xxhdpi")
class DiscographySharedCoverTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var navController: NavHostController
    private lateinit var transitions: SharedTransitionScope

    @Test
    fun `an album opened from the discography takes its cover along, there and back`() {
        showArtistGraph(ArtistDetailLayout.Stacked)
        composeTestRule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("The Bends"))

        assertCoverFlies { composeTestRule.onNodeWithText("The Bends").performClick() }
        assertOnAlbum("a4", source = "artist")

        assertCoverFlies { composeTestRule.runOnIdle { navController.popBackStack() } }
        assertEquals("artist", navController.currentDestination?.route)
    }

    @Test
    @Config(qualifiers = WIDE)
    fun `on the wide layout the discography tab sends its cover too, and is still the tab after back`() {
        showArtistGraph(ArtistDetailLayout.SplitScreen)
        tab("Discography").performClick()
        composeTestRule.waitForIdle()

        assertCoverFlies { composeTestRule.onNodeWithText("The Bends").performClick() }
        assertOnAlbum("a4", source = "artist")

        assertCoverFlies { composeTestRule.runOnIdle { navController.popBackStack() } }
        tab("Discography").assertIsSelected()
        composeTestRule.onNodeWithText("The Bends").assertExists()
    }

    @Test
    @Config(qualifiers = WIDE)
    fun `a new artist page on top tracks doesn't pull the cover into its hidden discography`() {
        showArtistGraph(ArtistDetailLayout.SplitScreen)
        tab("Discography").performClick()
        composeTestRule.waitForIdle()
        composeTestRule.onNodeWithText("The Bends").performClick()
        composeTestRule.waitForIdle()
        assertOnAlbum("a4", source = "artist")

        // The album's "Go to artist": the same artist again, on a fresh page that starts on Top Tracks.
        val flew = coverFlew { composeTestRule.runOnIdle { navController.navigate("artist") } }

        assertFalse("the cover flew into the discography page, which is out of view", flew)
        tab("Top Tracks").assertIsSelected()
    }

    private fun showArtistGraph(layout: ArtistDetailLayout) {
        composeTestRule.setContent {
            MellowTheme(darkTheme = true) {
                navController = rememberNavController()
                SharedTransitionLayout {
                    transitions = this
                    CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                        NavHost(
                            navController = navController,
                            startDestination = "artist",
                            enterTransition = { fadeIn(tween(300)) },
                            exitTransition = { fadeOut(tween(300)) },
                            popEnterTransition = { fadeIn(tween(300)) },
                            popExitTransition = { fadeOut(tween(300)) },
                        ) {
                            composable("artist") {
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    ArtistDetailScreen(
                                        onBack = {},
                                        layout = layout,
                                        artistName = "Radiohead",
                                        topTracks = ScreenshotData.artistTracks,
                                        albums = ScreenshotData.artistAlbums,
                                        onAlbumClick = { albumId -> navController.navigate(artistAlbumRoute(albumId)) },
                                    )
                                }
                            }
                            composable(
                                ALBUM_ROUTE,
                                arguments = listOf(
                                    navArgument("albumId") { type = NavType.StringType },
                                    navArgument("source") { type = NavType.StringType; defaultValue = "library" },
                                ),
                            ) { entry ->
                                CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                                    AlbumDetailComponent(
                                        onBack = {},
                                        layout = if (layout == ArtistDetailLayout.SplitScreen) {
                                            AlbumDetailLayout.SplitScreen
                                        } else {
                                            AlbumDetailLayout.Stacked
                                        },
                                        albumId = entry.arguments?.getString("albumId").orEmpty(),
                                        sharedElementSource = entry.arguments?.getString("source").orEmpty(),
                                        albumName = "Album page",
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private fun assertOnAlbum(albumId: String, source: String) {
        val args = composeTestRule.runOnIdle { navController.currentBackStackEntry?.arguments }
        assertEquals(albumId, args?.getString("albumId"))
        assertEquals(source, args?.getString("source"))
    }

    private fun assertCoverFlies(navigate: () -> Unit) {
        assertTrue("no shared cover transition ran", coverFlew(navigate))
    }

    /** Whether a shared element (the cover: nothing else on these pages is shared) animated while [navigate] played. */
    private fun coverFlew(navigate: () -> Unit): Boolean {
        composeTestRule.mainClock.autoAdvance = false
        navigate()
        var flew = false
        repeat(30) {
            composeTestRule.mainClock.advanceTimeByFrame()
            if (transitions.isTransitionActive) flew = true
        }
        composeTestRule.mainClock.autoAdvance = true
        composeTestRule.waitForIdle()
        return flew
    }

    private fun tab(label: String) = composeTestRule.onNode(hasText(label) and hasClickAction())

    private companion object {
        /** A tablet-wide window, where the artist shows on the split-screen layout. */
        const val WIDE = "w900dp-h600dp-xxhdpi"
    }
}
