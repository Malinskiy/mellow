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
import androidx.media3.common.Tracks
import dev.mellow.core.network.ConnectionState
import dev.mellow.core.network.NetworkStateObserver
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
import dev.mellow.core.database.dao.getTracksById
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.withContext
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
    val playbackCodec: String? = null,
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
    private val networkStateObserver: NetworkStateObserver,
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

    /** A track that became current without playing (a restored queue); reported as started once it plays. */
    private var unreportedTrackId: String? = null

    /** The in-flight [syncFromController] lookup; a newer sync replaces it. */
    private var syncJob: Job? = null

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
                val ctrl = future.get()
                controller = ctrl
                ctrl.addListener(playerListener)
                // The service may have a different queue than this object remembers: empty after it was restarted,
                // or a restored queue. Show what the player actually has.
                syncFromController(ctrl, reportStart = false)
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

        syncJob?.cancel()
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
            if (c.isPlaying) c.pause() else c.playPrepared()
        }
    }

    fun skipNext() { controller?.seekToNextMediaItem() }
    fun skipPrevious() { controller?.seekToPreviousMediaItem() }
    fun seekTo(positionMs: Long) { controller?.seekTo(positionMs) }

    fun playFromQueue(index: Int) {
        controller?.let { c ->
            if (index in 0 until c.mediaItemCount) {
                c.seekTo(index, 0L)
                c.playPrepared()
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
        resetState()
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

    /** Plays, preparing first if needed: a restored queue isn't prepared, and play() alone doesn't start it. */
    private fun Player.playPrepared() {
        if (playbackState == Player.STATE_IDLE && mediaItemCount > 0) prepare()
        play()
    }

    /** A track built from a media item's own metadata, shown until (or if not) the library has it. */
    private fun MediaItem.toPlaceholderTrack(): Track {
        val meta = mediaMetadata
        return Track(
            id = mediaId,
            name = meta.title?.toString() ?: "",
            albumId = null,
            albumName = meta.albumTitle?.toString(),
            artistId = null,
            artistName = meta.artist?.toString(),
            trackNumber = meta.trackNumber,
            discNumber = meta.discNumber,
            duration = Duration.ZERO,
            genres = emptyList(),
            imageId = null,
            isFavorite = false,
            playCount = 0,
            lastPlayedAt = 0L,
            normalizationGain = null,
        )
    }

    private fun resetState() {
        syncJob?.cancel()
        currentQueue = emptyList()
        unreportedTrackId = null
        _state.value = PlaybackState()
        _positionState.value = PositionState()
    }

    /**
     * Sets the state from what [ctrl] holds: cleared if the player is empty, otherwise its queue, current track,
     * position and modes. Reports the current track as started if [reportStart]; otherwise that happens once it plays.
     *
     * What the controller knows is shown right away; library details follow from a lookup, which is dropped if the
     * controller's queue has changed by the time it finishes.
     */
    private fun syncFromController(ctrl: MediaController, reportStart: Boolean) {
        if (ctrl.mediaItemCount == 0) {
            resetState()
            return
        }
        val idx = ctrl.currentMediaItemIndex
        val items = (0 until ctrl.mediaItemCount).map { ctrl.getMediaItemAt(it) }
        val mediaIds = items.map { it.mediaId }
        val current = items[idx]
        val positionMs = ctrl.currentPosition
        val playerDurationMs = ctrl.duration.coerceAtLeast(0L)
        // A track that is already playing was reported when it started; only a paused one is still pending.
        unreportedTrackId = if (!reportStart && !ctrl.playWhenReady) current.mediaId else null
        positionUpdateCount = 0
        // Until the lookup finishes, the queue is what the controller says, so transitions never match an old one.
        currentQueue = items.map { it.toPlaceholderTrack() }
        _state.value = _state.value.copy(
            currentTrack = _state.value.currentTrack?.takeIf { it.id == current.mediaId }
                ?: current.toPlaceholderTrack(),
            isPlaying = ctrl.isPlaying,
            currentIndex = idx,
            positionMs = positionMs,
            durationMs = playerDurationMs,
            shuffleEnabled = ctrl.shuffleModeEnabled,
            repeatMode = ctrl.repeatMode,
            error = null,
        )
        _positionState.value = PositionState(positionMs = positionMs, durationMs = playerDurationMs)

        syncJob?.cancel()
        syncJob = reportingScope.launch {
            val tracks = trackDao.getTracksById(mediaIds)
            // One entry per controller item, so indices match the player's.
            val queue = items.map { tracks[it.mediaId]?.toModel() ?: it.toPlaceholderTrack() }
            val applied = withContext(Dispatchers.Main) {
                if (controller !== ctrl || ctrl.currentMediaItemIndex != idx ||
                    (0 until ctrl.mediaItemCount).map { ctrl.getMediaItemAt(it).mediaId } != mediaIds
                ) {
                    return@withContext false
                }
                currentQueue = queue
                val track = queue[idx]
                // An unprepared (restored) player doesn't know the duration yet; the library does.
                val durationMs = ctrl.duration.takeIf { it > 0L } ?: track.duration.toMillis()
                _state.value = _state.value.copy(
                    currentTrack = track,
                    currentIndex = idx,
                    queue = queue,
                    durationMs = durationMs,
                )
                _positionState.value = _positionState.value.copy(durationMs = durationMs)
                true
            }
            if (applied && reportStart) playbackReporter.reportStarted(current.mediaId)
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
            val unreported = unreportedTrackId
            if (isPlaying && unreported != null && unreported == _state.value.currentTrack?.id) {
                unreportedTrackId = null
                reportingScope.launch { playbackReporter.reportStarted(unreported) }
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            val ctrl = controller ?: return
            val idx = ctrl.currentMediaItemIndex
            // Trust the remembered queue only if it still matches the player; Android Auto may have replaced it.
            val track = currentQueue.getOrNull(idx)?.takeIf { it.id == mediaItem?.mediaId }

            if (track != null) {
                _state.value = _state.value.copy(
                    currentTrack = track,
                    currentIndex = idx,
                    error = null,
                )
                positionUpdateCount = 0
                unreportedTrackId = null
                reportingScope.launch { playbackReporter.reportStarted(track.id) }
            } else if (mediaItem != null) {
                // A queue this object didn't set: from Android Auto, or restored by the service. A restored queue
                // is paused, so its track is reported once it actually plays.
                syncFromController(ctrl, reportStart = ctrl.playWhenReady)
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

        override fun onTracksChanged(tracks: Tracks) {
            val codec = tracks.groups
                .flatMap { group -> (0 until group.length).map { group.getTrackFormat(it) } }
                .firstOrNull { it.sampleMimeType?.startsWith("audio/") == true }
                ?.sampleMimeType
                ?.let { mimeToCodec(it) }
            _state.value = _state.value.copy(playbackCodec = codec)
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

            val isOffline = networkStateObserver.connectionState.value != ConnectionState.Connected
            if (isOffline) {
                controller?.stop()
            }

            val errorMessage = when {
                httpCode == 404 -> "Track no longer available"
                isOffline -> "No network connection"
                else -> "Playback error: ${error.localizedMessage}"
            }

            _state.value = _state.value.copy(
                isPlaying = false,
                error = errorMessage,
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
        // Whatever this showed may be gone by the next connect (the service can stop meanwhile); that connect
        // shows what the player has then.
        resetState()
    }

    private fun mimeToCodec(mime: String): String = when (mime) {
        "audio/mpeg" -> "mp3"
        "audio/flac" -> "flac"
        "audio/opus" -> "opus"
        "audio/mp4a-latm" -> "aac"
        "audio/vorbis" -> "vorbis"
        "audio/raw" -> "pcm"
        else -> mime.removePrefix("audio/")
    }

    companion object {
        private const val TAG = "MellowPlayer"
        private const val POSITION_UPDATE_INTERVAL_MS = 250L
        private const val PROGRESS_REPORT_INTERVAL = 40 // ~10s at 250ms intervals
    }
}
