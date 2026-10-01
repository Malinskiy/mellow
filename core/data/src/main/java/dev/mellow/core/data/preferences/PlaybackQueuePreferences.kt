package dev.mellow.core.data.preferences

import android.content.Context
import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import javax.inject.Inject
import javax.inject.Singleton

// Two stores: the queue (large, changes rarely) and the position (small, saved often), so saving the
// position doesn't rewrite the whole queue.
private val Context.playbackQueueDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback_queue",
)
private val Context.playbackPositionDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback_position",
)

/** The last play queue, so it can be restored after the app process or the playback service is gone. */
data class SavedQueue(
    val serverId: String,
    val trackIds: List<String>,
    val index: Int,
    val positionMs: Long,
    val shuffleEnabled: Boolean,
    val repeatMode: Int,
)

/** A position in the queue, tagged with the revision of the queue it belongs to. */
data class SavedPosition(val revision: Long, val index: Int, val positionMs: Long)

/**
 * The position to restore: the latest saved position if it belongs to the saved queue, otherwise the one saved with
 * the queue. They differ when the process died between writing a new queue and its position.
 */
fun pickSavedPosition(queueRevision: Long, savedWithQueue: SavedPosition, latest: SavedPosition?): SavedPosition =
    if (latest != null && latest.revision == queueRevision) latest else savedWithQueue

/**
 * Stores the play queue. Saves are fire-and-forget and run one at a time in the order they were made, across
 * playback service instances; [load] waits for earlier saves.
 */
@Singleton
class PlaybackQueuePreferences @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val queueStore = context.playbackQueueDataStore
    private val positionStore = context.playbackPositionDataStore
    private val executor = SerialExecutor(CoroutineScope(SupervisorJob() + Dispatchers.IO)) { e ->
        Log.e(TAG, "Play queue storage failed", e)
    }

    suspend fun load(): SavedQueue? = executor.call {
        val queue = queueStore.data.first()
        val serverId = queue[SERVER_ID] ?: return@call null
        val trackIds = queue[TRACK_IDS]?.split(SEPARATOR)?.filter { it.isNotEmpty() }.orEmpty()
        if (trackIds.isEmpty()) return@call null
        val queueRevision = queue[REVISION] ?: 0L
        val position = positionStore.data.first()
        val latest = position[REVISION]?.let {
            SavedPosition(it, position[INDEX] ?: 0, position[POSITION_MS] ?: 0L)
        }
        val picked = pickSavedPosition(
            queueRevision = queueRevision,
            savedWithQueue = SavedPosition(queueRevision, queue[INDEX] ?: 0, queue[POSITION_MS] ?: 0L),
            latest = latest,
        )
        SavedQueue(
            serverId = serverId,
            trackIds = trackIds,
            index = picked.index,
            positionMs = picked.positionMs,
            shuffleEnabled = queue[SHUFFLE] ?: false,
            repeatMode = queue[REPEAT_MODE] ?: 0,
        )
    }

    /** Saves a new queue of tracks from the server [serverId]. */
    fun saveQueue(
        serverId: String,
        trackIds: List<String>,
        index: Int,
        positionMs: Long,
        shuffleEnabled: Boolean,
        repeatMode: Int,
    ) = executor.submit {
        var revision = 0L
        queueStore.edit {
            revision = (it[REVISION] ?: 0L) + 1
            it[REVISION] = revision
            it[SERVER_ID] = serverId
            it[TRACK_IDS] = trackIds.joinToString(SEPARATOR)
            it[SHUFFLE] = shuffleEnabled
            it[REPEAT_MODE] = repeatMode
            it[INDEX] = index
            it[POSITION_MS] = positionMs
        }
        writePosition(revision, index, positionMs)
    }

    fun savePosition(index: Int, positionMs: Long) = executor.submit {
        val revision = queueStore.data.first()[REVISION] ?: return@submit
        writePosition(revision, index, positionMs)
    }

    fun clear() = executor.submit {
        queueStore.edit { it.clear() }
        positionStore.edit { it.clear() }
    }

    private suspend fun writePosition(revision: Long, index: Int, positionMs: Long) {
        positionStore.edit {
            it[REVISION] = revision
            it[INDEX] = index
            it[POSITION_MS] = positionMs
        }
    }

    private companion object {
        const val TAG = "PlaybackQueuePrefs"

        // Jellyfin item IDs are hex GUIDs, so a comma never appears inside one.
        const val SEPARATOR = ","
        val REVISION = longPreferencesKey("revision")
        val SERVER_ID = stringPreferencesKey("server_id")
        val TRACK_IDS = stringPreferencesKey("track_ids")
        val SHUFFLE = booleanPreferencesKey("shuffle")
        val REPEAT_MODE = intPreferencesKey("repeat_mode")
        val INDEX = intPreferencesKey("index")
        val POSITION_MS = longPreferencesKey("position_ms")
    }
}
