package dev.mellow.core.network

sealed interface ConnectionState {
    /** Device has network and server responded to last heartbeat */
    data object Connected : ConnectionState
    /** Device has network but server did not respond */
    data object ServerUnreachable : ConnectionState
    /** Device has no network connectivity at all */
    data object Offline : ConnectionState
}
