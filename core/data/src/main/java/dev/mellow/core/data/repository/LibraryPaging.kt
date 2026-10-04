package dev.mellow.core.data.repository

import androidx.paging.PagingConfig
import dev.mellow.core.database.dao.LibraryOrder
import dev.mellow.core.model.LibrarySort

/**
 * Paging for the library's lists. Pages scrolled far away are dropped, so a list of any length keeps at most
 * [PagingConfig.maxSize] rows in memory.
 */
internal val LIBRARY_PAGING_CONFIG = PagingConfig(pageSize = 60, maxSize = 600)

internal fun LibrarySort.toOrder(): Int = when (this) {
    LibrarySort.RecentlyAdded -> LibraryOrder.RECENTLY_ADDED
    LibrarySort.NameAscending -> LibraryOrder.NAME_ASC
    LibrarySort.NameDescending -> LibraryOrder.NAME_DESC
    LibrarySort.Year -> LibraryOrder.YEAR
}

/**
 * The [limit] genres on the most albums, most first, given each distinct genre list with its number of albums. Ties
 * keep the order the genres first appear in.
 */
internal fun topGenres(genreListCounts: List<Pair<List<String>, Int>>, limit: Int): List<String> {
    val albumsPerGenre = LinkedHashMap<String, Int>()
    for ((genres, albums) in genreListCounts) {
        for (genre in genres) albumsPerGenre[genre] = (albumsPerGenre[genre] ?: 0) + albums
    }
    return albumsPerGenre.entries.sortedByDescending { it.value }.take(limit).map { it.key }
}
