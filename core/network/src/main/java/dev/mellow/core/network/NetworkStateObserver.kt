package dev.mellow.core.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NetworkStateObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val jellyfinClientWrapper: JellyfinClientWrapper,
    private val networkPreferences: NetworkPreferences,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    private val heartbeatClient by lazy {
        val builder = OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
        if (networkPreferences.isTrustSelfSignedSync()) {
            builder.trustSelfSignedCertificates()
        }
        builder.build()
    }

    private val _connectionState = MutableStateFlow<ConnectionState>(ConnectionState.Offline)
    val connectionState: StateFlow<ConnectionState> = _connectionState.asStateFlow()

    private val _isOfflineMode = MutableStateFlow(false)
    val isOfflineMode: StateFlow<Boolean> = _isOfflineMode.asStateFlow()

    @Volatile
    private var hasNetwork = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            hasNetwork = true
            refreshConnectionState()
        }

        override fun onLost(network: Network) {
            hasNetwork = false
            _connectionState.value = ConnectionState.Offline
        }
    }

    init {
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        startHeartbeatLoop()
    }

    fun setOfflineMode(enabled: Boolean) {
        _isOfflineMode.value = enabled
        if (enabled) {
            _connectionState.value = ConnectionState.Offline
        } else {
            refreshConnectionState()
        }
    }

    fun refresh() {
        refreshConnectionState()
    }

    fun markConnected() {
        hasNetwork = true
        _connectionState.value = ConnectionState.Connected
    }

    fun cleanup() {
        connectivityManager.unregisterNetworkCallback(networkCallback)
        scope.cancel()
    }

    private fun startHeartbeatLoop() {
        scope.launch {
            while (true) {
                delay(HEARTBEAT_INTERVAL_MS)
                if (hasNetwork && !_isOfflineMode.value) {
                    performHeartbeat()
                }
            }
        }
    }

    private fun refreshConnectionState() {
        if (_isOfflineMode.value) {
            _connectionState.value = ConnectionState.Offline
            return
        }
        if (!hasNetwork) {
            _connectionState.value = ConnectionState.Offline
            return
        }
        scope.launch {
            performHeartbeat()
        }
    }

    private fun performHeartbeat() {
        val serverUrl = getServerUrl() ?: return
        val reachable = checkServerReachable(serverUrl)
        _connectionState.value = if (reachable) {
            ConnectionState.Connected
        } else {
            ConnectionState.ServerUnreachable
        }
    }

    private fun getServerUrl(): String? {
        if (!jellyfinClientWrapper.isConnected) return null
        return try {
            jellyfinClientWrapper.api.baseUrl
        } catch (_: IllegalStateException) {
            null
        }
    }

    private fun checkServerReachable(serverUrl: String): Boolean {
        return try {
            val request = Request.Builder()
                .url("$serverUrl/System/Info/Public")
                .get()
                .build()
            heartbeatClient.newCall(request).execute().use { response ->
                response.isSuccessful
            }
        } catch (_: Exception) {
            false
        }
    }

    companion object {
        private const val HEARTBEAT_INTERVAL_MS = 30_000L
    }
}
