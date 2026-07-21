package dev.mellow.core.data.repository

import android.util.Log
import dev.mellow.core.common.MellowResult
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
import dev.mellow.core.database.dao.getInstantMix
import dev.mellow.core.database.entity.SearchQueryEntity
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import dev.mellow.core.network.datasource.JellyfinDataSource
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
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

    override fun getAlbums(serverId: String): Flow<MellowResult<List<Album>>> =
        albumDao.observeAlbumsByServer(serverId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Album>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getArtists(serverId: String): Flow<MellowResult<List<Artist>>> =
        artistDao.observeArtistsByServer(serverId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Artist>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getAlbumTracks(albumId: String): Flow<MellowResult<List<Track>>> =
        trackDao.getTracksByAlbum(albumId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Track>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getArtistAlbums(artistName: String): Flow<MellowResult<List<Album>>> =
        albumDao.getAlbumsByArtistName(artistName, artistNameVariant(artistName))
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


    override fun getArtistTracks(artistName: String): Flow<MellowResult<List<Track>>> =
        trackDao.getTracksByArtistName(artistName, artistNameVariant(artistName))
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Track>> }
            .catch { emit(MellowResult.Error(it)) }

    override suspend fun countArtistAlbums(artistName: String): MellowResult<Int> =
        try {
            MellowResult.Success(albumDao.countAlbumsByArtistName(artistName, artistNameVariant(artistName)))
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun countArtistTracks(artistName: String): MellowResult<Int> =
        try {
            MellowResult.Success(trackDao.countTracksByArtistName(artistName, artistNameVariant(artistName)))
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override fun getRecentTracks(serverId: String): Flow<MellowResult<List<Track>>> =
        trackDao.observeRecentTracks(serverId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Track>> }
            .catch { emit(MellowResult.Error(it)) }

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

    override fun getFavoriteTracks(serverId: String): Flow<MellowResult<List<Track>>> =
        trackDao.getFavoriteTracks(serverId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Track>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getFavoriteAlbums(serverId: String): Flow<MellowResult<List<Album>>> =
        albumDao.getFavoriteAlbums(serverId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Album>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getFavoriteArtists(serverId: String): Flow<MellowResult<List<Artist>>> =
        artistDao.getFavoriteArtists(serverId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Artist>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getRecentlyPlayedAlbums(serverId: String): Flow<MellowResult<List<Album>>> =
        albumDao.getRecentlyPlayedAlbums(serverId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Album>> }
            .catch { emit(MellowResult.Error(it)) }

    override fun getMostPlayedAlbums(serverId: String): Flow<MellowResult<List<Album>>> =
        albumDao.getMostPlayedAlbums(serverId)
            .map { entities -> MellowResult.Success(entities.map { it.toModel() }) as MellowResult<List<Album>> }
            .catch { emit(MellowResult.Error(it)) }

    override suspend fun syncHomeScreenPriority(
        serverId: String,
        onProgress: (SyncProgress) -> Unit,
    ): MellowResult<Set<String>> {
        return try {
            val server = serverDao.getActiveServer() ?: return MellowResult.Success(emptySet())
            val userId = UUID.fromString(server.userId)
            val imageIds = mutableSetOf<String>()

            onProgress(SyncProgress("home", 0, 4))

            coroutineScope {
                val recentAlbums = async { jellyfinDataSource.getRecentlyAddedAlbums(userId, 50) }
                val recentTracks = async { jellyfinDataSource.getRecentlyPlayedItems(userId, 200) }
                val favAlbums = async { jellyfinDataSource.getFavoriteAlbums(userId) }
                val favTracks = async { jellyfinDataSource.getFavoriteTracks(userId) }

                val albums = recentAlbums.await()
                albumDao.upsertAlbums(albums.map { it.toAlbumEntity(serverId) })
                albums.forEach { it.id.toString().let(imageIds::add) }
                onProgress(SyncProgress("home", 1, 4))

                val tracks = recentTracks.await()
                trackDao.upsertTracks(tracks.map { it.toTrackEntity(serverId) })
                tracks.forEach { dto ->
                    dto.albumId?.toString()?.let(imageIds::add)
                }
                onProgress(SyncProgress("home", 2, 4))

                val fAlbums = favAlbums.await()
                albumDao.upsertAlbums(fAlbums.map { it.toAlbumEntity(serverId) })
                fAlbums.forEach { it.id.toString().let(imageIds::add) }
                onProgress(SyncProgress("home", 3, 4))

                val fTracks = favTracks.await()
                trackDao.upsertTracks(fTracks.map { it.toTrackEntity(serverId) })
                fTracks.forEach { dto ->
                    dto.albumId?.toString()?.let(imageIds::add)
                }
                onProgress(SyncProgress("home", 4, 4))
            }

            Log.d(TAG, "Home screen priority sync: ${imageIds.size} unique image IDs")
            MellowResult.Success(imageIds)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun syncLibrary(serverId: String, onProgress: (SyncProgress) -> Unit): MellowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return MellowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)

            val lastSyncMs = syncPreferences.lastSyncTimestamp.first()

            if (lastSyncMs == 0L) {
                Log.d(TAG, "First sync — full")
                fullSync(serverId, userId, onProgress)
            } else {
                val albumsOutdated = syncPreferences.albumRevision.first() < SyncPreferences.CURRENT_ALBUM_REVISION
                val artistsOutdated = syncPreferences.artistRevision.first() < SyncPreferences.CURRENT_ARTIST_REVISION
                val tracksOutdated = syncPreferences.trackRevision.first() < SyncPreferences.CURRENT_TRACK_REVISION

                if (albumsOutdated || artistsOutdated || tracksOutdated) {
                    Log.d(TAG, "Data revision outdated (albums=$albumsOutdated, artists=$artistsOutdated, tracks=$tracksOutdated)")
                }

                val since = LocalDateTime.ofInstant(Instant.ofEpochMilli(lastSyncMs), ZoneOffset.UTC)
                if (albumsOutdated) syncAllAlbums(serverId, userId, onProgress) else syncAlbumsIncremental(serverId, userId, since, onProgress)
                if (artistsOutdated) syncAllArtists(serverId, userId, onProgress) else syncArtistsIncremental(serverId, userId, since, onProgress)
                if (tracksOutdated) syncAllTracks(serverId, userId, onProgress) else syncTracksIncremental(serverId, userId, since, onProgress)
            }

            albumDao.resolveArtistIds(serverId)
            trackDao.resolveArtistIds(serverId)

            onProgress(SyncProgress("favorites", 0, 0))
            syncFavoritesDiff(serverId, userId)
            syncRecentlyPlayed(serverId, userId)

            syncPreferences.setLastSyncTimestamp(System.currentTimeMillis())
            syncPreferences.setAlbumRevision(SyncPreferences.CURRENT_ALBUM_REVISION)
            syncPreferences.setArtistRevision(SyncPreferences.CURRENT_ARTIST_REVISION)
            syncPreferences.setTrackRevision(SyncPreferences.CURRENT_TRACK_REVISION)
            syncPreferences.incrementSyncCount()
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun cleanupOrphans(serverId: String, onProgress: (SyncProgress) -> Unit): MellowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return MellowResult.Success(Unit)
            val userId = UUID.fromString(server.userId)

            onProgress(SyncProgress("albums", 0, 0))
            detectOrphanedAlbums(serverId, userId)
            onProgress(SyncProgress("artists", 0, 0))
            detectOrphanedArtists(serverId, userId)
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun syncFavorites(serverId: String): MellowResult<Unit> {
        return try {
            val server = serverDao.getActiveServer() ?: return MellowResult.Success(Unit)
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
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    private suspend fun fullSync(serverId: String, userId: UUID, onProgress: (SyncProgress) -> Unit) {
        syncAllAlbums(serverId, userId, onProgress)
        syncAllArtists(serverId, userId, onProgress)
        syncAllTracks(serverId, userId, onProgress)
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
}
