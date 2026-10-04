package dev.mellow.core.database.migration

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import dev.mellow.core.database.MellowDatabase
import org.junit.Assert.assertEquals
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

    private companion object {
        const val DB_NAME = "migration-test"
    }
}
