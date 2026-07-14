package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "lyrics")
data class LyricsEntity(
    @PrimaryKey val trackId: String,
    val serverId: String,
    val lyricsData: String,
    val lastSynced: Long,
)
