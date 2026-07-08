package dev.mellow.core.data.repository

import android.util.Log
import dev.mellow.core.data.mapper.toAlbumEntity
import dev.mellow.core.data.mapper.toArtistEntity
import dev.mellow.core.data.mapper.toModel
import dev.mellow.core.data.mapper.toTrackEntity
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import dev.mellow.core.network.datasource.JellyfinDataSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class LibraryRepositoryImpl @Inject constructor(
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val trackDao: TrackDao,
    private val serverDao: ServerDao,
    private val jellyfinDataSource: JellyfinDataSource,
) : LibraryRepository {

    companion object {
        private const val TAG = "LibraryRepository"
    }

    override fun getAlbums(serverId: String): Flow<List<Album>> =
        albumDao.observeAlbumsByServer(serverId).map { entities -> entities.map { it.toModel() } }

    override fun getArtists(serverId: String): Flow<List<Artist>> =
        artistDao.observeArtistsByServer(serverId).map { entities -> entities.map { it.toModel() } }

    override fun getAlbumTracks(albumId: String): Flow<List<Track>> =
        trackDao.getTracksByAlbum(albumId).map { entities -> entities.map { it.toModel() } }

    override fun getArtistAlbums(artistId: String): Flow<List<Album>> =
        albumDao.getAlbumsByArtist(artistId).map { entities -> entities.map { it.toModel() } }

    override suspend fun getAlbum(albumId: String): Album? =
        albumDao.getAlbumById(albumId)?.toModel()

    override fun observeAlbum(albumId: String): Flow<Album?> =
        albumDao.observeAlbumById(albumId).map { it?.toModel() }

    override suspend fun getArtist(artistId: String): Artist? =
        artistDao.getArtistById(artistId)?.toModel()

    override fun observeArtist(artistId: String): Flow<Artist?> =
        artistDao.observeArtistById(artistId).map { it?.toModel() }

    override fun getArtistTracks(artistId: String): Flow<List<Track>> =
        trackDao.getTracksByArtist(artistId).map { entities -> entities.map { it.toModel() } }

    override fun getRecentTracks(serverId: String): Flow<List<Track>> =
        trackDao.observeRecentTracks(serverId).map { entities -> entities.map { it.toModel() } }

    override suspend fun search(serverId: String, query: String): List<Track> =
        trackDao.search(serverId, query).map { it.toModel() }

    override suspend fun searchAlbums(serverId: String, query: String): List<Album> =
        albumDao.search(serverId, query).map { it.toModel() }

    override suspend fun searchArtists(serverId: String, query: String): List<Artist> =
        artistDao.search(serverId, query).map { it.toModel() }

    override fun getFavoriteTracks(serverId: String): Flow<List<Track>> =
        trackDao.getFavoriteTracks(serverId).map { entities -> entities.map { it.toModel() } }

    override fun getFavoriteAlbums(serverId: String): Flow<List<Album>> =
        albumDao.getFavoriteAlbums(serverId).map { entities -> entities.map { it.toModel() } }

    override fun getFavoriteArtists(serverId: String): Flow<List<Artist>> =
        artistDao.getFavoriteArtists(serverId).map { entities -> entities.map { it.toModel() } }

    override suspend fun syncLibrary(serverId: String) {
        val server = serverDao.getActiveServer() ?: return
        val userId = UUID.fromString(server.userId)
        syncAlbums(serverId, userId)
        syncArtists(serverId, userId)
        syncTracks(serverId, userId)
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

    private suspend fun syncAlbums(serverId: String, userId: UUID) {
        var startIndex = 0
        val pageSize = 200
        while (true) {
            val items = jellyfinDataSource.getAlbums(userId, startIndex, pageSize)
            if (items.isEmpty()) break
            albumDao.upsertAlbums(items.map { it.toAlbumEntity(serverId) })
            if (items.size < pageSize) break
            startIndex += pageSize
        }
    }

    private suspend fun syncArtists(serverId: String, userId: UUID) {
        var startIndex = 0
        val pageSize = 200
        while (true) {
            val items = jellyfinDataSource.getArtists(userId, startIndex, pageSize)
            if (items.isEmpty()) break
            artistDao.upsertArtists(items.map { it.toArtistEntity(serverId) })
            if (items.size < pageSize) break
            startIndex += pageSize
        }
    }

    private suspend fun syncTracks(serverId: String, userId: UUID) {
        var startIndex = 0
        val pageSize = 500
        while (true) {
            val items = jellyfinDataSource.getTracks(userId, startIndex, pageSize)
            if (items.isEmpty()) break
            trackDao.upsertTracks(items.map { it.toTrackEntity(serverId) })
            if (items.size < pageSize) break
            startIndex += pageSize
        }
    }
}
