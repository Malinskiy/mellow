package dev.mellow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Index.Order.ASC
import androidx.room.Index.Order.DESC
import androidx.room.PrimaryKey

/**
 * A track of a server's library. Its indexes each match a query that would otherwise read every track; the
 * million-track benchmark (QueryBenchmark) and QueryPlanTest keep them in use.
 */
@Entity(
    tableName = "tracks",
    indices = [
        Index("artistName"),
        // The Tracks tab, one per order (LibraryTracksQuery). Name Z to A reads the A to Z index backwards.
        Index(value = ["serverId", "dateAdded", "id"], orders = [ASC, DESC, ASC]),
        Index(value = ["serverId", "name", "dateAdded", "id"], orders = [ASC, ASC, DESC, ASC]),
        Index(value = ["serverId", "albumName", "dateAdded", "id"], orders = [ASC, DESC, DESC, ASC]),
        // Android Auto's Songs list, and a song's position in it.
        Index("serverId", "sortName", "id"),
        // An album's tracks, and an artist's top tracks.
        Index("albumId", "discNumber", "trackNumber", "id"),
        Index(value = ["resolvedArtistId", "playCount"], orders = [ASC, DESC]),
        // Home's played rows (albumId: Recently Played albums read only this index), and the favorites (in the order
        // they were saved: rowid, the last column of every index).
        Index("serverId", "lastPlayedAt", "albumId"),
        Index("serverId", "playCount"),
        Index("serverId", "isFavorite"),
    ],
)
data class TrackEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    // Sorts ignoring (ASCII) case, as the name orders always have, so their index can do the sorting.
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val sortName: String,
    val albumId: String?,
    val albumName: String?,
    val artistId: String?,
    val artistName: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val durationMs: Long,
    val genres: List<String>,
    val imageTag: String?,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long,
    val normalizationGain: Float?,
    val container: String?,
    val codec: String?,
    val bitrate: Int?,
    val sampleRate: Int?,
    val channels: Int?,
    val resolvedArtistId: String?,
    val dateAdded: Long,
    val lastSynced: Long,
)
