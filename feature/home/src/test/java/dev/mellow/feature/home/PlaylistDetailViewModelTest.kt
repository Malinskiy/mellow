package dev.mellow.feature.home

import androidx.lifecycle.SavedStateHandle
import androidx.paging.PagingData
import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.data.repository.PlaylistRepository
import dev.mellow.core.model.Track
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.time.Duration

/** Playing a playlist queues at most 500 tracks, however long it is. */
@OptIn(ExperimentalCoroutinesApi::class)
class PlaylistDetailViewModelTest {

    private val playlists = mockk<PlaylistRepository>()
    private val library = mockk<LibraryRepository>()

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { playlists.getPagedPlaylistTracks(PLAYLIST) } returns flowOf(PagingData.empty())
        coEvery { playlists.getPlaylistById(PLAYLIST) } returns MellowResult.Success(null)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `play starts a long playlist from the top, 500 tracks at most`() = runTest {
        val first = tracks(0 until 500)
        coEvery { playlists.countPlaylistTracks(PLAYLIST) } returns MellowResult.Success(3_000)
        coEvery { playlists.getPlaylistTracksSlice(PLAYLIST, 0, 500) } returns MellowResult.Success(first)

        assertEquals(first to 0, viewModel().tracksToPlay(index = 0, trackId = null))
    }

    @Test
    fun `playing a track of a long playlist queues the 500 around it`() = runTest {
        val window = tracks(1_900 until 2_400)
        coEvery { playlists.countPlaylistTracks(PLAYLIST) } returns MellowResult.Success(3_000)
        coEvery { playlists.getPlaylistTracksSlice(PLAYLIST, 1_900, 500) } returns MellowResult.Success(window)

        assertEquals(window to 100, viewModel().tracksToPlay(index = 2_000, trackId = "t2000"))
    }

    @Test
    fun `a short playlist is queued whole`() = runTest {
        val all = tracks(0 until 40)
        coEvery { playlists.countPlaylistTracks(PLAYLIST) } returns MellowResult.Success(40)
        coEvery { playlists.getPlaylistTracksSlice(PLAYLIST, 0, 500) } returns MellowResult.Success(all)

        assertEquals(all to 30, viewModel().tracksToPlay(index = 30, trackId = "t30"))
    }

    @Test
    fun `a track that moved since the list was shown plays on its own`() = runTest {
        val moved = track(7)
        coEvery { playlists.countPlaylistTracks(PLAYLIST) } returns MellowResult.Success(5)
        coEvery { playlists.getPlaylistTracksSlice(PLAYLIST, 0, 500) } returns MellowResult.Success(tracks(0 until 5))
        coEvery { library.getTrack("t7") } returns MellowResult.Success(moved)

        assertEquals(listOf(moved) to 0, viewModel().tracksToPlay(index = 3, trackId = "t7"))
    }

    @Test
    fun `shuffling picks at most 500 of the playlist's tracks at random, in the order picked`() = runTest {
        val picked = tracks(0 until 500).shuffled()
        coEvery { playlists.pickRandomPlaylistTracks(PLAYLIST, 500) } returns MellowResult.Success(picked)

        assertEquals(picked, viewModel().shuffledTracks())
    }

    private fun viewModel() =
        PlaylistDetailViewModel(SavedStateHandle(mapOf("playlistId" to PLAYLIST)), playlists, library)

    private companion object {
        const val PLAYLIST = "playlist"

        fun tracks(ids: IntRange) = ids.map(::track)

        fun track(id: Int) = Track(
            id = "t$id", name = "Track $id", albumId = null, albumName = null, artistId = null, artistName = null,
            trackNumber = null, discNumber = null, duration = Duration.ZERO, genres = emptyList(), imageId = null,
            isFavorite = false, playCount = 0, lastPlayedAt = 0, normalizationGain = null,
        )
    }
}
