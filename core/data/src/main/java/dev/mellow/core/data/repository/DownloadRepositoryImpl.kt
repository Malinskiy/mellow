package dev.mellow.core.data.repository

import dev.mellow.core.common.DownloadExecutor
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.model.AlbumDownloadState
import dev.mellow.core.model.DownloadState
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao,
    private val serverDao: ServerDao,
    private val downloadExecutor: DownloadExecutor,
) : DownloadRepository {

    override val liveProgress: StateFlow<Map<String, Float>> = downloadExecutor.downloadProgress

    override fun observeDownload(trackId: String): Flow<DownloadState?> =
        downloadDao.observeDownload(trackId).map { it?.toDomainModel() }

    override fun observeAlbumDownloads(albumId: String): Flow<AlbumDownloadState> =
        downloadDao.observeAlbumDownloads(albumId).map { entities ->
            val completed = entities.count { it.status == DownloadEntity.STATUS_COMPLETED }
            val downloading = entities.any {
                it.status == DownloadEntity.STATUS_DOWNLOADING || it.status == DownloadEntity.STATUS_QUEUED
            }
            val totalBytes = entities.sumOf { it.totalBytes }
            val downloadedBytes = entities.sumOf { it.bytesDownloaded }

            val overallStatus = when {
                entities.isEmpty() -> AlbumDownloadState.Status.NONE
                downloading -> AlbumDownloadState.Status.DOWNLOADING
                completed == entities.size -> AlbumDownloadState.Status.COMPLETED
                completed > 0 -> AlbumDownloadState.Status.PARTIAL
                else -> AlbumDownloadState.Status.NONE
            }

            AlbumDownloadState(
                albumId = albumId,
                totalTracks = entities.size,
                downloadedTracks = completed,
                totalBytes = totalBytes,
                downloadedBytes = downloadedBytes,
                overallStatus = overallStatus,
            )
        }

    override fun observeActiveDownloads(): Flow<List<DownloadState>> =
        downloadDao.observeActiveDownloads().map { entities ->
            entities.map { it.toDomainModel() }
        }

    override fun getTotalDownloadedBytes(): Flow<Long> =
        downloadDao.getTotalDownloadedBytes()

    override fun isTrackDownloaded(trackId: String): Flow<Boolean> =
        downloadDao.isDownloaded(trackId)

    override suspend fun downloadTrack(track: Track, serverId: String, quality: String) {
        val server = serverDao.getActiveServer() ?: return
        val entity = DownloadEntity(
            trackId = track.id,
            albumId = track.albumId,
            serverId = serverId,
            status = DownloadEntity.STATUS_QUEUED,
            progress = 0f,
            bytesDownloaded = 0L,
            totalBytes = 0L,
            quality = quality,
            filePath = null,
            requestedAt = System.currentTimeMillis(),
            completedAt = 0L,
            errorMessage = null,
            lastSynced = System.currentTimeMillis(),
        )
        downloadDao.upsert(entity)
        downloadExecutor.startDownload(track.id, server.url, server.accessToken)
    }

    override suspend fun downloadAlbum(
        albumId: String,
        tracks: List<Track>,
        serverId: String,
        quality: String,
    ) {
        val server = serverDao.getActiveServer() ?: return
        val now = System.currentTimeMillis()
        val entities = tracks.map { track ->
            DownloadEntity(
                trackId = track.id,
                albumId = albumId,
                serverId = serverId,
                status = DownloadEntity.STATUS_QUEUED,
                progress = 0f,
                bytesDownloaded = 0L,
                totalBytes = 0L,
                quality = quality,
                filePath = null,
                requestedAt = now,
                completedAt = 0L,
                errorMessage = null,
                lastSynced = now,
            )
        }
        downloadDao.upsertAll(entities)
        tracks.forEach { track ->
            downloadExecutor.startDownload(track.id, server.url, server.accessToken)
        }
    }

    override suspend fun cancelDownload(trackId: String) {
        downloadExecutor.removeDownload(trackId)
        downloadDao.getDownload(trackId)?.let { entity ->
            downloadDao.upsert(
                entity.copy(
                    status = DownloadEntity.STATUS_REMOVED,
                    lastSynced = System.currentTimeMillis(),
                ),
            )
        }
    }

    override suspend fun removeDownload(trackId: String) {
        downloadExecutor.removeDownload(trackId)
        downloadDao.delete(trackId)
    }

    override suspend fun removeAlbumDownloads(albumId: String) {
        val downloads = downloadDao.getDownloadsByAlbum(albumId)
        downloads.forEach { downloadExecutor.removeDownload(it.trackId) }
        downloadDao.deleteByAlbum(albumId)
    }

    override suspend fun clearAllDownloads() {
        val downloads = downloadDao.getAllDownloads()
        downloads.forEach { downloadExecutor.removeDownload(it.trackId) }
        downloadDao.deleteAll()
    }

    private fun DownloadEntity.toDomainModel(): DownloadState = when (status) {
        DownloadEntity.STATUS_QUEUED -> DownloadState.Queued(trackId)
        DownloadEntity.STATUS_DOWNLOADING -> DownloadState.Downloading(
            trackId = trackId,
            progress = progress,
            bytesDownloaded = bytesDownloaded,
            totalBytes = totalBytes,
        )
        DownloadEntity.STATUS_COMPLETED -> DownloadState.Completed(
            trackId = trackId,
            bytesDownloaded = bytesDownloaded,
            filePath = filePath,
            completedAt = completedAt,
        )
        DownloadEntity.STATUS_FAILED -> DownloadState.Failed(
            trackId = trackId,
            error = errorMessage,
        )
        else -> DownloadState.Removed(trackId)
    }

    override suspend fun getDownloadedTrackIds(): Set<String> =
        downloadDao.getDownloadedTrackIds().toSet()

    override suspend fun getDownloadedAlbumIds(): Set<String> =
        downloadDao.getDownloadedAlbumIds().toSet()

    override suspend fun getDownloadedArtistNames(): Set<String> =
        downloadDao.getDownloadedArtistNames().toSet()
}
