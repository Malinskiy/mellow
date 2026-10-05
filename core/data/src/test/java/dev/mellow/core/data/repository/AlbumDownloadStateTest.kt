package dev.mellow.core.data.repository

import androidx.room.Room
import dev.mellow.core.common.DownloadExecutor
import dev.mellow.core.common.MellowResult
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.database.entity.TrackEntity
import dev.mellow.core.model.AlbumDownloadState.Status
import dev.mellow.core.model.Track
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import java.time.Duration
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * An album's download state counts the album's current tracks, so single-track downloads leave it partial; and queuing
 * downloads never queues a track twice.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AlbumDownloadStateTest {

    private lateinit var db: MellowDatabase
    private val executor = mockk<DownloadExecutor>(relaxUnitFun = true) {
        every { downloadProgress } returns MutableStateFlow(emptyMap())
    }

    /** The fake repository's downloads table. */
    private val rows = mutableMapOf<String, DownloadEntity>()

    @Before
    fun setUp() {
        db = Room.inMemoryDatabaseBuilder(RuntimeEnvironment.getApplication(), MellowDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() = db.close()

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
    fun `an orphaned download doesn't hide the album's missing track`() = runTest {
        // 11 current tracks: 10 downloaded, 1 not. Plus a download of a track the library no longer lists.
        val current = (1..11).map { "t$it" }
        db.trackDao().upsertTrackRows(current.map { trackRow(it) })
        db.downloadDao().upsertAll(current.take(10).map { row(it, DownloadEntity.STATUS_COMPLETED) })
        db.downloadDao().upsert(row("orphan", DownloadEntity.STATUS_COMPLETED))
        val repository = DownloadRepositoryImpl(db.downloadDao(), db.serverDao(), executor)

        val state = (repository.observeAlbumDownloads(ALBUM).first() as MellowResult.Success).data

        assertEquals(Status.PARTIAL, state.overallStatus)
        assertEquals(11, state.totalTracks)
        assertEquals(10, state.downloadedTracks)

        // Removing the album's downloads still removes the orphan, by the album's id.
        repository.removeAlbumDownloads(ALBUM)
        assertNull(db.downloadDao().getDownload("orphan"))
        verify { executor.removeDownload("orphan") }
    }

    @Test
    fun `a download of the album's track on another server isn't the album's`() = runTest {
        db.trackDao().upsertTrackRows(listOf(trackRow("t1")))
        db.downloadDao().upsert(row("t1", DownloadEntity.STATUS_COMPLETED).copy(serverId = "other"))
        val repository = DownloadRepositoryImpl(db.downloadDao(), db.serverDao(), executor)

        val state = (repository.observeAlbumDownloads(ALBUM).first() as MellowResult.Success).data

        assertEquals(Status.NONE, state.overallStatus)
    }

    @Test
    fun `downloading an album queues only absent, failed and cancelled tracks`() = runTest {
        rows["queued"] = row("queued", DownloadEntity.STATUS_QUEUED)
        rows["downloading"] = row("downloading", DownloadEntity.STATUS_DOWNLOADING)
        rows["completed"] = row("completed", DownloadEntity.STATUS_COMPLETED)
        rows["failed"] = row("failed", DownloadEntity.STATUS_FAILED)
        rows["cancelled"] = row("cancelled", DownloadEntity.STATUS_REMOVED)
        val before = rows.toMap()
        val tracks = listOf("queued", "downloading", "completed", "failed", "cancelled", "absent").map { track(it) }

        val result = fakeRepository().downloadAlbum(ALBUM, tracks, SERVER, "original")

        assertEquals(MellowResult.Success(Unit), result)
        for (active in listOf("queued", "downloading", "completed")) {
            assertEquals("$active was reset", before[active], rows[active])
            verify(exactly = 0) { executor.startDownload(active, any(), any(), any()) }
        }
        for (queued in listOf("failed", "cancelled", "absent")) {
            assertEquals(DownloadEntity.STATUS_QUEUED, rows.getValue(queued).status)
            verify(exactly = 1) { executor.startDownload(queued, URL, TOKEN, "original") }
        }
    }

    @Test
    fun `downloading an album twice at once queues each track once`() = runTest {
        val repository = fakeRepository()
        val tracks = listOf(track("t1"), track("t2"))

        val first = async { repository.downloadAlbum(ALBUM, tracks, SERVER, "original") }
        val second = async { repository.downloadAlbum(ALBUM, tracks, SERVER, "original") }
        awaitAll(first, second)

        verify(exactly = 1) { executor.startDownload("t1", any(), any(), any()) }
        verify(exactly = 1) { executor.startDownload("t2", any(), any(), any()) }
    }

    @Test
    fun `downloading a track that is queued, downloading or downloaded does nothing`() = runTest {
        val active = listOf(
            DownloadEntity.STATUS_QUEUED,
            DownloadEntity.STATUS_DOWNLOADING,
            DownloadEntity.STATUS_COMPLETED,
        )
        for (status in active) {
            val row = row("t$status", status)
            rows[row.trackId] = row

            fakeRepository().downloadTrack(track(row.trackId), SERVER, "original")

            assertEquals(row, rows[row.trackId])
            verify(exactly = 0) { executor.startDownload(row.trackId, any(), any(), any()) }
        }
    }

    @Test
    fun `downloading a track twice at once queues it once`() = runTest {
        val repository = fakeRepository()

        val first = async { repository.downloadTrack(track("t1"), SERVER, "original") }
        val second = async { repository.downloadTrack(track("t1"), SERVER, "original") }
        awaitAll(first, second)

        assertEquals(DownloadEntity.STATUS_QUEUED, rows.getValue("t1").status)
        verify(exactly = 1) { executor.startDownload("t1", any(), any(), any()) }
    }

    /**
     * A repository over [rows]. Its writes suspend before they land, so a second caller that weren't kept out would
     * read the rows the first one has yet to write.
     */
    private fun fakeRepository(): DownloadRepositoryImpl {
        val dao = mockk<DownloadDao>()
        val serverDao = mockk<ServerDao>()
        coEvery { serverDao.getActiveServer() } returns server()
        coEvery { dao.getDownload(any()) } answers { rows[firstArg()] }
        coEvery { dao.getDownloads(any()) } answers { firstArg<List<String>>().mapNotNull { rows[it] } }
        coEvery { dao.upsert(any()) } coAnswers {
            yield()
            val row = firstArg<DownloadEntity>()
            rows[row.trackId] = row
        }
        coEvery { dao.upsertAll(any()) } coAnswers {
            yield()
            firstArg<List<DownloadEntity>>().forEach { rows[it.trackId] = it }
        }
        return DownloadRepositoryImpl(dao, serverDao, executor)
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

    private fun trackRow(id: String) = TrackEntity(
        id = id,
        serverId = SERVER,
        name = id,
        sortName = id,
        albumId = ALBUM,
        albumName = "Album",
        artistId = null,
        artistName = null,
        trackNumber = null,
        discNumber = null,
        durationMs = 0L,
        genres = emptyList(),
        imageTag = null,
        isFavorite = false,
        playCount = 0,
        lastPlayedAt = 0L,
        normalizationGain = null,
        container = null,
        codec = null,
        bitrate = null,
        sampleRate = null,
        channels = null,
        resolvedArtistId = null,
        dateAdded = 0L,
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
