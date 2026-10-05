package dev.mellow.core.database.dao

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [TrackKeysetPagingChecks] on the device's own SQLite (see QueryPlanDeviceTest for when and how to run it). */
@RunWith(AndroidJUnit4::class)
class TrackKeysetPagingDeviceTest {

    private val checks = TrackKeysetPagingChecks()

    @Before
    fun setUp() = checks.setUp(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = checks.tearDown()

    @Test
    fun everyOrderAndDownloadFilterPagesForwardWithoutSkipsOrDuplicates() =
        runTest { checks.everyOrderAndDownloadFilterPagesForwardWithoutSkipsOrDuplicates() }

    @Test
    fun prependingFromAMiddleRefreshReachesTheSameTopInEveryOrderAndFilter() =
        runTest { checks.prependingFromAMiddleRefreshReachesTheSameTopInEveryOrderAndFilter() }

    @Test
    fun positionRefreshReportsExactPlaceholdersAroundTheRequestedRow() =
        runTest { checks.positionRefreshReportsExactPlaceholdersAroundTheRequestedRow() }

    @Test
    fun rowRefreshKeyKeepsTheAnchorAfterInsertsAndDeletesAboveIt() =
        runTest { checks.rowRefreshKeyKeepsTheAnchorAfterInsertsAndDeletesAboveIt() }

    @Test
    fun trackAndDownloadWritesInvalidateTheirPagingSources() =
        runTest { checks.trackAndDownloadWritesInvalidateTheirPagingSources() }

    @Test
    fun libraryQueueWindowsKeepContextAndShiftBackAtTheEnd() =
        runTest { checks.libraryQueueWindowsKeepContextAndShiftBackAtTheEnd() }

    @Test
    fun androidAutoSongWindowUsesItsSortNameKey() = runTest { checks.androidAutoSongWindowUsesItsSortNameKey() }
}
