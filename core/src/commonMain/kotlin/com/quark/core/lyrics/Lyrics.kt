package com.quark.core.lyrics

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** One line of a song, with the moment it is sung if the source knows it. */
data class LyricLine(val at: Duration, val text: String)

/**
 * A song's words.
 *
 * [synced] says whether the timestamps mean anything: plain text comes back as
 * lines all at zero, and the view should not try to follow along with it.
 */
data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    /**
     * Index of the line that should be lit at [position], or -1 before the
     * first one. Binary search: this is asked on every position update, which
     * arrives several times a second.
     */
    fun lineAt(position: Duration): Int {
        if (!synced || lines.isEmpty()) return -1

        var low = 0
        var high = lines.lastIndex
        var found = -1
        while (low <= high) {
            val middle = (low + high) / 2
            if (lines[middle].at <= position) {
                found = middle
                low = middle + 1
            } else {
                high = middle - 1
            }
        }
        return found
    }

    companion object {
        val None = Lyrics(emptyList(), synced = false)
    }
}

/**
 * Reads the two formats Yandex serves: `TEXT_LRC` and `TEXT_PLAIN`.
 *
 * The Dart version parsed the fractional part of a timestamp as milliseconds
 * (`yandex_music_singleton.dart:606`), so `[01:23.45]` became 23.045s instead of
 * 23.45s and every line ran up to a second early. Here two digits are
 * centiseconds and three are milliseconds, which is what the format says.
 */
object LrcParser {

    private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    /** Metadata lines such as `[ar:…]` and `[length:…]`, which are not lyrics. */
    private val METADATA = Regex("""^\[[a-zA-Z#]+:.*]$""")

    fun parse(text: String): Lyrics {
        val lines = mutableListOf<LyricLine>()
        var sawTimestamp = false

        for (raw in text.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || METADATA.matches(line)) continue

            val stamps = TIMESTAMP.findAll(line).toList()
            if (stamps.isEmpty()) {
                lines += LyricLine(Duration.ZERO, line)
                continue
            }

            sawTimestamp = true
            // A line may carry several timestamps when the same words repeat.
            val words = line.substring(stamps.last().range.last + 1).trim()
            for (stamp in stamps) {
                lines += LyricLine(stamp.toDuration(), words)
            }
        }

        if (!sawTimestamp) {
            return Lyrics(lines.filter { it.text.isNotEmpty() }, synced = false)
        }

        val ordered = lines.sortedBy { it.at }
        // Give the view something to sit on before the first line arrives, as
        // the Dart build did.
        val withLeadIn = if (ordered.firstOrNull()?.at != Duration.ZERO) {
            listOf(LyricLine(Duration.ZERO, "")) + ordered
        } else {
            ordered
        }
        return Lyrics(withLeadIn, synced = true)
    }

    private fun MatchResult.toDuration(): Duration {
        val minutes = groupValues[1].toLong()
        val seconds = groupValues[2].toLong()
        val fraction = groupValues[3]
        val fractionMillis = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 100
            2 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return (minutes * 60_000 + seconds * 1_000 + fractionMillis).milliseconds
    }
}
