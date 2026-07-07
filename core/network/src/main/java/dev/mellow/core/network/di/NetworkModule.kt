package dev.mellow.core.network.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.mellow.core.network.JellyfinClientWrapper
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJellyfinClient(): JellyfinClientWrapper = JellyfinClientWrapper()
}
