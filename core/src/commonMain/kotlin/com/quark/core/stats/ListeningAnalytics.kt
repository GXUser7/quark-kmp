package com.quark.core.stats

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.Instant
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

/** One listen, as the analytics see it. Times are Unix seconds. */
data class Listen(
    val key: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val source: String,
    val at: Long,
    val playedSeconds: Long,
    val totalSeconds: Long,
    val skipped: Boolean,
) {
    val progress: Int
        get() = if (totalSeconds <= 0) 0 else ((playedSeconds * 100) / totalSeconds).toInt().coerceIn(0, 100)
}

data class TrackStat(
    val key: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val plays: Int,
    val seconds: Long,
    val completions: Int,
    val skips: Int,
) {
    val completionRate: Double get() = if (plays == 0) 0.0 else completions.toDouble() / plays
}

data class ArtistStat(val name: String, val plays: Int, val seconds: Long, val tracks: Int)

data class AlbumStat(val album: String, val artist: String, val plays: Int, val seconds: Long)

data class DayStat(val date: LocalDate, val plays: Int, val seconds: Long)

data class ListeningReport(
    val totalPlays: Int,
    val totalSeconds: Long,
    val completePlays: Int,
    val skips: Int,
    val uniqueTracks: Int,
    val uniqueArtists: Int,
    val uniqueAlbums: Int,
    val firstListen: Long?,
    val lastListen: Long?,
    val activeDays: Int,
    val topTracksByPlays: List<TrackStat>,
    val topTracksByTime: List<TrackStat>,
    val mostSkipped: List<TrackStat>,
    val topArtistsByPlays: List<ArtistStat>,
    val topArtistsByTime: List<ArtistStat>,
    val topAlbums: List<AlbumStat>,
    /** Plays per hour of the day, 0 to 23, in local time. */
    val hourly: List<Int>,
    /** Plays per day of the week, Monday first. */
    val byDayOfWeek: List<Int>,
    val topDays: List<DayStat>,
    val sessions: Int,
    val averageSessionSeconds: Long,
    val longestSessionSeconds: Long,
    val longestStreakDays: Int,
    val currentStreakDays: Int,
    val sources: Map<String, Int>,
) {
    val skipRate: Double get() = if (totalPlays == 0) 0.0 else skips.toDouble() / totalPlays
    val completionRate: Double get() = if (totalPlays == 0) 0.0 else completePlays.toDouble() / totalPlays
    val averagePlaysPerDay: Double get() = if (activeDays == 0) 0.0 else totalPlays.toDouble() / activeDays
    val averageSecondsPerDay: Long get() = if (activeDays == 0) 0 else totalSeconds / activeDays
    val peakHour: Int get() = hourly.indices.maxByOrNull { hourly[it] } ?: 0

    /** Share of plays in the morning (6–12), afternoon (12–18), evening (18–24) and night (0–6). */
    fun dayPartShares(): List<Double> {
        val total = hourly.sum().takeIf { it > 0 } ?: return listOf(0.0, 0.0, 0.0, 0.0)
        fun share(range: IntRange) = range.sumOf { hourly[it] }.toDouble() / total
        return listOf(share(6..11), share(12..17), share(18..23), share(0..5))
    }

    companion object {
        val Empty = ListeningAnalytics.analyze(emptyList(), 0)
    }
}

/**
 * The statistics screen's numbers, from the raw listen log. A port of the part
 * of `MusicAnalyticsEngine` (`listen_stats_enjoyer.dart` on slop) the screen
 * shows: totals, rankings, when the listening happens, sessions and streaks.
 *
 * A listen counts as complete at 80 % played; a pause of more than half an hour
 * starts a new session. Both thresholds are the Dart build's.
 */
object ListeningAnalytics {

    const val COMPLETION_THRESHOLD = 80
    const val SESSION_GAP_SECONDS = 30 * 60L
    private const val TOP = 10

