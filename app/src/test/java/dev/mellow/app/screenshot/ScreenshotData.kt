package dev.mellow.app.screenshot

import dev.mellow.core.model.Album
import dev.mellow.core.model.Artist
import dev.mellow.core.model.Track
import dev.mellow.feature.home.HomeAlbumItem
import dev.mellow.feature.home.HomeTrackItem
import dev.mellow.feature.home.PlaylistDetailTrack
import dev.mellow.feature.home.PlaylistItem
import dev.mellow.feature.library.AlbumDetailTrack
import dev.mellow.feature.library.AlbumItem
import dev.mellow.feature.library.ArtistAlbum
import dev.mellow.feature.library.ArtistItem
import dev.mellow.feature.library.ArtistTrack
import dev.mellow.feature.library.LibraryPlaylistItem
import dev.mellow.feature.library.TrackItem
import dev.mellow.feature.player.LyricsLine
import dev.mellow.feature.player.QueueTrack
import java.time.Duration

object ScreenshotData {

    val homeAlbums = listOf(
        HomeAlbumItem("a1", "OK Computer", "Radiohead", "a1"),
        HomeAlbumItem("a2", "In Rainbows", "Radiohead", "a2"),
        HomeAlbumItem("a3", "Kid A", "Radiohead", "a3"),
        HomeAlbumItem("a4", "The Bends", "Radiohead", "a4"),
        HomeAlbumItem("a5", "Hail to the Thief", "Radiohead", "a5"),
        HomeAlbumItem("a6", "Amnesiac", "Radiohead", "a6"),
        HomeAlbumItem("a7", "A Moon Shaped Pool", "Radiohead", "a7"),
        HomeAlbumItem("a8", "Pablo Honey", "Radiohead", "a8"),
        HomeAlbumItem("a9", "Currents", "Tame Impala", "a9"),
        HomeAlbumItem("a10", "Lonerism", "Tame Impala", "a10"),
        HomeAlbumItem("a11", "Innerspeaker", "Tame Impala", "a11"),
        HomeAlbumItem("a12", "The Slow Rush", "Tame Impala", "a12"),
    )

    val homeTracks = listOf(
        HomeTrackItem("t1", "Everything In Its Right Place", "Radiohead", "Kid A", "4:11", "a3", "a3"),
        HomeTrackItem("t2", "Let Down", "Radiohead", "OK Computer", "4:59", "a1", "a1"),
        HomeTrackItem("t3", "Reckoner", "Radiohead", "In Rainbows", "4:50", "a2", "a2"),
        HomeTrackItem("t4", "The Less I Know the Better", "Tame Impala", "Currents", "3:36", "a9", "a9"),
        HomeTrackItem("t5", "Weird Fishes / Arpeggi", "Radiohead", "In Rainbows", "5:18", "a2", "a2"),
    )

    val genres = listOf("Alternative Rock", "Art Rock", "Electronic", "Indie Rock", "Post-Rock", "Psychedelic Rock", "Shoegaze")

    val albumItems = listOf(
        AlbumItem("a1", "OK Computer", "Radiohead", "a1"),
        AlbumItem("a2", "In Rainbows", "Radiohead", "a2"),
        AlbumItem("a3", "Kid A", "Radiohead", "a3"),
        AlbumItem("a4", "The Bends", "Radiohead", "a4"),
        AlbumItem("a5", "Hail to the Thief", "Radiohead", "a5"),
        AlbumItem("a6", "Amnesiac", "Radiohead", "a6"),
        AlbumItem("a7", "A Moon Shaped Pool", "Radiohead", "a7"),
        AlbumItem("a8", "Currents", "Tame Impala", "a8"),
        AlbumItem("a9", "Lonerism", "Tame Impala", "a9"),
        AlbumItem("a10", "Is This It", "The Strokes", "a10"),
        AlbumItem("a11", "Room on Fire", "The Strokes", "a11"),
        AlbumItem("a12", "Innerspeaker", "Tame Impala", "a12"),
    )

