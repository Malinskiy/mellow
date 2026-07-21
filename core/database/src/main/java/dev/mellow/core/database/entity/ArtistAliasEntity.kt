package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.Index

@Entity(
    tableName = "artist_aliases",
    primaryKeys = ["serverId", "rawArtistId"],
    indices = [Index("serverId", "canonicalArtistId")],
)
data class ArtistAliasEntity(
    val serverId: String,
    val rawArtistId: String,
    val canonicalArtistId: String,
    val lastSynced: Long,
)
