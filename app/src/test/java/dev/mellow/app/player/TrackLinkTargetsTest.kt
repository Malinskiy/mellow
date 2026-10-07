package dev.mellow.app.player

import dev.mellow.app.navigation.TrackLinkTargets
import dev.mellow.app.navigation.trackLinkTargets
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/** The expanded player opens a track's album and artist only when the library has them. */
class TrackLinkTargetsTest {

    @Test
    fun `an album and artists in the library can both be opened`() = runBlocking {
        assertEquals(TrackLinkTargets("album", hasArtist = true), targets(albumId = "album", hasArtists = true))
    }

    @Test
    fun `an album that isn't in the library, or no album at all, can't be opened`() = runBlocking {
        assertEquals(null, targets(albumId = "gone", albums = emptySet()).albumId)
        assertEquals(null, targets(albumId = null).albumId)
    }

    @Test
    fun `without artist rows the track's own artist id opens if the library has it`() = runBlocking {
        assertEquals(true, targets(hasArtists = false, fallback = "artist", artists = setOf("artist")).hasArtist)
        assertEquals(false, targets(hasArtists = false, fallback = "gone", artists = setOf("artist")).hasArtist)
        assertEquals(false, targets(hasArtists = false, fallback = null).hasArtist)
    }

    private suspend fun targets(
        albumId: String? = "album",
        hasArtists: Boolean = true,
        fallback: String? = null,
        albums: Set<String> = setOf("album"),
        artists: Set<String> = emptySet(),
    ) = trackLinkTargets(
        albumId = albumId,
        fallbackArtistId = fallback,
        hasArtists = { hasArtists },
        albumExists = { it in albums },
        artistExists = { it in artists },
    )
}
