package dev.mellow.core.database.dao

import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** [TrackKeysetPagingChecks] under Robolectric. TrackKeysetPagingDeviceTest runs them on a device's own SQLite. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TrackKeysetPagingTest {

    private val checks = TrackKeysetPagingChecks()

    @Before
    fun setUp() = checks.setUp(RuntimeEnvironment.getApplication())

    @After
    fun tearDown() = checks.tearDown()

    @Test
    fun `every order and download filter pages forward without skips or duplicates`() =
        runTest { checks.everyOrderAndDownloadFilterPagesForwardWithoutSkipsOrDuplicates() }

    @Test
    fun `prepending from a middle refresh reaches the same top in every order and filter`() =
        runTest { checks.prependingFromAMiddleRefreshReachesTheSameTopInEveryOrderAndFilter() }

    @Test
    fun `position refresh reports exact placeholders around the requested row`() =
        runTest { checks.positionRefreshReportsExactPlaceholdersAroundTheRequestedRow() }

    @Test
    fun `row refresh key keeps the anchor after inserts and deletes above it`() =
        runTest { checks.rowRefreshKeyKeepsTheAnchorAfterInsertsAndDeletesAboveIt() }

    @Test
    fun `track and download writes invalidate their paging sources`() =
        runTest { checks.trackAndDownloadWritesInvalidateTheirPagingSources() }

    @Test
    fun `library queue windows keep context and shift back at the end`() =
        runTest { checks.libraryQueueWindowsKeepContextAndShiftBackAtTheEnd() }

    @Test
    fun `Android Auto song window uses its sort-name key`() =
        runTest { checks.androidAutoSongWindowUsesItsSortNameKey() }
}
