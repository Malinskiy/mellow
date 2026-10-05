package dev.mellow.core.database.dao

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/** [LibraryPagingQueriesChecks] on the device's own SQLite (see QueryPlanDeviceTest for when and how to run it). */
@RunWith(AndroidJUnit4::class)
class LibraryPagingQueriesDeviceTest {

    private val checks = LibraryPagingQueriesChecks()

    @Before
    fun setUp() = checks.setUp(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = checks.tearDown()

    @Test
    fun theTracksTabPagesThroughEveryTrackNewestFirstPastTheOld500Cap() =
        runTest { checks.theTracksTabPagesThroughEveryTrackNewestFirstPastTheOld500Cap() }

    @Test
    fun tracksAddedAtTheSameTimeAreOrderedByIdSoPagesNeverOverlapOrSkip() =
        runTest { checks.tracksAddedAtTheSameTimeAreOrderedByIdSoPagesNeverOverlapOrSkip() }

    @Test
    fun nameOrdersIgnoreCaseAndZToAIsTheExactReverseOfAToZ() =
        runTest { checks.nameOrdersIgnoreCaseAndZToAIsTheExactReverseOfAToZ() }

    @Test
    fun theYearOrderSortsTracksByAlbumNameZToATracksWithoutAnAlbumLast() =
        runTest { checks.theYearOrderSortsTracksByAlbumNameZToATracksWithoutAnAlbumLast() }

    @Test
    fun downloadedOnlyKeepsTracksWhoseDownloadCompleted() =
        runTest { checks.downloadedOnlyKeepsTracksWhoseDownloadCompleted() }

    @Test
    fun aKeyedWindowIsTheSamePartOfTheListAPageShows() =
        runTest { checks.aKeyedWindowIsTheSamePartOfTheListAPageShows() }

    @Test
    fun otherServersTracksNeverShow() = runTest { checks.otherServersTracksNeverShow() }

    @Test
    fun favoriteTracksKeepTheOrderTheyWereSavedInAndPositionsAndCountsMatchIt() =
        runTest { checks.favoriteTracksKeepTheOrderTheyWereSavedInAndPositionsAndCountsMatchIt() }

    @Test
    fun aFavoritesShuffleIsABoundedRandomPickOfTheFavoritesOfDownloadsOnlyIfAsked() =
        runTest { checks.aFavoritesShuffleIsABoundedRandomPickOfTheFavoritesOfDownloadsOnlyIfAsked() }

    @Test
    fun aLibraryShuffleIsABoundedRandomPickOfTheServersTracksOfDownloadsOnlyIfAsked() =
        runTest { checks.aLibraryShuffleIsABoundedRandomPickOfTheServersTracksOfDownloadsOnlyIfAsked() }

    @Test
    fun downloadedOnlyKeepsFavoritesWhoseDownloadCompleted() =
        runTest { checks.downloadedOnlyKeepsFavoritesWhoseDownloadCompleted() }

    @Test
    fun aTracksPositionAmongTheServersTracksMatchesTheSlicedOrder() =
        runTest { checks.aTracksPositionAmongTheServersTracksMatchesTheSlicedOrder() }

    @Test
    fun offlinePositionsCountDownloadsOnlyAsTheDownloadedListsShowThem() =
        runTest { checks.offlinePositionsCountDownloadsOnlyAsTheDownloadedListsShowThem() }

    @Test
    fun theGenreFilterMatchesWholeGenresOnly() = runTest { checks.theGenreFilterMatchesWholeGenresOnly() }

    @Test
    fun albumOrders() = runTest { checks.albumOrders() }

    @Test
    fun downloadedOnlyKeepsAlbumsWithADownloadedTrack() =
        runTest { checks.downloadedOnlyKeepsAlbumsWithADownloadedTrack() }

    @Test
    fun genresComeFromDistinctGenreListsWithAlbumCountsForTheTopGenres() =
        runTest { checks.genresComeFromDistinctGenreListsWithAlbumCountsForTheTopGenres() }

    @Test
    fun theArtistsTabMergesAliasesAndCountsTheAlbumsCreditedToEachArtist() =
        runTest { checks.theArtistsTabMergesAliasesAndCountsTheAlbumsCreditedToEachArtist() }

    @Test
    fun playlistTracksPageInPlaylistOrderAndPositionsAndCountsMatchIt() =
        runTest { checks.playlistTracksPageInPlaylistOrderAndPositionsAndCountsMatchIt() }

    @Test
    fun aPlaylistShuffleIsABoundedRandomPickOfThatPlaylistsTracksOnly() =
        runTest { checks.aPlaylistShuffleIsABoundedRandomPickOfThatPlaylistsTracksOnly() }

    @Test
    fun serverAlbumsAndCanonicalArtistsComeASliceAtATime() =
        runTest { checks.serverAlbumsAndCanonicalArtistsComeASliceAtATime() }

    @Test
    fun homeRowsReadNoMoreRowsThanTheyShow() = runTest { checks.homeRowsReadNoMoreRowsThanTheyShow() }
}
