package dev.mellow.app.navigation

import androidx.compose.animation.AnimatedContentTransitionScope.SlideDirection
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut

import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.ui.platform.LocalContext
import com.mikepenz.aboutlibraries.Libs
import com.mikepenz.aboutlibraries.util.withContext
import dev.mellow.app.dev.DevIconComparisonScreen
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.gestures.anchoredDraggable
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.draw.clip
import dev.mellow.core.designsystem.component.MellowDialog
import dev.mellow.core.designsystem.theme.MellowPalette
import dev.mellow.core.designsystem.theme.MellowShapes
import dev.mellow.core.designsystem.component.MellowRadioOption
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.mellow.app.AuthState
import dev.mellow.app.MainViewModel
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.data.repository.LibraryRepositoryImpl
import dev.mellow.core.designsystem.component.LocalNavAnimatedVisibilityScope
import dev.mellow.core.designsystem.component.LocalSharedTransitionScope
import dev.mellow.core.designsystem.component.MellowBottomNavBar
import dev.mellow.core.designsystem.component.MellowNavigationRail
import dev.mellow.core.player.PositionState
import dev.mellow.core.designsystem.component.MellowNavDestination
import dev.mellow.core.designsystem.component.MiniPlayer
import dev.mellow.core.designsystem.component.TrackContextMenu
import dev.mellow.core.designsystem.component.TrackMenuData
import dev.mellow.core.designsystem.theme.LocalBatterySaverActive
import dev.mellow.core.designsystem.theme.LocalMiniPlayerPadding
import dev.mellow.core.designsystem.theme.LocalWindowWidthClass
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.core.designsystem.theme.WindowWidthClass
import dev.mellow.core.designsystem.theme.rememberIsBatterySaverActive
import dev.mellow.core.network.ConnectionState
import androidx.compose.ui.unit.dp
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
import dev.mellow.feature.library.AlbumDownloadEvent
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
import dev.mellow.feature.settings.LicensesScreen
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

    val trustSelfSigned by loginViewModel.trustSelfSigned.collectAsState()

    LoginScreen(
        onSignIn = { serverUrl, username, password ->
            loginViewModel.signIn(serverUrl, username, password)
        },
        isLoading = loginState.isLoading,
        error = loginState.error,
        trustSelfSigned = trustSelfSigned,
        onTrustSelfSignedChange = loginViewModel::setTrustSelfSigned,
    )
}

