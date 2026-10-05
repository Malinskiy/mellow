package dev.mellow.core.database.dao

import dev.mellow.core.database.converter.Converters
import dev.mellow.core.database.entity.DownloadEntity

/**
 * The Library's Albums tab, with one index-backed query per [LibraryOrder]. Name Z to A is the exact reverse of A to
 * Z; year uses SQLite's descending NULL-last order. These are visible changes from the old shared query, which kept
 * sort-name and ID ties ascending in every order and treated a missing year as zero.
 *
 * A genre remains a filter while SQLite walks the requested order index. Downloaded only instead starts from the
 * completed downloads, resolves their tracks, and de-duplicates the much smaller album set before sorting it.
 */
internal object LibraryAlbumsQuery {

    fun from(downloadedOnly: Boolean): String = if (downloadedOnly) {
        """
        FROM (
            SELECT DISTINCT t.albumId
            FROM downloads d INDEXED BY index_downloads_status_serverId_trackId
            CROSS JOIN tracks t ON t.id = d.trackId
            WHERE d.status = ${DownloadEntity.STATUS_COMPLETED}
                AND d.serverId = ? AND t.serverId = ? AND t.albumId IS NOT NULL
        ) downloaded
        CROSS JOIN albums a ON a.id = downloaded.albumId
        """.trimIndent()
    } else {
        "FROM albums a"
    }

    fun seekFrom(downloadedOnly: Boolean, sort: Int): String =
        if (downloadedOnly) from(true) else "FROM albums a INDEXED BY ${index(sort)}"

    fun where(genre: String?): String = buildString {
        append("a.serverId = ?")
        if (genre != null) {
            val separator = Converters.SEPARATOR
            append(" AND instr('$separator' || a.genres || '$separator', '$separator' || ? || '$separator') > 0")
        }
    }

    fun args(serverId: String, genre: String?, downloadedOnly: Boolean): List<Any> = buildList {
        if (downloadedOnly) {
            add(serverId)
            add(serverId)
        }
        add(serverId)
        if (genre != null) add(genre)
    }

    fun order(sort: Int): String = when (sort) {
        LibraryOrder.NAME_ASC -> "a.name ASC, a.sortName ASC, a.id ASC"
        LibraryOrder.NAME_DESC -> "a.name DESC, a.sortName DESC, a.id DESC"
        LibraryOrder.YEAR -> "a.year DESC, a.sortName ASC, a.id ASC"
        else -> "a.dateAdded DESC, a.sortName ASC, a.id ASC"
    }

    fun index(sort: Int): String = when (sort) {
        LibraryOrder.NAME_ASC, LibraryOrder.NAME_DESC -> "index_albums_serverId_name_sortName_id"
        LibraryOrder.YEAR -> "index_albums_serverId_year_sortName_id"
        else -> "index_albums_serverId_dateAdded_sortName_id"
    }
}
