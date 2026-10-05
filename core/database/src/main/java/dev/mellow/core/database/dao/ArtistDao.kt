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

    /** Android Auto's Artists list; EXISTS avoids joining every alias and grouping the result. */
    @Transaction
    suspend fun getCanonicalArtistsSlice(
        serverId: String,
        downloadedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<ArtistEntity> = if (downloadedOnly) {
        getDownloadedCanonicalArtistsSlice(serverId, limit, offset)
    } else {
        getAllCanonicalArtistsSlice(serverId, limit, offset)
    }

    @Query(
        """
        SELECT a.* FROM artists a
        WHERE a.serverId = :serverId AND EXISTS (
            SELECT 1 FROM artist_aliases aa
            WHERE aa.serverId = a.serverId AND aa.canonicalArtistId = a.id
        )
        ORDER BY a.sortName ASC, a.id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getAllCanonicalArtistsSlice(
        serverId: String,
        limit: Int,
        offset: Int,
    ): List<ArtistEntity>

    @Query(
        """
        SELECT a.* FROM artists a
        WHERE a.serverId = :serverId AND a.id IN ($DOWNLOADED_ARTIST_IDS) AND EXISTS (
            SELECT 1 FROM artist_aliases aa
            WHERE aa.serverId = a.serverId AND aa.canonicalArtistId = a.id
        )
        ORDER BY a.sortName ASC, a.id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getDownloadedCanonicalArtistsSlice(
        serverId: String,
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
