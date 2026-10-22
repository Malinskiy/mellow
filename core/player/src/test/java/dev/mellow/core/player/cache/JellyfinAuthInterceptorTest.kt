package dev.mellow.core.player.cache

import dev.mellow.core.database.dao.ServerDao
import dev.mellow.core.database.entity.ServerEntity
import io.mockk.coEvery
import io.mockk.mockk
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class JellyfinAuthInterceptorTest {

    private val serverDao = mockk<ServerDao> {
        coEvery { getServers() } returns listOf(
            server("home", "https://music.example", "home-token"),
            server("away", "https://example.org/jellyfin/", "away-token"),
        )
    }

    @Test
    fun `stream request to a signed-in server carries its token in the Authorization header`() {
        val sent = send("https://music.example/Audio/t1/stream?static=true")

        assertEquals("MediaBrowser Token=\"home-token\"", sent.header("Authorization"))
        assertEquals("https://music.example/Audio/t1/stream?static=true", sent.url.toString())
    }

    @Test
    fun `server under a path gets its own token`() {
        val sent = send("https://example.org/jellyfin/Audio/t1/stream?static=true")

        assertEquals("MediaBrowser Token=\"away-token\"", sent.header("Authorization"))
    }

    @Test
    fun `token saved in the URL by an older version is taken out`() {
        val sent = send("https://music.example/Audio/t1/stream?static=false&audioCodec=mp3&api_key=old-token")

        assertEquals("https://music.example/Audio/t1/stream?static=false&audioCodec=mp3", sent.url.toString())
        assertEquals("MediaBrowser Token=\"home-token\"", sent.header("Authorization"))
    }

    @Test
    fun `request to another host gets no token`() {
        val sent = send("https://cdn.example/Audio/t1/stream?api_key=kept")

        assertNull(sent.header("Authorization"))
        assertEquals("https://cdn.example/Audio/t1/stream?api_key=kept", sent.url.toString())
    }

    @Test
    fun `same host on another path or port gets no token`() {
        assertNull(send("https://example.org/jellyfin2/Audio/t1/stream").header("Authorization"))
        assertNull(send("https://music.example:8920/Audio/t1/stream").header("Authorization"))
        assertNull(send("http://music.example/Audio/t1/stream").header("Authorization"))
    }

    /** Sends [url] through the interceptor and returns the request as it would reach the server. */
    private fun send(url: String): Request {
        var sent: Request? = null
        val client = OkHttpClient.Builder()
            .addInterceptor(JellyfinAuthInterceptor(serverDao))
            .addInterceptor { chain ->
                sent = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(ByteArray(0).toResponseBody())
                    .build()
            }
            .build()
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return requireNotNull(sent)
    }

    private fun server(id: String, url: String, token: String) = ServerEntity(
        id = id,
        name = id,
        url = url,
        userId = "user",
        accessToken = token,
        isActive = id == "home",
        lastConnected = 0,
    )
}
