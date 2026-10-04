package dev.mellow.core.player

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionError
import androidx.media3.session.SimpleBitmapLoader
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.mellow.core.common.QUEUE_WINDOW_SIZE
import dev.mellow.core.common.jellyfinStreamUrl
import dev.mellow.core.common.queueWindowStart
import dev.mellow.core.data.preferences.PlaybackQueuePreferences
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.dao.getTracksById
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.PlaylistEntity
import dev.mellow.core.database.entity.TrackEntity
import dev.mellow.core.network.ConnectionState
import dev.mellow.core.network.JellyfinClientWrapper
import dev.mellow.core.network.NetworkStateObserver
import dev.mellow.core.player.cache.MellowDataSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

@AndroidEntryPoint
class MellowMediaService : MediaLibraryService() {

    @Inject lateinit var dataSourceFactory: MellowDataSourceFactory
    @Inject lateinit var serverDao: ServerDao
    @Inject lateinit var albumDao: AlbumDao
    @Inject lateinit var artistDao: ArtistDao
    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var playlistDao: PlaylistDao
    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var networkStateObserver: NetworkStateObserver
    @Inject lateinit var jellyfinClientWrapper: JellyfinClientWrapper
    @Inject lateinit var queuePreferences: PlaybackQueuePreferences

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val handler = Handler(Looper.getMainLooper())

    /**
     * The server the player's tracks come from, kept current so a queue is tagged with its server when it changes
     * rather than whenever the save runs.
     */
    @Volatile private var activeServerId: String? = null
    private val savePositionPeriodically = object : Runnable {
        override fun run() {
            savePosition()
            handler.postDelayed(this, POSITION_SAVE_INTERVAL_MS)
        }
    }

