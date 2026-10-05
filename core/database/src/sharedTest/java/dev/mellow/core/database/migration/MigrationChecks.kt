package dev.mellow.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

/**
 * The migrations, on databases [helper] creates from the exported schemas. Run by MigrationTest under Robolectric and
 * by MigrationDeviceTest on a device's own SQLite.
 */
class MigrationChecks(private val helper: MigrationTestHelper) {

    fun migration11To12KeepsTheLibraryAndAddsTheSyncPassTable() {
        helper.createDatabase(DB_NAME, 11).use { db ->
            db.execSQL(
                "INSERT INTO albums (id, serverId, name, sortName, artistId, artistName, year, trackCount, genres, " +
                    "imageTag, isFavorite, resolvedArtistId, dateAdded, lastSynced) " +
                    "VALUES ('a1', 's1', 'Album', 'Album', 'ar1', 'Artist', 2001, 1, '', NULL, 1, 'ar1', 5, 6)",
            )
            db.execSQL(
                "INSERT INTO album_artists (albumId, artistId, artistName, displayOrder) " +
                    "VALUES ('a1', 'ar1', 'Artist', 0)",
            )
        }

        helper.runMigrationsAndValidate(DB_NAME, 12, true, Migrations.MIGRATION_11_12).use { db ->
            db.query("SELECT name, isFavorite FROM albums WHERE id = 'a1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals("Album", cursor.getString(0))
                assertEquals(1, cursor.getInt(1))
            }
            db.query("SELECT COUNT(*) FROM album_artists WHERE albumId = 'a1'").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
            db.execSQL("INSERT INTO sync_pass_items (kind, itemId) VALUES ('track', 't1')")
            db.execSQL("INSERT OR IGNORE INTO sync_pass_items (kind, itemId) VALUES ('track', 't1')")
            db.query("SELECT COUNT(*) FROM sync_pass_items").use { cursor ->
                cursor.moveToFirst()
                assertEquals(1, cursor.getInt(0))
            }
        }
    }

    fun migration12To13KeepsRebuiltTablesAndTheirChildrenAndSortsNamesIgnoringCase() {
        helper.createDatabase(DB_NAME, 12).use { db -> addTracksAndPlaylist(db) }

        helper.runMigrationsAndValidate(DB_NAME, 13, true, Migrations.MIGRATION_12_13).use { db ->
            assertEquals(listOf("t2", "t1", "t3"), db.strings("SELECT id FROM tracks ORDER BY name"))
            assertEquals(listOf("a2", "a1"), db.strings("SELECT id FROM albums ORDER BY name"))
            assertEquals(listOf("ar2", "ar1"), db.strings("SELECT id FROM artists ORDER BY name"))
            assertEquals(listOf("t1", "t3"), db.playlist())
            assertEquals(listOf("ar1", "ar2"), db.strings("SELECT artistId FROM track_artists ORDER BY artistId"))
            assertEquals(listOf("ar1", "ar2"), db.strings("SELECT artistId FROM album_artists ORDER BY artistId"))
            db.query("SELECT playCount, lastPlayedAt, normalizationGain, resolvedArtistId FROM tracks WHERE id = 't1'")
                .use { cursor ->
                    cursor.moveToFirst()
                    assertEquals(7, cursor.getInt(0))
                    assertEquals(8L, cursor.getLong(1))
                    assertEquals(0.5f, cursor.getFloat(2))
                    assertEquals("ar1", cursor.getString(3))
                }
            val createSql = db.strings("SELECT sql FROM sqlite_master WHERE name = 'tracks'").single()
            assertTrue(createSql, createSql.contains("`name` TEXT NOT NULL COLLATE NOCASE"))
            for (table in listOf("albums", "artists")) {
                val sql = db.strings("SELECT sql FROM sqlite_master WHERE name = '$table'").single()
                assertTrue(sql, sql.contains("`name` TEXT NOT NULL COLLATE NOCASE"))
            }
            assertEquals(emptyList<String>(), db.strings("SELECT name FROM sqlite_master WHERE name LIKE '%_kept'"))
        }
    }

