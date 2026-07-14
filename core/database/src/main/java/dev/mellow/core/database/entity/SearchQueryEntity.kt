package dev.mellow.core.database.entity

import androidx.room.Entity

@Entity(
    tableName = "search_queries",
    primaryKeys = ["serverId", "queryText"],
)
data class SearchQueryEntity(
    val serverId: String,
    val queryText: String,
    val searchedAt: Long,
)
