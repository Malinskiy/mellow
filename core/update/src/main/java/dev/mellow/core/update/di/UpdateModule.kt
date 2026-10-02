package dev.mellow.core.update.di

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.mellow.core.update.GitHubReleaseApi
import dev.mellow.core.update.OkHttpGitHubReleaseApi
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton

/**
 * The client for api.github.com and release downloads. Deliberately separate from the Jellyfin client: it uses only
 * the system trust store (no self-signed trust) and never follows a redirect from HTTPS down to HTTP.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class GitHubClient

@Module
@InstallIn(SingletonComponent::class)
object UpdateModule {

    private const val TIMEOUT_SECONDS = 30L

    @Provides
    @Singleton
    @GitHubClient
    fun provideGitHubOkHttpClient(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(false)
        .build()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class UpdateBindingsModule {

    @Binds
    abstract fun bindGitHubReleaseApi(impl: OkHttpGitHubReleaseApi): GitHubReleaseApi
}
