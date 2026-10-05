package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import dev.mellow.core.database.converter.Converters
import dev.mellow.core.database.entity.AlbumArtistCrossRef
import dev.mellow.core.database.entity.AlbumEntity
import dev.mellow.core.database.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

/** The favorite albums, in the order they were first saved. */
private const val FAVORITE_ALBUMS_QUERY = """
    SELECT * FROM albums
    WHERE isFavorite = 1 AND serverId = :serverId
        AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_ALBUM_IDS))
    ORDER BY rowid
"""

/**
 * Separates a stored album's genres. A genre filter wraps both the genres and the genre in it, so only whole genres
 * match: "Rock" doesn't match "Punk Rock".
 */
private const val SEP = Converters.SEPARATOR

@Dao
interface AlbumDao {

    /** Android Auto's Albums list; the common online path follows the sort-name index. */
    @Transaction
    suspend fun getAlbumsByServerSlice(
        serverId: String,
        downloadedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<AlbumEntity> = if (downloadedOnly) {
        getDownloadedAlbumsByServerSlice(serverId, limit, offset)
    } else {
        getAllAlbumsByServerSlice(serverId, limit, offset)
    }

    @Query(
        """
        SELECT * FROM albums
        WHERE serverId = :serverId
        ORDER BY sortName ASC, id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getAllAlbumsByServerSlice(
        serverId: String,
        limit: Int,
        offset: Int,
    ): List<AlbumEntity>

    @Query(
        """
        SELECT * FROM albums
        WHERE serverId = :serverId AND id IN ($DOWNLOADED_ALBUM_IDS)
        ORDER BY sortName ASC, id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getDownloadedAlbumsByServerSlice(serverId: String, limit: Int, offset: Int): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE id = :id")
    suspend fun getAlbumById(id: String): AlbumEntity?

    @Query("SELECT * FROM albums WHERE id = :id")
    fun observeAlbumById(id: String): Flow<AlbumEntity?>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC")
    fun getAlbumsByArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE artistName = :artistName OR artistName = :altName ORDER BY year DESC")
    fun getAlbumsByArtistName(artistName: String, altName: String = artistName): Flow<List<AlbumEntity>>

    @Query("SELECT COUNT(*) FROM albums WHERE artistName = :artistName OR artistName = :altName")
    suspend fun countAlbumsByArtistName(artistName: String, altName: String = artistName): Int

    @Query(FAVORITE_ALBUMS_QUERY)
    fun getFavoriteAlbumsPaged(serverId: String, downloadedOnly: Boolean): PagingSource<Int, AlbumEntity>

    /** [limit] albums of [getFavoriteAlbumsPaged] from position [offset]. */
    @Query("$FAVORITE_ALBUMS_QUERY LIMIT :limit OFFSET :offset")
    suspend fun getFavoriteAlbumsSlice(
        serverId: String,
        downloadedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE serverId = :serverId AND (name LIKE '%' || :query || '%' OR artistName LIKE '%' || :query || '%') ORDER BY sortName ASC LIMIT :limit")
    suspend fun search(serverId: String, query: String, limit: Int = 20): List<AlbumEntity>

    /**
     * Saves albums from the server, keeping each row's [AlbumEntity.resolvedArtistId]: it's derived locally from the artist
     * aliases, so the server's copy never has it. Rows that had none get it from their artist's alias.
     */
    @Transaction
    suspend fun upsertAlbums(albums: List<AlbumEntity>) {
        val ids = albums.map { it.id }
        val kept = ids.chunked(BIND_LIMIT)
            .flatMap { getAlbumResolvedArtistIds(it) }
            .associate { it.id to it.resolvedArtistId }
        upsertAlbumRows(albums.map { it.copy(resolvedArtistId = it.resolvedArtistId ?: kept[it.id]) })
        ids.chunked(BIND_LIMIT).forEach { resolveMissingAlbumArtistAliases(it) }
    }

    @Upsert
    suspend fun upsertAlbumRows(albums: List<AlbumEntity>)

    @Query("SELECT id, resolvedArtistId FROM albums WHERE id IN (:ids)")
    suspend fun getAlbumResolvedArtistIds(ids: List<String>): List<ResolvedArtistRow>

    @Query("""
        UPDATE albums SET resolvedArtistId = (
            SELECT aa.canonicalArtistId FROM artist_aliases aa
            WHERE aa.serverId = albums.serverId AND aa.rawArtistId = albums.artistId
        )
        WHERE id IN (:ids) AND resolvedArtistId IS NULL AND artistId IS NOT NULL
    """)
    suspend fun resolveMissingAlbumArtistAliases(ids: List<String>)

    @Query("""
        UPDATE albums SET artistId = (
            SELECT ar.id FROM artists ar
            WHERE ar.name = albums.artistName AND ar.serverId = albums.serverId
            LIMIT 1
        )
        WHERE serverId = :serverId
        AND artistName IS NOT NULL
        AND artistId != (
            SELECT ar2.id FROM artists ar2
            WHERE ar2.name = albums.artistName AND ar2.serverId = albums.serverId
            LIMIT 1
        )
    """)
    suspend fun resolveArtistIds(serverId: String)

    @Query("""
        UPDATE albums SET resolvedArtistId = (
            SELECT aa.canonicalArtistId FROM artist_aliases aa
            WHERE aa.serverId = albums.serverId AND aa.rawArtistId = albums.artistId
        )
        WHERE serverId = :serverId AND artistId IS NOT NULL
    """)
    suspend fun resolveArtistAliases(serverId: String)

    @Query("SELECT * FROM albums WHERE resolvedArtistId = :artistId ORDER BY year DESC")
    fun getAlbumsByResolvedArtist(artistId: String): Flow<List<AlbumEntity>>

    @Query("SELECT * FROM albums WHERE resolvedArtistId = :artistId ORDER BY year DESC")
    suspend fun getAllAlbumsByResolvedArtist(artistId: String): List<AlbumEntity>

    @Query("SELECT COUNT(*) FROM albums WHERE resolvedArtistId = :artistId")
    suspend fun countAlbumsByResolvedArtist(artistId: String): Int

    @Query("UPDATE albums SET isFavorite = :isFavorite WHERE id = :albumId")
    suspend fun setFavorite(albumId: String, isFavorite: Boolean)

    @Query("""
        SELECT a.* FROM albums a 
        INNER JOIN (
            SELECT albumId, MAX(lastPlayedAt) as maxPlayed 
            FROM tracks 
            WHERE serverId = :serverId AND lastPlayedAt > 0 AND albumId IS NOT NULL
            GROUP BY albumId
        ) t ON a.id = t.albumId 
        ORDER BY t.maxPlayed DESC 
        LIMIT :limit
    """)
    fun getRecentlyPlayedAlbums(serverId: String, limit: Int = 20): Flow<List<AlbumEntity>>

    @Query("""
        SELECT a.* FROM albums a 
        INNER JOIN (
            SELECT albumId, SUM(playCount) as totalPlays 
            FROM tracks 
            WHERE serverId = :serverId AND playCount > 0 AND albumId IS NOT NULL
            GROUP BY albumId
        ) t ON a.id = t.albumId 
        ORDER BY t.totalPlays DESC 
        LIMIT :limit
    """)
    fun getMostPlayedAlbums(serverId: String, limit: Int = 20): Flow<List<AlbumEntity>>

    @Query("SELECT DISTINCT genres FROM albums WHERE serverId = :serverId AND genres != ''")
    suspend fun getRawGenreStrings(serverId: String): List<String>

    /** The distinct stored genre lists of the albums, as [Converters] joins them; one row per combination. */
    @Query(
        """
        SELECT DISTINCT genres FROM albums
        WHERE serverId = :serverId AND genres != ''
            AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_ALBUM_IDS))
        """,
    )
    fun observeRawGenreStrings(serverId: String, downloadedOnly: Boolean): Flow<List<String>>

    /**
     * How many albums have each stored genre list, the lists in the order their first album appears by sort name.
     * One row per combination, however many albums there are.
     */
    @Query(
        """
        SELECT genres, COUNT(*) AS albumCount FROM albums
        WHERE serverId = :serverId AND genres != ''
        GROUP BY genres
        ORDER BY MIN(sortName) ASC
        """,
    )
    fun observeGenreAlbumCounts(serverId: String): Flow<List<GenreAlbumCount>>

    /** The albums tagged [genre], whole genres only as in the Library tab: "Rap" isn't "Pop Rap". */
    @Query(
        """
        SELECT * FROM albums
        WHERE serverId = :serverId
            AND instr('$SEP' || genres || '$SEP', '$SEP' || :genre || '$SEP') > 0
            AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_ALBUM_IDS))
        ORDER BY sortName ASC, id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getAlbumsByGenreSlice(
        genre: String,
        serverId: String,
        downloadedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY dateAdded DESC, sortName ASC LIMIT :limit")
    fun observeRecentlyAddedAlbums(serverId: String, limit: Int): Flow<List<AlbumEntity>>

    /** [limit] albums picked uniformly at random, for the home screen's Quick Picks row. */
    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY RANDOM() LIMIT :limit")
    fun observeRandomAlbums(serverId: String, limit: Int): Flow<List<AlbumEntity>>

    /**
     * [limit] albums picked uniformly at random among the favorites and the [mostPlayedCount] most played, for
     * Android Auto's Quick Picks row.
     */
    @Query(
        """
        SELECT * FROM albums
        WHERE serverId = :serverId
            AND (isFavorite = 1 OR id IN (
                SELECT a.id FROM albums a
                INNER JOIN (
                    SELECT albumId, SUM(playCount) AS totalPlays
                    FROM tracks
                    WHERE serverId = :serverId AND playCount > 0 AND albumId IS NOT NULL
                    GROUP BY albumId
                ) t ON a.id = t.albumId
                ORDER BY t.totalPlays DESC
                LIMIT :mostPlayedCount
            ))
            AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_ALBUM_IDS))
        ORDER BY RANDOM()
        LIMIT :limit
        """,
    )
    suspend fun getRandomFavoriteOrMostPlayedAlbums(
        serverId: String,
        mostPlayedCount: Int,
        downloadedOnly: Boolean,
        limit: Int,
    ): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE artistId = :artistId ORDER BY year DESC")
    suspend fun getAllAlbumsByArtist(artistId: String): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE artistName = :artistName OR artistName = :altName ORDER BY year DESC")
    suspend fun getAllAlbumsByArtistName(artistName: String, altName: String = artistName): List<AlbumEntity>

    @Query("SELECT id FROM albums WHERE isFavorite = 1 AND serverId = :serverId")
    suspend fun getFavoriteAlbumIds(serverId: String): List<String>

    @Query("UPDATE albums SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavoriteByIds(ids: List<String>, isFavorite: Boolean)

    @Query("DELETE FROM albums WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("SELECT id, imageTag FROM albums WHERE serverId = :serverId AND imageTag IS NOT NULL")
    suspend fun getImageTags(serverId: String): List<ImageTagRow>

    @Query("SELECT imageTag FROM albums WHERE id = :id")
    suspend fun getImageTag(id: String): String?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAlbumArtists(refs: List<AlbumArtistCrossRef>)

    @Query("DELETE FROM album_artists WHERE albumId = :albumId")
    suspend fun clearAlbumArtists(albumId: String)

    @Query("DELETE FROM album_artists WHERE albumId IN (:albumIds)")
    suspend fun clearAlbumArtistsByIds(albumIds: List<String>)

    /** Makes [refs] the artist links of [albumIds]; other albums keep theirs. */
    @Transaction
    suspend fun replaceAlbumArtists(albumIds: List<String>, refs: List<AlbumArtistCrossRef>) {
        albumIds.chunked(BIND_LIMIT).forEach { clearAlbumArtistsByIds(it) }
        insertAlbumArtists(refs)
    }

    @Query("""
        SELECT a.* FROM artists a
        INNER JOIN album_artists aa ON a.id = aa.artistId
        WHERE aa.albumId = :albumId
        ORDER BY aa.displayOrder ASC
    """)
    suspend fun getArtistsForAlbum(albumId: String): List<ArtistEntity>

    @Query("""
        SELECT a.* FROM artists a
        INNER JOIN album_artists aa ON a.id = aa.artistId
        WHERE aa.albumId = :albumId
        ORDER BY aa.displayOrder ASC
    """)
    fun observeArtistsForAlbum(albumId: String): Flow<List<ArtistEntity>>

    @Query("SELECT artistName FROM album_artists WHERE albumId = :albumId ORDER BY displayOrder ASC")
    suspend fun getArtistNamesForAlbum(albumId: String): List<String>

    @Query("SELECT COUNT(*) FROM album_artists WHERE artistId = :artistId")
    suspend fun countAlbumsByArtistCrossRef(artistId: String): Int

    @Query("""
        SELECT a.* FROM albums a 
        INNER JOIN (
            SELECT albumId, MAX(lastPlayedAt) as maxPlayed 
            FROM tracks 
            WHERE serverId = :serverId AND lastPlayedAt > 0 AND albumId IS NOT NULL
            GROUP BY albumId
        ) t ON a.id = t.albumId 
        ORDER BY t.maxPlayed DESC 
        LIMIT :limit
    """)
    suspend fun getRecentlyPlayedAlbumsSync(serverId: String, limit: Int = 12): List<AlbumEntity>

    @Query("SELECT * FROM albums WHERE serverId = :serverId ORDER BY dateAdded DESC LIMIT :limit")
    suspend fun getRecentlyAddedAlbums(serverId: String, limit: Int = 20): List<AlbumEntity>

    @Query("""
        SELECT a.* FROM albums a 
        INNER JOIN (
            SELECT albumId, SUM(playCount) as totalPlays 
            FROM tracks 
            WHERE serverId = :serverId AND playCount > 0 AND albumId IS NOT NULL
            GROUP BY albumId
        ) t ON a.id = t.albumId 
        ORDER BY t.totalPlays DESC 
        LIMIT :limit
    """)
    suspend fun getMostPlayedAlbumsSync(serverId: String, limit: Int = 20): List<AlbumEntity>
}

/** How many albums have the genre list [genres], as [Converters] stores it. */
data class GenreAlbumCount(
    val genres: String,
    val albumCount: Int,
)
