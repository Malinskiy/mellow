package dev.mellow.core.network

const val AUTHORIZATION_HEADER = "Authorization"

/**
 * The `Authorization` header value that authenticates a request made outside the SDK (streams, artwork) with
 * [accessToken]. Jellyfin 12 rejects the legacy ways of passing it: `api_key` in the URL and the `X-Emby-*` headers.
 */
fun jellyfinAuthorization(accessToken: String): String = "MediaBrowser Token=\"$accessToken\""
