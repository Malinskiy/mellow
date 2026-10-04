package dev.mellow.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import dev.mellow.core.database.entity.SyncPassItemEntity
import dev.mellow.core.database.entity.SyncPassKind

/**
 * Bookkeeping for a full library pass: what it has seen on the server, and deleting what it hasn't.
 *
 * Rows that something on the device still needs are never deleted: tracks with a download, albums that a remaining
 * track belongs to, and artists that a remaining album or track links to.
 */
@Dao
interface SyncPassDao {

    @Query("DELETE FROM sync_pass_items")
    suspend fun clear()

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertItems(items: List<SyncPassItemEntity>)

    /** The number of distinct items recorded as [kind]. */
    @Query("SELECT COUNT(*) FROM sync_pass_items WHERE kind = :kind")
    suspend fun count(kind: String): Int

    /** This server's albums the pass hasn't seen. */
    @Query(
        """
        SELECT id FROM albums
        WHERE serverId = :serverId
        AND id NOT IN (SELECT itemId FROM sync_pass_items WHERE kind = '${SyncPassKind.ALBUM}')
        """,
    )
    suspend fun getUnseenAlbumIds(serverId: String): List<String>

    /** This server's tracks the pass hasn't seen, except downloaded ones, which are kept regardless. */
    @Query(
        """
        SELECT id FROM tracks
        WHERE serverId = :serverId
        AND id NOT IN (SELECT itemId FROM sync_pass_items WHERE kind = '${SyncPassKind.TRACK}')
        AND id NOT IN (SELECT trackId FROM downloads)
        """,
    )
    suspend fun getUnseenTrackIds(serverId: String): List<String>

    /** Deletes the tracks recorded as gone, unless one got a download meanwhile. Their artist links go with them. */
    @Query(
        """
        DELETE FROM tracks
        WHERE serverId = :serverId
        AND id IN (SELECT itemId FROM sync_pass_items WHERE kind = '${SyncPassKind.GONE_TRACK}')
        AND id NOT IN (SELECT trackId FROM downloads)
        """,
    )
    suspend fun deleteGoneTracks(serverId: String): Int

    /** Deletes the albums recorded as gone that no remaining track belongs to. Their artist links go with them. */
    @Query(
        """
        DELETE FROM albums
        WHERE serverId = :serverId
        AND id IN (SELECT itemId FROM sync_pass_items WHERE kind = '${SyncPassKind.GONE_ALBUM}')
        AND id NOT IN (SELECT albumId FROM tracks WHERE albumId IS NOT NULL)
        """,
    )
    suspend fun deleteGoneAlbums(serverId: String): Int

    /** Deletes this server's artists the pass hasn't seen that no remaining album or track links to. */
    @Query(
        """
        DELETE FROM artists
        WHERE serverId = :serverId
        AND id NOT IN (SELECT itemId FROM sync_pass_items WHERE kind = '${SyncPassKind.ARTIST}')
        AND id NOT IN (SELECT artistId FROM album_artists)
        AND id NOT IN (SELECT artistId FROM track_artists)
        AND id NOT IN (SELECT artistId FROM albums WHERE artistId IS NOT NULL)
        AND id NOT IN (SELECT artistId FROM tracks WHERE artistId IS NOT NULL)
        """,
    )
    suspend fun deleteUnseenArtists(serverId: String): Int

    /** Deletes this server's cached lyrics whose track no longer exists. */
    @Query("DELETE FROM lyrics WHERE serverId = :serverId AND trackId NOT IN (SELECT id FROM tracks)")
    suspend fun deleteOrphanedLyrics(serverId: String): Int
}

/** Records [ids] as [kind]. */
suspend fun SyncPassDao.mark(kind: String, ids: Collection<String>) {
    if (ids.isEmpty()) return
    insertItems(ids.map { SyncPassItemEntity(kind, it) })
}
