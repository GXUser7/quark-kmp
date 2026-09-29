package com.quark.data.local

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.COPY_ATTRIBUTES
import java.nio.file.StandardCopyOption.REPLACE_EXISTING

/**
 * Complete set of tags written by [TagWriter]. A null or blank text field is
 * omitted, matching the original exporter which rebuilt the tag from scratch.
 */
data class AudioTags(
    val title: String? = null,
    val artist: String? = null,
    val albumArtist: String? = null,
    val album: String? = null,
    val year: String? = null,
    val genre: String? = null,
    val comment: String? = null,
    val trackNumber: Int? = null,
    val trackTotal: Int? = null,
    val discNumber: Int? = null,
    val discTotal: Int? = null,
    val coverData: ByteArray? = null,
    val coverMime: String = "image/jpeg",
) {
    init {
        require(trackNumber == null || trackNumber > 0) { "Track number must be positive" }
        require(trackTotal == null || trackTotal > 0) { "Track total must be positive" }
        require(discNumber == null || discNumber > 0) { "Disc number must be positive" }
        require(discTotal == null || discTotal > 0) { "Disc total must be positive" }
        require(trackNumber != null || trackTotal == null) { "Track total requires a track number" }
        require(discNumber != null || discTotal == null) { "Disc total requires a disc number" }
    }
}

/** Writes tags through jaudiotagger without ever modifying the only copy. */
object TagWriter {

    /**
     * Replaces the supported metadata atomically. Work happens in a sibling
     * file so a failed parser, encoder, or process shutdown leaves [file]
     * byte-for-byte untouched.
     */
    fun write(file: File, tags: AudioTags) {
        require(file.isFile) { "Audio file does not exist: ${file.path}" }

        val target = file.toPath().toAbsolutePath()
        val extension = file.extension.takeIf(String::isNotBlank)
            ?: throw IllegalArgumentException("Audio file has no extension: ${file.path}")
        val temporary = Files.createTempFile(target.parent, ".quark-tags-", ".$extension")

        try {
            Files.copy(target, temporary, REPLACE_EXISTING, COPY_ATTRIBUTES)
            val audio = AudioFileIO.read(temporary.toFile())
            val tag = audio.createDefaultTag()
                ?: throw IllegalArgumentException("Cannot create tags for ${file.name}")
            audio.tag = tag
            tag.apply(tags)
            audio.commit()
            replaceAtomically(temporary.toFile(), file)
        } finally {
            Files.deleteIfExists(temporary)
        }
    }

    private fun Tag.apply(tags: AudioTags) {
        set(FieldKey.TITLE, tags.title)
        set(FieldKey.ARTIST, tags.artist)
        set(FieldKey.ALBUM_ARTIST, tags.albumArtist)
        set(FieldKey.ALBUM, tags.album)
        set(FieldKey.YEAR, tags.year)
        set(FieldKey.GENRE, tags.genre)
        set(FieldKey.COMMENT, tags.comment)
        set(FieldKey.TRACK, tags.trackNumber?.toString())
        set(FieldKey.TRACK_TOTAL, tags.trackTotal?.toString())
        set(FieldKey.DISC_NO, tags.discNumber?.toString())
        set(FieldKey.DISC_TOTAL, tags.discTotal?.toString())

        tags.coverData?.let { bytes ->
            val artwork = ArtworkFactory.getNew().apply {
                binaryData = bytes
                mimeType = tags.coverMime
                description = "Cover"
                pictureType = FRONT_COVER
                setImageFromData()
            }
            setField(artwork)
        }
    }

    private fun Tag.set(key: FieldKey, value: String?) {
        value?.takeIf(String::isNotBlank)?.let { setField(key, it) }
    }

    private fun replaceAtomically(source: File, destination: File) {
        try {
            Files.move(source.toPath(), destination.toPath(), REPLACE_EXISTING, ATOMIC_MOVE)
        } catch (_: AtomicMoveNotSupportedException) {
            Files.move(source.toPath(), destination.toPath(), REPLACE_EXISTING)
        }
    }

    private const val FRONT_COVER = 3
}
