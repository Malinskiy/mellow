package dev.mellow.core.database.dao

import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistAliasEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.PlaylistEntity
import dev.mellow.core.database.entity.PlaylistTrackCrossRef
import dev.mellow.core.database.entity.TrackEntity

/** Rows of [SERVER] for the database tests, with neutral values for every column a test doesn't set. */
internal object TestEntities {
    const val SERVER = "server"

    fun track(
        id: String,
        serverId: String = SERVER,
        name: String = id,
        albumId: String? = null,
        albumName: String? = null,
        isFavorite: Boolean = false,
        playCount: Int = 0,
        dateAdded: Long = 0,
    ) = TrackEntity(
        id = id, serverId = serverId, name = name, sortName = name, albumId = albumId, albumName = albumName,
        artistId = null, artistName = null, trackNumber = null, discNumber = null, durationMs = 0,
        genres = emptyList(), imageTag = null, isFavorite = isFavorite, playCount = playCount, lastPlayedAt = 0,
        normalizationGain = null, container = null, codec = null, bitrate = null, sampleRate = null,
        channels = null, resolvedArtistId = null, dateAdded = dateAdded, lastSynced = 0,
    )

    fun album(
        id: String,
        name: String = id,
        year: Int? = null,
        genres: List<String> = emptyList(),
        artistId: String? = null,
        resolvedArtistId: String? = null,
        isFavorite: Boolean = false,
        dateAdded: Long = 0,
    ) = AlbumEntity(
        id = id, serverId = SERVER, name = name, sortName = name, artistId = artistId, artistName = null,
        year = year, trackCount = 0, genres = genres, imageTag = null, isFavorite = isFavorite,
        resolvedArtistId = resolvedArtistId, dateAdded = dateAdded, lastSynced = 0,
    )

    fun artist(id: String, name: String) = ArtistEntity(
        id = id, serverId = SERVER, name = name, sortName = name, albumCount = 0, imageTag = null,
        isFavorite = false, overview = null, genres = emptyList(), cleanName = name.lowercase(),
        musicBrainzId = null, lastSynced = 0,
    )

    fun alias(raw: String, canonical: String) = ArtistAliasEntity(SERVER, raw, canonical, 0)

    fun download(trackId: String, status: Int) = DownloadEntity(
        trackId = trackId, albumId = null, serverId = SERVER, status = status, progress = 0f,
        bytesDownloaded = 0, totalBytes = 0, quality = "original", filePath = null, requestedAt = 0,
        completedAt = 0, errorMessage = null, lastSynced = 0,
    )

    fun playlist(id: String) = PlaylistEntity(
        id = id, serverId = SERVER, name = id, sortName = id, trackCount = 0, durationMs = 0, imageTag = null,
        isFavorite = false, isLocal = false, lastSynced = 0,
    )

    fun crossRef(playlistId: String, trackId: String, position: Int) =
        PlaylistTrackCrossRef(playlistId = playlistId, trackId = trackId, position = position, addedAt = 0)
}
