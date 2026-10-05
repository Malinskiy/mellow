package dev.mellow.core.database.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** An artist of a server's library. Canonical artists page in name or sort-name order. */
@Entity(
    tableName = "artists",
    indices = [
        Index("serverId", "name", "sortName", "id"),
        Index("serverId", "sortName", "id"),
        Index("serverId", "isFavorite"),
    ],
)
data class ArtistEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val sortName: String,
    val albumCount: Int,
    val imageTag: String?,
    val isFavorite: Boolean,
    val overview: String?,
    val genres: List<String>,
    val cleanName: String,
    val musicBrainzId: String?,
    val lastSynced: Long,
)
