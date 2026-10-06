package dev.mellow.app.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpRect
import androidx.compose.ui.unit.dp
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.player.PlayerLayout
import dev.mellow.feature.player.PlayerScreen
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The title always has room for two lines, so going from a one-line title to a two-line one (skipping a track) moves
 * neither the cover nor the progress bar nor the controls, nor the title and its heart.
 *
 * Default profile: a short phone, 1080 × 2340 at 450 dpi (the owner's Samsung), with room taken by the system bars.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w384dp-h832dp-450dpi")
class PlayerTitleReservationTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    private var title by mutableStateOf(SHORT_TITLE)

    @Test
    fun `short phone - nothing moves when the title takes a second line`() = assertStill(PlayerLayout.Compact)

    @Test
    fun `short phone - nothing moves with large text`() = assertStill(PlayerLayout.Compact, fontScale = 1.3f)

    @Test
    fun `short phone - nothing moves when downloaded`() = assertStill(PlayerLayout.Compact, downloaded = true)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `pixel - nothing moves when the title takes a second line`() = assertStill(PlayerLayout.Compact)

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `pixel - nothing moves with large text`() = assertStill(PlayerLayout.Compact, fontScale = 1.3f)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `small phone - nothing moves when the title takes a second line`() =
        assertStill(PlayerLayout.Compact, systemBars = false)

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `small phone - nothing moves with large text`() =
        assertStill(PlayerLayout.Compact, fontScale = 1.3f, systemBars = false)

    @Test
    @Config(qualifiers = "w915dp-h412dp-xxhdpi")
    fun `landscape phone - nothing moves when the title takes a second line`() =
        assertStill(PlayerLayout.Landscape, systemBars = false)

    @Test
    @Config(qualifiers = "w915dp-h412dp-xxhdpi")
    fun `landscape phone - nothing moves with large text`() =
        assertStill(PlayerLayout.Landscape, fontScale = 1.3f, systemBars = false)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `tablet - nothing moves when the title takes a second line`() =
        assertStill(PlayerLayout.ExpandedWithQueue, sidePanel = true, longTitle = TABLET_LONG_TITLE)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `tablet - nothing moves with large text`() =
        assertStill(PlayerLayout.ExpandedWithQueue, fontScale = 1.3f, sidePanel = true, longTitle = TABLET_LONG_TITLE)

    @Test
    @Config(qualifiers = "w1023dp-h876dp-420dpi")
    fun `tabletop - nothing moves when the title takes a second line`() =
        assertStill(PlayerLayout.Tabletop, systemBars = false, controls = false)

    @Test
    @Config(qualifiers = "w1023dp-h876dp-420dpi")
    fun `tabletop - nothing moves with large text`() =
        assertStill(PlayerLayout.Tabletop, fontScale = 1.3f, systemBars = false, controls = false)

    private fun assertStill(
        layout: PlayerLayout,
        fontScale: Float = 1f,
        downloaded: Boolean = false,
        sidePanel: Boolean = false,
        systemBars: Boolean = true,
        controls: Boolean = true,
        longTitle: String = LONG_TITLE,
    ) {
        show(layout, fontScale, downloaded, sidePanel, systemBars)
        assertEquals("short title lines", 1, textLayout(SHORT_TITLE).lineCount)
        val short = positions(controls)

        title = longTitle
        composeTestRule.waitForIdle()
        assertEquals("long title lines", 2, textLayout(longTitle).lineCount)
        val long = positions(controls)

        for ((name, before) in short) {
            assertEquals("$name moved", before, long.getValue(name))
        }
    }

    /** Where the parts that must stay still are. The tabletop's controls are below the hinge, out of the way. */
    private fun positions(controls: Boolean): Map<String, DpRect> = buildMap {
        put("cover", bounds(composeTestRule.onNodeWithTag("cover", useUnmergedTree = true).fetchSemanticsNode()))
        put("title top", bounds(titleNode()).let { DpRect(it.left, it.top, it.left, it.top) })
        put("heart", bounds(unlabelledClickable(HEART_BOX)))
        if (controls) {
            put("progress bar", bounds(progressBar()))
            put("play button", bounds(unlabelledClickable(PLAY_BUTTON)))
        }
    }

    private fun progressBar(): SemanticsNode = composeTestRule
        .onNode(SemanticsMatcher.keyIsDefined(SemanticsProperties.ProgressBarRangeInfo), useUnmergedTree = true)
        .fetchSemanticsNode()

    /** The one clickable, unlabelled node of this size: the heart (43.2 dp) or the play button (64 dp). */
    private fun unlabelledClickable(size: Dp): SemanticsNode {
        val found = composeTestRule.onAllNodes(hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.ContentDescription) == null }
            .filter { it.config.getOrNull(SemanticsProperties.Text) == null }
            .filter {
                val box = bounds(it)
                abs((box.right - box.left - size).value) < 0.5f && abs((box.bottom - box.top - size).value) < 0.5f
            }
        assertEquals("$size candidates: ${found.map { bounds(it) }}", 1, found.size)
        return found.single()
    }

    private fun titleNode(): SemanticsNode =
        composeTestRule.onNodeWithText(title, useUnmergedTree = true).fetchSemanticsNode()

    private fun textLayout(text: String): TextLayoutResult {
        val node = composeTestRule.onNodeWithText(text, useUnmergedTree = true).fetchSemanticsNode()
        val results = mutableListOf<TextLayoutResult>()
        node.config[SemanticsActions.GetTextLayoutResult].action!!.invoke(results)
        return results.single()
    }

    /** A node's bounds in the root, not clipped to its parent's. */
    private fun bounds(node: SemanticsNode): DpRect = with(composeTestRule.density) {
        DpRect(
            left = node.positionInRoot.x.toDp(),
            top = node.positionInRoot.y.toDp(),
            right = (node.positionInRoot.x + node.size.width).toDp(),
            bottom = (node.positionInRoot.y + node.size.height).toDp(),
        )
    }

    private fun show(
        layout: PlayerLayout,
        fontScale: Float,
        downloaded: Boolean,
        sidePanel: Boolean,
        systemBars: Boolean,
    ) {
        composeTestRule.setContent {
            val density = LocalDensity.current
            CompositionLocalProvider(LocalDensity provides Density(density.density, fontScale)) {
                MellowTheme(darkTheme = true) {
                    // The sheet the player sits in keeps clear of the status and navigation bars.
                    val bars = if (systemBars) Modifier.padding(top = STATUS_BAR, bottom = NAVIGATION_BAR) else Modifier
                    Box(Modifier.fillMaxSize().then(bars)) {
                        PlayerScreen(
                            embedded = true,
                            layout = layout,
                            tabletopTopHeight = TABLETOP_TOP_HEIGHT,
                            artModifier = Modifier.testTag("cover"),
                            trackName = title,
                            artistName = "Hammock",
                            albumName = ALBUM,
                            isDownloaded = downloaded,
                            codec = "flac",
                            sidePanelContent = if (sidePanel) {
                                { Box(Modifier.width(400.dp).fillMaxHeight()) }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }
        composeTestRule.waitForIdle()
    }

    private companion object {
        const val SHORT_TITLE = "Help!"
        const val LONG_TITLE = "Let the Water Wash Away Your Sins (Reinterpretation)"

        /** Two lines in the tablet's wide pane (the usual long title fits on one there). */
        const val TABLET_LONG_TITLE =
            "Let the Water Wash Away Your Sins (Reinterpretation) [Live at the Royal Albert Hall]"
        const val ALBUM = "Far Cry 5 Presents: We Will Rise Again (Original Game Soundtrack)"
        val HEART_BOX = 43.2.dp
        val PLAY_BUTTON = 64.dp
        val STATUS_BAR = 32.dp
        val NAVIGATION_BAR = 48.dp
        val TABLETOP_TOP_HEIGHT = 395.dp
    }
}
