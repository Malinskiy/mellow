package dev.mellow.core.data

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.ServerDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
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
) {
    private val cacheDir = File(context.cacheDir, "artwork")

    suspend fun preCacheArtwork(
        serverId: String,
        onProgress: (SyncProgress) -> Unit = {},
    ) = withContext(Dispatchers.IO) {
        val server = serverDao.getActiveServer() ?: return@withContext
        cacheDir.mkdirs()

        val albumIds = albumDao.getIdsWithImage(serverId)
        val artistIds = artistDao.getIdsWithImage(serverId)
        val playlistIds = playlistDao.getIdsWithImage(serverId)

        val allIds = albumIds + artistIds + playlistIds
        val missing = allIds.filter { !File(cacheDir, "$it.jpg").exists() }

        if (missing.isEmpty()) {
            Log.d(TAG, "Artwork cache up to date (${allIds.size} items)")
            return@withContext
        }

        Log.d(TAG, "Pre-caching ${missing.size} artwork images (${allIds.size} total)")
        var cached = 0
        var failed = 0

        missing.forEachIndexed { index, itemId ->
            if (index % 50 == 0) {
                onProgress(SyncProgress("artwork", index, missing.size))
            }
            val success = downloadArtwork(server.url, server.accessToken, itemId)
            if (success) cached++ else failed++
        }

        onProgress(SyncProgress("artwork", missing.size, missing.size))
        Log.d(TAG, "Artwork pre-cache complete: $cached cached, $failed failed")
    }

    private fun downloadArtwork(serverUrl: String, apiKey: String, itemId: String): Boolean {
        val cacheFile = File(cacheDir, "$itemId.jpg")
        if (cacheFile.exists() && cacheFile.length() > 0) return true

        val imageUrl = "$serverUrl/Items/$itemId/Images/Primary?maxWidth=600&quality=90&api_key=$apiKey"
        return try {
            val tmpFile = File(cacheDir, "$itemId.tmp")
            URL(imageUrl).openStream().use { input ->
                tmpFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            tmpFile.renameTo(cacheFile)
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val TAG = "ArtworkPreCacher"
    }
}
