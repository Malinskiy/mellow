package dev.mellow.core.player

import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.dao.TrackKeysetQueryFactory
import dev.mellow.core.database.entity.TrackEntity
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** What Android Auto queues when a track is picked from a list it browsed. */
class AutoQueueTest {

    private val trackDao = mockk<TrackDao>()
    private val playlistDao = mockk<PlaylistDao>()
    private val trackKeysetQueries = mockk<TrackKeysetQueryFactory>()
    private val autoQueue = AutoQueue(trackDao, playlistDao, trackKeysetQueries)

    @Test
    fun `a song picked from Library Songs queues the songs around it, not its album`() = runTest {
        val song = track("s5", albumId = "album-of-s5")
        val songs = (0 until 10).map { track("s$it") }
        coEvery { trackKeysetQueries.autoQueueWindow(SERVER, false, "s5", 100, 500) } returns songs

        // The parent the songs Android Auto lists under Library > Songs carry.
        val parent = queueParentOf(MellowMediaService.LIBRARY_SONGS)

        assertEquals(songs to 5, autoQueue.around("s5", song, parent, SERVER, downloadedOnly = false))
    }

    @Test
    fun `songs, favorites and playlists are queued as listed, album tracks with their album`() {
        assertEquals(MellowMediaService.LIBRARY_SONGS, queueParentOf(MellowMediaService.LIBRARY_SONGS))
        assertEquals(MellowMediaService.FAV_TRACKS, queueParentOf(MellowMediaService.FAV_TRACKS))
        assertEquals("playlist:p", queueParentOf("playlist:p"))
        assertNull(queueParentOf("album:a"))
    }

    @Test
    fun `offline, a song is queued among the downloaded songs Android Auto listed`() = runTest {
        val song = track("s5")
        val downloaded = listOf(track("d0"), track("d1"), song)
        coEvery { trackKeysetQueries.autoQueueWindow(SERVER, true, "s5", 100, 500) } returns downloaded

        val queue = autoQueue.around("s5", song, MellowMediaService.LIBRARY_SONGS, SERVER, downloadedOnly = true)

        assertEquals(downloaded to 2, queue)
    }

    @Test
    fun `offline, a favorite is queued among the downloaded favorites`() = runTest {
        val favorite = track("f2")
        val downloaded = listOf(track("f0"), favorite)
        coEvery { trackDao.countFavoriteTracksBefore(SERVER, "f2", true) } returns 1
        coEvery { trackDao.countFavoriteTracks(SERVER, true) } returns 2
        coEvery { trackDao.getFavoriteTracksSlice(SERVER, true, 500, 0) } returns downloaded

        val queue = autoQueue.around("f2", favorite, MellowMediaService.FAV_TRACKS, SERVER, downloadedOnly = true)

        assertEquals(downloaded to 1, queue)
    }

    @Test
    fun `offline, a playlist track is queued among the playlist's downloads`() = runTest {
        val picked = track("p3")
        val downloaded = listOf(picked, track("p7"))
        coEvery { playlistDao.countPlaylistTracksBefore("pl", "p3", true) } returns 0
        coEvery { playlistDao.countPlaylistTracks("pl", true) } returns 2
        coEvery { playlistDao.getPlaylistTracksSlice("pl", true, 500, 0) } returns downloaded

        assertEquals(downloaded to 0, autoQueue.around("p3", picked, "playlist:pl", SERVER, downloadedOnly = true))
    }

    @Test
    fun `a track missing from the part of its list that was read plays on its own`() = runTest {
        val song = track("s5")
        coEvery { trackKeysetQueries.autoQueueWindow(SERVER, false, "s5", 100, 500) } returns
            listOf(track("s0"), track("s1"))

        assertNull(autoQueue.around("s5", song, MellowMediaService.LIBRARY_SONGS, SERVER, downloadedOnly = false))
    }

    @Test
    fun `a song no longer in the library plays on its own`() = runTest {
        assertNull(autoQueue.around("gone", null, MellowMediaService.LIBRARY_SONGS, SERVER, downloadedOnly = false))
    }

    @Test
    fun `an album track is queued with its album`() = runTest {
        val album = listOf(track("a1", albumId = "a"), track("a2", albumId = "a"), track("a3", albumId = "a"))
        coEvery { trackDao.getTracksByAlbumSync("a") } returns album

        assertEquals(album to 1, autoQueue.around("a2", album[1], "album:a", SERVER, downloadedOnly = false))
    }

    private companion object {
        const val SERVER = "server"

        fun track(id: String, albumId: String? = null) = TrackEntity(
            id = id, serverId = SERVER, name = id, sortName = id, albumId = albumId, albumName = null,
            artistId = null, artistName = null, trackNumber = null, discNumber = null, durationMs = 0,
            genres = emptyList(), imageTag = null, isFavorite = false, playCount = 0, lastPlayedAt = 0,
            normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
            channels = null, resolvedArtistId = null, dateAdded = 0, lastSynced = 0,
        )
    }
}
