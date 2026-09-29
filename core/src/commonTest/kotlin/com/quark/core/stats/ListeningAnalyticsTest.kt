package com.quark.core.stats

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlin.test.Test
import kotlin.test.assertEquals

class ListeningAnalyticsTest {

    private val utc = TimeZone.UTC

    /** 2026-01-05 is a Monday; hours are UTC. */
    private fun at(day: Int, hour: Int, minute: Int = 0): Long =
        LocalDate(2026, 1, day).toEpochDays().toLong() * 86_400 + hour * 3_600 + minute * 60

    private fun listen(key: String, artist: String, at: Long, played: Long, total: Long = 200, skipped: Boolean = false) =
        Listen(key, key, listOf(artist), "Album of $artist", "local", at, played, total, skipped)

    @Test
    fun totals_rankings_and_hours() {
        val listens = listOf(
            listen("dogs", "Pink Floyd", at(5, 9), played = 200),
            listen("dogs", "Pink Floyd", at(5, 10), played = 190),
            listen("sheep", "Pink Floyd", at(5, 21), played = 20, skipped = true),
            listen("heroes", "David Bowie", at(6, 21), played = 180),
        )

        val report = ListeningAnalytics.analyze(listens, nowSeconds = at(6, 23), timeZone = utc)

        assertEquals(4, report.totalPlays)
        assertEquals(590, report.totalSeconds)
        assertEquals(3, report.completePlays)
        assertEquals(1, report.skips)
        assertEquals(3, report.uniqueTracks)
        assertEquals(2, report.uniqueArtists)
        assertEquals("dogs", report.topTracksByPlays.first().key)
        assertEquals("Pink Floyd", report.topArtistsByPlays.first().name)
        assertEquals(21, report.peakHour)
        assertEquals(3, report.byDayOfWeek[0]) // Monday
        assertEquals(1, report.byDayOfWeek[1]) // Tuesday
        assertEquals(2, report.activeDays)
    }

    @Test
    fun sessions_break_after_half_an_hour_of_silence() {
        val listens = listOf(
            listen("a", "x", at(5, 9, 0), played = 200),
            listen("b", "x", at(5, 9, 4), played = 200),
            listen("c", "x", at(5, 11, 0), played = 100),
        )
        assertEquals(listOf(440L, 100L), ListeningAnalytics.sessions(listens))
    }

    @Test
    fun streaks_count_consecutive_days_and_the_one_running_now() {
        val days = setOf(
            LocalDate(2026, 1, 1), LocalDate(2026, 1, 2), LocalDate(2026, 1, 3),
            LocalDate(2026, 1, 7), LocalDate(2026, 1, 8),
        )
        assertEquals(3 to 2, ListeningAnalytics.streaks(days, today = LocalDate(2026, 1, 8)))
        assertEquals(3 to 2, ListeningAnalytics.streaks(days, today = LocalDate(2026, 1, 9)))
        assertEquals(3 to 0, ListeningAnalytics.streaks(days, today = LocalDate(2026, 1, 11)))
    }
}
