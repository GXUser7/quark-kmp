package com.quark.network.yandex

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Expected values were produced independently with Python's hmac/hashlib, so
 * these catch a wrong key, a reordered payload or a missing separator rather
 * than just pinning whatever this code happens to return.
 */
class YandexSignaturesTest {

    private val codecs = listOf(
        "flac", "aac", "he-aac", "mp3", "flac-mp4", "aac-mp4", "he-aac-mp4",
    )

    @Test
    fun file_info_signature_matches_the_reference() {
        val signature = YandexSignatures.fileInfo(
            trackId = "47127",
            quality = "lossless",
            codecs = codecs,
            transport = "raw",
            timestamp = 1726000000L,
        )

        assertEquals("ObgHhMjr8yv7Ub37icnzL5k+vmbfZQ+nYS/gp1xQDgM", signature)
    }

    @Test
    fun lyrics_signature_matches_the_reference() {
        val signature = YandexSignatures.lyrics(trackId = "47127", timestamp = 1726000000L)

        assertEquals(1726000000L, signature.timestamp)
        assertEquals("+uNjqu3w+CdWNB7mpy+ke38JgdZNhXYNJCOIhbUFnEM=", signature.signature)
    }

    @Test
    fun mp3_signature_drops_the_leading_slash_of_the_path() {
        val signature = YandexSignatures.mp3(
            path = "/get-mp3/abc/123456/rmusic/file.mp3",
            salt = "deadbeef",
        )

        assertEquals("816c4c7ed88eb40e47acf5a8489b2274", signature)
    }
}
