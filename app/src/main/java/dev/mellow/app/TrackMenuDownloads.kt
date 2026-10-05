package dev.mellow.app

import dev.mellow.core.designsystem.component.TrackMenuDownload
import dev.mellow.core.model.DownloadState
import dev.mellow.core.network.ConnectionState

/**
 * The track menu's download entry for a track whose download row is [download]. A download on the device or in progress
 * can always be removed or cancelled; only a new download needs the server and room under the storage cap.
 */
internal fun trackMenuDownload(
    download: DownloadState?,
    connection: ConnectionState,
    storageFull: Boolean,
): TrackMenuDownload = when {
    download is DownloadState.Completed -> TrackMenuDownload.Downloaded
    download is DownloadState.Queued || download is DownloadState.Downloading -> TrackMenuDownload.Downloading
    connection !is ConnectionState.Connected -> TrackMenuDownload.Offline
    storageFull -> TrackMenuDownload.StorageFull
    else -> TrackMenuDownload.Available
}

/** Downloads have reached the storage cap, the same check an album download makes. No cap is `Long.MAX_VALUE`. */
internal fun isStorageFull(usedBytes: Long, capBytes: Long): Boolean =
    capBytes != Long.MAX_VALUE && usedBytes >= capBytes
