package com.quark.core.cue

import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/** A CUE document. File and track order is significant and is preserved. */
data class CueSheet(
    val title: String? = null,
    val performer: String? = null,
    val songwriter: String? = null,
    val catalog: String? = null,
    val cdTextFile: String? = null,
    val files: List<CueFile> = emptyList(),
    val remarks: List<CueRemark> = emptyList(),
)

data class CueFile(
    val name: String,
    val type: String,
    val tracks: List<CueTrack>,
)

data class CueTrack(
    val number: Int,
    val type: String,
    val title: String? = null,
    val performer: String? = null,
    val songwriter: String? = null,
    val isrc: String? = null,
    val indexes: List<CueIndex> = emptyList(),
    val pregap: CuePosition? = null,
    val postgap: CuePosition? = null,
    val flags: List<String> = emptyList(),
    val remarks: List<CueRemark> = emptyList(),
) {
    /** INDEX 01 is where the audible part normally begins. */
    val start: Duration? get() = indexes.firstOrNull { it.number == 1 }?.position?.duration
}

data class CueIndex(val number: Int, val position: CuePosition)

/** CUE uses CD frames: 75 frames make one second. */
data class CuePosition(val minutes: Int, val seconds: Int, val frames: Int) {
    init {
        require(minutes >= 0) { "Minutes cannot be negative" }
        require(seconds in 0..59) { "Seconds must be between 00 and 59" }
        require(frames in 0..74) { "Frames must be between 00 and 74" }
    }

    val totalFrames: Long get() = (minutes * 60L + seconds) * FRAMES_PER_SECOND + frames
    val duration: Duration get() = (totalFrames * 1_000L / FRAMES_PER_SECOND).milliseconds

    private companion object {
        const val FRAMES_PER_SECOND = 75L
    }
}

data class CueRemark(val name: String, val value: String)

class CueParseException(message: String) : IllegalArgumentException(message)

/**
 * Parser for the commands used by ordinary single- and multi-file CUE sheets.
 * Unknown commands are ignored because authoring tools commonly add private
 * extensions; known malformed commands fail with a line number instead of
 * quietly shifting every following track.
 */
object CueParser {

    fun parse(text: String): CueSheet {
        val sheet = SheetBuilder()
        var file: FileBuilder? = null
        var track: TrackBuilder? = null

        for ((zeroBasedLine, rawLine) in text.lineSequence().withIndex()) {
            val lineNumber = zeroBasedLine + 1
            val line = rawLine.removePrefix("\uFEFF").trim()
            if (line.isEmpty()) continue

            val command = line.substringBefore(' ').substringBefore('\t').uppercase()
            val argument = line.drop(command.length).trim()

            fun fail(message: String): Nothing =
                throw CueParseException("Line $lineNumber: $message")

            when (command) {
                "REM" -> {
                    val (name, value) = splitFirst(argument)
                        ?: fail("REM requires a name and value")
                    val remark = CueRemark(name.uppercase(), unquote(value))
                    if (track == null) sheet.remarks += remark else track.remarks += remark
                }

                "CATALOG" -> sheet.catalog = requiredText(argument, "CATALOG", ::fail)
                "CDTEXTFILE" -> sheet.cdTextFile = requiredText(argument, "CDTEXTFILE", ::fail)
                "TITLE" -> {
                    val value = requiredText(argument, "TITLE", ::fail)
                    if (track == null) sheet.title = value else track.title = value
                }

                "PERFORMER" -> {
                    val value = requiredText(argument, "PERFORMER", ::fail)
                    if (track == null) sheet.performer = value else track.performer = value
                }

                "SONGWRITER" -> {
                    val value = requiredText(argument, "SONGWRITER", ::fail)
                    if (track == null) sheet.songwriter = value else track.songwriter = value
                }

                "FILE" -> {
                    val parts = tokenize(argument)
                    if (parts.size != 2) fail("FILE requires a quoted path and type")
                    track = null
                    file = FileBuilder(parts[0], parts[1].uppercase()).also(sheet.files::add)
                }

                "TRACK" -> {
                    val currentFile = file ?: fail("TRACK must follow FILE")
                    val parts = tokenize(argument)
                    if (parts.size != 2) fail("TRACK requires a number and type")
                    val number = parts[0].toIntOrNull() ?: fail("Invalid track number '${parts[0]}'")
                    if (number !in 1..99) fail("Track number must be between 01 and 99")
                    if (currentFile.tracks.any { it.number == number }) {
                        fail("Duplicate track number ${parts[0]}")
                    }
                    track = TrackBuilder(number, parts[1].uppercase()).also(currentFile.tracks::add)
                }

                "INDEX" -> {
                    val currentTrack = track ?: fail("INDEX must follow TRACK")
                    val parts = tokenize(argument)
                    if (parts.size != 2) fail("INDEX requires a number and MM:SS:FF position")
                    val number = parts[0].toIntOrNull() ?: fail("Invalid index number '${parts[0]}'")
                    if (number !in 0..99) fail("Index number must be between 00 and 99")
                    if (currentTrack.indexes.any { it.number == number }) {
                        fail("Duplicate index number ${parts[0]}")
                    }
                    currentTrack.indexes += CueIndex(number, parsePosition(parts[1], ::fail))
                }

                "PREGAP" -> {
                    val currentTrack = track ?: fail("PREGAP must follow TRACK")
                    currentTrack.pregap = parsePosition(argument, ::fail)
                }

                "POSTGAP" -> {
                    val currentTrack = track ?: fail("POSTGAP must follow TRACK")
                    currentTrack.postgap = parsePosition(argument, ::fail)
                }

                "FLAGS" -> {
                    val currentTrack = track ?: fail("FLAGS must follow TRACK")
                    currentTrack.flags = tokenize(argument).map(String::uppercase)
                }

                "ISRC" -> {
                    val currentTrack = track ?: fail("ISRC must follow TRACK")
                    currentTrack.isrc = requiredText(argument, "ISRC", ::fail)
                }
            }
        }

        return sheet.build()
    }