    val artistItems = listOf(
        ArtistItem("ar1", "Radiohead", 9, "ar1"),
        ArtistItem("ar2", "Tame Impala", 4, "ar2"),
        ArtistItem("ar3", "The Strokes", 6, "ar3"),
        ArtistItem("ar4", "Arcade Fire", 5, "ar4"),
        ArtistItem("ar5", "LCD Soundsystem", 4, "ar5"),
        ArtistItem("ar6", "Bon Iver", 4, "ar6"),
    )

    val trackItems = listOf(
        TrackItem("t1", "Paranoid Android", "Radiohead", "OK Computer", "6:27", "a1", "a1"),
        TrackItem("t2", "Karma Police", "Radiohead", "OK Computer", "4:22", "a1", "a1"),
        TrackItem("t3", "No Surprises", "Radiohead", "OK Computer", "3:48", "a1", "a1"),
        TrackItem("t4", "15 Step", "Radiohead", "In Rainbows", "3:58", "a2", "a2"),
        TrackItem("t5", "Bodysnatchers", "Radiohead", "In Rainbows", "4:02", "a2", "a2"),
        TrackItem("t6", "Nude", "Radiohead", "In Rainbows", "4:15", "a2", "a2"),
        TrackItem("t7", "Let It Happen", "Tame Impala", "Currents", "7:47", "a8", "a8"),
        TrackItem("t8", "New Person, Same Old Mistakes", "Tame Impala", "Currents", "6:03", "a8", "a8"),
    )

    val playlists = listOf(
        PlaylistItem("p1", "Late Night Vibes", 24, null),
        PlaylistItem("p2", "Morning Commute", 18, null),
        PlaylistItem("p3", "Workout Mix", 32, null),
        PlaylistItem("p4", "Chill Afternoons", 15, null),
    )

    val libraryPlaylists = listOf(
        LibraryPlaylistItem("p1", "Late Night Vibes", 24, null),
        LibraryPlaylistItem("p2", "Morning Commute", 18, null),
        LibraryPlaylistItem("p3", "Workout Mix", 32, null),
    )

    val albumDetailTracks = listOf(
        AlbumDetailTrack("t1", "Airbag", "Radiohead", "4:44", 1),
        AlbumDetailTrack("t2", "Paranoid Android", "Radiohead", "6:27", 2, isFavorite = true),
        AlbumDetailTrack("t3", "Subterranean Homesick Alien", "Radiohead", "4:27", 3),
        AlbumDetailTrack("t4", "Exit Music (For a Film)", "Radiohead", "4:24", 4),
        AlbumDetailTrack("t5", "Let Down", "Radiohead", "4:59", 5, isFavorite = true),
        AlbumDetailTrack("t6", "Karma Police", "Radiohead", "4:22", 6),
        AlbumDetailTrack("t7", "Fitter Happier", "Radiohead", "1:57", 7),
        AlbumDetailTrack("t8", "Electioneering", "Radiohead", "3:50", 8),
        AlbumDetailTrack("t9", "Climbing Up the Walls", "Radiohead", "4:45", 9),
        AlbumDetailTrack("t10", "No Surprises", "Radiohead", "3:48", 10),
        AlbumDetailTrack("t11", "Lucky", "Radiohead", "4:19", 11),
        AlbumDetailTrack("t12", "The Tourist", "Radiohead", "5:25", 12),
    )

    val artistTracks = listOf(
        ArtistTrack("t1", "Paranoid Android", "6:27", "OK Computer"),
        ArtistTrack("t2", "Karma Police", "4:22", "OK Computer"),
        ArtistTrack("t3", "Everything In Its Right Place", "4:11", "Kid A"),
        ArtistTrack("t4", "Reckoner", "4:50", "In Rainbows"),
        ArtistTrack("t5", "No Surprises", "3:48", "OK Computer"),
    )

