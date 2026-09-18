package com.quark.core.util

import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

/**
 * Values that stop being true after a while.
 *
 * Signed stream urls are the reason this exists: Yandex hands out a url that
 * works for a few minutes, so caching it forever would break playback and not
 * caching it at all would mean a round trip before every track. Ported from
 * `TimedCache` in `objects/track.dart:617`.
 *
 * Expired entries are dropped when they are next looked at rather than on a
 * timer; the cache holds tens of entries, not thousands.
 */
@OptIn(ExperimentalTime::class)
class TimedCache<K, V>(
    private val lifetime: Duration,
    private val now: () -> Instant = { Clock.System.now() },
) {
    private data class Entry<V>(val value: V, val storedAt: Instant)

    private val entries = mutableMapOf<K, Entry<V>>()

    operator fun get(key: K): V? {
        val entry = entries[key] ?: return null
        if (now() - entry.storedAt >= lifetime) {
            entries.remove(key)
            return null
        }
        return entry.value
    }

    operator fun set(key: K, value: V) {
        entries[key] = Entry(value, now())
    }

    /** Returns the cached value, or stores and returns what [produce] gives. */
    suspend fun getOrPut(key: K, produce: suspend (K) -> V): V {
        get(key)?.let { return it }
        val produced = produce(key)
        set(key, produced)
        return produced
    }

    fun remove(key: K) {
        entries.remove(key)
    }

    fun clear() = entries.clear()

    /** Entries that have not expired yet. Drops the ones that have. */
    val size: Int
        get() {
            val current = now()
            entries.entries.removeAll { current - it.value.storedAt >= lifetime }
            return entries.size
        }
}
