package dev.mellow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.Index.Order.ASC
import androidx.room.Index.Order.DESC
import androidx.room.PrimaryKey

/** An album of a server's library. The Library tab indexes match [dateAdded], [name], and [year] keyset orders. */
@Entity(
    tableName = "albums",
    indices = [
        Index("artistName"),
        Index(value = ["serverId", "dateAdded", "sortName", "id"], orders = [ASC, DESC, ASC, ASC]),
        Index("serverId", "name", "sortName", "id"),
        Index(value = ["serverId", "year", "sortName", "id"], orders = [ASC, DESC, ASC, ASC]),
        Index("serverId", "sortName", "id"),
        Index("serverId", "isFavorite"),
        // The two arms of the canonical artist album count; artist-first also serves artist detail.
        Index("resolvedArtistId", "serverId"),
        Index("artistId", "serverId", "resolvedArtistId"),
    ],
)
data class AlbumEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val sortName: String,
    val artistId: String?,
    val artistName: String?,
    val year: Int?,
    val trackCount: Int,
    val genres: List<String>,
    val imageTag: String?,
    val isFavorite: Boolean,
    val resolvedArtistId: String?,
    val dateAdded: Long,
    val lastSynced: Long,
)
