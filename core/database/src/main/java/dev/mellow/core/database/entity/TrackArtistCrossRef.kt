package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index

@Entity(
    tableName = "track_artists",
    primaryKeys = ["trackId", "artistId"],
    foreignKeys = [
        ForeignKey(
            entity = TrackEntity::class,
            parentColumns = ["id"],
            childColumns = ["trackId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("artistId")],
)
data class TrackArtistCrossRef(
    val trackId: String,
    val artistId: String,
    val artistName: String,
    val displayOrder: Int,
)
