package dev.mellow.core.database.dao

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [QueryPlanChecks] under Robolectric's SQLite (3.32). QueryPlanDeviceTest runs them on a device's own SQLite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueryPlanTest {

    private val checks = QueryPlanChecks()

    @Before
    fun setUp() = checks.setUp(RuntimeEnvironment.getApplication())

    @After
    fun tearDown() = checks.tearDown()

    @Test
    fun `each order of the Tracks tab reads its index without sorting`() =
        runTest { checks.eachOrderOfTheTracksTabReadsItsIndexWithoutSorting() }

    @Test
    fun `downloaded only starts from the downloads`() = runTest { checks.downloadedOnlyStartsFromTheDownloads() }

    @Test
    fun `each order of the Albums tab reads its index without sorting`() =
        runTest { checks.eachOrderOfTheAlbumsTabReadsItsIndexWithoutSorting() }

    @Test
    fun `genre albums keep index order and downloaded albums start from downloads`() =
        runTest { checks.genreAlbumsKeepIndexOrderAndDownloadedAlbumsStartFromDownloads() }

    @Test
    fun `each order of the Artists tab reads its index without grouping or sorting`() =
        runTest { checks.eachOrderOfTheArtistsTabReadsItsIndexWithoutGroupingOrSorting() }

    @Test
    fun `Android Auto album and artist slices use their sort indexes`() =
        runTest { checks.androidAutoAlbumAndArtistSlicesUseTheirSortIndexes() }

    @Test
    fun `album and artist screens read their tracks by index`() =
        runTest { checks.albumAndArtistScreensReadTheirTracksByIndex() }

    @Test
    fun `home and favorites read their tracks by index`() =
        runTest { checks.homeAndFavoritesReadTheirTracksByIndex() }

    @Test
    fun `a playlist reads its tracks in order without sorting`() =
        runTest { checks.aPlaylistReadsItsTracksInOrderWithoutSorting() }

    @Test
    fun `Android Auto's songs read their index`() = runTest { checks.androidAutosSongsReadTheirIndex() }

    @Test
    fun `the whole-library shuffle reads only the rows it picks`() =
        runTest { checks.theWholeLibraryShuffleReadsOnlyTheRowsItPicks() }
}
