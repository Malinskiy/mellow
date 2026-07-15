package dev.mellow.core.player

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SimpleBitmapLoader
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.SettableFuture
import dagger.hilt.android.AndroidEntryPoint
import dev.mellow.core.common.jellyfinStreamUrl
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.PlaylistEntity
import dev.mellow.core.database.entity.TrackEntity
import dev.mellow.core.network.ConnectionState
import dev.mellow.core.network.NetworkStateObserver
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
    @Inject lateinit var downloadDao: DownloadDao
    @Inject lateinit var networkStateObserver: NetworkStateObserver

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

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

        val bitmapLoader = CacheBitmapLoader(ContentBitmapLoader(this))

        mediaLibrarySession = MediaLibrarySession.Builder(this, exoPlayer, LibrarySessionCallback())
            .setBitmapLoader(bitmapLoader)
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

    private fun drawableUri(resId: Int): Uri =
        Uri.parse("android.resource://$packageName/$resId")

    private fun AlbumEntity.toBrowsableItem(groupTitle: String? = null): MediaItem {
        val extras = Bundle().apply {
            putInt(CONTENT_STYLE_PLAYABLE_HINT, CONTENT_STYLE_LIST_ITEM)
            groupTitle?.let { putString(CONTENT_STYLE_GROUP_TITLE, it) }
        }
        return MediaItem.Builder()
            .setMediaId("album:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setArtist(artistName)
                    .setSubtitle(artistName?.let { "Album \u2022 $it" } ?: "Album")
                    .setArtworkUri(artworkUri(id))
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
                    .setArtworkUri(if (imageTag != null) artworkUri(id) else null)
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

    private fun AlbumEntity.toPlayablePreview(): MediaItem =
        MediaItem.Builder()
            .setMediaId("album:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle(artistName?.let { "Album \u2022 $it" } ?: "Album")
                    .setArtworkUri(artworkUri(id))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ALBUM)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private fun ArtistEntity.toPlayablePreview(): MediaItem =
        MediaItem.Builder()
            .setMediaId("artist:$id")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(name)
                    .setSubtitle("Artist")
                    .setArtworkUri(if (imageTag != null) artworkUri(id) else null)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_ARTIST)
                    .setIsBrowsable(false)
                    .setIsPlayable(true)
                    .build()
            )
            .build()

    private suspend fun enrichMediaItems(mediaItems: List<MediaItem>): List<MediaItem> {
        val server = serverDao.getActiveServer() ?: return mediaItems
        val serverUrl = server.url
        val apiKey = server.accessToken

        return mediaItems.map { item ->
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
                            .setArtworkUri(artworkUri(track.albumId ?: trackId))
                            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
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

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

        override fun onSetMediaItems(
            session: MediaSession,
            browser: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
            startIndex: Int,
            startPositionMs: Long,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> {
            return asyncFuture {
                if (mediaItems.size == 1) {
                    val mediaId = mediaItems[0].mediaId

                    if (mediaId.startsWith("album:")) {
                        val albumId = mediaId.removePrefix("album:")
                        val tracks = trackDao.getTracksByAlbumSync(albumId)
                        if (tracks.isNotEmpty()) {
                            val enriched = enrichMediaItems(tracks.map {
                                it.toPlayableItem(parentId = "album:$albumId")
                            })
                            return@asyncFuture MediaSession.MediaItemsWithStartPosition(
                                enriched, 0, startPositionMs,
                            )
                        }
                    }
                    if (mediaId.startsWith("artist:")) {
                        val artistId = mediaId.removePrefix("artist:")
                        val albums = albumDao.getAllAlbumsByArtist(artistId)
                        if (albums.isNotEmpty()) {
                            val firstAlbumTracks = trackDao.getTracksByAlbumSync(albums[0].id)
                            if (firstAlbumTracks.isNotEmpty()) {
                                val enriched = enrichMediaItems(firstAlbumTracks.map {
                                    it.toPlayableItem(parentId = "album:${albums[0].id}")
                                })
                                return@asyncFuture MediaSession.MediaItemsWithStartPosition(
                                    enriched, 0, startPositionMs,
                                )
                            }
                        }
                    }

                    val trackId = mediaId
                    val parentId = mediaItems[0].mediaMetadata.extras?.getString(EXTRA_PARENT_ID)
                    val track = trackDao.getTrackById(trackId)
                    val serverId = serverDao.getActiveServer()?.id ?: ""

                    val siblings = when {
                        parentId?.startsWith("playlist:") == true -> {
                            val plId = parentId.removePrefix("playlist:")
                            playlistDao.getPlaylistTracksSync(plId)
                        }
                        parentId == FAV_TRACKS -> {
                            trackDao.getFavoriteTracksSync(serverId)
                        }
                        parentId == LIBRARY_SONGS -> {
                            trackDao.getAllTracksByServer(serverId)
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

                    if (!siblings.isNullOrEmpty()) {
                        val enriched = enrichMediaItems(siblings.map {
                            it.toPlayableItem(parentId = parentId)
                        })
                        val idx = siblings.indexOfFirst { it.id == trackId }.coerceAtLeast(0)
                        MediaSession.MediaItemsWithStartPosition(enriched, idx, startPositionMs)
                    } else {
                        val enriched = enrichMediaItems(mediaItems)
                        MediaSession.MediaItemsWithStartPosition(enriched, startIndex, startPositionMs)
                    }
                } else {
                    val enriched = enrichMediaItems(mediaItems)
                    MediaSession.MediaItemsWithStartPosition(enriched, startIndex, startPositionMs)
                }
            }
        }

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: List<MediaItem>,
        ): ListenableFuture<List<MediaItem>> {
            return asyncFuture { enrichMediaItems(mediaItems) }
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
                val isOnline = networkStateObserver.connectionState.value == ConnectionState.Connected
                val dlTrackIds = if (!isOnline) downloadDao.getDownloadedTrackIds().toSet() else null
                val dlAlbumIds = if (!isOnline) downloadDao.getDownloadedAlbumIds().toSet() else null
                val dlArtistNames = if (!isOnline) downloadDao.getDownloadedArtistNames().toSet() else null

                fun List<TrackEntity>.onlineFilter() =
                    if (dlTrackIds != null) filter { it.id in dlTrackIds } else this
                fun List<AlbumEntity>.onlineFilter() =
                    if (dlAlbumIds != null) filter { it.id in dlAlbumIds } else this
                fun List<ArtistEntity>.onlineFilter() =
                    if (dlArtistNames != null) filter { it.name in dlArtistNames } else this

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

                        val mostPlayed = albumDao.getMostPlayedAlbumsSync(serverId, limit = 20)
                            .onlineFilter()
                        val favoriteAlbums = albumDao.getFavoriteAlbumsSync(serverId)
                            .onlineFilter()
                        val quickPicks = (mostPlayed + favoriteAlbums)
                            .distinctBy { it.id }
                            .filter { it.id !in usedIds }
                            .shuffled()
                            .take(HOME_ROW_SIZE)
                            .map { it.toBrowsableItem(groupTitle = "Quick Picks") }

                        val favoriteTracks = trackDao.getFavoriteTracksSync(serverId)
                            .onlineFilter()
                            .shuffled()
                            .take(HOME_ROW_SIZE)
                            .map { it.toPlayableItem(groupTitle = "Favorite Tracks") }

                        recentItems + addedItems + quickPicks + favoriteTracks
                    }
                    parentId == TAB_FAVORITES -> {
                        val items = mutableListOf<MediaItem>()

                        val favAlbums = albumDao.getFavoriteAlbumsSync(serverId).onlineFilter()
                        if (favAlbums.isNotEmpty()) {
                            items.add(browsableItem(
                                mediaId = FAV_ALBUMS, title = "Albums",
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ALBUMS,
                                browsableHint = CONTENT_STYLE_GRID_ITEM,
                                iconUri = drawableUri(R.drawable.ic_aa_albums),
                            ))
                            items.addAll(favAlbums.take(HOME_ROW_SIZE)
                                .map { it.toPlayablePreview() })
                        }

                        val favArtists = artistDao.getFavoriteArtistsSync(serverId).onlineFilter()
                        if (favArtists.isNotEmpty()) {
                            items.add(browsableItem(
                                mediaId = FAV_ARTISTS, title = "Artists",
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_ARTISTS,
                                browsableHint = CONTENT_STYLE_GRID_ITEM,
                                iconUri = drawableUri(R.drawable.ic_aa_artists),
                            ))
                            items.addAll(favArtists.take(HOME_ROW_SIZE)
                                .map { it.toPlayablePreview() })
                        }

                        val favTracks = trackDao.getFavoriteTracksSync(serverId).onlineFilter()
                        if (favTracks.isNotEmpty()) {
                            items.add(browsableItem(
                                mediaId = FAV_TRACKS, title = "Tracks",
                                mediaType = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED,
                                playableHint = CONTENT_STYLE_GRID_ITEM,
                                iconUri = drawableUri(R.drawable.ic_aa_songs),
                            ))
                            items.addAll(favTracks.take(HOME_ROW_SIZE)
                                .map { it.toPlayableItem(parentId = FAV_TRACKS) })
                        }

                        items
                    }
                    parentId == FAV_ALBUMS -> {
                        albumDao.getFavoriteAlbumsSync(serverId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId == FAV_ARTISTS -> {
                        artistDao.getFavoriteArtistsSync(serverId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId == FAV_TRACKS -> {
                        trackDao.getFavoriteTracksSync(serverId)
                            .onlineFilter()
                            .map { it.toPlayableItem(parentId = FAV_TRACKS) }
                    }
                    parentId == LIBRARY_ALBUMS -> {
                        albumDao.getAllAlbumsByServer(serverId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId == LIBRARY_ARTISTS -> {
                        artistDao.getAllArtistsByServer(serverId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId == LIBRARY_GENRES -> {
                        val genreAlbums = albumDao.getRawGenreStrings(serverId)
                        genreAlbums
                            .flatMap { it.split(GENRE_SEPARATOR) }
                            .filter { it.isNotBlank() }
                            .distinct()
                            .sorted()
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
                        trackDao.getAllTracksByServer(serverId)
                            .onlineFilter()
                            .map { it.toPlayableItem() }
                    }
                    parentId == TAB_PLAYLISTS -> {
                        playlistDao.getPlaylistsByServer(serverId)
                            .map { it.toBrowsableItem() }
                    }
                    parentId.startsWith("album:") -> {
                        val albumId = parentId.removePrefix("album:")
                        trackDao.getTracksByAlbumSync(albumId)
                            .onlineFilter()
                            .map { it.toPlayableItem() }
                    }
                    parentId.startsWith("artist:") -> {
                        val artistId = parentId.removePrefix("artist:")
                        albumDao.getAllAlbumsByArtist(artistId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId.startsWith("genre:") -> {
                        val genre = parentId.removePrefix("genre:")
                        albumDao.getAlbumsByGenre(genre, serverId)
                            .onlineFilter()
                            .map { it.toBrowsableItem() }
                    }
                    parentId.startsWith("playlist:") -> {
                        val playlistId = parentId.removePrefix("playlist:")
                        playlistDao.getPlaylistTracksSync(playlistId)
                            .onlineFilter()
                            .map { it.toPlayableItem(parentId = parentId) }
                    }
                    else -> emptyList()
                }
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
            if (query.isBlank()) {
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

                LibraryResult.ofItemList(ImmutableList.copyOf(artists + albums + tracks), params)
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
