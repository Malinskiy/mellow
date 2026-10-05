package dev.mellow.core.database

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Versions whose migration to the next version is quick enough not to need the maintenance screen (a new table or
 * column, not a rebuild or an index over a large table). The screen still shows when any other migration also runs.
 */
val SKIP_MAINTENANCE_SCREEN_FROM: Set<Int> = emptySet()

/** Every SQLite database file starts with this header string. */
private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

/** Size of the SQLite file header. */
private const val SQLITE_HEADER_SIZE = 100

/** Where the header keeps user_version, Room's schema version: a big-endian 4-byte integer. */
private const val USER_VERSION_OFFSET = 60L

/**
 * The schema version stored in the SQLite database [file] (its user_version, where Room keeps it), or null when there
 * is no database there yet or the file isn't one. Reads only the file's header, without opening the database.
 *
 * With write-ahead logging, a version committed but not yet checkpointed into the file reads as the previous one.
 */
fun readStoredSchemaVersion(file: File): Int? {
    if (!file.isFile || file.length() < SQLITE_HEADER_SIZE) return null
    return try {
        RandomAccessFile(file, "r").use { raf ->
            val magic = ByteArray(SQLITE_MAGIC.size)
            raf.readFully(magic)
            if (!magic.contentEquals(SQLITE_MAGIC)) return null
            raf.seek(USER_VERSION_OFFSET)
            raf.readInt()
        }
    } catch (_: IOException) {
        null
    }
}

/**
 * Whether opening the database [file] will migrate it to [expectedVersion]: it exists at an older version, and not
 * every migration on the way starts at a version in [quickFrom]. A new database (no file, or version 0) is created,
 * not migrated.
 */
fun isMigrationPending(
    file: File,
    expectedVersion: Int = MellowDatabase.VERSION,
    quickFrom: Set<Int> = SKIP_MAINTENANCE_SCREEN_FROM,
): Boolean {
    val stored = readStoredSchemaVersion(file) ?: return false
    if (stored < 1 || stored >= expectedVersion) return false
    return (stored until expectedVersion).any { it !in quickFrom }
}
