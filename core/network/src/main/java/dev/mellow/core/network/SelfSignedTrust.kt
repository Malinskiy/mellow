package dev.mellow.core.network

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
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

fun OkHttpClient.Builder.trustSelfSignedCertificates(): OkHttpClient.Builder = apply {
    sslSocketFactory(trustAllSslContext.socketFactory, trustAllManager)
    hostnameVerifier { _, _ -> true }
}

fun createOkHttpClient(trustSelfSigned: Boolean): OkHttpClient {
    val builder = OkHttpClient.Builder()
    if (trustSelfSigned) {
        builder.trustSelfSignedCertificates()
    }
    return builder.build()
}
