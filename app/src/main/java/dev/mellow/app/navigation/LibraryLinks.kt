package dev.mellow.app.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.navigation.NavController
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.designsystem.component.ArtistPickerSheet
import dev.mellow.core.designsystem.component.PickerArtist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** The album page's route, with its `albumId` argument. */
internal const val ALBUM_ROUTE = "album/{albumId}?source={source}"

/** The artist page's route, with its `artistId` argument. */
internal const val ARTIST_ROUTE = "artist/{artistId}"

/**
 * Opens album and artist pages from the expanded player and the menus. The expanded player collapses first, so the page
 * doesn't open behind it, and the page already showing isn't opened again on top of itself. For a track or an album
 * with several artists, [openArtistOf] asks which one with the artist picker ([ArtistPickerHost]), which shows over the
 * expanded player; picking one opens it, dismissing the picker leaves everything as it was.
 */
@Stable
class LibraryLinks internal constructor(
    private val navController: NavController,
    private val sheetState: ExpandableSheetState,
    private val scope: CoroutineScope,
    private val toPickerArtist: suspend (ArtistEntity) -> PickerArtist,
) {
    /** The artists the picker offers while it's open, else null. */
    var pickerArtists by mutableStateOf<List<PickerArtist>?>(null)
        private set

    fun openAlbum(albumId: String) {
        collapsePlayer()
        if (!isShowing(ALBUM_ROUTE, "albumId", albumId)) navController.navigate("album/$albumId")
    }

    fun openArtist(artistId: String) {
        collapsePlayer()
        if (!isShowing(ARTIST_ROUTE, "artistId", artistId)) navController.navigate("artist/$artistId")
    }

    /**
     * Opens the artist of a track or an album: the one of [loadArtists] (its artists, in order), or the one of
     * [fallbackArtistId] when it has none; with several, the picker asks which.
     */
    fun openArtistOf(fallbackArtistId: String?, loadArtists: suspend () -> List<ArtistEntity>) {
        scope.launch {
            val artists = loadArtists()
            if (artists.size <= 1) {
                (artists.firstOrNull()?.id ?: fallbackArtistId)?.let(::openArtist)
            } else {
                pickerArtists = artists.map { toPickerArtist(it) }
            }
        }
    }

    fun pickArtist(artistId: String) {
        pickerArtists = null
        openArtist(artistId)
    }

    fun dismissArtistPicker() {
        pickerArtists = null
    }

    private fun collapsePlayer() {
        if (sheetState.dragFraction > 0f) sheetState.collapse()
    }

    private fun isShowing(route: String, argument: String, id: String): Boolean {
        val entry = navController.currentBackStackEntry ?: return false
        return entry.destination.route == route && entry.arguments?.getString(argument) == id
    }
}

/** [LibraryLinks] for [navController]'s pages and the player in [sheetState]. */
@Composable
fun rememberLibraryLinks(
    navController: NavController,
    sheetState: ExpandableSheetState,
    toPickerArtist: suspend (ArtistEntity) -> PickerArtist,
): LibraryLinks {
    val scope = rememberCoroutineScope()
    val currentToPickerArtist by rememberUpdatedState(toPickerArtist)
    return remember(navController, sheetState, scope) {
        LibraryLinks(navController, sheetState, scope) { currentToPickerArtist(it) }
    }
}

/** The artist picker of [links], while it's open. */
@Composable
fun ArtistPickerHost(links: LibraryLinks) {
    val artists = links.pickerArtists ?: return
    ArtistPickerSheet(
        artists = artists,
        onArtistClick = links::pickArtist,
        onDismiss = links::dismissArtistPicker,
    )
}

/** What the expanded player can open for a track: its album and its artists, each only when it's in the library. */
data class TrackLinkTargets(val albumId: String?, val hasArtist: Boolean) {
    companion object {
        val None = TrackLinkTargets(albumId = null, hasArtist = false)
    }
}

/**
 * The [TrackLinkTargets] of a track on album [albumId] whose artists are [hasArtists] or, failing that, the one of
 * [fallbackArtistId]; [albumExists] and [artistExists] say whether the library has them.
 */
suspend fun trackLinkTargets(
    albumId: String?,
    fallbackArtistId: String?,
    hasArtists: suspend () -> Boolean,
    albumExists: suspend (String) -> Boolean,
    artistExists: suspend (String) -> Boolean,
): TrackLinkTargets = TrackLinkTargets(
    albumId = albumId?.takeIf { albumExists(it) },
    hasArtist = hasArtists() || (fallbackArtistId != null && artistExists(fallbackArtistId)),
)
