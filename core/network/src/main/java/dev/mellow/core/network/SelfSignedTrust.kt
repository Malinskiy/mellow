package dev.mellow.core.network

import okhttp3.OkHttpClient
import java.net.HttpURLConnection
import java.net.URL
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

private val trustAllManager = object : X509TrustManager {
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {}
    override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
}

private val trustAllSslContext: SSLContext = SSLContext.getInstance("TLS").apply {
    init(null, arrayOf(trustAllManager), SecureRandom())
}

private val acceptAnyHostname = HostnameVerifier { _, _ -> true }

fun OkHttpClient.Builder.trustSelfSignedCertificates(): OkHttpClient.Builder = apply {
    sslSocketFactory(trustAllSslContext.socketFactory, trustAllManager)
    hostnameVerifier(acceptAnyHostname)
}

/**
 * Opens [url] like [URL.openConnection]. With [trustSelfSigned], an HTTPS connection accepts self-signed certificates
 * the same way [trustSelfSignedCertificates] does for OkHttp clients, for code that downloads without OkHttp.
 */
fun openHttpConnection(url: URL, trustSelfSigned: Boolean): HttpURLConnection {
    val connection = url.openConnection() as HttpURLConnection
    if (trustSelfSigned && connection is HttpsURLConnection) {
        connection.sslSocketFactory = trustAllSslContext.socketFactory
        connection.hostnameVerifier = acceptAnyHostname
    }
    return connection
}

fun createOkHttpClient(trustSelfSigned: Boolean): OkHttpClient {
    val builder = OkHttpClient.Builder()
    if (trustSelfSigned) {
        builder.trustSelfSignedCertificates()
    }
    return builder.build()
}
