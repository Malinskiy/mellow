package dev.mellow.core.database.perf

import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteStatement
import dev.mellow.core.database.entity.DownloadEntity
import java.util.Random

/**
 * A made-up library with the shape of a real one, written straight into a database with raw statements: entities
 * are far too slow at a million tracks. The same seed always gives the same library, so query timings and plans can
 * be compared between runs.
 *
 * Shape: artists with 1 to 8 albums, albums with 8 to 20 tracks on one or two discs, about 2% favorites, a fifth of
 * the tracks played, 1 in 500 downloaded, and some playlists. A second server's tracks are mixed in (about 1 in 50
 * rows), as when the app was used with another server before.
 */
class FakeLibrary(private val seed: Long = 42) {

    /** What [fill] wrote, to pick realistic query arguments from. */
    data class Contents(
        val tracks: Int,
        val albums: Int,
        val artists: Int,
        val downloads: Int,
        /** An album, artist and playlist in the middle of the library, and the playlist's track count. */
        val sampleAlbumId: String,
        val sampleArtistId: String,
        val samplePlaylistId: String,
    )

    /** Fills [db] with a library of [trackCount] tracks on [SERVER]. Expects the tables to exist and be empty. */
    fun fill(db: SupportSQLiteDatabase, trackCount: Int): Contents {
        val random = Random(seed)
        val words = WORDS
        fun id() = "%016x%016x".format(random.nextLong(), random.nextLong())
        fun title(min: Int, max: Int) =
            (1..min + random.nextInt(max - min + 1)).joinToString(" ") { words[random.nextInt(words.size)] }
                .replaceFirstChar(Char::uppercase)

        val trackSql = db.compileStatement(
            "INSERT INTO tracks (id, serverId, name, sortName, albumId, albumName, artistId, artistName, " +
                "trackNumber, discNumber, durationMs, genres, imageTag, isFavorite, playCount, lastPlayedAt, " +
                "normalizationGain, container, codec, bitrate, sampleRate, channels, resolvedArtistId, dateAdded, " +
                "lastSynced) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
        val albumSql = db.compileStatement(
            "INSERT INTO albums (id, serverId, name, sortName, artistId, artistName, year, trackCount, genres, " +
                "imageTag, isFavorite, resolvedArtistId, dateAdded, lastSynced) " +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        )
        val artistSql = db.compileStatement(
            "INSERT INTO artists (id, serverId, name, sortName, albumCount, imageTag, isFavorite, overview, genres, " +
                "cleanName, musicBrainzId, lastSynced) VALUES (?, ?, ?, ?, ?, ?, ?, NULL, ?, ?, NULL, 0)",
        )
        val albumArtistSql = db.compileStatement(
            "INSERT INTO album_artists (albumId, artistId, artistName, displayOrder) VALUES (?, ?, ?, 0)",
        )
        val trackArtistSql = db.compileStatement(
            "INSERT INTO track_artists (trackId, artistId, artistName, displayOrder) VALUES (?, ?, ?, 0)",
        )
        val aliasSql = db.compileStatement(
            "INSERT INTO artist_aliases (serverId, rawArtistId, canonicalArtistId, lastSynced) VALUES (?, ?, ?, 0)",
        )
        val downloadSql = db.compileStatement(
            "INSERT INTO downloads (trackId, albumId, serverId, status, progress, bytesDownloaded, totalBytes, " +
                "quality, filePath, requestedAt, completedAt, errorMessage, lastSynced) " +
                "VALUES (?, ?, ?, ${DownloadEntity.STATUS_COMPLETED}, 1, 1, 1, 'original', NULL, 0, 0, NULL, 0)",
        )

        val playlistTrackIds = mutableListOf<String>()
        var tracks = 0
        var albums = 0
        var artists = 0
        var downloads = 0
        var sampleAlbumId = ""
        var sampleArtistId = ""

        // Random IDs scatter the inserts across the B-trees: a large page cache keeps them in memory (41 s → 29 s for
        // a million tracks). The app's own cache size comes back afterwards, so queries are timed as the app runs them.
        val cacheSize = db.query("PRAGMA cache_size").use { it.moveToFirst(); it.getLong(0) }
        db.execSQL("PRAGMA cache_size = -262144")
        db.beginTransaction()
        try {
            db.execSQL(
                "INSERT INTO servers (id, name, url, userId, accessToken, isActive, lastConnected) VALUES " +
                    "('$SERVER', 'Home', 'https://jellyfin.example', 'user', 'token', 1, 0), " +
                    "('$OTHER_SERVER', 'Old', 'https://old.example', 'user', 'token', 0, 0)",
            )
            while (tracks < trackCount) {
                val artistId = id()
                val artistName = title(1, 3)
                val albumCount = 1 + random.nextInt(8)
                artistSql.run(
                    artistId, SERVER, artistName, artistName.lowercase(), albumCount, imageTagOrNull(random, id()),
                    flag(random, 0.03), "Rock", artistName.lowercase(),
                )
                aliasSql.run(SERVER, artistId, artistId)
                artists++
                if (sampleArtistId.isEmpty() && tracks >= trackCount / 2) sampleArtistId = artistId

                repeat(albumCount) {
                    if (tracks >= trackCount) return@repeat
                    val albumId = id()
                    val albumName = title(1, 4)
                    val trackTotal = 8 + random.nextInt(13)
                    val discs = if (random.nextInt(10) == 0) 2 else 1
                    val added = YEAR_2015 + (random.nextDouble() * TEN_YEARS).toLong()
                    val genre = GENRES[random.nextInt(GENRES.size)]
                    albumSql.run(
                        albumId, SERVER, albumName, albumName.lowercase(), artistId, artistName,
                        1960 + random.nextInt(65), trackTotal, genre, imageTagOrNull(random, id()),
                        flag(random, 0.02), artistId, added, 0,
                    )
                    albumArtistSql.run(albumId, artistId, artistName)
                    albums++
                    if (sampleAlbumId.isEmpty() && tracks >= trackCount / 2) sampleAlbumId = albumId

                    for (n in 0 until trackTotal) {
                        if (tracks >= trackCount) break
                        // Another server's track now and then, so this server's rows aren't one contiguous block.
                        if (random.nextInt(50) == 0) {
                            val name = title(1, 5)
                            trackSql.runTrack(
                                id(), OTHER_SERVER, name, null, null, null, null, 1, 1, genre, null, 0, 0, 0,
                                null, added,
                            )
                        }
                        val trackId = id()
                        val name = title(1, 5)
                        val played = random.nextInt(5) == 0
                        trackSql.runTrack(
                            trackId, SERVER, name, albumId, albumName, artistId, artistName,
                            n % (trackTotal / discs + 1) + 1, n / (trackTotal / discs + 1) + 1, genre,
                            if (random.nextInt(2) == 0) id() else null,
                            flag(random, 0.02),
                            if (played) 1 + (100 / (1 + random.nextInt(100))) else 0,
                            if (played) added + random.nextInt(1_000_000_000) else 0,
                            artistId, added,
                        )
                        trackArtistSql.run(trackId, artistId, artistName)
                        if (random.nextInt(500) == 0) {
                            downloadSql.run(trackId, albumId, SERVER)
                            downloads++
                        }
                        if (random.nextInt(200) == 0) playlistTrackIds += trackId
                        tracks++
                    }
                }
            }

            val playlistId = id()
            db.execSQL(
                "INSERT INTO playlists (id, serverId, name, sortName, trackCount, durationMs, imageTag, isFavorite, " +
                    "isLocal, lastSynced) VALUES ('$playlistId', '$SERVER', 'Big playlist', 'big playlist', " +
                    "${playlistTrackIds.size}, 0, NULL, 1, 0, 0)",
            )
            val playlistTrackSql = db.compileStatement(
                "INSERT INTO playlist_tracks (playlistId, trackId, position, addedAt) VALUES (?, ?, ?, 0)",
            )
            playlistTrackIds.forEachIndexed { position, trackId -> playlistTrackSql.run(playlistId, trackId, position) }
            db.setTransactionSuccessful()
            return Contents(tracks, albums, artists, downloads, sampleAlbumId, sampleArtistId, playlistId)
        } finally {
            db.endTransaction()
            db.execSQL("PRAGMA cache_size = $cacheSize")
        }
    }

    private fun SupportSQLiteStatement.runTrack(
        id: String, serverId: String, name: String, albumId: String?, albumName: String?, artistId: String?,
        artistName: String?, trackNumber: Int, discNumber: Int, genre: String, imageTag: String?, isFavorite: Int,
        playCount: Int, lastPlayedAt: Long, resolvedArtistId: String?, dateAdded: Long,
    ) = run(
        id, serverId, name, name.lowercase(), albumId, albumName, artistId, artistName, trackNumber, discNumber,
        200_000, genre, imageTag, isFavorite, playCount, lastPlayedAt, null, "flac", "flac", 900_000, 44_100, 2,
        resolvedArtistId, dateAdded, 0,
    )

    private fun SupportSQLiteStatement.run(vararg args: Any?) {
        clearBindings()
        args.forEachIndexed { i, arg ->
            when (arg) {
                null -> bindNull(i + 1)
                is String -> bindString(i + 1, arg)
                is Int -> bindLong(i + 1, arg.toLong())
                is Long -> bindLong(i + 1, arg)
                is Double -> bindDouble(i + 1, arg)
                else -> error("Can't bind $arg")
            }
        }
        executeInsert()
    }

    private fun flag(random: Random, probability: Double) = if (random.nextDouble() < probability) 1 else 0

    private fun imageTagOrNull(random: Random, tag: String) = if (random.nextInt(10) == 0) null else tag

    companion object {
        const val SERVER = "5f0c1a2b3c4d5e6f7a8b9c0d1e2f3a4b"
        const val OTHER_SERVER = "0a1b2c3d4e5f60718293a4b5c6d7e8f9"

        private const val YEAR_2015 = 1_420_070_400_000L
        private const val TEN_YEARS = 315_360_000_000.0
        private val GENRES = listOf("Rock", "Pop", "Jazz", "Classical", "Electronic", "Hip-Hop", "Folk", "Metal")
        private val WORDS = (
            "love night blue heart fire road river light dream city song rain summer stone gold moon wild time " +
                "home world dance shadow ocean silver morning glass paper electric garden winter star tide machine"
            ).split(" ")
    }
}
