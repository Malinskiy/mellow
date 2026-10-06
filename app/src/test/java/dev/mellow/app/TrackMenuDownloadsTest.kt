package dev.mellow.app

import dev.mellow.core.designsystem.component.TrackMenuDownload
import dev.mellow.core.model.DownloadState
import dev.mellow.core.network.ConnectionState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackMenuDownloadsTest {

    private val completed = DownloadState.Completed(TRACK, bytesDownloaded = 1L, filePath = null, completedAt = 1L)
    private val queued = DownloadState.Queued(TRACK)
    private val downloading = DownloadState.Downloading(TRACK, progress = 0.5f, bytesDownloaded = 1L, totalBytes = 2L)

    @Test
    fun `a track that isn't downloaded can be downloaded when online with room to spare`() {
        assertEquals(TrackMenuDownload.Available, trackMenuDownload(null, ConnectionState.Connected, false))
        assertEquals(
            TrackMenuDownload.Available,
            trackMenuDownload(DownloadState.Failed(TRACK, "boom"), ConnectionState.Connected, false),
        )
        assertEquals(
            TrackMenuDownload.Available,
            trackMenuDownload(DownloadState.Removed(TRACK), ConnectionState.Connected, false),
        )
    }

    @Test
    fun `a downloaded track can always be removed`() {
        for (connection in CONNECTIONS) {
            for (storageFull in listOf(false, true)) {
                assertEquals(TrackMenuDownload.Downloaded, trackMenuDownload(completed, connection, storageFull))
            }
        }
    }

    @Test
    fun `a queued or downloading track can always be cancelled`() {
        for (connection in CONNECTIONS) {
            for (storageFull in listOf(false, true)) {
                assertEquals(TrackMenuDownload.Downloading, trackMenuDownload(queued, connection, storageFull))
                assertEquals(TrackMenuDownload.Downloading, trackMenuDownload(downloading, connection, storageFull))
            }
        }
    }

    @Test
    fun `without the server a new download is disabled as offline, before the storage limit`() {
        assertEquals(TrackMenuDownload.Offline, trackMenuDownload(null, ConnectionState.Offline, false))
        assertEquals(TrackMenuDownload.Offline, trackMenuDownload(null, ConnectionState.ServerUnreachable, false))
        assertEquals(TrackMenuDownload.Offline, trackMenuDownload(null, ConnectionState.Offline, true))
    }

    @Test
    fun `online at the storage limit a new download is disabled`() {
        assertEquals(TrackMenuDownload.StorageFull, trackMenuDownload(null, ConnectionState.Connected, true))
    }

    @Test
    fun `storage is full once downloads reach the cap, never without a cap`() {
        assertFalse(isStorageFull(usedBytes = 99L, capBytes = 100L))
        assertTrue(isStorageFull(usedBytes = 100L, capBytes = 100L))
        assertFalse(isStorageFull(usedBytes = Long.MAX_VALUE, capBytes = Long.MAX_VALUE))
    }

    private companion object {
        const val TRACK = "track"
        val CONNECTIONS = listOf(ConnectionState.Connected, ConnectionState.ServerUnreachable, ConnectionState.Offline)
    }
}
