package dev.mellow.core.designsystem.component

import kotlin.math.abs
import kotlin.math.sign
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier

/**
 * The pages behind a [MellowTabBar]: one per tab, swiped sideways. The bar draws its indicator from [pagerState], and
 * a tapped tab scrolls the pages with [animateToTab].
 */
@Stable
class MellowTabPagerState internal constructor(val pagerState: PagerState) {

    /** During a tap's jump over other tabs: the page that stands in for the tab the jump started from. */
    private var standIn by mutableStateOf<StandIn?>(null)

    /**
     * The tab whose content [page] shows: its own, except during a jump, when the stand-in shows the tab the jump
     * started from and that tab's own page, now out of view, shows nothing (-1), so no content is composed twice.
     */
    fun contentPageFor(page: Int): Int {
        val jump = standIn ?: return page
        return when (page) {
            jump.page -> jump.tab
            jump.tab -> NO_CONTENT
            else -> page
        }
    }

    /**
     * Scrolls to [tab] the way a tap does. To a neighbour this is the pager's own animation. Further away the pager
     * would pass every tab in between, or jump three pages and slide past the last one in between; instead the page
     * next to [tab] takes on the content the jump starts from, the pager moves there (nothing seen changes) and slides
     * the last page, so [tab] comes in next to where the jump started, like a neighbour. [tab] is composed as the
     * stand-in's neighbour during the move, so the slide doesn't start with a long frame.
     */
    suspend fun animateToTab(tab: Int) {
        val from = pagerState.currentPage
        val distance = tab - from
        if (abs(distance) <= 1) {
            pagerState.animateScrollToPage(tab)
            return
        }
        val offset = pagerState.currentPageOffsetFraction
        val via = tab - distance.sign
        pagerState.scroll {
            with(pagerState) {
                updateTargetPage(tab)
                standIn = StandIn(page = via, tab = from)
                try {
                    updateCurrentPage(via, offset)
                    val pageSize = layoutInfo.pageSize + layoutInfo.pageSpacing
                    val target = (tab - via - offset) * pageSize
                    var scrolled = 0f
                    animate(0f, target, animationSpec = spring()) { value, _ ->
                        scrolled += scrollBy(value - scrolled)
                    }
                } finally {
                    standIn = null
                }
            }
        }
    }

    /** Shows [tab] at once, for when animations are off. */
    suspend fun jumpToTab(tab: Int) {
        pagerState.scrollToPage(tab)
    }

    /**
     * The key of [page]'s content. The stand-in takes the key of the tab it stands in for, so the pager moves that
     * tab's composition (with its scroll position) to the stand-in's place instead of composing it again; the tab's
     * own page gets a new, empty one.
     */
    internal fun keyFor(page: Int): Int = when (val tab = contentPageFor(page)) {
        NO_CONTENT -> -1 - page
        else -> tab
    }

    private data class StandIn(val page: Int, val tab: Int)

    internal companion object {
        const val NO_CONTENT = -1
    }
}

/**
 * A [MellowTabPagerState] for [pageCount] tabs, kept in step with the screen's own [selectedTab] both ways: the
 * pages start on it and show it when it changes, and every page the pager settles on is reported through
 * [onSelectedTabChange].
 */
@Composable
fun rememberMellowTabPagerState(
    pageCount: Int,
    selectedTab: Int,
    onSelectedTabChange: (Int) -> Unit,
): MellowTabPagerState {
    val pagerState = rememberPagerState(initialPage = selectedTab) { pageCount }
    val state = remember(pagerState) { MellowTabPagerState(pagerState) }
    val currentOnChange by rememberUpdatedState(onSelectedTabChange)
    LaunchedEffect(state) {
        snapshotFlow { pagerState.settledPage }.collect { currentOnChange(it) }
    }
    LaunchedEffect(state, selectedTab) {
        if (selectedTab != pagerState.settledPage && !pagerState.isScrollInProgress) {
            pagerState.scrollToPage(selectedTab)
        }
    }
    return state
}

/**
 * The tabs' pages, side by side. [pageContent] is given the tab to show; see [MellowTabPagerState.animateToTab].
 * The pages next to the one showing stay composed ([beyondViewportPageCount]), so a swipe or a tap never waits for the
 * next page to be built while it is already moving.
 */
@Composable
fun MellowTabPager(
    state: MellowTabPagerState,
    modifier: Modifier = Modifier,
    beyondViewportPageCount: Int = 1,
    pageContent: @Composable (tab: Int) -> Unit,
) {
    HorizontalPager(
        state = state.pagerState,
        modifier = modifier,
        beyondViewportPageCount = beyondViewportPageCount,
        key = state::keyFor,
    ) { page ->
        val tab = state.contentPageFor(page)
        if (tab != MellowTabPagerState.NO_CONTENT) pageContent(tab)
    }
}
