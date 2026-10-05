package dev.mellow.core.database.dao

import android.database.sqlite.SQLiteDatabase
import android.util.Log
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/**
 * [QueryPlanChecks] on the device's own SQLite; every plan checked goes to logcat (tag QueryPlanDeviceTest). Android
 * 8's SQLite (3.18) is the oldest the app runs on: before a release that changes a query or an index, run the device
 * tests on an Android 8 (API 26) emulator:
 *
 *     ANDROID_SERIAL=<emulator serial> ./gradlew :core:database:connectedDebugAndroidTest
 */
@RunWith(AndroidJUnit4::class)
class QueryPlanDeviceTest {

    private val checks = QueryPlanChecks(report = { Log.i(TAG, it) })

    @Before
    fun setUp() = checks.setUp(ApplicationProvider.getApplicationContext())

    @After
    fun tearDown() = checks.tearDown()

    @Test
    fun eachOrderOfTheTracksTabReadsItsIndexWithoutSorting() =
        runTest { checks.eachOrderOfTheTracksTabReadsItsIndexWithoutSorting() }

    @Test
    fun downloadedOnlyStartsFromTheDownloads() = runTest { checks.downloadedOnlyStartsFromTheDownloads() }

    @Test
    fun eachOrderOfTheAlbumsTabReadsItsIndexWithoutSorting() =
        runTest { checks.eachOrderOfTheAlbumsTabReadsItsIndexWithoutSorting() }

    @Test
    fun genreAlbumsKeepIndexOrderAndDownloadedAlbumsStartFromDownloads() =
        runTest { checks.genreAlbumsKeepIndexOrderAndDownloadedAlbumsStartFromDownloads() }

    @Test
    fun eachOrderOfTheArtistsTabReadsItsIndexWithoutGroupingOrSorting() =
        runTest { checks.eachOrderOfTheArtistsTabReadsItsIndexWithoutGroupingOrSorting() }

    @Test
    fun androidAutoAlbumAndArtistSlicesUseTheirSortIndexes() =
        runTest { checks.androidAutoAlbumAndArtistSlicesUseTheirSortIndexes() }

    @Test
    fun albumAndArtistScreensReadTheirTracksByIndex() =
        runTest { checks.albumAndArtistScreensReadTheirTracksByIndex() }

    @Test
    fun homeAndFavoritesReadTheirTracksByIndex() = runTest { checks.homeAndFavoritesReadTheirTracksByIndex() }

    @Test
    fun aPlaylistReadsItsTracksInOrderWithoutSorting() =
        runTest { checks.aPlaylistReadsItsTracksInOrderWithoutSorting() }

    @Test
    fun androidAutosSongsReadTheirIndex() = runTest { checks.androidAutosSongsReadTheirIndex() }

    @Test
    fun theWholeLibraryShuffleReadsOnlyTheRowsItPicks() =
        runTest { checks.theWholeLibraryShuffleReadsOnlyTheRowsItPicks() }

    companion object {
        private const val TAG = "QueryPlanDeviceTest"

        @BeforeClass
        @JvmStatic
        fun logSqliteVersion() {
            val version = SQLiteDatabase.create(null).use { db ->
                db.compileStatement("SELECT sqlite_version()").use { it.simpleQueryForString() }
            }
            Log.i(TAG, "SQLite $version")
        }
    }
}
