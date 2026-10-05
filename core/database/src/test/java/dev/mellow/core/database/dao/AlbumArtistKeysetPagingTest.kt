package dev.mellow.core.database.dao

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * [AlbumArtistKeysetPagingChecks] under Robolectric. AlbumArtistKeysetPagingDeviceTest runs them on a device's own
 * SQLite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumArtistKeysetPagingTest {

    private val checks = AlbumArtistKeysetPagingChecks()

    @Before
    fun setUp() = checks.setUp(RuntimeEnvironment.getApplication())

    @After
    fun tearDown() = checks.tearDown()

    @Test
    fun `albums page without skips for every order download and genre filter`() =
        runTest { checks.albumsPageWithoutSkipsForEveryOrderDownloadAndGenreFilter() }

    @Test
    fun `album middle refresh prepends and appends to the same filtered list`() =
        runTest { checks.albumMiddleRefreshPrependsAndAppendsToTheSameFilteredList() }

    @Test
    fun `album name descending is the exact reverse and null years sort last`() =
        runTest { checks.albumNameDescendingIsTheExactReverseAndNullYearsSortLast() }

    @Test
    fun `album placeholders report the full filtered count`() =
        runTest { checks.albumPlaceholdersReportTheFullFilteredCount() }

    @Test
    fun `artist aliases merge and indexed counts equal the old coalesce query`() =
        runTest { checks.artistAliasesMergeAndIndexedCountsEqualTheOldCoalesceQuery() }

    @Test
    fun `artists page every name and sort-name order in both download modes`() =
        runTest { checks.artistsPageEveryNameAndSortNameOrderInBothDownloadModes() }

    @Test
    fun `album and artist dependencies invalidate their paging sources`() =
        runTest { checks.albumAndArtistDependenciesInvalidateTheirPagingSources() }
}
