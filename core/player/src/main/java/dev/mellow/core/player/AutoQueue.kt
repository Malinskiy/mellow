package dev.mellow.core.player

import dev.mellow.core.common.QUEUE_WINDOW_SIZE
import dev.mellow.core.common.queueWindowStart
import dev.mellow.core.database.dao.PlaylistDao
import dev.mellow.core.database.dao.TrackDao
import dev.mellow.core.database.entity.TrackEntity

/**
 * What Android Auto queues when a track is picked from a list it browsed, and where the picked track is in it. The
 * list is the one the track names as its parent, or else its album. The library's songs, the favorites and
 * playlists can be long, so they're queued [QUEUE_WINDOW_SIZE] tracks around the picked one, as the app does.
 */
internal class AutoQueue(
    private val trackDao: TrackDao,
    private val playlistDao: PlaylistDao,
) {
    /**
     * The queue for the item Android Auto asked to play by its [mediaId], and where the picked track is in it. The
     * list comes from the ID ([BrowsedTrackId]), since a head unit sends nothing else; [parentHint], the parent the
     * item's extras name when a controller does send them, only counts for a plain track ID.
     */
    suspend fun forItem(
        mediaId: String,
        parentHint: String?,
        serverId: String,
        downloadedOnly: Boolean,
    ): Pair<List<TrackEntity>, Int>? {
        val picked = BrowsedTrackId.parse(mediaId)
        val track = trackDao.getTrackById(picked.trackId)
        return around(picked.trackId, track, picked.parentId ?: parentHint, serverId, downloadedOnly)
    }

    /**
     * The queue for the track [trackId] ([track] if it's in the library) picked under [parentId]: from the
     * downloaded tracks only if [downloadedOnly], as the list was browsed. `null` to play the track on its own.
     */
    suspend fun around(
        trackId: String,
        track: TrackEntity?,
        parentId: String?,
        serverId: String,
        downloadedOnly: Boolean,
    ): Pair<List<TrackEntity>, Int>? {
        val siblings = when {
            parentId?.startsWith("playlist:") == true -> {
                val playlistId = parentId.removePrefix("playlist:")
                val position = playlistDao.countPlaylistTracksBefore(playlistId, trackId, downloadedOnly)
                val start = queueWindowStart(position, playlistDao.countPlaylistTracks(playlistId, downloadedOnly))
                playlistDao.getPlaylistTracksSlice(playlistId, downloadedOnly, QUEUE_WINDOW_SIZE, start)
            }
            parentId == MellowMediaService.FAV_TRACKS -> {
                val position = trackDao.countFavoriteTracksBefore(serverId, trackId, downloadedOnly)
                val start = queueWindowStart(position, trackDao.countFavoriteTracks(serverId, downloadedOnly))
                trackDao.getFavoriteTracksSlice(serverId, downloadedOnly, QUEUE_WINDOW_SIZE, start)
            }
            parentId == MellowMediaService.LIBRARY_SONGS -> {
                // A song that isn't in the library has no place among the songs.
                val song = track ?: return null
                val position = trackDao.countTracksBefore(serverId, song.sortName, song.id, downloadedOnly)
                val start = queueWindowStart(position, trackDao.countTracks(serverId, downloadedOnly))
                trackDao.getTracksByServerPaged(serverId, downloadedOnly, QUEUE_WINDOW_SIZE, start)
            }
            parentId?.startsWith("album:") == true -> trackDao.getTracksByAlbumSync(parentId.removePrefix("album:"))
            // The top tracks an artist with no albums lists, as the app's artist screen plays them.
            parentId?.startsWith("artist:") == true ->
                trackDao.getTracksByResolvedArtistSync(parentId.removePrefix("artist:"))
            else -> track?.albumId?.let { trackDao.getTracksByAlbumSync(it) }
        }
        // Not where it was listed (the list changed, or it isn't downloaded): play it alone, not another track.
        val index = siblings?.indexOfFirst { it.id == trackId } ?: return null
        return if (index >= 0) siblings to index else null
    }
}

/** The media ID Android Auto gets for [track] listed under [parentId] (its album if `null`): see [BrowsedTrackId]. */
internal fun browsedTrackMediaId(parentId: String?, track: TrackEntity): String =
    BrowsedTrackId.of(parentId ?: track.albumId?.let { "album:$it" }, track.id)

/**
 * The parent a track listed under [parentId] names, in its media ID ([browsedTrackMediaId]) and its extras: the list
 * Android Auto queues around it when it's picked. `null` for a track that's queued with its album.
 */
internal fun queueParentOf(parentId: String): String? = when {
    parentId == MellowMediaService.LIBRARY_SONGS ||
        parentId == MellowMediaService.FAV_TRACKS ||
        parentId.startsWith("playlist:") -> parentId
    else -> null
}
