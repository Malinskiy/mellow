package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.paging.KeysetColumn
import dev.mellow.core.database.paging.KeysetDirection
import dev.mellow.core.database.paging.KeysetPagingKey
import dev.mellow.core.database.paging.KeysetPagingSource
import dev.mellow.core.database.paging.KeysetQuery

/** Builds keyed album queries for the Library Albums tab. */
class AlbumKeysetQueryFactory(private val database: MellowDatabase) {

    fun libraryPagingSource(
        serverId: String,
        sort: Int,
        genre: String?,
        downloadedOnly: Boolean,
    ): PagingSource<KeysetPagingKey, AlbumEntity> = KeysetPagingSource(
        database,
        KeysetQuery(
            select = "SELECT a.*",
            from = LibraryAlbumsQuery.from(downloadedOnly),
            seekFrom = LibraryAlbumsQuery.seekFrom(downloadedOnly, sort),
            where = LibraryAlbumsQuery.where(genre),
            arguments = LibraryAlbumsQuery.args(serverId, genre, downloadedOnly),
            order = order(sort),
            observedTables = arrayOf("albums", "tracks", "downloads"),
            mapRow = ::mapAlbum,
        ),
    )

    private fun order(sort: Int): List<KeysetColumn<AlbumEntity>> = when (sort) {
        LibraryOrder.NAME_ASC -> listOf(
            KeysetColumn("a.name", "name", KeysetDirection.ASCENDING, valueOf = AlbumEntity::name),
            KeysetColumn("a.sortName", "sortName", KeysetDirection.ASCENDING, valueOf = AlbumEntity::sortName),
            KeysetColumn("a.id", "id", KeysetDirection.ASCENDING, valueOf = AlbumEntity::id),
        )
        LibraryOrder.NAME_DESC -> listOf(
            KeysetColumn("a.name", "name", KeysetDirection.DESCENDING, valueOf = AlbumEntity::name),
            KeysetColumn("a.sortName", "sortName", KeysetDirection.DESCENDING, valueOf = AlbumEntity::sortName),
            KeysetColumn("a.id", "id", KeysetDirection.DESCENDING, valueOf = AlbumEntity::id),
        )
        LibraryOrder.YEAR -> listOf(
            KeysetColumn(
                "a.year",
                "year",
                KeysetDirection.DESCENDING,
                nullable = true,
                valueOf = AlbumEntity::year,
            ),
            KeysetColumn("a.sortName", "sortName", KeysetDirection.ASCENDING, valueOf = AlbumEntity::sortName),
            KeysetColumn("a.id", "id", KeysetDirection.ASCENDING, valueOf = AlbumEntity::id),
        )
        else -> listOf(
            KeysetColumn("a.dateAdded", "dateAdded", KeysetDirection.DESCENDING, valueOf = AlbumEntity::dateAdded),
            KeysetColumn("a.sortName", "sortName", KeysetDirection.ASCENDING, valueOf = AlbumEntity::sortName),
            KeysetColumn("a.id", "id", KeysetDirection.ASCENDING, valueOf = AlbumEntity::id),
        )
    }
}
