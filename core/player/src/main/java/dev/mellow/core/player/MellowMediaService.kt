package dev.mellow.core.player

import android.content.Intent
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
import dagger.hilt.android.AndroidEntryPoint
import dev.mellow.core.player.cache.MellowDataSourceFactory
import javax.inject.Inject

@AndroidEntryPoint
class MellowMediaService : MediaLibraryService() {

    @Inject lateinit var dataSourceFactory: MellowDataSourceFactory

    private var mediaLibrarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null

    override fun onCreate() {
        super.onCreate()

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
        mediaLibrarySession?.run {
            player.release()
            release()
            mediaLibrarySession = null
        }
        super.onDestroy()
    }

    private inner class LibrarySessionCallback : MediaLibrarySession.Callback {

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
            val children = when (parentId) {
                ROOT_ID -> rootChildren()
                TAB_RECENT -> emptyList()
                TAB_LIBRARY -> libraryChildren()
                TAB_PLAYLISTS -> emptyList()
                TAB_FAVORITES -> emptyList()
                LIBRARY_ALBUMS -> emptyList()
                LIBRARY_ARTISTS -> emptyList()
                LIBRARY_GENRES -> emptyList()
                else -> emptyList()
            }
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.copyOf(children), params)
            )
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?,
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> {
            return Futures.immediateFuture(
                LibraryResult.ofItemList(ImmutableList.of(), params)
            )
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
    }
}
