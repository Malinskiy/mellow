package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.paging.KeysetColumn
import dev.mellow.core.database.paging.KeysetDirection
import dev.mellow.core.database.paging.KeysetPagingKey
import dev.mellow.core.database.paging.KeysetPagingSource
import dev.mellow.core.database.paging.KeysetQuery

/** Builds keyed canonical-artist queries for the Library Artists tab. */
class ArtistKeysetQueryFactory(private val database: MellowDatabase) {

    fun libraryPagingSource(
        serverId: String,
        sort: Int,
        downloadedOnly: Boolean,
    ): PagingSource<KeysetPagingKey, ArtistWithAlbumCount> = KeysetPagingSource(
        database,
        KeysetQuery(
            select = LibraryArtistsQuery.select(downloadedOnly),
            from = LibraryArtistsQuery.from(),
            seekFrom = LibraryArtistsQuery.seekFrom(downloadedOnly, sort),
            where = LibraryArtistsQuery.where(downloadedOnly),
            arguments = listOf(serverId),
            order = order(sort),
            observedTables = arrayOf("artists", "artist_aliases", "albums", "tracks", "track_artists", "downloads"),
            mapRow = ::mapArtistWithAlbumCount,
        ),
    )

    private fun order(sort: Int): List<KeysetColumn<ArtistWithAlbumCount>> = when (sort) {
        LibraryOrder.NAME_ASC -> listOf(
            artistColumn("a.name", "name", KeysetDirection.ASCENDING) { it.artist.name },
            artistColumn("a.sortName", "sortName", KeysetDirection.ASCENDING) { it.artist.sortName },
            artistColumn("a.id", "id", KeysetDirection.ASCENDING) { it.artist.id },
        )
        LibraryOrder.NAME_DESC -> listOf(
            artistColumn("a.name", "name", KeysetDirection.DESCENDING) { it.artist.name },
            artistColumn("a.sortName", "sortName", KeysetDirection.DESCENDING) { it.artist.sortName },
            artistColumn("a.id", "id", KeysetDirection.DESCENDING) { it.artist.id },
        )
        else -> listOf(
            artistColumn("a.sortName", "sortName", KeysetDirection.ASCENDING) { it.artist.sortName },
            artistColumn("a.id", "id", KeysetDirection.ASCENDING) { it.artist.id },
        )
    }

    private fun artistColumn(
        expression: String,
        resultColumn: String,
        direction: KeysetDirection,
        valueOf: (ArtistWithAlbumCount) -> Any,
    ) = KeysetColumn(expression, resultColumn, direction, valueOf = valueOf)
}
