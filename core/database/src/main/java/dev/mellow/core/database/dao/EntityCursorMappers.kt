package dev.mellow.core.database.dao

import android.database.Cursor
import dev.mellow.core.database.converter.Converters
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.TrackEntity

internal fun mapAlbum(cursor: Cursor): AlbumEntity = AlbumEntity(
    id = cursor.string("id"),
    serverId = cursor.string("serverId"),
    name = cursor.string("name"),
    sortName = cursor.string("sortName"),
    artistId = cursor.nullableString("artistId"),
    artistName = cursor.nullableString("artistName"),
    year = cursor.nullableInt("year"),
    trackCount = cursor.int("trackCount"),
    genres = Converters().toStringList(cursor.string("genres")),
    imageTag = cursor.nullableString("imageTag"),
    isFavorite = cursor.int("isFavorite") != 0,
    resolvedArtistId = cursor.nullableString("resolvedArtistId"),
    dateAdded = cursor.long("dateAdded"),
    lastSynced = cursor.long("lastSynced"),
)

internal fun mapArtistWithAlbumCount(cursor: Cursor): ArtistWithAlbumCount = ArtistWithAlbumCount(
    artist = ArtistEntity(
        id = cursor.string("id"),
        serverId = cursor.string("serverId"),
        name = cursor.string("name"),
        sortName = cursor.string("sortName"),
        albumCount = cursor.int("albumCount"),
        imageTag = cursor.nullableString("imageTag"),
        isFavorite = cursor.int("isFavorite") != 0,
        overview = cursor.nullableString("overview"),
        genres = Converters().toStringList(cursor.string("genres")),
        cleanName = cursor.string("cleanName"),
        musicBrainzId = cursor.nullableString("musicBrainzId"),
        lastSynced = cursor.long("lastSynced"),
    ),
    localAlbumCount = cursor.int("localAlbumCount"),
)

internal fun mapTrack(cursor: Cursor): TrackEntity = TrackEntity(
    id = cursor.string("id"),
    serverId = cursor.string("serverId"),
    name = cursor.string("name"),
    sortName = cursor.string("sortName"),
    albumId = cursor.nullableString("albumId"),
    albumName = cursor.nullableString("albumName"),
    artistId = cursor.nullableString("artistId"),
    artistName = cursor.nullableString("artistName"),
    trackNumber = cursor.nullableInt("trackNumber"),
    discNumber = cursor.nullableInt("discNumber"),
    durationMs = cursor.long("durationMs"),
    genres = Converters().toStringList(cursor.string("genres")),
    imageTag = cursor.nullableString("imageTag"),
    isFavorite = cursor.int("isFavorite") != 0,
    playCount = cursor.int("playCount"),
    lastPlayedAt = cursor.long("lastPlayedAt"),
    normalizationGain = cursor.nullableFloat("normalizationGain"),
    container = cursor.nullableString("container"),
    codec = cursor.nullableString("codec"),
    bitrate = cursor.nullableInt("bitrate"),
    sampleRate = cursor.nullableInt("sampleRate"),
    channels = cursor.nullableInt("channels"),
    resolvedArtistId = cursor.nullableString("resolvedArtistId"),
    dateAdded = cursor.long("dateAdded"),
    lastSynced = cursor.long("lastSynced"),
)

private fun Cursor.index(column: String): Int = getColumnIndexOrThrow(column)
private fun Cursor.string(column: String): String = getString(index(column))
private fun Cursor.nullableString(column: String): String? =
    index(column).let { if (isNull(it)) null else getString(it) }
private fun Cursor.int(column: String): Int = getInt(index(column))
private fun Cursor.nullableInt(column: String): Int? = index(column).let { if (isNull(it)) null else getInt(it) }
private fun Cursor.long(column: String): Long = getLong(index(column))
private fun Cursor.nullableFloat(column: String): Float? = index(column).let { if (isNull(it)) null else getFloat(it) }
