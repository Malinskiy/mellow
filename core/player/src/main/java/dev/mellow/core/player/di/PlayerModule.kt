package dev.mellow.core.player.di

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mellow.core.player.cache.MellowCache
import dev.mellow.core.player.cache.MellowDataSourceFactory
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object PlayerModule {

    @Provides
    @Singleton
    fun provideMellowCache(@ApplicationContext context: Context): MellowCache =
        MellowCache(context)

    @Provides
    @Singleton
    fun provideMellowDataSourceFactory(cache: MellowCache): MellowDataSourceFactory =
        MellowDataSourceFactory(cache)

}
