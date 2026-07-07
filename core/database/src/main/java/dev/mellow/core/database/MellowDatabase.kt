package dev.mellow.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import dev.mellow.core.database.converter.Converters
import dev.mellow.core.database.dao.AlbumDao
import dev.mellow.core.database.dao.ArtistDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.TrackEntity

@Database(
    entities = [
        AlbumEntity::class,
        ArtistEntity::class,
        TrackEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class MellowDatabase : RoomDatabase() {
    abstract fun albumDao(): AlbumDao
    abstract fun artistDao(): ArtistDao
    abstract fun trackDao(): TrackDao
}
