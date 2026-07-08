package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val name: String,
    val sortName: String,
    val trackCount: Int,
    val durationMs: Long,
    val imageTag: String?,
    val isFavorite: Boolean,
    val isLocal: Boolean,
    val lastSynced: Long,
)
