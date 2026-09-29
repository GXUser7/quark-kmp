package com.quark.data.repository

import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Playlist
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import com.quark.data.db.DatabaseFactory
import com.quark.data.db.QuarkDatabase
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private fun local(name: String) = LocalTrack(
    title = name,
    artists = listOf("Pink Floyd"),
    albums = listOf("Animals"),
    filepath = "/music/$name.flac",
    coverType = CoverType.BuiltIn,
)

class RepositoryTest {

    private lateinit var db: QuarkDatabase
    private lateinit var tracks: TrackRepository
    private lateinit var playlists: PlaylistRepository
    private lateinit var stats: ListenStatsRepository
    private lateinit var coverColors: CoverColorRepository

    @BeforeTest
    fun setUp() {
        db = DatabaseFactory.inMemory()
        tracks = TrackRepository(db, Dispatchers.Unconfined)
        playlists = PlaylistRepository(db, Dispatchers.Unconfined)
        stats = ListenStatsRepository(db, Dispatchers.Unconfined)
        coverColors = CoverColorRepository(db, Dispatchers.Unconfined)
    }

    @Test
    fun remembers_tracks_and_reads_them_back() = runTest {
        tracks.remember(listOf(local("dogs"), local("sheep")))

        val all = tracks.all()
        assertEquals(2, all.size)
        assertEquals(setOf("dogs", "sheep"), all.map(Track::title).toSet())
        assertEquals(listOf("Pink Floyd"), all.first().artists)
    }

    @Test
    fun remembering_the_same_path_twice_does_not_duplicate_it() = runTest {
        tracks.remember(listOf(local("dogs")))
        tracks.remember(listOf(local("dogs")))

        assertEquals(1, tracks.all().size)
    }

    @Test
    fun a_remote_track_keeps_its_source_and_id() = runTest {
        val yandex = YandexTrack(
            title = "Heroes",
            artists = listOf("David Bowie"),
            albums = listOf("Heroes"),
            filepath = "/cache/yandex/47127.flac",
            coverType = CoverType.Url,
            cover = "https://avatars.yandex.net/cover/300x300",
            trackId = "47127",
        )
        tracks.remember(listOf(yandex))

        val restored = tracks.byPath(yandex.filepath)
        assertTrue(restored is YandexTrack)
        assertEquals("47127", restored.trackId)
        assertEquals("https://avatars.yandex.net/cover/300x300", restored.cover)
    }

    @Test
    fun artists_split_on_every_separator_tags_use() = runTest {
        tracks.upsert(
            local("x").copy(artists = listOf("Simon & Garfunkel", "Art Garfunkel")),
        )

        val restored = assertNotNull(tracks.byPath("/music/x.flac"))
        assertEquals(listOf("Simon", "Garfunkel", "Art Garfunkel"), restored.artists)
    }

    @Test
    fun playlist_keeps_the_order_tracks_were_added_in() = runTest {
        val id = playlists.create("Animals")
        val ordered = listOf(local("pigs-on-the-wing"), local("dogs"), local("pigs"))
        playlists.addTracks(id, ordered)

        assertEquals(ordered.map(Track::title), playlists.tracksOf(id).map(Track::title))
        assertEquals(3L, playlists.size(id))
    }

    @Test
    fun saving_a_snapshot_replaces_the_same_playlist_atomically() = runTest {
        val first = Playlist(name = "Animals", tracks = listOf(local("dogs"), local("sheep")))
        val id = playlists.saveSnapshot(first)

        val replacement = Playlist(name = "Animals remastered", tracks = listOf(local("pigs")))
        val sameId = playlists.saveSnapshot(replacement, existingId = id)

        assertEquals(id, sameId)
        assertEquals(1, playlists.all().size)
        assertEquals("Animals remastered", assertNotNull(playlists.byId(id)).title)
        assertEquals(listOf("pigs"), playlists.tracksOf(id).map(Track::title))
    }

    @Test
    fun deleting_a_playlist_takes_its_entries_but_leaves_the_tracks() = runTest {
        val id = playlists.create("Temp")
        playlists.addTracks(id, listOf(local("dogs")))

        playlists.delete(id)

        assertNull(playlists.byId(id))
        assertEquals(1, tracks.all().size)
    }

    @Test
    fun renaming_a_playlist_sticks() = runTest {
        val id = playlists.create("Untitled")
        playlists.rename(id, "Animals")

        assertEquals("Animals", assertNotNull(playlists.byId(id)).title)
    }

    @Test
    fun cover_colors_round_trip_signed_argb_and_replace_old_values() = runTest {
        val first = listOf(0xFF102030.toInt(), 0xFF405060.toInt(), 0xFF708090.toInt())
        coverColors.put("deadbeef", first)
        assertEquals(first, coverColors.get("deadbeef"))

        val replacement = listOf(0xFFFFFFFF.toInt())
        coverColors.put("deadbeef", replacement)
        assertEquals(replacement, coverColors.get("deadbeef"))
        assertNull(coverColors.get("missing"))
    }

    @Test
    fun a_listen_is_recorded_even_for_a_track_not_seen_before() = runTest {
        val track = local("dogs")
        stats.record(
            Listen(
                trackPath = track.filepath,
                at = 1_726_000_000,
                played = 200.seconds,
                total = 1017.seconds,
                skipped = false,
            ),
            track,
        )

        assertEquals(200L, stats.totalSeconds(since = 0))
        assertEquals(1, stats.topTracks(since = 0).size)
    }

    @Test
    fun skipped_listens_stay_out_of_the_top_list() = runTest {
        val track = local("dogs")
        stats.record(
            Listen(track.filepath, 1_726_000_000, 5.seconds, 1017.seconds, skipped = true),
            track,
        )

        assertTrue(stats.topTracks(since = 0).isEmpty())
        // Still counted towards total time listened.
        assertEquals(5L, stats.totalSeconds(since = 0))
    }

    @Test
    fun progress_percent_is_clamped_and_safe_on_a_zero_length_track() {
        assertEquals(0, Listen("p", 0, 10.seconds, kotlin.time.Duration.ZERO, false).progressPercent)
        assertEquals(100, Listen("p", 0, 20.seconds, 10.seconds, false).progressPercent)
        assertEquals(50, Listen("p", 0, 5.seconds, 10.seconds, false).progressPercent)
    }
}
