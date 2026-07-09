package dev.mellow.core.player.cache

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.NoOpCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MellowCache @Inject constructor(
    context: Context,
) {
    private val databaseProvider = StandaloneDatabaseProvider(context)

    val streamingCache: SimpleCache = SimpleCache(
        File(context.cacheDir, STREAMING_CACHE_DIR),
        LeastRecentlyUsedCacheEvictor(STREAMING_CACHE_SIZE_BYTES),
        databaseProvider,
    )

    val downloadCache: SimpleCache = SimpleCache(
        File(context.filesDir, DOWNLOAD_CACHE_DIR),
        NoOpCacheEvictor(),
        databaseProvider,
    )

    fun release() {
        streamingCache.release()
        downloadCache.release()
    }

    companion object {
        private const val STREAMING_CACHE_DIR = "media_cache"
        private const val DOWNLOAD_CACHE_DIR = "downloads"
        private const val STREAMING_CACHE_SIZE_BYTES = 256L * 1024 * 1024 // 256 MB
    }
}
