package dev.mellow.core.player

import android.content.Intent
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.mellow.core.common.jellyfinStreamUrl
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.PlaylistEntity
import dev.mellow.core.database.entity.TrackEntity
import dev.mellow.core.player.cache.MellowDataSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MellowMediaService : MediaLibraryService() {

    @Inject lateinit var dataSourceFactory: MellowDataSourceFactory
    @Inject lateinit var serverDao: ServerDao
    @Inject lateinit var albumDao: AlbumDao
    @Inject lateinit var artistDao: ArtistDao
    @Inject lateinit var trackDao: TrackDao
    @Inject lateinit var playlistDao: PlaylistDao

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        if (mediaLibrarySession != null) return // Guard against Media3 bind/unbind loop

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

        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer, LibrarySessionCallback())
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? =
        mediaLibrarySession

    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = mediaLibrarySession?.player ?: return
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        player = null
        super.onDestroy()
    }

    private fun <T> asyncFuture(block: suspend () -> T): ListenableFuture<T> {
        val future = SettableFuture.create<T>()
        serviceScope.launch {
            try {
                future.set(block())
            } catch (e: Exception) {
                future.setException(e)
            }
        }
        return future
    }

    private fun artworkUri(itemId: String): Uri =
        Uri.parse("content://${packageName}.artwork/$itemId")

    private fun AlbumEntity.toBrowsableItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId("album:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistName)
                    .setArtworkUri(if (imageTag != null) artworkUri(id) else null)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun ArtistEntity.toBrowsableItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId("artist:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtworkUri(if (imageTag != null) artworkUri(id) else null)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun PlaylistEntity.toBrowsableItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId("playlist:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtworkUri(if (imageTag != null) artworkUri(id) else null)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .build()
            )
            .build()

    private fun TrackEntity.toPlayableItem(): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistName)
                    .setAlbumTitle(albumName)
                    .setArtworkUri(if (imageTag != null) artworkUri(albumId ?: id) else null)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            return asyncFuture {
                val server = serverDao.getActiveServer()
                    ?: return@asyncFuture mediaItems
                val serverUrl = server.url
                val apiKey = server.accessToken

                mediaItems.map { item ->
                    val trackId = item.mediaId
                    if (item.localConfiguration != null) return@map item

                    val track = trackDao.getTrackById(trackId)
                    if (track != null) {
                        MediaItem.Builder()
                            .setMediaId(trackId)
                            .setUri(Uri.parse(jellyfinStreamUrl(serverUrl, trackId, apiKey)))
                            .setCustomCacheKey(trackId)
                            .setMediaMetadata(
                                MediaMetadata.Builder()
                                    .setTitle(track.name)
                                    .setArtist(track.artistName)
                                    .setAlbumTitle(track.albumName)
                                    .setArtworkUri(if (track.imageTag != null) artworkUri(track.albumId ?: trackId) else null)
                                    .setIsPlayable(true)
                                    .setIsBrowsable(false)
                                    .build()
                            )
                            .build()
                    } else {
                        item
                    }
                }
            }
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<MediaItem>> {
            val root = MediaItem.Builder()
                .setMediaId(ROOT_ID)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle("Mellow")
                        .build()
                )
                .build()
            return Futures.immediateFuture(LibraryResult.ofItem(root, params))
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            val syncChildren = when (parentId) {
                ROOT_ID -> rootChildren()
                TAB_LIBRARY -> libraryChildren()
                else -> null
            }
            if (syncChildren != null) {
                return Futures.immediateFuture(
                    LibraryResult.ofItemList(ImmutableList.copyOf(syncChildren), params)
                )
            }

            return asyncFuture {
                val server = serverDao.getActiveServer()
                val serverId = server?.id ?: ""

                val items = when {
                    parentId == TAB_RECENT -> {
                        trackDao.getRecentlyPlayedTracks(serverId, limit = 50)
                            .map { it.toPlayableItem() }
                    }
                    parentId == TAB_FAVORITES -> {
                        trackDao.getFavoriteTracksSync(serverId)
                            .map { it.toPlayableItem() }
                    }
                    parentId == LIBRARY_ALBUMS -> {
                        albumDao.getAllAlbumsByServer(serverId)
                            .map { it.toBrowsableItem() }
                    }
                    parentId == LIBRARY_ARTISTS -> {
                        artistDao.getAllArtistsByServer(serverId)
                            .map { it.toBrowsableItem() }
                    }
                    parentId == LIBRARY_GENRES -> {
                        albumDao.getRawGenreStrings(serverId)
                            .flatMap { it.split(GENRE_SEPARATOR) }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
                            .map { genre -> browsableItem("genre:$genre", genre) }
                    }
                    parentId == TAB_PLAYLISTS -> {
                        playlistDao.getPlaylistsByServer(serverId)
                            .map { it.toBrowsableItem() }
                    }
                    parentId.startsWith("album:") -> {
                        val albumId = parentId.removePrefix("album:")
                        trackDao.getTracksByAlbumSync(albumId)
                            .map { it.toPlayableItem() }
                    }
                    parentId.startsWith("artist:") -> {
                        val artistId = parentId.removePrefix("artist:")
                        albumDao.getAllAlbumsByArtist(artistId)
                            .map { it.toBrowsableItem() }
                    }
                    parentId.startsWith("genre:") -> {
                        val genre = parentId.removePrefix("genre:")
                        albumDao.getAlbumsByGenre(genre, serverId)
                            .map { it.toBrowsableItem() }
                    }
                    parentId.startsWith("playlist:") -> {
                        val playlistId = parentId.removePrefix("playlist:")
                        playlistDao.getPlaylistTracksSync(playlistId)
                            .map { it.toPlayableItem() }
                    }
                    else -> emptyList()
                }
                LibraryResult.ofItemList(ImmutableList.copyOf(items), params)
            }
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            if (query.isBlank()) {
                return Futures.immediateFuture(LibraryResult.ofItemList(ImmutableList.of(), params))
            }
            return asyncFuture {
                val server = serverDao.getActiveServer()
                val serverId = server?.id ?: ""

                val albums = albumDao.search(serverId, query, limit = 10)
                    .map { it.toBrowsableItem() }
                val tracks = trackDao.search(serverId, query, limit = 20)
                    .map { it.toPlayableItem() }

                LibraryResult.ofItemList(ImmutableList.copyOf(albums + tracks), params)
            }
        }

        private fun rootChildren(): List<MediaItem> = listOf(
            browsableItem(TAB_RECENT, "Recent"),
            browsableItem(TAB_LIBRARY, "Library"),
            browsableItem(TAB_PLAYLISTS, "Playlists"),
            browsableItem(TAB_FAVORITES, "Favorites"),
        )

        private fun libraryChildren(): List<MediaItem> = listOf(
            browsableItem(LIBRARY_ALBUMS, "Albums"),
            browsableItem(LIBRARY_ARTISTS, "Artists"),
            browsableItem(LIBRARY_GENRES, "Genres"),
        )

        private fun browsableItem(mediaId: String, title: String): MediaItem =
            MediaItem.Builder()
                .setMediaId(mediaId)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setIsBrowsable(true)
                        .setIsPlayable(false)
                        .setTitle(title)
                        .build()
                )
                .build()
    }

    companion object {
        private const val ROOT_ID = "mellow_root"
        private const val TAB_RECENT = "tab_recent"
        private const val TAB_LIBRARY = "tab_library"
        private const val TAB_PLAYLISTS = "tab_playlists"
        private const val TAB_FAVORITES = "tab_favorites"
        private const val LIBRARY_ALBUMS = "library_albums"
        private const val LIBRARY_ARTISTS = "library_artists"
        private const val LIBRARY_GENRES = "library_genres"
        private const val GENRE_SEPARATOR = "|||"
    }
}
