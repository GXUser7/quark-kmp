package com.quark.data.local

import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Track
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/** Audio the player will try to open. Anything else found in a folder is ignored. */
val AUDIO_EXTENSIONS: Set<String> = setOf(
    "flac", "mp3", "m4a", "aac", "alac", "ogg", "oga", "opus", "wav", "wv",
    "aiff", "aif", "ape", "mpc", "dsf", "dff", "wma",
)

/** Cover art files that sit next to a track, in the order they are preferred. */
private val COVER_NAMES = listOf("cover", "folder", "front", "album", "artwork")
private val COVER_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")

/**
 * Reads tags off a file, replacing `audio_metadata_reader`.
 *
 * A file whose tags cannot be read is still returned as a track named after the
 * file, which is what the Dart build did: the player can play plenty of things
 * jaudiotagger will not parse, and refusing to list them would hide music the
 * user can hear.
 */
object TagReader {

    init {
        // jaudiotagger narrates every field it does not recognise at INFO.
        Logger.getLogger("org.jaudiotagger").level = Level.SEVERE
    }

    fun read(file: File): LocalTrack {
        val fallback = fallbackTrack(file)
        if (!file.isFile) return fallback

        return try {
            val audio = AudioFileIO.read(file)
            val tag = audio.tag
            val header = audio.audioHeader

            val hasEmbeddedArt = tag?.firstArtwork != null
            val externalCover = if (hasEmbeddedArt) null else findCoverBeside(file)

            LocalTrack(
                title = tag?.firstOrNull(FieldKey.TITLE) ?: file.nameWithoutExtension,
                artists = tag?.artists() ?: listOf(Track.UNKNOWN_ARTIST),
                albums = listOf(tag?.firstOrNull(FieldKey.ALBUM) ?: Track.UNKNOWN_ALBUM),
                filepath = file.absolutePath,
                coverType = when {
                    hasEmbeddedArt -> CoverType.BuiltIn
                    externalCover != null -> CoverType.ExternalFile
                    else -> CoverType.NoCover
                },
                cover = externalCover?.absolutePath ?: file.absolutePath,
                durationMs = header.duration().inWholeMilliseconds,
            )
        } catch (e: Exception) {
            fallback
        } catch (e: OutOfMemoryError) {
            // A corrupt header can make jaudiotagger allocate wildly.
            fallback
        }
    }

    /** Embedded artwork bytes, or null when the file carries none. */
    fun readArtwork(file: File): ByteArray? = try {
        AudioFileIO.read(file).tag?.firstArtwork?.binaryData
    } catch (e: Exception) {
        null
    }

    private fun fallbackTrack(file: File) = LocalTrack(
        title = file.nameWithoutExtension.ifBlank { file.name },
        artists = listOf(Track.UNKNOWN_ARTIST),
        albums = listOf(Track.UNKNOWN_ALBUM),
        filepath = file.absolutePath,
        coverType = findCoverBeside(file)?.let { CoverType.ExternalFile } ?: CoverType.NoCover,
        cover = findCoverBeside(file)?.absolutePath ?: file.absolutePath,
    )

    /**
     * Tags may hold several artists, or one string with all of them in it.
     * Both happen in real libraries, so both are handled.
     */
    private fun org.jaudiotagger.tag.Tag.artists(): List<String> {
        val all = getAll(FieldKey.ARTIST).orEmpty().filter(String::isNotBlank)
        val split = when {
            all.size > 1 -> all
            all.size == 1 -> all.first().split(Regex("[,;/]|\\sfeat\\.\\s|\\s&\\s"))
            else -> emptyList()
        }
        return split.map(String::trim)
            .filter(String::isNotEmpty)
            .ifEmpty { listOf(Track.UNKNOWN_ARTIST) }
    }

    private fun org.jaudiotagger.tag.Tag.firstOrNull(key: FieldKey): String? =
        runCatching { getFirst(key) }.getOrNull()?.takeIf(String::isNotBlank)

    private fun org.jaudiotagger.audio.AudioHeader.duration(): Duration =
        runCatching { trackLength.seconds }.getOrDefault(Duration.ZERO)

    private fun findCoverBeside(file: File): File? {
        val folder = file.parentFile ?: return null
        for (name in COVER_NAMES) {
            for (extension in COVER_EXTENSIONS) {
                val candidate = File(folder, "$name.$extension")
                if (candidate.isFile) return candidate
            }
        }
        return null
    }
}
