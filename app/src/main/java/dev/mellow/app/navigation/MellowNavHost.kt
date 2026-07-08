package dev.mellow.app.navigation

import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.mellow.app.AuthState
import dev.mellow.app.MainViewModel
import dev.mellow.core.designsystem.component.MellowBottomNavBar
import dev.mellow.core.designsystem.component.MellowNavDestination
import dev.mellow.core.designsystem.component.MiniPlayer
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.common.jellyfinImageUrl
import dev.mellow.feature.home.FavoritesScreen
import dev.mellow.feature.home.PlaylistsScreen
import dev.mellow.feature.library.AlbumDetailScreen
import dev.mellow.feature.library.AlbumDetailTrack
import dev.mellow.feature.library.AlbumDetailViewModel
import dev.mellow.feature.library.AlbumItem
import dev.mellow.feature.library.ArtistAlbum
import dev.mellow.feature.library.ArtistDetailScreen
import dev.mellow.feature.library.ArtistDetailViewModel
import dev.mellow.feature.library.ArtistItem
import dev.mellow.feature.library.ArtistTrack
import dev.mellow.feature.library.LibraryScreen
import dev.mellow.feature.library.LibraryViewModel
import dev.mellow.feature.library.TrackItem
import dev.mellow.feature.player.PlayerScreen
import dev.mellow.feature.player.QueueScreen
import dev.mellow.feature.player.QueueTrack
import dev.mellow.feature.search.SearchScreen
import dev.mellow.feature.settings.LoginScreen
import dev.mellow.feature.settings.LoginViewModel
import dev.mellow.feature.settings.SettingsScreen
import kotlinx.coroutines.launch
import java.time.Duration

@Composable
fun MellowNavHost(mainViewModel: MainViewModel = hiltViewModel()) {
    val authState by mainViewModel.authState.collectAsState()

    when (authState) {
        AuthState.CHECKING -> {
            Box(
                Modifier.fillMaxSize().background(MellowTheme.colors.background),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = MellowTheme.colors.foreground)
            }
        }
        AuthState.LOGGED_OUT -> {
            LoginFlow(onLoggedIn = { serverId -> mainViewModel.onLoggedIn(serverId) })
        }
        AuthState.LOGGED_IN -> {
            val serverId by mainViewModel.serverId.collectAsState()
            MainAppShell(serverId = serverId ?: "", mainViewModel = mainViewModel)
        }
    }
}

@Composable
private fun LoginFlow(onLoggedIn: (String) -> Unit) {
    val loginViewModel: LoginViewModel = hiltViewModel()
    val loginState by loginViewModel.uiState.collectAsState()

    LaunchedEffect(loginState.server) {
        loginState.server?.let { server -> onLoggedIn(server.id) }
    }

    LoginScreen(
        onSignIn = { serverUrl, username, password ->
            loginViewModel.signIn(serverUrl, username, password)
        },
        isLoading = loginState.isLoading,
        error = loginState.error,
    )
}