    fun analyze(
        listens: List<Listen>,
        nowSeconds: Long,
        timeZone: TimeZone = TimeZone.currentSystemDefault(),
    ): ListeningReport {
        val sorted = listens.sortedBy(Listen::at)

        val byTrack = sorted.groupBy(Listen::key).map { (key, plays) ->
            val first = plays.first()
            TrackStat(
                key = key,
                title = first.title,
                artists = first.artists,
                album = first.album,
                plays = plays.size,
                seconds = plays.sumOf(Listen::playedSeconds),
                completions = plays.count { it.progress >= COMPLETION_THRESHOLD },
                skips = plays.count(Listen::skipped),
            )
        }

        val artistPlays = mutableMapOf<String, MutableList<Listen>>()
        for (listen in sorted) {
            for (artist in listen.artists.ifEmpty { listOf("") }) {
                if (artist.isBlank()) continue
                artistPlays.getOrPut(artist) { mutableListOf() } += listen
            }
        }
        val byArtist = artistPlays.map { (name, plays) ->
            ArtistStat(name, plays.size, plays.sumOf(Listen::playedSeconds), plays.map(Listen::key).toSet().size)
        }

        val byAlbum = sorted.filter { it.album.isNotBlank() }
            .groupBy { it.album to (it.artists.firstOrNull() ?: "") }
            .map { (key, plays) -> AlbumStat(key.first, key.second, plays.size, plays.sumOf(Listen::playedSeconds)) }

        val local = sorted.map { it to Instant.fromEpochSeconds(it.at).toLocalDateTime(timeZone) }
        val hourly = MutableList(24) { 0 }
        val weekdays = MutableList(7) { 0 }
        val days = mutableMapOf<LocalDate, Pair<Int, Long>>()
        for ((listen, time) in local) {
            hourly[time.hour]++
            weekdays[time.dayOfWeek.ordinalFromMonday()]++
            val (plays, seconds) = days[time.date] ?: (0 to 0L)
            days[time.date] = (plays + 1) to (seconds + listen.playedSeconds)
        }

        val sessionLengths = sessions(sorted)
        val (longestStreak, currentStreak) = streaks(days.keys, Instant.fromEpochSeconds(nowSeconds).toLocalDateTime(timeZone).date)

        return ListeningReport(
            totalPlays = sorted.size,
            totalSeconds = sorted.sumOf(Listen::playedSeconds),
            completePlays = sorted.count { it.progress >= COMPLETION_THRESHOLD },
            skips = sorted.count(Listen::skipped),
            uniqueTracks = byTrack.size,
            uniqueArtists = byArtist.size,
            uniqueAlbums = byAlbum.size,
            firstListen = sorted.firstOrNull()?.at,
            lastListen = sorted.lastOrNull()?.at,
            activeDays = days.size,
            topTracksByPlays = byTrack.sortedWith(compareByDescending<TrackStat> { it.plays }.thenByDescending { it.seconds }).take(TOP),
            topTracksByTime = byTrack.sortedByDescending(TrackStat::seconds).take(TOP),
            mostSkipped = byTrack.filter { it.skips > 0 }.sortedByDescending(TrackStat::skips).take(TOP),
            topArtistsByPlays = byArtist.sortedWith(compareByDescending<ArtistStat> { it.plays }.thenByDescending { it.seconds }).take(TOP),
            topArtistsByTime = byArtist.sortedByDescending(ArtistStat::seconds).take(TOP),
            topAlbums = byAlbum.sortedByDescending(AlbumStat::plays).take(TOP),
            hourly = hourly,
            byDayOfWeek = weekdays,
            topDays = days.map { (date, value) -> DayStat(date, value.first, value.second) }
                .sortedByDescending(DayStat::seconds).take(TOP),
            sessions = sessionLengths.size,
            averageSessionSeconds = if (sessionLengths.isEmpty()) 0 else sessionLengths.sum() / sessionLengths.size,
            longestSessionSeconds = sessionLengths.maxOrNull() ?: 0,
            longestStreakDays = longestStreak,
            currentStreakDays = currentStreak,
            sources = sorted.groupingBy(Listen::source).eachCount(),
        )
    }

    /** Lengths of listening sessions: runs of listens without a long pause between them. */
    internal fun sessions(sorted: List<Listen>): List<Long> {
        if (sorted.isEmpty()) return emptyList()
        val lengths = mutableListOf<Long>()
        var start = sorted.first().at
        var end = sorted.first().at + sorted.first().playedSeconds
        for (listen in sorted.drop(1)) {
            if (listen.at - end > SESSION_GAP_SECONDS) {
                lengths += (end - start).coerceAtLeast(0)
                start = listen.at
            }
            end = maxOf(end, listen.at + listen.playedSeconds)
        }
        lengths += (end - start).coerceAtLeast(0)
        return lengths
    }

    /** The longest run of consecutive listening days, and the run ending today or yesterday. */
    internal fun streaks(days: Set<LocalDate>, today: LocalDate): Pair<Int, Int> {
        if (days.isEmpty()) return 0 to 0
        val ordered = days.map(LocalDate::toEpochDays).sorted()
        var longest = 1
        var run = 1
        for (index in 1 until ordered.size) {
            run = if (ordered[index] == ordered[index - 1] + 1) run + 1 else 1
            longest = maxOf(longest, run)
        }
        val set = ordered.toSet()
        var cursor = today.toEpochDays()
        if (cursor !in set) cursor -= 1
        var current = 0
        while (cursor in set) {
            current++
            cursor -= 1
        }
        return longest to current
    }

    private fun DayOfWeek.ordinalFromMonday(): Int = ordinal
}
