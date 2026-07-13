package dev.mellow.core.player

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import dagger.hilt.android.qualifiers.ApplicationContext
import androidx.media3.datasource.HttpDataSource
import dev.mellow.core.common.PlaybackReporter
import dev.mellow.core.common.jellyfinStreamUrl
import dev.mellow.core.data.mapper.toModel
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.Duration
import javax.inject.Inject
import javax.inject.Singleton

data class PlaybackState(
    val isPlaying: Boolean = false,
    val isBuffering: Boolean = false,
    val currentTrack: Track? = null,
    val currentIndex: Int = 0,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
    val queue: List<Track> = emptyList(),
    val error: String? = null,
    val shuffleEnabled: Boolean = false,
    val repeatMode: Int = 0,
)

data class PositionState(
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

@Singleton
class MellowPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverDao: ServerDao,
    private val downloadDao: DownloadDao,
    private val trackDao: TrackDao,
    private val playbackReporter: PlaybackReporter,
) {
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(PlaybackState())
    val state: StateFlow<PlaybackState> = _state.asStateFlow()

    private val _positionState = MutableStateFlow(PositionState())
    val positionState: StateFlow<PositionState> = _positionState.asStateFlow()

    private val reportingScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private var serverUrl: String = ""
    private var apiKey: String = ""
    private var currentQueue: List<Track> = emptyList()
    private var downloadedTrackIds: Set<String> = emptySet()
    private var positionUpdateCount = 0

    private val handler = Handler(Looper.getMainLooper())
    private val positionUpdateRunnable = object : Runnable {
        override fun run() {
            controller?.let { c ->
                if (c.isPlaying) {
                    _positionState.value = PositionState(
                        positionMs = c.currentPosition,
                        durationMs = c.duration.coerceAtLeast(0L),
                    )
                    positionUpdateCount++
                    if (positionUpdateCount % PROGRESS_REPORT_INTERVAL == 0) {
                        val track = _state.value.currentTrack
                        val posMs = c.currentPosition
                        if (track != null) {
                            reportingScope.launch {
                                playbackReporter.reportProgress(track.id, posMs)
                            }
                        }
                    }
                }
            }
            handler.postDelayed(this, POSITION_UPDATE_INTERVAL_MS)
        }
    }

    fun connect() {
        val sessionToken = SessionToken(context, ComponentName(context, MellowMediaService::class.java))
        val future = MediaController.Builder(context, sessionToken).buildAsync()
        future.addListener({
            try {
                controller = future.get()
                controller?.addListener(playerListener)
                startPositionUpdates()
                Log.d(TAG, "MediaController connected")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to connect MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    suspend fun playTracks(tracks: List<Track>, startIndex: Int = 0) {
        val server = serverDao.getActiveServer() ?: run {
            Log.e(TAG, "No active server found")
            return
        }
        serverUrl = server.url
        apiKey = server.accessToken
        currentQueue = tracks

        downloadedTrackIds = downloadDao.getDownloadedTrackIds().toSet()

        val ctrl = controller ?: run {
            Log.e(TAG, "MediaController not connected, cannot play")
            _state.value = _state.value.copy(
                currentTrack = tracks.getOrNull(startIndex),
                queue = tracks,
                error = "Player not ready, try again",
            )
            return
        }

        val mediaItems = tracks.map { it.toMediaItem() }
        Log.d(TAG, "Playing ${mediaItems.size} tracks starting at $startIndex")
        Log.d(TAG, "Stream URL: ${mediaItems.getOrNull(startIndex)?.localConfiguration?.uri}")

        _state.value = _state.value.copy(
            currentTrack = tracks.getOrNull(startIndex),
            queue = tracks,
            error = null,
        )

        ctrl.setMediaItems(mediaItems, startIndex, 0L)
        ctrl.prepare()
        ctrl.play()
    }

    fun playPause() {
        controller?.let { c ->
            if (c.isPlaying) c.pause() else c.play()
        }
    }

    fun skipNext() { controller?.seekToNextMediaItem() }
    fun skipPrevious() { controller?.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun playFromQueue(index: Int) {
        controller?.let { c ->
            if (index in 0 until c.mediaItemCount) {
                c.seekTo(index, 0L)
                c.play()
            }
        }
    }

    fun addToQueue(track: Track) {
        val ctrl = controller ?: return
        val mediaItem = track.toMediaItem()
        ctrl.addMediaItem(mediaItem)
        currentQueue = currentQueue + track
        _state.value = _state.value.copy(queue = currentQueue)
    }

    fun playNext(track: Track) {
        val ctrl = controller ?: return
        val insertIndex = (ctrl.currentMediaItemIndex + 1).coerceAtMost(currentQueue.size)
        val mediaItem = track.toMediaItem()
        ctrl.addMediaItem(insertIndex, mediaItem)
        currentQueue = currentQueue.toMutableList().apply { add(insertIndex, track) }
        _state.value = _state.value.copy(queue = currentQueue)
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        val ctrl = controller ?: return
        if (fromIndex == toIndex) return
        if (fromIndex !in 0 until ctrl.mediaItemCount) return
        if (toIndex !in 0 until ctrl.mediaItemCount) return
        ctrl.moveMediaItem(fromIndex, toIndex)
        currentQueue = currentQueue.toMutableList().apply {
            val item = removeAt(fromIndex)
            add(toIndex, item)
        }
        val newIndex = ctrl.currentMediaItemIndex
        _state.value = _state.value.copy(queue = currentQueue, currentIndex = newIndex)
    }

    fun removeFromQueue(index: Int) {
        val ctrl = controller ?: return
        if (index !in 0 until ctrl.mediaItemCount) return
        ctrl.removeMediaItem(index)
        currentQueue = currentQueue.toMutableList().apply { removeAt(index) }
        val newIndex = ctrl.currentMediaItemIndex
        _state.value = _state.value.copy(
            queue = currentQueue,
            currentIndex = newIndex,
            currentTrack = currentQueue.getOrNull(newIndex),
        )
    }

    fun clearQueue() {
        val track = _state.value.currentTrack
        val pos = controller?.currentPosition ?: 0L
        controller?.let { c ->
            c.stop()
            c.clearMediaItems()
        }
        if (track != null) {
            reportingScope.launch { playbackReporter.reportStopped(track.id, pos) }
        }
        currentQueue = emptyList()
        _state.value = PlaybackState()
        _positionState.value = PositionState()
    }

    fun toggleShuffle() {
        controller?.let { c ->
            c.shuffleModeEnabled = !c.shuffleModeEnabled
        }
    }

    fun cycleRepeatMode() {
        controller?.let { c ->
            c.repeatMode = when (c.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                Player.REPEAT_MODE_ONE -> Player.REPEAT_MODE_OFF
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    private fun Track.toMediaItem(): MediaItem {
        val streamUri = Uri.parse(jellyfinStreamUrl(serverUrl, id, apiKey))
        val artItemId = albumId ?: id
        val artUri = Uri.parse("content://${context.packageName}.artwork/$artItemId")
        val isDownloaded = id in downloadedTrackIds

        if (isDownloaded) {
            Log.d(TAG, "Track $id ($name) available offline via download cache")
        }

        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(streamUri)
            .setCustomCacheKey(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistName)
                    .setAlbumTitle(albumName)
                    .setArtworkUri(artUri)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build()
            )
            .build()
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _state.value = _state.value.copy(isPlaying = isPlaying)
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = controller ?: return
            val idx = ctrl.currentMediaItemIndex
            val track = currentQueue.getOrNull(idx)

            if (track != null) {
                _state.value = _state.value.copy(
                    currentTrack = track,
                    currentIndex = idx,
                    error = null,
                )
                positionUpdateCount = 0
                reportingScope.launch { playbackReporter.reportStarted(track.id) }
            } else if (mediaItem != null) {
                reportingScope.launch {
                    val newQueue = (0 until ctrl.mediaItemCount).mapNotNull { i ->
                        trackDao.getTrackById(ctrl.getMediaItemAt(i).mediaId)?.toModel()
                    }
                    currentQueue = newQueue
                    val resolved = newQueue.getOrNull(idx)
                    _state.value = _state.value.copy(
                        currentTrack = resolved,
                        currentIndex = idx,
                        queue = newQueue,
                        error = null,
                    )
                    if (resolved != null) {
                        playbackReporter.reportStarted(resolved.id)
                    }
                }
            }
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            when (playbackState) {
                Player.STATE_BUFFERING -> {
                    _state.value = _state.value.copy(isBuffering = true)
                }
                Player.STATE_READY -> {
                    controller?.let { c ->
                        val dur = c.duration.coerceAtLeast(0L)
                        val pos = c.currentPosition
                        _state.value = _state.value.copy(
                            isBuffering = false,
                            durationMs = dur,
                            positionMs = pos,
                        )
                        _positionState.value = PositionState(
                            positionMs = pos,
                            durationMs = dur,
                        )
                    }
                }
                Player.STATE_ENDED -> {
                    val track = _state.value.currentTrack
                    val pos = controller?.currentPosition ?: 0L
                    _state.value = _state.value.copy(isPlaying = false, positionMs = 0L)
                    _positionState.value = PositionState(positionMs = 0L, durationMs = 0L)
                    if (track != null) {
                        reportingScope.launch { playbackReporter.reportStopped(track.id, pos) }
                    }
                }
                else -> {}
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            _state.value = _state.value.copy(shuffleEnabled = shuffleModeEnabled)
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            _state.value = _state.value.copy(repeatMode = repeatMode)
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e(TAG, "Playback error: ${error.errorCodeName}", error)

            val httpCode = extractHttpStatusCode(error)
            if (httpCode == 404) {
                val track = _state.value.currentTrack
                if (track != null) {
                    reportingScope.launch {
                        trackDao.deleteById(track.id)
                        Log.d(TAG, "Removed orphaned track: ${track.name} (404)")
                    }
                }
            }

            _state.value = _state.value.copy(
                isPlaying = false,
                error = if (httpCode == 404) "Track no longer available" else "Playback error: ${error.localizedMessage}",
            )
        }
    }

    private fun extractHttpStatusCode(error: PlaybackException): Int? {
        var cause: Throwable? = error.cause
        while (cause != null) {
            if (cause is HttpDataSource.InvalidResponseCodeException) {
                return cause.responseCode
            }
            cause = cause.cause
        }
        return null
    }

    private fun startPositionUpdates() {
        handler.removeCallbacks(positionUpdateRunnable)
        handler.post(positionUpdateRunnable)
    }

    fun release() {
        handler.removeCallbacks(positionUpdateRunnable)
        controller?.removeListener(playerListener)
        controller?.release()
        controller = null
    }

    companion object {
        private const val TAG = "MellowPlayer"
        private const val POSITION_UPDATE_INTERVAL_MS = 250L
        private const val PROGRESS_REPORT_INTERVAL = 40 // ~10s at 250ms intervals
    }
}
