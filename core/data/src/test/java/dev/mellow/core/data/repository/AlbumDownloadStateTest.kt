package dev.mellow.core.data.repository

import dev.mellow.core.common.DownloadExecutor
import dev.mellow.core.common.MellowResult
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.model.AlbumDownloadState.Status
import dev.mellow.core.model.Track
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import java.time.Duration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/** An album's download state counts the album's tracks, so single-track downloads leave it partial. */
class AlbumDownloadStateTest {

    @Test
    fun `no downloads is none`() {
        val state = albumDownloadState(ALBUM, trackCount = 12, entities = emptyList())

        assertEquals(Status.NONE, state.overallStatus)
        assertEquals(0, state.downloadedTracks)
        assertEquals(12, state.totalTracks)
    }

    @Test
    fun `one downloaded track of twelve is partial`() {
        val state = albumDownloadState(ALBUM, 12, listOf(row("t1", DownloadEntity.STATUS_COMPLETED)))

        assertEquals(Status.PARTIAL, state.overallStatus)
        assertEquals(1, state.downloadedTracks)
        assertEquals(12, state.totalTracks)
    }

    @Test
    fun `every track downloaded is completed`() {
        val rows = (1..12).map { row("t$it", DownloadEntity.STATUS_COMPLETED) }

        val state = albumDownloadState(ALBUM, 12, rows)

        assertEquals(Status.COMPLETED, state.overallStatus)
        assertEquals(12, state.downloadedTracks)
    }

    @Test
    fun `a queued or downloading track is downloading`() {
        val queued = albumDownloadState(
            ALBUM,
            12,
            listOf(row("t1", DownloadEntity.STATUS_COMPLETED), row("t2", DownloadEntity.STATUS_QUEUED)),
        )
        val downloading = albumDownloadState(ALBUM, 12, listOf(row("t1", DownloadEntity.STATUS_DOWNLOADING)))

        assertEquals(Status.DOWNLOADING, queued.overallStatus)
        assertEquals(1, queued.downloadedTracks)
        assertEquals(Status.DOWNLOADING, downloading.overallStatus)
    }

    @Test
    fun `failed rows are not downloads`() {
        val onlyFailed = albumDownloadState(ALBUM, 12, listOf(row("t1", DownloadEntity.STATUS_FAILED)))
        val someFailed = albumDownloadState(
            ALBUM,
            3,
            listOf(
                row("t1", DownloadEntity.STATUS_COMPLETED),
                row("t2", DownloadEntity.STATUS_COMPLETED),
                row("t3", DownloadEntity.STATUS_FAILED),
            ),
        )

        assertEquals(Status.NONE, onlyFailed.overallStatus)
        assertEquals(Status.PARTIAL, someFailed.overallStatus)
        assertEquals(2, someFailed.downloadedTracks)
    }

    @Test
    fun `cancelled rows are ignored`() {
        val state = albumDownloadState(
            ALBUM,
            2,
            listOf(row("t1", DownloadEntity.STATUS_COMPLETED), row("t2", DownloadEntity.STATUS_REMOVED)),
        )

        assertEquals(Status.PARTIAL, state.overallStatus)
    }

    @Test
    fun `downloads of tracks the library no longer lists never exceed the total`() {
        val state = albumDownloadState(
            ALBUM,
            1,
            listOf(row("t1", DownloadEntity.STATUS_COMPLETED), row("gone", DownloadEntity.STATUS_COMPLETED)),
        )

        assertEquals(Status.COMPLETED, state.overallStatus)
        assertEquals(2, state.totalTracks)
    }

    @Test
    fun `downloading an album queues only the tracks not yet downloaded`() = runTest {
        val downloadDao = mockk<DownloadDao>(relaxUnitFun = true)
        val serverDao = mockk<ServerDao>()
        val executor = mockk<DownloadExecutor>(relaxUnitFun = true)
        every { executor.downloadProgress } returns MutableStateFlow(emptyMap())
        coEvery { serverDao.getActiveServer() } returns server()
        coEvery { downloadDao.getDownloadsByAlbum(ALBUM) } returns listOf(
            row("t1", DownloadEntity.STATUS_COMPLETED),
            row("t2", DownloadEntity.STATUS_FAILED),
        )
        val queued = slot<List<DownloadEntity>>()
        coEvery { downloadDao.upsertAll(capture(queued)) } returns Unit
        val repository = DownloadRepositoryImpl(downloadDao, serverDao, executor)

        val result = repository.downloadAlbum(ALBUM, listOf(track("t1"), track("t2"), track("t3")), SERVER, "original")

        assertEquals(MellowResult.Success(Unit), result)
        assertEquals(listOf("t2", "t3"), queued.captured.map { it.trackId })
        verify(exactly = 0) { executor.startDownload("t1", any(), any(), any()) }
        verify { executor.startDownload("t2", URL, TOKEN, "original") }
        verify { executor.startDownload("t3", URL, TOKEN, "original") }
        coVerify(exactly = 0) { downloadDao.upsert(any()) }
    }

    private fun row(trackId: String, status: Int) = DownloadEntity(
        trackId = trackId,
        albumId = ALBUM,
        serverId = SERVER,
        status = status,
        progress = 0f,
        bytesDownloaded = 0L,
        totalBytes = 0L,
        quality = "original",
        filePath = null,
        requestedAt = 0L,
        completedAt = 0L,
        errorMessage = null,
        lastSynced = 0L,
    )

    private fun track(id: String) = Track(
        id = id,
        name = id,
        albumId = ALBUM,
        albumName = "Album",
        artistId = null,
        artistName = null,
        trackNumber = null,
        discNumber = null,
        duration = Duration.ZERO,
        genres = emptyList(),
        imageId = null,
        isFavorite = false,
        playCount = 0,
        lastPlayedAt = 0L,
        normalizationGain = null,
    )

    private fun server() = ServerEntity(
        id = SERVER,
        name = "Jellyfin",
        url = URL,
        userId = "user",
        accessToken = TOKEN,
        isActive = true,
        lastConnected = 0L,
    )

    private companion object {
        const val ALBUM = "album"
        const val SERVER = "server"
        const val URL = "https://jellyfin.example"
        const val TOKEN = "token"
    }
}
