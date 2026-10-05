package dev.mellow.core.database.dao

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [AlbumArtistKeysetPagingChecks] on the device's own SQLite (see QueryPlanDeviceTest for when and how to run it). */
@RunWith(AndroidJUnit4::class)
class AlbumArtistKeysetPagingDeviceTest {

    private val checks = AlbumArtistKeysetPagingChecks()

    @Before
    fun setUp() = checks.setUp(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = checks.tearDown()

    @Test
    fun albumsPageWithoutSkipsForEveryOrderDownloadAndGenreFilter() =
        runTest { checks.albumsPageWithoutSkipsForEveryOrderDownloadAndGenreFilter() }

    @Test
    fun albumMiddleRefreshPrependsAndAppendsToTheSameFilteredList() =
        runTest { checks.albumMiddleRefreshPrependsAndAppendsToTheSameFilteredList() }

    @Test
    fun albumNameDescendingIsTheExactReverseAndNullYearsSortLast() =
        runTest { checks.albumNameDescendingIsTheExactReverseAndNullYearsSortLast() }

    @Test
    fun albumPlaceholdersReportTheFullFilteredCount() =
        runTest { checks.albumPlaceholdersReportTheFullFilteredCount() }

    @Test
    fun artistAliasesMergeAndIndexedCountsEqualTheOldCoalesceQuery() =
        runTest { checks.artistAliasesMergeAndIndexedCountsEqualTheOldCoalesceQuery() }

    @Test
    fun artistsPageEveryNameAndSortNameOrderInBothDownloadModes() =
        runTest { checks.artistsPageEveryNameAndSortNameOrderInBothDownloadModes() }

    @Test
    fun albumAndArtistDependenciesInvalidateTheirPagingSources() =
        runTest { checks.albumAndArtistDependenciesInvalidateTheirPagingSources() }
}
