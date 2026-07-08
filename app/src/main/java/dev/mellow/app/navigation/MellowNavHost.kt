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
import dev.mellow.core.designsystem.component.MellowNavDestination
import dev.mellow.core.designsystem.component.MiniPlayer
import dev.mellow.core.designsystem.theme.MellowSpacing
import dev.mellow.core.designsystem.theme.MellowTheme
import dev.mellow.feature.home.FavoritesScreen
import dev.mellow.feature.home.PlaylistsScreen
import dev.mellow.feature.library.AlbumDetailScreen
import dev.mellow.feature.library.ArtistDetailScreen
import dev.mellow.feature.library.LibraryScreen
import dev.mellow.feature.library.LibraryViewModel

import dev.mellow.feature.player.PlayerScreen
import dev.mellow.feature.player.QueueScreen
import dev.mellow.feature.search.SearchScreen
import dev.mellow.feature.settings.LoginScreen
import dev.mellow.feature.settings.LoginViewModel
import dev.mellow.feature.settings.SettingsScreen

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

    val fullScreenRoutes = setOf("now_playing", "queue")
    val isFullScreen = currentRoute in fullScreenRoutes
    var showMiniPlayer by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        contentWindowInsets = WindowInsets.systemBars,
        bottomBar = {
            if (!isFullScreen) {
                Column {
                    if (showMiniPlayer) {
                        MiniPlayer(
                            title = "Reckoner",
                            artist = "Radiohead",
                            imageUrl = null,
                            isPlaying = true,
                            progress = 0.35f,
                            onPlayPauseClick = {},
                            onNextClick = {},
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

                    LaunchedEffect(serverId) {
                        if (serverId.isNotEmpty()) libraryVm.loadLibrary(serverId)
                    }

                    LibraryScreen(
                        albums = state.albums.map { it.name to (it.artistName ?: "") },
                        artists = state.artists.map { it.name to it.albumCount },
                        tracks = emptyList(),
                        serverUrl = mainViewModel.serverUrl.collectAsState().value,
                    )
                }
                composable(MellowNavDestination.Search.route) { SearchScreen() }
                composable(MellowNavDestination.Favorites.route) { FavoritesScreen() }
                composable(MellowNavDestination.Playlists.route) { PlaylistsScreen() }
                composable("settings") { SettingsScreen(onBack = { navController.popBackStack() }) }
                composable("album/{albumId}") { AlbumDetailScreen(onBack = { navController.popBackStack() }) }
                composable("artist/{artistId}") { ArtistDetailScreen(onBack = { navController.popBackStack() }) }
                composable("now_playing") {
                    PlayerScreen(
                        onCollapse = { navController.popBackStack() },
                        onQueueClick = { navController.navigate("queue") },
                    )
                }
                composable("queue") { QueueScreen(onBack = { navController.popBackStack() }) }
            }
        }
    }
}
