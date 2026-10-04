package dev.mellow.core.player

import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.TrackEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A head unit plays a browsed track through the legacy session's `playFromMediaId`: the session gets back the media
 * ID browse gave the track and nothing else, no extras. That ID alone has to say which list the track was tapped in.
 */
class AndroidAutoTapTest {

    private val trackDao = mockk<TrackDao>()
    private val playlistDao = mockk<PlaylistDao>()
    private val autoQueue = AutoQueue(trackDao, playlistDao)

    @Test
    fun `a song tapped in Library Songs queues the songs around it`() = runTest {
        val songs = (0 until 10).map { track("s$it", albumId = "album") }
        coEvery { trackDao.getTrackById("s5") } returns songs[5]
        coEvery { trackDao.countTracksBefore(SERVER, "s5", "s5", false) } returns 5
        coEvery { trackDao.countTracks(SERVER, false) } returns 10
        coEvery { trackDao.getTracksByServerPaged(SERVER, false, 500, 0) } returns songs

        assertEquals(songs to 5, tap(songs[5], listedUnder = queueParentOf(MellowMediaService.LIBRARY_SONGS)))
    }

    @Test
    fun `a favorite tapped in Favorites Tracks queues the favorites around it`() = runTest {
        val favorites = (0 until 4).map { track("f$it", albumId = "album") }
        coEvery { trackDao.getTrackById("f2") } returns favorites[2]
        coEvery { trackDao.countFavoriteTracksBefore(SERVER, "f2", false) } returns 2
        coEvery { trackDao.countFavoriteTracks(SERVER, false) } returns 4
        coEvery { trackDao.getFavoriteTracksSlice(SERVER, false, 500, 0) } returns favorites

        assertEquals(favorites to 2, tap(favorites[2], listedUnder = queueParentOf(MellowMediaService.FAV_TRACKS)))
    }

    @Test
    fun `a playlist track tapped in its playlist queues the playlist around it`() = runTest {
        val playlist = (0 until 3).map { track("p$it", albumId = "album") }
        coEvery { trackDao.getTrackById("p1") } returns playlist[1]
        coEvery { playlistDao.countPlaylistTracksBefore("pl", "p1", false) } returns 1
        coEvery { playlistDao.countPlaylistTracks("pl", false) } returns 3
        coEvery { playlistDao.getPlaylistTracksSlice("pl", false, 500, 0) } returns playlist

        assertEquals(playlist to 1, tap(playlist[1], listedUnder = queueParentOf("playlist:pl")))
    }

    @Test
    fun `offline, a song tapped in Library Songs queues the downloaded songs around it`() = runTest {
        val downloaded = listOf(track("d0", albumId = "album"), track("s5", albumId = "album"))
        coEvery { trackDao.getTrackById("s5") } returns downloaded[1]
        coEvery { trackDao.countTracksBefore(SERVER, "s5", "s5", true) } returns 1
        coEvery { trackDao.countTracks(SERVER, true) } returns 2
        coEvery { trackDao.getTracksByServerPaged(SERVER, true, 500, 0) } returns downloaded

        val queue = tap(downloaded[1], listedUnder = queueParentOf(MellowMediaService.LIBRARY_SONGS), downloadedOnly = true)

        assertEquals(downloaded to 1, queue)
    }

    @Test
    fun `offline, a favorite tapped in Favorites Tracks queues the downloaded favorites around it`() = runTest {
        val downloaded = listOf(track("f2", albumId = "album"))
        coEvery { trackDao.getTrackById("f2") } returns downloaded[0]
        coEvery { trackDao.countFavoriteTracksBefore(SERVER, "f2", true) } returns 0
        coEvery { trackDao.countFavoriteTracks(SERVER, true) } returns 1
        coEvery { trackDao.getFavoriteTracksSlice(SERVER, true, 500, 0) } returns downloaded

        val queue = tap(downloaded[0], listedUnder = queueParentOf(MellowMediaService.FAV_TRACKS), downloadedOnly = true)

        assertEquals(downloaded to 0, queue)
    }

