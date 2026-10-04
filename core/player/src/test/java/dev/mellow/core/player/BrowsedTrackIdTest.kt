package dev.mellow.core.player

import dev.mellow.core.database.entity.TrackEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class BrowsedTrackIdTest {

    @Test
    fun `every list a track is browsed in round-trips through its media ID`() {
        val parents = listOf(
            MellowMediaService.LIBRARY_SONGS,
            MellowMediaService.FAV_TRACKS,
            "playlist:$PLAYLIST",
            "album:$ALBUM",
            "artist:$ARTIST",
            // A parent may contain the separators itself; only the track ID can't.
            "genre:AC/DC: Live/Rare",
        )

        for (parent in parents) {
            assertEquals(parent, BrowsedTrackId(parent, TRACK), BrowsedTrackId.parse(BrowsedTrackId.of(parent, TRACK)))
        }
    }

    @Test
    fun `the media ID reads as the format says`() {
        assertEquals("track:library_songs/$TRACK", BrowsedTrackId.of(MellowMediaService.LIBRARY_SONGS, TRACK))
        assertEquals("track:playlist:$PLAYLIST/$TRACK", BrowsedTrackId.of("playlist:$PLAYLIST", TRACK))
    }

    @Test
    fun `plain IDs, as the app and saved queues use, read as plain track IDs`() {
        for (plain in listOf(TRACK, "ffd1fa5d97fb4d7b8f5c3b5f7a4c1e2d", "album:$ALBUM", "artist:$ARTIST", "")) {
            assertEquals(plain, BrowsedTrackId(null, plain), BrowsedTrackId.parse(plain))
        }
    }

    @Test
    fun `malformed browsed IDs read as plain, so nothing is misread as another track`() {
        val malformed = listOf("track:", "track:/$TRACK", "track:album:$ALBUM/", "track:$TRACK", "track:/")

        for (mediaId in malformed) {
            assertEquals(mediaId, BrowsedTrackId(null, mediaId), BrowsedTrackId.parse(mediaId))
        }
    }

    @Test
    fun `a list nobody knows still reads, and is left for the queue to ignore`() {
        assertEquals(BrowsedTrackId("mystery:list", TRACK), BrowsedTrackId.parse("track:mystery:list/$TRACK"))
    }

    @Test
    fun `without a parent, or for an ID that would be ambiguous, the media ID is the plain track ID`() {
        assertEquals(TRACK, BrowsedTrackId.of(null, TRACK))
        assertEquals(TRACK, BrowsedTrackId.of("", TRACK))
        assertEquals("odd/id", BrowsedTrackId.of(MellowMediaService.LIBRARY_SONGS, "odd/id"))
        assertEquals(BrowsedTrackId(null, "odd/id"), BrowsedTrackId.parse("odd/id"))
    }

    @Test
    fun `a browsed track names the list it's listed in, or else its album`() {
        assertEquals("track:library_songs/$TRACK", browsedTrackMediaId(MellowMediaService.LIBRARY_SONGS, track(ALBUM)))
        assertEquals("track:album:$ALBUM/$TRACK", browsedTrackMediaId(null, track(ALBUM)))
        assertEquals(TRACK, browsedTrackMediaId(null, track(albumId = null)))
    }

    private companion object {
        const val TRACK = "d8a33446-dc40-cd1e-8741-d7e9dcf7efae"
        const val ALBUM = "0c3f3d7e-5b1a-4a5e-9d4b-2f6e8a1c7b90"
        const val PLAYLIST = "6e2b9a14-8f3c-4d71-a5e0-93b7c2d4f160"
        const val ARTIST = "a1b2c3d4-e5f6-4a7b-8c9d-0e1f2a3b4c5d"

        fun track(albumId: String?) = TrackEntity(
            id = TRACK, serverId = "server", name = "Song", sortName = "Song", albumId = albumId, albumName = null,
            artistId = null, artistName = null, trackNumber = null, discNumber = null, durationMs = 0,
            genres = emptyList(), imageTag = null, isFavorite = false, playCount = 0, lastPlayedAt = 0,
            normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
            channels = null, resolvedArtistId = null, dateAdded = 0, lastSynced = 0,
        )
    }
}
