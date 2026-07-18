package dev.mellow.app.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.mellow.app.BuildConfig
import dev.mellow.core.common.DownloadExecutor
import dev.mellow.core.player.download.MellowDownloadManager
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Named("appVersion")
    fun provideAppVersion(): String = BuildConfig.VERSION_NAME
}

@Module
@InstallIn(SingletonComponent::class)
abstract class AppBindingsModule {

    @Binds
    @Singleton
    abstract fun bindDownloadExecutor(impl: MellowDownloadManager): DownloadExecutor
}