    override fun onCreate() {
        super.onCreate()
        if (mediaLibrarySession != null) return

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val cacheDataSourceFactory = dataSourceFactory.createPlaybackDataSourceFactory()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        player = exoPlayer

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                Log.e(TAG, "ExoPlayer error: ${error.errorCodeName}", error)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                val stateName = when (playbackState) {
                    Player.STATE_IDLE -> "IDLE"
                    Player.STATE_BUFFERING -> "BUFFERING"
                    Player.STATE_READY -> "READY"
                    Player.STATE_ENDED -> "ENDED"
                    else -> "UNKNOWN($playbackState)"
                }
                Log.d(TAG, "Playback state: $stateName, items=${exoPlayer.mediaItemCount}")
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                Log.d(TAG, "Media item transition: id=${mediaItem?.mediaId}, uri=${mediaItem?.localConfiguration?.uri}")
            }
        })

        exoPlayer.addListener(queuePersistenceListener)

        val bitmapLoader = CacheBitmapLoader(ContentBitmapLoader(this))

        // The session sees errors with readable messages; the service keeps using exoPlayer directly.
        mediaLibrarySession = MediaLibrarySession.Builder(this, FriendlyErrorPlayer(exoPlayer, this), LibrarySessionCallback())
            .setBitmapLoader(bitmapLoader)
            .build()

        Log.d(TAG, "MediaLibrarySession created")

        serviceScope.launch {
            if (!jellyfinClientWrapper.isConnected) {
                val server = serverDao.getActiveServer() ?: run {
                    Log.w(TAG, "No active server in Room, skipping session restore")
                    return@launch
                }
                jellyfinClientWrapper.restoreSession(server.url, server.accessToken)
                Log.d(TAG, "Jellyfin session restored for ${server.url}")
                networkStateObserver.refresh()
            }
        }

        serviceScope.launch { serverDao.observeActiveServer().collect { activeServerId = it?.id } }
    }

    /** Saves the play queue as it changes, so [restoreQueue] can bring it back in a new service. */
    private val queuePersistenceListener = object : Player.Listener {
        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            if (reason == Player.TIMELINE_CHANGE_REASON_PLAYLIST_CHANGED) saveQueue()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = savePosition()

        override fun onPositionDiscontinuity(
            oldPosition: Player.PositionInfo,
            newPosition: Player.PositionInfo,
            reason: Int,
        ) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK) savePosition()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            handler.removeCallbacks(savePositionPeriodically)
            if (isPlaying) {
                handler.postDelayed(savePositionPeriodically, POSITION_SAVE_INTERVAL_MS)
            } else {
                savePosition()
            }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = saveQueue()

        override fun onRepeatModeChanged(repeatMode: Int) = saveQueue()
    }

    private fun saveQueue() {
        val exoPlayer = player ?: return
        if (exoPlayer.mediaItemCount == 0) {
            queuePreferences.clear()
            return
        }
        val serverId = activeServerId ?: return
        queuePreferences.saveQueue(
            serverId = serverId,
            trackIds = (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it).mediaId },
            index = exoPlayer.currentMediaItemIndex,
            positionMs = exoPlayer.currentPosition,
            shuffleEnabled = exoPlayer.shuffleModeEnabled,
            repeatMode = exoPlayer.repeatMode,
        )
    }

    private fun savePosition() {
        val exoPlayer = player ?: return
        if (exoPlayer.mediaItemCount == 0) return
        queuePreferences.savePosition(exoPlayer.currentMediaItemIndex, exoPlayer.currentPosition)
    }

    /** A saved queue ready to put back into the player. */
    private class RestorableQueue(
        val serverId: String,
        val items: List<MediaItem>,
        val startIndex: Int,
        val startPositionMs: Long,
        val shuffleEnabled: Boolean,
        val repeatMode: Int,
    )

    /**
     * Loads the saved queue, keeping only tracks of its own server that are still in the library. `null` if there is
     * nothing to restore, or the user has since logged out or switched servers.
     */
    private suspend fun loadRestorableQueue(): RestorableQueue? {
        val saved = try {
            queuePreferences.load()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load the saved play queue", e)
            null
        } ?: return null
        if (serverDao.getActiveServer()?.id != saved.serverId) return null

        val tracks = trackDao.getTracksById(saved.trackIds).filterValues { it.serverId == saved.serverId }
        val plan = planQueueRestore(saved.trackIds.size, saved.index, saved.positionMs) {
            saved.trackIds[it] in tracks
        } ?: return null
        val items = enrichMediaItems(
            plan.keptIndices.map { MediaItem.Builder().setMediaId(saved.trackIds[it]).build() },
        )
        // A track removed in the meantime has no stream URI; skip restoring rather than queue an unplayable item.
        if (items.any { it.localConfiguration == null }) return null
        // The user may have logged out or switched servers while this was loading.
        if (serverDao.getActiveServer()?.id != saved.serverId) return null
        return RestorableQueue(
            serverId = saved.serverId,
            items = items,
            startIndex = plan.startIndex,
            startPositionMs = plan.startPositionMs,
            shuffleEnabled = saved.shuffleEnabled,
            repeatMode = saved.repeatMode,
        )
    }

    /**
     * Puts the saved queue back into an empty [exoPlayer], paused at the saved track and position, so the app can
     * show it. The player isn't prepared, so nothing is fetched until playback starts.
     *
     * Only done for the app's own UI (see `onPostConnect`). Other callers that start the service, such as System UI
     * probing for its resume card or Android Auto browsing, get the queue through `onPlaybackResumption` when they
     * press play; restoring for them would post a media notification for a player nobody is using.
     */
    private suspend fun restoreQueue(exoPlayer: ExoPlayer) {
        val queue = loadRestorableQueue() ?: return
        withContext(Dispatchers.Main) {
            // The player may have been released, or given a queue by the app, Android Auto or a playback
            // resumption, meanwhile.
            if (player !== exoPlayer || exoPlayer.mediaItemCount > 0) return@withContext
            activeServerId = queue.serverId
            exoPlayer.shuffleModeEnabled = queue.shuffleEnabled
            exoPlayer.repeatMode = queue.repeatMode
            exoPlayer.setMediaItems(queue.items, queue.startIndex, queue.startPositionMs)
            Log.i(TAG, "Restored a queue of ${queue.items.size} tracks at ${queue.startIndex}")
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            savePosition()
            stopSelf()
        }
    }

    override fun onDestroy() {
        handler.removeCallbacks(savePositionPeriodically)
        savePosition()
        serviceScope.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        player = null
        super.onDestroy()
    }

    /**
     * Keeps only items the player can stream. If none are left, tells [controller] why and fails: Media3 ignores a
     * failed request, so without the error Android Auto would wait on "Getting your selection…" indefinitely.
     */
    private fun playableOrError(
        controller: MediaSession.ControllerInfo,
        items: List<MediaItem>,
        startIndex: Int,
        startPositionMs: Long,
    ): MediaSession.MediaItemsWithStartPosition {
        val playable = items.filter { it.localConfiguration != null }
        if (playable.isEmpty()) {
            Log.w(TAG, "Nothing playable among ${items.size} items for ${controller.packageName}")
            val error = SessionError(SessionError.ERROR_NOT_SUPPORTED, getString(R.string.playback_error_nothing_playable))
            mediaLibrarySession?.let { session -> handler.post { session.sendError(controller, error) } }
            throw UnsupportedOperationException("Nothing playable")
        }
        if (playable.size < items.size) {
            Log.w(TAG, "Dropped ${items.size - playable.size} items without a stream")
        }
        val (start, position) = remapStart(items.map { it.localConfiguration != null }, startIndex, startPositionMs)
        return MediaSession.MediaItemsWithStartPosition(playable, start, position)
    }

    private fun <T> asyncFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                Log.e(TAG, "asyncFuture failed", e)
                future.setException(e)
            }
        }
        return future
    }

    private fun artworkUri(itemId: String): Uri =
        Uri.parse("content://${packageName}.artwork/$itemId")

    private fun drawableUri(resId: Int): Uri =
        Uri.parse("android.resource://$packageName/$resId")

    private fun AlbumEntity.toBrowsableItem(groupTitle: String? = null): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM)
            groupTitle?.let { putString(CONTENT_STYLE_GROUP_TITLE, it) }
        }
        val artUri = if (imageTag != null) artworkUri(id) else drawableUri(R.drawable.ic_aa_albums)
        return MediaItem.Builder()
            .setMediaId("album:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistName)
                    .setSubtitle(artistName?.let { "Album \u2022 $it" } ?: "Album")
                    .setArtworkUri(artUri)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                    .apply { year?.let { setReleaseYear(it) } }
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun ArtistEntity.toBrowsableItem(groupTitle: String? = null): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_GRID_ITEM)
            groupTitle?.let { putString(CONTENT_STYLE_GROUP_TITLE, it) }
        }
        return MediaItem.Builder()
            .setMediaId("artist:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle("Artist")
                    .setArtworkUri(if (imageTag != null) artworkUri(id) else drawableUri(R.drawable.ic_aa_artists))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun PlaylistEntity.toBrowsableItem(): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM)
        }
        val subtitle = if (trackCount > 0) "Playlist \u2022 $trackCount tracks" else "Playlist"
        val artUri = if (imageTag != null) artworkUri(id) else drawableUri(R.drawable.ic_aa_playlists)
        return MediaItem.Builder()
            .setMediaId("playlist:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle(subtitle)
                    .setArtworkUri(artUri)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_PLAYLIST)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun TrackEntity.toPlayableItem(
        groupTitle: String? = null,
        parentId: String? = null,
    ): MediaItem {
        val extras = Bundle().apply {
            groupTitle?.let { putString(CONTENT_STYLE_GROUP_TITLE, it) }
            putString(EXTRA_PARENT_ID, parentId ?: albumId?.let { "album:$it" } ?: "")
        }
        return MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistName)
                    .setAlbumTitle(albumName)
                    .setArtworkUri(artworkUri(albumId ?: id))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .apply { trackNumber?.let { setTrackNumber(it) } }
                    .apply { discNumber?.let { setDiscNumber(it) } }
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .setExtras(extras)
                    .build()
            )
            .build()
    }

    private fun AlbumEntity.toPlayablePreview(): MediaItem {
        val artUri = if (imageTag != null) artworkUri(id) else drawableUri(R.drawable.ic_aa_albums)
        return MediaItem.Builder()
            .setMediaId("album:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle(artistName?.let { "Album \u2022 $it" } ?: "Album")
                    .setArtworkUri(artUri)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
    }

    private fun ArtistEntity.toPlayablePreview(): MediaItem =
        MediaItem.Builder()
            .setMediaId("artist:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle("Artist")
                    .setArtworkUri(if (imageTag != null) artworkUri(id) else drawableUri(R.drawable.ic_aa_artists))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private suspend fun enrichMediaItems(mediaItems: List<MediaItem>): List<MediaItem> {
        val server = serverDao.getActiveServer()
        if (server == null) {
            Log.w(TAG, "enrichMediaItems: no active server, returning ${mediaItems.size} unenriched items")
            return mediaItems
        }
        val serverUrl = server.url
        val apiKey = server.accessToken

        var enrichedCount = 0
        var missCount = 0
        val tracksById = trackDao.getTracksById(
            mediaItems.filter { it.localConfiguration == null }.map { it.mediaId },
        )
        val result = mediaItems.map { item ->
            val trackId = item.mediaId
            if (item.localConfiguration != null) {
                enrichedCount++
                return@map item
            }

            val track = tracksById[trackId]
            if (track != null) {
                enrichedCount++
                MediaItem.Builder()
                    .setMediaId(trackId)
                    .setUri(Uri.parse(jellyfinStreamUrl(serverUrl, trackId, apiKey)))
                    .setCustomCacheKey(trackId)
                    .setMediaMetadata(
                        MediaMetadata.Builder()
                            .setTitle(track.name)
                            .setArtist(track.artistName)
                            .setAlbumTitle(track.albumName)
                            .setArtworkUri(artworkUri(track.albumId ?: trackId))
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                            .setIsPlayable(true)
                            .setIsBrowsable(false)
                            .build()
                    )
                    .build()
            } else {
                missCount++
                Log.w(TAG, "enrichMediaItems: track not found in Room: $trackId")
                item
            }
        }
        Log.d(TAG, "enrichMediaItems: ${result.size} items, $enrichedCount enriched, $missCount not found")
        return result
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onSetMediaItems(
            session: MediaSession,
            browser: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            Log.d(TAG, "onSetMediaItems: ${mediaItems.size} items, startIndex=$startIndex, " +
                "ids=${mediaItems.map { it.mediaId }}, " +
                "controller=${browser.packageName}")
            return asyncFuture {
                if (mediaItems.size == 1) {
                    val mediaId = mediaItems[0].mediaId
                    Log.d(TAG, "onSetMediaItems: resolving single item mediaId=$mediaId")

                    if (mediaId.startsWith("album:")) {
                        val albumId = mediaId.removePrefix("album:")
                        val tracks = trackDao.getTracksByAlbumSync(albumId)
                        Log.d(TAG, "onSetMediaItems: album:$albumId → ${tracks.size} tracks")
                        if (tracks.isNotEmpty()) {
                            val enriched = enrichMediaItems(tracks.map {
                                it.toPlayableItem(parentId = "album:$albumId")
                            })
                            Log.d(TAG, "onSetMediaItems: resolved album, returning ${enriched.size} items")
                            return@asyncFuture playableOrError(browser, enriched, 0, startPositionMs)
                        }
                    }
                    if (mediaId.startsWith("artist:")) {
                        // Same as the app's artist Play All: the artist's top tracks by resolved artist, which also
                        // covers artists that only appear on other artists' albums.
                        val artistId = mediaId.removePrefix("artist:")
                        val tracks = trackDao.getTracksByResolvedArtistSync(artistId)
                        Log.d(TAG, "onSetMediaItems: artist:$artistId → ${tracks.size} tracks")
                        return@asyncFuture playableOrError(
                            browser,
                            enrichMediaItems(tracks.map { it.toPlayableItem(parentId = "artist:$artistId") }),
                            0,
                            startPositionMs,
                        )
                    }

                    val trackId = mediaId
                    val parentId = mediaItems[0].mediaMetadata.extras?.getString(EXTRA_PARENT_ID)
                    val track = trackDao.getTrackById(trackId)
                    val serverId = serverDao.getActiveServer()?.id ?: ""
                    Log.d(TAG, "onSetMediaItems: track lookup id=$trackId, found=${track != null}, parentId=$parentId, serverId=$serverId")

                    // A playlist, the favorites or the library can be too long to queue whole: queue the tracks
                    // around this one instead, as the app does.
                    val siblings = when {
                        parentId?.startsWith("playlist:") == true -> {
                            val plId = parentId.removePrefix("playlist:")
                            val position = playlistDao.countPlaylistTracksBefore(plId, trackId)
                            val start = queueWindowStart(position, playlistDao.countPlaylistTracks(plId))
                            playlistDao.getPlaylistTracksSlice(
                                plId,
                                downloadedOnly = false,
                                limit = QUEUE_WINDOW_SIZE,
                                offset = start,
                            )
                        }
                        parentId == FAV_TRACKS -> {
                            val position = trackDao.countFavoriteTracksBefore(serverId, trackId)
                            val start = queueWindowStart(position, trackDao.countFavoriteTracks(serverId, false))
                            trackDao.getFavoriteTracksSlice(
                                serverId,
                                downloadedOnly = false,
                                limit = QUEUE_WINDOW_SIZE,
                                offset = start,
                            )
                        }
                        parentId == LIBRARY_SONGS -> {
                            val position = track?.let { trackDao.countTracksBefore(serverId, it.sortName, it.id) } ?: 0
                            val start = queueWindowStart(position, trackDao.countTracks(serverId))
                            trackDao.getTracksByServerPaged(
                                serverId,
                                downloadedOnly = false,
                                limit = QUEUE_WINDOW_SIZE,
                                offset = start,
                            )
                        }
                        parentId?.startsWith("album:") == true -> {
                            val aId = parentId.removePrefix("album:")
                            trackDao.getTracksByAlbumSync(aId)
                        }
                        track?.albumId != null -> {
                            trackDao.getTracksByAlbumSync(track.albumId!!)
                        }
                        else -> null
                    }
                    Log.d(TAG, "onSetMediaItems: siblings=${siblings?.size}, source=${
                        when {
                            parentId?.startsWith("playlist:") == true -> "playlist"
                            parentId == FAV_TRACKS -> "fav_tracks"
                            parentId == LIBRARY_SONGS -> "library_songs"
                            parentId?.startsWith("album:") == true -> "album_parent"
                            track?.albumId != null -> "album_fallback"
                            else -> "none"
                        }
                    }")

                    if (!siblings.isNullOrEmpty()) {
                        val enriched = enrichMediaItems(siblings.map {
                            it.toPlayableItem(parentId = parentId)
                        })
                        val idx = siblings.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                        Log.d(TAG, "onSetMediaItems: returning ${enriched.size} sibling items, startIdx=$idx")
                        playableOrError(browser, enriched, idx, startPositionMs)
                    } else {
                        val enriched = enrichMediaItems(mediaItems)
                        Log.d(TAG, "onSetMediaItems: no siblings, returning ${enriched.size} single items")
                        playableOrError(browser, enriched, startIndex, startPositionMs)
                    }
                } else {
                    Log.d(TAG, "onSetMediaItems: multi-item (${mediaItems.size}), enriching directly")
                    playableOrError(browser, enrichMediaItems(mediaItems), startIndex, startPositionMs)
                }
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            Log.d(TAG, "onAddMediaItems: ${mediaItems.size} items, ids=${mediaItems.map { it.mediaId }}")
            // Items that couldn't be resolved to a stream would make the player fail; leave them out.
            return asyncFuture { enrichMediaItems(mediaItems).filter { it.localConfiguration != null } }
        }

        override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
            if (controller.packageName == packageName && !session.isMediaNotificationController(controller)) {
                player?.let { exoPlayer -> serviceScope.launch { restoreQueue(exoPlayer) } }
            }
        }

        /**
         * Play was requested while the player has no current item: from a headset or Bluetooth button (via
         * MediaButtonReceiver) after the app was gone, from the system's media controls after a reboot, or from a
         * controller such as Android Auto. Media3 sets the returned queue and starts playback; System UI also uses it
         * to show the last-played track on its resumption card.
         */
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            val future = SettableFuture.create<MediaSession.MediaItemsWithStartPosition>()
            serviceScope.launch {
                val queue = try {
                    loadRestorableQueue()
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to load the queue to resume", e)
                    null
                }
                // Complete on the main thread, where Media3 applies the result in the same turn. If the player got
                // a queue meanwhile (the app restored it, or Android Auto picked something), answer with that queue
                // instead of overwriting it with the saved one.
                withContext(Dispatchers.Main) {
                    val exoPlayer = player
                    when {
                        exoPlayer == null -> future.setException(IllegalStateException("The player was released"))
                        exoPlayer.mediaItemCount > 0 -> future.set(
                            MediaSession.MediaItemsWithStartPosition(
                                (0 until exoPlayer.mediaItemCount).map { exoPlayer.getMediaItemAt(it) },
                                exoPlayer.currentMediaItemIndex,
                                exoPlayer.currentPosition,
                            ),
                        )
                        queue == null -> future.setException(UnsupportedOperationException("No play queue to resume"))
                        else -> {
                            activeServerId = queue.serverId
                            exoPlayer.shuffleModeEnabled = queue.shuffleEnabled
                            exoPlayer.repeatMode = queue.repeatMode
                            Log.i(
                                TAG,
                                "Resuming a queue of ${queue.items.size} tracks at ${queue.startIndex} " +
                                    "for ${controller.packageName}",
                            )
                            future.set(
                                MediaSession.MediaItemsWithStartPosition(
                                    queue.items, queue.startIndex, queue.startPositionMs,
                                ),
                            )
                        }
                    }
                }
            }
            return future
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val rootExtras = Bundle().apply {
                putInt(CONTENT_STYLE_BROWSABLE_HINT, CONTENT_STYLE_LIST_ITEM)
                putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_GRID_ITEM)
            }
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Mellow")
                        .setExtras(rootExtras)
                        .build()
                )
                .build()
            val rootParams = LibraryParams.Builder().setExtras(rootExtras).build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, rootParams))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            // Lists are read a page at a time, never whole: Android Auto asks for page 0 of Int.MAX_VALUE items,
            // so every page is also capped at AA_MAX_ITEMS.
            val window = BrowseWindow.of(page, pageSize, AA_MAX_ITEMS)
                ?: return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            val syncChildren = when (parentId) {
                ROOT_ID -> rootChildren()
                TAB_LIBRARY -> libraryChildren()
                else -> null
            }
            if (syncChildren != null) {
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.copyOf(syncChildren.page(window)), params)
                )
            }

            return asyncFuture {
                val server = serverDao.getActiveServer()
                val serverId = server?.id ?: ""
                val isOnline = networkStateObserver.connectionState.value == ConnectionState.Connected
                // Offline, only downloads can play. Long lists filter in their queries; short ones filter here.
                val downloadedOnly = !isOnline
                val dlTrackIds = if (!isOnline) downloadDao.getDownloadedTrackIds().toSet() else null
                val dlAlbumIds = if (!isOnline) downloadDao.getDownloadedAlbumIds().toSet() else null

                fun List<TrackEntity>.onlineFilter() =
                    if (dlTrackIds != null) filter { it.id in dlTrackIds } else this
                fun List<AlbumEntity>.onlineFilter() =
                    if (dlAlbumIds != null) filter { it.id in dlAlbumIds } else this

                val items = when {
                    parentId == TAB_HOME -> {
                        val recentAlbums = albumDao.getRecentlyPlayedAlbumsSync(serverId, limit = HOME_ROW_SIZE)
                            .onlineFilter()
                        val recentItems = recentAlbums
                            .map { it.toBrowsableItem(groupTitle = "Recently Played") }

                        val addedAlbums = albumDao.getRecentlyAddedAlbums(serverId, limit = HOME_ROW_SIZE)
                            .onlineFilter()
                        val addedItems = addedAlbums
                            .map { it.toBrowsableItem(groupTitle = "Recently Added") }

                        val usedIds = recentAlbums.map { it.id }.toSet() +
                            addedAlbums.map { it.id }.toSet()

                        val quickPicks = albumDao.getRandomFavoriteOrMostPlayedAlbums(
                            serverId,
                            mostPlayedCount = 20,
                            downloadedOnly = downloadedOnly,
                            limit = HOME_ROW_SIZE + usedIds.size,
                        )
                            .filter { it.id !in usedIds }
                            .take(HOME_ROW_SIZE)
                            .map { it.toBrowsableItem(groupTitle = "Quick Picks") }

                        val favoriteTracks = trackDao.getRandomFavoriteTracks(serverId, downloadedOnly, HOME_ROW_SIZE)
                            .map { it.toPlayableItem(groupTitle = "Favorite Tracks") }

                        (recentItems + addedItems + quickPicks + favoriteTracks).page(window)
                    }
                    parentId == TAB_FAVORITES -> {
                        val items = mutableListOf<MediaItem>()

                        val favAlbums = albumDao.getFavoriteAlbumsSlice(serverId, downloadedOnly, HOME_ROW_SIZE, 0)
                        if (favAlbums.isNotEmpty()) {
                            items.add(browsableItem(
                                mediaId = FAV_ALBUMS, title = "Albums",
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                                browsableHint = CONTENT_STYLE_GRID_ITEM,
                                iconUri = drawableUri(R.drawable.ic_aa_albums),
                            ))
                            items.addAll(favAlbums.map { it.toPlayablePreview() })
                        }

                        val favArtists = artistDao.getFavoriteArtistsSlice(serverId, downloadedOnly, HOME_ROW_SIZE, 0)
                        if (favArtists.isNotEmpty()) {
                            items.add(browsableItem(
                                mediaId = FAV_ARTISTS, title = "Artists",
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                                browsableHint = CONTENT_STYLE_GRID_ITEM,
                                iconUri = drawableUri(R.drawable.ic_aa_artists),
                            ))
                            items.addAll(favArtists.map { it.toPlayablePreview() })
                        }

                        val favTracks = trackDao.getFavoriteTracksSlice(serverId, downloadedOnly, HOME_ROW_SIZE, 0)
                        if (favTracks.isNotEmpty()) {
                            items.add(browsableItem(
                                mediaId = FAV_TRACKS, title = "Tracks",
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                                playableHint = CONTENT_STYLE_GRID_ITEM,
                                iconUri = drawableUri(R.drawable.ic_aa_songs),
                            ))
                            items.addAll(favTracks.map { it.toPlayableItem(parentId = FAV_TRACKS) })
                        }

                        items.page(window)
                    }
                    parentId == FAV_ALBUMS -> {
                        albumDao.getFavoriteAlbumsSlice(serverId, downloadedOnly, window.limit, window.offset)
                            .map { it.toBrowsableItem() }
                    }
                    parentId == FAV_ARTISTS -> {
                        artistDao.getFavoriteArtistsSlice(serverId, downloadedOnly, window.limit, window.offset)
                            .map { it.toBrowsableItem() }
                    }
                    parentId == FAV_TRACKS -> {
                        trackDao.getFavoriteTracksSlice(serverId, downloadedOnly, window.limit, window.offset)
                            .map { it.toPlayableItem(parentId = FAV_TRACKS) }
                    }
                    parentId == LIBRARY_ALBUMS -> {
                        albumDao.getAlbumsByServerSlice(serverId, downloadedOnly, window.limit, window.offset)
                            .map { it.toBrowsableItem() }
                    }
                    parentId == LIBRARY_ARTISTS -> {
                        artistDao.getCanonicalArtistsSlice(serverId, downloadedOnly, window.limit, window.offset)
                            .map { it.toBrowsableItem() }
                    }
                    parentId == LIBRARY_GENRES -> {
                        // One row per distinct genre list, not per album, so this stays small.
                        val genreAlbums = albumDao.getRawGenreStrings(serverId)
                        genreAlbums
                            .flatMap { it.split(GENRE_SEPARATOR) }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
                            .page(window)
                            .map { genre ->
                                browsableItem(
                                    mediaId = "genre:$genre",
                                    title = genre,
                                    mediaType = MediaMetadata.MEDIA_TYPE_GENRE,
                                    browsableHint = CONTENT_STYLE_GRID_ITEM,
                                )
                            }
                    }
                    parentId == LIBRARY_SONGS -> {
                        trackDao.getTracksByServerPaged(serverId, downloadedOnly, window.limit, window.offset)
                            .map { it.toPlayableItem() }
                    }
                    parentId == TAB_PLAYLISTS -> {
                        playlistDao.getPlaylistsByServer(serverId)
                            .page(window)
                            .map { it.toBrowsableItem() }
                    }
                    parentId.startsWith("album:") -> {
                        val albumId = parentId.removePrefix("album:")
                        trackDao.getTracksByAlbumSync(albumId)
                            .onlineFilter()
                            .page(window)
                            .map { it.toPlayableItem() }
                    }
                    parentId.startsWith("artist:") -> {
                        // Same artist as the app's artist screen: resolved, so duplicates merge and artists that
                        // only appear on other artists' albums still have their tracks.
                        val artistId = parentId.removePrefix("artist:")
                        val albums = albumDao.getAllAlbumsByResolvedArtist(artistId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                        albums.ifEmpty {
                            trackDao.getTracksByResolvedArtistSync(artistId)
                                .onlineFilter()
                                .map { it.toPlayableItem(parentId = parentId) }
                        }.page(window)
                    }
                    parentId.startsWith("genre:") -> {
                        val genre = parentId.removePrefix("genre:")
                        albumDao.getAlbumsByGenreSlice(genre, serverId, downloadedOnly, window.limit, window.offset)
                            .map { it.toBrowsableItem() }
                    }
                    parentId.startsWith("playlist:") -> {
                        val playlistId = parentId.removePrefix("playlist:")
                        playlistDao.getPlaylistTracksSlice(playlistId, downloadedOnly, window.limit, window.offset)
                            .map { it.toPlayableItem(parentId = parentId) }
                    }
                    else -> emptyList()
                }
                // Info level so it survives release builds: Android Auto sometimes shows "No items" right after
                // connecting, and this records whether the connection state made us filter to downloads only.
                Log.i(
                    TAG,
                    "onGetChildren: parent=$parentId page=$page pageSize=$pageSize " +
                        "state=${networkStateObserver.connectionState.value} downloadsOnly=$downloadedOnly " +
                        "items=${items.size} controller=${browser.packageName}",
                )
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<Void>> {
            serviceScope.launch {
                val server = serverDao.getActiveServer()
                val serverId = server?.id ?: ""
                val isOnline = networkStateObserver.connectionState.value == ConnectionState.Connected
                val dlTrackIds = if (!isOnline) downloadDao.getDownloadedTrackIds().toSet() else null
                val dlAlbumIds = if (!isOnline) downloadDao.getDownloadedAlbumIds().toSet() else null
                val dlArtistNames = if (!isOnline) downloadDao.getDownloadedArtistNames().toSet() else null

                val artists = artistDao.search(serverId, query, limit = 5)
                    .let { if (dlArtistNames != null) it.filter { a -> a.name in dlArtistNames } else it }
                val albums = albumDao.search(serverId, query, limit = 10)
                    .let { if (dlAlbumIds != null) it.filter { a -> a.id in dlAlbumIds } else it }
                val tracks = trackDao.search(serverId, query, limit = 20)
                    .let { if (dlTrackIds != null) it.filter { t -> t.id in dlTrackIds } else it }
                session.notifySearchResultChanged(
                    browser, query, artists.size + albums.size + tracks.size, params,
                )
            }
            return Futures.immediateFuture(LibraryResult.ofVoid())
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val window = BrowseWindow.of(page, pageSize, AA_MAX_ITEMS)
            if (query.isBlank() || window == null) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
            return asyncFuture {
                val server = serverDao.getActiveServer()
                val serverId = server?.id ?: ""
                val isOnline = networkStateObserver.connectionState.value == ConnectionState.Connected
                val dlTrackIds = if (!isOnline) downloadDao.getDownloadedTrackIds().toSet() else null
                val dlAlbumIds = if (!isOnline) downloadDao.getDownloadedAlbumIds().toSet() else null
                val dlArtistNames = if (!isOnline) downloadDao.getDownloadedArtistNames().toSet() else null

                val artists = artistDao.search(serverId, query, limit = 5)
                    .let { if (dlArtistNames != null) it.filter { a -> a.name in dlArtistNames } else it }
                    .map { it.toBrowsableItem() }
                val albums = albumDao.search(serverId, query, limit = 10)
                    .let { if (dlAlbumIds != null) it.filter { a -> a.id in dlAlbumIds } else it }
                    .map { it.toBrowsableItem() }
                val tracks = trackDao.search(serverId, query, limit = 20)
                    .let { if (dlTrackIds != null) it.filter { t -> t.id in dlTrackIds } else it }
                    .map { it.toPlayableItem() }

                LibraryResult.ofItemList(ImmutableList.copyOf((artists + albums + tracks).page(window)), params)
            }
        }

        private fun rootChildren(): List<MediaItem> = listOf(
            browsableItem(
                mediaId = TAB_HOME,
                title = "Home",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                browsableHint = CONTENT_STYLE_GRID_ITEM,
                playableHint = CONTENT_STYLE_GRID_ITEM,
            ),
            browsableItem(
                mediaId = TAB_LIBRARY,
                title = "Library",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                browsableHint = CONTENT_STYLE_LIST_ITEM,
            ),
            browsableItem(
                mediaId = TAB_PLAYLISTS,
                title = "Playlists",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_PLAYLISTS,
                browsableHint = CONTENT_STYLE_GRID_ITEM,
            ),
            browsableItem(
                mediaId = TAB_FAVORITES,
                title = "Favorites",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                browsableHint = CONTENT_STYLE_LIST_ITEM,
                playableHint = CONTENT_STYLE_GRID_ITEM,
            ),
        )

        private fun libraryChildren(): List<MediaItem> = listOf(
            browsableItem(
                mediaId = LIBRARY_ALBUMS,
                title = "Albums",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                browsableHint = CONTENT_STYLE_GRID_ITEM,
                iconUri = drawableUri(R.drawable.ic_aa_albums),
            ),
            browsableItem(
                mediaId = LIBRARY_ARTISTS,
                title = "Artists",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                browsableHint = CONTENT_STYLE_GRID_ITEM,
                iconUri = drawableUri(R.drawable.ic_aa_artists),
            ),
            browsableItem(
                mediaId = LIBRARY_GENRES,
                title = "Genres",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_GENRES,
                browsableHint = CONTENT_STYLE_LIST_ITEM,
                iconUri = drawableUri(R.drawable.ic_aa_genres),
            ),
            browsableItem(
                mediaId = LIBRARY_SONGS,
                title = "Songs",
                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                playableHint = CONTENT_STYLE_GRID_ITEM,
                iconUri = drawableUri(R.drawable.ic_aa_songs),
            ),
        )

        private fun browsableItem(
            mediaId: String,
            title: String,
            mediaType: Int? = null,
            browsableHint: Int? = null,
            playableHint: Int? = null,
            groupTitle: String? = null,
            iconUri: Uri? = null,
        ): MediaItem {
            val extras = if (browsableHint != null || playableHint != null || groupTitle != null) {
                Bundle().apply {
                    browsableHint?.let { putInt(CONTENT_STYLE_BROWSABLE_HINT, it) }
                    playableHint?.let { putInt(CONTENT_STYLE_PLAYABLE_HINT, it) }
                    groupTitle?.let { putString(CONTENT_STYLE_GROUP_TITLE, it) }
                }
            } else null
            return MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle(title)
                        .apply { iconUri?.let { setArtworkUri(it) } }
                        .apply { mediaType?.let { setMediaType(it) } }
                        .apply { extras?.let { setExtras(it) } }
                        .build()
                )
                .build()
        }
    }

    companion object {
        private const val TAG = "MellowMediaService"
        private const val POSITION_SAVE_INTERVAL_MS = 10_000L
        private const val ROOT_ID = "mellow_root"
        private const val TAB_HOME = "tab_home"
        private const val TAB_LIBRARY = "tab_library"
        private const val TAB_PLAYLISTS = "tab_playlists"
        private const val TAB_FAVORITES = "tab_favorites"
        private const val LIBRARY_ALBUMS = "library_albums"
        private const val LIBRARY_ARTISTS = "library_artists"
        private const val LIBRARY_GENRES = "library_genres"
        private const val LIBRARY_SONGS = "library_songs"
        private const val FAV_ALBUMS = "fav_albums"
        private const val FAV_ARTISTS = "fav_artists"
        private const val FAV_TRACKS = "fav_tracks"
        private const val HOME_ROW_SIZE = 3
        private const val AA_MAX_ITEMS = 500
        private const val EXTRA_PARENT_ID = "dev.mellow.PARENT_ID"
        private const val GENRE_SEPARATOR = "|||"
        private const val CONTENT_STYLE_BROWSABLE_HINT =
            "android.media.browse.CONTENT_STYLE_BROWSABLE_HINT"
        private const val CONTENT_STYLE_PLAYABLE_HINT =
            "android.media.browse.CONTENT_STYLE_PLAYABLE_HINT"
        private const val CONTENT_STYLE_GROUP_TITLE =
            "android.media.browse.CONTENT_STYLE_GROUP_TITLE_HINT"
        private const val CONTENT_STYLE_LIST_ITEM = 1
        private const val CONTENT_STYLE_GRID_ITEM = 2
    }
}

private class ContentBitmapLoader(
    private val context: android.content.Context,
) : androidx.media3.session.BitmapLoader {

    private val fallback = SimpleBitmapLoader()
    private val executor = java.util.concurrent.Executors.newCachedThreadPool()

    override fun supportsMimeType(mimeType: String): Boolean = true

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> {
        if (uri.scheme == "content") {
            val future = SettableFuture.create<Bitmap>()
            executor.execute {
                try {
                    val bitmap = context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream)
                    }
                    if (bitmap != null) {
                        future.set(bitmap)
                    } else {
                        future.setException(java.io.IOException("Failed to decode bitmap from $uri"))
                    }
                } catch (e: Exception) {
                    future.setException(e)
                }
            }
            return future
        }
        return fallback.loadBitmap(uri)
    }
}