    @Test
    fun `offline, a playlist track tapped in its playlist queues the playlist's downloads around it`() = runTest {
        val downloaded = listOf(track("p0", albumId = "album"), track("p1", albumId = "album"))
        coEvery { trackDao.getTrackById("p1") } returns downloaded[1]
        coEvery { playlistDao.countPlaylistTracksBefore("pl", "p1", true) } returns 1
        coEvery { playlistDao.countPlaylistTracks("pl", true) } returns 2
        coEvery { playlistDao.getPlaylistTracksSlice("pl", true, 500, 0) } returns downloaded

        assertEquals(downloaded to 1, tap(downloaded[1], listedUnder = queueParentOf("playlist:pl"), downloadedOnly = true))
    }

    @Test
    fun `an artist's top track tapped under the artist queues the artist's top tracks`() = runTest {
        val top = listOf(track("hit1", albumId = "album"), track("hit2", albumId = "other-album"))
        coEvery { trackDao.getTrackById("hit2") } returns top[1]
        coEvery { trackDao.getTracksByResolvedArtistSync("ar", 20) } returns top

        assertEquals(top to 1, tap(top[1], listedUnder = "artist:ar"))
    }

    @Test
    fun `a track tapped in its album queues the album`() = runTest {
        val album = listOf(track("a1", albumId = "a"), track("a2", albumId = "a"))
        coEvery { trackDao.getTrackById("a2") } returns album[1]
        coEvery { trackDao.getTracksByAlbumSync("a") } returns album

        assertEquals(album to 1, tap(album[1], listedUnder = null))
    }

    @Test
    fun `an ID naming a list nobody knows plays the track with its album`() = runTest {
        val album = listOf(track("a1", albumId = "a"), track("a2", albumId = "a"))
        coEvery { trackDao.getTrackById("a2") } returns album[1]
        coEvery { trackDao.getTracksByAlbumSync("a") } returns album

        val queue = autoQueue.forItem(BrowsedTrackId.of("mystery:list", "a2"), parentHint = null, SERVER, false)

        assertEquals(album to 1, queue)
    }

    @Test
    fun `a plain track ID still plays as before, with its extras as a hint`() = runTest {
        val songs = (0 until 3).map { track("s$it", albumId = "album") }
        val album = songs.take(2)
        coEvery { trackDao.getTrackById("s1") } returns songs[1]
        coEvery { trackDao.getTracksByAlbumSync("album") } returns album
        coEvery { trackDao.countTracksBefore(SERVER, "s1", "s1", false) } returns 1
        coEvery { trackDao.countTracks(SERVER, false) } returns 3
        coEvery { trackDao.getTracksByServerPaged(SERVER, false, 500, 0) } returns songs

        // Saved queues and the app use plain IDs: no hint, the album; a hint in the extras, that list.
        assertEquals(album to 1, autoQueue.forItem("s1", parentHint = null, SERVER, downloadedOnly = false))
        assertEquals(
            songs to 1,
            autoQueue.forItem("s1", parentHint = MellowMediaService.LIBRARY_SONGS, SERVER, downloadedOnly = false),
        )
    }

    /** What the session gets when [track], browsed under [listedUnder], is tapped: its media ID and no extras. */
    private suspend fun tap(track: TrackEntity, listedUnder: String?, downloadedOnly: Boolean = false) =
        autoQueue.forItem(browsedTrackMediaId(listedUnder, track), parentHint = null, SERVER, downloadedOnly)

    private companion object {
        const val SERVER = "server"

        fun track(id: String, albumId: String?) = TrackEntity(
            id = id, serverId = SERVER, name = id, sortName = id, albumId = albumId, albumName = null,
            artistId = null, artistName = null, trackNumber = null, discNumber = null, durationMs = 0,
            genres = emptyList(), imageTag = null, isFavorite = false, playCount = 0, lastPlayedAt = 0,
            normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
            channels = null, resolvedArtistId = null, dateAdded = 0, lastSynced = 0,
        )
    }
}
