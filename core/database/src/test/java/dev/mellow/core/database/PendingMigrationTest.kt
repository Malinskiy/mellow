package dev.mellow.core.database

import android.database.sqlite.SQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.ByteBuffer

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PendingMigrationTest {

    @get:Rule
    val folder = TemporaryFolder()

    private val expected = MellowDatabase.VERSION

    @Test
    fun `no database file means a fresh install, no migration`() {
        val file = File(folder.root, "mellow.db")

        assertNull(readStoredSchemaVersion(file))
        assertFalse(isMigrationPending(file, expected, emptySet()))
    }

    @Test
    fun `a database at the expected version needs no migration`() {
        val file = headerFile(userVersion = expected)

        assertEquals(expected, readStoredSchemaVersion(file))
        assertFalse(isMigrationPending(file, expected, emptySet()))
    }

    @Test
    fun `a database one version behind needs a migration`() {
        val file = headerFile(userVersion = expected - 1)

        assertTrue(isMigrationPending(file, expected, emptySet()))
    }

    @Test
    fun `a newer database or version 0 is not migrated`() {
        assertFalse(isMigrationPending(headerFile(userVersion = expected + 1), expected, emptySet()))
        assertFalse(isMigrationPending(headerFile(userVersion = 0), expected, emptySet()))
    }

    @Test
    fun `a file too short to be a database is not migrated, and doesn't throw`() {
        val file = folder.newFile("tiny.db").apply { writeBytes(ByteArray(40) { 7 }) }

        assertNull(readStoredSchemaVersion(file))
        assertFalse(isMigrationPending(file, expected, emptySet()))
    }

    @Test
    fun `a file that isn't a SQLite database is not migrated`() {
        val garbage = ByteArray(4096) { (it * 31).toByte() }
        ByteBuffer.wrap(garbage).putInt(60, expected - 1)
        val file = folder.newFile("garbage.db").apply { writeBytes(garbage) }

        assertNull(readStoredSchemaVersion(file))
        assertFalse(isMigrationPending(file, expected, emptySet()))
    }

    @Test
    fun `a quick migration skips the screen`() {
        val file = headerFile(userVersion = expected - 1)

        assertFalse(isMigrationPending(file, expected, quickFrom = setOf(expected - 1)))
    }

    @Test
    fun `the screen shows when any migration on the way isn't quick`() {
        val file = headerFile(userVersion = expected - 2)

        assertTrue(isMigrationPending(file, expected, quickFrom = setOf(expected - 2)))
        assertFalse(isMigrationPending(file, expected, quickFrom = setOf(expected - 2, expected - 1)))
    }

    @Test
    fun `any future version bump shows the screen`() {
        // As if a later release bumped the schema: the stored version is now one behind the app's.
        val stored = 20
        val file = headerFile(userVersion = stored)

        assertTrue(isMigrationPending(file, expectedVersion = stored + 1, quickFrom = emptySet()))
        assertFalse(isMigrationPending(file, expectedVersion = stored, quickFrom = emptySet()))
    }

    @Test
    fun `reads the version SQLite stored in a real database`() {
        val file = File(folder.root, "real.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL("CREATE TABLE tracks (id TEXT PRIMARY KEY)")
            db.execSQL("PRAGMA user_version = 12")
        }

        assertEquals(12, readStoredSchemaVersion(file))
        assertTrue(isMigrationPending(file, expectedVersion = 13, quickFrom = emptySet()))
        assertFalse(isMigrationPending(file, expectedVersion = 12, quickFrom = emptySet()))
    }

    @Test
    fun `the app's own defaults`() {
        assertTrue(SKIP_MAINTENANCE_SCREEN_FROM.isEmpty())
        assertTrue(isMigrationPending(headerFile(userVersion = MellowDatabase.VERSION - 1)))
        assertFalse(isMigrationPending(headerFile(userVersion = MellowDatabase.VERSION)))
    }

    /** A file with a valid 100-byte SQLite header whose user_version is [userVersion], and one empty page after it. */
    private fun headerFile(userVersion: Int): File {
        val header = ByteBuffer.allocate(4096)
        header.put("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
        header.putShort(16, 4096.toShort())
        header.putInt(60, userVersion)
        return File.createTempFile("header", ".db", folder.root).apply { writeBytes(header.array()) }
    }
}
