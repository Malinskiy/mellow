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

fun jellyfinStreamUrl(
    serverUrl: String,
    itemId: String,
    apiKey: String,
    quality: String = "original",
): String {
    val base = "$serverUrl/Audio/$itemId/stream"
    return when (quality) {
        "high" -> "$base?static=false&audioCodec=mp3&audioBitRate=320000&api_key=$apiKey"
        "medium" -> "$base?static=false&audioCodec=opus&audioBitRate=128000&container=ogg&api_key=$apiKey"
        else -> "$base?static=true&api_key=$apiKey"
    }
}
