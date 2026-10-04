package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.mellow.core.database.entity.ArtistEntity
import kotlinx.coroutines.flow.Flow

/** The favorite artists, in the order they were first saved. */
private const val FAVORITE_ARTISTS_QUERY = """
    SELECT * FROM artists
    WHERE isFavorite = 1 AND serverId = :serverId
        AND (:downloadedOnly = 0 OR $ARTIST_IS_DOWNLOADED)
    ORDER BY rowid
"""

@Dao
interface ArtistDao {

    /**
     * The library's artists tab: one row per canonical artist, in [LibraryOrder] `:sort` order (by name, or else by
     * sort name), each with the number of the library's albums credited to it (only downloaded ones if
     * [downloadedOnly]). The ID makes the order total, so pages never overlap or skip an artist.
     */
    @Query(
        """
        SELECT a.*, COALESCE(c.albumCount, 0) AS localAlbumCount
        FROM artists a
        INNER JOIN artist_aliases aa ON a.id = aa.canonicalArtistId AND a.serverId = aa.serverId
        LEFT JOIN (
            SELECT COALESCE(resolvedArtistId, artistId) AS artistKey, COUNT(*) AS albumCount
            FROM albums
            WHERE serverId = :serverId
                AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_ALBUM_IDS))
            GROUP BY artistKey
        ) c ON c.artistKey = a.id
        WHERE a.serverId = :serverId
            AND (:downloadedOnly = 0 OR a.id IN ($DOWNLOADED_ARTIST_IDS))
        GROUP BY aa.canonicalArtistId
        ORDER BY
            CASE WHEN :sort = ${LibraryOrder.NAME_ASC} THEN a.name END COLLATE NOCASE ASC,
            CASE WHEN :sort = ${LibraryOrder.NAME_DESC} THEN a.name END COLLATE NOCASE DESC,
            a.sortName ASC,
            a.id ASC
        """,
    )
    fun getLibraryArtists(
        serverId: String,
        sort: Int,
        downloadedOnly: Boolean,
    ): PagingSource<Int, ArtistWithAlbumCount>

    @Query(
        """
        SELECT a.* FROM artists a
        INNER JOIN artist_aliases aa ON a.id = aa.canonicalArtistId AND a.serverId = aa.serverId
        WHERE a.serverId = :serverId
            AND (:downloadedOnly = 0 OR a.id IN ($DOWNLOADED_ARTIST_IDS))
        GROUP BY aa.canonicalArtistId
        ORDER BY a.sortName ASC, a.id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getCanonicalArtistsSlice(
        serverId: String,
        downloadedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<ArtistEntity>

    @Query("SELECT * FROM artists WHERE id = :id")
    suspend fun getArtistById(id: String): ArtistEntity?

    @Query("SELECT * FROM artists WHERE id = :id")
    fun observeArtistById(id: String): Flow<ArtistEntity?>

    @Query(FAVORITE_ARTISTS_QUERY)
    fun getFavoriteArtistsPaged(serverId: String, downloadedOnly: Boolean): PagingSource<Int, ArtistEntity>

    /** [limit] artists of [getFavoriteArtistsPaged] from position [offset]. */
    @Query("$FAVORITE_ARTISTS_QUERY LIMIT :limit OFFSET :offset")
    suspend fun getFavoriteArtistsSlice(
        serverId: String,
        downloadedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<ArtistEntity>

    /** Artists named like [query]; only those with a downloaded track if [downloadedOnly]. */
    @Query(
        """
        SELECT * FROM artists
        WHERE serverId = :serverId AND name LIKE '%' || :query || '%'
            AND (:downloadedOnly = 0 OR $ARTIST_IS_DOWNLOADED)
        ORDER BY sortName ASC
        LIMIT :limit
        """,
    )
    suspend fun search(serverId: String, query: String, limit: Int = 10, downloadedOnly: Boolean = false): List<ArtistEntity>

    /**
     * Saves artists from the server without clearing a stored favorite: Jellyfin's `/Artists` reports
     * `IsFavorite = false` even for favorite artists, so favorites are set and removed from the server's favorites
     * list instead (see [setFavoriteByIds]).
     */
    @Transaction
    suspend fun upsertArtists(artists: List<ArtistEntity>) {
        val favorites = artists.map { it.id }.chunked(BIND_LIMIT).flatMap { getFavoriteIdsAmong(it) }.toSet()
        upsertArtistRows(artists.map { it.copy(isFavorite = it.isFavorite || it.id in favorites) })
    }

    @Upsert
    suspend fun upsertArtistRows(artists: List<ArtistEntity>)

    @Query("SELECT id FROM artists WHERE isFavorite = 1 AND id IN (:ids)")
    suspend fun getFavoriteIdsAmong(ids: List<String>): List<String>

    @Query("UPDATE artists SET cleanName = :cleanName WHERE id = :id")
    suspend fun updateCleanName(id: String, cleanName: String)

    @Query("UPDATE artists SET isFavorite = :isFavorite WHERE id = :artistId")
    suspend fun setFavorite(artistId: String, isFavorite: Boolean)

    @Query("SELECT * FROM artists WHERE serverId = :serverId ORDER BY sortName ASC")
    suspend fun getAllArtistsByServer(serverId: String): List<ArtistEntity>

    @Query("SELECT id FROM artists WHERE isFavorite = 1 AND serverId = :serverId")
    suspend fun getFavoriteArtistIds(serverId: String): List<String>

    @Query("UPDATE artists SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavoriteByIds(ids: List<String>, isFavorite: Boolean)

    @Query("DELETE FROM artists WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("SELECT id, imageTag FROM artists WHERE serverId = :serverId AND imageTag IS NOT NULL")
    suspend fun getImageTags(serverId: String): List<ImageTagRow>

    @Query("SELECT imageTag FROM artists WHERE id = :id")
    suspend fun getImageTag(id: String): String?
}

/** An artist with the number of the library's albums credited to it, counted on the device. */
data class ArtistWithAlbumCount(
    @Embedded val artist: ArtistEntity,
    val localAlbumCount: Int,
)
