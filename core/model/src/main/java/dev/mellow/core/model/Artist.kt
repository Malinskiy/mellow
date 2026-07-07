package dev.mellow.core.model

data class Artist(
    val id: String,
    val name: String,
    val albumCount: Int,
    val imageId: String?,
    val isFavorite: Boolean,
    val overview: String?,
    val genres: List<String>,
)