@Composable
private fun MainAppShell(serverId: String, mainViewModel: MainViewModel) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: MellowNavDestination.Home.route
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val fullScreenRoutes = setOf("now_playing", "queue", "lyrics")
    val isFullScreen = currentRoute in fullScreenRoutes
    val tabRoutes = MellowNavDestination.entries.map { it.route }.toSet()
    val baseRoute = currentRoute.substringBefore("?")
    val playbackState by mainViewModel.player.state.collectAsState()
    val positionState by mainViewModel.player.positionState.collectAsState()
    val isSyncing by mainViewModel.isSyncing.collectAsState()
    val syncProgress by mainViewModel.syncProgress.collectAsState()
    val isCleaningUp by mainViewModel.isCleaningUp.collectAsState()
    val serverUrl by mainViewModel.serverUrl.collectAsState()
    val connectionState by mainViewModel.connectionState.collectAsState()
    val sheetState = rememberExpandableSheetState()
    val sharedArtPositions = remember { SharedArtPositions() }
    val density = androidx.compose.ui.platform.LocalDensity.current

    var contextMenuState by remember { mutableStateOf<ContextMenuState?>(null) }
    var trackInfoTrack by remember { mutableStateOf<Track?>(null) }
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
                            imageUrl = if (serverUrl != null) {
                                val imgId = track.imageId ?: track.albumId
                                if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                            } else null,
                isFavorite = track.isFavorite,
                isDownloaded = false,
            ),
            track = track,
        )
    }

    val navigateToTab: (String) -> Unit = { route ->
        if (baseRoute !in tabRoutes) {
            navController.popBackStack(route, inclusive = false)
        }
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    val selectedTabRoute = if (baseRoute in tabRoutes) baseRoute else ""

    @Composable
    fun MiniPlayerBar(modifier: Modifier = Modifier) {
        if (playbackState.currentTrack != null) {
            val track = playbackState.currentTrack!!
            val isTransitioning = sheetState.dragFraction > 0f && sheetState.dragFraction < 1f
            MiniPlayer(
                title = track.name,
                artist = track.artistName ?: "",
                imageUrl = if (serverUrl != null) {
                    val imgId = track.imageId ?: track.albumId
                    if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                } else null,
                isPlaying = playbackState.isPlaying,
                isBuffering = playbackState.isBuffering,
                progress = if (positionState.durationMs > 0) {
                    positionState.positionMs.toFloat() / positionState.durationMs
                } else 0f,
                onPlayPauseClick = { mainViewModel.player.playPause() },
                onNextClick = { mainViewModel.player.skipNext() },
                onClick = {
                    if (sheetState.anchoredState.anchors.size > 0) sheetState.expand()
                    else navController.navigate("now_playing")
                },
                modifier = modifier
                    .padding(horizontal = MellowSpacing.Sp2, vertical = MellowSpacing.Sp1),
                artModifier = Modifier
                    .trackArtPosition(sharedArtPositions, isMini = true)
                    .graphicsLayer { alpha = if (isTransitioning) 0f else 1f },
            )
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val windowWidthClass = when {
            maxWidth >= 840.dp && maxHeight >= 600.dp -> WindowWidthClass.Expanded
            maxWidth >= 600.dp -> WindowWidthClass.Medium
            else -> WindowWidthClass.Compact
        }
        val isExpanded = windowWidthClass != WindowWidthClass.Compact
        val isTabletLayout = windowWidthClass == WindowWidthClass.Expanded
        val isTabletPortrait = isTabletLayout && maxHeight > maxWidth
        val hasTrack = playbackState.currentTrack != null
        val showSheet = hasTrack
        val sheetBottomNavPx = if (windowWidthClass == WindowWidthClass.Compact || isTabletPortrait) {
            with(density) { MellowSpacing.BottomNavHeight.toPx() }
        } else 0f

    val showExpandedMiniPlayer = isExpanded && !isFullScreen && hasTrack && !isTabletPortrait
    val miniPlayerPadding = if (showExpandedMiniPlayer) MellowSpacing.MiniPlayerHeight + MellowSpacing.Sp2 else 0.dp
    CompositionLocalProvider(
        LocalWindowWidthClass provides windowWidthClass,
        LocalMiniPlayerPadding provides miniPlayerPadding,
        LocalBatterySaverActive provides (rememberIsBatterySaverActive() || mainViewModel.lowPowerMode.collectAsState().value),
    ) {
    Row(modifier = Modifier.fillMaxSize()) {
        if (isExpanded && !isFullScreen && !isTabletPortrait) {
            MellowNavigationRail(
                selectedRoute = selectedTabRoute,
                onNavigate = navigateToTab,
            )
        }

    Box(modifier = Modifier.weight(1f)) {
    Scaffold(
        contentWindowInsets = if (isFullScreen || (isExpanded && !isTabletPortrait)) {
            WindowInsets(0)
        } else {
            WindowInsets.systemBars
        },
        containerColor = MellowTheme.colors.background,
        bottomBar = {
            if (!isFullScreen && (!isExpanded || isTabletPortrait)) {
                Column {
                    if (hasTrack) {
                        MiniPlayerBar(
                            modifier = Modifier.anchoredDraggable(
                                sheetState.anchoredState,
                                androidx.compose.foundation.gestures.Orientation.Vertical,
                            ),
                        )
                    }
                    MellowBottomNavBar(
                        selectedRoute = selectedTabRoute,
                        onNavigate = navigateToTab,
                    )
                }
            }
        },
        modifier = Modifier.fillMaxSize(),
    ) { innerPadding ->
        @OptIn(ExperimentalSharedTransitionApi::class)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            SharedTransitionLayout {
                CompositionLocalProvider(LocalSharedTransitionScope provides this) {
                    NavHost(
                        navController = navController,
                        startDestination = MellowNavDestination.Home.route,
                enterTransition = { fadeIn(tween(300)) },
                exitTransition = { fadeOut(tween(300)) },
                popEnterTransition = { fadeIn(tween(300)) },
                popExitTransition = { fadeOut(tween(300)) },
                    ) {
                composable(MellowNavDestination.Home.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                    val homeVm: HomeViewModel = hiltViewModel()
                    val homeState by homeVm.uiState.collectAsState()
                    val effectiveFilter by mainViewModel.downloadedOnly.collectAsState()
                    LaunchedEffect(serverId) {
                        if (serverId.isNotEmpty()) homeVm.loadHome(serverId)
                    }

                    HomeScreen(
                        quickPicks = homeState.quickPicks,
                        recentlyPlayed = homeState.recentlyPlayed,
                        recentlyAdded = homeState.recentlyAdded,
                        favoriteTracks = homeState.favoriteTracks,
                        genres = homeState.genres,
                        serverUrl = serverUrl,
                        isConnected = connectionState is ConnectionState.Connected,
                        isServerUnreachable = connectionState is ConnectionState.ServerUnreachable,
                        error = homeState.error,
                        onRetry = homeVm::retry,
                        isFilterActive = effectiveFilter,
                        onToggleFilter = { mainViewModel.toggleDownloadedOnly() },
                        onAlbumClick = { albumId, source -> navController.navigate("album/$albumId?source=$source") },
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
                        onGenreClick = { genre ->
                            navController.navigate("library?genre=${android.net.Uri.encode(genre)}")
                        },
                        isLoading = homeState.isLoading,
                    )
                    }
                }
                composable(
                    "library?genre={genre}",
                    arguments = listOf(
                        navArgument("genre") { type = NavType.StringType; defaultValue = "" },
                    ),
                ) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                    val libraryVm: LibraryViewModel = hiltViewModel()
                    val state by libraryVm.uiState.collectAsState()
                    var currentSort by rememberSaveable { mutableStateOf("Recently Added") }
                    val initialGenre = it.arguments?.getString("genre")?.takeIf { g -> g.isNotEmpty() }
                    var selectedGenre by rememberSaveable { mutableStateOf(initialGenre) }

                    val effectiveLibFilter by mainViewModel.downloadedOnly.collectAsState()
                    LaunchedEffect(serverId) {
                        if (serverId.isNotEmpty()) libraryVm.loadLibrary(serverId)
                    }

                    val albumCountByArtist = state.albums.groupingBy { it.artistName ?: "" }.eachCount()

                    val filteredAlbums = remember(state.albums, selectedGenre) {
                        if (selectedGenre != null) {
                            state.albums.filter { it.genres.contains(selectedGenre) }
                        } else {
                            state.albums
                        }
                    }

                    val albumItems = remember(filteredAlbums, currentSort) {
                        val sorted = when (currentSort) {
                            "Name (A-Z)" -> filteredAlbums.sortedBy { it.name.lowercase() }
                            "Name (Z-A)" -> filteredAlbums.sortedByDescending { it.name.lowercase() }
                            "Year" -> filteredAlbums.sortedByDescending { it.year ?: 0 }
                            else -> filteredAlbums.sortedByDescending { it.dateAdded }
                        }
                        sorted.map { AlbumItem(it.id, it.name, it.artistName ?: "", it.imageId) }
                    }
                    val artists = remember(state.artists, albumCountByArtist, currentSort) {
                        val sorted = when (currentSort) {
                            "Name (A-Z)" -> state.artists.sortedBy { it.name.lowercase() }
                            "Name (Z-A)" -> state.artists.sortedByDescending { it.name.lowercase() }
                            else -> state.artists
                        }
                        sorted.map { artist ->
                            val count = albumCountByArtist[artist.name]
                                ?: albumCountByArtist[LibraryRepositoryImpl.artistNameVariant(artist.name)]
                                ?: 0
                            ArtistItem(artist.id, artist.name, count, artist.imageId)
                        }
                    }
                    val tracks = remember(state.tracks, currentSort) {
                        val sorted = when (currentSort) {
                            "Name (A-Z)" -> state.tracks.sortedBy { it.name.lowercase() }
                            "Name (Z-A)" -> state.tracks.sortedByDescending { it.name.lowercase() }
                            "Year" -> state.tracks.sortedByDescending { it.albumName ?: "" }
                            else -> state.tracks.sortedByDescending { it.dateAdded }
                        }
                        sorted.map { track ->
                            TrackItem(
                                id = track.id,
                                title = track.name,
                                artist = track.artistName ?: "",
                                album = track.albumName ?: "",
                                duration = formatTrackDuration(track.duration),
                                imageId = track.imageId,
                                albumId = track.albumId,
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
                        error = state.error,
                        onRetry = libraryVm::retry,
                        isFilterActive = effectiveLibFilter,
                        onToggleFilter = { mainViewModel.toggleDownloadedOnly() },
                        sortLabel = currentSort,
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId?source=library") },
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
                        onGenreClick = { genre -> selectedGenre = genre },
                        selectedGenre = selectedGenre,
                        onClearGenre = { selectedGenre = null },
                    )
                    }
                }
                composable(MellowNavDestination.Search.route) {
                    val searchVm: SearchViewModel = hiltViewModel()
                    val searchState by searchVm.uiState.collectAsState()
                    val searchLibraryVm: LibraryViewModel = hiltViewModel()
                    val searchLibraryState by searchLibraryVm.uiState.collectAsState()

                    LaunchedEffect(serverId) {
                        if (serverId.isNotEmpty()) searchLibraryVm.loadLibrary(serverId)
                    }

                    val searchGenres = remember(searchLibraryState.albums) {
                        searchLibraryState.albums.flatMap { it.genres }.distinct().sorted()
                    }

                    val effectiveSearchFilter by mainViewModel.downloadedOnly.collectAsState()
                    SearchScreen(
                        serverId = serverId,
                        serverUrl = serverUrl ?: "",
                        isConnected = connectionState is ConnectionState.Connected,
                        isServerUnreachable = connectionState is ConnectionState.ServerUnreachable,
                        isFilterActive = effectiveSearchFilter,
                        onToggleFilter = { mainViewModel.toggleDownloadedOnly() },
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
                        onSettingsClick = { navController.navigate("settings") },
                        genres = searchGenres,
                        onGenreClick = { genre ->
                            navController.navigate("library?genre=${android.net.Uri.encode(genre)}")
                        },
                    )
                }
                composable(MellowNavDestination.Favorites.route) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                    val favVm: FavoritesViewModel = hiltViewModel()
                    val favState by favVm.uiState.collectAsState()
                    val effectiveFavFilter by mainViewModel.downloadedOnly.collectAsState()
                    FavoritesScreen(
                        isFilterActive = effectiveFavFilter,
                        onToggleFilter = { mainViewModel.toggleDownloadedOnly() },
                        serverId = serverId,
                        serverUrl = serverUrl,
                        isConnected = connectionState is ConnectionState.Connected,
                        isServerUnreachable = connectionState is ConnectionState.ServerUnreachable,
                        onAlbumClick = { albumId -> navController.navigate("album/$albumId?source=favorites") },
                        onArtistClick = { artistId -> navController.navigate("artist/$artistId") },
                        onSettingsClick = { navController.navigate("settings") },
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
                }
                composable(
                    "settings",
                    enterTransition = { slideIntoContainer(SlideDirection.Start, tween(200)) },
                    exitTransition = { fadeOut(tween(150)) },
                    popEnterTransition = { fadeIn(tween(150)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(200)) },
                ) {
                    val settingsVm: SettingsViewModel = hiltViewModel()
                    val downloadQuality by settingsVm.downloadQuality.collectAsState()
                    val wifiOnly by settingsVm.wifiOnly.collectAsState()
                    val storageCap by settingsVm.storageCap.collectAsState()
                    val autoCleanupDays by settingsVm.autoCleanupDays.collectAsState()
                    val totalDownloadedBytes by settingsVm.totalDownloadedBytes.collectAsState()
                    val lowPowerMode by settingsVm.lowPowerMode.collectAsState()

                    SettingsScreen(
                        onBack = { navController.popBackStack() },
                        serverUrl = serverUrl ?: "",
                        connectionState = connectionState,
                        lastSyncTimestamp = mainViewModel.lastSyncTimestamp.collectAsState().value,
                        isSyncing = isSyncing,
                        syncProgress = syncProgress,
                        isCleaningUp = isCleaningUp,
                        isForceOffline = mainViewModel.isForceOffline.collectAsState().value,
                        autoSyncIntervalHours = mainViewModel.autoSyncIntervalHours.collectAsState().value,
                        onSyncNow = mainViewModel::syncNow,
                        onCleanup = mainViewModel::cleanupLibrary,
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
                        lowPowerMode = lowPowerMode,
                        onLowPowerModeChange = settingsVm::setLowPowerMode,
                        onDevToolsClick = {
                            navController.navigate("dev_tools")
                        },
                        onLicensesClick = {
                            navController.navigate("licenses")
                        },
                        onLogout = mainViewModel::logout,
                    )
                }
                composable("dev_tools") {
                    DevIconComparisonScreen(onBack = { navController.popBackStack() })
                }
                composable(
                    "licenses",
                    enterTransition = { slideIntoContainer(SlideDirection.Start, tween(200)) },
                    popExitTransition = { slideOutOfContainer(SlideDirection.End, tween(200)) },
                ) {
                    val context = LocalContext.current
                    val (community, platform) = remember {
                        val all = Libs.Builder().withContext(context).build().libraries + MANUAL_LIBRARIES
                        all.partition { library ->
                            val group = library.uniqueId.substringBefore(":")
                            !PLATFORM_PREFIXES.any { group.startsWith(it) }
                        }
                    }
                    LicensesScreen(
                        communityLibraries = community,
                        platformLibraries = platform,
                        onBack = { navController.popBackStack() },
                    )
                }
                composable(
                    "album/{albumId}?source={source}",
                    arguments = listOf(
                        navArgument("albumId") { type = NavType.StringType },
                        navArgument("source") { type = NavType.StringType; defaultValue = "library" },
                    ),
                ) {
                    CompositionLocalProvider(LocalNavAnimatedVisibilityScope provides this@composable) {
                    val routeAlbumId = it.arguments?.getString("albumId") ?: ""
                    val routeSource = it.arguments?.getString("source") ?: "library"
                    val albumVm: AlbumDetailViewModel = hiltViewModel()
                    val albumState by albumVm.uiState.collectAsState()
                    val downloadState by albumVm.albumDownloadState.collectAsState()
                    val trackDlStates by albumVm.trackDownloadStates.collectAsState()
                    val liveProgress by albumVm.liveProgress.collectAsState()
                    val isOffline = connectionState is ConnectionState.Offline

                    val showDlIndicators = downloadState.overallStatus != AlbumDownloadState.Status.NONE

                    var showStorageCapDialog by remember { mutableStateOf<Pair<Long, Long>?>(null) }

                    LaunchedEffect(Unit) {
                        albumVm.downloadEvents.collect { event ->
                            when (event) {
                                is AlbumDownloadEvent.StorageCapExceeded ->
                                    showStorageCapDialog = event.usedBytes to event.capBytes
                            }
                        }
                    }

                    if (showStorageCapDialog != null) {
                        val (used, cap) = showStorageCapDialog!!
                        StorageCapExceededDialog(
                            usedBytes = used,
                            capBytes = cap,
                            onIncreaseCap = { newCap ->
                                albumVm.updateStorageCap(newCap)
                                albumVm.downloadAlbumForced()
                                showStorageCapDialog = null
                            },
                            onDismiss = { showStorageCapDialog = null },
                        )
                    }

                    val mappedTracks = albumState.tracks.map { track ->
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
                                downloadProgress = liveProgress[track.id] ?: (dlState as? DownloadState.Downloading)?.progress ?: 0f,
                        )
                    }

                    AlbumDetailScreen(
                        onBack = { navController.popBackStack() },
                        albumId = routeAlbumId,
                        sharedElementSource = routeSource,
                        albumName = albumState.album?.name ?: "",
                        artistName = albumState.album?.artistName ?: "",
                        albumImageUrl = if (serverUrl != null) {
                            val imgId = albumState.album?.imageId ?: routeAlbumId
                            jellyfinImageUrl(serverUrl!!, imgId)
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
                        onShare = {
                            val album = albumState.album
                            if (album != null) {
                                val text = buildString {
                                    append("${album.name} by ${album.artistName ?: "Unknown Artist"}")
                                    val url = serverUrl
                                    if (url != null) append("\n${url}/web/index.html#!/details?id=${album.id}")
                                }
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, null))
                            }
                        },
                        onAddAllToQueue = {
                            albumState.tracks.forEach { mainViewModel.player.addToQueue(it) }
                        },
                        onGoToArtist = {
                            val artist = albumState.album?.artistId
                            if (artist != null) navController.navigate("artist/$artist")
                        },
                    )
                    }
                }
                composable("artist/{artistId}") {
                    val artistVm: ArtistDetailViewModel = hiltViewModel()
                    val artistState by artistVm.uiState.collectAsState()

                    val topTracks = remember(artistState.topTracks, serverUrl) {
                        artistState.topTracks.map { track ->
                            ArtistTrack(
                                id = track.id,
                                title = track.name,
                                duration = formatTrackDuration(track.duration),
                                albumName = track.albumName ?: "",
                                imageUrl = if (serverUrl != null) {
                                    val imgId = track.imageId ?: track.albumId
                                    if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                                } else null,
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
                        albumCount = albums.size,
                        totalTrackCount = artistState.totalTrackCount,
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
                        onShare = {
                            val artist = artistState.artist
                            if (artist != null) {
                                val text = buildString {
                                    append(artist.name)
                                    val url = serverUrl
                                    if (url != null) append("\n${url}/web/index.html#!/details?id=${artist.id}")
                                }
                                val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                                    type = "text/plain"
                                    putExtra(android.content.Intent.EXTRA_TEXT, text)
                                }
                                context.startActivity(android.content.Intent.createChooser(intent, null))
                            }
                        },
                        onAddAllToQueue = {
                            artistState.topTracks.forEach { mainViewModel.player.addToQueue(it) }
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
                                imageUrl = if (serverUrl != null) {
                                    val imgId = track.imageId ?: track.albumId
                                    if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
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
                    LaunchedEffect(Unit) {
                        sheetState.expand()
                        navController.popBackStack()
                    }
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
                            imageUrl = if (serverUrl != null) {
                                val imgId = track.imageId ?: track.albumId
                                if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
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
                                    imageUrl = if (serverUrl != null) {
                                        val imgId = track.imageId ?: track.albumId
                                        if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
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
                        albumImageUrl = if (serverUrl != null) {
                            val imgId = track?.imageId ?: track?.albumId
                            if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
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
            if (showExpandedMiniPlayer) {
                MiniPlayerBar(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .then(
                            if (showSheet) Modifier.anchoredDraggable(
                                sheetState.anchoredState,
                                androidx.compose.foundation.gestures.Orientation.Vertical,
                            ) else Modifier,
                        ),
                )
            }
        }
    }
    if (showSheet) {
        val track = playbackState.currentTrack!!

        val sheetIsFavorite by remember(track.id) {
            mainViewModel.observeTrackFavorite(track.id)
        }.collectAsState(initial = track.isFavorite)

        val sheetIsDownloaded by remember(track.id) {
            mainViewModel.isTrackDownloaded(track.id)
        }.collectAsState(initial = false)

        ExpandablePlayerSheet(
            sheetState = sheetState,
            trackName = track.name,
            artistName = track.artistName ?: "",
            albumImageUrl = if (serverUrl != null) {
                val imgId = track.imageId ?: track.albumId
                if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
            } else null,
            isPlaying = playbackState.isPlaying,
            isBuffering = playbackState.isBuffering,
            progress = if (positionState.durationMs > 0) {
                positionState.positionMs.toFloat() / positionState.durationMs
            } else 0f,
            onPlayPause = { mainViewModel.player.playPause() },
            onSkipNext = { mainViewModel.player.skipNext() },
            playerContent = { onCollapse, onQueueClick, onLyricsClick ->
                val isArtTransitioning = sheetState.dragFraction > 0f && sheetState.dragFraction < 1f
                PlayerScreen(
                    embedded = true,
                    artModifier = Modifier
                        .trackArtPosition(sharedArtPositions, isMini = false)
                        .graphicsLayer { alpha = if (isArtTransitioning) 0f else 1f },
                    trackName = track.name,
                    artistName = track.artistName ?: "",
                    albumName = track.albumName ?: "",
                    albumImageUrl = if (serverUrl != null) {
                        val imgId = track.imageId ?: track.albumId
                        if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                    } else null,
                    isPlaying = playbackState.isPlaying,
                    progress = if (positionState.durationMs > 0) {
                        positionState.positionMs.toFloat() / positionState.durationMs
                    } else 0f,
                    positionMs = positionState.positionMs,
                    durationMs = positionState.durationMs,
                    isFavorite = sheetIsFavorite,
                    isDownloaded = sheetIsDownloaded,
                    error = playbackState.error,
                    onCollapse = onCollapse,
                    onQueueClick = onQueueClick,
                    onLyricsClick = onLyricsClick,
                    onPlayPauseClick = { mainViewModel.player.playPause() },
                    onSkipNextClick = { mainViewModel.player.skipNext() },
                    onSkipPreviousClick = { mainViewModel.player.skipPrevious() },
                    onSeekTo = { ms -> mainViewModel.player.seekTo(ms) },
                    shuffleEnabled = playbackState.shuffleEnabled,
                    repeatMode = playbackState.repeatMode,
                    onShuffleClick = { mainViewModel.player.toggleShuffle() },
                    onRepeatClick = { mainViewModel.player.cycleRepeatMode() },
                    onFavoriteClick = { mainViewModel.toggleFavorite(track.id, sheetIsFavorite) },
                    onRetryClick = {
                        val tracks = playbackState.queue
                        if (tracks.isNotEmpty()) {
                            scope.launch { mainViewModel.player.playTracks(tracks, playbackState.currentIndex) }
                        }
                    },
                    onPlayDownloadedClick = {
                        sheetState.collapse()
                        navController.navigate(MellowNavDestination.Library.route)
                    },
                    codec = track.codec,
                    sidePanelContent = {
                        val pState = playbackState
                        val currentIdx = pState.currentIndex
                        val sideQueue = pState.queue
                        var activeTab by remember { mutableStateOf(0) }

                        val sideNowPlaying = pState.currentTrack?.let { t ->
                            dev.mellow.feature.player.QueueTrack(
                                id = t.id, title = t.name, artist = t.artistName ?: "",
                                album = t.albumName ?: "", duration = formatTrackDuration(t.duration),
                                imageUrl = if (serverUrl != null) {
                                    val imgId = t.imageId ?: t.albumId
                                    if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                                } else null,
                            )
                        }
                        val sideUpNext = remember(sideQueue, currentIdx, serverUrl) {
                            sideQueue.filterIndexed { idx, _ -> idx > currentIdx }.map { t ->
                                dev.mellow.feature.player.QueueTrack(
                                    id = t.id, title = t.name, artist = t.artistName ?: "",
                                    album = t.albumName ?: "", duration = formatTrackDuration(t.duration),
                                    imageUrl = if (serverUrl != null) {
                                        val imgId = t.imageId ?: t.albumId
                                        if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                                    } else null,
                                )
                            }
                        }

                        val lyricsTrack = pState.currentTrack
                        var sideLyrics by remember { mutableStateOf<List<dev.mellow.feature.player.LyricsLine>>(emptyList()) }
                        var sideIsLoadingLyrics by remember { mutableStateOf(true) }
                        LaunchedEffect(lyricsTrack?.id) {
                            sideIsLoadingLyrics = true
                            sideLyrics = if (lyricsTrack != null) {
                                mainViewModel.fetchLyrics(lyricsTrack.id).map { r ->
                                    dev.mellow.feature.player.LyricsLine(startMs = r.startMs, text = r.text)
                                }
                            } else emptyList()
                            sideIsLoadingLyrics = false
                        }

                        val borderColor = MellowTheme.colors.border
                        val panelBgColor = MellowTheme.colors.background.copy(alpha = 0.3f)
                        val density = LocalDensity.current
                        val topInsetPx = WindowInsets.systemBars.getTop(density).toFloat()
                        val bottomInsetPx = WindowInsets.systemBars.getBottom(density).toFloat()
                        Column(
                            modifier = Modifier
                                .width(400.dp)
                                .fillMaxSize()
                                .drawBehind {
                                    drawRect(
                                        color = panelBgColor,
                                        topLeft = Offset(0f, -topInsetPx),
                                        size = Size(size.width, size.height + topInsetPx + bottomInsetPx),
                                    )
                                    drawLine(
                                        color = borderColor,
                                        start = Offset(0f, -topInsetPx),
                                        end = Offset(0f, size.height + bottomInsetPx),
                                        strokeWidth = 1.dp.toPx(),
                                    )
                                },
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 24.dp)
                                    .padding(top = 20.dp, bottom = 8.dp),
                            ) {
                                Text(
                                    "Queue",
                                    fontSize = 13.sp,
                                    color = if (activeTab == 0) MellowTheme.colors.foreground else MellowTheme.colors.muted,
                                    fontWeight = if (activeTab == 0) FontWeight.Medium else FontWeight.Normal,
                                    modifier = Modifier
                                        .clickable { activeTab = 0 }
                                        .padding(bottom = 8.dp)
                                        .then(
                                            if (activeTab == 0) Modifier.drawBehind {
                                                drawLine(
                                                    color = borderColor,
                                                    start = Offset(0f, size.height),
                                                    end = Offset(size.width, size.height),
                                                    strokeWidth = 2.dp.toPx(),
                                                )
                                            } else Modifier,
                                        ),
                                )
                                Spacer(Modifier.width(MellowSpacing.Sp4))
                                Text(
                                    "Lyrics",
                                    fontSize = 13.sp,
                                    color = if (activeTab == 1) MellowTheme.colors.foreground else MellowTheme.colors.muted,
                                    fontWeight = if (activeTab == 1) FontWeight.Medium else FontWeight.Normal,
                                    modifier = Modifier
                                        .clickable { activeTab = 1 }
                                        .padding(bottom = 8.dp)
                                        .then(
                                            if (activeTab == 1) Modifier.drawBehind {
                                                drawLine(
                                                    color = borderColor,
                                                    start = Offset(0f, size.height),
                                                    end = Offset(size.width, size.height),
                                                    strokeWidth = 2.dp.toPx(),
                                                )
                                            } else Modifier,
                                        ),
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                when (activeTab) {
                                    0 -> dev.mellow.feature.player.QueueScreen(
                                        onBack = {},
                                        embedded = true,
                                        nowPlaying = sideNowPlaying,
                                        upNext = sideUpNext,
                                        currentAlbumName = pState.currentTrack?.albumName ?: "",
                                        shuffleEnabled = pState.shuffleEnabled,
                                        repeatMode = pState.repeatMode,
                                        onTrackClick = { relativeIndex ->
                                            mainViewModel.player.playFromQueue(currentIdx + 1 + relativeIndex)
                                        },
                                        onShuffleClick = { mainViewModel.player.toggleShuffle() },
                                        onRepeatClick = { mainViewModel.player.cycleRepeatMode() },
                                        onClearClick = { mainViewModel.player.clearQueue() },
                                        onMoveTrack = { from, to ->
                                            mainViewModel.player.moveQueueItem(currentIdx + 1 + from, currentIdx + 1 + to)
                                        },
                                        onRemoveTrack = { relativeIndex ->
                                            mainViewModel.player.removeFromQueue(currentIdx + 1 + relativeIndex)
                                        },
                                    )
                                    1 -> dev.mellow.feature.player.LyricsScreen(
                                        embedded = true,
                                        trackName = lyricsTrack?.name ?: "",
                                        artistName = lyricsTrack?.artistName ?: "",
                                        albumImageUrl = if (serverUrl != null) {
                                            val imgId = lyricsTrack?.imageId ?: lyricsTrack?.albumId
                                            if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                                        } else null,
                                        lyrics = sideLyrics,
                                        isLoadingLyrics = sideIsLoadingLyrics,
                                        positionMs = positionState.positionMs,
                                        durationMs = positionState.durationMs,
                                        isPlaying = pState.isPlaying,
                                        onClose = { activeTab = 0 },
                                        onSeekTo = { ms -> mainViewModel.player.seekTo(ms) },
                                        onPlayPauseClick = { mainViewModel.player.playPause() },
                                        onSkipNextClick = { mainViewModel.player.skipNext() },
                                        onSkipPreviousClick = { mainViewModel.player.skipPrevious() },
                                    )
                                }
                            }
                        }
                    },
                )
            },
            queueContent = { onBack ->
                val pState = playbackState
                val currentIdx = pState.currentIndex
                val queue = pState.queue

                val nowPlaying = pState.currentTrack?.let { t ->
                    dev.mellow.feature.player.QueueTrack(
                        id = t.id, title = t.name, artist = t.artistName ?: "",
                        album = t.albumName ?: "", duration = formatTrackDuration(t.duration),
                        imageUrl = if (serverUrl != null) {
                            val imgId = t.imageId ?: t.albumId
                            if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                        } else null,
                    )
                }

                val upNext = remember(queue, currentIdx, serverUrl) {
                    queue.filterIndexed { idx, _ -> idx > currentIdx }.map { t ->
                        dev.mellow.feature.player.QueueTrack(
                            id = t.id, title = t.name, artist = t.artistName ?: "",
                            album = t.albumName ?: "", duration = formatTrackDuration(t.duration),
                            imageUrl = if (serverUrl != null) {
                                val imgId = t.imageId ?: t.albumId
                                if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                            } else null,
                        )
                    }
                }

                dev.mellow.feature.player.QueueScreen(
                    onBack = onBack,
                    embedded = true,
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
                        onBack()
                    },
                    onMoveTrack = { from, to ->
                        mainViewModel.player.moveQueueItem(currentIdx + 1 + from, currentIdx + 1 + to)
                    },
                    onRemoveTrack = { relativeIndex ->
                        mainViewModel.player.removeFromQueue(currentIdx + 1 + relativeIndex)
                    },
                )
            },
            lyricsContent = { onBack ->
                val pState = playbackState
                val lyricsTrack = pState.currentTrack

                var lyrics by remember { mutableStateOf<List<dev.mellow.feature.player.LyricsLine>>(emptyList()) }
                var isLoadingLyrics by remember { mutableStateOf(true) }

                LaunchedEffect(lyricsTrack?.id) {
                    isLoadingLyrics = true
                    lyrics = if (lyricsTrack != null) {
                        mainViewModel.fetchLyrics(lyricsTrack.id).map { r ->
                            dev.mellow.feature.player.LyricsLine(startMs = r.startMs, text = r.text)
                        }
                    } else {
                        emptyList()
                    }
                    isLoadingLyrics = false
                }

                dev.mellow.feature.player.LyricsScreen(
                    embedded = true,
                    showControls = true,
                    trackName = lyricsTrack?.name ?: "",
                    artistName = lyricsTrack?.artistName ?: "",
                    albumImageUrl = if (serverUrl != null) {
                        val imgId = lyricsTrack?.imageId ?: lyricsTrack?.albumId
                        if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                    } else null,
                    lyrics = lyrics,
                    isLoadingLyrics = isLoadingLyrics,
                    positionMs = positionState.positionMs,
                    durationMs = positionState.durationMs,
                    isPlaying = pState.isPlaying,
                    onClose = onBack,
                    onSeekTo = { ms -> mainViewModel.player.seekTo(ms) },
                    onPlayPauseClick = { mainViewModel.player.playPause() },
                    onSkipNextClick = { mainViewModel.player.skipNext() },
                    onSkipPreviousClick = { mainViewModel.player.skipPrevious() },
                )
            },
            bottomNavHeightPx = sheetBottomNavPx,
        )

        var parentWindowOffset by remember { mutableStateOf(androidx.compose.ui.geometry.Offset.Zero) }
        if (playbackState.error == null) {
            SharedArtOverlay(
                imageUrl = if (serverUrl != null) {
                    val imgId = track.imageId ?: track.albumId
                    if (imgId != null) jellyfinImageUrl(serverUrl!!, imgId) else null
                } else null,
                dragFraction = sheetState.dragFraction,
                positions = sharedArtPositions,
                parentOffset = parentWindowOffset,
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { parentWindowOffset = it.positionInWindow() },
            )
        }
    }
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
            onStartMix = {
                mainViewModel.startMix(contextMenuState!!.menuData.id)
            },
            onToggleFavorite = {
                val t = contextMenuState!!.menuData
                mainViewModel.toggleFavorite(t.id, t.isFavorite)
            },
            onTrackInfo = {
                trackInfoTrack = contextMenuState?.track
                contextMenuState = null
            },
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

    if (trackInfoTrack != null) {
        val t = trackInfoTrack!!
        MellowDialog(
            onDismissRequest = { trackInfoTrack = null },
            title = t.name,
            dismissLabel = "Close",
            confirmLabel = null,
            content = {
                Column {
                    InfoRow("Artist", t.artistName ?: "Unknown")
                    InfoRow("Album", t.albumName ?: "Unknown")
                    InfoRow("Duration", formatTrackDuration(t.duration))
                    if (t.codec != null) InfoRow("Codec", t.codec!!.uppercase())
                    if (t.container != null) InfoRow("Format", t.container!!.uppercase())
                    InfoRow("Track #", "${t.trackNumber ?: "\u2014"}")
                }
            },
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, color = MellowTheme.colors.muted, modifier = Modifier.width(80.dp))
        Text(value, color = MellowTheme.colors.foreground)
    }
}

private data class ContextMenuState(
    val menuData: TrackMenuData,
    val track: Track,
)

@Composable
private fun TabScreenTopBar(
    route: String,
    isConnected: Boolean,
    isServerUnreachable: Boolean,
    onSettingsClick: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MellowTheme.colors.background)
            .padding(horizontal = MellowSpacing.Sp4, vertical = MellowSpacing.Sp3),
    ) {
        Text(
            text = when (route) {
                MellowNavDestination.Home.route -> "Mellow"
                MellowNavDestination.Library.route, "library" -> "Library"
                MellowNavDestination.Search.route -> "Search"
                MellowNavDestination.Favorites.route -> "Favorites"
                else -> ""
            },
            style = androidx.compose.material3.MaterialTheme.typography.headlineLarge,
            color = MellowTheme.colors.foreground,
        )
        Spacer(modifier = Modifier.weight(1f))
        dev.mellow.core.designsystem.component.ConnectionCloudIcon(
            isConnected = isConnected,
            isServerUnreachable = isServerUnreachable,
        )
        Box(modifier = Modifier.width(MellowSpacing.Sp2))
        androidx.compose.material3.IconButton(onClick = onSettingsClick) {
            androidx.compose.material3.Icon(
                imageVector = dev.mellow.core.designsystem.icon.PhosphorIcons.Gear,
                contentDescription = "Settings",
                tint = MellowTheme.colors.foreground,
                modifier = Modifier.height(20.dp).width(20.dp),
            )
        }
    }
}

private val MANUAL_LIBRARIES = listOf(
    com.mikepenz.aboutlibraries.entity.Library(
        uniqueId = "phosphor-icons:core",
        artifactVersion = null,
        name = "Phosphor Icons",
        description = "A flexible icon family for interfaces, diagrams, presentations and more.",
        website = "https://phosphoricons.com",
        developers = kotlinx.collections.immutable.persistentListOf(),
        organization = null,
        scm = null,
        licenses = kotlinx.collections.immutable.persistentSetOf(
            com.mikepenz.aboutlibraries.entity.License(
                name = "MIT License",
                url = "https://github.com/phosphor-icons/core/blob/main/LICENSE",
                spdxId = "MIT",
                hash = "phosphor-mit",
            ),
        ),
        funding = kotlinx.collections.immutable.persistentSetOf(),
        tag = null,
    ),
)

private val PLATFORM_PREFIXES = listOf(
    "androidx.", "com.google.", "jakarta.", "javax.",
    "org.jetbrains.", "org.jspecify",
)

@Composable
private fun StorageCapExceededDialog(
    usedBytes: Long,
    capBytes: Long,
    onIncreaseCap: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val gb = 1024L * 1024 * 1024
    val currentCapGb = if (capBytes == Long.MAX_VALUE) "Unlimited" else "${capBytes / gb} GB"
    val usedGb = "%.1f GB".format(usedBytes.toDouble() / gb)

    val options = listOf(
        10L * gb to "10 GB",
        20L * gb to "20 GB",
        50L * gb to "50 GB",
        Long.MAX_VALUE to "Unlimited",
    ).filter { it.first > capBytes }

    var selectedOption by remember { mutableStateOf(options.firstOrNull()?.first) }

    MellowDialog(
        onDismissRequest = onDismiss,
        title = "Storage Cap Reached",
        description = "You've used $usedGb of your $currentCapGb limit. Increase the cap to continue downloading.",
        dismissLabel = "Cancel",
        confirmLabel = if (selectedOption != null) "Increase" else null,
        onConfirm = if (selectedOption != null) {
            { onIncreaseCap(selectedOption!!) }
        } else null,
        content = {
            Column {
                val fraction = if (capBytes > 0 && capBytes != Long.MAX_VALUE) {
                    (usedBytes.toFloat() / capBytes).coerceIn(0f, 1f)
                } else 0f

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        "$usedGb used",
                        style = MaterialTheme.typography.labelSmall,
                        color = MellowPalette.Stone500,
                    )
                    Text(
                        "$currentCapGb limit",
                        style = MaterialTheme.typography.labelSmall,
                        color = MellowPalette.Stone500,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(MellowShapes.Full)
                        .background(MellowPalette.Stone800),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(fraction)
                            .height(4.dp)
                            .clip(MellowShapes.Full)
                            .background(MellowTheme.colors.warning),
                    )
                }
                Spacer(Modifier.height(20.dp))

                if (options.isNotEmpty()) {
                    options.forEach { (bytes, label) ->
                        MellowRadioOption(
                            label = label,
                            selected = selectedOption == bytes,
                            onClick = { selectedOption = bytes },
                        )
                    }
                }
            }
        },
    )
}

private fun formatTrackDuration(duration: Duration): String {
    val totalSeconds = duration.seconds
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
