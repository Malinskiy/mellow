package dev.mellow.core.database.entity

import androidx.room.Entity

/**
 * An item a full library pass has seen on the server, or found to be gone from it. The pass deletes what it didn't see
 * with set-based statements over this table. Holds one pass at a time: emptied when a pass starts and when it ends.
 *
 * Kept apart from the library tables so the other code paths that save artists, albums and tracks (home screen,
 * favorites, playlists, instant mixes) can't clear a mark the running pass made.
 */
@Entity(tableName = "sync_pass_items", primaryKeys = ["kind", "itemId"])
data class SyncPassItemEntity(
    val kind: String,
    val itemId: String,
)

/** What a [SyncPassItemEntity] records. */
object SyncPassKind {
    const val ARTIST = "artist"
    const val ALBUM = "album"
    const val TRACK = "track"

    /** An album the pass didn't see and the server says no longer exists. */
    const val GONE_ALBUM = "gone_album"

    /** A track the pass didn't see and the server says no longer exists. */
    const val GONE_TRACK = "gone_track"
}
