package dev.mellow.core.data.mapper

import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.TrackEntity
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.jellyfin.sdk.model.api.ImageType
import org.jellyfin.sdk.model.api.MediaStreamType

fun BaseItemDto.toAlbumEntity(serverId: String): AlbumEntity = AlbumEntity(
    id = id.toString(),
    serverId = serverId,
    name = name.orEmpty(),
    sortName = sortName ?: name.orEmpty(),
    artistId = albumArtists?.firstOrNull()?.id?.toString(),
    artistName = albumArtist,
    year = productionYear,
    trackCount = childCount ?: 0,
    genres = genres.orEmpty(),
    imageTag = imageTags?.get(ImageType.PRIMARY),
    isFavorite = userData?.isFavorite ?: false,
    dateAdded = System.currentTimeMillis(),
    lastSynced = System.currentTimeMillis(),
)

fun BaseItemDto.toArtistEntity(serverId: String): ArtistEntity = ArtistEntity(
    id = id.toString(),
    serverId = serverId,
    name = name.orEmpty(),
    sortName = sortName ?: name.orEmpty(),
    albumCount = childCount ?: 0,
    imageTag = imageTags?.get(ImageType.PRIMARY),
    isFavorite = userData?.isFavorite ?: false,
    overview = overview,
    genres = genres.orEmpty(),
    lastSynced = System.currentTimeMillis(),
)

fun BaseItemDto.toTrackEntity(serverId: String): TrackEntity {
    val audioStream = mediaStreams?.firstOrNull { it.type == MediaStreamType.AUDIO }
    return TrackEntity(
        id = id.toString(),
        serverId = serverId,
        name = name.orEmpty(),
        sortName = sortName ?: name.orEmpty(),
        albumId = albumId?.toString(),
        albumName = album,
        artistId = artistItems?.firstOrNull()?.id?.toString(),
        artistName = artists?.firstOrNull(),
        trackNumber = indexNumber,
        discNumber = parentIndexNumber,
        durationMs = (runTimeTicks ?: 0L) / 10_000,
        genres = genres.orEmpty(),
        imageTag = imageTags?.get(ImageType.PRIMARY),
        isFavorite = userData?.isFavorite ?: false,
        playCount = userData?.playCount ?: 0,
        normalizationGain = normalizationGain?.toFloat(),
        container = container,
        codec = audioStream?.codec,
        bitrate = audioStream?.bitRate,
        sampleRate = audioStream?.sampleRate,
        channels = audioStream?.channels,
        dateAdded = System.currentTimeMillis(),
        lastSynced = System.currentTimeMillis(),
    )
}
