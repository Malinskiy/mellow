package dev.mellow.core.data

import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.network.ConnectionState
import dev.mellow.core.network.NetworkStateObserver

/**
 * Keeps local copies of item artwork for the artwork provider, so covers show offline. Per item, in durable storage:
 * - `{id}.webp`: the image. A download replaces it only once complete, so readers get either the old or the new one.
 * - `{id}.tag`: the Room image tag the image was downloaded for. Missing when unknown (fetched on demand, or kept from
 *   an older version's cache): precache then takes the image to be the current one rather than downloading it again.
 * - `{id}.noart`: the server has no image for the item; holds the tag that was checked (empty when unknown).
 */
@Singleton
class ArtworkPreCacher @Inject constructor(
    @ApplicationContext private val context: Context,
    private val serverDao: ServerDao,
    private val albumDao: AlbumDao,
    private val artistDao: ArtistDao,
    private val playlistDao: PlaylistDao,
    private val trackDao: TrackDao,
    private val networkStateObserver: NetworkStateObserver,
) {
    /**
     * Not in `cacheDir`, which Android clears when storage runs low, and excluded from backups: it's large and can be
     * downloaded again. Resolved on first use, as looking up app directories may touch the disk.
     */
    private val artworkDir by lazy { File(context.noBackupFilesDir, ARTWORK_DIR) }

    private val storageLock = Any()

    @Volatile
    private var storageReady = false

    /**
     * Downloads of the same item are serialized; a fixed set of locks keeps memory bounded whatever ids are asked for.
     */
    private val downloadLocks = Array(LOCK_STRIPES) { Any() }

    /** Until when (System.nanoTime) on-demand requests skip the network after a failed connection to the server. */
    @Volatile
    private var skipNetworkUntil: Long? = null

    /**
     * Caches artwork for the albums, artists, playlists and album-less tracks with an image in Room: images that aren't
     * cached yet, and ones cached for an older image tag (still served until the new image is in place). Items the
     * server had no image for are checked again. Downloads run [PARALLEL_DOWNLOADS] at a time.
     *
     * @throws ArtworkServerUnreachableException if the server can't be reached: the run stops there and leaves the
     *   remaining items to the next one.
     */
    suspend fun preCacheArtwork(
        serverId: String,
        onProgress: (SyncProgress) -> Unit = {},
    ): Unit = withContext(Dispatchers.IO) {
        val server = serverDao.getActiveServer() ?: return@withContext
        ensureStorage()
        deleteAbandonedTempFiles()

        val items = imageTags(serverId).map { (id, tag) -> ArtworkItem(id, tag) }
        val missing = itemsToDownload(items, recheckNoArt = true)
        if (missing.isEmpty()) {
            Log.d(TAG, "Artwork cache up to date (${items.size} items)")
            return@withContext
        }

        Log.d(TAG, "Pre-caching ${missing.size} artwork images (${items.size} total)")
        val tally = downloadAll(server, missing, recheckNoArt = true, onProgress)
        Log.d(TAG, "Artwork pre-cache complete: $tally")
    }

    /**
     * Caches artwork for [itemIds] (what the home screen shows) like [preCacheArtwork], except that items the server
     * had no image for are skipped, unless Room now has an image tag they weren't checked for.
     *
     * @throws ArtworkServerUnreachableException if the server can't be reached.
     */
    suspend fun preCacheIds(
        itemIds: Set<String>,
    ): Unit = withContext(Dispatchers.IO) {
        val server = serverDao.getActiveServer() ?: return@withContext
        ensureStorage()
        val tags = imageTags(server.id)
        val missing = itemsToDownload(itemIds.map { ArtworkItem(it, tags[it]) }, recheckNoArt = false)
        if (missing.isEmpty()) return@withContext
        Log.d(TAG, "Pre-caching ${missing.size} priority artwork images")
        downloadAll(server, missing, recheckNoArt = false, onProgress = {})
    }

    /**
     * Artwork file for [itemId], downloading it if needed. A track whose own artwork can't be had (none on the server,
     * or not cached while the server is unreachable) falls back to its album's artwork. A cached image is served even
     * if Room has a newer tag for it; precache replaces it.
     *
     * While the server is known to be unreachable, uncached items fail fast instead of each waiting for the connection
     * timeout, so cached artwork (including album art for tracks) shows up immediately.
     */
    fun resolveArtwork(serverUrl: String, apiKey: String, itemId: String): File? {
        ensureStorage()
        cachedArtwork(itemId)?.let { return it }
        download(serverUrl, apiKey, itemId)?.let { return it }
        val albumId = runBlocking { trackDao.getTrackById(itemId) }?.albumId?.takeIf { it != itemId } ?: return null
        return cachedArtwork(albumId) ?: download(serverUrl, apiKey, albumId)
    }

    /**
     * Creates the artwork directory and, once per process, moves in what earlier versions cached in `cacheDir`. Runs on
     * the thread of the first caller: precache or the artwork provider, never the main thread.
     */
    private fun ensureStorage() {
        if (storageReady) return
        synchronized(storageLock) {
            if (storageReady) return
            artworkDir.mkdirs()
            moveLegacyCache()
            storageReady = true
        }
    }

    /** Moves images and no-art markers out of the old cache directory, so they needn't be downloaded again. */
    private fun moveLegacyCache() {
        val legacyDir = File(context.cacheDir, ARTWORK_DIR)
        val files = legacyDir.listFiles() ?: return
        var moved = 0
        for (file in files) {
            val target = File(artworkDir, file.name)
            val worthKeeping = file.extension == IMAGE_EXTENSION || file.extension == NO_ART_EXTENSION
            if (!worthKeeping || target.exists()) {
                // Temp files and legacy JPEGs, or a copy already moved by an interrupted run.
                file.delete()
            } else if (moveFile(file, target)) {
                moved++
            }
        }
        if (legacyDir.delete()) {
            Log.i(TAG, "Moved $moved cached artwork files to durable storage")
        } else {
            Log.w(TAG, "Moved $moved cached artwork files, the rest on the next start")
        }
    }

    /** Renames [source] to [target]; across filesystems, copies it into place and deletes the original. */
    private fun moveFile(source: File, target: File): Boolean {
        if (source.renameTo(target)) return true
        var tmpFile: File? = null
        return try {
            val tmp = File.createTempFile(source.name, TMP_SUFFIX, artworkDir).also { tmpFile = it }
            source.copyTo(tmp, overwrite = true)
            tmp.renameTo(target) && source.delete()
        } catch (e: IOException) {
            Log.w(TAG, "Could not move cached artwork ${source.name}", e)
            false
        } finally {
            tmpFile?.takeIf { it.exists() }?.delete()
        }
    }

    /** Deletes temp files of downloads that never finished (the process was killed), once too old to be in use. */
    private fun deleteAbandonedTempFiles() {
        val cutoff = System.currentTimeMillis() - ABANDONED_TEMP_FILE_AGE_MS
        artworkDir.listFiles { file -> file.name.endsWith(TMP_SUFFIX) && file.lastModified() < cutoff }
            ?.forEach { it.delete() }
    }

    /** Id → image tag of each album, artist, playlist and album-less track with an image in Room. */
    private suspend fun imageTags(serverId: String): Map<String, String> {
        val rows = albumDao.getImageTags(serverId) + artistDao.getImageTags(serverId) +
            playlistDao.getImageTags(serverId) + trackDao.getOrphanTrackImageTags(serverId)
        return rows.associate { it.id to it.imageTag }
    }

    /**
     * The [items] whose image needs downloading. An image cached without a tag is taken to be the current one: its
     * tag is recorded instead.
     */
    private fun itemsToDownload(items: List<ArtworkItem>, recheckNoArt: Boolean): List<ArtworkItem> =
        items.filter { item ->
            when (actionFor(item, recheckNoArt)) {
                CacheAction.NONE -> false
                CacheAction.ADOPT_TAG -> {
                    synchronized(lockFor(item.id)) {
                        if (actionFor(item, recheckNoArt) == CacheAction.ADOPT_TAG) writeTag(item.id, item.tag)
                    }
                    false
                }
                CacheAction.DOWNLOAD -> true
            }
        }

    /**
     * What precache has to do for [item]: nothing if its image is cached for its current tag (or there's no tag to
     * compare with), only record the tag for an image cached without one, or download the image if it's missing or
     * cached for an older tag. A no-art marker rules out the download, unless [recheckNoArt] is set or the marker
     * wasn't written for the current tag.
     */
    private fun actionFor(item: ArtworkItem, recheckNoArt: Boolean): CacheAction {
        if (cachedArtwork(item.id) != null) {
            val cachedTag = readTag(tagFile(item.id))
            if (item.tag == null || cachedTag == item.tag) return CacheAction.NONE
            if (cachedTag == null) return CacheAction.ADOPT_TAG
        }
        val noArt = noArtFile(item.id)
        val noArtIsCurrent = noArt.exists() && (item.tag == null || readTag(noArt) == item.tag)
        return if (noArtIsCurrent && !recheckNoArt) CacheAction.NONE else CacheAction.DOWNLOAD
    }

    /**
     * Downloads [items] [PARALLEL_DOWNLOADS] at a time, reporting progress as they finish. When a download gets no
     * response, the run stops if the server turns out to be unreachable; otherwise just that item failed.
     */
    private suspend fun downloadAll(
        server: ServerEntity,
        items: List<ArtworkItem>,
        recheckNoArt: Boolean,
        onProgress: (SyncProgress) -> Unit,
    ): Tally = coroutineScope {
        onProgress(SyncProgress(PROGRESS_PHASE, 0, items.size))
        val tally = Tally(items.size, onProgress)
        val next = AtomicInteger()
        repeat(minOf(PARALLEL_DOWNLOADS, items.size)) {
            launch {
                while (true) {
                    ensureActive()
                    val item = items.getOrNull(next.getAndIncrement()) ?: break
                    var result = cacheItem(server, item, recheckNoArt)
                    if (result is DownloadResult.Unreachable) {
                        ensureActive()
                        if (!isServerReachable(server.url)) {
                            markServerUnreachable()
                            throw ArtworkServerUnreachableException(result.cause)
                        }
                        Log.w(TAG, "Artwork download failed for ${item.id}", result.cause)
                        result = DownloadResult.Failed
                    }
                    tally.record(result)
                }
            }
        }
        tally
    }

    /** Brings [item]'s cached artwork up to date, under its lock: the artwork provider may be fetching it meanwhile. */
    private fun cacheItem(server: ServerEntity, item: ArtworkItem, recheckNoArt: Boolean): DownloadResult =
        synchronized(lockFor(item.id)) {
            when (actionFor(item, recheckNoArt)) {
                CacheAction.NONE -> if (cachedArtwork(item.id) != null) DownloadResult.Cached else DownloadResult.NoArt
                CacheAction.ADOPT_TAG -> {
                    writeTag(item.id, item.tag)
                    DownloadResult.Cached
                }
                CacheAction.DOWNLOAD -> downloadLocked(server.url, server.accessToken, item.id, item.tag)
            }
        }

    private fun cachedArtwork(itemId: String): File? = imageFile(itemId).takeIf { it.exists() && it.length() > 0 }

    /** On-demand download: callers waiting on the same item reuse its outcome (cached file or no-art marker). */
    private fun download(serverUrl: String, apiKey: String, itemId: String): File? =
        synchronized(lockFor(itemId)) {
            cachedArtwork(itemId)?.let { return@synchronized it }
            if (noArtFile(itemId).exists() || isServerUnreachable()) return@synchronized null
            when (downloadLocked(serverUrl, apiKey, itemId, tag = null)) {
                DownloadResult.Cached -> cachedArtwork(itemId)
                is DownloadResult.Unreachable -> {
                    markServerUnreachable()
                    null
                }
                DownloadResult.NoArt, DownloadResult.Failed -> null
            }
        }

    private fun isServerUnreachable(): Boolean =
        networkStateObserver.connectionState.value is ConnectionState.ServerUnreachable ||
            skipNetworkUntil?.let { System.nanoTime() - it < 0 } == true

    private fun markServerUnreachable() {
        skipNetworkUntil = System.nanoTime() + UNREACHABLE_BACKOFF_NANOS
    }

    /** Whether the server answers HTTP at all, which tells a download that failed apart from a lost connection. */
    private fun isServerReachable(serverUrl: String): Boolean {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL("$serverUrl/System/Info/Public").openConnection() as HttpURLConnection
            connection.connectTimeout = PROBE_TIMEOUT_MS
            connection.readTimeout = PROBE_TIMEOUT_MS
            connection.responseCode > 0
        } catch (_: IOException) {
            false
        } finally {
            connection?.disconnect()
        }
    }

    private fun lockFor(itemId: String): Any = downloadLocks[(itemId.hashCode() and Int.MAX_VALUE) % LOCK_STRIPES]

    /**
     * Downloads one item's image for [tag] (its image tag in Room, `null` if unknown). Callers hold the item's lock.
     * Only network failures are [DownloadResult.Unreachable]: the response is read in full before anything is saved.
     */
    private fun downloadLocked(serverUrl: String, apiKey: String, itemId: String, tag: String?): DownloadResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = URL(imageUrl(serverUrl, apiKey, itemId, tag)).openConnection() as HttpURLConnection
            connection.connectTimeout = TIMEOUT_MS
            connection.readTimeout = TIMEOUT_MS
            connection.instanceFollowRedirects = true

            val status = connection.responseCode
            val contentType = connection.contentType
            when {
                status == HttpURLConnection.HTTP_NOT_FOUND -> saveNoArt(itemId, tag)
                // Not an image: an HTTP error, or e.g. a Wi-Fi login page answering in the server's place.
                status != HttpURLConnection.HTTP_OK || contentType?.startsWith("image/") == false -> {
                    Log.w(TAG, "Artwork download failed for $itemId: HTTP $status, $contentType")
                    DownloadResult.Failed
                }
                else -> saveImage(itemId, tag, connection.inputStream.use { it.readBytes() })
            }
        } catch (e: IOException) {
            // Connection refused, timed out, no route, dropped mid-download: no complete response from the server.
            DownloadResult.Unreachable(e)
        } catch (_: Exception) {
            DownloadResult.Failed
        } finally {
            connection?.disconnect()
        }
    }

    /** Saves a downloaded image in place of the cached one, through a temp file so readers never see a partial one. */
    private fun saveImage(itemId: String, tag: String?, image: ByteArray): DownloadResult {
        if (image.isEmpty()) return DownloadResult.Failed
        var tmpFile: File? = null
        return try {
            val tmp = File.createTempFile(itemId, TMP_SUFFIX, artworkDir).also { tmpFile = it }
            tmp.writeBytes(image)
            if (!tmp.renameTo(imageFile(itemId))) return DownloadResult.Failed
            writeTag(itemId, tag)
            noArtFile(itemId).delete()
            DownloadResult.Cached
        } catch (e: IOException) {
            Log.w(TAG, "Could not save artwork for $itemId", e)
            DownloadResult.Failed
        } finally {
            tmpFile?.takeIf { it.exists() }?.delete()
        }
    }

    /** Remembers that the server has no image for [itemId], as checked for [tag]. A cached image is kept. */
    private fun saveNoArt(itemId: String, tag: String?): DownloadResult =
        try {
            noArtFile(itemId).writeText(tag.orEmpty())
            DownloadResult.NoArt
        } catch (e: IOException) {
            Log.w(TAG, "Could not save no-art marker for $itemId", e)
            DownloadResult.Failed
        }

    /** With the image's tag, so no cache between app and server can answer with an older image. */
    private fun imageUrl(serverUrl: String, apiKey: String, itemId: String, tag: String?): String {
        val tagParam = tag?.let { "&tag=" + URLEncoder.encode(it, "UTF-8") }.orEmpty()
        return "$serverUrl/Items/$itemId/Images/Primary?maxWidth=600&quality=90&format=Webp$tagParam&api_key=$apiKey"
    }

    /** Records the tag an item's image is for (`null`: unknown). Best effort: an untagged image is adopted later. */
    private fun writeTag(itemId: String, tag: String?) {
        val file = tagFile(itemId)
        try {
            if (tag == null) file.delete() else file.writeText(tag)
        } catch (e: IOException) {
            Log.w(TAG, "Could not record the artwork tag of $itemId", e)
            file.delete()
        }
    }

    private fun readTag(file: File): String? =
        try {
            file.readText().ifEmpty { null }
        } catch (_: IOException) {
            null
        }

    private fun imageFile(itemId: String) = File(artworkDir, "$itemId.$IMAGE_EXTENSION")

    private fun tagFile(itemId: String) = File(artworkDir, "$itemId.$IMAGE_TAG_EXTENSION")

    private fun noArtFile(itemId: String) = File(artworkDir, "$itemId.$NO_ART_EXTENSION")

    /** An item to cache artwork for, with its image tag in Room (`null` if there's none to compare with). */
    private data class ArtworkItem(val id: String, val tag: String?)

    private enum class CacheAction { NONE, ADOPT_TAG, DOWNLOAD }

    private sealed interface DownloadResult {
        /** The image is cached. */
        data object Cached : DownloadResult

        /** The server has no image. */
        data object NoArt : DownloadResult

        /** An HTTP error, a response that isn't an image, or the image couldn't be saved. */
        data object Failed : DownloadResult

        /** No complete response: the connection failed or dropped. */
        data class Unreachable(val cause: IOException) : DownloadResult
    }

    /** Counts finished downloads and reports progress in order: never backwards, and the total once all finished. */
    private class Tally(private val total: Int, private val onProgress: (SyncProgress) -> Unit) {
        private var done = 0
        private var cached = 0
        private var noArt = 0
        private var failed = 0

        @Synchronized
        fun record(result: DownloadResult) {
            when (result) {
                DownloadResult.Cached -> cached++
                DownloadResult.NoArt -> noArt++
                DownloadResult.Failed, is DownloadResult.Unreachable -> failed++
            }
            done++
            if (done % PROGRESS_STEP == 0 || done == total) onProgress(SyncProgress(PROGRESS_PHASE, done, total))
        }

        @Synchronized
        override fun toString() = "$cached cached, $noArt not found, $failed failed"
    }

    companion object {
        private const val TAG = "ArtworkPreCacher"
        private const val ARTWORK_DIR = "artwork"
        private const val IMAGE_EXTENSION = "webp"
        private const val IMAGE_TAG_EXTENSION = "tag"
        private const val NO_ART_EXTENSION = "noart"
        private const val TMP_SUFFIX = ".tmp"
        private const val TIMEOUT_MS = 10_000
        private const val PROBE_TIMEOUT_MS = 5_000
        private const val LOCK_STRIPES = 64
        private const val PARALLEL_DOWNLOADS = 4
        private const val PROGRESS_PHASE = "artwork"
        private const val PROGRESS_STEP = 50
        private val UNREACHABLE_BACKOFF_NANOS = TimeUnit.SECONDS.toNanos(30)
        private val ABANDONED_TEMP_FILE_AGE_MS = TimeUnit.HOURS.toMillis(1)
    }
}
