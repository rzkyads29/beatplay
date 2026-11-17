/**
 * BeatPlay Wrapped 2026 + Home showcase.
 * Self-contained models and realistic Indonesian sample data so the screens can
 * be rendered without wiring a ViewModel first.
 */

package com.metrolist.music.ui.screens.beatplay

data class BeatPlaySong(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val coverUrl: String,
    val plays: Int,
)

data class BeatPlayArtist(
    val id: String,
    val name: String,
    val imageUrl: String,
    val hoursListened: Int,
)

data class BeatPlayPlaylist(
    val id: String,
    val name: String,
    val description: String,
    val coverUrl: String,
    val trackCount: Int,
)

data class BeatPlayQuickAccess(
    val id: String,
    val title: String,
    val coverUrl: String,
)

data class BeatPlayWrappedStats(
    val year: Int,
    val totalMinutes: Int,
    val topGenres: List<String>,
    val topSongs: List<BeatPlaySong>,
    val topArtists: List<BeatPlayArtist>,
    val percentileLabel: String,
    val topArtistHours: Int,
    val topGenre: String,
    val minutesDeltaPercent: Int,
)

object BeatPlaySampleData {

    private fun art(seed: String, size: Int = 320): String =
        "https://picsum.photos/seed/$seed/$size/$size"

    /** 8 items for the compact 2-column quick grid. */
    val quickAccess: List<BeatPlayQuickAccess> = listOf(
        BeatPlayQuickAccess("qa_liked", "Lagu yang Disukai", art("beatplay-liked")),
        BeatPlayQuickAccess("qa_recent", "Baru Diputar", art("beatplay-recent")),
        BeatPlayQuickAccess("qa_pop", "Pop Indonesia Teratas", art("beatplay-pop")),
        BeatPlayQuickAccess("qa_indie", "Indie Lokal Pilihan", art("beatplay-indie")),
        BeatPlayQuickAccess("qa_jedag", "Jedag Jedug Remix", art("beatplay-jedag")),
        BeatPlayQuickAccess("qa_lofi", "Lo-fi Nusantara", art("beatplay-lofi")),
        BeatPlayQuickAccess("qa_koplo", "Dangdut Koplo", art("beatplay-koplo")),
        BeatPlayQuickAccess("qa_top50", "Top 50 Global", art("beatplay-top50")),
    )

    /** "Baru saja didengar" carousel. */
    val recentlyPlayed: List<BeatPlayPlaylist> = listOf(
        BeatPlayPlaylist(
            id = "rp_1",
            name = "Evaluasi",
            description = "Hindia",
            coverUrl = art("hindia-evaluasi"),
            trackCount = 10,
        ),
        BeatPlayPlaylist(
            id = "rp_2",
            name = "Selamat Ulang Tahun",
            description = "Nadin Amizah",
            coverUrl = art("nadin-selamat"),
            trackCount = 12,
        ),
        BeatPlayPlaylist(
            id = "rp_3",
            name = "Walk The Talk",
            description = "Pamungkas",
            coverUrl = art("pamungkas-walk"),
            trackCount = 9,
        ),
        BeatPlayPlaylist(
            id = "rp_4",
            name = "Manusia",
            description = "Tulus",
            coverUrl = art("tulus-manusia"),
            trackCount = 11,
        ),
        BeatPlayPlaylist(
            id = "rp_5",
            name = "Sialan",
            description = "Bernadya",
            coverUrl = art("bernadya-sialan"),
            trackCount = 8,
        ),
        BeatPlayPlaylist(
            id = "rp_6",
            name = "Abdi",
            description = "Feast",
            coverUrl = art("feast-abdi"),
            trackCount = 10,
        ),
    )

    /** "Album dari lagu yang kamu suka" carousel. */
    val albumsFromLikes: List<BeatPlayPlaylist> = listOf(
        BeatPlayPlaylist(
            id = "al_1",
            name = "Lagipula Hidup Akan Berakhir",
            description = "Hindia",
            coverUrl = art("alb-lagipula"),
            trackCount = 10,
        ),
        BeatPlayPlaylist(
            id = "al_2",
            name = "Kalah Bertaruh",
            description = "Nadin Amizah",
            coverUrl = art("alb-kalah"),
            trackCount = 11,
        ),
        BeatPlayPlaylist(
            id = "al_3",
            name = "Solipsism 0.2",
            description = "Pamungkas",
            coverUrl = art("alb-solipsism"),
            trackCount = 8,
        ),
        BeatPlayPlaylist(
            id = "al_4",
            name = "Monokrom",
            description = "Tulus",
            coverUrl = art("alb-monokrom"),
            trackCount = 9,
        ),
        BeatPlayPlaylist(
            id = "al_5",
            name = "Terlintas",
            description = "Bernadya",
            coverUrl = art("alb-terlintas"),
            trackCount = 7,
        ),
    )

    val wrapped: BeatPlayWrappedStats = BeatPlayWrappedStats(
        year = 2026,
        totalMinutes = 42_850,
        topGenres = listOf("Pop Indie", "Alternative Rock", "Jedag Jedug Remix"),
        topSongs = listOf(
            BeatPlaySong(
                id = "song_1",
                title = "Evaluasi",
                artist = "Hindia",
                album = "Evaluasi",
                coverUrl = art("song-evaluasi", 240),
                plays = 214,
            ),
            BeatPlaySong(
                id = "song_2",
                title = "Sialan",
                artist = "Bernadya",
                album = "Sialan",
                coverUrl = art("song-sialan", 240),
                plays = 187,
            ),
            BeatPlaySong(
                id = "song_3",
                title = "Selamat Ulang Tahun",
                artist = "Nadin Amizah",
                album = "Selamat Ulang Tahun",
                coverUrl = art("song-nadin", 240),
                plays = 163,
            ),
            BeatPlaySong(
                id = "song_4",
                title = "To The Bone",
                artist = "Pamungkas",
                album = "Walk The Talk",
                coverUrl = art("song-pamungkas", 240),
                plays = 141,
            ),
            BeatPlaySong(
                id = "song_5",
                title = "Runtuh",
                artist = "Feby Putri",
                album = "Runtuh",
                coverUrl = art("song-runtuh", 240),
                plays = 128,
            ),
        ),
        topArtists = listOf(
            BeatPlayArtist("artist_1", "Hindia", art("artist-hindia", 400), 1_284),
            BeatPlayArtist("artist_2", "Bernadya", art("artist-bernadya", 400), 964),
            BeatPlayArtist("artist_3", "Pamungkas", art("artist-pamungkas", 400), 812),
            BeatPlayArtist("artist_4", "Nadin Amizah", art("artist-nadin", 400), 701),
            BeatPlayArtist("artist_5", "Feast", art("artist-feast", 400), 553),
        ),
        percentileLabel = "Top 0,5% Pendengar Setia",
        topArtistHours = 128,
        topGenre = "Pop Indie",
        minutesDeltaPercent = 18,
    )
}