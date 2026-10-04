package dev.mellow.core.database.dao

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.RawQuery
import androidx.room.Upsert
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.sqlite.db.SupportSQLiteQuery
import dev.mellow.core.database.entity.ArtistEntity
import dev.mellow.core.database.entity.DownloadEntity
import dev.mellow.core.database.entity.TrackArtistCrossRef
import dev.mellow.core.database.entity.TrackEntity
import kotlinx.coroutines.flow.Flow
import kotlin.random.Random

/** The favorite tracks, in the order they were first saved. */
private const val FAVORITE_TRACKS_QUERY = """
    SELECT * FROM tracks
    WHERE isFavorite = 1 AND serverId = :serverId
        AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_TRACK_IDS))
    ORDER BY rowid
"""

@Dao
interface TrackDao {

    /** Pages of a [LibraryTracksQuery]: use [getLibraryTracks]. */
    @RawQuery(observedEntities = [TrackEntity::class, DownloadEntity::class])
    fun getLibraryTracksPaged(query: SupportSQLiteQuery): PagingSource<Int, TrackEntity>

    /** Tracks of a query over the tracks table that selects whole rows. */
    @RawQuery(observedEntities = [TrackEntity::class, DownloadEntity::class])
    suspend fun getTracksRaw(query: SupportSQLiteQuery): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC, id ASC")
    fun getTracksByAlbum(albumId: String): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE id = :id")
    suspend fun getTrackById(id: String): TrackEntity?

    /** Unordered. Use [getTracksById] for lists that may exceed SQLite's bound-parameter limit. */
    @Query("SELECT * FROM tracks WHERE id IN (:ids)")
    suspend fun getTracksByIds(ids: List<String>): List<TrackEntity>

    @Query("SELECT isFavorite FROM tracks WHERE id = :id")
    fun observeIsFavorite(id: String): Flow<Boolean?>

    @Query(FAVORITE_TRACKS_QUERY)
    fun getFavoriteTracksPaged(serverId: String, downloadedOnly: Boolean): PagingSource<Int, TrackEntity>

