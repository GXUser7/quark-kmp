package com.quark.network.yandex

import com.quark.network.base64
import com.quark.network.hmacSha256
import com.quark.network.md5Hex

/**
 * Request signatures for the unofficial Yandex Music api, ported from
 * `lib/overrided_libraries/yandex_music/lib/src/signs/signs.dart`.
 *
 * The keys below are the ones the Yandex clients ship; they are not secrets in
 * any meaningful sense, but the exact byte layout of each signed string is what
 * matters — the server rejects anything else, and the mistakes are silent.
 */
object YandexSignatures {

    private const val FILE_INFO_KEY = "7tvSmFbyf5hJnIHhCimDDD"
    private const val LYRICS_KEY = "p93jhgh689SBReK6ghtw62"
    private const val MP3_KEY = "XGRlBW9FXlekgbPrRHuSiA"

    /**
     * Signs a `/get-file-info` call. The signed string is the concatenation of
     * timestamp, track id, quality, every codec run together with no separator,
     * and the transport. The last base64 character is dropped, which is what
     * the api expects.
     */
    fun fileInfo(
        trackId: String,
        quality: String,
        codecs: List<String>,
        transport: String,
        timestamp: Long,
    ): String {
        val payload = "$timestamp$trackId$quality${codecs.joinToString("")}$transport"
        val digest = hmacSha256(FILE_INFO_KEY.encodeToByteArray(), payload.encodeToByteArray())
        return base64(digest).dropLast(1)
    }

    /** Signs a `/tracks/{id}/lyrics` call; the timestamp travels with it. */
    fun lyrics(trackId: String, timestamp: Long): LyricsSignature {
        val digest = hmacSha256(
            LYRICS_KEY.encodeToByteArray(),
            "$trackId$timestamp".encodeToByteArray(),
        )
        return LyricsSignature(timestamp = timestamp, signature = base64(digest))
    }

    /**
     * Signs the legacy mp3 storage url. [path] and [salt] come out of the
     * download-info xml as `<path>` and `<s>`; the leading slash of the path is
     * dropped before hashing.
     */
    fun mp3(path: String, salt: String): String =
        md5Hex((MP3_KEY + path.removePrefix("/") + salt).encodeToByteArray())
}

data class LyricsSignature(val timestamp: Long, val signature: String)
