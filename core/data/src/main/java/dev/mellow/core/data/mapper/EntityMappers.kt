package dev.mellow.core.data.mapper

import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.PlaylistEntity
import dev.mellow.core.database.entity.TrackEntity
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Playlist
import dev.mellow.core.model.Track
import java.time.Duration

fun AlbumEntity.toModel() = Album(
    id = id,
    name = name,
    artistId = artistId,
    artistName = artistName,
    year = year,
    trackCount = trackCount,
    genres = genres,
    imageId = imageTag?.let { id },
    isFavorite = isFavorite,
    dateAdded = dateAdded,
)

fun ArtistEntity.toModel() = Artist(
    id = id,
    name = name,
    albumCount = albumCount,
    imageId = imageTag?.let { id },
    isFavorite = isFavorite,
    overview = overview,
    genres = genres,
)

fun TrackEntity.toModel() = Track(
    id = id,
    name = name,
    albumId = albumId,
    albumName = albumName,
    artistId = artistId,
    artistName = artistName,
    trackNumber = trackNumber,
    discNumber = discNumber,
    duration = Duration.ofMillis(durationMs),
    genres = genres,
    imageId = imageTag?.let { id },
    isFavorite = isFavorite,
    playCount = playCount,
    lastPlayedAt = lastPlayedAt,
    normalizationGain = normalizationGain,
    codec = codec,
    container = container,
    dateAdded = dateAdded,
)

fun PlaylistEntity.toModel() = Playlist(
    id = id,
    name = name,
    trackCount = trackCount,
    durationMs = durationMs,
    imageId = imageTag?.let { id },
    isFavorite = isFavorite,
    isLocal = isLocal,
)
