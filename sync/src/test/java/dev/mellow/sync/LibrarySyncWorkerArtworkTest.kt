package dev.mellow.sync

import android.content.Context
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.work.ListenableWorker
import androidx.work.WorkerFactory
import androidx.work.WorkerParameters
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.workDataOf
import dev.mellow.core.data.ArtworkPreCacher
import dev.mellow.core.data.preferences.PlaybackQueuePreferences
import dev.mellow.core.data.preferences.SyncPreferences
import dev.mellow.core.data.repository.LibraryRepositoryImpl
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.RoomTransactionRunner
import dev.mellow.core.database.dao.AlbumKeysetQueryFactory
import dev.mellow.core.database.dao.ArtistKeysetQueryFactory
import dev.mellow.core.database.dao.TrackKeysetQueryFactory
import dev.mellow.core.database.dao.RecentlyPlayedAlbumsObserver
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.model.Server
import dev.mellow.core.network.JellyfinClientWrapper
import dev.mellow.core.network.datasource.JellyfinDataSource
import dev.mellow.core.network.datasource.PagedItems
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.NameGuidPair
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
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
import java.time.LocalDateTime
import java.util.UUID

/**
 * The worker with the real library sync, database and sync bookkeeping, against a mocked server and artwork cache
 * that fails the way the artwork pre-cacher does when it can't reach the server (an [IOException]).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LibrarySyncWorkerArtworkTest {

    @get:Rule
    val folder = TemporaryFolder()

    private lateinit var db: MellowDatabase
    private lateinit var dataStoreScope: CoroutineScope
    private lateinit var preferences: SyncPreferences
    private lateinit var repository: LibraryRepositoryImpl
    private val dataSource = mockk<JellyfinDataSource>()
    private val client = mockk<JellyfinClientWrapper>()
    private val artwork = mockk<ArtworkPreCacher>()

    private val artist = BaseItemDto(id = UUID(1, 1), type = BaseItemKind.MUSIC_ARTIST, name = "Artist")
    private val album = BaseItemDto(
        id = UUID(2, 1),
        type = BaseItemKind.MUSIC_ALBUM,
        name = "Album",
        albumArtists = listOf(NameGuidPair(artist.name, artist.id)),
    )
    private val track = BaseItemDto(
        id = UUID(3, 1),
        type = BaseItemKind.AUDIO,
        name = "Track",
        albumId = album.id,
        artistItems = listOf(NameGuidPair(artist.name, artist.id)),
    )

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dataStoreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
        preferences = SyncPreferences(
            PreferenceDataStoreFactory.create(scope = dataStoreScope) { File(folder.root, "sync.preferences_pb") },
        )
        val queue = mockk<PlaybackQueuePreferences>()
        coEvery { queue.load() } returns null
        repository = LibraryRepositoryImpl(
            albumDao = db.albumDao(),
            artistDao = db.artistDao(),
            artistAliasDao = db.artistAliasDao(),
            trackDao = db.trackDao(),
            albumKeysetQueries = AlbumKeysetQueryFactory(db),
            artistKeysetQueries = ArtistKeysetQueryFactory(db),
            trackKeysetQueries = TrackKeysetQueryFactory(db),
            recentlyPlayedAlbums = RecentlyPlayedAlbumsObserver(db),
            serverDao = db.serverDao(),
            searchQueryDao = db.searchQueryDao(),
            syncPassDao = db.syncPassDao(),
            transaction = RoomTransactionRunner(db),
            jellyfinDataSource = dataSource,
            syncPreferences = preferences,
            playbackQueuePreferences = queue,
        )
        runBlocking {
            db.serverDao().upsert(
                ServerEntity(SERVER, "Home", "https://jellyfin.example", USER.toString(), "token", true, 0),
            )
        }
        every { client.isConnected } returns true

        coEvery { dataSource.getRecentlyAddedAlbums(USER, any()) } returns listOf(album)
        coEvery { dataSource.getRecentlyPlayedItems(USER, any()) } returns emptyList()
        coEvery { dataSource.getFavoriteAlbums(USER) } returns emptyList()
        coEvery { dataSource.getFavoriteTracks(USER) } returns emptyList()
        coEvery { dataSource.getFavoriteArtists(USER) } returns emptyList()
        coEvery { dataSource.getArtistsPaged(USER, any(), any()) } returns PagedItems(listOf(artist), 1)
        coEvery { dataSource.getAlbumsPaged(USER, any(), any(), any()) } returns PagedItems(listOf(album), 1)
        coEvery { dataSource.getTracksPaged(USER, any(), any(), any()) } returns PagedItems(listOf(track), 1)
        coEvery { artwork.preCacheIds(any()) } returns Unit
        coEvery { artwork.preCacheArtwork(SERVER, any()) } returns Unit
    }

    @After
    fun tearDown() {
        db.close()
        dataStoreScope.cancel()
    }

    @Test
    fun `home screen artwork failing doesn't stop the library sync`() = runTest {
        coEvery { artwork.preCacheIds(setOf(album.id.toString())) } throws IOException("Server unreachable")

        assertEquals(ListenableWorker.Result.success(), worker().doWork())

        coVerify { artwork.preCacheIds(setOf(album.id.toString())) }
        assertNotNull(db.trackDao().getTrackById(track.id.toString()))
        assertTrue(preferences.lastSyncTimestamp.first() > 0)
        assertFalse(isRebuildPending())
        assertEquals(0L, preferences.lastSyncFailedAt.first())
    }

    @Test
    fun `artwork failing after the library sync is retried without repeating the full pass`() = runTest {
        coEvery { artwork.preCacheArtwork(SERVER, any()) } throws IOException("Server unreachable")

        assertEquals(ListenableWorker.Result.retry(), worker().doWork())

        // The full pass is complete and recorded although the attempt is retried.
        val completedAt = preferences.lastSyncTimestamp.first()
        assertTrue(completedAt > 0)
        assertFalse(isRebuildPending())
        assertFalse(preferences.readLibrarySyncState().needsFullPass("$SERVER/$USER"))
        assertEquals(0L, preferences.lastSyncFailedAt.first())
        assertNotNull(db.trackDao().getTrackById(track.id.toString()))

        coEvery { artwork.preCacheArtwork(SERVER, any()) } returns Unit
        val windows = mutableListOf<LocalDateTime?>()
        coEvery { dataSource.getTracksPaged(USER, any(), any(), captureNullable(windows)) } returns
            PagedItems(listOf(track), 1)

        assertEquals(ListenableWorker.Result.success(), worker(runAttemptCount = 1).doWork())

        assertTrue("the retry syncs only what changed", windows.isNotEmpty() && windows.all { it != null })
        coVerify(exactly = 2) { artwork.preCacheArtwork(SERVER, any()) }
        assertTrue(preferences.lastSyncTimestamp.first() >= completedAt)
    }

    private suspend fun isRebuildPending(): Boolean =
        preferences.isFullPassPending(flowOf(Server(SERVER, "Home", "https://jellyfin.example", USER.toString(), "t")))
            .first()

    private fun worker(runAttemptCount: Int = 0): LibrarySyncWorker =
        TestListenableWorkerBuilder<LibrarySyncWorker>(RuntimeEnvironment.getApplication())
            .setInputData(workDataOf(LibrarySyncWorker.KEY_SERVER_ID to SERVER))
            .setRunAttemptCount(runAttemptCount)
            .setWorkerFactory(
                object : WorkerFactory() {
                    override fun createWorker(
                        appContext: Context,
                        workerClassName: String,
                        workerParameters: WorkerParameters,
                    ) = LibrarySyncWorker(
                        appContext, workerParameters, repository, db.serverDao(), client, preferences, artwork,
                    )
                },
            )
            .build()

    private companion object {
        const val SERVER = "server"
        val USER: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
    }
}
