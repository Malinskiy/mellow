package dev.mellow.core.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ArtworkPreCacher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverDao: ServerDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
) {
    private val cacheDir = File(context.cacheDir, "artwork")

    suspend fun preCacheArtwork(
        serverId: String,
        onProgress: (SyncProgress) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val server = serverDao.getActiveServer() ?: return@withContext
        cacheDir.mkdirs()

        cleanupLegacyJpeg()

        val albumIds = albumDao.getIdsWithImage(serverId)
        val artistIds = artistDao.getIdsWithImage(serverId)
        val playlistIds = playlistDao.getIdsWithImage(serverId)
        val trackIds = trackDao.getOrphanTrackIdsWithImage(serverId)

        val allIds = albumIds + artistIds + playlistIds + trackIds
        val missing = allIds.filter { id ->
            val cached = File(cacheDir, "$id.webp")
            !cached.exists() || cached.length() == 0L
        }

        if (missing.isEmpty()) {
            Log.d(TAG, "Artwork cache up to date (${allIds.size} items)")
            return@withContext
        }

        Log.d(TAG, "Pre-caching ${missing.size} artwork images (${allIds.size} total)")
        var cached = 0
        var failed = 0
        var negCached = 0

        missing.forEachIndexed { index, itemId ->
            if (index % 50 == 0) {
                onProgress(SyncProgress("artwork", index, missing.size))
            }
            when (downloadArtwork(server.url, server.accessToken, itemId)) {
                DownloadResult.SUCCESS -> cached++
                DownloadResult.NOT_FOUND -> negCached++
                DownloadResult.ERROR -> failed++
            }
        }

        onProgress(SyncProgress("artwork", missing.size, missing.size))
        Log.d(TAG, "Artwork pre-cache complete: $cached cached, $negCached not found, $failed failed")
    }

    fun resolveArtwork(serverUrl: String, apiKey: String, itemId: String): File? {
        cacheDir.mkdirs()
        val cacheFile = File(cacheDir, "$itemId.webp")
        if (cacheFile.exists() && cacheFile.length() > 0) return cacheFile
        val noArtMarker = File(cacheDir, "$itemId.noart")
        if (noArtMarker.exists()) return null
        return when (downloadArtwork(serverUrl, apiKey, itemId)) {
            DownloadResult.SUCCESS -> cacheFile
            else -> null
        }
    }

    private fun downloadArtwork(serverUrl: String, apiKey: String, itemId: String): DownloadResult {
        val cacheFile = File(cacheDir, "$itemId.webp")
        if (cacheFile.exists() && cacheFile.length() > 0) return DownloadResult.SUCCESS

        val noArtMarker = File(cacheDir, "$itemId.noart")
        val imageUrl = "$serverUrl/Items/$itemId/Images/Primary?maxWidth=600&quality=90&format=Webp&api_key=$apiKey"

        return try {
            val connection = URL(imageUrl).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true

            val status = connection.responseCode
            if (status == HttpURLConnection.HTTP_NOT_FOUND) {
                noArtMarker.createNewFile()
                connection.disconnect()
                return DownloadResult.NOT_FOUND
            }
            if (status != HttpURLConnection.HTTP_OK) {
                connection.disconnect()
                return DownloadResult.ERROR
            }

            val tmpFile = File(cacheDir, "$itemId.tmp")
            connection.inputStream.use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            connection.disconnect()
            tmpFile.renameTo(cacheFile)
            noArtMarker.delete()
            DownloadResult.SUCCESS
        } catch (_: Exception) {
            DownloadResult.ERROR
        }
    }

    suspend fun preCacheIds(
        itemIds: Set<String>,
    ) = withContext(Dispatchers.IO) {
        val server = serverDao.getActiveServer() ?: return@withContext
        cacheDir.mkdirs()
        val missing = itemIds.filter { id ->
            val cached = File(cacheDir, "$id.webp")
            val noArt = File(cacheDir, "$id.noart")
            (!cached.exists() || cached.length() == 0L) && !noArt.exists()
        }
        if (missing.isEmpty()) return@withContext
        Log.d(TAG, "Pre-caching ${missing.size} priority artwork images")
        missing.forEach { downloadArtwork(server.url, server.accessToken, it) }
    }

    private fun cleanupLegacyJpeg() {
        val jpegFiles = cacheDir.listFiles { f -> f.extension == "jpg" } ?: return
        if (jpegFiles.isEmpty()) return
        Log.d(TAG, "Cleaning up ${jpegFiles.size} legacy JPEG files")
        jpegFiles.forEach { it.delete() }
    }

    private enum class DownloadResult { SUCCESS, NOT_FOUND, ERROR }

    companion object {
        private const val TAG = "ArtworkPreCacher"
        private const val TIMEOUT_MS = 10_000
    }
}
