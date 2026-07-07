package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "artists")
data class ArtistEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val name: String,
    val sortName: String,
    val albumCount: Int,
    val imageTag: String?,
    val isFavorite: Boolean,
    val overview: String?,
    val genres: List<String>,
    val lastSynced: Long,
)
