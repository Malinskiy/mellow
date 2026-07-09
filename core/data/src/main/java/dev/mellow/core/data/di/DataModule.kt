package dev.mellow.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.mellow.core.data.playback.JellyfinPlaybackReporter
import dev.mellow.core.data.repository.DownloadRepository
import dev.mellow.core.data.repository.DownloadRepositoryImpl
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.data.repository.LibraryRepositoryImpl
import dev.mellow.core.data.repository.PlaylistRepository
import dev.mellow.core.data.repository.PlaylistRepositoryImpl
import dev.mellow.core.data.repository.UserRepository
import dev.mellow.core.data.repository.UserRepositoryImpl
import dev.mellow.core.common.PlaybackReporter

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository

    @Binds
    abstract fun bindPlaybackReporter(impl: JellyfinPlaybackReporter): PlaybackReporter

    @Binds
    abstract fun bindDownloadRepository(impl: DownloadRepositoryImpl): DownloadRepository

    @Binds
    abstract fun bindPlaylistRepository(impl: PlaylistRepositoryImpl): PlaylistRepository
}
