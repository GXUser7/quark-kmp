package com.quark.core.cue

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.time.Duration.Companion.seconds

class CueParserTest {

    @Test
    fun parses_album_and_track_metadata() {
        val cue = CueParser.parse(
            """
            REM GENRE "Art Rock"
            REM DATE 1977
            CATALOG 1234567890123
            PERFORMER "David Bowie"
            TITLE "Heroes"
            FILE "Heroes.flac" WAVE
              TRACK 01 AUDIO
                TITLE "Beauty and the Beast"
                PERFORMER "David Bowie"
                FLAGS PRE DCP
                ISRC GBAYE7700010
                INDEX 00 00:00:00
                INDEX 01 00:02:00
              TRACK 02 AUDIO
                TITLE "Joe the Lion"
                PREGAP 00:00:15
                INDEX 01 03:36:12
            """.trimIndent(),
        )

        assertEquals("Heroes", cue.title)
        assertEquals("David Bowie", cue.performer)
        assertEquals("1234567890123", cue.catalog)
        assertEquals(CueRemark("GENRE", "Art Rock"), cue.remarks.first())
        assertEquals("Heroes.flac", cue.files.single().name)
        assertEquals(2, cue.files.single().tracks.size)

        val first = cue.files.single().tracks.first()
        assertEquals("Beauty and the Beast", first.title)
        assertEquals(listOf("PRE", "DCP"), first.flags)
        assertEquals("GBAYE7700010", first.isrc)
        assertEquals(2.seconds, first.start)

        val second = cue.files.single().tracks.last()
        assertEquals(CuePosition(0, 0, 15), second.pregap)
        assertEquals(16_212, second.indexes.single().position.totalFrames)
    }

    @Test
    fun parses_multiple_files_and_a_utf8_bom() {
        val cue = CueParser.parse(
            """
            ﻿TITLE "A double single"
            FILE "01 - Side A.wav" WAVE
              TRACK 01 AUDIO
                INDEX 01 00:00:00
            FILE "02 - Side B.wav" WAVE
              TRACK 02 AUDIO
                INDEX 01 00:00:00
            """.trimIndent(),
        )

        assertEquals(listOf("01 - Side A.wav", "02 - Side B.wav"), cue.files.map { it.name })
        assertEquals(listOf(1, 2), cue.files.flatMap { it.tracks }.map { it.number })
    }

    @Test
    fun reports_the_line_for_an_invalid_frame_position() {
        val error = assertFailsWith<CueParseException> {
            CueParser.parse(
                """
                FILE "disc.flac" WAVE
                  TRACK 01 AUDIO
                    INDEX 01 00:00:75
                """.trimIndent(),
            )
        }

        assertEquals(
            "Line 3: Invalid CUE position '00:00:75': Frames must be between 00 and 74",
            error.message,
        )
    }

    @Test
    fun track_without_a_file_is_rejected() {
        val error = assertFailsWith<CueParseException> {
            CueParser.parse("TRACK 01 AUDIO\n  INDEX 01 00:00:00")
        }

        assertEquals("Line 1: TRACK must follow FILE", error.message)
    }
}
