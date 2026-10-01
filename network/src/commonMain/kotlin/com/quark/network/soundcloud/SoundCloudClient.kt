package com.quark.network.soundcloud

import com.quark.core.model.CoverType
import com.quark.core.model.ServiceTrack
import com.quark.core.model.Track
import com.quark.core.model.TrackSource
import com.quark.network.LenientJson
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.longOrNull

data class ScTrack(
    val id: Long,
    val title: String,
    val artist: String?,
    val artworkUrl: String?,
    val durationMs: Long,
    val fullDurationMs: Long,
    val genre: String?,
    val permalinkUrl: String?,
) {
    /** Go+ tracks come as a 30 second preview unless the account can play them. */
    val isFullyPlayable: Boolean get() = fullDurationMs <= 0 || durationMs >= fullDurationMs

    fun toTrack(cacheRoot: String, separator: String = "/"): ServiceTrack = ServiceTrack(
        title = title.ifBlank { Track.UNKNOWN_TITLE },
        artists = listOf(artist?.takeIf(String::isNotBlank) ?: Track.UNKNOWN_ARTIST),
        albums = listOf(Track.UNKNOWN_ALBUM),
        filepath = listOf(cacheRoot, "audio_cache", "soundcloud", "cisum_duolcdnuos_krauq$id.mp3").joinToString(separator),
        coverType = if (artworkUrl.isNullOrBlank()) CoverType.NoCover else CoverType.Url,
        cover = artworkUrl.orEmpty(),
        source = TrackSource.SoundCloud,
        id = id.toString(),
        durationMs = fullDurationMs.takeIf { it > 0 } ?: durationMs,
        extras = buildMap { permalinkUrl?.let { put("permalink", it) } },
    )
}

data class ScPlaylist(
    val id: Long,
    val title: String,
    val artworkUrl: String?,
    val permalinkUrl: String?,
    val trackCount: Int,
    val isAlbum: Boolean,
    val owner: String?,
)

data class ScUser(val id: Long, val username: String, val avatarUrl: String?, val permalinkUrl: String?)

/**
 * SoundCloud's own web api (`api-v2.soundcloud.com`), which the Dart build
 * reached through `soundcloud_explode_dart` (`services/soundcloud_services.dart`).
 *
 * The api wants the `client_id` of SoundCloud's web player, which rotates; it
 * is read out of the site's scripts the way the web player itself loads it,
 * with the id the Dart build hard-coded as the fallback. A user's OAuth token,
 * when there is one, unlocks full-length Go+ streams.
 */
