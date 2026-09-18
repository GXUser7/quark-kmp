package com.quark.core.util

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class)
class TimedCacheTest {

    private var now = Instant.fromEpochSeconds(1_726_000_000)
    private fun cache() = TimedCache<String, String>(lifetime = 30.minutes, now = { now })

    @Test
    fun returns_what_was_stored() {
        val cache = cache()
        cache["47127"] = "https://cdn/track.flac"

        assertEquals("https://cdn/track.flac", cache["47127"])
    }

    @Test
    fun forgets_an_entry_once_its_lifetime_is_up() {
        val cache = cache()
        cache["47127"] = "https://cdn/track.flac"

        now += 29.minutes
        assertEquals("https://cdn/track.flac", cache["47127"])

        now += 2.minutes
        assertNull(cache["47127"])
    }

    @Test
    fun the_boundary_counts_as_expired() {
        val cache = cache()
        cache["x"] = "v"

        now += 30.minutes
        assertNull(cache["x"])
    }

    @Test
    fun get_or_put_only_produces_once_while_the_entry_is_fresh() = runTest {
        val cache = cache()
        var calls = 0

        repeat(3) { cache.getOrPut("47127") { calls++; "url" } }
        assertEquals(1, calls)

        now += 31.minutes
        cache.getOrPut("47127") { calls++; "url" }
        assertEquals(2, calls)
    }

    @Test
    fun size_does_not_count_entries_that_have_expired() {
        val cache = cache()
        cache["a"] = "1"
        now += 20.seconds
        cache["b"] = "2"

        assertEquals(2, cache.size)

        now += 30.minutes
        assertEquals(0, cache.size)
    }
}
