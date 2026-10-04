package dev.mellow.core.data.repository

import androidx.paging.PagingData
import dev.mellow.core.common.MellowResult
import dev.mellow.core.data.SyncProgress
import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.LibrarySort
import dev.mellow.core.model.Track
import kotlinx.coroutines.flow.Flow

interface LibraryRepository {
    /** The library's albums tab, a page at a time: only [genre]'s albums if set, only downloaded ones if asked. */
    fun getPagedAlbums(serverId: String, sort: LibrarySort, genre: String?, downloadedOnly: Boolean): Flow<PagingData<Album>>

    /** The library's artists tab, a page at a time; [Artist.albumCount] is the number of the library's albums. */
    fun getPagedArtists(serverId: String, sort: LibrarySort, downloadedOnly: Boolean): Flow<PagingData<Artist>>

    /** The library's tracks tab, a page at a time: every track of the server, not a recent subset. */
    fun getPagedTracks(serverId: String, sort: LibrarySort, downloadedOnly: Boolean): Flow<PagingData<Track>>

    /** [limit] tracks of [getPagedTracks] from position [offset]. */
    suspend fun getTracksSlice(
        serverId: String,
        sort: LibrarySort,
        downloadedOnly: Boolean,
        offset: Int,
        limit: Int,
    ): MellowResult<List<Track>>

    /** The genres of the library's albums (only downloaded albums if asked), sorted. */
    fun getGenres(serverId: String, downloadedOnly: Boolean): Flow<MellowResult<List<String>>>
    fun getAlbumTracks(albumId: String): Flow<MellowResult<List<Track>>>
    fun getArtistAlbumsById(artistId: String): Flow<MellowResult<List<Album>>>
    suspend fun getAlbum(albumId: String): MellowResult<Album?>
    fun observeAlbum(albumId: String): Flow<MellowResult<Album?>>
    suspend fun getArtist(artistId: String): MellowResult<Artist?>
    fun observeArtist(artistId: String): Flow<MellowResult<Artist?>>
    fun getArtistTracksById(artistId: String): Flow<MellowResult<List<Track>>>
    suspend fun countArtistAlbumsById(artistId: String): MellowResult<Int>
    suspend fun countArtistTracksById(artistId: String): MellowResult<Int>
    suspend fun getTrack(trackId: String): MellowResult<Track?>
    suspend fun search(serverId: String, query: String): MellowResult<List<Track>>
    suspend fun searchAlbums(serverId: String, query: String): MellowResult<List<Album>>
    suspend fun searchArtists(serverId: String, query: String): MellowResult<List<Artist>>
    fun getRecentSearches(serverId: String): Flow<MellowResult<List<String>>>
    suspend fun saveRecentSearch(serverId: String, query: String): MellowResult<Unit>
    suspend fun deleteRecentSearch(serverId: String, query: String): MellowResult<Unit>
    suspend fun clearRecentSearches(serverId: String): MellowResult<Unit>
    fun getPagedFavoriteTracks(serverId: String, downloadedOnly: Boolean): Flow<PagingData<Track>>
    fun getPagedFavoriteAlbums(serverId: String, downloadedOnly: Boolean): Flow<PagingData<Album>>
    fun getPagedFavoriteArtists(serverId: String, downloadedOnly: Boolean): Flow<PagingData<Artist>>

    /** [limit] tracks of [getPagedFavoriteTracks] from position [offset]. */
    suspend fun getFavoriteTracksSlice(
        serverId: String,
        downloadedOnly: Boolean,
        offset: Int,
        limit: Int,
    ): MellowResult<List<Track>>
    suspend fun countFavoriteTracks(serverId: String, downloadedOnly: Boolean): MellowResult<Int>

    /** [limit] favorite tracks picked at random from all of them, in random order, to shuffle the favorites. */
    suspend fun pickRandomFavoriteTracks(serverId: String, downloadedOnly: Boolean, limit: Int): MellowResult<List<Track>>

    /** [limit] favorite tracks picked at random, for the home screen. */
    fun getRandomFavoriteTracks(serverId: String, limit: Int): Flow<MellowResult<List<Track>>>
    fun getRecentlyAddedAlbums(serverId: String, limit: Int): Flow<MellowResult<List<Album>>>

    /** [limit] albums picked at random, for the home screen. */
    fun getRandomAlbums(serverId: String, limit: Int): Flow<MellowResult<List<Album>>>

    /** The [limit] genres with the most albums, most first. */
    fun getTopGenres(serverId: String, limit: Int): Flow<MellowResult<List<String>>>
    fun getRecentlyPlayedAlbums(serverId: String): Flow<MellowResult<List<Album>>>
    fun getMostPlayedAlbums(serverId: String): Flow<MellowResult<List<Album>>>
    suspend fun syncHomeScreenPriority(serverId: String, onProgress: (SyncProgress) -> Unit = {}): MellowResult<Set<String>>
    suspend fun syncLibrary(serverId: String, onProgress: (SyncProgress) -> Unit = {}): MellowResult<Unit>
    suspend fun syncFavorites(serverId: String): MellowResult<Unit>
    suspend fun cleanupOrphans(serverId: String, onProgress: (SyncProgress) -> Unit = {}): MellowResult<Unit>
    suspend fun getInstantMix(serverId: String, trackId: String): MellowResult<List<Track>>
}
