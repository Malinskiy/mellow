package dev.mellow.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.mellow.core.database.converter.Converters
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistAliasDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.DownloadDao
import dev.mellow.core.database.dao.LyricsDao
import dev.mellow.core.database.dao.PendingPlaybackEventDao
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.SearchQueryDao
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistAliasEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.LyricsEntity
import dev.mellow.core.database.entity.PendingPlaybackEventEntity
import dev.mellow.core.database.entity.PlaylistEntity
import dev.mellow.core.database.entity.PlaylistTrackCrossRef
import dev.mellow.core.database.entity.SearchQueryEntity
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.database.entity.TrackEntity

@Database(
    entities = [
        ServerEntity::class,
        AlbumEntity::class,
        ArtistEntity::class,
        ArtistAliasEntity::class,
        TrackEntity::class,
        PlaylistEntity::class,
        PlaylistTrackCrossRef::class,
        PendingPlaybackEventEntity::class,
        DownloadEntity::class,
        LyricsEntity::class,
        SearchQueryEntity::class,
    ],
    version = 9,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MellowDatabase : RoomDatabase() {
    abstract fun serverDao(): ServerDao
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun artistAliasDao(): ArtistAliasDao
    abstract fun trackDao(): TrackDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun pendingPlaybackEventDao(): PendingPlaybackEventDao
    abstract fun downloadDao(): DownloadDao
    abstract fun lyricsDao(): LyricsDao
    abstract fun searchQueryDao(): SearchQueryDao
}
