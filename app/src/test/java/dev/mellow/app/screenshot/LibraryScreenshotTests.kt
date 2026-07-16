package dev.mellow.app.screenshot

import org.junit.Test
import dev.mellow.feature.library.AlbumDetailScreen
import dev.mellow.feature.library.ArtistDetailScreen
import dev.mellow.feature.library.LibraryScreen

abstract class LibraryScreenshotTests : ScreenshotCapture() {

    @Test
    fun libraryLoading() = capture("library-loading") {
        LibraryScreen(isLoading = true)
    }

    @Test
    fun libraryEmpty() = capture("library-empty") {
        LibraryScreen()
    }

    @Test
    fun libraryAlbumsPopulated() = capture("library-albums-populated") {
        LibraryScreen(albumItems = ScreenshotData.albumItems, initialTab = 0)
    }

    @Test
    fun libraryArtistsPopulated() = capture("library-artists-populated") {
        LibraryScreen(artists = ScreenshotData.artistItems, initialTab = 1)
    }

    @Test
    fun libraryTracksPopulated() = capture("library-tracks-populated") {
        LibraryScreen(tracks = ScreenshotData.trackItems, initialTab = 2)
    }

    @Test
    fun libraryGenresPopulated() = capture("library-genres-populated") {
        LibraryScreen(genres = ScreenshotData.genres, initialTab = 3)
    }

    @Test
    fun libraryPlaylistsPopulated() = capture("library-playlists-populated") {
        LibraryScreen(playlists = ScreenshotData.libraryPlaylists, initialTab = 4)
    }

    @Test
    fun albumDetailLoading() = capture("album-detail-loading") {
        AlbumDetailScreen(onBack = {}, isLoading = true)
    }

    @Test
    fun albumDetailError() = capture("album-detail-error") {
        AlbumDetailScreen(onBack = {}, error = "Failed to load album")
    }

    @Test
    fun albumDetailPopulated() = capture("album-detail-populated") {
        AlbumDetailScreen(
            onBack = {},
            albumName = "OK Computer",
            artistName = "Radiohead",
            year = 1997,
            tracks = ScreenshotData.albumDetailTracks,
            isFavorite = true,
        )
    }

    @Test
    fun artistDetailLoading() = capture("artist-detail-loading") {
        ArtistDetailScreen(onBack = {}, isLoading = true)
    }

    @Test
    fun artistDetailError() = capture("artist-detail-error") {
        ArtistDetailScreen(onBack = {}, error = "Failed to load artist")
    }

    @Test
    fun artistDetailPopulated() = capture("artist-detail-populated") {
        ArtistDetailScreen(
            onBack = {},
            artistName = "Radiohead",
            topTracks = ScreenshotData.artistTracks,
            albums = ScreenshotData.artistAlbums,
        )
    }
}
