package dev.mellow.core.data.repository

import android.util.Log
import androidx.paging.Pager
import androidx.paging.PagingData
import androidx.paging.map
import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.data.mapper.toAlbumArtistCrossRefs
import dev.mellow.core.data.mapper.toAlbumEntity
import dev.mellow.core.data.mapper.toArtistEntity
import dev.mellow.core.data.mapper.toModel
import dev.mellow.core.data.mapper.toTrackArtistCrossRefs
import dev.mellow.core.data.mapper.toTrackEntity
import dev.mellow.core.data.preferences.PlaybackQueuePreferences
import dev.mellow.core.data.preferences.SyncPreferences
import dev.mellow.core.data.preferences.librarySyncScope
import dev.mellow.core.common.getCleanValue
import dev.mellow.core.database.DatabaseTransactionRunner
import dev.mellow.core.database.converter.Converters
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.AlbumKeysetQueryFactory
import dev.mellow.core.database.dao.ArtistAliasDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.ArtistKeysetQueryFactory
import dev.mellow.core.database.dao.SearchQueryDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.SyncPassDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.dao.TrackKeysetQueryFactory
import dev.mellow.core.database.dao.getInstantMix
import dev.mellow.core.database.dao.mark
import dev.mellow.core.database.dao.pickRandomTracks
import dev.mellow.core.database.entity.ArtistAliasEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.SearchQueryEntity
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.database.entity.SyncPassKind
import dev.mellow.core.database.dao.RecentlyPlayedAlbumsObserver
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.LibrarySort
import dev.mellow.core.model.Track
import dev.mellow.core.network.datasource.JellyfinDataSource
import dev.mellow.core.network.datasource.PagedItems
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jellyfin.sdk.model.api.BaseItemDto
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val artistAliasDao: ArtistAliasDao,
    private val trackDao: TrackDao,
    private val albumKeysetQueries: AlbumKeysetQueryFactory,
    private val artistKeysetQueries: ArtistKeysetQueryFactory,
    private val trackKeysetQueries: TrackKeysetQueryFactory,
    private val recentlyPlayedAlbums: RecentlyPlayedAlbumsObserver,
    private val serverDao: ServerDao,
    private val searchQueryDao: SearchQueryDao,
    private val syncPassDao: SyncPassDao,
    private val transaction: DatabaseTransactionRunner,
    private val jellyfinDataSource: JellyfinDataSource,
    private val syncPreferences: SyncPreferences,
    private val playbackQueuePreferences: PlaybackQueuePreferences,
) : LibraryRepository {

    companion object {
        private const val TAG = "LibraryRepository"
        private const val ROOM_BIND_LIMIT = 900
        private const val ARTIST_PAGE_SIZE = 500
        private const val ALBUM_PAGE_SIZE = 500
        private const val TRACK_PAGE_SIZE = 1000
        private const val RECENTLY_PLAYED_ALBUMS = 20

        /** Unseen items asked about per request when a full pass checks which of them are gone. */
        private const val GONE_CHECK_BATCH_SIZE = 100

        /**
         * How long before the start of the last successful sync the next one starts looking for changes. Saving is
         * idempotent, so the overlap only refetches a few items; it covers changes saved on the server while that sync
         * ran and small clock differences between the phone and the server.
         */
        internal const val INCREMENTAL_SYNC_OVERLAP_MS = 10 * 60 * 1000L
    }

    /**
     * Library syncs and home screen syncs run one at a time: the scheduled and the on-demand sync are separate
     * WorkManager jobs, and nothing may save between a full pass's pages and its removal. Not reentrant: each of the
     * two takes it for itself, and neither calls the other.
     */
    private val syncMutex = Mutex()

    override fun getPagedAlbums(
        serverId: String,
        sort: LibrarySort,
        genre: String?,
        downloadedOnly: Boolean,
    ): Flow<PagingData<Album>> =
        Pager(LIBRARY_PAGING_CONFIG) {
            albumKeysetQueries.libraryPagingSource(serverId, sort.toOrder(), genre, downloadedOnly)
        }
            .flow
            .map { page -> page.map { it.toModel() } }

    override fun getPagedArtists(
        serverId: String,
        sort: LibrarySort,
        downloadedOnly: Boolean,
    ): Flow<PagingData<Artist>> =
        Pager(LIBRARY_PAGING_CONFIG) {
            artistKeysetQueries.libraryPagingSource(serverId, sort.toOrder(), downloadedOnly)
        }
            .flow
            .map { page -> page.map { it.artist.toModel().copy(albumCount = it.localAlbumCount) } }

    override fun getPagedTracks(
        serverId: String,
        sort: LibrarySort,
        downloadedOnly: Boolean,
    ): Flow<PagingData<Track>> =
        Pager(LIBRARY_PAGING_CONFIG) {
            trackKeysetQueries.libraryPagingSource(serverId, sort.toOrder(), downloadedOnly)
        }
            .flow
            .map { page -> page.map { it.toModel() } }

    override suspend fun getTracksWindow(
        serverId: String,
        sort: LibrarySort,
        downloadedOnly: Boolean,
        trackId: String,
        before: Int,
        limit: Int,
    ): MellowResult<List<Track>> =
        try {
            MellowResult.Success(
                trackKeysetQueries.libraryQueueWindow(
                    serverId,
                    sort.toOrder(),
                    downloadedOnly,
                    trackId,
                    before,
                    limit,
                )
                    .map { it.toModel() },
            )
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override fun getGenres(serverId: String, downloadedOnly: Boolean): Flow<MellowResult<List<String>>> =
        albumDao.observeRawGenreStrings(serverId, downloadedOnly)
            .map { rows ->
                val converters = Converters()
                MellowResult.Success(rows.flatMap { converters.toStringList(it) }.distinct().sorted())
                    as MellowResult<List<String>>
            }
            .catch { emit(MellowResult.Error(it)) }

    override fun getAlbumTracks(albumId: String): Flow<MellowResult<List<Track>>> =
        trackDao.getTracksByAlbum(albumId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Track>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getArtistAlbumsById(artistId: String): Flow<MellowResult<List<Album>>> =
        albumDao.getAlbumsByResolvedArtist(artistId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Album>> }
            .catch { emit(MellowResult.Error(it)) }

    override suspend fun getAlbum(albumId: String): MellowResult<Album?> =
        try {
            MellowResult.Success(albumDao.getAlbumById(albumId)?.toModel())
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override fun observeAlbum(albumId: String): Flow<MellowResult<Album?>> =
        albumDao.observeAlbumById(albumId)
            .map { MellowResult.Success(it?.toModel()) as MellowResult<Album?> }
            .catch { emit(MellowResult.Error(it)) }

    override suspend fun getArtist(artistId: String): MellowResult<Artist?> =
        try {
            MellowResult.Success(artistDao.getArtistById(artistId)?.toModel())
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override fun observeArtist(artistId: String): Flow<MellowResult<Artist?>> =
        artistDao.observeArtistById(artistId)
            .map { MellowResult.Success(it?.toModel()) as MellowResult<Artist?> }
            .catch { emit(MellowResult.Error(it)) }


    override fun getArtistTracksById(artistId: String): Flow<MellowResult<List<Track>>> =
        trackDao.getTracksByResolvedArtist(artistId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Track>> }
            .catch { emit(MellowResult.Error(it)) }

    override suspend fun countArtistAlbumsById(artistId: String): MellowResult<Int> =
        try {
            MellowResult.Success(albumDao.countAlbumsByResolvedArtist(artistId))
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun countArtistTracksById(artistId: String): MellowResult<Int> =
        try {
            MellowResult.Success(trackDao.countTracksByResolvedArtist(artistId))
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun getTrack(trackId: String): MellowResult<Track?> =
        try {
            MellowResult.Success(trackDao.getTrackById(trackId)?.toModel())
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun search(serverId: String, query: String): MellowResult<List<Track>> =
        try {
            MellowResult.Success(trackDao.search(serverId, query).map { it.toModel() })
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun searchAlbums(serverId: String, query: String): MellowResult<List<Album>> =
        try {
            MellowResult.Success(albumDao.search(serverId, query).map { it.toModel() })
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun searchArtists(serverId: String, query: String): MellowResult<List<Artist>> =
        try {
            MellowResult.Success(artistDao.search(serverId, query).map { it.toModel() })
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override fun getRecentSearches(serverId: String): Flow<MellowResult<List<String>>> =
        searchQueryDao.getRecentSearches(serverId)
            .map { entities -> MellowResult.Success(entities.map { it.queryText }) as MellowResult<List<String>> }
            .catch { emit(MellowResult.Error(it)) }

    override suspend fun saveRecentSearch(serverId: String, query: String): MellowResult<Unit> {
        return try {
            searchQueryDao.upsert(
                SearchQueryEntity(
                    serverId = serverId,
                    queryText = query,
                    searchedAt = System.currentTimeMillis(),
                ),
            )
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun deleteRecentSearch(serverId: String, query: String): MellowResult<Unit> {
        return try {
            searchQueryDao.delete(serverId, query)
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun clearRecentSearches(serverId: String): MellowResult<Unit> {
        return try {
            searchQueryDao.clearAll(serverId)
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override fun getPagedFavoriteTracks(serverId: String, downloadedOnly: Boolean): Flow<PagingData<Track>> =
        Pager(LIBRARY_PAGING_CONFIG) { trackDao.getFavoriteTracksPaged(serverId, downloadedOnly) }
            .flow
            .map { page -> page.map { it.toModel() } }

    override fun getPagedFavoriteAlbums(serverId: String, downloadedOnly: Boolean): Flow<PagingData<Album>> =
        Pager(LIBRARY_PAGING_CONFIG) { albumDao.getFavoriteAlbumsPaged(serverId, downloadedOnly) }
            .flow
            .map { page -> page.map { it.toModel() } }

    override fun getPagedFavoriteArtists(serverId: String, downloadedOnly: Boolean): Flow<PagingData<Artist>> =
        Pager(LIBRARY_PAGING_CONFIG) { artistDao.getFavoriteArtistsPaged(serverId, downloadedOnly) }
            .flow
            .map { page -> page.map { it.toModel() } }

    override suspend fun getFavoriteTracksSlice(
        serverId: String,
        downloadedOnly: Boolean,
        offset: Int,
        limit: Int,
    ): MellowResult<List<Track>> =
        try {
            MellowResult.Success(
                trackDao.getFavoriteTracksSlice(serverId, downloadedOnly, limit, offset).map { it.toModel() },
            )
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun countFavoriteTracks(serverId: String, downloadedOnly: Boolean): MellowResult<Int> =
        try {
            MellowResult.Success(trackDao.countFavoriteTracks(serverId, downloadedOnly))
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun pickRandomFavoriteTracks(
        serverId: String,
        downloadedOnly: Boolean,
        limit: Int,
    ): MellowResult<List<Track>> =
        try {
            MellowResult.Success(
                trackDao.getRandomFavoriteTracks(serverId, downloadedOnly, limit).map { it.toModel() },
            )
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun pickRandomTracks(
        serverId: String,
        downloadedOnly: Boolean,
        limit: Int,
    ): MellowResult<List<Track>> =
        try {
            MellowResult.Success(trackDao.pickRandomTracks(serverId, downloadedOnly, limit).map { it.toModel() })
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override fun getRandomFavoriteTracks(serverId: String, limit: Int): Flow<MellowResult<List<Track>>> =
        trackDao.observeRandomFavoriteTracks(serverId, limit)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Track>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getRecentlyAddedAlbums(serverId: String, limit: Int): Flow<MellowResult<List<Album>>> =
        albumDao.observeRecentlyAddedAlbums(serverId, limit)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Album>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getRandomAlbums(serverId: String, limit: Int): Flow<MellowResult<List<Album>>> =
        albumDao.observeRandomAlbums(serverId, limit)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Album>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getTopGenres(serverId: String, limit: Int): Flow<MellowResult<List<String>>> =
        albumDao.observeGenreAlbumCounts(serverId)
            .map { rows ->
                val converters = Converters()
                MellowResult.Success(topGenres(rows.map { converters.toStringList(it.genres) to it.albumCount }, limit))
                    as MellowResult<List<String>>
            }
            .catch { emit(MellowResult.Error(it)) }

    override fun getRecentlyPlayedAlbums(serverId: String): Flow<MellowResult<List<Album>>> =
        recentlyPlayedAlbums.observe(serverId, RECENTLY_PLAYED_ALBUMS)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Album>> }
            .catch { emit(MellowResult.Error(it)) }

    override suspend fun syncHomeScreenPriority(
        serverId: String,
        onProgress: (SyncProgress) -> Unit,
    ): MellowResult<Set<String>> = syncMutex.withLock {
        try {
            val server = activeServer(serverId) ?: return@withLock MellowResult.Success(emptySet())
            val userId = UUID.fromString(server.userId)
            val imageIds = mutableSetOf<String>()

            onProgress(SyncProgress("home", 0, 5))

            coroutineScope {
                val recentAlbums = async { jellyfinDataSource.getRecentlyAddedAlbums(userId, 50) }
                val recentTracks = async { jellyfinDataSource.getRecentlyPlayedItems(userId, 200) }
                val favAlbums = async { jellyfinDataSource.getFavoriteAlbums(userId) }
                val favTracks = async { jellyfinDataSource.getFavoriteTracks(userId) }

                val albums = recentAlbums.await()
                albumDao.upsertAlbums(albums.map { it.toAlbumEntity(serverId) })
                albumDao.insertAlbumArtists(albums.flatMap { it.toAlbumArtistCrossRefs() })
                albums.forEach { it.id.toString().let(imageIds::add) }
                onProgress(SyncProgress("home", 1, 5))

                val tracks = recentTracks.await()
                trackDao.upsertTracks(tracks.map { it.toTrackEntity(serverId) })
                trackDao.insertTrackArtists(tracks.flatMap { it.toTrackArtistCrossRefs() })
                tracks.forEach { dto ->
                    dto.albumId?.toString()?.let(imageIds::add)
                }
                onProgress(SyncProgress("home", 2, 5))

                val fAlbums = favAlbums.await()
                albumDao.upsertAlbums(fAlbums.map { it.toAlbumEntity(serverId) })
                albumDao.insertAlbumArtists(fAlbums.flatMap { it.toAlbumArtistCrossRefs() })
                fAlbums.forEach { it.id.toString().let(imageIds::add) }
                onProgress(SyncProgress("home", 3, 5))

                val fTracks = favTracks.await()
                trackDao.upsertTracks(fTracks.map { it.toTrackEntity(serverId) })
                trackDao.insertTrackArtists(fTracks.flatMap { it.toTrackArtistCrossRefs() })
                fTracks.forEach { dto ->
                    dto.albumId?.toString()?.let(imageIds::add)
                }
                onProgress(SyncProgress("home", 4, 5))

                val knownAlbumIds = albums.map { it.id.toString() }.toSet() +
                    fAlbums.map { it.id.toString() }.toSet()
                val missingAlbumIds = (tracks + fTracks).mapNotNull { it.albumId }
                    .distinct()
                    .filter { it.toString() !in knownAlbumIds }
                if (missingAlbumIds.isNotEmpty()) {
                    val parentAlbums = jellyfinDataSource.getAlbumsByIds(userId, missingAlbumIds)
                    albumDao.upsertAlbums(parentAlbums.map { it.toAlbumEntity(serverId) })
                    albumDao.insertAlbumArtists(parentAlbums.flatMap { it.toAlbumArtistCrossRefs() })
                    parentAlbums.forEach { it.id.toString().let(imageIds::add) }
                }
                onProgress(SyncProgress("home", 5, 5))
            }

            Log.d(TAG, "Home screen priority sync: ${imageIds.size} unique image IDs")
            MellowResult.Success(imageIds)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun syncLibrary(serverId: String, onProgress: (SyncProgress) -> Unit): MellowResult<Unit> =
        syncMutex.withLock {
            try {
                val server = activeServer(serverId) ?: return@withLock MellowResult.Success(Unit)
                val userId = UUID.fromString(server.userId)
                val scope = librarySyncScope(serverId, server.userId)
                val startedAt = System.currentTimeMillis()
                val state = syncPreferences.readLibrarySyncState()
                if (state.needsFullPass(scope)) {
                    val request = syncPreferences.markFullPassPending()
                    Log.d(TAG, "Full library pass")
                    fullPass(serverId, userId, onProgress)
                    syncPreferences.recordSyncSucceeded(scope, startedAt, System.currentTimeMillis(), request)
                } else {
                    val since = (state.lastStartedAt - INCREMENTAL_SYNC_OVERLAP_MS).coerceAtLeast(0L)
                    Log.d(TAG, "Library changes since ${since.toUtcDateTime()} UTC")
                    syncChanges(serverId, userId, since.toUtcDateTime(), onProgress)
                    syncPreferences.recordSyncSucceeded(scope, startedAt, System.currentTimeMillis(), null)
                }
                MellowResult.Success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Library sync failed, nothing recorded", e)
                MellowResult.Error(e)
            }
        }

    /**
     * The active server, if it's [serverId]. A sync scheduled for a server that is no longer active (logged out, or
     * another one logged in) must not fetch or save anything: it would save the active server's items under [serverId].
     */
    private suspend fun activeServer(serverId: String): ServerEntity? {
        val server = serverDao.getActiveServer()
        if (server?.id != serverId) {
            Log.d(TAG, "Sync skipped: $serverId is not the active server")
            return null
        }
        return server
    }

    override suspend fun syncFavorites(serverId: String): MellowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return MellowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)

            val favAlbums = jellyfinDataSource.getFavoriteAlbums(userId)
            Log.d(TAG, "syncFavorites: ${favAlbums.size} albums from API")
            if (favAlbums.isNotEmpty()) {
                albumDao.upsertAlbums(favAlbums.map { it.toAlbumEntity(serverId) })
                favAlbums.forEach { albumDao.clearAlbumArtists(it.id.toString()) }
                albumDao.insertAlbumArtists(favAlbums.flatMap { it.toAlbumArtistCrossRefs() })
            }

            val favArtists = jellyfinDataSource.getFavoriteArtists(userId)
            Log.d(TAG, "syncFavorites: ${favArtists.size} artists from API")
            if (favArtists.isNotEmpty()) {
                artistDao.upsertArtists(favArtists.map { it.toArtistEntity(serverId) })
            }
            applyFavoriteArtists(serverId, favArtists.map { it.id.toString() }.toSet())

            val favTracks = jellyfinDataSource.getFavoriteTracks(userId)
            Log.d(TAG, "syncFavorites: ${favTracks.size} tracks from API")
            if (favTracks.isNotEmpty()) {
                trackDao.upsertTracks(favTracks.map { it.toTrackEntity(serverId) })
                favTracks.forEach { trackDao.clearTrackArtists(it.id.toString()) }
                trackDao.insertTrackArtists(favTracks.flatMap { it.toTrackArtistCrossRefs() })
            }
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    private suspend fun resolveArtistAliases(serverId: String) {
        val artists = artistDao.getAllArtistsByServer(serverId).map { artist ->
            if (artist.cleanName.isEmpty()) {
                val computed = getCleanValue(artist.name)
                artistDao.updateCleanName(artist.id, computed)
                artist.copy(cleanName = computed)
            } else {
                artist
            }
        }

        val clusters = artists.groupBy { it.cleanName }

        val aliases = mutableListOf<ArtistAliasEntity>()
        val now = System.currentTimeMillis()

        for ((_, group) in clusters) {
            val canonical = group.sortedWith(
                compareByDescending<ArtistEntity> { it.musicBrainzId != null }
                    .thenByDescending { it.imageTag != null }
                    .thenByDescending { it.albumCount }
                    .thenBy { it.id },
            ).first()

            for (artist in group) {
                aliases.add(
                    ArtistAliasEntity(
                        serverId = serverId,
                        rawArtistId = artist.id,
                        canonicalArtistId = canonical.id,
                        lastSynced = now,
                    ),
                )
            }
        }

        val mbidGroups = aliases
            .mapNotNull { alias ->
                artists.find { it.id == alias.rawArtistId }?.musicBrainzId?.let { mbid -> mbid to alias }
            }
            .groupBy { it.first }

        for ((_, mbidAliases) in mbidGroups) {
            if (mbidAliases.size > 1) {
                val canonicalId = mbidAliases
                    .map { it.second.canonicalArtistId }
                    .minByOrNull { id ->
                        val a = artists.find { it.id == id }
                        if (a?.musicBrainzId != null) 0 else 1
                    } ?: continue
                for ((_, alias) in mbidAliases) {
                    val idx = aliases.indexOfFirst {
                        it.rawArtistId == alias.rawArtistId && it.serverId == alias.serverId
                    }
                    if (idx >= 0) {
                        aliases[idx] = aliases[idx].copy(canonicalArtistId = canonicalId)
                    }
                }
            }
        }

        artistAliasDao.deleteByServer(serverId)
        artistAliasDao.upsertAliases(aliases)
    }

    /**
     * Fetches the whole library and deletes what the server no longer has. It completes as a unit: when any step fails
     * the pass is abandoned, and the next sync starts a new one from scratch.
     *
     * Pages are saved as they arrive (it's current server data), each replacing the artist links of its own items
     * only, so an interrupted pass leaves every album and track with links. Every item a page holds is recorded as
     * seen; at the end, one transaction deletes the unseen ones the server confirms are gone.
     */
    private suspend fun fullPass(serverId: String, userId: UUID, onProgress: (SyncProgress) -> Unit) {
        syncPassDao.clear()

        val artists = pageThrough("artists", ARTIST_PAGE_SIZE, onProgress, { startIndex, limit ->
            jellyfinDataSource.getArtistsPaged(userId, startIndex, limit)
        }) { items ->
            transaction {
                artistDao.upsertArtists(items.map { it.toArtistEntity(serverId) })
                syncPassDao.mark(SyncPassKind.ARTIST, items.ids())
            }
        }
        val albums = pageThrough("albums", ALBUM_PAGE_SIZE, onProgress, { startIndex, limit ->
            jellyfinDataSource.getAlbumsPaged(userId, startIndex, limit)
        }) { items ->
            transaction {
                saveAlbums(serverId, items)
                syncPassDao.mark(SyncPassKind.ALBUM, items.ids())
            }
        }
        val tracks = pageThrough("tracks", TRACK_PAGE_SIZE, onProgress, { startIndex, limit ->
            jellyfinDataSource.getTracksPaged(userId, startIndex, limit)
        }) { items ->
            transaction {
                saveTracks(serverId, items)
                syncPassDao.mark(SyncPassKind.TRACK, items.ids())
            }
        }

        // Paging by offset skips or repeats items when the library changes underneath it. Only a pass that saw
        // exactly what the server reports, before and after, may delete anything.
        checkSawEverything("artists", artists, syncPassDao.count(SyncPassKind.ARTIST)) {
            jellyfinDataSource.getArtistsPaged(userId, 0, 1).totalRecordCount
        }
        checkSawEverything("albums", albums, syncPassDao.count(SyncPassKind.ALBUM)) {
            jellyfinDataSource.getAlbumsPaged(userId, 0, 1).totalRecordCount
        }
        checkSawEverything("tracks", tracks, syncPassDao.count(SyncPassKind.TRACK)) {
            jellyfinDataSource.getTracksPaged(userId, 0, 1).totalRecordCount
        }

        onProgress(SyncProgress("favorites", 0, 0))
        syncFavoritesDiff(serverId, userId)
        syncRecentlyPlayed(serverId, userId)

        val goneAlbumIds = findGoneAlbums(serverId, userId, onProgress)
        val goneTrackIds = findGoneTracks(serverId, userId, onProgress)

        transaction {
            // The play queue is read here rather than earlier: a track queued while the pass asked the server about
            // its unseen items is kept too. If the queue can't be read, no track is deleted.
            val queued = queuedTrackIds()
            syncPassDao.mark(SyncPassKind.GONE_TRACK, if (queued == null) emptyList() else goneTrackIds - queued)
            syncPassDao.mark(SyncPassKind.GONE_ALBUM, goneAlbumIds)
            val deletedTracks = syncPassDao.deleteGoneTracks(serverId)
            val deletedAlbums = syncPassDao.deleteGoneAlbums(serverId)
            val deletedArtists = syncPassDao.deleteUnseenArtists(serverId)
            syncPassDao.deleteOrphanedLyrics(serverId)
            resolveArtists(serverId)
            syncPassDao.clear()
            Log.d(TAG, "Full pass removed $deletedArtists artists, $deletedAlbums albums, $deletedTracks tracks")
        }
    }

    /**
     * Fetches what changed on the server since [since]. Artists are always fetched whole. Deletions aren't visible
     * this way; the next full pass removes them.
     */
    private suspend fun syncChanges(
        serverId: String,
        userId: UUID,
        since: LocalDateTime,
        onProgress: (SyncProgress) -> Unit,
    ) {
        pageThrough("artists", ARTIST_PAGE_SIZE, onProgress, { startIndex, limit ->
            jellyfinDataSource.getArtistsPaged(userId, startIndex, limit)
        }) { items ->
            artistDao.upsertArtists(items.map { it.toArtistEntity(serverId) })
        }

        val albumIds = HashSet<String>()
        val albums = pageThrough("albums", ALBUM_PAGE_SIZE, onProgress, { startIndex, limit ->
            jellyfinDataSource.getAlbumsPaged(userId, startIndex, limit, minDateLastSaved = since)
        }) { items ->
            transaction { saveAlbums(serverId, items) }
            albumIds += items.ids()
        }
        if (!albums.isSnapshot(albumIds.size)) {
            checkSawEverything("changed albums", albums, albumIds.size) {
                jellyfinDataSource.getAlbumsPaged(userId, 0, 1, minDateLastSaved = since).totalRecordCount
            }
        }

        val trackIds = HashSet<String>()
        val tracks = pageThrough("tracks", TRACK_PAGE_SIZE, onProgress, { startIndex, limit ->
            jellyfinDataSource.getTracksPaged(userId, startIndex, limit, minDateLastSaved = since)
        }) { items ->
            transaction { saveTracks(serverId, items) }
            trackIds += items.ids()
        }
        if (!tracks.isSnapshot(trackIds.size)) {
            checkSawEverything("changed tracks", tracks, trackIds.size) {
                jellyfinDataSource.getTracksPaged(userId, 0, 1, minDateLastSaved = since).totalRecordCount
            }
        }
        Log.d(TAG, "Changes: ${albumIds.size} albums, ${trackIds.size} tracks")

        transaction { resolveArtists(serverId) }

        onProgress(SyncProgress("favorites", 0, 0))
        syncFavoritesDiff(serverId, userId)
        syncRecentlyPlayed(serverId, userId)
    }

    /**
     * Pages through a listing from the start, saving each page while the next one is fetched. Returns the total the
     * server reported with the first page and the number of pages fetched.
     */
    private suspend fun pageThrough(
        phase: String,
        pageSize: Int,
        onProgress: (SyncProgress) -> Unit,
        fetch: suspend (startIndex: Int, limit: Int) -> PagedItems,
        save: suspend (List<BaseItemDto>) -> Unit,
    ): Listing = coroutineScope {
        var total = -1
        var pages = 0
        var startIndex = 0
        var next: Deferred<PagedItems>? = async { fetch(0, pageSize) }
        while (next != null) {
            val page = next.await()
            pages++
            if (total < 0) total = page.totalRecordCount
            val nextStart = startIndex + page.items.size
            next = if (page.items.size == pageSize) async { fetch(nextStart, pageSize) } else null
            if (page.items.isNotEmpty()) {
                save(page.items)
                onProgress(SyncProgress(phase, nextStart, maxOf(total, nextStart)))
            }
            startIndex = nextStart
        }
        Listing(total = total.coerceAtLeast(0), pages = pages)
    }

    /**
     * Throws unless [seen] distinct items is what the server reported when the listing started and reports now: if the
     * library changed while it was paged, items may have been skipped.
     */
    private suspend fun checkSawEverything(phase: String, listing: Listing, seen: Int, countNow: suspend () -> Int) {
        val now = countNow()
        if (seen != now || listing.total != now) {
            throw LibraryChangedDuringSyncException(
                "$phase: saw $seen, the server listed ${listing.total} at the start and lists $now now",
            )
        }
    }

    /**
     * This server's albums the full pass didn't see that the server confirms are gone. Ones it still has are saved
     * instead of being deleted.
     */
    private suspend fun findGoneAlbums(
        serverId: String,
        userId: UUID,
        onProgress: (SyncProgress) -> Unit,
    ): List<String> = findGone(
        phase = "removed albums",
        unseenIds = syncPassDao.getUnseenAlbumIds(serverId),
        onProgress = onProgress,
        fetch = { ids -> jellyfinDataSource.getAlbumsByIds(userId, ids) },
    ) { items ->
        saveAlbums(serverId, items)
        syncPassDao.mark(SyncPassKind.ALBUM, items.ids())
    }

    /**
     * This server's tracks the full pass didn't see that the server confirms are gone, except downloaded ones, which
     * are kept. Ones the server still has are saved instead of being deleted. Queued tracks are left out when the gone
     * ones are deleted.
     */
    private suspend fun findGoneTracks(
        serverId: String,
        userId: UUID,
        onProgress: (SyncProgress) -> Unit,
    ): List<String> = findGone(
        phase = "removed tracks",
        unseenIds = syncPassDao.getUnseenTrackIds(serverId),
        onProgress = onProgress,
        fetch = { ids -> jellyfinDataSource.getTracksByIds(userId, ids) },
    ) { items ->
        saveTracks(serverId, items)
        syncPassDao.mark(SyncPassKind.TRACK, items.ids())
    }

    /** Asks the server which of [unseenIds] it still has, saving those; returns the rest. */
    private suspend fun findGone(
        phase: String,
        unseenIds: List<String>,
        onProgress: (SyncProgress) -> Unit,
        fetch: suspend (List<UUID>) -> List<BaseItemDto>,
        save: suspend (List<BaseItemDto>) -> Unit,
    ): List<String> {
        val gone = mutableListOf<String>()
        unseenIds.chunked(GONE_CHECK_BATCH_SIZE).forEachIndexed { index, chunk ->
            onProgress(SyncProgress(phase, index * GONE_CHECK_BATCH_SIZE, unseenIds.size))
            // Jellyfin item IDs are GUIDs; anything else can't exist on the server.
            val ids = chunk.mapNotNull { it.toUuidOrNull() }
            val stillThere = if (ids.isEmpty()) emptyList() else fetch(ids)
            if (stillThere.isNotEmpty()) transaction { save(stillThere) }
            val stillThereIds = stillThere.map { it.id }.toSet()
            chunk.filterTo(gone) { id -> id.toUuidOrNull().let { it == null || it !in stillThereIds } }
        }
        Log.d(TAG, "${unseenIds.size} unseen, ${gone.size} gone ($phase)")
        return gone
    }

    /**
     * The tracks of the saved play queue, which a full pass never deletes; null if the queue can't be read. Read inside
     * the removal transaction: it's a small DataStore read that doesn't touch the database.
     */
    private suspend fun queuedTrackIds(): Set<String>? =
        try {
            playbackQueuePreferences.load()?.trackIds?.toSet().orEmpty()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG, "Couldn't read the play queue, so no track is deleted this pass", e)
            null
        }

    /** Saves albums from the server with the artist links they have there. */
    private suspend fun saveAlbums(serverId: String, items: List<BaseItemDto>) {
        albumDao.upsertAlbums(items.map { it.toAlbumEntity(serverId) })
        albumDao.replaceAlbumArtists(items.ids(), items.flatMap { it.toAlbumArtistCrossRefs() })
    }

    /** Saves tracks from the server with the artist links they have there. */
    private suspend fun saveTracks(serverId: String, items: List<BaseItemDto>) {
        trackDao.upsertTracks(items.map { it.toTrackEntity(serverId) })
        trackDao.replaceTrackArtists(items.ids(), items.flatMap { it.toTrackArtistCrossRefs() })
    }

    /** Rebuilds the artist aliases and points albums and tracks at their (canonical) artists. */
    private suspend fun resolveArtists(serverId: String) {
        resolveArtistAliases(serverId)
        albumDao.resolveArtistIds(serverId)
        trackDao.resolveArtistIds(serverId)
        albumDao.resolveArtistAliases(serverId)
        trackDao.resolveArtistAliases(serverId)
    }

    /**
     * Makes exactly [serverFavArtistIds] the favorite artists. Returns the added and removed ids.
     */
    private suspend fun applyFavoriteArtists(
        serverId: String,
        serverFavArtistIds: Set<String>,
    ): Pair<Set<String>, Set<String>> {
        val localFavArtistIds = artistDao.getFavoriteArtistIds(serverId).toSet()
        val added = serverFavArtistIds - localFavArtistIds
        val removed = localFavArtistIds - serverFavArtistIds
        added.chunked(ROOM_BIND_LIMIT).forEach { chunk -> artistDao.setFavoriteByIds(chunk, true) }
        removed.chunked(ROOM_BIND_LIMIT).forEach { chunk -> artistDao.setFavoriteByIds(chunk, false) }
        return added to removed
    }

    private suspend fun syncFavoritesDiff(serverId: String, userId: UUID) {
        val serverFavAlbumIds = jellyfinDataSource.getFavoriteAlbums(userId).map { it.id.toString() }.toSet()
        val serverFavTrackIds = jellyfinDataSource.getFavoriteTracks(userId).map { it.id.toString() }.toSet()
        val serverFavArtistIds = jellyfinDataSource.getFavoriteArtists(userId).map { it.id.toString() }.toSet()

        val localFavAlbumIds = albumDao.getFavoriteAlbumIds(serverId).toSet()
        val localFavTrackIds = trackDao.getFavoriteTrackIds(serverId).toSet()

        val newFavAlbums = serverFavAlbumIds - localFavAlbumIds
        val removedFavAlbums = localFavAlbumIds - serverFavAlbumIds
        newFavAlbums.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
            albumDao.setFavoriteByIds(chunk, true)
        }
        removedFavAlbums.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
            albumDao.setFavoriteByIds(chunk, false)
        }

        val newFavTracks = serverFavTrackIds - localFavTrackIds
        val removedFavTracks = localFavTrackIds - serverFavTrackIds
        newFavTracks.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
            trackDao.setFavoriteByIds(chunk, true)
        }
        removedFavTracks.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
            trackDao.setFavoriteByIds(chunk, false)
        }

        val (newFavArtists, removedFavArtists) = applyFavoriteArtists(serverId, serverFavArtistIds)

        Log.d(
            TAG,
            "Favorites diff: albums +${newFavAlbums.size}/-${removedFavAlbums.size}, " +
                "tracks +${newFavTracks.size}/-${removedFavTracks.size}, " +
                "artists +${newFavArtists.size}/-${removedFavArtists.size}",
        )
    }

    private suspend fun syncRecentlyPlayed(serverId: String, userId: UUID) {
        val recentItems = jellyfinDataSource.getRecentlyPlayedItems(userId, limit = 200)
        if (recentItems.isNotEmpty()) {
            trackDao.upsertTracks(recentItems.map { it.toTrackEntity(serverId) })
            Log.d(TAG, "Recently played sync: ${recentItems.size} tracks updated")
        }
    }

    override suspend fun getInstantMix(serverId: String, trackId: String): MellowResult<List<Track>> {
        return try {
            val server = serverDao.getActiveServer()
                ?: return MellowResult.Success(offlineInstantMix(serverId, trackId, downloadedOnly = true))
            val userId = UUID.fromString(server.userId)
            val dtos = jellyfinDataSource.getInstantMixFromSong(
                itemId = UUID.fromString(trackId),
                userId = userId,
            )
            if (dtos.isNotEmpty()) {
                trackDao.upsertTracks(dtos.map { it.toTrackEntity(serverId) })
                MellowResult.Success(
                    dtos.mapNotNull { dto ->
                        trackDao.getTrackById(dto.id.toString())?.toModel()
                    },
                )
            } else {
                // API returned empty — still online, can stream any local track
                MellowResult.Success(offlineInstantMix(serverId, trackId, downloadedOnly = false))
            }
        } catch (e: Exception) {
            Log.d(TAG, "InstantMix API unavailable, falling back to offline mix")
            // Network error — truly offline, only downloaded tracks are playable
            try {
                MellowResult.Success(offlineInstantMix(serverId, trackId, downloadedOnly = true))
            } catch (fallbackError: Exception) {
                MellowResult.Error(fallbackError)
            }
        }
    }

    private suspend fun offlineInstantMix(
        serverId: String,
        trackId: String,
        downloadedOnly: Boolean,
    ): List<Track> {
        val seed = trackDao.getTrackById(trackId) ?: return emptyList()
        return trackDao.getInstantMix(
            serverId = serverId,
            seedTrackId = trackId,
            artistName = seed.artistName,
            genres = seed.genres,
            downloadedOnly = downloadedOnly,
        ).map { it.toModel() }
    }

    /** What paging through a listing found: the total the server reported first, and the number of pages. */
    private data class Listing(val total: Int, val pages: Int) {
        /** One page held the whole listing, so it's a consistent snapshot of the server. */
        fun isSnapshot(distinctItems: Int): Boolean = pages == 1 && distinctItems == total
    }
}

/** The library changed on the server while a sync paged through it, so the sync may have missed items. */
internal class LibraryChangedDuringSyncException(message: String) : IllegalStateException(message)

private fun List<BaseItemDto>.ids(): List<String> = map { it.id.toString() }

private fun String.toUuidOrNull(): UUID? =
    try {
        UUID.fromString(this)
    } catch (_: IllegalArgumentException) {
        null
    }

private fun Long.toUtcDateTime(): LocalDateTime =
    LocalDateTime.ofInstant(Instant.ofEpochMilli(this), ZoneOffset.UTC)
