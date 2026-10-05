package dev.mellow.core.database.dao

import androidx.sqlite.db.SimpleSQLiteQuery
import dev.mellow.core.database.entity.DownloadEntity

/**
 * The Library's Tracks tab, in each [LibraryOrder]. One query per order, rather than one that picks its order with
 * CASE expressions: SQLite can't follow an index for an ORDER BY of CASE expressions, so every page sorted the whole
 * library. Each order here is an index of TrackEntity, read in order by the keyed pages and queue windows.
 *
 * Ties keep the newest first and the ID makes the order total, so pages never overlap or skip a track. Name Z to A is
 * the exact reverse of A to Z (equal names: oldest first), so it reads the same index backwards.
 *
 * Downloaded only starts from the completed downloads and sorts those, instead of walking every track of the library
 * to find the few that are downloaded. CROSS JOIN keeps SQLite from reordering the join back to the tracks.
 */
internal object LibraryTracksQuery {

    fun page(serverId: String, sort: Int, downloadedOnly: Boolean) =
        SimpleSQLiteQuery(sql(sort, downloadedOnly), args(serverId, downloadedOnly))

    internal fun sql(sort: Int, downloadedOnly: Boolean): String {
        return "SELECT t.* ${from(downloadedOnly)} WHERE ${where(downloadedOnly)} ORDER BY ${order(sort)}"
    }

    internal fun from(downloadedOnly: Boolean): String =
        if (downloadedOnly) {
            "FROM downloads d CROSS JOIN tracks t ON t.id = d.trackId"
        } else {
            "FROM tracks t"
        }

    internal fun where(downloadedOnly: Boolean): String =
        if (downloadedOnly) {
            "d.status = ${DownloadEntity.STATUS_COMPLETED} AND d.serverId = ? AND t.serverId = ?"
        } else {
            "t.serverId = ?"
        }

    internal fun args(serverId: String, downloadedOnly: Boolean): Array<Any> =
        if (downloadedOnly) arrayOf(serverId, serverId) else arrayOf(serverId)

    internal fun order(sort: Int): String = when (sort) {
        LibraryOrder.NAME_ASC -> "t.name ASC, t.dateAdded DESC, t.id ASC"
        LibraryOrder.NAME_DESC -> "t.name DESC, t.dateAdded ASC, t.id DESC"
        // By album name, Z to A; tracks without an album last (NULL sorts last in descending order).
        LibraryOrder.YEAR -> "t.albumName DESC, t.dateAdded DESC, t.id ASC"
        else -> "t.dateAdded DESC, t.id ASC"
    }
}
