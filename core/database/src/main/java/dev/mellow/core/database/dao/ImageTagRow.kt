package dev.mellow.core.database.dao

/** An item's id and the tag of its primary image, which changes whenever the image does. */
data class ImageTagRow(
    val id: String,
    val imageTag: String,
)
