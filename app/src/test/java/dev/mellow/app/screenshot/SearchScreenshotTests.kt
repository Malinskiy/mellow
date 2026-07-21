package dev.mellow.app.screenshot

import androidx.compose.ui.unit.dp
import org.junit.Test
import dev.mellow.core.designsystem.theme.WindowWidthClass
import dev.mellow.feature.search.SearchContent
import dev.mellow.feature.search.SearchResult

abstract class SearchScreenshotTests : ScreenshotCapture() {

    private val isExpanded: Boolean
        get() = windowWidthClass != WindowWidthClass.Compact

    private val hingeSplitWidth
        get() = if (foldableState.hasVerticalFold) {
            with(composeTestRule.density) { foldableState.hingeBounds.left.toDp() }
        } else {
            null
        }

    @Test
    fun searchInitial() = capture("search-initial") {
        SearchContent(isExpanded = isExpanded, hingeSplitWidth = hingeSplitWidth)
    }

    @Test
    fun searchNoResults() = capture("search-no-results") {
        SearchContent(
            query = "xyznonexistent",
            hasResults = false,
            isExpanded = isExpanded,
            hingeSplitWidth = hingeSplitWidth,
        )
    }

    @Test
    fun searchWithResults() = capture("search-with-results") {
        SearchContent(
            query = "radiohead",
            tracks = ScreenshotData.searchTracks,
            albums = ScreenshotData.searchAlbums,
            artists = ScreenshotData.searchArtists,
            topResult = SearchResult.ArtistResult(ScreenshotData.searchArtists.first()),
            hasResults = true,
            isExpanded = isExpanded,
            hingeSplitWidth = hingeSplitWidth,
        )
    }
}