    private fun parsePosition(value: String, fail: (String) -> Nothing): CuePosition {
        val parts = value.trim().split(':')
        if (parts.size != 3) fail("Invalid CUE position '$value'")
        val numbers = parts.map { it.toIntOrNull() ?: fail("Invalid CUE position '$value'") }
        return try {
            CuePosition(numbers[0], numbers[1], numbers[2])
        } catch (error: IllegalArgumentException) {
            fail("Invalid CUE position '$value': ${error.message}")
        }
    }

    private fun requiredText(
        argument: String,
        command: String,
        fail: (String) -> Nothing,
    ): String {
        if (argument.isBlank()) fail("$command requires a value")
        return unquote(argument)
    }

    /** Splits one leading token from a free-form tail, as REM requires. */
    private fun splitFirst(value: String): Pair<String, String>? {
        val index = value.indexOfFirst(Char::isWhitespace)
        if (index <= 0) return null
        val first = value.take(index)
        val rest = value.drop(index).trim()
        if (rest.isEmpty()) return null
        return first to rest
    }

    private fun tokenize(value: String): List<String> {
        val result = mutableListOf<String>()
        val token = StringBuilder()
        var quoted = false
        var escaped = false

        fun finish() {
            if (token.isNotEmpty()) {
                result += token.toString()
                token.clear()
            }
        }

        for (character in value.trim()) {
            when {
                escaped -> {
                    token.append(character)
                    escaped = false
                }
                character == '\\' && quoted -> escaped = true
                character == '"' -> quoted = !quoted
                character.isWhitespace() && !quoted -> finish()
                else -> token.append(character)
            }
        }
        if (escaped) token.append('\\')
        finish()
        return result
    }

    private fun unquote(value: String): String {
        val trimmed = value.trim()
        if (trimmed.length < 2 || trimmed.first() != '"' || trimmed.last() != '"') return trimmed
        return buildString {
            var escaped = false
            for (character in trimmed.substring(1, trimmed.lastIndex)) {
                when {
                    escaped -> {
                        append(character)
                        escaped = false
                    }
                    character == '\\' -> escaped = true
                    else -> append(character)
                }
            }
            if (escaped) append('\\')
        }
    }

    private class SheetBuilder {
        var title: String? = null
        var performer: String? = null
        var songwriter: String? = null
        var catalog: String? = null
        var cdTextFile: String? = null
        val files = mutableListOf<FileBuilder>()
        val remarks = mutableListOf<CueRemark>()

        fun build() = CueSheet(
            title = title,
            performer = performer,
            songwriter = songwriter,
            catalog = catalog,
            cdTextFile = cdTextFile,
            files = files.map(FileBuilder::build),
            remarks = remarks.toList(),
        )
    }

    private class FileBuilder(val name: String, val type: String) {
        val tracks = mutableListOf<TrackBuilder>()
        fun build() = CueFile(name, type, tracks.map(TrackBuilder::build))
    }

    private class TrackBuilder(val number: Int, val type: String) {
        var title: String? = null
        var performer: String? = null
        var songwriter: String? = null
        var isrc: String? = null
        val indexes = mutableListOf<CueIndex>()
        var pregap: CuePosition? = null
        var postgap: CuePosition? = null
        var flags: List<String> = emptyList()
        val remarks = mutableListOf<CueRemark>()

        fun build() = CueTrack(
            number = number,
            type = type,
            title = title,
            performer = performer,
            songwriter = songwriter,
            isrc = isrc,
            indexes = indexes.toList(),
            pregap = pregap,
            postgap = postgap,
            flags = flags,
            remarks = remarks.toList(),
        )
    }
}
