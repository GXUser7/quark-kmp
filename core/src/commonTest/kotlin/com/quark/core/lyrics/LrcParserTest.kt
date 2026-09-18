package com.quark.core.lyrics

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class LrcParserTest {

    @Test
    fun reads_timestamps_and_words() {
        val lyrics = LrcParser.parse(
            """
            [00:00.00]I, I will be king
            [00:12.34]And you, you will be queen
            [01:05.50]Though nothing will drive them away
            """.trimIndent()
        )

        assertTrue(lyrics.synced)
        assertEquals(3, lyrics.lines.size)
        assertEquals(Duration.ZERO, lyrics.lines[0].at)
        assertEquals(12_340.milliseconds, lyrics.lines[1].at)
        assertEquals(65_500.milliseconds, lyrics.lines[2].at)
        assertEquals("And you, you will be queen", lyrics.lines[1].text)
    }

    @Test
    fun two_fraction_digits_are_centiseconds() {
        // The Dart original read these as milliseconds, which put every line up
        // to a second early.
        val lyrics = LrcParser.parse("[01:23.45]words")

        assertEquals(83_450.milliseconds, lyrics.lines.last().at)
    }

    @Test
    fun three_fraction_digits_are_milliseconds() {
        val lyrics = LrcParser.parse("[00:01.005]words")

        assertEquals(1_005.milliseconds, lyrics.lines.last().at)
    }

    @Test
    fun a_lead_in_is_added_when_the_song_does_not_start_at_zero() {
        val lyrics = LrcParser.parse("[00:14.00]first line")

        assertEquals(2, lyrics.lines.size)
        assertEquals(Duration.ZERO, lyrics.lines.first().at)
        assertEquals("", lyrics.lines.first().text)
    }

    @Test
    fun metadata_lines_are_not_lyrics() {
        val lyrics = LrcParser.parse(
            """
            [ar:David Bowie]
            [ti:Heroes]
            [length:06:11]
            [00:00.00]I, I will be king
            """.trimIndent()
        )

        assertEquals(1, lyrics.lines.size)
        assertEquals("I, I will be king", lyrics.lines.single().text)
    }

    @Test
    fun a_repeated_line_carries_every_timestamp_it_is_given() {
        val lyrics = LrcParser.parse("[00:10.00][01:10.00]we can be heroes")

        assertEquals(
            listOf(10.seconds, 70.seconds),
            lyrics.lines.filter { it.text.isNotEmpty() }.map { it.at },
        )
    }

    @Test
    fun plain_text_comes_back_unsynced() {
        val lyrics = LrcParser.parse("I, I will be king\nAnd you, you will be queen")

        assertFalse(lyrics.synced)
        assertEquals(2, lyrics.lines.size)
        assertTrue(lyrics.lines.all { it.at == Duration.ZERO })
    }

    @Test
    fun lines_out_of_order_are_sorted() {
        val lyrics = LrcParser.parse("[00:30.00]later\n[00:10.00]earlier")

        assertEquals(listOf("", "earlier", "later"), lyrics.lines.map { it.text })
    }

    @Test
    fun finds_the_line_for_a_position() {
        val lyrics = LrcParser.parse(
            "[00:00.00]one\n[00:10.00]two\n[00:20.00]three"
        )

        assertEquals(0, lyrics.lineAt(Duration.ZERO))
        assertEquals(0, lyrics.lineAt(9.seconds))
        assertEquals(1, lyrics.lineAt(10.seconds))
        assertEquals(2, lyrics.lineAt(45.seconds))
    }

    @Test
    fun unsynced_lyrics_never_highlight_a_line() {
        val lyrics = LrcParser.parse("just words")

        assertEquals(-1, lyrics.lineAt(30.seconds))
    }

    @Test
    fun empty_input_yields_nothing() {
        val lyrics = LrcParser.parse("")

        assertTrue(lyrics.isEmpty)
        assertEquals(-1, lyrics.lineAt(Duration.ZERO))
    }
}
