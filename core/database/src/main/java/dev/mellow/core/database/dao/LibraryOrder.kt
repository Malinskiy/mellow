package dev.mellow.core.database.dao

/** The orders of the library's paged lists, passed as the `sort` argument of their queries. */
object LibraryOrder {
    /** Newest additions first; artists in sort-name order. */
    const val RECENTLY_ADDED = 0

    /** By name, A to Z, ignoring case. */
    const val NAME_ASC = 1

    /** By name, Z to A, ignoring case. */
    const val NAME_DESC = 2

    /** Albums by release year, newest first; tracks by album name, Z to A; artists in sort-name order. */
    const val YEAR = 3
}

/** IDs of the tracks downloaded for offline playback. */
internal const val DOWNLOADED_TRACK_IDS = "SELECT trackId FROM downloads WHERE status = 2"

/** IDs of the albums with at least one downloaded track. */
internal const val DOWNLOADED_ALBUM_IDS =
    "SELECT t.albumId FROM tracks t INNER JOIN downloads d ON t.id = d.trackId " +
        "WHERE d.status = 2 AND t.albumId IS NOT NULL"

/** Names of the artists with at least one downloaded track. */
internal const val DOWNLOADED_ARTIST_NAMES =
    "SELECT t.artistName FROM tracks t INNER JOIN downloads d ON t.id = d.trackId " +
        "WHERE d.status = 2 AND t.artistName IS NOT NULL"
