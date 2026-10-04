package dev.mellow.core.network

import java.net.URL
import java.security.KeyStore
import java.util.concurrent.Executors
import javax.net.ssl.KeyManagerFactory
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLException
import javax.net.ssl.SSLServerSocket
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Before
import org.junit.Test

/**
 * Talks to a local HTTPS server whose certificate is self-signed and issued for another host name
 * (`self-signed.invalid`), so a trusted download needs both the trust manager and the host name check relaxed.
 */
class SelfSignedTrustTest {

    private lateinit var server: SSLServerSocket
    private val pool = Executors.newCachedThreadPool()

    @Before
    fun setUp() {
        val keyStore = KeyStore.getInstance("PKCS12").apply {
            SelfSignedTrustTest::class.java.getResourceAsStream("/self-signed.p12").use { load(it, PASSWORD) }
        }
        val keyManagers = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm())
            .apply { init(keyStore, PASSWORD) }
            .keyManagers
        val context = SSLContext.getInstance("TLS").apply { init(keyManagers, null, null) }
        server = context.serverSocketFactory.createServerSocket(0) as SSLServerSocket
        pool.execute {
            while (!server.isClosed) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                pool.execute {
                    client.use {
                        runCatching {
                            val reader = it.getInputStream().bufferedReader()
                            while (!reader.readLine().isNullOrEmpty()) Unit
                            it.getOutputStream().apply {
                                write("HTTP/1.1 200 OK\r\nContent-Length: ${BODY.size}\r\nConnection: close\r\n\r\n".toByteArray())
                                write(BODY)
                                flush()
                            }
                        }
                    }
                }
            }
        }
    }

    @After
    fun tearDown() {
        server.close()
        pool.shutdownNow()
    }

    @Test
    fun `self-signed server is rejected when trust is off`() {
        val connection = openHttpConnection(url(), trustSelfSigned = false)

        assertThrows(SSLException::class.java) { connection.inputStream.use { it.readBytes() } }
    }

    @Test
    fun `self-signed server is downloaded from when trust is on`() {
        val connection = openHttpConnection(url(), trustSelfSigned = true)

        assertEquals(200, connection.responseCode)
        assertEquals(BODY.toList(), connection.inputStream.use { it.readBytes() }.toList())
    }

    @Test
    fun `plain http is unaffected by the trust setting`() {
        val connection = openHttpConnection(URL("http://127.0.0.1:1/"), trustSelfSigned = true)

        assertEquals("http", connection.url.protocol)
    }

    private fun url() = URL("https://127.0.0.1:${server.localPort}/Items/1/Images/Primary")

    private companion object {
        val PASSWORD = "changeit".toCharArray()
        val BODY = byteArrayOf(1, 2, 3, 4)
    }
}
