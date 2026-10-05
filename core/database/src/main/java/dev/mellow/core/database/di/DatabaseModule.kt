package dev.mellow.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mellow.core.database.DatabaseTransactionRunner
import dev.mellow.core.database.MellowDatabase
import dev.mellow.core.database.RoomTransactionRunner
import dev.mellow.core.database.dao.AlbumKeysetQueryFactory
import dev.mellow.core.database.dao.ArtistKeysetQueryFactory
import dev.mellow.core.database.dao.TrackKeysetQueryFactory
import dev.mellow.core.database.dao.RecentlyPlayedAlbumsObserver
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
                Migrations.MIGRATION_6_7,
                Migrations.MIGRATION_7_8,
                Migrations.MIGRATION_8_9,
                Migrations.MIGRATION_9_10,
                Migrations.MIGRATION_10_11,
                Migrations.MIGRATION_11_12,
                Migrations.MIGRATION_12_13,
            )
            .build()

    @Provides
    fun provideTransactionRunner(db: MellowDatabase): DatabaseTransactionRunner = RoomTransactionRunner(db)

    @Provides
    fun provideServerDao(db: MellowDatabase) = db.serverDao()

    @Provides
    fun provideAlbumDao(db: MellowDatabase) = db.albumDao()

    @Provides
    fun provideArtistDao(db: MellowDatabase) = db.artistDao()

    @Provides
    fun provideArtistAliasDao(db: MellowDatabase) = db.artistAliasDao()

    @Provides
    fun provideTrackDao(db: MellowDatabase) = db.trackDao()

    @Provides
    @Singleton
    fun provideAlbumKeysetQueryFactory(db: MellowDatabase) = AlbumKeysetQueryFactory(db)

    @Provides
    @Singleton
    fun provideArtistKeysetQueryFactory(db: MellowDatabase) = ArtistKeysetQueryFactory(db)

    @Provides
    @Singleton
    fun provideTrackKeysetQueryFactory(db: MellowDatabase) = TrackKeysetQueryFactory(db)

    @Provides
    @Singleton
    fun provideRecentlyPlayedAlbumsObserver(db: MellowDatabase) = RecentlyPlayedAlbumsObserver(db)

    @Provides
    fun providePlaylistDao(db: MellowDatabase) = db.playlistDao()

    @Provides
    fun providePendingPlaybackEventDao(db: MellowDatabase) = db.pendingPlaybackEventDao()

    @Provides
    fun provideDownloadDao(db: MellowDatabase) = db.downloadDao()

    @Provides
    fun provideLyricsDao(db: MellowDatabase) = db.lyricsDao()

    @Provides
    fun provideSearchQueryDao(db: MellowDatabase) = db.searchQueryDao()

    @Provides
    fun provideSyncPassDao(db: MellowDatabase) = db.syncPassDao()
}
