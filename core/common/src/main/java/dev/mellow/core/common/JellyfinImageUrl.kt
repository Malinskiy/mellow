package dev.mellow.core.common

const val ARTWORK_AUTHORITY = "com.malinskiy.mellow.artwork"

fun artworkUri(itemId: String): String = "content://$ARTWORK_AUTHORITY/$itemId"

fun jellyfinImageUrl(
    serverUrl: String,
    itemId: String,
    maxWidth: Int = 600,
    quality: Int = 90,
): String = "$serverUrl/Items/$itemId/Images/Primary?maxWidth=$maxWidth&quality=$quality&format=Webp"

/** The token goes in the `Authorization` header, added by the player's HTTP client, never in the URL. */
fun jellyfinStreamUrl(
    serverUrl: String,
    itemId: String,
    quality: String = "original",
): String {
    val base = "$serverUrl/Audio/$itemId/stream"
    return when (quality) {
        "high" -> "$base?static=false&audioCodec=mp3&audioBitRate=320000"
        "medium" -> "$base?static=false&audioCodec=opus&audioBitRate=128000&container=ogg"
        else -> "$base?static=true"
    }
}
