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

/**
 * IDs of the artists with at least one downloaded track: each downloaded track's artist and every artist it credits,
 * as the canonical artist the aliases merge them into. By identity, not by name: a track's artist name can differ
 * from its artist's ("The Beatles" for "Beatles"), and a track by several artists names them all at once.
 */
internal const val DOWNLOADED_ARTIST_IDS =
    "SELECT COALESCE(dt.resolvedArtistId, dalias.canonicalArtistId, dt.artistId) FROM tracks dt " +
        "INNER JOIN downloads dd ON dt.id = dd.trackId " +
        "LEFT JOIN artist_aliases dalias ON dalias.serverId = dt.serverId AND dalias.rawArtistId = dt.artistId " +
        "WHERE dd.status = 2 " +
        "UNION " +
        "SELECT COALESCE(dalias.canonicalArtistId, dta.artistId) FROM track_artists dta " +
        "INNER JOIN downloads dd ON dta.trackId = dd.trackId " +
        "INNER JOIN tracks dt ON dt.id = dta.trackId " +
        "LEFT JOIN artist_aliases dalias ON dalias.serverId = dt.serverId AND dalias.rawArtistId = dta.artistId " +
        "WHERE dd.status = 2"

/**
 * Whether the artist row `artists` (named so in the query) has a downloaded track: compared as its canonical artist,
 * so a favorite that's an alias still matches.
 */
internal const val ARTIST_IS_DOWNLOADED =
    "COALESCE((SELECT canonicalArtistId FROM artist_aliases " +
        "WHERE serverId = artists.serverId AND rawArtistId = artists.id), artists.id) " +
        "IN ($DOWNLOADED_ARTIST_IDS)"
