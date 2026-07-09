package dev.mellow.core.player.cache

import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR
import androidx.media3.datasource.okhttp.OkHttpDataSource
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MellowDataSourceFactory @Inject constructor(
    private val mellowCache: MellowCache,
) {
    private val okHttpClient = OkHttpClient()

    private val networkDataSourceFactory: DataSource.Factory =
        OkHttpDataSource.Factory(okHttpClient)

    /**
     * Creates a DataSource.Factory for streaming playback.
     *
     * Resolution order:
     * 1. Check downloadCache (read-only, for completed downloads)
     * 2. Check streamingCache (read/write, for recently streamed content)
     * 3. Fetch from network (writes to streamingCache)
     */
    fun createPlaybackDataSourceFactory(): DataSource.Factory {
        val streamingCacheFactory = CacheDataSource.Factory()
            .setCache(mellowCache.streamingCache)
            .setUpstreamDataSourceFactory(networkDataSourceFactory)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)

        return CacheDataSource.Factory()
            .setCache(mellowCache.downloadCache)
            .setUpstreamDataSourceFactory(streamingCacheFactory)
            .setCacheWriteDataSinkFactory(null)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createDownloadDataSourceFactory(): DataSource.Factory {
        return CacheDataSource.Factory()
            .setCache(mellowCache.downloadCache)
            .setUpstreamDataSourceFactory(networkDataSourceFactory)
            .setFlags(FLAG_IGNORE_CACHE_ON_ERROR)
    }

    fun createNetworkDataSourceFactory(): DataSource.Factory = networkDataSourceFactory
}
