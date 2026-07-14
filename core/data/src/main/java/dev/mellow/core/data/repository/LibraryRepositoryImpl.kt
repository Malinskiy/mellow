package dev.mellow.core.data.repository

import android.util.Log
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.data.mapper.toAlbumEntity
import dev.mellow.core.data.mapper.toArtistEntity
import dev.mellow.core.data.mapper.toModel
import dev.mellow.core.data.mapper.toTrackEntity
import dev.mellow.core.data.preferences.SyncPreferences
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.SearchQueryDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.SearchQueryEntity
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import dev.mellow.core.network.datasource.JellyfinDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
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
    private val trackDao: TrackDao,
    private val serverDao: ServerDao,
    private val searchQueryDao: SearchQueryDao,
    private val jellyfinDataSource: JellyfinDataSource,
    private val syncPreferences: SyncPreferences,
) : LibraryRepository {

    companion object {
        private const val TAG = "LibraryRepository"
        private const val ROOM_BIND_LIMIT = 900

        /**
         * Jellyfin replaces `/` with `+` in MusicArtist display names (e.g. "AC/DC" → "AC+DC").
         * Albums and tracks keep the original name from metadata ("AC/DC"), so exact matching
         * on artist.name fails. This helper produces the alternate form so DAO queries can
         * match either variant.
         */
        fun artistNameVariant(name: String): String {
            val slashToPlus = name.replace("/", "+")
            if (slashToPlus != name) return slashToPlus
            return name.replace("+", "/")
        }
    }

    override fun getAlbums(serverId: String): Flow<List<Album>> =
        albumDao.observeAlbumsByServer(serverId).map { entities -> entities.map { it.toModel() } }

    override fun getArtists(serverId: String): Flow<List<Artist>> =
        artistDao.observeArtistsByServer(serverId).map { entities -> entities.map { it.toModel() } }

    override fun getAlbumTracks(albumId: String): Flow<List<Track>> =
        trackDao.getTracksByAlbum(albumId).map { entities -> entities.map { it.toModel() } }

    override fun getArtistAlbums(artistName: String): Flow<List<Album>> =
        albumDao.getAlbumsByArtistName(artistName, artistNameVariant(artistName)).map { entities -> entities.map { it.toModel() } }

    override suspend fun getAlbum(albumId: String): Album? =
        albumDao.getAlbumById(albumId)?.toModel()

    override fun observeAlbum(albumId: String): Flow<Album?> =
        albumDao.observeAlbumById(albumId).map { it?.toModel() }

    override suspend fun getArtist(artistId: String): Artist? =
        artistDao.getArtistById(artistId)?.toModel()

    override fun observeArtist(artistId: String): Flow<Artist?> =
        artistDao.observeArtistById(artistId).map { it?.toModel() }

    override fun getArtistTracks(artistName: String): Flow<List<Track>> =
        trackDao.getTracksByArtistName(artistName, artistNameVariant(artistName)).map { entities -> entities.map { it.toModel() } }

    override suspend fun countArtistAlbums(artistName: String): Int =
        albumDao.countAlbumsByArtistName(artistName, artistNameVariant(artistName))

    override suspend fun countArtistTracks(artistName: String): Int =
        trackDao.countTracksByArtistName(artistName, artistNameVariant(artistName))

    override fun getRecentTracks(serverId: String): Flow<List<Track>> =
        trackDao.observeRecentTracks(serverId).map { entities -> entities.map { it.toModel() } }

    override suspend fun search(serverId: String, query: String): List<Track> =
        trackDao.search(serverId, query).map { it.toModel() }

    override suspend fun searchAlbums(serverId: String, query: String): List<Album> =
        albumDao.search(serverId, query).map { it.toModel() }

    override suspend fun searchArtists(serverId: String, query: String): List<Artist> =
        artistDao.search(serverId, query).map { it.toModel() }

    override fun getRecentSearches(serverId: String): Flow<List<String>> =
        searchQueryDao.getRecentSearches(serverId).map { entities ->
            entities.map { it.queryText }
        }

    override suspend fun saveRecentSearch(serverId: String, query: String) {
        searchQueryDao.upsert(
            SearchQueryEntity(
                serverId = serverId,
                queryText = query,
                searchedAt = System.currentTimeMillis(),
            ),
        )
    }

    override suspend fun deleteRecentSearch(serverId: String, query: String) {
        searchQueryDao.delete(serverId, query)
    }

    override suspend fun clearRecentSearches(serverId: String) {
        searchQueryDao.clearAll(serverId)
    }

    override fun getFavoriteTracks(serverId: String): Flow<List<Track>> =
        trackDao.getFavoriteTracks(serverId).map { entities -> entities.map { it.toModel() } }

    override fun getFavoriteAlbums(serverId: String): Flow<List<Album>> =
        albumDao.getFavoriteAlbums(serverId).map { entities -> entities.map { it.toModel() } }

    override fun getFavoriteArtists(serverId: String): Flow<List<Artist>> =
        artistDao.getFavoriteArtists(serverId).map { entities -> entities.map { it.toModel() } }

    override fun getRecentlyPlayedAlbums(serverId: String): Flow<List<Album>> =
        albumDao.getRecentlyPlayedAlbums(serverId).map { entities -> entities.map { it.toModel() } }

    override fun getMostPlayedAlbums(serverId: String): Flow<List<Album>> =
        albumDao.getMostPlayedAlbums(serverId).map { entities -> entities.map { it.toModel() } }

    override suspend fun syncLibrary(serverId: String, onProgress: (SyncProgress) -> Unit) {
        val server = serverDao.getActiveServer() ?: return
        val userId = UUID.fromString(server.userId)

        val lastSyncMs = syncPreferences.lastSyncTimestamp.first()

        if (lastSyncMs == 0L) {
            Log.d(TAG, "First sync — full")
            fullSync(serverId, userId, onProgress)
        } else {
            Log.d(TAG, "Incremental sync since ${Instant.ofEpochMilli(lastSyncMs)}")
            incrementalSync(serverId, userId, lastSyncMs, onProgress)
        }

        onProgress(SyncProgress("favorites", 0, 0))
        syncFavoritesDiff(serverId, userId)
        syncRecentlyPlayed(serverId, userId)

        syncPreferences.setLastSyncTimestamp(System.currentTimeMillis())
        syncPreferences.incrementSyncCount()
    }

    override suspend fun cleanupOrphans(serverId: String, onProgress: (SyncProgress) -> Unit) {
        val server = serverDao.getActiveServer() ?: return
        val userId = UUID.fromString(server.userId)

        onProgress(SyncProgress("albums", 0, 0))
        detectOrphanedAlbums(serverId, userId)
        onProgress(SyncProgress("artists", 0, 0))
        detectOrphanedArtists(serverId, userId)
    }

    override suspend fun syncFavorites(serverId: String) {
        val server = serverDao.getActiveServer() ?: return
        val userId = UUID.fromString(server.userId)

        val favAlbums = jellyfinDataSource.getFavoriteAlbums(userId)
        Log.d(TAG, "syncFavorites: ${favAlbums.size} albums from API")
        if (favAlbums.isNotEmpty()) {
            albumDao.upsertAlbums(favAlbums.map { it.toAlbumEntity(serverId) })
        }

        val favArtists = jellyfinDataSource.getFavoriteArtists(userId)
        Log.d(TAG, "syncFavorites: ${favArtists.size} artists from API")
        if (favArtists.isNotEmpty()) {
            artistDao.upsertArtists(favArtists.map { it.toArtistEntity(serverId) })
        }

        val favTracks = jellyfinDataSource.getFavoriteTracks(userId)
        Log.d(TAG, "syncFavorites: ${favTracks.size} tracks from API")
        if (favTracks.isNotEmpty()) {
            trackDao.upsertTracks(favTracks.map { it.toTrackEntity(serverId) })
        }
    }

    private suspend fun fullSync(serverId: String, userId: UUID, onProgress: (SyncProgress) -> Unit) {
        syncAllAlbums(serverId, userId, onProgress)
        syncAllArtists(serverId, userId, onProgress)
        syncAllTracks(serverId, userId, onProgress)
    }

    private suspend fun incrementalSync(
        serverId: String,
        userId: UUID,
        lastSyncMs: Long,
        onProgress: (SyncProgress) -> Unit,
    ) {
        val since = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastSyncMs), ZoneOffset.UTC)

        syncAlbumsIncremental(serverId, userId, since, onProgress)
        syncArtistsIncremental(serverId, userId, since, onProgress)
        syncTracksIncremental(serverId, userId, since, onProgress)
    }

    private suspend fun syncAlbumsIncremental(
        serverId: String,
        userId: UUID,
        since: LocalDateTime,
        onProgress: (SyncProgress) -> Unit,
    ) {
        var startIndex = 0
        val pageSize = 200
        while (true) {
            val items = jellyfinDataSource.getAlbums(userId, startIndex, pageSize, minDateLastSaved = since)
            if (items.isEmpty()) break
            Log.d(TAG, "Incremental album sync: ${items.size} changed items at offset $startIndex")
            albumDao.upsertAlbums(items.map { it.toAlbumEntity(serverId) })
            startIndex += items.size
            onProgress(SyncProgress("albums", startIndex, startIndex))
            if (items.size < pageSize) break
        }
    }

    private suspend fun syncArtistsIncremental(
        serverId: String,
        userId: UUID,
        since: LocalDateTime,
        onProgress: (SyncProgress) -> Unit,
    ) {
        var startIndex = 0
        val pageSize = 200
        while (true) {
            val items = jellyfinDataSource.getArtists(userId, startIndex, pageSize, minDateLastSaved = since)
            if (items.isEmpty()) break
            Log.d(TAG, "Incremental artist sync: ${items.size} changed items at offset $startIndex")
            artistDao.upsertArtists(items.map { it.toArtistEntity(serverId) })
            startIndex += items.size
            onProgress(SyncProgress("artists", startIndex, startIndex))
            if (items.size < pageSize) break
        }
    }

    private suspend fun syncTracksIncremental(
        serverId: String,
        userId: UUID,
        since: LocalDateTime,
        onProgress: (SyncProgress) -> Unit,
    ) {
        var startIndex = 0
        val pageSize = 500
        while (true) {
            val items = jellyfinDataSource.getTracks(userId, startIndex, pageSize, minDateLastSaved = since)
            if (items.isEmpty()) break
            Log.d(TAG, "Incremental track sync: ${items.size} changed items at offset $startIndex")
            trackDao.upsertTracks(items.map { it.toTrackEntity(serverId) })
            startIndex += items.size
            onProgress(SyncProgress("tracks", startIndex, startIndex))
            if (items.size < pageSize) break
        }
    }

    private suspend fun syncFavoritesDiff(serverId: String, userId: UUID) {
        val serverFavAlbumIds = jellyfinDataSource.getFavoriteAlbums(userId).map { it.id.toString() }.toSet()
        val serverFavTrackIds = jellyfinDataSource.getFavoriteTracks(userId).map { it.id.toString() }.toSet()
        val serverFavArtistIds = jellyfinDataSource.getFavoriteArtists(userId).map { it.id.toString() }.toSet()

        val localFavAlbumIds = albumDao.getFavoriteAlbumIds(serverId).toSet()
        val localFavTrackIds = trackDao.getFavoriteTrackIds(serverId).toSet()
        val localFavArtistIds = artistDao.getFavoriteArtistIds(serverId).toSet()

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

        val newFavArtists = serverFavArtistIds - localFavArtistIds
        val removedFavArtists = localFavArtistIds - serverFavArtistIds
        newFavArtists.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
            artistDao.setFavoriteByIds(chunk, true)
        }
        removedFavArtists.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
            artistDao.setFavoriteByIds(chunk, false)
        }

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

    private suspend fun detectOrphanedAlbums(serverId: String, userId: UUID) {
        val serverAlbumIds = mutableSetOf<String>()
        var startIndex = 0
        while (true) {
            val items = jellyfinDataSource.getAlbums(userId, startIndex, 500)
            if (items.isEmpty()) break
            serverAlbumIds.addAll(items.map { it.id.toString() })
            if (items.size < 500) break
            startIndex += 500
        }
        if (serverAlbumIds.isNotEmpty()) {
            val localAlbumIds = albumDao.getAllAlbumIdsByServer(serverId).toSet()
            val orphanIds = localAlbumIds - serverAlbumIds
            if (orphanIds.isNotEmpty()) {
                orphanIds.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
                    albumDao.deleteByIds(chunk)
                }
                Log.d(TAG, "Orphan detection: removed ${orphanIds.size} albums")
            }
        }
    }

    private suspend fun detectOrphanedArtists(serverId: String, userId: UUID) {
        val serverArtistIds = mutableSetOf<String>()
        var startIndex = 0
        while (true) {
            val items = jellyfinDataSource.getArtists(userId, startIndex, 500)
            if (items.isEmpty()) break
            serverArtistIds.addAll(items.map { it.id.toString() })
            if (items.size < 500) break
            startIndex += 500
        }
        if (serverArtistIds.isNotEmpty()) {
            val localArtistIds = artistDao.getAllArtistIdsByServer(serverId).toSet()
            val orphanIds = localArtistIds - serverArtistIds
            if (orphanIds.isNotEmpty()) {
                orphanIds.chunked(ROOM_BIND_LIMIT).forEach { chunk ->
                    artistDao.deleteByIds(chunk)
                }
                Log.d(TAG, "Orphan detection: removed ${orphanIds.size} artists")
            }
        }
    }

    private suspend fun syncAllAlbums(serverId: String, userId: UUID, onProgress: (SyncProgress) -> Unit) {
        var startIndex = 0
        val pageSize = 200
        var totalCount = 0
        while (true) {
            val paged = jellyfinDataSource.getAlbumsPaged(userId, startIndex, pageSize)
            if (paged.items.isEmpty()) break
            if (totalCount == 0) totalCount = paged.totalRecordCount
            albumDao.upsertAlbums(paged.items.map { it.toAlbumEntity(serverId) })
            startIndex += paged.items.size
            onProgress(SyncProgress("albums", startIndex, totalCount))
            if (paged.items.size < pageSize) break
        }
    }

    private suspend fun syncAllArtists(serverId: String, userId: UUID, onProgress: (SyncProgress) -> Unit) {
        var startIndex = 0
        val pageSize = 200
        var totalCount = 0
        while (true) {
            val paged = jellyfinDataSource.getArtistsPaged(userId, startIndex, pageSize)
            if (paged.items.isEmpty()) break
            if (totalCount == 0) totalCount = paged.totalRecordCount
            artistDao.upsertArtists(paged.items.map { it.toArtistEntity(serverId) })
            startIndex += paged.items.size
            onProgress(SyncProgress("artists", startIndex, totalCount))
            if (paged.items.size < pageSize) break
        }
    }

    private suspend fun syncAllTracks(serverId: String, userId: UUID, onProgress: (SyncProgress) -> Unit) {
        var startIndex = 0
        val pageSize = 500
        var totalCount = 0
        while (true) {
            val paged = jellyfinDataSource.getTracksPaged(userId, startIndex, pageSize)
            if (paged.items.isEmpty()) break
            if (totalCount == 0) totalCount = paged.totalRecordCount
            trackDao.upsertTracks(paged.items.map { it.toTrackEntity(serverId) })
            startIndex += paged.items.size
            onProgress(SyncProgress("tracks", startIndex, totalCount))
            if (paged.items.size < pageSize) break
        }
    }
}
