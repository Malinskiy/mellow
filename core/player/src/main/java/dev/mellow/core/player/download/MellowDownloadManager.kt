package dev.mellow.core.player.download

import android.content.Context
import android.net.Uri
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.offline.DefaultDownloadIndex
import androidx.media3.exoplayer.scheduler.Requirements
import androidx.media3.exoplayer.offline.DefaultDownloaderFactory
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mellow.core.common.DownloadExecutor
import dev.mellow.core.data.preferences.DownloadPreferences
import dev.mellow.core.common.jellyfinStreamUrl
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.player.cache.MellowDataSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class MellowDownloadManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataSourceFactory: MellowDataSourceFactory,
    private val downloadDao: DownloadDao,
    private val downloadPreferences: DownloadPreferences,
) : DownloadExecutor {
    private val executor = Executors.newFixedThreadPool(DOWNLOAD_THREAD_COUNT)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    override val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()
    private var progressJob: Job? = null

    private fun ensureProgressPublishing() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (true) {
                val active = downloadManager.currentDownloads
                if (active.isEmpty()) {
                    _downloadProgress.update { emptyMap() }
                    break
                }
                _downloadProgress.update {
                    active.associate { dl -> dl.request.id to (dl.percentDownloaded / 100f) }
                }
                delay(PROGRESS_INTERVAL_MS)
            }
        }
    }

    val downloadManager: DownloadManager by lazy {
        val databaseProvider = StandaloneDatabaseProvider(context)
        val downloadIndex = DefaultDownloadIndex(databaseProvider)
        val cacheDataSourceFactory =
            dataSourceFactory.createDownloadDataSourceFactory() as CacheDataSource.Factory
        val downloaderFactory = DefaultDownloaderFactory(cacheDataSourceFactory, executor)

        DownloadManager(context, downloadIndex, downloaderFactory).apply {
            maxParallelDownloads = MAX_PARALLEL_DOWNLOADS
            addListener(downloadListener)
        }.also { manager ->
            scope.launch {
                downloadPreferences.wifiOnly.collect { wifiOnly ->
                    val requirements = if (wifiOnly) {
                        Requirements(Requirements.NETWORK_UNMETERED)
                    } else {
                        Requirements(Requirements.NETWORK)
                    }
                    manager.requirements = requirements
                }
            }
        }
    }

    private val downloadListener = object : DownloadManager.Listener {
        override fun onDownloadChanged(
            manager: DownloadManager,
            download: Download,
            finalException: Exception?,
        ) {
            when (download.state) {
                Download.STATE_DOWNLOADING -> ensureProgressPublishing()
                Download.STATE_COMPLETED, Download.STATE_FAILED, Download.STATE_REMOVING -> {
                    _downloadProgress.update { it - download.request.id }
                }
            }
            scope.launch {
                syncDownloadState(download)
            }
        }

        override fun onDownloadRemoved(manager: DownloadManager, download: Download) {
            scope.launch {
                downloadDao.delete(download.request.id)
            }
        }
    }

    private suspend fun syncDownloadState(download: Download) {
        val trackId = download.request.id
        val entity = downloadDao.getDownload(trackId) ?: return

        val isFullyDownloaded = download.percentDownloaded >= 100f ||
            (download.contentLength > 0 && download.bytesDownloaded >= download.contentLength)

        val newStatus = when {
            download.state == Download.STATE_COMPLETED || isFullyDownloaded -> DownloadEntity.STATUS_COMPLETED
            download.state == Download.STATE_QUEUED -> DownloadEntity.STATUS_QUEUED
            download.state == Download.STATE_DOWNLOADING -> DownloadEntity.STATUS_DOWNLOADING
            download.state == Download.STATE_FAILED -> DownloadEntity.STATUS_FAILED
            download.state == Download.STATE_REMOVING -> DownloadEntity.STATUS_REMOVED
            else -> entity.status
        }

        if (newStatus == entity.status && newStatus == DownloadEntity.STATUS_DOWNLOADING) {
            return
        }

        val completedAt = if (newStatus == DownloadEntity.STATUS_COMPLETED) {
            System.currentTimeMillis()
        } else {
            entity.completedAt
        }

        downloadDao.upsert(
            entity.copy(
                status = newStatus,
                progress = download.percentDownloaded / 100f,
                bytesDownloaded = download.bytesDownloaded,
                totalBytes = if (download.contentLength > 0) download.contentLength else entity.totalBytes,
                completedAt = completedAt,
                errorMessage = if (newStatus == DownloadEntity.STATUS_FAILED) "Download failed" else null,
                lastSynced = System.currentTimeMillis(),
            ),
        )
    }

    override fun startDownload(trackId: String, serverUrl: String, apiKey: String) {
        // Ensure DownloadManager is initialized and listening
        downloadManager
        val uri = Uri.parse(jellyfinStreamUrl(serverUrl, trackId, apiKey))
        val request = DownloadRequest.Builder(trackId, uri)
            .setCustomCacheKey(trackId)
            .build()
        DownloadService.sendAddDownload(
            context,
            MellowDownloadService::class.java,
            request,
            false,
        )
    }

    override fun removeDownload(trackId: String) {
        DownloadService.sendRemoveDownload(
            context,
            MellowDownloadService::class.java,
            trackId,
            false,
        )
    }

    fun getDownload(trackId: String): Download? =
        downloadManager.downloadIndex.getDownload(trackId)

    fun isDownloaded(trackId: String): Boolean {
        val download = downloadManager.downloadIndex.getDownload(trackId)
        return download?.state == Download.STATE_COMPLETED
    }

    companion object {
        private const val DOWNLOAD_THREAD_COUNT = 4
        private const val MAX_PARALLEL_DOWNLOADS = 3
        private const val PROGRESS_INTERVAL_MS = 500L
    }
}
