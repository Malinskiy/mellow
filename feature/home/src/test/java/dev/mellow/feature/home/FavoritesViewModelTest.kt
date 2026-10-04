package dev.mellow.feature.home

import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.preferences.DisplayPreferences
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.time.Duration

/** Playing the favorites queues at most 500 tracks, however many favorites there are. */
@OptIn(ExperimentalCoroutinesApi::class)
class FavoritesViewModelTest {

    private val repository = mockk<LibraryRepository>()
    private val downloadedOnly = MutableStateFlow(false)
    private val displayPreferences = mockk<DisplayPreferences> {
        every { downloadedOnly } returns this@FavoritesViewModelTest.downloadedOnly
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        coEvery { repository.syncFavorites(any()) } returns MellowResult.Success(Unit)
    }

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `playing a favorite of many queues the 500 around it`() = runTest {
        val window = tracks(900 until 1_400)
        coEvery { repository.countFavoriteTracks(SERVER, false) } returns MellowResult.Success(2_000)
        coEvery { repository.getFavoriteTracksSlice(SERVER, false, 900, 500) } returns MellowResult.Success(window)

        val (queue, start) = viewModel().tracksToPlay(index = 1_000, trackId = "t1000")!!

        assertEquals(window, queue)
        assertEquals("t1000", queue[start].id)
    }

    @Test
    fun `playing a favorite of a few queues them all`() = runTest {
        val all = tracks(0 until 120)
        coEvery { repository.countFavoriteTracks(SERVER, false) } returns MellowResult.Success(120)
        coEvery { repository.getFavoriteTracksSlice(SERVER, false, 0, 500) } returns MellowResult.Success(all)

        assertEquals(all to 100, viewModel().tracksToPlay(index = 100, trackId = "t100"))
    }

    @Test
    fun `shuffling picks at most 500 favorites at random, in the order picked`() = runTest {
        val picked = tracks(0 until 500).shuffled()
        coEvery { repository.pickRandomFavoriteTracks(SERVER, false, 500) } returns MellowResult.Success(picked)

        assertEquals(picked, viewModel().shuffledTracks())
    }

    @Test
    fun `the queue and the shuffle only use downloads when the list shows only downloads`() = runTest {
        downloadedOnly.value = true
        coEvery { repository.countFavoriteTracks(SERVER, true) } returns MellowResult.Success(1)
        coEvery { repository.getFavoriteTracksSlice(SERVER, true, 0, 500) } returns MellowResult.Success(tracks(0 until 1))
        coEvery { repository.pickRandomFavoriteTracks(SERVER, true, 500) } returns MellowResult.Success(tracks(0 until 1))
        val viewModel = viewModel()

        viewModel.tracksToPlay(index = 0, trackId = "t0")
        viewModel.shuffledTracks()

        coVerify { repository.getFavoriteTracksSlice(SERVER, true, 0, 500) }
        coVerify { repository.pickRandomFavoriteTracks(SERVER, true, 500) }
    }

    @Test
    fun `nothing plays before the favorites are loaded`() = runTest {
        val viewModel = FavoritesViewModel(repository, displayPreferences)

        assertNull(viewModel.tracksToPlay(index = 0, trackId = "t0"))
        assertTrue(viewModel.shuffledTracks().isEmpty())
    }

    private fun viewModel() = FavoritesViewModel(repository, displayPreferences).apply { loadFavorites(SERVER) }

    private companion object {
        const val SERVER = "server"

        fun tracks(ids: IntRange) = ids.map(::track)

        fun track(id: Int) = Track(
            id = "t$id", name = "Track $id", albumId = null, albumName = null, artistId = null, artistName = null,
            trackNumber = null, discNumber = null, duration = Duration.ZERO, genres = emptyList(), imageId = null,
            isFavorite = true, playCount = 0, lastPlayedAt = 0, normalizationGain = null,
        )
    }
}
