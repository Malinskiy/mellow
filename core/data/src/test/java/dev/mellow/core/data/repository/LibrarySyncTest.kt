package dev.mellow.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.mapper.toTrackEntity
import dev.mellow.core.data.preferences.PlaybackQueuePreferences
import dev.mellow.core.data.preferences.SavedQueue
import dev.mellow.core.data.preferences.SyncPreferences
import dev.mellow.core.database.DatabaseTransactionRunner
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.RoomTransactionRunner
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.database.entity.SyncPassKind
import dev.mellow.core.network.datasource.JellyfinDataSource
import dev.mellow.core.network.datasource.PagedItems
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.NameGuidPair
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.io.IOException
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/** Library sync against a mocked Jellyfin server and a real (in-memory) database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibrarySyncTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: MellowDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var preferences: SyncPreferences
    private val dataSource = mockk<JellyfinDataSource>()
    private val queue = mockk<PlaybackQueuePreferences>()

    // What the server holds.
    private val a1 = artist(1)
    private val a2 = artist(2)
    private val a3 = artist(3)
    private val al1 = album(1, a1)
    private val al2 = album(2, a2)
    private val al3 = album(3, a3)
    private val t1 = track(1, al1, a1)
    private val t2 = track(2, al2, a2)
    private val t3 = track(3, al3, a3)
    private val t4 = track(4, album = null, artist = null)
    private var artists = listOf(a1, a2, a3)
    private var albums = listOf(al1, al2, al3)
    private var tracks = listOf(t1, t2, t3, t4)

    /** Items the server has that its library listings don't include, e.g. playlist entries of another kind. */
    private var unlisted = listOf<BaseItemDto>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        preferences = SyncPreferences(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(folder.root, "sync.preferences_pb") },
        )
        runBlocking {
            db.serverDao().upsert(
                ServerEntity(SERVER, "Home", "https://jellyfin.example", USER.toString(), "token", true, 0),
            )
        }
        coEvery { queue.load() } returns null
        coEvery { dataSource.getArtistsPaged(USER, any(), any()) } answers { page(artists, secondArg(), thirdArg()) }
        coEvery { dataSource.getAlbumsPaged(USER, any(), any(), any()) } answers {
            page(albums, secondArg(), thirdArg())
        }
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } answers {
            page(tracks, secondArg(), thirdArg())
        }
        coEvery { dataSource.getAlbumsByIds(USER, any()) } answers { (albums + unlisted).withIds(secondArg()) }
        coEvery { dataSource.getTracksByIds(USER, any()) } answers { (tracks + unlisted).withIds(secondArg()) }
        coEvery { dataSource.getFavoriteAlbums(USER) } returns emptyList()
        coEvery { dataSource.getFavoriteTracks(USER) } returns emptyList()
        coEvery { dataSource.getFavoriteArtists(USER) } returns emptyList()
        coEvery { dataSource.getRecentlyPlayedItems(USER, any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreScope.cancel()
    }

    @Test
    fun `a failed incremental sync records nothing`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val recorded = preferences.readLibrarySyncState()
        val lastSynced = preferences.lastSyncTimestamp.first()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } throws IOException("Connection reset")

        assertTrue(repository.syncLibrary(SERVER) is MellowResult.Error)

        coVerify { dataSource.getTracksPaged(USER, 0, any(), isNull(inverse = true)) }
        assertEquals(recorded, preferences.readLibrarySyncState())
        assertEquals(lastSynced, preferences.lastSyncTimestamp.first())
        assertEquals(1, preferences.syncCount.first())
        assertFalse(preferences.isFullPassPending.first())
    }

    @Test
    fun `changes are fetched from the start of the last successful sync, less the overlap`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val firstStart = preferences.readLibrarySyncState().lastStartedAt
        val windows = mutableListOf<LocalDateTime?>()
        coEvery { dataSource.getAlbumsPaged(USER, any(), any(), captureNullable(windows)) } answers {
            Thread.sleep(SLOW_REQUEST_MS) // changes saved on the server meanwhile must fall into the next window
            page(albums, secondArg(), thirdArg())
        }
        val beforeSecond = System.currentTimeMillis()

        assertSuccess(repository.syncLibrary(SERVER))

        assertEquals(listOf(utc(firstStart - LibraryRepositoryImpl.INCREMENTAL_SYNC_OVERLAP_MS)), windows.distinct())
        val secondStart = preferences.readLibrarySyncState().lastStartedAt
        val secondEnd = preferences.lastSyncTimestamp.first()
        assertTrue("$secondStart isn't when the sync started", secondStart in beforeSecond..secondEnd - SLOW_REQUEST_MS)
    }

    @Test
    fun `a failed first full pass is a full pass again next time`() = runTest {
        val repository = repository()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } throws IOException("timeout")

        assertTrue(repository.syncLibrary(SERVER) is MellowResult.Error)
        assertTrue(preferences.isFullPassPending.first())
        assertEquals(0L, preferences.lastSyncTimestamp.first())

        val windows = mutableListOf<LocalDateTime?>()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), captureNullable(windows)) } answers {
            page(tracks, secondArg(), thirdArg())
        }
        assertSuccess(repository.syncLibrary(SERVER))

        assertTrue(windows.isNotEmpty())
        assertTrue("a full pass lists every track", windows.all { it == null })
        assertFalse(preferences.isFullPassPending.first())
        assertNotNull(db.trackDao().getTrackById(t4.key))
    }

    @Test
    fun `an interrupted rebuild is retried as a full pass`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        preferences.requestFullPass()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } throws IOException("timeout")
        assertTrue(repository.syncLibrary(SERVER) is MellowResult.Error)
        assertTrue(preferences.isFullPassPending.first())

        tracks = listOf(t1, t3, t4)
        val windows = mutableListOf<LocalDateTime?>()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), captureNullable(windows)) } answers {
            page(tracks, secondArg(), thirdArg())
        }
        assertSuccess(repository.syncLibrary(SERVER))

        assertTrue("a full pass lists every track", windows.all { it == null })
        assertNull("deleted on the server", db.trackDao().getTrackById(t2.key))
        assertFalse(preferences.isFullPassPending.first())
    }

    @Test
    fun `a full pass removes what the server deleted and keeps what the device still uses`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val audiobook = track(5, album = null, artist = a1, kind = BaseItemKind.AUDIO_BOOK)
        unlisted = listOf(audiobook)
        db.trackDao().upsertTracks(listOf(audiobook.toTrackEntity(SERVER))) // saved from a playlist
        db.downloadDao().upsert(download(t3.key))
        coEvery { queue.load() } returns SavedQueue(SERVER, listOf(t4.key), 0, 0, false, 0)
        artists = listOf(a1)
        albums = listOf(al1)
        tracks = listOf(t1)
        preferences.requestFullPass()

        assertSuccess(repository.syncLibrary(SERVER))

        assertNull(db.trackDao().getTrackById(t2.key))
        assertNull(db.albumDao().getAlbumById(al2.key))
        assertNull(db.artistDao().getArtistById(a2.key))
        assertEquals(emptyList<String>(), db.trackDao().getArtistNamesForTrack(t2.key))
        assertEquals(emptyList<String>(), db.albumDao().getArtistNamesForAlbum(al2.key))
        // Downloaded: kept with its album and artist.
        assertNotNull(db.trackDao().getTrackById(t3.key))
        assertNotNull(db.albumDao().getAlbumById(al3.key))
        assertNotNull(db.artistDao().getArtistById(a3.key))
        assertEquals(listOf(a3.name), db.trackDao().getArtistNamesForTrack(t3.key))
        // In the play queue: kept.
        assertNotNull(db.trackDao().getTrackById(t4.key))
        // Still on the server, outside the listings: kept.
        assertNotNull(db.trackDao().getTrackById(audiobook.key))
        assertNotNull(db.trackDao().getTrackById(t1.key))
        coVerify(exactly = 0) {
            dataSource.getTracksByIds(USER, match { ids -> ids.any { it == t3.id || it == t4.id } })
        }
        assertEquals(0, db.syncPassDao().count(SyncPassKind.TRACK))
        assertFalse(preferences.isFullPassPending.first())
    }

    @Test
    fun `the removal is one transaction`() = runTest {
        assertSuccess(repository().syncLibrary(SERVER))
        tracks = listOf(t1, t3, t4)
        albums = listOf(al1, al3)
        artists = listOf(a1, a3)
        preferences.requestFullPass()
        // The process dies just before the transaction that removed t2 commits.
        val crashing = object : DatabaseTransactionRunner {
            private val room = RoomTransactionRunner(db)
            override suspend fun <T> invoke(block: suspend () -> T): T = room {
                val result = block()
                if (db.trackDao().getTrackById(t2.key) == null) throw IOException("process died")
                result
            }
        }

        val result = repository(crashing).syncLibrary(SERVER)

        assertEquals("process died", (result as MellowResult.Error).exception.message)
        assertNotNull(db.trackDao().getTrackById(t2.key))
        assertNotNull(db.albumDao().getAlbumById(al2.key))
        assertNotNull(db.artistDao().getArtistById(a2.key))
        assertEquals(listOf(a2.name), db.trackDao().getArtistNamesForTrack(t2.key))
        assertEquals(listOf(a2.name), db.albumDao().getArtistNamesForAlbum(al2.key))
        assertTrue(preferences.isFullPassPending.first())
    }

    @Test
    fun `a full pass that saw a different count than the server reports deletes nothing`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val lastSynced = preferences.lastSyncTimestamp.first()
        tracks = listOf(t1, t3, t4)
        // A track is added while the pass pages through the tracks: the count asked for at the end is one more.
        coEvery { dataSource.getTracksPaged(USER, 0, 1, null) } returns PagedItems(tracks.take(1), tracks.size + 1)
        preferences.requestFullPass()

        val result = repository.syncLibrary(SERVER)

        assertTrue((result as MellowResult.Error).exception is LibraryChangedDuringSyncException)
        assertNotNull("not swept", db.trackDao().getTrackById(t2.key))
        coVerify(exactly = 0) { dataSource.getTracksByIds(any(), any()) }
        assertTrue(preferences.isFullPassPending.first())
        assertEquals(lastSynced, preferences.lastSyncTimestamp.first())
    }

    @Test
    fun `artist links survive an interrupted full pass`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        preferences.requestFullPass()

        coEvery { dataSource.getAlbumsPaged(USER, any(), any(), any()) } throws IOException("timeout")
        assertTrue(repository.syncLibrary(SERVER) is MellowResult.Error)
        assertLinksIntact()

        coEvery { dataSource.getAlbumsPaged(USER, any(), any(), any()) } answers {
            page(albums, secondArg(), thirdArg())
        }
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } throws IOException("timeout")
        assertTrue(repository.syncLibrary(SERVER) is MellowResult.Error)
        assertLinksIntact()
    }

    @Test
    fun `a full pass replaces the artist links of what it saved`() = runTest {
        val repository = repository()
        assertSuccess(repository.syncLibrary(SERVER))
        val retagged = al1.copy(albumArtists = listOf(NameGuidPair(a2.name, a2.id)))
        albums = listOf(retagged, al2, al3)
        preferences.requestFullPass()

        assertSuccess(repository.syncLibrary(SERVER))

        assertEquals(listOf(a2.name), db.albumDao().getArtistNamesForAlbum(al1.key))
    }

    private suspend fun assertLinksIntact() {
        assertEquals(listOf(a1.name), db.albumDao().getArtistNamesForAlbum(al1.key))
        assertEquals(listOf(a2.name), db.albumDao().getArtistNamesForAlbum(al2.key))
        assertEquals(listOf(a1.name), db.trackDao().getArtistNamesForTrack(t1.key))
        assertEquals(listOf(a2.name), db.trackDao().getArtistNamesForTrack(t2.key))
    }

    private fun repository(transaction: DatabaseTransactionRunner = RoomTransactionRunner(db)) = LibraryRepositoryImpl(
        albumDao = db.albumDao(),
        artistDao = db.artistDao(),
        artistAliasDao = db.artistAliasDao(),
        trackDao = db.trackDao(),
        serverDao = db.serverDao(),
        searchQueryDao = db.searchQueryDao(),
        syncPassDao = db.syncPassDao(),
        transaction = transaction,
        jellyfinDataSource = dataSource,
        syncPreferences = preferences,
        playbackQueuePreferences = queue,
    )

    private fun assertSuccess(result: MellowResult<Unit>) {
        assertEquals(MellowResult.Success(Unit), result)
    }

    private companion object {
        const val SERVER = "server"
        val USER: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
        const val SLOW_REQUEST_MS = 20L

        val BaseItemDto.key: String get() = id.toString()

        fun page(items: List<BaseItemDto>, startIndex: Int, limit: Int) =
            PagedItems(items.drop(startIndex).take(limit), items.size)

        fun List<BaseItemDto>.withIds(ids: List<UUID>) = filter { it.id in ids }

        fun utc(epochMs: Long): LocalDateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC)

        fun artist(n: Long) = BaseItemDto(id = UUID(1, n), type = BaseItemKind.MUSIC_ARTIST, name = "Artist $n")

        fun album(n: Long, artist: BaseItemDto) = BaseItemDto(
            id = UUID(2, n),
            type = BaseItemKind.MUSIC_ALBUM,
            name = "Album $n",
            albumArtists = listOf(NameGuidPair(artist.name, artist.id)),
        )

        fun track(n: Long, album: BaseItemDto?, artist: BaseItemDto?, kind: BaseItemKind = BaseItemKind.AUDIO) =
            BaseItemDto(
                id = UUID(3, n),
                type = kind,
                name = "Track $n",
                albumId = album?.id,
                album = album?.name,
                artistItems = listOfNotNull(artist?.let { NameGuidPair(it.name, it.id) }),
                artists = listOfNotNull(artist?.name),
            )

        fun download(trackId: String) = DownloadEntity(
            trackId = trackId, albumId = null, serverId = SERVER, status = DownloadEntity.STATUS_COMPLETED,
            progress = 1f, bytesDownloaded = 1, totalBytes = 1, quality = "original", filePath = "/music/$trackId",
            requestedAt = 0, completedAt = 0, errorMessage = null, lastSynced = 0,
        )
    }
}
