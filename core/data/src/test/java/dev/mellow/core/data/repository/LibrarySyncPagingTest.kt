package dev.mellow.core.data.repository

import dev.mellow.core.common.MellowResult
import io.mockk.coEvery
import io.mockk.coVerify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Collections
import java.util.UUID
import kotlin.random.Random

/**
 * Full passes over a library of several pages per listing (500 artists or albums, 1,000 tracks a page), paged by
 * offset as Jellyfin does, so items shift between pages when the library changes while it's paged.
 */
class LibrarySyncPagingTest : LibrarySyncHarness() {

    private val allArtists = (0L until 600).map { artist(it) }
    private val allAlbums = (0L until 1_200).map { album(it, allArtists[(it % 600).toInt()]) }
    private val allTracks = (0L until 2_500).map { n ->
        track(n, allAlbums[(n % 1_200).toInt()], allArtists[(n % 600).toInt()])
    }

    @Before
    fun fillServer() {
        artists = allArtists
        albums = allAlbums
        tracks = allTracks
    }

    @Test
    fun `a full pass pages through every listing and asks about unseen items in batches`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        assertLibrarySize(artists = 600, albums = 1_200, tracks = 2_500)
        coVerify { dataSource.getArtistsPaged(USER, 500, 500) }
        coVerify { dataSource.getAlbumsPaged(USER, 1_000, 500, null) }
        coVerify { dataSource.getTracksPaged(USER, 2_000, 1_000, null) }

        // The server deletes its last 150 albums and their 300 tracks.
        val goneAlbums = allAlbums.drop(1_050)
        val goneTracks = allTracks.filterIndexed { n, _ -> n % 1_200 >= 1_050 }
        albums = allAlbums - goneAlbums.toSet()
        tracks = allTracks - goneTracks.toSet()
        val askedAlbums = mutableListOf<List<UUID>>()
        val askedTracks = mutableListOf<List<UUID>>()
        coEvery { dataSource.getAlbumsByIds(USER, capture(askedAlbums)) } answers { albums.withIds(secondArg()) }
        coEvery { dataSource.getTracksByIds(USER, capture(askedTracks)) } answers { tracks.withIds(secondArg()) }
        preferences.requestFullPass()

        assertSuccess(repository.syncLibrary(SERVER))

        assertEquals(listOf(100, 50), askedAlbums.map { it.size })
        assertEquals(goneAlbums.map { it.id }.toSet(), askedAlbums.flatten().toSet())
        assertEquals(listOf(100, 100, 100), askedTracks.map { it.size })
        assertEquals(goneTracks.map { it.id }.toSet(), askedTracks.flatten().toSet())
        assertLibrarySize(artists = 600, albums = 1_050, tracks = 2_200)
        assertNull(db.trackDao().getTrackById(goneTracks.last().key))
        assertEquals(emptyList<String>(), db.trackDao().getArtistNamesForTrack(goneTracks.last().key))
        assertNull(db.albumDao().getAlbumById(goneAlbums.first().key))
        assertEquals(listOf(allArtists[0].name), db.trackDao().getArtistNamesForTrack(allTracks[1_200].key))
        assertFalse(isRebuildPending())
    }

    @Test
    fun `a pass listing in another order keeps everything the server still has`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        artists = allArtists.reversed()
        albums = allAlbums.shuffled(Random(1))
        tracks = allTracks.reversed()
        preferences.requestFullPass()

        assertSuccess(repository.syncLibrary(SERVER))

        assertLibrarySize(artists = 600, albums = 1_200, tracks = 2_500)
        coVerify(exactly = 0) { dataSource.getAlbumsByIds(any(), any()) }
        coVerify(exactly = 0) { dataSource.getTracksByIds(any(), any()) }
        assertFalse(isRebuildPending())
    }

    @Test
    fun `an order that shifts between pages fails the pass and deletes nothing`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val gone = allTracks[5]
        tracks = allTracks - gone
        // Between the first and the second page, the track at the page boundary trades places with the next one: the
        // second page repeats it, and no page returns the other.
        val shifted = tracks.toMutableList().apply { Collections.swap(this, 999, 1_000) }
        coEvery { dataSource.getTracksPaged(USER, any(), any(), null) } answers {
            val startIndex = secondArg<Int>()
            page(if (startIndex == 1_000) shifted else tracks, startIndex, thirdArg())
        }
        preferences.requestFullPass()

        assertFailsAndRemovesNothing(gone) { repository.syncLibrary(SERVER) }

        // The next pass, with the order holding still, completes.
        coEvery { dataSource.getTracksPaged(USER, any(), any(), null) } answers {
            page(tracks, secondArg(), thirdArg())
        }
        assertSuccess(repository.syncLibrary(SERVER))
        assertNull(db.trackDao().getTrackById(gone.key))
        assertLibrarySize(artists = 600, albums = 1_200, tracks = 2_499)
    }

    @Test
    fun `a track added before the offset while paging fails the pass`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val gone = allTracks[5]
        tracks = allTracks - gone
        // After the first page, a track that sorts first is added: later pages start one track early, repeating one.
        val added = listOf(track(9_999, allAlbums[0], allArtists[0])) + tracks
        coEvery { dataSource.getTracksPaged(USER, any(), any(), null) } answers {
            val startIndex = secondArg<Int>()
            val limit = thirdArg<Int>()
            page(if (startIndex == 0 && limit > 1) tracks else added, startIndex, limit)
        }
        preferences.requestFullPass()

        assertFailsAndRemovesNothing(gone) { repository.syncLibrary(SERVER) }
    }

    @Test
    fun `a total that changes while paging fails the pass even when the end counts agree`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val gone = allTracks[7]
        tracks = allTracks - gone
        // When the pass starts, the server lists one more track, which is deleted before the pass gets to it: the
        // pass sees every track the server lists at the end, but the listing changed underneath it.
        coEvery { dataSource.getTracksPaged(USER, any(), any(), null) } answers {
            val listed = page(tracks, secondArg(), thirdArg())
            val isFirstPage = secondArg<Int>() == 0 && thirdArg<Int>() > 1
            if (isFirstPage) listed.copy(totalRecordCount = tracks.size + 1) else listed
        }
        preferences.requestFullPass()

        assertFailsAndRemovesNothing(gone) { repository.syncLibrary(SERVER) }
    }

    /** Runs [sync], which must fail as the library changed underneath it, leaving the library and its record alone. */
    private suspend fun assertFailsAndRemovesNothing(gone: BaseItemDto, sync: suspend () -> MellowResult<Unit>) {
        val lastSynced = preferences.lastSyncTimestamp.first()

        val result = sync()

        assertTrue((result as MellowResult.Error).exception is LibraryChangedDuringSyncException)
        assertNotNull("not removed", db.trackDao().getTrackById(gone.key))
        assertLibrarySize(artists = 600, albums = 1_200, tracks = 2_500)
        coVerify(exactly = 0) { dataSource.getTracksByIds(any(), any()) }
        assertTrue(isRebuildPending())
        assertEquals(lastSynced, preferences.lastSyncTimestamp.first())
    }

    private suspend fun assertLibrarySize(artists: Int, albums: Int, tracks: Int) {
        assertEquals(artists, countRows("artists", SERVER))
        assertEquals(albums, countRows("albums", SERVER))
        assertEquals(tracks, countRows("tracks", SERVER))
    }
}
