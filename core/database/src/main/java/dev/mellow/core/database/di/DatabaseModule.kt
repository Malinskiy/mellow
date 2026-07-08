package dev.mellow.core.database.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mellow.core.database.MellowDatabase
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
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideServerDao(db: MellowDatabase) = db.serverDao()

    @Provides
    fun provideAlbumDao(db: MellowDatabase) = db.albumDao()

    @Provides
    fun provideArtistDao(db: MellowDatabase) = db.artistDao()

    @Provides
    fun provideTrackDao(db: MellowDatabase) = db.trackDao()
}
