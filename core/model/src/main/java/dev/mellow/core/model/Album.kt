package dev.mellow.core.model

data class Album(
    val id: String,
    val name: String,
    val artistId: String?,
    val artistName: String?,
    val year: Int?,
    val trackCount: Int,
    val genres: List<String>,
    val imageId: String?,
    val isFavorite: Boolean,
)
