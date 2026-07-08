package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "pending_playback_events")
data class PendingPlaybackEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val serverId: String,
    val trackId: String,
    val eventType: String,
    val positionMs: Long,
    val durationMs: Long,
    val timestamp: Long,
)