    val artistAlbums = listOf(
        ArtistAlbum("a1", "OK Computer", 1997, "a1"),
        ArtistAlbum("a2", "In Rainbows", 2007, "a2"),
        ArtistAlbum("a3", "Kid A", 2000, "a3"),
        ArtistAlbum("a4", "The Bends", 1995, "a4"),
        ArtistAlbum("a5", "A Moon Shaped Pool", 2016, "a5"),
    )

    val queueNowPlaying = QueueTrack(
        "qt1", "Reckoner", "Radiohead", "In Rainbows", "4:50", null,
    )

    val queueUpNext = listOf(
        QueueTrack("qt2", "House of Cards", "Radiohead", "In Rainbows", "5:28", null),
        QueueTrack("qt3", "Jigsaw Falling into Place", "Radiohead", "In Rainbows", "4:09", null),
        QueueTrack("qt4", "Videotape", "Radiohead", "In Rainbows", "4:40", null),
        QueueTrack("qt5", "All I Need", "Radiohead", "In Rainbows", "3:49", null),
        QueueTrack("qt6", "Faust Arp", "Radiohead", "In Rainbows", "2:10", null),
    )

    val syncedLyrics = listOf(
        LyricsLine(0, "Take me on a trip upon your magic swirling ship"),
        LyricsLine(5_000, "My senses have been stripped"),
        LyricsLine(9_000, "My hands can't feel to grip"),
        LyricsLine(13_000, "My toes too numb to step"),
        LyricsLine(17_500, "Wait only for my boot heels to be wandering"),
        LyricsLine(24_000, "I'm ready to go anywhere"),
        LyricsLine(28_000, "I'm ready for to fade"),
        LyricsLine(32_000, "Into my own parade"),
        LyricsLine(36_000, "Cast your dancing spell my way"),
        LyricsLine(41_000, "I promise to go under it"),
        LyricsLine(50_000, ""),
        LyricsLine(52_000, "Hey, Mr. Tambourine Man, play a song for me"),
        LyricsLine(58_000, "I'm not sleepy and there is no place I'm going to"),
        LyricsLine(65_000, "Hey, Mr. Tambourine Man, play a song for me"),
        LyricsLine(71_000, "In the jingle jangle morning I'll come following you"),
    )

    val playlistDetailTracks = listOf(
        PlaylistDetailTrack("pt1", "Paranoid Android", "Radiohead", "6:27", null),
        PlaylistDetailTrack("pt2", "Let It Happen", "Tame Impala", "7:47", null),
        PlaylistDetailTrack("pt3", "Last Nite", "The Strokes", "3:13", null),
        PlaylistDetailTrack("pt4", "Sprawl II", "Arcade Fire", "5:25", null),
        PlaylistDetailTrack("pt5", "Losing My Edge", "LCD Soundsystem", "7:54", null),
    )

