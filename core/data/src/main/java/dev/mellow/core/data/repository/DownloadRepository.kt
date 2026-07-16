package dev.mellow.core.data.repository

import dev.mellow.core.common.MellowResult
import dev.mellow.core.model.AlbumDownloadState
import dev.mellow.core.model.DownloadState
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DownloadRepository {
    val liveProgress: StateFlow<Map<String, Float>>
    fun observeDownload(trackId: String): Flow<MellowResult<DownloadState?>>
    fun observeAlbumDownloads(albumId: String): Flow<MellowResult<AlbumDownloadState>>
    fun observeActiveDownloads(): Flow<MellowResult<List<DownloadState>>>
    fun getTotalDownloadedBytes(): Flow<MellowResult<Long>>
    fun isTrackDownloaded(trackId: String): Flow<MellowResult<Boolean>>
    suspend fun downloadTrack(track: Track, serverId: String, quality: String): MellowResult<Unit>
    suspend fun downloadAlbum(albumId: String, tracks: List<Track>, serverId: String, quality: String): MellowResult<Unit>
    suspend fun cancelDownload(trackId: String): MellowResult<Unit>
    suspend fun removeDownload(trackId: String): MellowResult<Unit>
    suspend fun removeAlbumDownloads(albumId: String): MellowResult<Unit>
    suspend fun clearAllDownloads(): MellowResult<Unit>
    suspend fun getDownloadedTrackIds(): MellowResult<Set<String>>
    suspend fun getDownloadedAlbumIds(): MellowResult<Set<String>>
    suspend fun getDownloadedArtistNames(): MellowResult<Set<String>>
}
