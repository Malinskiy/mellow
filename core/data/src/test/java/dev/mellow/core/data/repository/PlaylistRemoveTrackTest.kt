package dev.mellow.core.data.repository

import dev.mellow.core.common.MellowResult
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.network.datasource.JellyfinDataSource
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.jellyfin.sdk.model.api.BaseItemDto
import org.jellyfin.sdk.model.api.BaseItemKind
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

/** Removing a track from a playlist on the server goes by the playlist entry's ID, which isn't the track's. */
class PlaylistRemoveTrackTest {

    private val playlistDao = mockk<PlaylistDao>(relaxUnitFun = true)
    private val serverDao = mockk<ServerDao> {
        coEvery { getActiveServer() } returns ServerEntity(
            id = "server",
            name = "Jellyfin",
            url = "https://jellyfin.example",
            userId = USER.toString(),
            accessToken = "token",
            isActive = true,
            lastConnected = 0,
        )
    }
    private val dataSource = mockk<JellyfinDataSource>(relaxUnitFun = true)
    private val repository = PlaylistRepositoryImpl(playlistDao, mockk<TrackDao>(), serverDao, dataSource)

    @Test
    fun `track is removed on the server by its playlist entry ID`() = runTest {
        coEvery { dataSource.getPlaylistItems(PLAYLIST, USER) } returns listOf(
            entry(OTHER_TRACK, "entry-other"),
            entry(TRACK, "entry-1"),
        )

        val result = repository.removeTrackFromPlaylist(PLAYLIST.toString(), TRACK.toString())

        assertTrue(result is MellowResult.Success)
        coVerify { playlistDao.removeTrackFromPlaylist(PLAYLIST.toString(), TRACK.toString()) }
        coVerify(exactly = 1) { dataSource.removeFromPlaylist(PLAYLIST.toString(), listOf("entry-1")) }
    }

    @Test
    fun `every entry of a track that is in the playlist more than once is removed`() = runTest {
        coEvery { dataSource.getPlaylistItems(PLAYLIST, USER) } returns listOf(
            entry(TRACK, "entry-1"),
            entry(OTHER_TRACK, "entry-other"),
            entry(TRACK, "entry-2"),
        )

        repository.removeTrackFromPlaylist(PLAYLIST.toString(), TRACK.toString())

        coVerify(exactly = 1) { dataSource.removeFromPlaylist(PLAYLIST.toString(), listOf("entry-1", "entry-2")) }
    }

    @Test
    fun `track the server's playlist no longer has asks the server to remove nothing`() = runTest {
        coEvery { dataSource.getPlaylistItems(PLAYLIST, USER) } returns listOf(entry(OTHER_TRACK, "entry-other"))

        val result = repository.removeTrackFromPlaylist(PLAYLIST.toString(), TRACK.toString())

        assertTrue(result is MellowResult.Success)
        coVerify(exactly = 0) { dataSource.removeFromPlaylist(any(), any()) }
    }

    private fun entry(track: UUID, entryId: String) =
        BaseItemDto(id = track, type = BaseItemKind.AUDIO, playlistItemId = entryId)

    private companion object {
        val USER: UUID = UUID(9, 9)
        val PLAYLIST: UUID = UUID(5, 5)
        val TRACK: UUID = UUID(3, 1)
        val OTHER_TRACK: UUID = UUID(3, 2)
    }
}
