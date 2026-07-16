package dev.mellow.app.screenshot

import org.junit.Test
import dev.mellow.feature.search.SearchContent
import dev.mellow.feature.search.SearchResult

abstract class SearchScreenshotTests : ScreenshotCapture() {

    @Test
    fun searchInitial() = capture("search-initial") {
        SearchContent()
    }

    @Test
    fun searchNoResults() = capture("search-no-results") {
        SearchContent(
            query = "xyznonexistent",
            hasResults = false,
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
        )
    }
}
