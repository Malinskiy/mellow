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
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.UUID

class PlaylistCreateTest {

    private val playlistDao = mockk<PlaylistDao>(relaxUnitFun = true)
    private val serverDao = mockk<ServerDao> {
        coEvery { getActiveServer() } returns ServerEntity(
            id = SERVER,
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
    fun `new playlist is saved under the ID sync gives it, so tracks can be added to it at once`() = runTest {
        // Jellyfin answers with the ID without dashes.
        coEvery { dataSource.createPlaylist("Road trip", USER) } returns "a7fb7c549b42111545830d52c8751489"

        val result = repository.createPlaylist("Road trip", SERVER)

        assertEquals(MellowResult.Success(PLAYLIST), result)
        coVerify { playlistDao.upsert(match { it.id == PLAYLIST }) }

        coEvery { playlistDao.getPlaylistById(PLAYLIST) } returns null
        coEvery { playlistDao.insertPlaylistTracks(any()) } returns Unit
        repository.addTrackToPlaylist(PLAYLIST, TRACK.toString(), SERVER)

        coVerify { dataSource.addToPlaylist(UUID.fromString(PLAYLIST), listOf(TRACK), USER) }
    }

    private companion object {
        const val SERVER = "server"
        const val PLAYLIST = "a7fb7c54-9b42-1115-4583-0d52c8751489"
        val USER: UUID = UUID(9, 9)
        val TRACK: UUID = UUID(3, 1)
    }
}
