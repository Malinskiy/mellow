package dev.mellow.core.model

import androidx.compose.runtime.Stable
import java.time.Duration

@Stable
data class Track(
    val id: String,
    val name: String,
    val albumId: String?,
    val albumName: String?,
    val artistId: String?,
    val resolvedArtistId: String? = null,
    val artistName: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val duration: Duration,
    val genres: List<String>,
    val imageId: String?,
    val isFavorite: Boolean,
    val playCount: Int,
    val lastPlayedAt: Long,
    val normalizationGain: Float?,
    val codec: String? = null,
    val container: String? = null,
    val dateAdded: Long = 0L,
)
