package dev.mellow.core.data

import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.nio.file.Files
import java.util.Collections
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.CountDownLatch
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import android.content.Context
import android.util.Log
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkStatic
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.ImageTagRow
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.database.entity.TrackEntity
import dev.mellow.core.network.ConnectionState
import dev.mellow.core.network.NetworkStateObserver

class ArtworkPreCacherTest {

    private lateinit var root: File
    private lateinit var legacyDir: File
    private lateinit var artworkDir: File
    private lateinit var context: Context
    private lateinit var networkStateObserver: NetworkStateObserver
    private val serverDao = mockk<ServerDao>()
    private val albumDao = mockk<AlbumDao>()
    private val artistDao = mockk<ArtistDao>()
    private val playlistDao = mockk<PlaylistDao>()
    private val trackDao = mockk<TrackDao>()
    private val connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Connected)
    private lateinit var preCacher: ArtworkPreCacher
    private val servers = mutableListOf<Pair<ServerSocket, ExecutorService>>()
    private val executor = Executors.newCachedThreadPool()

    @Before
    fun setUp() {
        mockkStatic(Log::class)
        every { Log.d(any(), any()) } returns 0
        every { Log.i(any(), any()) } returns 0
        every { Log.w(any(), any<String>()) } returns 0
        every { Log.w(any(), any<String>(), any()) } returns 0
        root = Files.createTempDirectory("artwork-test").toFile()
        val cacheRoot = File(root, "cache")
        val noBackupRoot = File(root, "no_backup")
        legacyDir = File(cacheRoot, "artwork")
        artworkDir = File(noBackupRoot, "artwork").apply { mkdirs() }
        context = mockk {
            every { cacheDir } returns cacheRoot
            every { noBackupFilesDir } returns noBackupRoot
        }
        networkStateObserver = mockk { every { connectionState } returns this@ArtworkPreCacherTest.connectionState }
        coEvery { trackDao.getTrackById(any()) } returns null
        coEvery { trackDao.getTrackById(TRACK_ID) } returns track(TRACK_ID, albumId = ALBUM_ID)
        preCacher = newPreCacher()
    }

    @After
    fun tearDown() {
        servers.forEach { (socket, pool) ->
            socket.close()
            pool.shutdownNow()
        }
        executor.shutdownNow()
        root.deleteRecursively()
        unmockkStatic(Log::class)
    }

    @Test
    fun `cached track art is served`() {
        val trackArt = cache(TRACK_ID, TRACK_BYTES)
        cache(ALBUM_ID, ALBUM_BYTES)

        assertEquals(trackArt, preCacher.resolveArtwork(unreachableUrl(), API_KEY, TRACK_ID))
    }

    @Test
    fun `uncached track falls back to cached album art when the server is unreachable`() {
        val albumArt = cache(ALBUM_ID, ALBUM_BYTES)

        assertEquals(albumArt, preCacher.resolveArtwork(unreachableUrl(), API_KEY, TRACK_ID))
    }

    @Test
    fun `track's own art is preferred over cached album art when the server is reachable`() {
        cache(ALBUM_ID, ALBUM_BYTES)
        val url = serve(mapOf(TRACK_ID to TRACK_BYTES, ALBUM_ID to ALBUM_BYTES))

        val resolved = preCacher.resolveArtwork(url, API_KEY, TRACK_ID)

        assertArrayEquals(TRACK_BYTES, resolved!!.readBytes())
    }

    @Test
    fun `track without its own image falls back to album art downloaded from the server`() {
        val url = serve(mapOf(ALBUM_ID to ALBUM_BYTES))

        val resolved = preCacher.resolveArtwork(url, API_KEY, TRACK_ID)

        assertArrayEquals(ALBUM_BYTES, resolved!!.readBytes())
        assertTrue(File(artworkDir, "$TRACK_ID.noart").exists())
    }

    @Test
    fun `item that is not a track and has no art resolves to nothing`() {
        assertNull(preCacher.resolveArtwork(unreachableUrl(), API_KEY, ALBUM_ID))
    }

    @Test
    fun `server reported unreachable skips the network and serves cached album art`() {
        connectionState.value = ConnectionState.ServerUnreachable
        val albumArt = cache(ALBUM_ID, ALBUM_BYTES)
        val requests = AtomicInteger()
        val url = serve(mapOf(TRACK_ID to TRACK_BYTES), requests)

        assertEquals(albumArt, preCacher.resolveArtwork(url, API_KEY, TRACK_ID))
        assertEquals(0, requests.get())
    }

    @Test
    fun `after a failed connection other items skip the network for a while`() {
        preCacher.resolveArtwork(unreachableUrl(), API_KEY, "other-item")
        val requests = AtomicInteger()
        val url = serve(mapOf(ALBUM_ID to ALBUM_BYTES), requests)

        assertNull(preCacher.resolveArtwork(url, API_KEY, ALBUM_ID))
        assertEquals(0, requests.get())
    }

    @Test
    fun `concurrent requests for the same item download it once and cache intact bytes`() {
        val requests = AtomicInteger()
        val url = serve(mapOf(ALBUM_ID to LARGE_BYTES), requests, delayMs = 200)

        val files = resolveConcurrently(url, ALBUM_ID)

        assertEquals(1, requests.get())
        files.forEach { assertArrayEquals(LARGE_BYTES, it!!.readBytes()) }
        assertEquals(listOf("$ALBUM_ID.webp"), artworkDir.list()!!.toList())
    }

    @Test
    fun `concurrent requests for an item without art ask the server once`() {
        val requests = AtomicInteger()
        val url = serve(emptyMap(), requests, delayMs = 200)

        val files = resolveConcurrently(url, ALBUM_ID)

        assertEquals(1, requests.get())
        files.forEach { assertNull(it) }
        assertEquals(listOf("$ALBUM_ID.noart"), artworkDir.list()!!.toList())
    }

    @Test
    fun `artwork cached by an earlier version is moved to durable storage instead of downloaded again`() {
        legacyDir.mkdirs()
        File(legacyDir, "$ALBUM_ID.webp").writeBytes(ALBUM_BYTES)
        File(legacyDir, "$TRACK_ID.noart").createNewFile()
        File(legacyDir, "legacy.jpg").writeBytes(TRACK_BYTES)
        File(legacyDir, "${ALBUM_ID}123.tmp").writeBytes(TRACK_BYTES)
        val server = FakeServer(mapOf(ALBUM_ID to LARGE_BYTES, TRACK_ID to LARGE_BYTES))

        // The moved marker says the track has no art of its own, so it gets the moved album art.
        val resolved = preCacher.resolveArtwork(server.url, API_KEY, TRACK_ID)

        assertEquals(File(artworkDir, "$ALBUM_ID.webp"), resolved)
        assertArrayEquals(ALBUM_BYTES, resolved!!.readBytes())
        assertEquals(0, server.requests.get())
        assertEquals(setOf("$ALBUM_ID.webp", "$TRACK_ID.noart"), artworkDir.list()!!.toSet())
        assertFalse(legacyDir.exists())
    }

    @Test
    fun `moving the legacy cache again keeps the copies already in durable storage`() {
        // A move cut short (the app was killed) left files behind, one of them already moved before.
        cache(ALBUM_ID, ALBUM_BYTES)
        legacyDir.mkdirs()
        File(legacyDir, "$ALBUM_ID.webp").writeBytes(TRACK_BYTES)
        File(legacyDir, "album-2.webp").writeBytes(LARGE_BYTES)

        newPreCacher().resolveArtwork(unreachableUrl(), API_KEY, ALBUM_ID)
        newPreCacher().resolveArtwork(unreachableUrl(), API_KEY, ALBUM_ID)

        assertArrayEquals(ALBUM_BYTES, File(artworkDir, "$ALBUM_ID.webp").readBytes())
        assertArrayEquals(LARGE_BYTES, File(artworkDir, "album-2.webp").readBytes())
        assertEquals(setOf("$ALBUM_ID.webp", "album-2.webp"), artworkDir.list()!!.toSet())
        assertFalse(legacyDir.exists())
    }

    @Test
    fun `precache caches every item with an image, in parallel, each once, with progress up to the total`() {
        val ids = (1..120).map { "item-$it" }
        val server = FakeServer(ids.associateWith { it.toByteArray() }, delayMs = 20)
        val tagged = ids.chunked(30).map { group -> group.associateWith { "tag-$it" } }
        library(server.url, albums = tagged[0], artists = tagged[1], playlists = tagged[2], orphanTracks = tagged[3])
        val progress = Collections.synchronizedList(mutableListOf<SyncProgress>())

        preCache { progress += it }

        assertEquals(ids.sorted(), server.requestedIds().sorted())
        assertTrue("downloads ran in parallel, at most 4 at a time", server.maxConcurrent.get() in 2..4)
        ids.forEach { id ->
            assertArrayEquals(id.toByteArray(), File(artworkDir, "$id.webp").readBytes())
            assertEquals("tag-$id", File(artworkDir, "$id.tag").readText())
        }
        assertEquals(listOf(0, 50, 100, 120), progress.map { it.current })
        assertTrue(progress.all { it.phase == "artwork" && it.total == ids.size })
    }

    @Test
    fun `precache and the artwork provider fetch an image once between them`() {
        val ids = (1..20).map { "album-$it" }
        val server = FakeServer(ids.associateWith { ALBUM_BYTES }, delayMs = 20)
        library(server.url, albums = ids.associateWith { "t1" })

        val run = background { preCache() }
        for (id in ids.reversed()) {
            assertArrayEquals(ALBUM_BYTES, preCacher.resolveArtwork(server.url, API_KEY, id)!!.readBytes())
        }
        run.get(10, TimeUnit.SECONDS)

        assertEquals(ids.sorted(), server.requestedIds().sorted())
        ids.forEach { assertEquals("t1", File(artworkDir, "$it.tag").readText()) }
    }

    @Test
    fun `changed image is downloaded again, and the old one is served until the new one is in place`() {
        cache(ALBUM_ID, ALBUM_BYTES, tag = "old")
        val gate = CountDownLatch(1)
        val server = FakeServer(mapOf(ALBUM_ID to NEW_BYTES), gate = gate)
        library(server.url, albums = mapOf(ALBUM_ID to "new"))

        val run = background { preCache() }
        assertTrue(server.received.await(5, TimeUnit.SECONDS))
        assertArrayEquals(ALBUM_BYTES, preCacher.resolveArtwork(unreachableUrl(), API_KEY, ALBUM_ID)!!.readBytes())
        gate.countDown()
        run.get(10, TimeUnit.SECONDS)

        assertArrayEquals(NEW_BYTES, preCacher.resolveArtwork(unreachableUrl(), API_KEY, ALBUM_ID)!!.readBytes())
        assertEquals("new", File(artworkDir, "$ALBUM_ID.tag").readText())
        assertTrue(server.requestedPaths.single().contains("tag=new"))
    }

    @Test
    fun `image cached for an older tag is still served while the server is unreachable`() {
        cache(ALBUM_ID, ALBUM_BYTES, tag = "old")
        library(unreachableUrl(), albums = mapOf(ALBUM_ID to "new"))

        assertThrows(ArtworkServerUnreachableException::class.java) { preCache() }

        assertArrayEquals(ALBUM_BYTES, preCacher.resolveArtwork(unreachableUrl(), API_KEY, ALBUM_ID)!!.readBytes())
        assertEquals("old", File(artworkDir, "$ALBUM_ID.tag").readText())
    }

    @Test
    fun `image cached without a tag is kept for the current tag instead of downloaded again`() {
        cache(ALBUM_ID, ALBUM_BYTES)
        val server = FakeServer(mapOf(ALBUM_ID to NEW_BYTES))
        library(server.url, albums = mapOf(ALBUM_ID to "t1"))

        preCache()

        assertEquals(0, server.requests.get())
        assertArrayEquals(ALBUM_BYTES, File(artworkDir, "$ALBUM_ID.webp").readBytes())
        assertEquals("t1", File(artworkDir, "$ALBUM_ID.tag").readText())
    }

    @Test
    fun `precache stops and throws when the server goes away instead of failing through the list`() {
        val ids = (1..40).map { "album-$it" }
        val server = FakeServer(ids.associateWith { ALBUM_BYTES }, closeAfter = 5)
        library(server.url, albums = ids.associateWith { "t1" })
        val progress = Collections.synchronizedList(mutableListOf<SyncProgress>())

        assertThrows(ArtworkServerUnreachableException::class.java) { preCache { progress += it } }

        assertTrue(server.requests.get() < ids.size)
        assertEquals(5, artworkDir.list { _, name -> name.endsWith(".webp") }!!.size)
        assertEquals(5, artworkDir.list { _, name -> name.endsWith(".tag") }!!.size)
        assertTrue(artworkDir.list { _, name -> name.endsWith(".noart") }!!.isEmpty())
        assertTrue(progress.none { it.current == it.total })
    }

    @Test
    fun `priority precache throws when the server can't be reached`() {
        library(unreachableUrl(), albums = mapOf(ALBUM_ID to "t1"))

        assertThrows(ArtworkServerUnreachableException::class.java) {
            runBlocking { preCacher.preCacheIds(setOf(ALBUM_ID)) }
        }
        assertTrue(artworkDir.list()!!.isEmpty())
    }

    @Test
    fun `item the server has no image for gets a no-art marker for its tag, and the rest are cached`() {
        val server = FakeServer(mapOf("album-2" to ALBUM_BYTES))
        library(server.url, albums = mapOf("album-1" to "t1", "album-2" to "t2"))

        preCache()

        assertEquals(setOf("album-1.noart", "album-2.webp", "album-2.tag"), artworkDir.list()!!.toSet())
        assertEquals("t1", File(artworkDir, "album-1.noart").readText())
        assertEquals("t2", File(artworkDir, "album-2.tag").readText())
    }

    @Test
    fun `server errors and dropped connections only fail their own item while the server is reachable`() {
        val images = (1..3).associate { "album-$it" to ALBUM_BYTES }
        val server = FakeServer(images, statuses = mapOf("album-1" to 500), dropped = setOf("album-2"))
        library(server.url, albums = images.mapValues { "t1" })
        val progress = mutableListOf<SyncProgress>()

        preCache { progress += it }

        assertEquals(setOf("album-3.webp", "album-3.tag"), artworkDir.list()!!.toSet())
        assertEquals(SyncProgress("artwork", 3, 3), progress.last())
    }

    @Test
    fun `a response that isn't an image doesn't replace the cached one`() {
        cache("album-1", ALBUM_BYTES, tag = "old")
        cache("album-2", ALBUM_BYTES, tag = "old")
        val server = FakeServer(
            images = mapOf("album-1" to "<html>Wi-Fi login</html>".toByteArray(), "album-2" to ByteArray(0)),
            contentTypes = mapOf("album-1" to "text/html"),
        )
        library(server.url, albums = mapOf("album-1" to "new", "album-2" to "new"))

        preCache()

        assertEquals(listOf("album-1", "album-2"), server.requestedIds().sorted())
        for (id in listOf("album-1", "album-2")) {
            assertArrayEquals(ALBUM_BYTES, File(artworkDir, "$id.webp").readBytes())
            assertEquals("old", File(artworkDir, "$id.tag").readText())
        }
    }

    @Test
    fun `full precache checks items without art again`() {
        File(artworkDir, "$ALBUM_ID.noart").writeText("t1")
        val server = FakeServer(mapOf(ALBUM_ID to ALBUM_BYTES))
        library(server.url, albums = mapOf(ALBUM_ID to "t1"))

        preCache()

        assertEquals(setOf("$ALBUM_ID.webp", "$ALBUM_ID.tag"), artworkDir.list()!!.toSet())
    }

    @Test
    fun `priority precache skips items without art unless Room has a tag they weren't checked for`() {
        File(artworkDir, "album-1.noart").writeText("t1") // checked for the current image
        File(artworkDir, "album-2.noart").createNewFile() // checked before tags were recorded
        File(artworkDir, "album-3.noart").writeText("old") // checked for an older image
        File(artworkDir, "album-4.noart").createNewFile() // no image in Room either
        val ids = (1..4).map { "album-$it" }
        val server = FakeServer(ids.associateWith { ALBUM_BYTES })
        library(server.url, albums = mapOf("album-1" to "t1", "album-2" to "t2", "album-3" to "t3"))

        runBlocking { preCacher.preCacheIds(ids.toSet()) }

        assertEquals(listOf("album-2", "album-3"), server.requestedIds().sorted())
        assertEquals(
            setOf("album-1.noart", "album-2.webp", "album-2.tag", "album-3.webp", "album-3.tag", "album-4.noart"),
            artworkDir.list()!!.toSet(),
        )
    }

    private fun resolveConcurrently(url: String, itemId: String, threads: Int = 8): List<File?> {
        val pool = Executors.newFixedThreadPool(threads)
        val ready = CountDownLatch(threads)
        val start = CountDownLatch(1)
        try {
            val results = (1..threads).map {
                pool.submit<File?> {
                    ready.countDown()
                    start.await()
                    preCacher.resolveArtwork(url, API_KEY, itemId)
                }
            }
            ready.await(5, TimeUnit.SECONDS)
            start.countDown()
            return results.map { it.get(10, TimeUnit.SECONDS) }
        } finally {
            pool.shutdownNow()
        }
    }

    /** A new instance over the same storage, as after an app restart. */
    private fun newPreCacher() =
        ArtworkPreCacher(context, serverDao, albumDao, artistDao, playlistDao, trackDao, networkStateObserver)

    /** What Room knows: the active server at [url] and the items (id → image tag) that have an image. */
    private fun library(
        url: String,
        albums: Map<String, String> = emptyMap(),
        artists: Map<String, String> = emptyMap(),
        playlists: Map<String, String> = emptyMap(),
        orphanTracks: Map<String, String> = emptyMap(),
    ) {
        coEvery { serverDao.getActiveServer() } returns ServerEntity(
            id = SERVER_ID,
            name = "Jellyfin",
            url = url,
            userId = "user",
            accessToken = API_KEY,
            isActive = true,
            lastConnected = 0,
        )
        coEvery { albumDao.getImageTags(SERVER_ID) } returns albums.toRows()
        coEvery { artistDao.getImageTags(SERVER_ID) } returns artists.toRows()
        coEvery { playlistDao.getImageTags(SERVER_ID) } returns playlists.toRows()
        coEvery { trackDao.getOrphanTrackImageTags(SERVER_ID) } returns orphanTracks.toRows()
    }

    private fun Map<String, String>.toRows() = map { (id, tag) -> ImageTagRow(id, tag) }

    private fun preCache(onProgress: (SyncProgress) -> Unit = {}) =
        runBlocking { preCacher.preCacheArtwork(SERVER_ID, onProgress) }

    private fun <T> background(block: () -> T): Future<T> = executor.submit<T> { block() }

    private fun cache(itemId: String, bytes: ByteArray, tag: String? = null): File {
        tag?.let { File(artworkDir, "$itemId.tag").writeText(it) }
        return File(artworkDir, "$itemId.webp").apply { writeBytes(bytes) }
    }

    /** A port nothing listens on: connections are refused immediately, like an unreachable server. */
    private fun unreachableUrl(): String {
        val port = ServerSocket(0).use { it.localPort }
        return "http://127.0.0.1:$port"
    }

    private fun serve(images: Map<String, ByteArray>, requests: AtomicInteger = AtomicInteger(), delayMs: Long = 0) =
        FakeServer(images, requests, delayMs).url

    /**
     * Minimal HTTP server for `/Items/{id}/Images/...`: 200 with the image bytes, or 404 if [images] has none. Other
     * paths, like the reachability check, get an empty 200.
     */
    private inner class FakeServer(
        private val images: Map<String, ByteArray>,
        val requests: AtomicInteger = AtomicInteger(),
        private val delayMs: Long = 0,
        /** Answered with this HTTP status instead. */
        private val statuses: Map<String, Int> = emptyMap(),
        /** Answered with this content type instead of `image/webp`. */
        private val contentTypes: Map<String, String> = emptyMap(),
        /** Get their connection closed without a response. */
        private val dropped: Set<String> = emptySet(),
        /** Image requests answered before the server goes away (stops listening): later ones get no answer. */
        private val closeAfter: Int = Int.MAX_VALUE,
        /** Image requests wait for it before being answered. */
        private val gate: CountDownLatch? = null,
    ) {
        private val socket = ServerSocket(0)
        private val pool = Executors.newCachedThreadPool()
        private val concurrent = AtomicInteger()
        val url = "http://127.0.0.1:${socket.localPort}"
        val requestedPaths = ConcurrentLinkedQueue<String>()
        val maxConcurrent = AtomicInteger()
        val received = CountDownLatch(1)

        init {
            servers += socket to pool
            pool.execute {
                while (!socket.isClosed) {
                    val client = runCatching { socket.accept() }.getOrNull() ?: break
                    pool.execute { respond(client) }
                }
            }
        }

        fun requestedIds(): List<String> = requestedPaths.map { it.removePrefix("/Items/").substringBefore('/') }

        private fun respond(client: Socket) {
            client.use {
                val reader = it.getInputStream().bufferedReader()
                val path = reader.readLine()?.split(' ')?.get(1) ?: return
                while (!reader.readLine().isNullOrEmpty()) Unit
                val out = it.getOutputStream()
                if (!path.startsWith("/Items/")) {
                    out.write("HTTP/1.1 200 OK\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                    return
                }
                // Stop listening as the last request to answer comes in, so whatever comes after can't get through.
                val request = synchronized(this) {
                    requests.incrementAndGet().also { if (it == closeAfter) socket.close() }
                }
                requestedPaths += path
                received.countDown()
                val id = path.removePrefix("/Items/").substringBefore('/')
                if (request > closeAfter || id in dropped) return
                maxConcurrent.accumulateAndGet(concurrent.incrementAndGet(), ::maxOf)
                try {
                    gate?.await(10, TimeUnit.SECONDS)
                    Thread.sleep(delayMs)
                } finally {
                    concurrent.decrementAndGet()
                }
                val image = images[id]
                val status = statuses[id] ?: if (image == null) 404 else 200
                val body = image?.takeIf { status == 200 } ?: ByteArray(0)
                val head = "HTTP/1.1 $status -\r\nContent-Type: ${contentTypes[id] ?: "image/webp"}\r\n" +
                    "Content-Length: ${body.size}\r\nConnection: close\r\n\r\n"
                out.write(head.toByteArray() + body)
                out.flush()
            }
        }
    }

    private fun track(id: String, albumId: String?) = mockk<TrackEntity> {
        every { this@mockk.id } returns id
        every { this@mockk.albumId } returns albumId
    }

    private companion object {
        const val SERVER_ID = "server-1"
        const val TRACK_ID = "track-1"
        const val ALBUM_ID = "album-1"
        const val API_KEY = "key"
        val TRACK_BYTES = byteArrayOf(1, 2, 3)
        val ALBUM_BYTES = byteArrayOf(4, 5, 6)
        val NEW_BYTES = byteArrayOf(7, 8, 9)
        val LARGE_BYTES = ByteArray(512 * 1024) { (it % 251).toByte() }
    }
}
