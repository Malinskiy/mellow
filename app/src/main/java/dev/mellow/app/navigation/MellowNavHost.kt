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
import dev.mellow.core.player.PositionState
import dev.mellow.core.designsystem.component.MellowNavDestination
import dev.mellow.core.designsystem.component.MiniPlayer
import dev.mellow.core.designsystem.component.TrackContextMenu
import dev.mellow.core.designsystem.component.TrackMenuData
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.network.ConnectionState
import dev.mellow.core.common.jellyfinImageUrl
import dev.mellow.core.model.AlbumDownloadState
import dev.mellow.core.model.DownloadState
import dev.mellow.core.designsystem.component.AddToPlaylistSheet
import dev.mellow.core.designsystem.component.PlaylistPickerItem
import dev.mellow.core.model.Track
import dev.mellow.feature.home.FavoritesScreen
import dev.mellow.feature.home.FavoritesViewModel
import dev.mellow.feature.home.HomeScreen
import dev.mellow.feature.home.HomeViewModel
import dev.mellow.feature.home.PlaylistDetailScreen
import dev.mellow.feature.home.PlaylistDetailTrack
import dev.mellow.feature.home.PlaylistDetailViewModel
import dev.mellow.feature.home.PlaylistItem
import dev.mellow.feature.home.PlaylistsScreen
import dev.mellow.feature.home.PlaylistsViewModel
import dev.mellow.feature.library.AlbumDetailScreen
import dev.mellow.feature.library.AlbumDetailTrack
import dev.mellow.feature.library.AlbumDetailViewModel
import dev.mellow.feature.library.AlbumItem
import dev.mellow.feature.library.ArtistAlbum
import dev.mellow.feature.library.ArtistDetailScreen
import dev.mellow.feature.library.ArtistDetailViewModel
import dev.mellow.feature.library.ArtistItem
import dev.mellow.feature.library.ArtistTrack
import dev.mellow.feature.library.LibraryPlaylistItem
import dev.mellow.feature.library.LibraryScreen
import dev.mellow.feature.library.LibraryViewModel
import dev.mellow.feature.library.TrackDownloadIndicator
import dev.mellow.feature.library.TrackItem
import dev.mellow.feature.player.LyricsLine
import dev.mellow.feature.player.LyricsScreen
import dev.mellow.feature.player.PlayerScreen
import dev.mellow.feature.player.QueueScreen
import dev.mellow.feature.player.QueueTrack
import dev.mellow.feature.search.SearchScreen
import dev.mellow.feature.search.SearchViewModel
import dev.mellow.feature.settings.LoginScreen
import dev.mellow.feature.settings.LoginViewModel
import dev.mellow.feature.settings.SettingsScreen
import dev.mellow.feature.settings.SettingsViewModel
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
    val currentRoute = navBackStackEntry?.destination?.route ?: MellowNavDestination.Home.route
    val scope = rememberCoroutineScope()

    val fullScreenRoutes = setOf("now_playing", "queue", "lyrics")
    val isFullScreen = currentRoute in fullScreenRoutes
    val playbackState by mainViewModel.player.state.collectAsState()
    val positionState by mainViewModel.player.positionState.collectAsState()
    val isSyncing by mainViewModel.isSyncing.collectAsState()
    val serverUrl by mainViewModel.serverUrl.collectAsState()
    val connectionState by mainViewModel.connectionState.collectAsState()

    var contextMenuState by remember { mutableStateOf<ContextMenuState?>(null) }
    var showAddToPlaylistSheet by remember { mutableStateOf(false) }
    var addToPlaylistTrackId by remember { mutableStateOf<String?>(null) }
    val playlistsVm: PlaylistsViewModel = hiltViewModel()
    val playlistsState by playlistsVm.uiState.collectAsState()

    LaunchedEffect(serverId) {
        if (serverId.isNotEmpty()) playlistsVm.loadPlaylists(serverId)
    }

    fun openContextMenu(track: Track, sUrl: String?) {
        contextMenuState = ContextMenuState(
            menuData = TrackMenuData(
                id = track.id,
                title = track.name,
                artist = track.artistName ?: "",
                album = track.albumName ?: "",
                albumId = track.albumId,
                artistId = track.artistId,
                                imageUrl = if (serverUrl != null && track.imageId != null) {
                                    jellyfinImageUrl(serverUrl!!, track.imageId!!)
                } else null,
                isFavorite = track.isFavorite,
                isDownloaded = false,
            ),
            track = track,
        )
    }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            if (!isFullScreen) {
                Column {
                    if (playbackState.currentTrack != null) {
                        val track = playbackState.currentTrack!!
                        MiniPlayer(
                            title = track.name,
                            artist = track.artistName ?: "",
                            imageUrl = if (serverUrl != null && track.imageId != null) {
                                jellyfinImageUrl(serverUrl!!, track.imageId!!)
                            } else null,
                            isPlaying = playbackState.isPlaying,
                            progress = if (positionState.durationMs > 0) {
                                positionState.positionMs.toFloat() / positionState.durationMs
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
                startDestination = MellowNavDestination.Home.route,
                enterTransition = { EnterTransition.None },
                exitTransition = { ExitTransition.None },
            ) {
                composable(MellowNavDestination.Home.route) {
                    val homeVm: HomeViewModel = hiltViewModel()
                    val homeState by homeVm.uiState.collectAsState()
                    LaunchedEffect(serverId) {
                        if (serverId.isNotEmpty()) homeVm.loadHome(serverId)
                    }

                    HomeScreen(
                        recentlyPlayed = homeState.recentlyPlayed,
                        recentlyAdded = homeState.recentlyAdded,
                        favoriteTracks = homeState.favoriteTracks,
                        genres = homeState.genres,
                        albumCount = homeState.albumCount,
                        serverUrl = serverUrl,
                        isConnected = connectionState is ConnectionState.Connected,
                        isServerUnreachable = connectionState is ConnectionState.ServerUnreachable,
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                        onTrackClick = { trackId ->
                            val favTracks = homeVm.uiState.value.favoriteTracks
                            val idx = favTracks.indexOfFirst { it.id == trackId }
                            if (idx >= 0) {
                                scope.launch {
                                    mainViewModel.player.playTracks(
                                        homeVm.favTrackModels.value,
                                        idx,
                                    )
                                }
                            }
                        },
                        onTrackMenuClick = { trackId ->
                            val track = homeVm.favTrackModels.value.find { it.id == trackId }
                            if (track != null) {
                                openContextMenu(track, mainViewModel.serverUrl.value)
                            }
                        },
                        onSettingsClick = { navController.navigate("settings") },
                    )
                }
                composable(MellowNavDestination.Library.route) {
                    val libraryVm: LibraryViewModel = hiltViewModel()
                    val state by libraryVm.uiState.collectAsState()
                    var currentSort by rememberSaveable { mutableStateOf("Recently Added") }

                    LaunchedEffect(serverId) {
                        if (serverId.isNotEmpty()) libraryVm.loadLibrary(serverId)
                    }

                    val albumCountByArtist = state.albums.groupingBy { it.artistId }.eachCount()

                    val albumItems = remember(state.albums) {
                        state.albums.map { AlbumItem(it.id, it.name, it.artistName ?: "", it.imageId) }
                    }
                    val artists = remember(state.artists, albumCountByArtist) {
                        state.artists.map { ArtistItem(it.id, it.name, albumCountByArtist[it.id] ?: 0, it.imageId) }
                    }
                    val tracks = remember(state.tracks) {
                        state.tracks.map { track ->
                            TrackItem(
                                id = track.id,
                                title = track.name,
                                artist = track.artistName ?: "",
                                album = track.albumName ?: "",
                                duration = formatTrackDuration(track.duration),
                                imageId = track.imageId,
                            )
                        }
                    }
                    val genres = remember(state.albums) {
                        state.albums.flatMap { it.genres }.distinct().sorted()
                    }
                    val playlists = remember(playlistsState.playlists) {
                        playlistsState.playlists.map { pl ->
                            LibraryPlaylistItem(pl.id, pl.name, pl.trackCount, pl.imageId)
                        }
                    }

                    LibraryScreen(
                        albumItems = albumItems,
                        artists = artists,
                        tracks = tracks,
                        genres = genres,
                        playlists = playlists,
                        serverUrl = serverUrl,
                        isLoading = state.isLoading,
                        isSyncing = isSyncing,
                        isConnected = connectionState is ConnectionState.Connected,
                        isServerUnreachable = connectionState is ConnectionState.ServerUnreachable,
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
                        onTrackMenuClick = { trackId ->
                            val track = state.tracks.find { it.id == trackId }
                            if (track != null) {
                                openContextMenu(track, mainViewModel.serverUrl.value)
                            }
                        },
                        onPlaylistClick = { playlistId -> navController.navigate("playlist/$playlistId") },
                        onCreatePlaylist = { name -> playlistsVm.createPlaylist(name) },
                        onSettingsClick = { navController.navigate("settings") },
                        onSortChanged = { sort -> currentSort = sort },
                    )
                }
                composable(MellowNavDestination.Search.route) {
                    val searchVm: SearchViewModel = hiltViewModel()
                    val searchState by searchVm.uiState.collectAsState()

                    SearchScreen(
                        serverId = serverId,
                        serverUrl = serverUrl ?: "",
                        onPlayTracks = { tracks, index ->
                            scope.launch { mainViewModel.player.playTracks(tracks, index) }
                        },
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                        onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                        onTrackMenuClick = { trackId ->
                            val track = searchState.tracks.find { it.id == trackId }
                            if (track != null) {
                                openContextMenu(track, mainViewModel.serverUrl.value)
                            }
                        },
                    )
                }
                composable(MellowNavDestination.Favorites.route) {
                    val favVm: FavoritesViewModel = hiltViewModel()
                    val favState by favVm.uiState.collectAsState()
                    FavoritesScreen(
                        serverId = serverId,
                        serverUrl = serverUrl,
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId") },
                        onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                        onTrackClick = { trackId ->
                            val tracks = favState.tracks
                            val idx = tracks.indexOfFirst { it.id == trackId }
                            if (idx >= 0) {
                                scope.launch { mainViewModel.player.playTracks(tracks, idx) }
                            }
                        },
                        onTrackMenuClick = { trackId ->
                            val track = favState.tracks.find { it.id == trackId }
                            if (track != null) {
                                openContextMenu(track, mainViewModel.serverUrl.value)
                            }
                        },
                        onShuffleAll = {
                            val tracks = favState.tracks
                            if (tracks.isNotEmpty()) {
                                scope.launch { mainViewModel.player.playTracks(tracks.shuffled(), 0) }
                            }
                        },
                    )
                }
                composable("settings") {
                    val settingsVm: SettingsViewModel = hiltViewModel()
                    val downloadQuality by settingsVm.downloadQuality.collectAsState()
                    val wifiOnly by settingsVm.wifiOnly.collectAsState()
                    val storageCap by settingsVm.storageCap.collectAsState()
                    val autoCleanupDays by settingsVm.autoCleanupDays.collectAsState()
                    val totalDownloadedBytes by settingsVm.totalDownloadedBytes.collectAsState()

                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        serverUrl = serverUrl ?: "",
                        connectionState = connectionState,
                        lastSyncTimestamp = mainViewModel.lastSyncTimestamp.collectAsState().value,
                        isSyncing = isSyncing,
                        isForceOffline = mainViewModel.isForceOffline.collectAsState().value,
                        autoSyncIntervalHours = mainViewModel.autoSyncIntervalHours.collectAsState().value,
                        onSyncNow = mainViewModel::syncNow,
                        onForceOfflineChange = mainViewModel::setForceOffline,
                        onAutoSyncIntervalChange = mainViewModel::setAutoSyncInterval,
                        downloadQuality = downloadQuality,
                        wifiOnly = wifiOnly,
                        storageCap = storageCap,
                        autoCleanupDays = autoCleanupDays,
                        totalDownloadedBytes = totalDownloadedBytes,
                        onDownloadQualityChange = settingsVm::setDownloadQuality,
                        onWifiOnlyChange = settingsVm::setWifiOnly,
                        onStorageCapChange = settingsVm::setStorageCap,
                        onAutoCleanupChange = settingsVm::setAutoCleanupDays,
                        onClearAllDownloads = settingsVm::clearAllDownloads,
                    )
                }
                composable("album/{albumId}") {
                    val albumVm: AlbumDetailViewModel = hiltViewModel()
                    val albumState by albumVm.uiState.collectAsState()
                    val downloadState by albumVm.albumDownloadState.collectAsState()
                    val trackDlStates by albumVm.trackDownloadStates.collectAsState()
                    val isOffline = connectionState is ConnectionState.Offline

                    val showDlIndicators = downloadState.overallStatus != AlbumDownloadState.Status.NONE

                    val downloadInfoText = if (downloadState.downloadedTracks > 0) {
                        val downloadedTrackIds = trackDlStates
                            .filter { (_, state) -> state is DownloadState.Completed }
                            .keys
                        val downloadedSeconds = albumState.tracks
                            .filter { it.id in downloadedTrackIds }
                            .sumOf { it.duration.seconds }
                        val durStr = formatTrackDuration(Duration.ofSeconds(downloadedSeconds))
                        "${downloadState.downloadedTracks} of ${downloadState.totalTracks} downloaded \u00B7 $durStr offline"
                    } else {
                        null
                    }

                    val mappedTracks = remember(albumState.tracks, trackDlStates, isOffline) {
                        albumState.tracks.map { track ->
                            val dlState = trackDlStates[track.id]
                            val indicator = when {
                                !showDlIndicators -> TrackDownloadIndicator.NONE
                                dlState is DownloadState.Completed -> TrackDownloadIndicator.DOWNLOADED
                                dlState is DownloadState.Downloading -> TrackDownloadIndicator.DOWNLOADING
                                dlState is DownloadState.Queued -> TrackDownloadIndicator.DOWNLOADING
                                else -> TrackDownloadIndicator.NOT_DOWNLOADED
                            }
                            AlbumDetailTrack(
                                id = track.id,
                                title = track.name,
                                artistName = track.artistName ?: "",
                                duration = formatTrackDuration(track.duration),
                                trackNumber = track.trackNumber,
                                isFavorite = track.isFavorite,
                                downloadIndicator = indicator,
                            )
                        }
                    }

                    AlbumDetailScreen(
                        onBack = { navController.popBackStack() },
                        albumName = albumState.album?.name ?: "",
                        artistName = albumState.album?.artistName ?: "",
                        albumImageUrl = if (serverUrl != null && albumState.album?.imageId != null) {
                            jellyfinImageUrl(serverUrl!!, albumState.album!!.imageId!!)
                        } else null,
                        year = albumState.album?.year,
                        expectedTrackCount = albumState.album?.trackCount ?: 0,
                        tracks = mappedTracks,
                        isFavorite = albumState.album?.isFavorite ?: false,
                        isLoading = albumState.isLoading,
                        isSyncing = isSyncing,
                        error = albumState.error,
                        downloadStatus = downloadState.overallStatus,
                        downloadProgress = if (downloadState.totalTracks > 0) {
                            downloadState.downloadedTracks.toFloat() / downloadState.totalTracks
                        } else 0f,
                        downloadedCount = downloadState.downloadedTracks,
                        totalDownloadCount = downloadState.totalTracks,
                        downloadInfoText = downloadInfoText,
                        isOffline = isOffline,
                        onRetry = { albumVm.retry() },
                        onDownloadClick = { albumVm.downloadAlbum() },
                        onRemoveDownloadsClick = { albumVm.removeAlbumDownloads() },
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
                        onTrackMenuClick = { trackId ->
                            val track = albumState.tracks.find { it.id == trackId }
                            if (track != null) {
                                openContextMenu(track, mainViewModel.serverUrl.value)
                            }
                        },
                    )
                }
                composable("artist/{artistId}") {
                    val artistVm: ArtistDetailViewModel = hiltViewModel()
                    val artistState by artistVm.uiState.collectAsState()

                    val topTracks = remember(artistState.topTracks) {
                        artistState.topTracks.map { track ->
                            ArtistTrack(
                                id = track.id,
                                title = track.name,
                                duration = formatTrackDuration(track.duration),
                                albumName = track.albumName ?: "",
                            )
                        }
                    }
                    val albums = remember(artistState.albums) {
                        artistState.albums.map { album ->
                            ArtistAlbum(
                                id = album.id,
                                name = album.name,
                                year = album.year,
                                imageId = album.imageId,
                            )
                        }
                    }

                    ArtistDetailScreen(
                        onBack = { navController.popBackStack() },
                        artistName = artistState.artist?.name ?: "",
                        artistImageUrl = if (serverUrl != null && artistState.artist?.imageId != null) {
                            jellyfinImageUrl(serverUrl!!, artistState.artist!!.imageId!!)
                        } else null,
                        albumCount = artistState.artist?.albumCount ?: 0,
                        overview = artistState.artist?.overview,
                        topTracks = topTracks,
                        albums = albums,
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
                        onTrackMenuClick = { trackId ->
                            val track = artistState.topTracks.find { it.id == trackId }
                            if (track != null) {
                                openContextMenu(track, mainViewModel.serverUrl.value)
                            }
                        },
                        serverUrl = serverUrl,
                        isFavorite = artistState.artist?.isFavorite ?: false,
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
                composable("playlist/{playlistId}") {
                    val playlistDetailVm: PlaylistDetailViewModel = hiltViewModel()
                    val playlistDetailState by playlistDetailVm.uiState.collectAsState()

                    LaunchedEffect(serverId) {
                        if (serverId.isNotEmpty()) playlistDetailVm.syncTracks(serverId)
                    }

                    val mappedPlaylistTracks = remember(playlistDetailState.tracks, serverUrl) {
                        playlistDetailState.tracks.map { track ->
                            PlaylistDetailTrack(
                                id = track.id,
                                title = track.name,
                                artistName = track.artistName ?: "",
                                duration = formatTrackDuration(track.duration),
                                imageUrl = if (serverUrl != null && track.imageId != null) {
                                    jellyfinImageUrl(serverUrl!!, track.imageId!!)
                                } else null,
                            )
                        }
                    }

                    PlaylistDetailScreen(
                        onBack = { navController.popBackStack() },
                        playlistName = playlistDetailState.playlist?.name ?: "",
                        tracks = mappedPlaylistTracks,
                        isLoading = playlistDetailState.isLoading,
                        onTrackClick = { trackId ->
                            val tracks = playlistDetailState.tracks
                            val idx = tracks.indexOfFirst { it.id == trackId }
                            if (idx >= 0) {
                                scope.launch { mainViewModel.player.playTracks(tracks, idx) }
                            }
                        },
                        onPlayAll = {
                            val tracks = playlistDetailState.tracks
                            if (tracks.isNotEmpty()) {
                                scope.launch { mainViewModel.player.playTracks(tracks, 0) }
                            }
                        },
                        onShuffle = {
                            val tracks = playlistDetailState.tracks
                            if (tracks.isNotEmpty()) {
                                scope.launch { mainViewModel.player.playTracks(tracks.shuffled(), 0) }
                            }
                        },
                        onTrackMenuClick = { trackId ->
                            val track = playlistDetailState.tracks.find { it.id == trackId }
                            if (track != null) {
                                openContextMenu(track, mainViewModel.serverUrl.value)
                            }
                        },
                        onRemoveTrack = { trackId ->
                            playlistDetailVm.removeTrack(trackId)
                        },
                    )
                }
                composable("now_playing") {
                    val pState = playbackState
                    val track = pState.currentTrack

                    val isDownloaded by remember(track?.id) {
                        if (track != null) mainViewModel.isTrackDownloaded(track.id)
                        else kotlinx.coroutines.flow.flowOf(false)
                    }.collectAsState(initial = false)

                    PlayerScreen(
                        trackName = track?.name ?: "",
                        artistName = track?.artistName ?: "",
                        albumName = track?.albumName ?: "",
                        albumImageUrl = if (serverUrl != null && track?.imageId != null) {
                            jellyfinImageUrl(serverUrl!!, track.imageId!!)
                        } else null,
                        isPlaying = pState.isPlaying,
                        progress = if (positionState.durationMs > 0) {
                            positionState.positionMs.toFloat() / positionState.durationMs
                        } else 0f,
                        positionMs = positionState.positionMs,
                        durationMs = positionState.durationMs,
                        isFavorite = track?.isFavorite ?: false,
                        isDownloaded = isDownloaded,
                        error = pState.error,
                        onCollapse = { navController.popBackStack() },
                        onQueueClick = { navController.navigate("queue") },
                        onLyricsClick = { navController.navigate("lyrics") },
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
                        onRetryClick = {
                            val tracks = pState.queue
                            if (tracks.isNotEmpty()) {
                                scope.launch { mainViewModel.player.playTracks(tracks, pState.currentIndex) }
                            }
                        },
                        onPlayDownloadedClick = {
                            navController.popBackStack()
                            navController.navigate(MellowNavDestination.Library.route)
                        },
                        codec = track?.codec,
                    )
                }
                composable("queue") {
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
                            imageUrl = if (serverUrl != null && track.imageId != null) {
                                jellyfinImageUrl(serverUrl!!, track.imageId!!)
                            } else null,
                        )
                    }

                    val upNext = remember(queue, currentIdx, serverUrl) {
                        queue
                            .filterIndexed { idx, _ -> idx > currentIdx }
                            .map { track ->
                                QueueTrack(
                                    id = track.id,
                                    title = track.name,
                                    artist = track.artistName ?: "",
                                    album = track.albumName ?: "",
                                    duration = formatTrackDuration(track.duration),
                                    imageUrl = if (serverUrl != null && track.imageId != null) {
                                        jellyfinImageUrl(serverUrl!!, track.imageId!!)
                                    } else null,
                                )
                            }
                    }

                    QueueScreen(
                        onBack = { navController.popBackStack() },
                        nowPlaying = nowPlaying,
                        upNext = upNext,
                        currentAlbumName = pState.currentTrack?.albumName ?: "",
                        shuffleEnabled = pState.shuffleEnabled,
                        repeatMode = pState.repeatMode,
                        onTrackClick = { relativeIndex ->
                            mainViewModel.player.playFromQueue(currentIdx + 1 + relativeIndex)
                        },
                        onShuffleClick = { mainViewModel.player.toggleShuffle() },
                        onRepeatClick = { mainViewModel.player.cycleRepeatMode() },
                        onClearClick = {
                            mainViewModel.player.clearQueue()
                            navController.popBackStack()
                        },
                        onMoveTrack = { from, to ->
                            mainViewModel.player.moveQueueItem(
                                currentIdx + 1 + from,
                                currentIdx + 1 + to,
                            )
                        },
                        onRemoveTrack = { relativeIndex ->
                            mainViewModel.player.removeFromQueue(currentIdx + 1 + relativeIndex)
                        },
                    )
                }
                composable("lyrics") {
                    val pState = playbackState
                    val track = pState.currentTrack

                    var lyrics by remember { mutableStateOf<List<LyricsLine>>(emptyList()) }
                    var isLoadingLyrics by remember { mutableStateOf(true) }

                    LaunchedEffect(track?.id) {
                        isLoadingLyrics = true
                        lyrics = if (track != null) {
                            mainViewModel.fetchLyrics(track.id).map { r ->
                                LyricsLine(startMs = r.startMs, text = r.text)
                            }
                        } else {
                            emptyList()
                        }
                        isLoadingLyrics = false
                    }

                    LyricsScreen(
                        trackName = track?.name ?: "",
                        artistName = track?.artistName ?: "",
                        albumImageUrl = if (serverUrl != null && track?.imageId != null) {
                            jellyfinImageUrl(serverUrl!!, track.imageId!!)
                        } else null,
                        lyrics = lyrics,
                        isLoadingLyrics = isLoadingLyrics,
                        positionMs = positionState.positionMs,
                        durationMs = positionState.durationMs,
                        isPlaying = pState.isPlaying,
                        onClose = { navController.popBackStack() },
                        onSeekTo = { ms -> mainViewModel.player.seekTo(ms) },
                        onPlayPauseClick = { mainViewModel.player.playPause() },
                        onSkipNextClick = { mainViewModel.player.skipNext() },
                        onSkipPreviousClick = { mainViewModel.player.skipPrevious() },
                    )
                }
            }
        }
    }

    if (contextMenuState != null) {
        TrackContextMenu(
            track = contextMenuState!!.menuData,
            onDismiss = { contextMenuState = null },
            onPlayNext = {
                mainViewModel.player.playNext(contextMenuState!!.track)
            },
            onAddToQueue = {
                mainViewModel.player.addToQueue(contextMenuState!!.track)
            },
            onAddToPlaylist = {
                addToPlaylistTrackId = contextMenuState?.track?.id
                showAddToPlaylistSheet = true
            },
            onGoToAlbum = {
                contextMenuState!!.menuData.albumId?.let { navController.navigate("album/$it") }
                contextMenuState = null
            },
            onGoToArtist = {
                contextMenuState!!.menuData.artistId?.let { navController.navigate("artist/$it") }
                contextMenuState = null
            },
            onStartMix = {},
            onToggleFavorite = {
                val t = contextMenuState!!.menuData
                mainViewModel.toggleFavorite(t.id, t.isFavorite)
            },
            onTrackInfo = {},
        )
    }

    if (showAddToPlaylistSheet) {
        val playlistPickerItems = remember(playlistsState.playlists) {
            playlistsState.playlists.map { PlaylistPickerItem(it.id, it.name) }
        }
        AddToPlaylistSheet(
            playlists = playlistPickerItems,
            onSelect = { playlistId ->
                val trackId = addToPlaylistTrackId
                if (trackId != null) {
                    scope.launch {
                        mainViewModel.addTrackToPlaylist(playlistId, trackId, serverId)
                    }
                }
                showAddToPlaylistSheet = false
                addToPlaylistTrackId = null
            },
            onDismiss = {
                showAddToPlaylistSheet = false
                addToPlaylistTrackId = null
            },
            onCreateNew = { name ->
                playlistsVm.createPlaylist(name)
            },
        )
    }
}

private data class ContextMenuState(
    val menuData: TrackMenuData,
    val track: Track,
)

private fun formatTrackDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
