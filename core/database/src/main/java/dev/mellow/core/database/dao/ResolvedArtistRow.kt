package dev.mellow.core.database.dao

/** A row's id and its locally derived canonical artist. */
data class ResolvedArtistRow(
    val id: String,
    val resolvedArtistId: String?,
)

/** Stays under SQLite's 999 bound parameters on older Android versions. */
internal const val BIND_LIMIT = 900
