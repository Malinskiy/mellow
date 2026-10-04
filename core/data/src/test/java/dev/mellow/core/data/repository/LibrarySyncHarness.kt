package dev.mellow.core.data.repository

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.preferences.PlaybackQueuePreferences
import dev.mellow.core.data.preferences.SyncPreferences
import dev.mellow.core.database.DatabaseTransactionRunner
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.RoomTransactionRunner
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.model.Server
import dev.mellow.core.network.datasource.JellyfinDataSource
import dev.mellow.core.network.datasource.PagedItems
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.NameGuidPair
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID

/**
 * Library sync against a real (in-memory) database and sync bookkeeping, and a mocked Jellyfin server that pages
 * through [artists], [albums] and [tracks] by offset, as Jellyfin does.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
abstract class LibrarySyncHarness {

    @get:Rule
    val folder = TemporaryFolder()

    protected lateinit var db: MellowDatabase
    protected lateinit var preferences: SyncPreferences
    protected val dataSource = mockk<JellyfinDataSource>()
    protected val queue = mockk<PlaybackQueuePreferences>()
    private lateinit var dataStoreScope: CoroutineScope

    // What the server lists.
    protected var artists = listOf<BaseItemDto>()
    protected var albums = listOf<BaseItemDto>()
    protected var tracks = listOf<BaseItemDto>()

    /** Items the server has that its library listings don't include, e.g. playlist entries of another kind. */
    protected var unlisted = listOf<BaseItemDto>()

    @Before
    fun setUpServerAndDatabase() {
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
    fun closeDatabase() {
        db.close()
        dataStoreScope.cancel()
    }

    protected fun repository(transaction: DatabaseTransactionRunner = RoomTransactionRunner(db)) =
        LibraryRepositoryImpl(
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

    /** What Settings shows: the next sync of the active server is a full pass. */
    protected suspend fun isRebuildPending(): Boolean = preferences.isFullPassPending(flowOf(ACTIVE_SERVER)).first()

    protected fun assertSuccess(result: MellowResult<Unit>) {
        assertEquals(MellowResult.Success(Unit), result)
    }

    companion object {
        const val SERVER = "server"
        val USER: UUID = UUID.fromString("00000000-0000-0000-0000-0000000000aa")
        val ACTIVE_SERVER = Server(SERVER, "Home", "https://jellyfin.example", USER.toString(), "token")

        val BaseItemDto.key: String get() = id.toString()

        /** One page of [items] as Jellyfin returns it: a slice by offset, and the total of the whole listing. */
        fun page(items: List<BaseItemDto>, startIndex: Int, limit: Int) =
            PagedItems(items.drop(startIndex).take(limit), items.size)

        fun List<BaseItemDto>.withIds(ids: List<UUID>) = filter { it.id in ids }

        fun utc(epochMs: Long): LocalDateTime =
            LocalDateTime.ofInstant(Instant.ofEpochMilli(epochMs), ZoneOffset.UTC)

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