    fun migration12To13KeepsEveryCascadingChildEvenWithForeignKeysOn() {
        helper.createDatabase(DB_NAME, 12).use { db ->
            addTracksAndPlaylist(db)
            // Dropping the old tracks table cascades to playlist_tracks while foreign keys are on.
            db.execSQL("PRAGMA foreign_keys = ON")

            Migrations.MIGRATION_12_13.migrate(db)

            assertEquals(listOf("t1", "t3"), db.playlist())
            assertEquals(listOf("ar1", "ar2"), db.strings("SELECT artistId FROM track_artists ORDER BY artistId"))
            assertEquals(listOf("ar1", "ar2"), db.strings("SELECT artistId FROM album_artists ORDER BY artistId"))
            assertEquals(3, db.strings("SELECT id FROM tracks").size)
            assertEquals(2, db.strings("SELECT id FROM albums").size)
            assertEquals(2, db.strings("SELECT id FROM artists").size)
        }
    }

    private fun addTracksAndPlaylist(db: SupportSQLiteDatabase) {
        for ((id, name, artistId) in listOf(Triple("a1", "beta", "ar1"), Triple("a2", "Alpha", "ar2"))) {
            db.execSQL(
                "INSERT INTO albums (id, serverId, name, sortName, artistId, artistName, year, trackCount, genres, " +
                    "imageTag, isFavorite, resolvedArtistId, dateAdded, lastSynced) VALUES ('$id', 's1', '$name', " +
                    "'$name', '$artistId', 'Artist', 2001, 1, '', NULL, 0, '$artistId', 0, 0)",
            )
        }
        for ((id, name) in listOf("ar1" to "beta", "ar2" to "Alpha")) {
            db.execSQL(
                "INSERT INTO artists (id, serverId, name, sortName, albumCount, imageTag, isFavorite, overview, " +
                    "genres, cleanName, musicBrainzId, lastSynced) VALUES ('$id', 's1', '$name', '$name', 1, " +
                    "NULL, 0, NULL, '', '$name', NULL, 0)",
            )
        }
        db.execSQL(
            "INSERT INTO playlists (id, serverId, name, sortName, trackCount, durationMs, imageTag, isFavorite, " +
                "isLocal, lastSynced) VALUES ('p1', 's1', 'Mix', 'mix', 2, 0, NULL, 0, 0, 0)",
        )
        for ((id, name) in listOf("t1" to "beta", "t2" to "Alpha", "t3" to "gamma")) {
            db.execSQL(
                "INSERT INTO tracks (id, serverId, name, sortName, albumId, albumName, artistId, artistName, " +
                    "trackNumber, discNumber, durationMs, genres, imageTag, isFavorite, playCount, lastPlayedAt, " +
                    "normalizationGain, container, codec, bitrate, sampleRate, channels, resolvedArtistId, " +
                    "dateAdded, lastSynced) VALUES ('$id', 's1', '$name', '$name', 'a1', 'Album', 'ar1', " +
                    "'Artist', 1, 1, 1000, '', 'tag', 1, 7, 8, 0.5, 'flac', 'flac', 900, 44100, 2, 'ar1', 9, 10)",
            )
        }
        db.execSQL("INSERT INTO playlist_tracks (playlistId, trackId, position, addedAt) VALUES ('p1', 't1', 0, 0)")
        db.execSQL("INSERT INTO playlist_tracks (playlistId, trackId, position, addedAt) VALUES ('p1', 't3', 1, 0)")
        db.execSQL(
            "INSERT INTO track_artists (trackId, artistId, artistName, displayOrder) VALUES " +
                "('t1', 'ar1', 'Artist 1', 0), ('t3', 'ar2', 'Artist 2', 0)",
        )
        db.execSQL(
            "INSERT INTO album_artists (albumId, artistId, artistName, displayOrder) VALUES " +
                "('a1', 'ar1', 'Artist 1', 0), ('a2', 'ar2', 'Artist 2', 0)",
        )
    }

    private fun SupportSQLiteDatabase.playlist(): List<String> =
        strings("SELECT trackId FROM playlist_tracks WHERE playlistId = 'p1' ORDER BY position")

    private fun SupportSQLiteDatabase.strings(sql: String): List<String> = query(sql).use {
        buildList { while (it.moveToNext()) add(it.getString(0)) }
    }

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
