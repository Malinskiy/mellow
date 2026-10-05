package dev.mellow.core.database.dao

/**
 * The Library's Artists tab. EXISTS returns one row per canonical artist without the old join and GROUP BY. The two
 * correlated counts are the two disjoint arms of COALESCE(resolvedArtistId, artistId): resolved albums first, then
 * unresolved albums credited to the raw artist. Both arms seek an album index for each artist.
 */
internal object LibraryArtistsQuery {

    fun select(downloadedOnly: Boolean): String =
        "SELECT a.*, (${albumCount(downloadedOnly)}) AS localAlbumCount"

    fun from(): String = "FROM artists a"

    fun seekFrom(downloadedOnly: Boolean, sort: Int): String =
        if (downloadedOnly) from() else "FROM artists a INDEXED BY ${index(sort)}"

    fun where(downloadedOnly: Boolean): String = buildString {
        append("a.serverId = ?")
        append(
            " AND EXISTS (SELECT 1 FROM artist_aliases aa " +
                "WHERE aa.serverId = a.serverId AND aa.canonicalArtistId = a.id)",
        )
        if (downloadedOnly) append(" AND a.id IN ($DOWNLOADED_ARTIST_IDS)")
    }

    fun order(sort: Int): String = when (sort) {
        LibraryOrder.NAME_ASC -> "a.name ASC, a.sortName ASC, a.id ASC"
        LibraryOrder.NAME_DESC -> "a.name DESC, a.sortName DESC, a.id DESC"
        else -> "a.sortName ASC, a.id ASC"
    }

    fun index(sort: Int): String = when (sort) {
        LibraryOrder.NAME_ASC, LibraryOrder.NAME_DESC -> "index_artists_serverId_name_sortName_id"
        else -> "index_artists_serverId_sortName_id"
    }

    private fun albumCount(downloadedOnly: Boolean): String {
        val downloadedFilter = if (downloadedOnly) " AND counted.id IN ($DOWNLOADED_ALBUM_IDS)" else ""
        return """
            (SELECT COUNT(*) FROM albums counted INDEXED BY index_albums_resolvedArtistId_serverId
             WHERE counted.resolvedArtistId = a.id AND counted.serverId = a.serverId$downloadedFilter)
            +
            (SELECT COUNT(*) FROM albums counted INDEXED BY index_albums_artistId_serverId_resolvedArtistId
             WHERE counted.artistId = a.id AND counted.serverId = a.serverId
                 AND counted.resolvedArtistId IS NULL$downloadedFilter)
        """.trimIndent()
    }
}