    val favoriteTracks = listOf(
        Track(
            id = "ft1", name = "Paranoid Android", albumId = "a1", albumName = "OK Computer",
            artistId = "ar1", artistName = "Radiohead", trackNumber = 2, discNumber = 1,
            duration = Duration.ofSeconds(387), genres = listOf("Alternative Rock"), imageId = null,
            isFavorite = true, playCount = 42, lastPlayedAt = 0L, normalizationGain = null,
        ),
        Track(
            id = "ft2", name = "Reckoner", albumId = "a2", albumName = "In Rainbows",
            artistId = "ar1", artistName = "Radiohead", trackNumber = 7, discNumber = 1,
            duration = Duration.ofSeconds(290), genres = listOf("Art Rock"), imageId = null,
            isFavorite = true, playCount = 28, lastPlayedAt = 0L, normalizationGain = null,
        ),
        Track(
            id = "ft3", name = "Let It Happen", albumId = "a8", albumName = "Currents",
            artistId = "ar2", artistName = "Tame Impala", trackNumber = 1, discNumber = 1,
            duration = Duration.ofSeconds(467), genres = listOf("Psychedelic Rock"), imageId = null,
            isFavorite = true, playCount = 35, lastPlayedAt = 0L, normalizationGain = null,
        ),
        Track(
            id = "ft4", name = "No Surprises", albumId = "a1", albumName = "OK Computer",
            artistId = "ar1", artistName = "Radiohead", trackNumber = 10, discNumber = 1,
            duration = Duration.ofSeconds(228), genres = listOf("Alternative Rock"), imageId = null,
            isFavorite = true, playCount = 19, lastPlayedAt = 0L, normalizationGain = null,
        ),
        Track(
            id = "ft5", name = "Everything In Its Right Place", albumId = "a3", albumName = "Kid A",
            artistId = "ar1", artistName = "Radiohead", trackNumber = 1, discNumber = 1,
            duration = Duration.ofSeconds(251), genres = listOf("Electronic"), imageId = null,
            isFavorite = true, playCount = 31, lastPlayedAt = 0L, normalizationGain = null,
        ),
    )

    val favoriteAlbums = listOf(
        Album(id = "a1", name = "OK Computer", artistId = "ar1", artistName = "Radiohead", year = 1997, trackCount = 12, genres = listOf("Alternative Rock"), imageId = "a1", isFavorite = true),
        Album(id = "a2", name = "In Rainbows", artistId = "ar1", artistName = "Radiohead", year = 2007, trackCount = 10, genres = listOf("Art Rock"), imageId = "a2", isFavorite = true),
        Album(id = "a8", name = "Currents", artistId = "ar2", artistName = "Tame Impala", year = 2015, trackCount = 13, genres = listOf("Psychedelic Rock"), imageId = "a8", isFavorite = true),
    )

    val favoriteArtists = listOf(
        Artist(id = "ar1", name = "Radiohead", albumCount = 9, imageId = "ar1", isFavorite = true, overview = "English rock band from Abingdon, Oxfordshire", genres = listOf("Alternative Rock", "Art Rock")),
        Artist(id = "ar2", name = "Tame Impala", albumCount = 4, imageId = "ar2", isFavorite = true, overview = "Psychedelic music project led by Kevin Parker", genres = listOf("Psychedelic Rock")),
    )

    val searchTracks = listOf(
        Track(
            id = "st1", name = "Paranoid Android", albumId = "a1", albumName = "OK Computer",
            artistId = "ar1", artistName = "Radiohead", trackNumber = 2, discNumber = 1,
            duration = Duration.ofSeconds(387), genres = listOf("Alternative Rock"), imageId = null,
            isFavorite = false, playCount = 0, lastPlayedAt = 0L, normalizationGain = null,
        ),
        Track(
            id = "st2", name = "Karma Police", albumId = "a1", albumName = "OK Computer",
            artistId = "ar1", artistName = "Radiohead", trackNumber = 6, discNumber = 1,
            duration = Duration.ofSeconds(262), genres = listOf("Alternative Rock"), imageId = null,
            isFavorite = false, playCount = 0, lastPlayedAt = 0L, normalizationGain = null,
        ),
    )

    val searchAlbums = listOf(
        Album(id = "a1", name = "OK Computer", artistId = "ar1", artistName = "Radiohead", year = 1997, trackCount = 12, genres = listOf("Alternative Rock"), imageId = "a1", isFavorite = false),
        Album(id = "a2", name = "In Rainbows", artistId = "ar1", artistName = "Radiohead", year = 2007, trackCount = 10, genres = listOf("Art Rock"), imageId = "a2", isFavorite = false),
    )

    val searchArtists = listOf(
        Artist(id = "ar1", name = "Radiohead", albumCount = 9, imageId = "ar1", isFavorite = false, overview = null, genres = listOf("Alternative Rock")),
    )
}
