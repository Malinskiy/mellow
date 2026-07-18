package dev.mellow.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.okhttp.OkHttpFactory
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import java.util.UUID
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

@Singleton
class JellyfinClientWrapper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val networkPreferences: NetworkPreferences,
    @Named("appVersion") private val appVersion: String,
) {
    private var _api: ApiClient? = null
    val api: ApiClient get() = requireNotNull(_api) { "API client not initialized. Call connect() first." }

    private fun createJellyfinInstance(): Jellyfin {
        val okHttpClient = createOkHttpClient(networkPreferences.isTrustSelfSignedSync())
        val factory = OkHttpFactory(okHttpClient)
        return createJellyfin {
            clientInfo = ClientInfo("Mellow", appVersion)
            this.context = this@JellyfinClientWrapper.context
            apiClientFactory = factory
            socketConnectionFactory = factory
        }
    }

    fun connect(serverUrl: String, deviceInfo: DeviceInfo) {
        _api = createJellyfinInstance().createApi(
            baseUrl = serverUrl,
            deviceInfo = deviceInfo,
        )
    }

    fun authenticate(accessToken: String) {
        _api?.update(accessToken = accessToken)
    }

    val isConnected: Boolean get() = _api != null

    fun restoreSession(serverUrl: String, accessToken: String) {
        if (!isConnected) {
            connect(serverUrl, DeviceInfo(id = UUID.randomUUID().toString(), name = "Mellow"))
        }
        authenticate(accessToken)
    }
}
