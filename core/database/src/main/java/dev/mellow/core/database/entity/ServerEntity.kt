package dev.mellow.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "servers")
data class ServerEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val userId: String,
    val accessToken: String,
    val isActive: Boolean,
    val lastConnected: Long,
)
