package dev.mellow.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import dev.mellow.core.database.MellowDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        MellowDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun `11 to 12 keeps the library and adds the sync pass table`() {
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

    @Test
    fun `12 to 13 keeps tracks and playlists, and sorts names ignoring case`() {
        helper.createDatabase(DB_NAME, 12).use { db -> addTracksAndPlaylist(db) }

        helper.runMigrationsAndValidate(DB_NAME, 13, true, Migrations.MIGRATION_12_13).use { db ->
            assertEquals(listOf("t2", "t1", "t3"), db.strings("SELECT id FROM tracks ORDER BY name"))
            assertEquals(listOf("t1", "t3"), db.playlist())
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
            assertEquals(emptyList<String>(), db.strings("SELECT name FROM sqlite_master WHERE name LIKE '%_kept'"))
        }
    }

    @Test
    fun `12 to 13 keeps the playlists even with foreign keys on`() {
        helper.createDatabase(DB_NAME, 12).use { db ->
            addTracksAndPlaylist(db)
            // Dropping the old tracks table cascades to playlist_tracks while foreign keys are on.
            db.execSQL("PRAGMA foreign_keys = ON")

            Migrations.MIGRATION_12_13.migrate(db)

            assertEquals(listOf("t1", "t3"), db.playlist())
            assertEquals(3, db.strings("SELECT id FROM tracks").size)
        }
    }

    private fun addTracksAndPlaylist(db: SupportSQLiteDatabase) {
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
