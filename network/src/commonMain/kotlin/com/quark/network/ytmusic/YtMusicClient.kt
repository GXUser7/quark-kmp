package com.quark.network.ytmusic

import com.quark.core.model.CoverType
import com.quark.core.model.Track
import com.quark.core.model.YtMusicTrack
import com.quark.network.LenientJson
import com.quark.network.quark.QUARK_BASE_URL
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.put

/** A video as the backend describes it: yt-dlp's fields, the ones the player reads. */
data class YtVideo(
    val id: String,
    val title: String,
    val channel: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val durationSeconds: Int? = null,
    val thumbnail: String? = null,
    val streamUrl: String? = null,
    val availability: String? = null,
) {
    val isPlayable: Boolean
        get() = availability == null || availability == "public" || availability == "unlisted"

    fun toTrack(cacheRoot: String, separator: String = "/"): YtMusicTrack = YtMusicTrack(
        title = title.ifBlank { Track.UNKNOWN_TITLE },
        artists = listOf(artist?.takeIf(String::isNotBlank) ?: channel?.takeIf(String::isNotBlank) ?: Track.UNKNOWN_ARTIST),
        albums = listOf(album?.takeIf(String::isNotBlank) ?: Track.UNKNOWN_ALBUM),
        filepath = youtubeCachePath(cacheRoot, id, separator),
        coverType = if (thumbnail.isNullOrBlank()) CoverType.NoCover else CoverType.Url,
        cover = thumbnail.orEmpty(),
        videoId = id,
        durationMs = (durationSeconds ?: 0) * 1_000L,
        streamUrl = streamUrl,
    )
}

/** A playlist on the user's YouTube account, as the cookie-authorised listing returns it. */
data class YtPlaylist(val id: String, val title: String, val url: String, val thumbnail: String?)

data class YtPlaylistContents(val id: String, val title: String?, val videos: List<YtVideo>)

/** Where a cached YouTube track lives, by the same scheme the Yandex cache uses. */
fun youtubeCachePath(cacheRoot: String, videoId: String, separator: String = "/"): String =
    listOf(cacheRoot, "audio_cache", "youtube", "cisum_ebutuoy_krauq$videoId.m4a").joinToString(separator)

/**
 * YouTube Music through quark's backend, which runs yt-dlp on the server and
 * hands back direct stream urls (`services/ytmusic_services.dart`).
 *
 * Search and song lookups are GET requests with a json body — unusual, and
 * OkHttp refuses to send it, so this client runs on Ktor's own CIO engine. The
 * playlist calls upload the user's exported `cookies.txt`, which is how the
 * backend reaches private playlists without a Google login.
 */
