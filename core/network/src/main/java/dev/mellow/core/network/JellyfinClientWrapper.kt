package dev.mellow.core.network

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import org.jellyfin.sdk.Jellyfin
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.createJellyfin
import org.jellyfin.sdk.model.ClientInfo
import org.jellyfin.sdk.model.DeviceInfo
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JellyfinClientWrapper @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val jellyfin: Jellyfin = createJellyfin {
        clientInfo = ClientInfo("Mellow", "0.1.0")
        this.context = context
    }

    private var _api: ApiClient? = null
    val api: ApiClient get() = requireNotNull(_api) { "API client not initialized. Call connect() first." }

    fun connect(serverUrl: String, deviceInfo: DeviceInfo) {
        _api = jellyfin.createApi(
            baseUrl = serverUrl,
            deviceInfo = deviceInfo,
        )
    }

    fun authenticate(accessToken: String) {
        _api?.update(accessToken = accessToken)
    }

    val isConnected: Boolean get() = _api != null
}
