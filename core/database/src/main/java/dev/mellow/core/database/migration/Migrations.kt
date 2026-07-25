package dev.mellow.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

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
