package dev.mellow.core.data.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.mellow.core.data.repository.LibraryRepository
import dev.mellow.core.data.repository.LibraryRepositoryImpl
import dev.mellow.core.data.repository.UserRepository
import dev.mellow.core.data.repository.UserRepositoryImpl

@Module
@InstallIn(SingletonComponent::class)
abstract class DataModule {

    @Binds
    abstract fun bindLibraryRepository(impl: LibraryRepositoryImpl): LibraryRepository

    @Binds
    abstract fun bindUserRepository(impl: UserRepositoryImpl): UserRepository
}
