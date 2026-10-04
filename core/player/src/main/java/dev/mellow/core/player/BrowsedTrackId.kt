package dev.mellow.core.player

/**
 * The media ID of a track Android Auto browses, naming the list it's listed in: `track:<parentId>/<trackId>`, for
 * example `track:library_songs/<trackId>`, `track:fav_tracks/<trackId>`, `track:playlist:<playlistId>/<trackId>`,
 * `track:album:<albumId>/<trackId>` or `track:artist:<artistId>/<trackId>`.
 *
 * A head unit plays a browsed item through the legacy session's `playFromMediaId`, so the session gets back the media
 * ID and nothing else: the item's extras are lost. The list a track was tapped in decides what's queued around it, so
 * it has to be in the ID.
 *
 * The track ID is what follows the last `/`, and the parent is what's between the prefix and that `/`, so a parent
 * may itself contain `:` or `/`. Jellyfin track IDs never contain `/`; one that did would be left plain. Any other
 * media ID, a malformed `track:` one included, reads as a plain track ID: the app, saved queues and other
 * controllers use plain IDs, and the player's queue only ever holds them.
 *
 * @property parentId the list the track was listed in, `null` for a plain track ID
 * @property trackId the track's own ID, the one the library, the player and saved queues know it by
 */
internal data class BrowsedTrackId(val parentId: String?, val trackId: String) {

    companion object {
        private const val PREFIX = "track:"
        private const val SEPARATOR = '/'

        /** The media ID of the track [trackId] listed under [parentId]: the plain [trackId] if there's no parent. */
        fun of(parentId: String?, trackId: String): String =
            if (parentId.isNullOrEmpty() || trackId.isEmpty() || SEPARATOR in trackId) {
                trackId
            } else {
                "$PREFIX$parentId$SEPARATOR$trackId"
            }

        /** Reads [mediaId]: a browsed track's list and track, or, for anything else, a plain track ID. */
        fun parse(mediaId: String): BrowsedTrackId {
            if (!mediaId.startsWith(PREFIX)) return BrowsedTrackId(parentId = null, trackId = mediaId)
            val rest = mediaId.substring(PREFIX.length)
            val separator = rest.lastIndexOf(SEPARATOR)
            if (separator <= 0 || separator == rest.lastIndex) return BrowsedTrackId(parentId = null, trackId = mediaId)
            return BrowsedTrackId(parentId = rest.substring(0, separator), trackId = rest.substring(separator + 1))
        }
    }
}