    /** [limit] tracks of [getFavoriteTracksPaged] from position [offset]. */
    @Query("$FAVORITE_TRACKS_QUERY LIMIT :limit OFFSET :offset")
    suspend fun getFavoriteTracksSlice(
        serverId: String,
        downloadedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<TrackEntity>

    @Query(
        """
        SELECT COUNT(*) FROM tracks
        WHERE isFavorite = 1 AND serverId = :serverId
            AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_TRACK_IDS))
        """,
    )
    suspend fun countFavoriteTracks(serverId: String, downloadedOnly: Boolean): Int

    /** How many favorite tracks (only downloaded ones if [downloadedOnly]) come before [trackId] in list order. */
    @Query(
        """
        SELECT COUNT(*) FROM tracks
        WHERE isFavorite = 1 AND serverId = :serverId
            AND rowid < (SELECT rowid FROM tracks WHERE id = :trackId)
            AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_TRACK_IDS))
        """,
    )
    suspend fun countFavoriteTracksBefore(serverId: String, trackId: String, downloadedOnly: Boolean): Int

    /** [limit] favorite tracks picked uniformly at random, for the home screen's Favorite Tracks row. */
    @Query("SELECT * FROM tracks WHERE isFavorite = 1 AND serverId = :serverId ORDER BY RANDOM() LIMIT :limit")
    fun observeRandomFavoriteTracks(serverId: String, limit: Int): Flow<List<TrackEntity>>

    /**
     * [limit] favorite tracks picked uniformly at random from all of them, in random order: Android Auto's Favorite
     * Tracks row, and the favorites' shuffle, which never needs the whole list.
     */
    @Query(
        """
        SELECT * FROM tracks
        WHERE isFavorite = 1 AND serverId = :serverId
            AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_TRACK_IDS))
        ORDER BY RANDOM()
        LIMIT :limit
        """,
    )
    suspend fun getRandomFavoriteTracks(serverId: String, downloadedOnly: Boolean, limit: Int): List<TrackEntity>

    /**
     * The IDs of [limit] tracks picked uniformly at random from the server's library (its downloaded tracks only, if
     * [downloadedOnly]), in random order. Only IDs: the pick sorts every track, which is cheaper on narrow rows.
     * [pickRandomTracks] loads the tracks.
     */
    @Query(
        """
        SELECT id FROM tracks
        WHERE serverId = :serverId
            AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_TRACK_IDS))
        ORDER BY RANDOM()
        LIMIT :limit
        """,
    )
    suspend fun getRandomTrackIds(serverId: String, downloadedOnly: Boolean, limit: Int): List<String>

    /**
     * The lowest and highest internal row number of the tracks table (all servers); nulls when it's empty. Two
     * subqueries: SQLite finds a lone MIN or MAX of the row number at once, but scans the table for both together.
     */
    @Query("SELECT (SELECT MIN(rowid) FROM tracks) AS low, (SELECT MAX(rowid) FROM tracks) AS high")
    suspend fun getRowidRange(): RowidRange

    /**
     * The server's tracks among the rows [rowids], in row order. `+serverId` keeps SQLite looking the rows up by
     * number: with hundreds of them it would otherwise read every track of the server from an index.
     */
    @Query("SELECT * FROM tracks WHERE rowid IN (:rowids) AND +serverId = :serverId")
    suspend fun getTracksByRowids(serverId: String, rowids: List<Long>): List<TrackEntity>

    /**
     * The IDs of [limit] of the server's downloaded tracks, picked uniformly at random, in random order. Starts from
     * the downloads (CROSS JOIN keeps that order), so it sorts only the downloaded tracks.
     */
    @Query(
        """
        SELECT t.id FROM downloads d CROSS JOIN tracks t ON t.id = d.trackId
        WHERE d.status = ${DownloadEntity.STATUS_COMPLETED} AND d.serverId = :serverId AND t.serverId = :serverId
        ORDER BY RANDOM()
        LIMIT :limit
        """,
    )
    suspend fun getRandomDownloadedTrackIds(serverId: String, limit: Int): List<String>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId ORDER BY playCount DESC LIMIT :limit")
    fun getMostPlayed(serverId: String, limit: Int = 50): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE serverId = :serverId AND (name LIKE '%' || :query || '%' OR artistName LIKE '%' || :query || '%') ORDER BY sortName ASC LIMIT :limit")
    suspend fun search(serverId: String, query: String, limit: Int = 50): List<TrackEntity>

    /**
     * Saves tracks from the server, keeping each row's [TrackEntity.resolvedArtistId]: it's derived locally from the artist
     * aliases, so the server's copy never has it. Rows that had none get it from their artist's alias.
     */
    @Transaction
    suspend fun upsertTracks(tracks: List<TrackEntity>) {
        val ids = tracks.map { it.id }
        val kept = ids.chunked(BIND_LIMIT)
            .flatMap { getTrackResolvedArtistIds(it) }
            .associate { it.id to it.resolvedArtistId }
        upsertTrackRows(tracks.map { it.copy(resolvedArtistId = it.resolvedArtistId ?: kept[it.id]) })
        ids.chunked(BIND_LIMIT).forEach { resolveMissingTrackArtistAliases(it) }
    }

    @Upsert
    suspend fun upsertTrackRows(tracks: List<TrackEntity>)

    @Query("SELECT id, resolvedArtistId FROM tracks WHERE id IN (:ids)")
    suspend fun getTrackResolvedArtistIds(ids: List<String>): List<ResolvedArtistRow>

    @Query("""
        UPDATE tracks SET resolvedArtistId = (
            SELECT aa.canonicalArtistId FROM artist_aliases aa
            WHERE aa.serverId = tracks.serverId AND aa.rawArtistId = tracks.artistId
        )
        WHERE id IN (:ids) AND resolvedArtistId IS NULL AND artistId IS NOT NULL
    """)
    suspend fun resolveMissingTrackArtistAliases(ids: List<String>)

    @Query("""
        UPDATE tracks SET artistId = (
            SELECT ar.id FROM artists ar
            WHERE ar.name = tracks.artistName AND ar.serverId = tracks.serverId
            LIMIT 1
        )
        WHERE serverId = :serverId
        AND artistName IS NOT NULL
        AND artistId != (
            SELECT ar2.id FROM artists ar2
            WHERE ar2.name = tracks.artistName AND ar2.serverId = tracks.serverId
            LIMIT 1
        )
    """)
    suspend fun resolveArtistIds(serverId: String)

    @Query("""
        UPDATE tracks SET resolvedArtistId = (
            SELECT aa.canonicalArtistId FROM artist_aliases aa
            WHERE aa.serverId = tracks.serverId AND aa.rawArtistId = tracks.artistId
        )
        WHERE serverId = :serverId AND artistId IS NOT NULL
    """)
    suspend fun resolveArtistAliases(serverId: String)

    @Query("SELECT * FROM tracks WHERE resolvedArtistId = :artistId ORDER BY playCount DESC LIMIT :limit")
    fun getTracksByResolvedArtist(artistId: String, limit: Int = 20): Flow<List<TrackEntity>>

    /** An artist's most played tracks, as the app's artist screen shows and plays them. */
    @Query("SELECT * FROM tracks WHERE resolvedArtistId = :artistId ORDER BY playCount DESC LIMIT :limit")
    suspend fun getTracksByResolvedArtistSync(artistId: String, limit: Int = 20): List<TrackEntity>

    @Query("SELECT COUNT(*) FROM tracks WHERE resolvedArtistId = :artistId")
    suspend fun countTracksByResolvedArtist(artistId: String): Int

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id = :trackId")
    suspend fun setFavorite(trackId: String, isFavorite: Boolean)

    @Query("UPDATE tracks SET playCount = playCount + 1 WHERE id = :trackId")
    suspend fun incrementPlayCount(trackId: String)

    @Query("UPDATE tracks SET playCount = playCount + 1, lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun recordPlayback(trackId: String, timestamp: Long)

    @Query("UPDATE tracks SET lastPlayedAt = :timestamp WHERE id = :trackId")
    suspend fun updateLastPlayedAt(trackId: String, timestamp: Long)

    @Query("SELECT * FROM tracks WHERE artistId = :artistId ORDER BY playCount DESC LIMIT :limit")
    fun getTracksByArtist(artistId: String, limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT * FROM tracks WHERE (artistName = :artistName OR artistName = :altName) ORDER BY playCount DESC LIMIT :limit")
    fun getTracksByArtistName(artistName: String, altName: String = artistName, limit: Int = 20): Flow<List<TrackEntity>>

    @Query("SELECT COUNT(*) FROM tracks WHERE artistName = :artistName OR artistName = :altName")
    suspend fun countTracksByArtistName(artistName: String, altName: String = artistName): Int

    @Query("SELECT * FROM tracks WHERE serverId = :serverId AND lastPlayedAt > 0 ORDER BY lastPlayedAt DESC LIMIT :limit")
    suspend fun getRecentlyPlayedTracks(serverId: String, limit: Int = 50): List<TrackEntity>

    @Query("SELECT * FROM tracks WHERE albumId = :albumId ORDER BY discNumber ASC, trackNumber ASC, id ASC")
    suspend fun getTracksByAlbumSync(albumId: String): List<TrackEntity>

    @Query("SELECT id FROM tracks WHERE isFavorite = 1 AND serverId = :serverId")
    suspend fun getFavoriteTrackIds(serverId: String): List<String>

    @Query("UPDATE tracks SET isFavorite = :isFavorite WHERE id IN (:ids)")
    suspend fun setFavoriteByIds(ids: List<String>, isFavorite: Boolean)

    @Query("DELETE FROM tracks WHERE serverId = :serverId")
    suspend fun deleteByServer(serverId: String)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query(
        """
        SELECT * FROM tracks
        WHERE serverId = :serverId
            AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_TRACK_IDS))
        ORDER BY sortName ASC, id ASC
        LIMIT :limit OFFSET :offset
        """,
    )
    suspend fun getTracksByServerPaged(
        serverId: String,
        downloadedOnly: Boolean,
        limit: Int,
        offset: Int,
    ): List<TrackEntity>

    /**
     * How many of the server's tracks (only downloaded ones if [downloadedOnly]) come before the track [id] named
     * [sortName] in [getTracksByServerPaged] order.
     */
    @Query(
        """
        SELECT COUNT(*) FROM tracks
        WHERE serverId = :serverId AND (sortName < :sortName OR (sortName = :sortName AND id < :id))
            AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_TRACK_IDS))
        """,
    )
    suspend fun countTracksBefore(serverId: String, sortName: String, id: String, downloadedOnly: Boolean): Int

    @Query(
        """
        SELECT COUNT(*) FROM tracks
        WHERE serverId = :serverId AND (:downloadedOnly = 0 OR id IN ($DOWNLOADED_TRACK_IDS))
        """,
    )
    suspend fun countTracks(serverId: String, downloadedOnly: Boolean): Int

    @RawQuery
    suspend fun getInstantMixRaw(query: SupportSQLiteQuery): List<TrackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTrackArtists(refs: List<TrackArtistCrossRef>)

    @Query("DELETE FROM track_artists WHERE trackId = :trackId")
    suspend fun clearTrackArtists(trackId: String)

    @Query("DELETE FROM track_artists WHERE trackId IN (:trackIds)")
    suspend fun clearTrackArtistsByIds(trackIds: List<String>)

    /** Makes [refs] the artist links of [trackIds]; other tracks keep theirs. */
    @Transaction
    suspend fun replaceTrackArtists(trackIds: List<String>, refs: List<TrackArtistCrossRef>) {
        trackIds.chunked(BIND_LIMIT).forEach { clearTrackArtistsByIds(it) }
        insertTrackArtists(refs)
    }

    @Query("""
        SELECT a.* FROM artists a
        INNER JOIN track_artists ta ON a.id = ta.artistId
        WHERE ta.trackId = :trackId
        ORDER BY ta.displayOrder ASC
    """)
    suspend fun getArtistsForTrack(trackId: String): List<ArtistEntity>

    @Query("""
        SELECT a.* FROM artists a
        INNER JOIN track_artists ta ON a.id = ta.artistId
        WHERE ta.trackId = :trackId
        ORDER BY ta.displayOrder ASC
    """)
    fun observeArtistsForTrack(trackId: String): Flow<List<ArtistEntity>>

    @Query("SELECT artistName FROM track_artists WHERE trackId = :trackId ORDER BY displayOrder ASC")
    suspend fun getArtistNamesForTrack(trackId: String): List<String>

    @Query("SELECT id, imageTag FROM tracks WHERE serverId = :serverId AND imageTag IS NOT NULL AND albumId IS NULL")
    suspend fun getOrphanTrackImageTags(serverId: String): List<ImageTagRow>

    @Query("SELECT imageTag FROM tracks WHERE id = :id")
    suspend fun getImageTag(id: String): String?
}

suspend fun TrackDao.getInstantMix(
    serverId: String,
    seedTrackId: String,
    artistName: String?,
    genres: List<String>,
    downloadedOnly: Boolean = false,
    limit: Int = 50,
): List<TrackEntity> {
    val sb = if (downloadedOnly) {
        StringBuilder("SELECT t.* FROM tracks t INNER JOIN downloads d ON t.id = d.trackId AND d.status = 2 WHERE t.serverId = ? AND t.id != ?")
    } else {
        StringBuilder("SELECT * FROM tracks WHERE serverId = ? AND id != ?")
    }
    val args = mutableListOf<Any>(serverId, seedTrackId)
    val col = if (downloadedOnly) "t." else ""

    val conditions = mutableListOf<String>()
    if (artistName != null) {
        conditions.add("${col}artistName = ?")
        args.add(artistName)
    }
    for (genre in genres) {
        conditions.add("${col}genres LIKE '%' || ? || '%'")
        args.add(genre)
    }
    if (conditions.isEmpty()) return emptyList()

    sb.append(" AND (")
    sb.append(conditions.joinToString(" OR "))
    sb.append(") ORDER BY RANDOM() LIMIT ?")
    args.add(limit)

    return getInstantMixRaw(SimpleSQLiteQuery(sb.toString(), args.toTypedArray()))
}

/** The Library's Tracks tab in [LibraryOrder] [sort] order (see [LibraryTracksQuery]). */
fun TrackDao.getLibraryTracks(serverId: String, sort: Int, downloadedOnly: Boolean): PagingSource<Int, TrackEntity> =
    getLibraryTracksPaged(LibraryTracksQuery.page(serverId, sort, downloadedOnly))

/** [limit] tracks of [getLibraryTracks] from position [offset]. */
suspend fun TrackDao.getLibraryTracksSlice(
    serverId: String,
    sort: Int,
    downloadedOnly: Boolean,
    limit: Int,
    offset: Int,
): List<TrackEntity> = getTracksRaw(LibraryTracksQuery.slice(serverId, sort, downloadedOnly, limit, offset))

/**
 * [limit] tracks picked uniformly at random from the server's library (its downloaded tracks only, if
 * [downloadedOnly]), in random order. A track removed while picking is left out.
 *
 * The whole library is sampled by internal row number ([sampleByRowid]), which reads only the rows it picks; sorting
 * every track by RANDOM() took a quarter of a second at a million tracks. Small libraries, and rows mostly of another
 * server, are sorted instead: that's cheap for them and always finds every track.
 */
suspend fun TrackDao.pickRandomTracks(
    serverId: String,
    downloadedOnly: Boolean,
    limit: Int,
    random: Random = Random.Default,
): List<TrackEntity> {
    if (limit <= 0) return emptyList()
    if (downloadedOnly) return loadInOrder(getRandomDownloadedTrackIds(serverId, limit))
    return sampleByRowid(serverId, limit, random) ?: loadInOrder(getRandomTrackIds(serverId, false, limit))
}

/**
 * [limit] of the server's tracks, drawn by internal row number: random row numbers in the table's range, keeping the
 * rows that are this server's tracks. Every track is equally likely, since every row number is, and only the drawn
 * rows are read. Draws in rounds until it has [limit]; `null` if the rows are too few or too sparse for that (row
 * numbers left by deleted tracks, another server's tracks), for the caller to sort instead.
 */
private suspend fun TrackDao.sampleByRowid(serverId: String, limit: Int, random: Random): List<TrackEntity>? {
    val range = getRowidRange()
    val low = range.low ?: return emptyList()
    val high = range.high ?: return emptyList()
    val span = high - low + 1
    if (span < limit.toLong() * ROWID_SAMPLE_MIN_SPAN) return null
    val picked = LinkedHashMap<String, TrackEntity>()
    val drawn = HashSet<Long>()
    var hitRate = 1.0
    repeat(ROWID_SAMPLE_ROUNDS) {
        val missing = limit - picked.size
        if (missing == 0) return picked.values.shuffled(random)
        // Never more row numbers than are left to draw, or the draw below would never finish.
        val wanted = minOf((missing / hitRate * 1.25 + 8).toLong(), ROWID_SAMPLE_BATCH.toLong(), span - drawn.size)
            .toInt()
        if (wanted <= 0) return null
        val candidates = ArrayList<Long>(wanted)
        while (candidates.size < wanted) {
            val rowid = random.nextLong(low, high + 1)
            if (drawn.add(rowid)) candidates += rowid
        }
        val found = getTracksByRowids(serverId, candidates)
        hitRate = (found.size.coerceAtLeast(1).toDouble() / candidates.size).coerceAtMost(1.0)
        // Found in row order: shuffle before taking some, so the cut doesn't favor low row numbers.
        found.shuffled(random).forEach { if (picked.size < limit) picked.putIfAbsent(it.id, it) }
    }
    return if (picked.size == limit) picked.values.shuffled(random) else null
}

/** The tracks [ids], in that order; IDs of tracks removed meanwhile are left out. */
private suspend fun TrackDao.loadInOrder(ids: List<String>): List<TrackEntity> {
    val tracks = getTracksById(ids)
    return ids.mapNotNull { tracks[it] }
}

/** The row-number range has to be this many times the tracks to pick, or sorting is as cheap. */
private const val ROWID_SAMPLE_MIN_SPAN = 20

/** Rounds of row numbers to draw before giving up on sampling. */
private const val ROWID_SAMPLE_ROUNDS = 6

/** Row numbers per round: SQLite allows 999 bound parameters on old Android versions. */
private const val ROWID_SAMPLE_BATCH = 900

/** The tracks table's internal row-number range. */
data class RowidRange(val low: Long?, val high: Long?)

/** Looks up tracks by ID in chunks that stay under SQLite's bound-parameter limit; missing IDs are absent. */
suspend fun TrackDao.getTracksById(ids: Collection<String>): Map<String, TrackEntity> =
    ids.distinct().chunked(500).flatMap { getTracksByIds(it) }.associateBy { it.id }
