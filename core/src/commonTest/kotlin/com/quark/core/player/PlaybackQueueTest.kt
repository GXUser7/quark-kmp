package com.quark.core.player

import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Track
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

private fun track(name: String): Track = LocalTrack(
    title = name,
    artists = listOf("artist"),
    albums = listOf("album"),
    filepath = "/music/$name.flac",
    coverType = CoverType.NoCover,
)

private val a = track("a")
private val b = track("b")
private val c = track("c")
private val d = track("d")

private fun queueOf(vararg playlist: Track, current: Track) = PlaybackQueue().apply {
    setPlaylist(playlist.toList())
    setCurrent(current)
}

class PlaybackQueueTest {

    @Test
    fun walks_the_playlist_in_order() {
        val q = queueOf(a, b, c, current = a)
        assertEquals(b, q.next())
        assertEquals(c, q.next())
    }

    @Test
    fun wraps_around_at_the_end() {
        val q = queueOf(a, b, c, current = c)
        assertEquals(a, q.next())
    }

    @Test
    fun repeat_one_holds_position() {
        val q = queueOf(a, b, c, current = b).apply { repeat = RepeatMode.One }
        assertEquals(b, q.next())
    }

    @Test
    fun previous_wraps_backwards() {
        val q = queueOf(a, b, c, current = a)
        assertEquals(c, q.previous())
    }

    @Test
    fun queued_tracks_play_before_the_playlist_resumes() {
        val q = queueOf(a, b, c, current = a)
        q.enqueueLast(d)

        assertEquals(d, q.next())
        // Queue exhausted: back into the playlist right after where we left it.
        assertEquals(b, q.next())
    }

    @Test
    fun queue_returns_to_the_track_that_opened_it() {
        val q = queueOf(a, b, c, current = b)
        q.enqueueLast(d)
        assertEquals(b, q.returnPoint)

        assertEquals(d, q.next())
        assertEquals(c, q.next())
    }

    @Test
    fun picking_from_the_queue_drops_what_was_before_it() {
        val q = queueOf(a, b, current = a)
        q.enqueueLast(listOf(c, d))

        q.playNow(d)

        assertEquals(listOf(d), q.queue)
        assertEquals(d, q.current)
    }

    @Test
    fun picking_from_the_playlist_sets_the_return_point() {
        val q = queueOf(a, b, c, current = a)
        q.enqueueLast(d)

        q.playNow(c)

        assertEquals(c, q.returnPoint)
    }

    @Test
    fun clearing_the_queue_forgets_the_return_point() {
        val q = queueOf(a, b, current = a)
        q.enqueueLast(c)
        q.clearQueue()

        assertTrue(q.queue.isEmpty())
        assertNull(q.returnPoint)
        assertEquals(b, q.next())
    }

    @Test
    fun peek_does_not_advance_or_consume_the_return_point() {
        val q = queueOf(a, b, c, current = b)
        q.enqueueLast(d)
        q.playNow(d)

        assertEquals(c, q.peekNext())
        assertEquals(d, q.current)
        assertEquals(b, q.returnPoint)
        // Still the same answer the second time round.
        assertEquals(c, q.peekNext())
    }

    @Test
    fun an_empty_playlist_yields_nothing_instead_of_crashing() {
        val q = PlaybackQueue()
        assertNull(q.next())
        assertNull(q.previous())
    }

    @Test
    fun a_track_rebuilt_from_storage_still_matches_by_value() {
        val q = queueOf(a, b, c, current = a)
        // Same data, different instance: the Dart original compares by identity
        // here and loses its place in the playlist.
        val rebuilt = track("a")
        q.setCurrent(rebuilt)

        assertEquals(b, q.next())
    }
}

class ShufflesTest {

    @Test
    fun after_current_keeps_the_head_intact() {
        val result = Shuffles.afterCurrent(listOf(a, b, c, d), pivot = b)
        assertEquals(listOf(a, b), result.take(2))
        assertEquals(setOf(c, d), result.drop(2).toSet())
    }

    @Test
    fun now_on_top_lifts_the_current_track() {
        val result = Shuffles.nowOnTop(listOf(a, b, c, d), pivot = c)
        assertEquals(c, result.first())
        assertEquals(4, result.size)
        assertEquals(setOf(a, b, c, d), result.toSet())
    }

    @Test
    fun full_keeps_every_track() {
        val result = Shuffles.full(listOf(a, b, c, d))
        assertEquals(setOf(a, b, c, d), result.toSet())
    }
}