@Composable
private fun MainAppShell(serverId: String, mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: MellowNavDestination.Library.route
    val scope = rememberCoroutineScope()

    val fullScreenRoutes = setOf("now_playing", "queue")
    val isFullScreen = currentRoute in fullScreenRoutes
    val playbackState by mainViewModel.player.state.collectAsState()
    val isSyncing by mainViewModel.isSyncing.collectAsState()

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            if (!isFullScreen) {
                Column {
                    if (playbackState.currentTrack != null) {
                        val track = playbackState.currentTrack!!
                        val sUrl = mainViewModel.serverUrl.collectAsState().value
                        MiniPlayer(
                            title = track.name,
                            artist = track.artistName ?: "",
                            imageUrl = if (sUrl != null && track.imageId != null) {
                                jellyfinImageUrl(sUrl, track.imageId!!)
                            } else null,
                            isPlaying = playbackState.isPlaying,
                            progress = if (playbackState.durationMs > 0) {
                                playbackState.positionMs.toFloat() / playbackState.durationMs
                            } else 0f,
                            onPlayPauseClick = { mainViewModel.player.playPause() },
                            onNextClick = { mainViewModel.player.skipNext() },
                            onClick = { navController.navigate("now_playing") },
                            modifier = Modifier.padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp1),
                        )
                    }
                    MellowBottomNavBar(
                        selectedRoute = currentRoute,
                        onNavigate = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            NavHost(
                navController = navController,
                startDestination = MellowNavDestination.Library.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
                composable(MellowNavDestination.Library.route) {
                    val libraryVm: LibraryViewModel = hiltViewModel()
                    val state by libraryVm.uiState.collectAsState()
                    var currentSort by rememberSaveable { mutableStateOf("Recently Added") }

                    LaunchedEffect(serverId) {
                        if (serverId.isNotEmpty()) libraryVm.loadLibrary(serverId)
                    }

                    val albumCountByArtist = state.albums.groupingBy { it.artistId }.eachCount()

                    LibraryScreen(
                        albumItems = state.albums.map { AlbumItem(it.id, it.name, it.artistName ?: "", it.imageId) },
                        artists = state.artists.map { ArtistItem(it.id, it.name, albumCountByArtist[it.id] ?: 0, it.imageId) },
                        tracks = state.tracks.map { track ->
                            TrackItem(
                                id = track.id,
                                title = track.name,
                                artist = track.artistName ?: "",
                                album = track.albumName ?: "",
                                duration = formatTrackDuration(track.duration),
                                imageId = track.imageId,
                            )
                        },
                        genres = state.albums.flatMap { it.genres }.distinct().sorted(),
                        serverUrl = mainViewModel.serverUrl.collectAsState().value,
                        isLoading = state.isLoading,
                        isSyncing = isSyncing,
                        sortLabel = currentSort,
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                        onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                        onTrackClick = { trackId ->
                            val tracks = state.tracks
                            val idx = tracks.indexOfFirst { it.id == trackId }
                            if (idx >= 0) {
                                scope.launch { mainViewModel.player.playTracks(tracks, idx) }
                            }
                        },
                        onSettingsClick = { navController.navigate("settings") },
                        onSortChanged = { sort -> currentSort = sort },
                    )
                }
                composable(MellowNavDestination.Search.route) {
                    SearchScreen(
                        serverId = serverId,
                        onPlayTracks = { tracks, index ->
                            scope.launch { mainViewModel.player.playTracks(tracks, index) }
                        },
                    )
                }
                composable(MellowNavDestination.Favorites.route) {
                    FavoritesScreen(
                        serverId = serverId,
                        serverUrl = mainViewModel.serverUrl.collectAsState().value,
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                        onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                    )
                }
                composable(MellowNavDestination.Playlists.route) {
                    val context = LocalContext.current
                    PlaylistsScreen(
                        onCreatePlaylist = { name ->
                            android.widget.Toast.makeText(context, "Playlist '$name' created", android.widget.Toast.LENGTH_SHORT).show()
                        },
                    )
                }
                composable("settings") {
                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        serverUrl = mainViewModel.serverUrl.collectAsState().value ?: "",
                    )
                }
                composable("album/{albumId}") {
                    val albumVm: AlbumDetailViewModel = hiltViewModel()
                    val albumState by albumVm.uiState.collectAsState()
                    val sUrl = mainViewModel.serverUrl.collectAsState().value

                    AlbumDetailScreen(
                        onBack = { navController.popBackStack() },
                        albumName = albumState.album?.name ?: "",
                        artistName = albumState.album?.artistName ?: "",
                        albumImageUrl = if (sUrl != null && albumState.album?.imageId != null) {
                            jellyfinImageUrl(sUrl, albumState.album!!.imageId!!)
                        } else null,
                        year = albumState.album?.year,
                        expectedTrackCount = albumState.album?.trackCount ?: 0,
                        tracks = albumState.tracks.map { track ->
                            AlbumDetailTrack(
                                id = track.id,
                                title = track.name,
                                artistName = track.artistName ?: "",
                                duration = formatTrackDuration(track.duration),
                                trackNumber = track.trackNumber,
                                isFavorite = track.isFavorite,
                            )
                        },
                        isLoading = albumState.isLoading,
                        isSyncing = isSyncing,
                        error = albumState.error,
                        onRetry = { albumVm.retry() },
                        onTrackClick = { trackId ->
                            val tracks = albumState.tracks
                            val idx = tracks.indexOfFirst { it.id == trackId }
                            if (idx >= 0) {
                                scope.launch { mainViewModel.player.playTracks(tracks, idx) }
                            }
                        },
                        onPlayAll = {
                            val tracks = albumState.tracks
                            if (tracks.isNotEmpty()) {
                                scope.launch { mainViewModel.player.playTracks(tracks, 0) }
                            }
                        },
                        onShuffle = {
                            val tracks = albumState.tracks
                            if (tracks.isNotEmpty()) {
                                scope.launch { mainViewModel.player.playTracks(tracks.shuffled(), 0) }
                            }
                        },
                        onFavoriteClick = {
                            val album = albumState.album
                            if (album != null) {
                                mainViewModel.toggleFavorite(album.id, album.isFavorite)
                            }
                        },
                        onTrackFavoriteClick = { trackId ->
                            val track = albumState.tracks.find { it.id == trackId }
                            if (track != null) {
                                mainViewModel.toggleFavorite(trackId, track.isFavorite)
                            }
                        },
                    )
                }
                composable("artist/{artistId}") {
                    val artistVm: ArtistDetailViewModel = hiltViewModel()
                    val artistState by artistVm.uiState.collectAsState()
                    val sUrl = mainViewModel.serverUrl.collectAsState().value

                    ArtistDetailScreen(
                        onBack = { navController.popBackStack() },
                        artistName = artistState.artist?.name ?: "",
                        artistImageUrl = if (sUrl != null && artistState.artist?.imageId != null) {
                            jellyfinImageUrl(sUrl, artistState.artist!!.imageId!!)
                        } else null,
                        albumCount = artistState.artist?.albumCount ?: 0,
                        overview = artistState.artist?.overview,
                        topTracks = artistState.topTracks.map { track ->
                            ArtistTrack(
                                id = track.id,
                                title = track.name,
                                duration = formatTrackDuration(track.duration),
                                albumName = track.albumName ?: "",
                            )
                        },
                        albums = artistState.albums.map { album ->
                            ArtistAlbum(
                                id = album.id,
                                name = album.name,
                                year = album.year,
                                imageId = album.imageId,
                            )
                        },
                        isLoading = artistState.isLoading,
                        isSyncing = isSyncing,
                        error = artistState.error,
                        onRetry = { artistVm.retry() },
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                        onTrackClick = { trackId ->
                            val tracks = artistState.topTracks
                            val idx = tracks.indexOfFirst { it.id == trackId }
                            if (idx >= 0) {
                                scope.launch { mainViewModel.player.playTracks(tracks, idx) }
                            }
                        },
                        serverUrl = sUrl,
                        onPlayAll = {
                            val tracks = artistState.topTracks
                            if (tracks.isNotEmpty()) {
                                scope.launch { mainViewModel.player.playTracks(tracks, 0) }
                            }
                        },
                        onShuffle = {
                            val tracks = artistState.topTracks
                            if (tracks.isNotEmpty()) {
                                scope.launch { mainViewModel.player.playTracks(tracks.shuffled(), 0) }
                            }
                        },
                        onFavoriteClick = {
                            val artist = artistState.artist
                            if (artist != null) {
                                mainViewModel.toggleFavorite(artist.id, artist.isFavorite)
                            }
                        },
                    )
                }
                composable("now_playing") {
                    val sUrl = mainViewModel.serverUrl.collectAsState().value
                    val pState = playbackState
                    val track = pState.currentTrack
                    PlayerScreen(
                        trackName = track?.name ?: "",
                        artistName = track?.artistName ?: "",
                        albumName = track?.albumName ?: "",
                        albumImageUrl = if (sUrl != null && track?.imageId != null) {
                            jellyfinImageUrl(sUrl, track.imageId!!)
                        } else null,
                        isPlaying = pState.isPlaying,
                        progress = if (pState.durationMs > 0) {
                            pState.positionMs.toFloat() / pState.durationMs
                        } else 0f,
                        positionMs = pState.positionMs,
                        durationMs = pState.durationMs,
                        isFavorite = track?.isFavorite ?: false,
                        onCollapse = { navController.popBackStack() },
                        onQueueClick = { navController.navigate("queue") },
                        onPlayPauseClick = { mainViewModel.player.playPause() },
                        onSkipNextClick = { mainViewModel.player.skipNext() },
                        onSkipPreviousClick = { mainViewModel.player.skipPrevious() },
                        onSeekTo = { ms -> mainViewModel.player.seekTo(ms) },
                        shuffleEnabled = pState.shuffleEnabled,
                        repeatMode = pState.repeatMode,
                        onShuffleClick = { mainViewModel.player.toggleShuffle() },
                        onRepeatClick = { mainViewModel.player.cycleRepeatMode() },
                        onFavoriteClick = {
                            if (track != null) {
                                mainViewModel.toggleFavorite(track.id, track.isFavorite)
                            }
                        },
                        codec = track?.codec,
                    )
                }
                composable("queue") {
                    val sUrl = mainViewModel.serverUrl.collectAsState().value
                    val pState = playbackState
                    val currentIdx = pState.currentIndex
                    val queue = pState.queue

                    val nowPlaying = pState.currentTrack?.let { track ->
                        QueueTrack(
                            id = track.id,
                            title = track.name,
                            artist = track.artistName ?: "",
                            album = track.albumName ?: "",
                            duration = formatTrackDuration(track.duration),
                            imageUrl = if (sUrl != null && track.imageId != null) {
                                jellyfinImageUrl(sUrl, track.imageId!!)
                            } else null,
                        )
                    }

                    val upNext = queue
                        .filterIndexed { idx, _ -> idx > currentIdx }
                        .map { track ->
                            QueueTrack(
                                id = track.id,
                                title = track.name,
                                artist = track.artistName ?: "",
                                album = track.albumName ?: "",
                                duration = formatTrackDuration(track.duration),
                                imageUrl = if (sUrl != null && track.imageId != null) {
                                    jellyfinImageUrl(sUrl, track.imageId!!)
                                } else null,
                            )
                        }

                    QueueScreen(
                        onBack = { navController.popBackStack() },
                        nowPlaying = nowPlaying,
                        upNext = upNext,
                        shuffleEnabled = pState.shuffleEnabled,
                        onTrackClick = { relativeIndex ->
                            mainViewModel.player.playFromQueue(currentIdx + 1 + relativeIndex)
                        },
                        onShuffleClick = { mainViewModel.player.toggleShuffle() },
                        onClearClick = {
                            mainViewModel.player.clearQueue()
                            navController.popBackStack()
                        },
                    )
                }
            }
        }
    }
}

private fun formatTrackDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
