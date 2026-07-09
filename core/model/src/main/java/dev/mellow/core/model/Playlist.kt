package dev.mellow.core.model

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val durationMs: Long,
    val imageId: String?,
    val isFavorite: Boolean,
    val isLocal: Boolean,
)
