package dev.mellow.app.screenshot

import org.junit.Test
import dev.mellow.feature.home.FavoritesContent
import dev.mellow.feature.home.HomeScreen
import dev.mellow.feature.home.PlaylistDetailScreen
import dev.mellow.feature.home.PlaylistsScreen

abstract class HomeScreenshotTests : ScreenshotCapture() {

    @Test
    fun homeEmpty() = capture("home-empty") {
        HomeScreen()
    }

    @Test
    fun homePopulated() = capture("home-populated") {
        HomeScreen(
            quickPicks = ScreenshotData.homeAlbums.take(6),
            recentlyPlayed = ScreenshotData.homeAlbums.drop(6),
            recentlyAdded = ScreenshotData.homeAlbums.take(4),
            favoriteTracks = ScreenshotData.homeTracks,
            genres = ScreenshotData.genres,
        )
    }

    @Test
    fun favoritesLoading() = capture("favorites-loading") {
        FavoritesContent(isLoading = true)
    }

    @Test
    fun favoritesEmptyTracks() = capture("favorites-empty-tracks") {
        FavoritesContent(selectedTab = 0)
    }

    @Test
    fun favoritesPopulatedTracks() = capture("favorites-populated-tracks") {
        FavoritesContent(
            tracks = ScreenshotData.favoriteTracks,
            selectedTab = 0,
        )
    }

    @Test
    fun favoritesPopulatedAlbums() = capture("favorites-populated-albums") {
        FavoritesContent(
            albums = ScreenshotData.favoriteAlbums,
            selectedTab = 1,
        )
    }

    @Test
    fun favoritesPopulatedArtists() = capture("favorites-populated-artists") {
        FavoritesContent(
            artists = ScreenshotData.favoriteArtists,
            selectedTab = 2,
        )
    }

    @Test
    fun playlistsEmpty() = capture("playlists-empty") {
        PlaylistsScreen()
    }

    @Test
    fun playlistsPopulated() = capture("playlists-populated") {
        PlaylistsScreen(playlists = ScreenshotData.playlists)
    }

    @Test
    fun playlistDetailLoading() = capture("playlist-detail-loading") {
        PlaylistDetailScreen(onBack = {}, isLoading = true)
    }

    @Test
    fun playlistDetailEmpty() = capture("playlist-detail-empty") {
        PlaylistDetailScreen(onBack = {}, playlistName = "Late Night Vibes")
    }

    @Test
    fun playlistDetailPopulated() = capture("playlist-detail-populated") {
        PlaylistDetailScreen(
            onBack = {},
            playlistName = "Late Night Vibes",
            tracks = ScreenshotData.playlistDetailTracks,
        )
    }
}
