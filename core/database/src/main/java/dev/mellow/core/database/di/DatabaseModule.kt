package dev.mellow.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.migration.Migrations
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MellowDatabase =
        Room.databaseBuilder(
            context,
            MellowDatabase::class.java,
            "mellow.db",
        )
            .addMigrations(
                Migrations.MIGRATION_2_3,
                Migrations.MIGRATION_3_4,
                Migrations.MIGRATION_4_5,
                Migrations.MIGRATION_5_6,
            )
            .build()

    @Provides
    fun provideServerDao(db: MellowDatabase) = db.serverDao()

    @Provides
    fun provideAlbumDao(db: MellowDatabase) = db.albumDao()

    @Provides
    fun provideArtistDao(db: MellowDatabase) = db.artistDao()

    @Provides
    fun provideTrackDao(db: MellowDatabase) = db.trackDao()

    @Provides
    fun providePlaylistDao(db: MellowDatabase) = db.playlistDao()

    @Provides
    fun providePendingPlaybackEventDao(db: MellowDatabase) = db.pendingPlaybackEventDao()

    @Provides
    fun provideDownloadDao(db: MellowDatabase) = db.downloadDao()
}
