package com.quark.data.local

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import java.nio.file.Files
import java.util.Base64
import kotlin.io.path.listDirectoryEntries
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TagWriterTest {

    @Test
    fun writes_text_numbers_and_cover_to_mp3() {
        val directory = Files.createTempDirectory("quark-tags-test")
        try {
            val file = directory.resolve("track.mp3").toFile().apply {
                writeBytes(Base64.getDecoder().decode(SILENT_MP3))
            }

            TagWriter.write(
                file,
                AudioTags(
                    title = "Heroes",
                    artist = "David Bowie",
                    albumArtist = "David Bowie",
                    album = "Heroes",
                    year = "1977",
                    genre = "Art Rock",
                    comment = "Quark export",
                    trackNumber = 3,
                    trackTotal = 10,
                    discNumber = 1,
                    discTotal = 2,
                    coverData = Base64.getDecoder().decode(ONE_PIXEL_PNG),
                    coverMime = "image/png",
                ),
            )

            val tag = AudioFileIO.read(file).tag
            assertEquals("Heroes", tag.getFirst(FieldKey.TITLE))
            assertEquals("David Bowie", tag.getFirst(FieldKey.ARTIST))
            assertEquals("Heroes", tag.getFirst(FieldKey.ALBUM))
            assertEquals("3", tag.getFirst(FieldKey.TRACK))
            assertEquals("10", tag.getFirst(FieldKey.TRACK_TOTAL))
            assertEquals("1", tag.getFirst(FieldKey.DISC_NO))
            assertEquals("2", tag.getFirst(FieldKey.DISC_TOTAL))
            assertContentEquals(Base64.getDecoder().decode(ONE_PIXEL_PNG), tag.firstArtwork.binaryData)
            assertTrue(directory.listDirectoryEntries().none { it.fileName.toString().startsWith(".quark-tags-") })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun a_second_write_replaces_the_supported_tag_set() {
        val directory = Files.createTempDirectory("quark-tags-replace")
        try {
            val file = directory.resolve("track.mp3").toFile().apply {
                writeBytes(Base64.getDecoder().decode(SILENT_MP3))
            }
            TagWriter.write(file, AudioTags(title = "Old", artist = "Old artist"))
            TagWriter.write(file, AudioTags(title = "New"))

            val tag = AudioFileIO.read(file).tag
            assertEquals("New", tag.getFirst(FieldKey.TITLE))
            assertEquals("", tag.getFirst(FieldKey.ARTIST))
            assertNull(tag.firstArtwork)
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun a_failed_write_does_not_replace_the_source() {
        val directory = Files.createTempDirectory("quark-tags-failure")
        try {
            val file = directory.resolve("not-audio.mp3").toFile().apply {
                writeText("not an mp3")
            }
            val before = file.readBytes()

            assertFails { TagWriter.write(file, AudioTags(title = "Nope")) }

            assertContentEquals(before, file.readBytes())
            assertEquals(listOf("not-audio.mp3"), directory.listDirectoryEntries().map { it.fileName.toString() })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    private companion object {
        /** 150 ms of mono silence, generated once with ffmpeg and kept tiny. */
        const val SILENT_MP3 =
            "//sQxAADwAABpAAAACAAADSAAAAETEFNRTMuMTAwVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVUxBTUUzLjEwMFX/+xLEKYPAAAGkAAAAIAAANIAAAARVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVX/+xDEU4PAAAGkAAAAIAAANIAAAARVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVf/7EsR9A8AAAaQAAAAgAAA0gAAABFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVf/7EMSnA8AAAaQAAAAgAAA0gAAABFVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV//sSxNCDwAABpAAAACAAADSAAAAEVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVV//sQxNYDwAABpAAAACAAADSAAAAEVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVVU="

        const val ONE_PIXEL_PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII="
    }
}
