package dev.mellow.core.data.repository

import dev.mellow.core.common.DownloadExecutor
import dev.mellow.core.common.MellowResult
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.model.AlbumDownloadState
import dev.mellow.core.model.DownloadState
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DownloadRepositoryImpl @Inject constructor(
    private val downloadDao: DownloadDao,
    private val serverDao: ServerDao,
    private val downloadExecutor: DownloadExecutor,
) : DownloadRepository {

    override val liveProgress: StateFlow<Map<String, Float>> = downloadExecutor.downloadProgress

    override fun observeDownload(trackId: String): Flow<MellowResult<DownloadState?>> =
        downloadDao.observeDownload(trackId)
            .map { MellowResult.Success(it?.toDomainModel()) as MellowResult<DownloadState?> }
            .catch { emit(MellowResult.Error(it)) }

    override fun observeAlbumDownloads(albumId: String): Flow<MellowResult<AlbumDownloadState>> =
        combine(
            downloadDao.observeAlbumTrackCount(albumId),
            downloadDao.observeAlbumTrackDownloads(albumId),
        ) { trackCount, entities ->
            MellowResult.Success(albumDownloadState(albumId, trackCount, entities)) as MellowResult<AlbumDownloadState>
        }.catch { emit(MellowResult.Error(it)) }

    override fun observeActiveDownloads(): Flow<MellowResult<List<DownloadState>>> =
        downloadDao.observeActiveDownloads().map { entities ->
            MellowResult.Success(entities.map { it.toDomainModel() }) as MellowResult<List<DownloadState>>
        }.catch { emit(MellowResult.Error(it)) }

    override fun getTotalDownloadedBytes(): Flow<MellowResult<Long>> =
        downloadDao.getTotalDownloadedBytes()
            .map { MellowResult.Success(it) as MellowResult<Long> }
            .catch { emit(MellowResult.Error(it)) }

    override fun isTrackDownloaded(trackId: String): Flow<MellowResult<Boolean>> =
        downloadDao.isDownloaded(trackId)
            .map { MellowResult.Success(it) as MellowResult<Boolean> }
            .catch { emit(MellowResult.Error(it)) }

    /**
     * Serializes the operations that claim, cancel and remove downloads, so reading a track's row, deciding and
     * writing it (and starting or removing its download) is one step: two taps can't both claim a track and start it
     * twice, and a cancel can't land between a claim's row and its start. A Room transaction would make only the row
     * writes atomic, not the executor calls that follow them; the repository is a singleton, so one lock covers every
     * caller.
     */
    private val queueLock = Mutex()

    override suspend fun downloadTrack(
        track: Track,
        serverId: String,
        quality: String,
    ): MellowResult<Unit> = queueLock.withLock {
        try {
            val server = serverDao.getActiveServer()
                ?: return MellowResult.Error(IllegalStateException("No active server"))
            // Already queued, downloading or on the device: nothing to do.
            if (downloadDao.getDownload(track.id)?.isClaimed() == true) return MellowResult.Success(Unit)
            val now = System.currentTimeMillis()
            downloadDao.upsert(queuedEntity(track.id, track.albumId, serverId, quality, now))
            downloadExecutor.startDownload(track.id, server.url, server.accessToken, quality)
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun downloadAlbum(
        albumId: String,
        tracks: List<Track>,
        serverId: String,
        quality: String,
    ): MellowResult<Unit> = queueLock.withLock {
        try {
            val server = serverDao.getActiveServer()
                ?: return MellowResult.Error(IllegalStateException("No active server"))
            val now = System.currentTimeMillis()
            // Tracks already queued, downloading or on the device (e.g. downloaded one by one) stay as they are; only
            // the rest (no row, failed or cancelled) is queued.
            val claimedIds = tracks.map { it.id }.chunked(MAX_IDS_PER_QUERY)
                .flatMap { ids -> downloadDao.getDownloads(ids) }
                .filter { it.isClaimed() }
                .mapTo(HashSet()) { it.trackId }
            val missing = tracks.filter { it.id !in claimedIds }
            downloadDao.upsertAll(missing.map { queuedEntity(it.id, albumId, serverId, quality, now) })
            missing.forEach { track ->
                downloadExecutor.startDownload(track.id, server.url, server.accessToken, quality)
            }
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun cancelDownload(trackId: String): MellowResult<Unit> = queueLock.withLock {
        try {
            downloadExecutor.removeDownload(trackId)
            downloadDao.getDownload(trackId)?.let { entity ->
                downloadDao.upsert(
                    entity.copy(
                        status = DownloadEntity.STATUS_REMOVED,
                        lastSynced = System.currentTimeMillis(),
                    ),
                )
            }
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun removeDownload(trackId: String): MellowResult<Unit> = queueLock.withLock {
        try {
            downloadExecutor.removeDownload(trackId)
            downloadDao.delete(trackId)
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun removeAlbumDownloads(albumId: String): MellowResult<Unit> = queueLock.withLock {
        try {
            // By the album's id, so downloads of tracks the library no longer lists under it go too.
            val downloads = downloadDao.getDownloadsByAlbum(albumId)
            downloads.forEach { downloadExecutor.removeDownload(it.trackId) }
            downloadDao.deleteByAlbum(albumId)
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    override suspend fun clearAllDownloads(): MellowResult<Unit> = queueLock.withLock {
        try {
            val downloads = downloadDao.getAllDownloads()
            downloads.forEach { downloadExecutor.removeDownload(it.trackId) }
            downloadDao.deleteAll()
            MellowResult.Success(Unit)
        } catch (e: Exception) {
            MellowResult.Error(e)
        }
    }

    private fun DownloadEntity.isClaimed(): Boolean = status == DownloadEntity.STATUS_QUEUED ||
        status == DownloadEntity.STATUS_DOWNLOADING ||
        status == DownloadEntity.STATUS_COMPLETED

    private fun queuedEntity(trackId: String, albumId: String?, serverId: String, quality: String, now: Long) =
        DownloadEntity(
            trackId = trackId,
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

    override suspend fun getDownloadedTrackIds(): MellowResult<Set<String>> =
        try {
            MellowResult.Success(downloadDao.getDownloadedTrackIds().toSet())
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun getDownloadedAlbumIds(): MellowResult<Set<String>> =
        try {
            MellowResult.Success(downloadDao.getDownloadedAlbumIds().toSet())
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    override suspend fun getDownloadedArtistNames(): MellowResult<Set<String>> =
        try {
            MellowResult.Success(downloadDao.getDownloadedArtistNames().toSet())
        } catch (e: Exception) {
            MellowResult.Error(e)
        }

    private companion object {
        /** Under SQLite's 999 bound arguments on Android 8. */
        const val MAX_IDS_PER_QUERY = 500
    }
}
