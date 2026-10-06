package dev.mellow.app.player

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
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
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import kotlin.math.abs

/**
 * The expanded player keeps a fixed gap between the cover and the title (the cover shrinks instead), and the heart
 * sits level with the title's first line, whatever the title's length.
 *
 * Default profile: a short phone, 1080 × 2340 at 450 dpi (the owner's Samsung), with room taken by the system bars.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w384dp-h832dp-450dpi")
class PlayerTitleSpacingTest {

    @get:Rule
    val composeTestRule = createAndroidComposeRule<ComponentActivity>()

    // Compact: cover ↔ title gap

    @Test
    fun `short phone - the title keeps 24 dp off the cover`() = assertCoverGaps()

    @Test
    @Config(qualifiers = "w412dp-h915dp-xxhdpi")
    fun `pixel - the title keeps 24 dp off the cover`() = assertCoverGaps()

    @Test
    fun `short phone - the title keeps 24 dp off the cover with large text`() = assertCoverGaps(fontScale = 1.3f)

    // Heart ↔ first line of the title

    @Test
    fun `compact - the heart lines up with a one-line title`() =
        assertHeartOnFirstLine(PlayerLayout.Compact, SHORT_TITLE, lines = 1)

    @Test
    fun `compact - the heart lines up with the first line of a two-line title`() =
        assertHeartOnFirstLine(PlayerLayout.Compact, LONG_TITLE, lines = 2)

    @Test
    fun `compact - the heart lines up with the first line when downloaded`() =
        assertHeartOnFirstLine(PlayerLayout.Compact, LONG_TITLE, lines = 2, downloaded = true)

    @Test
    fun `compact - the heart lines up with the first line with large text`() =
        assertHeartOnFirstLine(PlayerLayout.Compact, LONG_TITLE, lines = null, fontScale = 1.3f)

    @Test
    @Config(qualifiers = "w915dp-h412dp-xxhdpi")
    fun `landscape - the heart lines up with a one-line title`() =
        assertHeartOnFirstLine(PlayerLayout.Landscape, SHORT_TITLE, lines = 1)

    @Test
    @Config(qualifiers = "w915dp-h412dp-xxhdpi")
    fun `landscape - the heart lines up with the first line of a two-line title`() =
        assertHeartOnFirstLine(PlayerLayout.Landscape, LONG_TITLE, lines = 2)

    @Test
    @Config(qualifiers = "w1280dp-h800dp-xhdpi")
    fun `tablet - the heart lines up with a one-line title`() =
        assertHeartOnFirstLine(PlayerLayout.ExpandedWithQueue, SHORT_TITLE, lines = 1, sidePanel = true)

    @Test
    @Config(qualifiers = "w876dp-h1023dp-420dpi")
    fun `fold book - the heart lines up with the first line of a two-line title`() =
        assertHeartOnFirstLine(
            PlayerLayout.ExpandedWithQueue,
            LONG_TITLE,
            lines = 2,
            sidePanel = true,
            splitPaneWidth = FOLD_HINGE,
        )

    // A title too long for two lines stops at two, with an ellipsis

    @Test
    @Config(qualifiers = "w360dp-h640dp-xhdpi")
    fun `small phone - a very long title stops at two lines and leaves the cover room`() =
        assertCappedAtTwoLines(systemBars = false, minCover = COVER_MIN)

    @Test
    fun `short phone - a very long title stops at two lines`() = assertCappedAtTwoLines(systemBars = true)

    private fun assertCappedAtTwoLines(systemBars: Boolean, minCover: Dp? = null) {
        show(PlayerLayout.Compact, VERY_LONG_TITLE, downloaded = true, systemBars = systemBars)

        val text = textLayout(titleNode())
        assertEquals("title lines", 2, text.lineCount)
        assertTrue("second line not ellipsized", text.isLineEllipsized(1))
        assertTrue("no visual overflow", text.hasVisualOverflow)
        assertHeartOnFirstLine(text)
        if (minCover != null) {
            val cover = bounds(composeTestRule.onNodeWithTag("cover", useUnmergedTree = true).fetchSemanticsNode())
            assertTrue("cover ${cover.bottom - cover.top}", cover.bottom - cover.top > minCover)
        }
    }

    private fun assertCoverGaps(fontScale: Float = 1f) {
        show(PlayerLayout.Compact, LONG_TITLE, downloaded = true, fontScale = fontScale)

        val cover = bounds(composeTestRule.onNodeWithTag("cover", useUnmergedTree = true).fetchSemanticsNode())
        val title = bounds(titleNode())
        val collapse = bounds(composeTestRule.onNodeWithContentDescription("Collapse").fetchSemanticsNode())
        val album = bounds(composeTestRule.onNodeWithText(ALBUM, useUnmergedTree = true).fetchSemanticsNode())
        val topBarBottom = maxOf(collapse.bottom, album.bottom)

        val side = (cover.right - cover.left).value
        assertEquals("cover not square: $cover", side, (cover.bottom - cover.top).value, 0.5f)
        assertTrue("gap cover→title ${title.top - cover.bottom}", title.top - cover.bottom >= MIN_GAP - TOLERANCE)
        assertTrue("gap top bar→cover ${cover.top - topBarBottom}", cover.top - topBarBottom >= MIN_GAP - TOLERANCE)
    }

    private fun assertHeartOnFirstLine(
        layout: PlayerLayout,
        title: String,
        lines: Int?,
        downloaded: Boolean = false,
        fontScale: Float = 1f,
        sidePanel: Boolean = false,
        splitPaneWidth: Dp = Dp.Unspecified,
    ) {
        show(layout, title, downloaded, fontScale, sidePanel, splitPaneWidth)

        val text = textLayout(titleNode())
        if (lines != null) assertEquals("title lines", lines, text.lineCount)
        assertHeartOnFirstLine(text)
    }

    private fun assertHeartOnFirstLine(text: TextLayoutResult) {
        val titleBounds = bounds(titleNode())
        val firstLineCentre = with(composeTestRule.density) {
            titleBounds.top + ((text.getLineTop(0) + text.getLineBottom(0)) / 2f).toDp()
        }

        val heart = heartBounds(titleBounds)
        val heartCentre = (heart.top + heart.bottom) / 2f
        assertTrue(
            "heart centre $heartCentre vs first line centre $firstLineCentre",
            abs((heartCentre - firstLineCentre).value) <= 1f,
        )
    }

    /** The heart: the clickable, unlabelled 43.2 dp box to the right of the title. */
    private fun heartBounds(title: DpRect): DpRect {
        val candidates = composeTestRule.onAllNodes(hasClickAction(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .filter { it.config.getOrNull(SemanticsProperties.ContentDescription) == null }
            .filter { it.config.getOrNull(SemanticsProperties.Text) == null }
            .map { bounds(it) }
            .filter { abs((it.right - it.left - HEART_BOX).value) < 0.5f }
            .filter { abs((it.bottom - it.top - HEART_BOX).value) < 0.5f }
            .filter { it.left >= title.right - 1.dp }
        assertEquals("heart candidates: $candidates", 1, candidates.size)
        return candidates.single()
    }

    private fun titleNode(): SemanticsNode =
        composeTestRule.onNodeWithText(currentTitle, useUnmergedTree = true).fetchSemanticsNode()

    private fun textLayout(node: SemanticsNode): TextLayoutResult {
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

    private var currentTitle = ""

    private fun show(
        layout: PlayerLayout,
        title: String,
        downloaded: Boolean = false,
        fontScale: Float = 1f,
        sidePanel: Boolean = false,
        splitPaneWidth: Dp = Dp.Unspecified,
        systemBars: Boolean = true,
    ) {
        currentTitle = title
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
                            splitPaneWidth = splitPaneWidth,
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
        const val VERY_LONG_TITLE =
            "Let the Water Wash Away Your Sins (Reinterpretation) [Live at the Royal Albert Hall, 2019 Remaster]"
        const val ALBUM = "Far Cry 5 Presents: We Will Rise Again (Original Game Soundtrack)"
        val MIN_GAP = 24.dp
        val TOLERANCE = 0.5.dp
        val HEART_BOX = 43.2.dp

        /** Compact: the size below which the gaps around the cover give way. */
        val COVER_MIN = 120.dp
        val STATUS_BAR = 32.dp
        val NAVIGATION_BAR = 48.dp

        /** Pixel 10 Pro Fold, open: the hinge's x (1038 px at 420 dpi). */
        val FOLD_HINGE = 395.dp
    }
}
