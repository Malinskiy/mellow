package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "albums")
data class AlbumEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val name: String,
    val sortName: String,
    val artistId: String?,
    val artistName: String?,
    val year: Int?,
    val trackCount: Int,
    val genres: List<String>,
    val imageTag: String?,
    val isFavorite: Boolean,
    val dateAdded: Long,
    val lastSynced: Long,
)
