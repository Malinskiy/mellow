package dev.mellow.core.update

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.security.MessageDigest

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadedUpdateStoreTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    private lateinit var directory: File
    private lateinit var preferences: UpdatePreferences
    private lateinit var store: DownloadedUpdateStore

    @Before
    fun setUp() {
        directory = File(temporaryFolder.root, "updates")
        preferences = UpdatePreferences(InMemoryPreferencesDataStore(), testConfig())
        store = DownloadedUpdateStore(preferences, { directory }, Dispatchers.IO)
    }

    private fun apkFile(tag: String, content: String = "apk for $tag"): File =
        File(directory.apply { mkdirs() }, DownloadedUpdateStore.fileNameFor(tag)).apply { writeText(content) }

    @Test
    fun `apk files are named after the tag inside the updates directory`() = runTest {
        assertEquals(File(directory, "mellow-v1.2.0.apk"), store.apkFile("v1.2.0"))
    }

    @Test
    fun `tags cannot escape the updates directory`() {
        val name = DownloadedUpdateStore.fileNameFor("../../shared_prefs/x")

        assertEquals("mellow-.._.._shared_prefs_x.apk", name)
        assertFalse(name.contains('/'))
    }

    @Test
    fun `record stores the path and the SHA-256 of the file`() = runTest {
        val file = apkFile("v1.2.0", content = "hello")

        val recorded = store.record("v1.2.0", file)

        val expectedSha = MessageDigest.getInstance("SHA-256").digest("hello".toByteArray())
            .joinToString("") { "%02x".format(it) }
        assertEquals(DownloadedApk("v1.2.0", file.absolutePath, expectedSha), recorded)
        assertEquals(recorded, preferences.downloadedApk.first())
        assertEquals(recorded, store.entry.first())
    }

    @Test
    fun `forTag returns the entry only for its tag and only while the file exists`() = runTest {
        val file = apkFile("v1.2.0")
        val recorded = store.record("v1.2.0", file)

        assertEquals(recorded, store.forTag("v1.2.0"))
        assertNull(store.forTag("v1.3.0"))

        file.delete()
        assertNull(store.forTag("v1.2.0"))
    }

    @Test
    fun `validate keeps an intact entry`() = runTest {
        val recorded = store.record("v1.2.0", apkFile("v1.2.0"))

        assertEquals(recorded, store.validate())
        assertEquals(recorded, preferences.downloadedApk.first())
    }

    @Test
    fun `validate clears the entry when the file is missing`() = runTest {
        val file = apkFile("v1.2.0")
        store.record("v1.2.0", file)
        val unrelated = File(directory, "keep-me").apply { writeText("x") }
        file.delete()

        assertNull(store.validate())
        assertNull(preferences.downloadedApk.first())
        assertTrue(unrelated.exists())
    }

    @Test
    fun `validate clears only the entry when the file no longer matches its hash`() = runTest {
        val file = apkFile("v1.2.0")
        store.record("v1.2.0", file)
        file.writeText("replaced")

        assertNull(store.validate())
        assertNull(preferences.downloadedApk.first())
        assertTrue(file.exists())
    }

    @Test
    fun `validate without an entry does nothing`() = runTest {
        assertNull(store.validate())
    }

    @Test
    fun `purgeExcept deletes other files and forgets an entry for another tag`() = runTest {
        val old = apkFile("v1.1.0")
        store.record("v1.1.0", old)
        val partial = File(directory, "download-123.part").apply { writeText("partial") }
        val current = apkFile("v1.2.0")

        store.purgeExcept("v1.2.0")

        assertFalse(old.exists())
        assertFalse(partial.exists())
        assertTrue(current.exists())
        assertNull(preferences.downloadedApk.first())
    }

    @Test
    fun `purgeExcept keeps the entry for the same tag`() = runTest {
        val recorded = store.record("v1.2.0", apkFile("v1.2.0"))

        store.purgeExcept("v1.2.0")

        assertEquals(recorded, preferences.downloadedApk.first())
        assertTrue(File(recorded.path).exists())
    }

    @Test
    fun `purgeExcept copes with a missing directory`() = runTest {
        store.purgeExcept("v1.2.0")

        assertFalse(directory.exists())
    }

    @Test
    fun `deleteAll forgets the entry and removes the directory`() = runTest {
        store.record("v1.2.0", apkFile("v1.2.0"))

        store.deleteAll()

        assertNull(preferences.downloadedApk.first())
        assertFalse(directory.exists())
    }
}
