package dev.mellow.core.database.dao

import android.database.Cursor
import androidx.paging.PagingSource
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.converter.Converters
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.TrackEntity
import dev.mellow.core.database.paging.KeysetColumn
import dev.mellow.core.database.paging.KeysetDirection
import dev.mellow.core.database.paging.KeysetPagingKey
import dev.mellow.core.database.paging.KeysetPagingSource
import dev.mellow.core.database.paging.KeysetQuery
import dev.mellow.core.database.paging.KeysetQueryRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Builds keyed track queries for the Library Tracks tab and Android Auto's Songs list. */
class TrackKeysetQueryFactory(private val database: MellowDatabase) {

    fun libraryPagingSource(
        serverId: String,
        sort: Int,
        downloadedOnly: Boolean,
    ): PagingSource<KeysetPagingKey, TrackEntity> =
        KeysetPagingSource(database, libraryQuery(serverId, sort, downloadedOnly))

    suspend fun libraryQueueWindow(
        serverId: String,
        sort: Int,
        downloadedOnly: Boolean,
        trackId: String,
        before: Int,
        size: Int,
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        KeysetQueryRunner(database, libraryQuery(serverId, sort, downloadedOnly))
            .loadWindowById(trackId, before, size)
            ?.items
            .orEmpty()
    }

    suspend fun autoQueueWindow(
        serverId: String,
        downloadedOnly: Boolean,
        trackId: String,
        before: Int,
        size: Int,
    ): List<TrackEntity> = withContext(Dispatchers.IO) {
        KeysetQueryRunner(database, autoQuery(serverId, downloadedOnly))
            .loadWindowById(trackId, before, size)
            ?.items
            .orEmpty()
    }

    suspend fun libraryTrackIdAtPosition(
        serverId: String,
        sort: Int,
        downloadedOnly: Boolean,
        position: Int,
    ): String? = withContext(Dispatchers.IO) {
        KeysetQueryRunner(database, libraryQuery(serverId, sort, downloadedOnly))
            .keyAt(position)
            ?.lastOrNull() as? String
    }

    suspend fun autoTrackIdAtPosition(serverId: String, downloadedOnly: Boolean, position: Int): String? =
        withContext(Dispatchers.IO) {
            KeysetQueryRunner(database, autoQuery(serverId, downloadedOnly)).keyAt(position)?.lastOrNull() as? String
        }

    suspend fun findAutoTrack(serverId: String, downloadedOnly: Boolean, trackId: String): TrackEntity? =
        withContext(Dispatchers.IO) {
            KeysetQueryRunner(database, autoQuery(serverId, downloadedOnly)).findById(trackId)
        }

    private fun libraryQuery(serverId: String, sort: Int, downloadedOnly: Boolean): KeysetQuery<TrackEntity> =
        KeysetQuery(
            select = "SELECT t.*",
            from = LibraryTracksQuery.from(downloadedOnly),
            seekFrom = seekFrom(downloadedOnly, libraryIndex(sort)),
            where = LibraryTracksQuery.where(downloadedOnly),
            arguments = LibraryTracksQuery.args(serverId, downloadedOnly).toList(),
            order = libraryOrder(sort),
            observedTables = arrayOf("tracks", "downloads"),
            mapRow = ::mapTrack,
        )

    private fun autoQuery(serverId: String, downloadedOnly: Boolean): KeysetQuery<TrackEntity> {
        val from = if (downloadedOnly) {
            "FROM downloads d CROSS JOIN tracks t ON t.id = d.trackId"
        } else {
            "FROM tracks t"
        }
        val where = if (downloadedOnly) {
            "d.status = ${DownloadEntity.STATUS_COMPLETED} AND d.serverId = ? AND t.serverId = ?"
        } else {
            "t.serverId = ?"
        }
        val arguments = if (downloadedOnly) listOf(serverId, serverId) else listOf(serverId)
        return KeysetQuery(
            select = "SELECT t.*",
            from = from,
            seekFrom = seekFrom(downloadedOnly, AUTO_INDEX),
            where = where,
            arguments = arguments,
            order = listOf(
                KeysetColumn("t.sortName", "sortName", KeysetDirection.ASCENDING, valueOf = TrackEntity::sortName),
                KeysetColumn("t.id", "id", KeysetDirection.ASCENDING, valueOf = TrackEntity::id),
            ),
            observedTables = arrayOf("tracks", "downloads"),
            mapRow = ::mapTrack,
        )
    }

    private fun libraryOrder(sort: Int): List<KeysetColumn<TrackEntity>> = when (sort) {
        LibraryOrder.NAME_ASC -> listOf(
            KeysetColumn("t.name", "name", KeysetDirection.ASCENDING, valueOf = TrackEntity::name),
            KeysetColumn("t.dateAdded", "dateAdded", KeysetDirection.DESCENDING, valueOf = TrackEntity::dateAdded),
            KeysetColumn("t.id", "id", KeysetDirection.ASCENDING, valueOf = TrackEntity::id),
        )
        LibraryOrder.NAME_DESC -> listOf(
            KeysetColumn("t.name", "name", KeysetDirection.DESCENDING, valueOf = TrackEntity::name),
            KeysetColumn("t.dateAdded", "dateAdded", KeysetDirection.ASCENDING, valueOf = TrackEntity::dateAdded),
            KeysetColumn("t.id", "id", KeysetDirection.DESCENDING, valueOf = TrackEntity::id),
        )
        LibraryOrder.YEAR -> listOf(
            KeysetColumn(
                "t.albumName",
                "albumName",
                KeysetDirection.DESCENDING,
                nullable = true,
                valueOf = TrackEntity::albumName,
            ),
            KeysetColumn("t.dateAdded", "dateAdded", KeysetDirection.DESCENDING, valueOf = TrackEntity::dateAdded),
            KeysetColumn("t.id", "id", KeysetDirection.ASCENDING, valueOf = TrackEntity::id),
        )
        else -> listOf(
            KeysetColumn("t.dateAdded", "dateAdded", KeysetDirection.DESCENDING, valueOf = TrackEntity::dateAdded),
            KeysetColumn("t.id", "id", KeysetDirection.ASCENDING, valueOf = TrackEntity::id),
        )
    }

    private fun seekFrom(downloadedOnly: Boolean, index: String): String =
        if (downloadedOnly) LibraryTracksQuery.from(true) else "FROM tracks t INDEXED BY $index"

    private fun libraryIndex(sort: Int): String = when (sort) {
        LibraryOrder.NAME_ASC, LibraryOrder.NAME_DESC -> "index_tracks_serverId_name_dateAdded_id"
        LibraryOrder.YEAR -> "index_tracks_serverId_albumName_dateAdded_id"
        else -> "index_tracks_serverId_dateAdded_id"
    }

    private companion object {
        const val AUTO_INDEX = "index_tracks_serverId_sortName_id"
    }
}

private fun mapTrack(cursor: Cursor): TrackEntity = TrackEntity(
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
