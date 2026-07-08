package dev.mellow.core.common

fun jellyfinImageUrl(serverUrl: String, itemId: String, maxWidth: Int = 600, quality: Int = 90): String =
    "$serverUrl/Items/$itemId/Images/Primary?maxWidth=$maxWidth&quality=$quality"

fun jellyfinStreamUrl(serverUrl: String, itemId: String, apiKey: String): String =
    "$serverUrl/Audio/$itemId/stream?static=true&api_key=$apiKey"