class SoundCloudClient(
    private val http: HttpClient,
    private val oauthToken: () -> String? = { null },
) {
    private val clientIdLock = Mutex()
    private var clientId: String? = null

    suspend fun searchTracks(query: String, limit: Int = 30, offset: Int = 0): List<ScTrack> =
        collection("/search/tracks", mapOf("q" to query, "limit" to limit, "offset" to offset))
            .mapNotNull { it.toScTrack() }

    suspend fun searchPlaylists(query: String, limit: Int = 30): List<ScPlaylist> =
        collection("/search/playlists", mapOf("q" to query, "limit" to limit))
            .mapNotNull { it.toScPlaylist() }

    suspend fun track(id: Long): ScTrack? = (get("/tracks/$id") as? JsonObject)?.toScTrack()

    /** Tracks of a playlist or album, filling in the stubs the api leaves unexpanded. */
    suspend fun playlistTracks(playlistId: Long): List<ScTrack> {
        val playlist = get("/playlists/$playlistId") as? JsonObject ?: return emptyList()
        val entries = (playlist["tracks"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
        val full = entries.associate { entry ->
            val id = (entry["id"] as? JsonPrimitive)?.longOrNull ?: 0L
            id to entry.takeIf { it["title"] != null }?.toScTrack()
        }
        val missing = full.filterValues { it == null }.keys.filter { it != 0L }
        val fetched = missing.chunked(BATCH).flatMap { ids ->
            (get("/tracks", mapOf("ids" to ids.joinToString(","))) as? JsonArray).orEmpty()
                .mapNotNull { (it as? JsonObject)?.toScTrack() }
        }.associateBy(ScTrack::id)
        return entries.mapNotNull { entry ->
            val id = (entry["id"] as? JsonPrimitive)?.longOrNull ?: return@mapNotNull null
            full[id] ?: fetched[id]
        }
    }

    /** Resolves a profile, track or playlist link to what it points at. */
    suspend fun resolve(url: String): JsonObject? = get("/resolve", mapOf("url" to url.trim())) as? JsonObject

    suspend fun resolveUser(profileUrl: String): ScUser? {
        val body = resolve(profileUrl.trim().trimEnd('/')) ?: return null
        if ((body["kind"] as? JsonPrimitive)?.contentOrNull != "user") return null
        return ScUser(
            id = (body["id"] as? JsonPrimitive)?.longOrNull ?: return null,
            username = body.string("username") ?: "Unknown",
            avatarUrl = body.string("avatar_url"),
            permalinkUrl = body.string("permalink_url"),
        )
    }

    suspend fun userPlaylists(userId: Long, limit: Int = 50): List<ScPlaylist> =
        collection("/users/$userId/playlists", mapOf("limit" to limit)).mapNotNull { it.toScPlaylist() }

    suspend fun userTracks(userId: Long, limit: Int = 50): List<ScTrack> =
        collection("/users/$userId/tracks", mapOf("limit" to limit)).mapNotNull { it.toScTrack() }

    suspend fun userLikes(userId: Long, limit: Int = 100): List<ScTrack> =
        collection("/users/$userId/track_likes", mapOf("limit" to limit))
            .mapNotNull { (it["track"] as? JsonObject)?.toScTrack() }

    /**
     * A playable url for [trackId]: progressive mp3 first, then progressive
     * aac, then HLS — the order `_pickBestTranscoding` used.
     */
    suspend fun streamUrl(trackId: Long): String? {
        val track = get("/tracks/$trackId") as? JsonObject ?: return null
        val transcodings = ((track["media"] as? JsonObject)?.get("transcodings") as? JsonArray).orEmpty()
            .mapNotNull { it as? JsonObject }
        if (transcodings.isEmpty()) return null
        val chosen = pick(transcodings)
        val url = chosen.string("url") ?: return null
        val authorization = track.string("track_authorization")
        val stream = get(url, buildMap { authorization?.let { put("track_authorization", it) } }) as? JsonObject
        return stream?.string("url")
    }

    private fun pick(transcodings: List<JsonObject>): JsonObject {
        val preferences = listOf("progressive" to "mp3", "progressive" to "aac", "hls" to "mp3", "hls" to "aac")
        for ((protocol, preset) in preferences) {
            transcodings.firstOrNull { transcoding ->
                val format = transcoding["format"] as? JsonObject
                format?.string("protocol") == protocol &&
                    transcoding.string("preset").orEmpty().contains(preset) &&
                    (transcoding["snipped"] as? JsonPrimitive)?.booleanOrNull != true
            }?.let { return it }
        }
        return transcodings.first()
    }

    // --- Plumbing ----------------------------------------------------------------

    private suspend fun collection(path: String, parameters: Map<String, Any>): List<JsonObject> {
        val body = get(path, parameters + ("linked_partitioning" to 1)) as? JsonObject ?: return emptyList()
        return (body["collection"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }
    }

    private suspend fun get(path: String, parameters: Map<String, Any> = emptyMap()): JsonElement? {
        var response = request(path, parameters, currentClientId())
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            // The id may have rotated since it was read; read it again once.
            response = request(path, parameters, currentClientId(refresh = true))
        }
        if (!response.status.isSuccess()) {
            if (response.status == HttpStatusCode.NotFound) return null
            throw SoundCloudException("SoundCloud answered ${response.status.value} for $path")
        }
        return runCatching { LenientJson.parseToJsonElement(response.bodyAsText()) }.getOrNull()
    }

    private suspend fun request(path: String, parameters: Map<String, Any>, id: String): HttpResponse =
        http.get(if (path.startsWith("http")) path else API + path) {
            parameter("client_id", id)
            parameters.forEach { (name, value) -> parameter(name, value) }
            header("Origin", "https://soundcloud.com")
            header("Referer", "https://soundcloud.com/")
            header("Accept", "application/json")
            header("User-Agent", USER_AGENT)
            oauthToken()?.takeIf(String::isNotBlank)?.let { token ->
                header("Authorization", if (token.startsWith("OAuth ")) token else "OAuth $token")
            }
        }

    private suspend fun currentClientId(refresh: Boolean = false): String = clientIdLock.withLock {
        if (!refresh) clientId?.let { return it }
        val found = runCatching { discoverClientId() }.getOrNull()
        (found ?: FALLBACK_CLIENT_ID).also { clientId = it }
    }

    /** Reads the client id out of the web player's scripts, newest bundle first. */
    private suspend fun discoverClientId(): String? {
        val page = http.get("https://soundcloud.com/") { header("User-Agent", USER_AGENT) }.bodyAsText()
        val scripts = SCRIPT.findAll(page).map { it.groupValues[1] }.toList().asReversed()
        for (script in scripts) {
            val source = runCatching { http.get(script) { header("User-Agent", USER_AGENT) }.bodyAsText() }
                .getOrNull() ?: continue
            CLIENT_ID.find(source)?.groupValues?.get(1)?.let { return it }
        }
        return null
    }

    private companion object {
        const val API = "https://api-v2.soundcloud.com"
        const val BATCH = 50

        /** What the Dart build hard-coded; used when the scripts cannot be read. */
        const val FALLBACK_CLIENT_ID = "KKzJxmw11tYpCs6T24P4uUYhqmjalG6M"

        const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"

        val SCRIPT = Regex("<script[^>]+src=\"(https://a-v2\\.sndcdn\\.com/assets/[^\"]+\\.js)\"")
        val CLIENT_ID = Regex("client_id\\s*[:=]\\s*\"?([a-zA-Z0-9]{32})")
    }
}

class SoundCloudException(message: String) : RuntimeException(message)

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

/** Artwork links name their size; `large` is 100 px, too small for the player. */
private fun artwork(url: String?): String? = url?.replace("-large.", "-t500x500.")

private fun JsonObject.toScTrack(): ScTrack? {
    val id = (this["id"] as? JsonPrimitive)?.longOrNull ?: return null
    val user = this["user"] as? JsonObject
    return ScTrack(
        id = id,
        title = string("title") ?: "Unknown",
        artist = user?.string("username"),
        artworkUrl = artwork(string("artwork_url") ?: user?.string("avatar_url")),
        durationMs = (this["duration"] as? JsonPrimitive)?.longOrNull ?: 0L,
        fullDurationMs = (this["full_duration"] as? JsonPrimitive)?.longOrNull ?: 0L,
        genre = string("genre"),
        permalinkUrl = string("permalink_url"),
    )
}

private fun JsonObject.toScPlaylist(): ScPlaylist? {
    val id = (this["id"] as? JsonPrimitive)?.longOrNull ?: return null
    val tracks = this["tracks"] as? JsonArray
    val firstArtwork = (tracks?.firstOrNull() as? JsonObject)?.string("artwork_url")
    return ScPlaylist(
        id = id,
        title = string("title") ?: "Unknown playlist",
        artworkUrl = artwork(string("artwork_url") ?: firstArtwork),
        permalinkUrl = string("permalink_url"),
        trackCount = (this["track_count"] as? JsonPrimitive)?.longOrNull?.toInt() ?: tracks?.size ?: 0,
        isAlbum = (this["is_album"] as? JsonPrimitive)?.booleanOrNull ?: false,
        owner = (this["user"] as? JsonObject)?.string("username"),
    )
}