class YtMusicClient(
    private val baseUrl: String = "$QUARK_BASE_URL/api/yt",
    private val http: HttpClient = HttpClient(CIO) {
        install(HttpTimeout) {
            connectTimeoutMillis = 10_000
            requestTimeoutMillis = 60_000
        }
        expectSuccess = false
    },
) {
    suspend fun search(query: String, limit: Int = 20): List<YtVideo> {
        val response = http.get("$baseUrl/search") {
            setBody(jsonBody { put("query", query); put("max_results", limit) })
        }
        if (!response.status.isSuccess()) return emptyList()
        val items = parse(response) as? JsonArray ?: return emptyList()
        return items.mapNotNull { (it as? JsonObject)?.toVideo() }
    }

    /** The video with a fresh stream url; audio only unless [format] says otherwise. */
    suspend fun song(
        videoId: String,
        format: String = "bestaudio[ext=m4a]/bestaudio[acodec!=opus]/bestaudio",
    ): YtVideo {
        val response = http.get("$baseUrl/song") {
            setBody(jsonBody { put("video_id", videoId); put("format", format) })
        }
        if (!response.status.isSuccess()) {
            throw YtMusicException("Could not resolve $videoId (${response.status.value})")
        }
        val body = parse(response) as? JsonObject ?: throw YtMusicException("Unexpected response for $videoId")
        return body.toVideo() ?: throw YtMusicException("No video in the response for $videoId")
    }

    /** The playlists of the account the cookies belong to. */
    suspend fun playlists(cookies: String, fileName: String = "cookies.txt"): List<YtPlaylist> {
        val response = http.post("$baseUrl/playlists") {
            setBody(MultiPartFormDataContent(formData { cookieFile(cookies, fileName) }))
        }
        if (!response.status.isSuccess()) {
            throw YtMusicException("Could not load playlists (${response.status.value})")
        }
        val body = parse(response) as? JsonObject ?: return emptyList()
        // Each entry of `playlist` wraps one "track", which is really a playlist.
        return (body["playlist"] as? JsonArray).orEmpty().mapNotNull { entry ->
            val first = ((entry as? JsonObject)?.get("tracks") as? JsonArray)?.firstOrNull() as? JsonObject
                ?: return@mapNotNull null
            val thumbnails = first["thumbnails"] as? JsonArray
            YtPlaylist(
                id = first.string("id") ?: return@mapNotNull null,
                title = first.string("title") ?: "Untitled",
                url = first.string("url").orEmpty(),
                thumbnail = (thumbnails?.lastOrNull() as? JsonObject)?.string("url") ?: first.string("thumbnail"),
            )
        }
    }

    /** The videos of one playlist, read with the same cookies. */
    suspend fun playlist(
        cookies: String,
        playlistId: String,
        format: String = "ba",
        fileName: String = "cookies.txt",
    ): YtPlaylistContents {
        val response = http.post("$baseUrl/playlistAuth") {
            setBody(
                MultiPartFormDataContent(
                    formData {
                        append("playlist_id", playlistId)
                        append("format", format)
                        cookieFile(cookies, fileName)
                    }
                )
            )
        }
        if (!response.status.isSuccess()) {
            throw YtMusicException("Could not load the playlist (${response.status.value})")
        }
        val body = parse(response) as? JsonObject ?: throw YtMusicException("Unexpected playlist response")
        val videos = (body["playlist"] as? JsonArray).orEmpty().flatMap { entry ->
            ((entry as? JsonObject)?.get("tracks") as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.toVideo() }
        }
        return YtPlaylistContents(
            id = body.string("playlist_id") ?: playlistId,
            title = body.string("title"),
            videos = videos,
        )
    }

    private fun io.ktor.client.request.forms.FormBuilder.cookieFile(cookies: String, fileName: String) {
        append(
            "cookies",
            cookies.encodeToByteArray(),
            Headers.build {
                append(HttpHeaders.ContentType, "text/plain")
                append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"")
            },
        )
    }

    private fun jsonBody(block: kotlinx.serialization.json.JsonObjectBuilder.() -> Unit) =
        TextContent(buildJsonObject(block).toString(), ContentType.Application.Json)

    private suspend fun parse(response: HttpResponse): JsonElement? =
        runCatching { LenientJson.parseToJsonElement(response.bodyAsText()) }.getOrNull()
}

class YtMusicException(message: String) : RuntimeException(message)

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

private fun JsonObject.toVideo(): YtVideo? {
    val id = string("id")?.takeIf(String::isNotBlank) ?: return null
    val thumbnails = this["thumbnails"] as? JsonArray
    // The widest thumbnail near 480 px, as `getBestThumbnail` picked it.
    val bestThumbnail = thumbnails
        ?.mapNotNull { it as? JsonObject }
        ?.filter { (it["width"] as? JsonPrimitive)?.doubleOrNull != null }
        ?.minByOrNull { kotlin.math.abs(((it["width"] as JsonPrimitive).doubleOrNull ?: 0.0) - 480) }
        ?.string("url")
    return YtVideo(
        id = id,
        title = string("title") ?: "Untitled",
        channel = string("channel") ?: string("uploader"),
        artist = string("artist"),
        album = string("album"),
        durationSeconds = (this["duration"] as? JsonPrimitive)?.doubleOrNull?.toInt(),
        thumbnail = bestThumbnail ?: string("thumbnail"),
        // `url` is the watch page on search results and the media itself on a
        // song lookup; only the latter is worth handing to the player.
        streamUrl = string("streamUrl") ?: string("url")?.takeIf {
            it.startsWith("http") && "youtube.com/watch" !in it && "youtu.be/" !in it
        },
        availability = string("availability"),
    )
}
