package dev.mellow.core.data.repository

import dev.mellow.core.model.AlbumDownloadState
import dev.mellow.core.model.DownloadState
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface DownloadRepository {
    val liveProgress: StateFlow<Map<String, Float>>
    fun observeDownload(trackId: String): Flow<DownloadState?>
    fun observeAlbumDownloads(albumId: String): Flow<AlbumDownloadState>
    fun observeActiveDownloads(): Flow<List<DownloadState>>
    fun getTotalDownloadedBytes(): Flow<Long>
    fun isTrackDownloaded(trackId: String): Flow<Boolean>
    suspend fun downloadTrack(track: Track, serverId: String, quality: String)
    suspend fun downloadAlbum(albumId: String, tracks: List<Track>, serverId: String, quality: String)
    suspend fun cancelDownload(trackId: String)
    suspend fun removeDownload(trackId: String)
    suspend fun removeAlbumDownloads(albumId: String)
    suspend fun clearAllDownloads()
    suspend fun getDownloadedTrackIds(): Set<String>
    suspend fun getDownloadedAlbumIds(): Set<String>
    suspend fun getDownloadedArtistNames(): Set<String>
}
