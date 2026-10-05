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
 * [LibraryPagingQueriesChecks] under Robolectric: the paged, sliced and sampled queries that let the app show and
 * play large lists a part at a time. LibraryPagingQueriesDeviceTest runs them on a device's own SQLite.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibraryPagingQueriesTest {

    private val checks = LibraryPagingQueriesChecks()

    @Before
    fun setUp() = checks.setUp(RuntimeEnvironment.getApplication())

    @After
    fun tearDown() = checks.tearDown()

    @Test
    fun `the tracks tab pages through every track, newest first, past the old 500 cap`() =
        runTest { checks.theTracksTabPagesThroughEveryTrackNewestFirstPastTheOld500Cap() }

    @Test
    fun `tracks added at the same time are ordered by ID, so pages never overlap or skip`() =
        runTest { checks.tracksAddedAtTheSameTimeAreOrderedByIdSoPagesNeverOverlapOrSkip() }

    @Test
    fun `name orders ignore case, and Z to A is the exact reverse of A to Z`() =
        runTest { checks.nameOrdersIgnoreCaseAndZToAIsTheExactReverseOfAToZ() }

    @Test
    fun `the year order sorts tracks by album name, Z to A, tracks without an album last`() =
        runTest { checks.theYearOrderSortsTracksByAlbumNameZToATracksWithoutAnAlbumLast() }

    @Test
    fun `downloaded only keeps tracks whose download completed`() =
        runTest { checks.downloadedOnlyKeepsTracksWhoseDownloadCompleted() }

    @Test
    fun `a keyed window is the same part of the list a page shows`() =
        runTest { checks.aKeyedWindowIsTheSamePartOfTheListAPageShows() }

    @Test
    fun `other servers' tracks never show`() = runTest { checks.otherServersTracksNeverShow() }

    @Test
    fun `favorite tracks keep the order they were saved in, and positions and counts match it`() =
        runTest { checks.favoriteTracksKeepTheOrderTheyWereSavedInAndPositionsAndCountsMatchIt() }

    @Test
    fun `a favorites shuffle is a bounded random pick of the favorites, of downloads only if asked`() =
        runTest { checks.aFavoritesShuffleIsABoundedRandomPickOfTheFavoritesOfDownloadsOnlyIfAsked() }

    @Test
    fun `a library shuffle is a bounded random pick of the server's tracks, of downloads only if asked`() =
        runTest { checks.aLibraryShuffleIsABoundedRandomPickOfTheServersTracksOfDownloadsOnlyIfAsked() }

    @Test
    fun `downloaded only keeps favorites whose download completed`() =
        runTest { checks.downloadedOnlyKeepsFavoritesWhoseDownloadCompleted() }

    @Test
    fun `a track's position among the server's tracks matches the sliced order`() =
        runTest { checks.aTracksPositionAmongTheServersTracksMatchesTheSlicedOrder() }

    @Test
    fun `offline positions count downloads only, as the downloaded lists show them`() =
        runTest { checks.offlinePositionsCountDownloadsOnlyAsTheDownloadedListsShowThem() }

    @Test
    fun `the genre filter matches whole genres only`() = runTest { checks.theGenreFilterMatchesWholeGenresOnly() }

    @Test
    fun `album orders`() = runTest { checks.albumOrders() }

    @Test
    fun `downloaded only keeps albums with a downloaded track`() =
        runTest { checks.downloadedOnlyKeepsAlbumsWithADownloadedTrack() }

    @Test
    fun `genres come from distinct genre lists, with album counts for the top genres`() =
        runTest { checks.genresComeFromDistinctGenreListsWithAlbumCountsForTheTopGenres() }

    @Test
    fun `the artists tab merges aliases and counts the albums credited to each artist`() =
        runTest { checks.theArtistsTabMergesAliasesAndCountsTheAlbumsCreditedToEachArtist() }

    @Test
    fun `playlist tracks page in playlist order, and positions and counts match it`() =
        runTest { checks.playlistTracksPageInPlaylistOrderAndPositionsAndCountsMatchIt() }

    @Test
    fun `a playlist shuffle is a bounded random pick of that playlist's tracks only`() =
        runTest { checks.aPlaylistShuffleIsABoundedRandomPickOfThatPlaylistsTracksOnly() }

    @Test
    fun `server albums and canonical artists come a slice at a time`() =
        runTest { checks.serverAlbumsAndCanonicalArtistsComeASliceAtATime() }

    @Test
    fun `home rows read no more rows than they show`() = runTest { checks.homeRowsReadNoMoreRowsThanTheyShow() }
}
