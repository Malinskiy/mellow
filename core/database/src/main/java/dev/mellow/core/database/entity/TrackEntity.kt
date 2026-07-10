package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "tracks", indices = [Index("artistName")])
data class TrackEntity(
    @PrimaryKey val id: String,
    val serverId: String,
    val name: String,
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
    val dateAdded: Long,
    val lastSynced: Long,
)
