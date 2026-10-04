package dev.mellow.feature.library

import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.preferences.DisplayPreferences
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.LibrarySort
import dev.mellow.core.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import java.time.Duration

@OptIn(ExperimentalCoroutinesApi::class)
class LibraryViewModelTest {

    private val repository = mockk<LibraryRepository>()
    private val downloadedOnly = MutableStateFlow(false)
    private val displayPreferences = mockk<DisplayPreferences> {
        every { downloadedOnly } returns this@LibraryViewModelTest.downloadedOnly
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        every { repository.getGenres(any(), any()) } returns flowOf(MellowResult.Success(listOf("Jazz", "Rock")))
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `playing a track of a long list queues the tracks around it`() = runTest {
        val window = tracks(900 until 1_400)
        coEvery { repository.getTracksSlice(SERVER, LibrarySort.RecentlyAdded, false, 900, 500) } returns
            MellowResult.Success(window)
        val viewModel = viewModel()

        val (queue, start) = viewModel.tracksToPlay(index = 1_000, trackId = "t1000", count = 50_000)!!

        assertEquals(window, queue)
        assertEquals(100, start)
    }

    @Test
    fun `playing a track of a short list queues the whole list`() = runTest {
        val all = tracks(0 until 300)
        coEvery { repository.getTracksSlice(SERVER, LibrarySort.RecentlyAdded, false, 0, 500) } returns
            MellowResult.Success(all)
        val viewModel = viewModel()

        val (queue, start) = viewModel.tracksToPlay(index = 250, trackId = "t250", count = 300)!!

        assertEquals(all, queue)
        assertEquals(250, start)
    }

    @Test
    fun `the queue comes in the order and filter the list shows`() = runTest {
        downloadedOnly.value = true
        coEvery { repository.getTracksSlice(any(), any(), any(), any(), any()) } returns
            MellowResult.Success(tracks(0 until 1))
        val viewModel = viewModel(sort = LibrarySort.NameDescending)

        viewModel.tracksToPlay(index = 0, trackId = "t0", count = 1)

        coVerify { repository.getTracksSlice(SERVER, LibrarySort.NameDescending, true, 0, 500) }
    }

    @Test
    fun `a track that moved since the list was shown plays on its own`() = runTest {
        val moved = track(7)
        coEvery { repository.getTracksSlice(any(), any(), any(), any(), any()) } returns
            MellowResult.Success(tracks(0 until 5))
        coEvery { repository.getTrack("t7") } returns MellowResult.Success(moved)
        val viewModel = viewModel()

        assertEquals(listOf(moved) to 0, viewModel.tracksToPlay(index = 3, trackId = "t7", count = 5))
    }

    @Test
    fun `nothing plays before the library is loaded`() = runTest {
        val viewModel = LibraryViewModel(repository, displayPreferences)

        assertNull(viewModel.tracksToPlay(index = 0, trackId = "t0", count = 1))
    }

    @Test
    fun `genres come from the library`() = runTest {
        val viewModel = viewModel()

        assertEquals(listOf("Jazz", "Rock"), viewModel.uiState.value.genres)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    private fun viewModel(sort: LibrarySort = LibrarySort.RecentlyAdded) =
        LibraryViewModel(repository, displayPreferences).apply { loadLibrary(SERVER, sort) }

    private companion object {
        const val SERVER = "server"

        fun tracks(ids: IntRange) = ids.map(::track)

        fun track(id: Int) = Track(
            id = "t$id", name = "Track $id", albumId = null, albumName = null, artistId = null, artistName = null,
            trackNumber = null, discNumber = null, duration = Duration.ZERO, genres = emptyList(), imageId = null,
            isFavorite = false, playCount = 0, lastPlayedAt = 0, normalizationGain = null,
        )
    }
}
