package dev.mellow.core.database.perf

import androidx.paging.PagingSource
import androidx.room.Room
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.dao.AlbumKeysetQueryFactory
import dev.mellow.core.database.dao.ArtistKeysetQueryFactory
import dev.mellow.core.database.dao.LibraryOrder
import dev.mellow.core.database.dao.TrackKeysetQueryFactory
import dev.mellow.core.database.dao.getTracksById
import dev.mellow.core.database.dao.pickRandomTracks
import dev.mellow.core.database.paging.KeysetPagingKey
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/**
 * Times the app's hot database queries on a library of a million tracks and writes the results as JSON, for CI to
 * compare a pull request with its base. Skipped unless asked for:
 *
 *     ./gradlew :core:database:testDebugUnitTest --tests '*QueryBenchmark*' -PperfOutput=/abs/path/results.json
 *
 * Every result has a stable name (the screen and what it loads), so a change that rewrites a query is compared with
 * the query it replaces. Times are host times under Robolectric's SQLite: they show changes, not phone milliseconds.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueryBenchmark {

    @get:Rule
    val folder = TemporaryFolder()

    private val results = linkedMapOf<String, JSONObject>()

    @Test
    fun benchmark() = runBlocking {
        val output = System.getProperty(OUTPUT_PROPERTY)
        assumeTrue("Set -PperfOutput=<file> to run the benchmark", !output.isNullOrBlank())

        val file = File(folder.root, "library.db")
        val db = Room.databaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java, file.path)
            .build()
        val sql = db.openHelper.writableDatabase
        val started = System.nanoTime()
        val library = FakeLibrary().fill(sql, TRACKS)
        val fillMs = (System.nanoTime() - started) / 1e6
        sql.query("PRAGMA wal_checkpoint(TRUNCATE)").use { it.moveToFirst() }
        val sqliteVersion = sql.query("SELECT sqlite_version()").use { it.moveToFirst(); it.getString(0) }
        val pageSize = sql.query("PRAGMA page_size").use { it.moveToFirst(); it.getLong(0) }
        val pages = sql.query("PRAGMA page_count").use { it.moveToFirst(); it.getLong(0) }

        val tracks = db.trackDao()
        val albums = db.albumDao()
        val artists = db.artistDao()
        val playlists = db.playlistDao()
        val server = FakeLibrary.SERVER
        val trackCount = tracks.countTracks(server, downloadedOnly = false)
        val middle = trackCount / 2
        val albumQueries = AlbumKeysetQueryFactory(db)
        val artistQueries = ArtistKeysetQueryFactory(db)
        val trackQueries = TrackKeysetQueryFactory(db)

        // The Library's Tracks tab, in each order: opening it, reopening it scrolled halfway, scrolling on from there.
        for ((name, order) in TRACK_ORDERS) {
            measure("tracks.$name.open") {
                trackQueries.libraryPagingSource(server, order, false).refreshKeyset(null)
            }
            measure("tracks.$name.reopen@50%") {
                trackQueries.libraryPagingSource(server, order, false)
                    .refreshKeyset(KeysetPagingKey.Position(middle))
            }
            val scrollSource = trackQueries.libraryPagingSource(server, order, false)
            val middlePage = scrollSource.refreshKeyset(KeysetPagingKey.Position(middle))
            val nextKey = requireNotNull(middlePage.nextKey)
            measure("tracks.$name.scroll@50%") { scrollSource.appendKeyset(nextKey) }
        }
        measure("tracks.downloaded.open") {
            trackQueries.libraryPagingSource(server, LibraryOrder.RECENTLY_ADDED, true).refreshKeyset(null)
        }
        measure("tracks.count") { tracks.countTracks(server, false) }
        val queueAnchor = requireNotNull(
            trackQueries.libraryTrackIdAtPosition(server, LibraryOrder.RECENTLY_ADDED, false, middle),
        )
        measure("tracks.queue@50%") {
            trackQueries.libraryQueueWindow(
                server,
                LibraryOrder.RECENTLY_ADDED,
                downloadedOnly = false,
                trackId = queueAnchor,
                before = 100,
                size = QUEUE,
            )
        }
        measure("tracks.shuffle") { tracks.pickRandomTracks(server, false, QUEUE) }
        measure("tracks.shuffle.downloaded") { tracks.pickRandomTracks(server, true, QUEUE) }

        val albumCount = library.albums
        for ((name, order) in ALBUM_ORDERS) {
            measure("albums.$name.open") {
                albumQueries.libraryPagingSource(server, order, null, false).refreshKeyset(null)
            }
            val source = albumQueries.libraryPagingSource(server, order, null, false)
            val middlePage = source.refreshKeyset(KeysetPagingKey.Position(albumCount / 2))
            val nextKey = requireNotNull(middlePage.nextKey)
            measure("albums.$name.scroll@50%") { source.appendKeyset(nextKey) }
        }
        measure("albums.genre.open") {
            albumQueries.libraryPagingSource(server, LibraryOrder.RECENTLY_ADDED, "Jazz", false).refreshKeyset(null)
        }
        measure("artists.open") {
            artistQueries.libraryPagingSource(server, LibraryOrder.NAME_ASC, false).refreshKeyset(null)
        }
        val artistSource = artistQueries.libraryPagingSource(server, LibraryOrder.NAME_ASC, false)
        val artistMiddlePage = artistSource.refreshKeyset(KeysetPagingKey.Position(library.artists / 2))
        val artistNextKey = requireNotNull(artistMiddlePage.nextKey)
        measure("artists.scroll@50%") {
            artistSource.appendKeyset(artistNextKey)
        }

        measure("album.tracks") { tracks.getTracksByAlbumSync(library.sampleAlbumId) }
        measure("artist.topTracks") { tracks.getTracksByResolvedArtistSync(library.sampleArtistId) }
        measure("artist.trackCount") { tracks.countTracksByResolvedArtist(library.sampleArtistId) }
        measure("artist.albums") { albums.getAllAlbumsByResolvedArtist(library.sampleArtistId) }

        measure("home.recentlyPlayed") { tracks.getRecentlyPlayedTracks(server) }
        measure("home.mostPlayed") { tracks.getMostPlayed(server).first() }
        measure("home.recentlyAdded") { albums.getRecentlyAddedAlbums(server) }
        measure("home.quickPicks") { albums.observeRandomAlbums(server, 20).first() }
        measure("home.recentlyPlayedAlbums") { albums.getRecentlyPlayedAlbums(server).first() }
        measure("home.mostPlayedAlbums") { albums.getMostPlayedAlbums(server).first() }
        measure("home.favoriteTracks") { tracks.observeRandomFavoriteTracks(server, 20).first() }

        measure("favorites.tracks.open") { tracks.getFavoriteTracksPaged(server, false).refresh(0) }
        measure("favorites.tracks.count") { tracks.countFavoriteTracks(server, false) }
        measure("favorites.tracks.shuffle") { tracks.getRandomFavoriteTracks(server, false, QUEUE) }
        measure("favorites.albums.open") { albums.getFavoriteAlbumsPaged(server, false).refresh(0) }

        measure("playlist.open") { playlists.getPlaylistTracksPaged(library.samplePlaylistId, false).refresh(0) }
        measure("search.tracks") { tracks.search(server, "river") }
        measure("search.albums") { albums.search(server, "river") }

        val autoId = requireNotNull(trackQueries.autoTrackIdAtPosition(server, false, middle))
        measure("auto.songs.window@50%") {
            trackQueries.autoQueueWindow(server, false, autoId, before = 100, size = QUEUE)
        }
        measure("auto.songs.position@50%") { trackQueries.findAutoTrack(server, false, autoId) }

        // Sync: saving tracks the library already has (a re-sync), then tracks it doesn't.
        val existing = tracks.getTracksById(tracks.getRandomTrackIds(server, false, WRITE_BATCH)).values.toList()
        measure("sync.resave10k", runs = 3) {
            tracks.upsertTracks(existing.map { it.copy(lastSynced = it.lastSynced + 1) })
        }
        var batch = 0
        measure("sync.add10k", runs = 3) {
            batch++
            tracks.upsertTracks(existing.map { it.copy(id = "new-$batch-${it.id}") })
        }

        val json = JSONObject()
            .put("tracks", library.tracks)
            .put("albums", library.albums)
            .put("artists", library.artists)
            .put("sqlite", sqliteVersion)
            .put("fillMs", fillMs.toLong())
            .put("databaseMb", pages * pageSize / 1_048_576.0)
            .put("results", JSONObject(results as Map<*, *>))
        File(output!!).apply { parentFile?.mkdirs() }.writeText(json.toString(2))
        db.close()
    }

    /**
     * Times [block] and records the median and the median absolute deviation. The first run warms up and is dropped,
     * except for blocks of a second or more, which are timed once from that run: their noise is small next to their
     * time, and the benchmark has to finish twice (base and pull request) in one CI job. Faster blocks run 3 or 9
     * more times, or [runs] times.
     */
    private suspend fun measure(name: String, runs: Int? = null, block: suspend () -> Unit) {
        val first = System.nanoTime().also { block() }.let { (System.nanoTime() - it) / 1e6 }
        val count = runs ?: when {
            first >= 1_000 -> 0
            first >= 100 -> 3
            else -> 9
        }
        val times = (if (count == 0) listOf(first) else (1..count).map {
            val start = System.nanoTime()
            block()
            (System.nanoTime() - start) / 1e6
        }).sorted()
        val median = times[times.size / 2]
        val deviation = times.map { kotlin.math.abs(it - median) }.sorted()[times.size / 2]
        results[name] = JSONObject().put("medianMs", median).put("madMs", deviation).put("runs", times.size)
        println("perf " + name + ": " + "%.2f ms (±%.2f)".format(median, deviation))
    }

    private suspend fun <V : Any> PagingSource<Int, V>.refresh(position: Int) {
        val result = load(PagingSource.LoadParams.Refresh(position, PAGE * 3, placeholdersEnabled = true))
        check(result is PagingSource.LoadResult.Page) { "Refresh at $position failed: $result" }
    }

    private suspend fun <V : Any> PagingSource<KeysetPagingKey, V>.refreshKeyset(key: KeysetPagingKey?):
        PagingSource.LoadResult.Page<KeysetPagingKey, V> {
        val result = load(PagingSource.LoadParams.Refresh(key, PAGE * 3, placeholdersEnabled = true))
        check(result is PagingSource.LoadResult.Page) { "Refresh at $key failed: $result" }
        return result
    }

    private suspend fun <V : Any> PagingSource<KeysetPagingKey, V>.appendKeyset(key: KeysetPagingKey) {
        val result = load(PagingSource.LoadParams.Append(key, PAGE, placeholdersEnabled = true))
        check(result is PagingSource.LoadResult.Page) { "Append at $key failed: $result" }
    }

    private companion object {
        const val OUTPUT_PROPERTY = "mellow.perf.output"
        const val TRACKS = 1_000_000
        const val PAGE = 60
        const val QUEUE = 500
        const val WRITE_BATCH = 10_000

        val TRACK_ORDERS = listOf(
            "recent" to LibraryOrder.RECENTLY_ADDED,
            "nameAsc" to LibraryOrder.NAME_ASC,
            "nameDesc" to LibraryOrder.NAME_DESC,
            "album" to LibraryOrder.YEAR,
        )
        val ALBUM_ORDERS = listOf(
            "recent" to LibraryOrder.RECENTLY_ADDED,
            "nameAsc" to LibraryOrder.NAME_ASC,
            "nameDesc" to LibraryOrder.NAME_DESC,
            "year" to LibraryOrder.YEAR,
        )
    }
}
