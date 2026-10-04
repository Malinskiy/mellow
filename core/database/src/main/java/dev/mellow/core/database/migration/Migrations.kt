package dev.mellow.core.database.migration

import android.util.Log
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

private const val TAG = "Migrations"

/** The tracks table's columns, as of version 13. */
private const val TRACK_COLUMNS =
    "`id`, `serverId`, `name`, `sortName`, `albumId`, `albumName`, `artistId`, `artistName`, " +
    "`trackNumber`, `discNumber`, `durationMs`, `genres`, `imageTag`, `isFavorite`, `playCount`, " +
    "`lastPlayedAt`, `normalizationGain`, `container`, `codec`, `bitrate`, `sampleRate`, `channels`, " +
    "`resolvedArtistId`, `dateAdded`, `lastSynced`"

object Migrations {

    /**
     * Speeds the library up on large libraries (measured on a million tracks by QueryBenchmark):
     * - Track names compare ignoring case (COLLATE NOCASE), as the name orders always sorted them, so an index can do
     *   that sorting. SQLite can't change a column's collation, so the tracks table is rebuilt.
     * - Indexes for the Tracks tab's orders, Android Auto's Songs, album and artist screens, Home and favorites, and
     *   the downloads (see TrackEntity and DownloadEntity).
     *
     * playlist_tracks references tracks with ON DELETE CASCADE, so dropping the old table would empty every playlist
     * while foreign keys are on: its rows are set aside and put back.
     */
    val MIGRATION_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val started = System.nanoTime()
            db.execSQL("CREATE TABLE `playlist_tracks_kept` AS SELECT * FROM `playlist_tracks`")
            db.execSQL(
                """
                CREATE TABLE `tracks_new` (
                    `id` TEXT NOT NULL,
                    `serverId` TEXT NOT NULL,
                    `name` TEXT NOT NULL COLLATE NOCASE,
                    `sortName` TEXT NOT NULL,
                    `albumId` TEXT,
                    `albumName` TEXT,
                    `artistId` TEXT,
                    `artistName` TEXT,
                    `trackNumber` INTEGER,
                    `discNumber` INTEGER,
                    `durationMs` INTEGER NOT NULL,
                    `genres` TEXT NOT NULL,
                    `imageTag` TEXT,
                    `isFavorite` INTEGER NOT NULL,
                    `playCount` INTEGER NOT NULL,
                    `lastPlayedAt` INTEGER NOT NULL,
                    `normalizationGain` REAL,
                    `container` TEXT,
                    `codec` TEXT,
                    `bitrate` INTEGER,
                    `sampleRate` INTEGER,
                    `channels` INTEGER,
                    `resolvedArtistId` TEXT,
                    `dateAdded` INTEGER NOT NULL,
                    `lastSynced` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )
            db.execSQL("INSERT INTO `tracks_new` ($TRACK_COLUMNS) SELECT $TRACK_COLUMNS FROM `tracks`")
            db.execSQL("DROP TABLE `tracks`")
            db.execSQL("ALTER TABLE `tracks_new` RENAME TO `tracks`")
            db.execSQL("DELETE FROM `playlist_tracks`")
            db.execSQL("INSERT INTO `playlist_tracks` SELECT * FROM `playlist_tracks_kept`")
            db.execSQL("DROP TABLE `playlist_tracks_kept`")
            db.execSQL("CREATE INDEX `index_tracks_artistName` ON `tracks` (`artistName`)")
            db.execSQL(
                "CREATE INDEX `index_tracks_serverId_dateAdded_id` " +
                    "ON `tracks` (`serverId` ASC, `dateAdded` DESC, `id` ASC)",
            )
            db.execSQL(
                "CREATE INDEX `index_tracks_serverId_name_dateAdded_id` " +
                    "ON `tracks` (`serverId` ASC, `name` ASC, `dateAdded` DESC, `id` ASC)",
            )
            db.execSQL(
                "CREATE INDEX `index_tracks_serverId_albumName_dateAdded_id` " +
                    "ON `tracks` (`serverId` ASC, `albumName` DESC, `dateAdded` DESC, `id` ASC)",
            )
            db.execSQL("CREATE INDEX `index_tracks_serverId_sortName_id` ON `tracks` (`serverId`, `sortName`, `id`)")
            db.execSQL(
                "CREATE INDEX `index_tracks_albumId_discNumber_trackNumber_id` " +
                    "ON `tracks` (`albumId`, `discNumber`, `trackNumber`, `id`)",
            )
            db.execSQL(
                "CREATE INDEX `index_tracks_resolvedArtistId_playCount` " +
                    "ON `tracks` (`resolvedArtistId` ASC, `playCount` DESC)",
            )
            db.execSQL("CREATE INDEX `index_tracks_serverId_lastPlayedAt` ON `tracks` (`serverId`, `lastPlayedAt`)")
            db.execSQL("CREATE INDEX `index_tracks_serverId_playCount` ON `tracks` (`serverId`, `playCount`)")
            db.execSQL("CREATE INDEX `index_tracks_serverId_isFavorite` ON `tracks` (`serverId`, `isFavorite`)")
            db.execSQL(
                "CREATE INDEX `index_downloads_status_serverId_trackId` " +
                    "ON `downloads` (`status`, `serverId`, `trackId`)",
            )
            Log.i(TAG, "Migrated 12 to 13 in ${(System.nanoTime() - started) / 1_000_000} ms")
        }
    }

    /** Adds the bookkeeping table of the full library pass. */
    val MIGRATION_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `sync_pass_items` (
                    `kind` TEXT NOT NULL,
                    `itemId` TEXT NOT NULL,
                    PRIMARY KEY(`kind`, `itemId`)
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE IF EXISTS `album_artists`")
            db.execSQL("DROP TABLE IF EXISTS `track_artists`")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `album_artists` (
                    `albumId` TEXT NOT NULL,
                    `artistId` TEXT NOT NULL,
                    `artistName` TEXT NOT NULL,
                    `displayOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`albumId`, `artistId`),
                    FOREIGN KEY(`albumId`) REFERENCES `albums`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_album_artists_artistId` ON `album_artists` (`artistId`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `track_artists` (
                    `trackId` TEXT NOT NULL,
                    `artistId` TEXT NOT NULL,
                    `artistName` TEXT NOT NULL,
                    `displayOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`trackId`, `artistId`),
                    FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_track_artists_artistId` ON `track_artists` (`artistId`)",
            )
        }
    }

    val MIGRATION_9_10 = object : Migration(9, 10) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `album_artists` (
                    `albumId` TEXT NOT NULL,
                    `artistId` TEXT NOT NULL,
                    `artistName` TEXT NOT NULL,
                    `displayOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`albumId`, `artistId`),
                    FOREIGN KEY(`albumId`) REFERENCES `albums`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`artistId`) REFERENCES `artists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_album_artists_artistId` ON `album_artists` (`artistId`)",
            )

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `track_artists` (
                    `trackId` TEXT NOT NULL,
                    `artistId` TEXT NOT NULL,
                    `artistName` TEXT NOT NULL,
                    `displayOrder` INTEGER NOT NULL,
                    PRIMARY KEY(`trackId`, `artistId`),
                    FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`artistId`) REFERENCES `artists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_track_artists_artistId` ON `track_artists` (`artistId`)",
            )
        }
    }

    val MIGRATION_8_9 = object : Migration(8, 9) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE artists ADD COLUMN cleanName TEXT NOT NULL DEFAULT ''")
            db.execSQL("ALTER TABLE artists ADD COLUMN musicBrainzId TEXT")

            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `artist_aliases` (
                    `serverId` TEXT NOT NULL,
                    `rawArtistId` TEXT NOT NULL,
                    `canonicalArtistId` TEXT NOT NULL,
                    `lastSynced` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `rawArtistId`)
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_artist_aliases_serverId_canonicalArtistId` ON `artist_aliases` (`serverId`, `canonicalArtistId`)",
            )

            db.execSQL("ALTER TABLE albums ADD COLUMN resolvedArtistId TEXT")
            db.execSQL("ALTER TABLE tracks ADD COLUMN resolvedArtistId TEXT")
        }
    }

    val MIGRATION_7_8 = object : Migration(7, 8) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `search_queries` (
                    `serverId` TEXT NOT NULL,
                    `queryText` TEXT NOT NULL,
                    `searchedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`serverId`, `queryText`)
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_6_7 = object : Migration(6, 7) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `lyrics` (
                    `trackId` TEXT NOT NULL,
                    `serverId` TEXT NOT NULL,
                    `lyricsData` TEXT NOT NULL,
                    `lastSynced` INTEGER NOT NULL,
                    PRIMARY KEY(`trackId`)
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_5_6 = object : Migration(5, 6) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_albums_artistName` ON `albums` (`artistName`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_tracks_artistName` ON `tracks` (`artistName`)")
        }
    }

    val MIGRATION_4_5 = object : Migration(4, 5) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `downloads` (
                    `trackId` TEXT NOT NULL,
                    `albumId` TEXT,
                    `serverId` TEXT NOT NULL,
                    `status` INTEGER NOT NULL,
                    `progress` REAL NOT NULL,
                    `bytesDownloaded` INTEGER NOT NULL,
                    `totalBytes` INTEGER NOT NULL,
                    `quality` TEXT NOT NULL,
                    `filePath` TEXT,
                    `requestedAt` INTEGER NOT NULL,
                    `completedAt` INTEGER NOT NULL,
                    `errorMessage` TEXT,
                    `lastSynced` INTEGER NOT NULL,
                    PRIMARY KEY(`trackId`)
                )
                """.trimIndent(),
            )
        }
    }

    val MIGRATION_3_4 = object : Migration(3, 4) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE tracks ADD COLUMN lastPlayedAt INTEGER NOT NULL DEFAULT 0")
        }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // Create playlists table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlists` (
                    `id` TEXT NOT NULL,
                    `serverId` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `sortName` TEXT NOT NULL,
                    `trackCount` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `imageTag` TEXT,
                    `isFavorite` INTEGER NOT NULL,
                    `isLocal` INTEGER NOT NULL,
                    `lastSynced` INTEGER NOT NULL,
                    PRIMARY KEY(`id`)
                )
                """.trimIndent(),
            )

            // Create playlist_tracks join table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `playlist_tracks` (
                    `playlistId` TEXT NOT NULL,
                    `trackId` TEXT NOT NULL,
                    `position` INTEGER NOT NULL,
                    `addedAt` INTEGER NOT NULL,
                    PRIMARY KEY(`playlistId`, `trackId`),
                    FOREIGN KEY(`playlistId`) REFERENCES `playlists`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE,
                    FOREIGN KEY(`trackId`) REFERENCES `tracks`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_playlist_tracks_trackId` ON `playlist_tracks` (`trackId`)",
            )

            // Create pending_playback_events table
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `pending_playback_events` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `serverId` TEXT NOT NULL,
                    `trackId` TEXT NOT NULL,
                    `eventType` TEXT NOT NULL,
                    `positionMs` INTEGER NOT NULL,
                    `durationMs` INTEGER NOT NULL,
                    `timestamp` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }
    }
}
