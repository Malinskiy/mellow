package dev.mellow.core.data

import java.io.IOException

/**
 * Artwork precaching stopped because the Jellyfin server can't be reached (connection refused, timed out or lost,
 * host not found): the remaining items are left to the next run instead of all failing.
 */
class ArtworkServerUnreachableException(cause: IOException) :
    IOException("Jellyfin server unreachable while caching artwork", cause)
