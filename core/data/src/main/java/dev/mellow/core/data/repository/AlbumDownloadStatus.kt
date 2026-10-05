package dev.mellow.core.data.repository

import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.model.AlbumDownloadState

/**
 * The download state of album [albumId], measured against its [trackCount] tracks in the library rather than against
 * the rows in `downloads`: one track downloaded on its own leaves a 12-track album [AlbumDownloadState.Status.PARTIAL],
 * not complete. [entities] are the downloads of the album's current tracks (DownloadDao.observeAlbumTrackDownloads),
 * at most one per track. Cancelled ([DownloadEntity.STATUS_REMOVED]) rows don't count.
 */
internal fun albumDownloadState(
    albumId: String,
    trackCount: Int,
    entities: List<DownloadEntity>,
): AlbumDownloadState {
    val rows = entities.filter { it.status != DownloadEntity.STATUS_REMOVED }
    val completed = rows.count { it.status == DownloadEntity.STATUS_COMPLETED }
    val inProgress = rows.any {
        it.status == DownloadEntity.STATUS_QUEUED || it.status == DownloadEntity.STATUS_DOWNLOADING
    }
    val status = when {
        inProgress -> AlbumDownloadState.Status.DOWNLOADING
        completed == 0 -> AlbumDownloadState.Status.NONE
        completed >= trackCount -> AlbumDownloadState.Status.COMPLETED
        else -> AlbumDownloadState.Status.PARTIAL
    }
    return AlbumDownloadState(
        albumId = albumId,
        totalTracks = trackCount,
        downloadedTracks = completed,
        totalBytes = rows.sumOf { it.totalBytes },
        downloadedBytes = rows.sumOf { it.bytesDownloaded },
        overallStatus = status,
    )
}
