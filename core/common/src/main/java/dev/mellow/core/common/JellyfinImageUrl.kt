package dev.mellow.core.common

fun jellyfinImageUrl(
    serverUrl: String,
    itemId: String,
    maxWidth: Int = 600,
    quality: Int = 90,
    apiKey: String? = null,
): String {
    val base = "$serverUrl/Items/$itemId/Images/Primary?maxWidth=$maxWidth&quality=$quality"
    return if (apiKey != null) "$base&api_key=$apiKey" else base
}

fun jellyfinStreamUrl(serverUrl: String, itemId: String, apiKey: String): String =
    "$serverUrl/Audio/$itemId/stream?static=true&api_key=$apiKey"
