package dev.mellow.core.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object Migrations {

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
