package dev.mellow.core.database.dao

import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.entity.AlbumEntity
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * The [limit] most recently played albums of the server: those of its played tracks, ordered by each album's latest
 * play. Walks the played tracks from the latest play back and stops at [limit] albums. An album's latest play is
 * where it first turns up in that walk, so this is the order of grouping every played track by album and sorting by
 * each group's latest play, without reading the whole play history: that took a second on a million tracks with a
 * fifth of them played. Albums the library doesn't have (any more) are skipped.
 */
suspend fun AlbumDao.getRecentlyPlayedAlbums(serverId: String, limit: Int): List<AlbumEntity> {
    if (limit <= 0) return emptyList()
    val albums = ArrayList<AlbumEntity>(limit)
    val seen = HashSet<String>()
    var offset = 0
    while (albums.size < limit) {
        val batch = getPlayedAlbumIds(serverId, RECENTLY_PLAYED_BATCH, offset)
        offset += batch.size
        val newIds = batch.filter { seen.add(it) }
        if (newIds.isNotEmpty()) {
            val found = getAlbumsByIds(newIds).associateBy { it.id }
            newIds.mapNotNullTo(albums) { found[it] }
        }
        if (batch.size < RECENTLY_PLAYED_BATCH) break
    }
    return albums.take(limit)
}

/** [getRecentlyPlayedAlbums], again whenever tracks or albums change: for Home's Recently Played row. */
class RecentlyPlayedAlbumsObserver(private val database: MellowDatabase) {
    fun observe(serverId: String, limit: Int): Flow<List<AlbumEntity>> =
        database.invalidationTracker.createFlow("tracks", "albums")
            .map { database.albumDao().getRecentlyPlayedAlbums(serverId, limit) }
}

/** Played tracks read per step; most of the latest plays belong to few albums, so a step often finds them all. */
private const val RECENTLY_PLAYED_BATCH = 200
