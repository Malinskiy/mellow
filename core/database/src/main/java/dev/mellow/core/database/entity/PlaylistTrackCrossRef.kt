package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "playlist_tracks",
    primaryKeys = ["playlistId", "trackId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaylistEntity::class,
            parentColumns = ["id"],
            childColumns = ["playlistId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("trackId")],
)
data class PlaylistTrackCrossRef(
    val playlistId: String,
    val trackId: String,
    val position: Int,
    val addedAt: Long,
)
