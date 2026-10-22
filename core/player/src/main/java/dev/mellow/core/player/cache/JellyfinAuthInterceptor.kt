package dev.mellow.core.player.cache

import kotlinx.coroutines.runBlocking
import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.entity.ServerEntity
import dev.mellow.core.network.AUTHORIZATION_HEADER
import dev.mellow.core.network.jellyfinAuthorization
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Authenticates stream and download requests to a signed-in Jellyfin server with that server's token, in the
 * `Authorization` header: Jellyfin 12 no longer accepts it in the URL.
 *
 * Downloads queued by older versions still have `api_key=` in their URLs; it's taken out, as a server may turn down a
 * request that has it.
 */
@Singleton
class JellyfinAuthInterceptor @Inject constructor(
    private val serverDao: ServerDao,
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // OkHttp calls interceptors on its own threads, never the main one; the servers table has a row or two.
        val server = runBlocking { serverDao.getServers() }.firstOrNull { it.serves(request.url) }
            ?: return chain.proceed(request)
        return chain.proceed(
            request.newBuilder()
                .url(request.url.newBuilder().removeAllQueryParameters(LEGACY_TOKEN_PARAM).build())
                .header(AUTHORIZATION_HEADER, jellyfinAuthorization(server.accessToken))
                .build(),
        )
    }

    private fun ServerEntity.serves(url: HttpUrl): Boolean {
        val base = this.url.toHttpUrlOrNull() ?: return false
        val basePath = base.encodedPath.trimEnd('/')
        return base.scheme == url.scheme && base.host == url.host && base.port == url.port &&
            (url.encodedPath == basePath || url.encodedPath.startsWith("$basePath/"))
    }

    private companion object {
        const val LEGACY_TOKEN_PARAM = "api_key"
    }
}
