package com.quark.network.vk

import com.quark.core.model.CoverType
import com.quark.core.model.ServiceTrack
import com.quark.core.model.Track
import com.quark.core.model.TrackSource
import com.quark.network.LenientJson
import com.quark.network.quark.QUARK_BASE_URL
import com.quark.network.quark.QuarkAccount
import com.quark.network.quark.bearer
import com.quark.network.quark.failure
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.http.isSuccess
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull

/** A VK audio record. Ids are `ownerId_audioId`, which is how VK names them. */
data class VkSong(
    val id: String,
    val ownerId: Long?,
    val title: String,
    val artist: String?,
    val durationSeconds: Int?,
    val url: String?,
    val coverUrl: String?,
    val album: String?,
    val accessKey: String?,
) {
    val isPlayable: Boolean get() = !url.isNullOrEmpty()

    fun toTrack(cacheRoot: String, separator: String = "/"): ServiceTrack = ServiceTrack(
        title = title.ifBlank { Track.UNKNOWN_TITLE },
        artists = artist?.split(ARTIST_SEPARATORS)?.map(String::trim)?.filter(String::isNotEmpty)
            ?.ifEmpty { null } ?: listOf(Track.UNKNOWN_ARTIST),
        albums = listOf(album?.takeIf(String::isNotBlank) ?: Track.UNKNOWN_ALBUM),
        filepath = listOf(cacheRoot, "audio_cache", "vk", "cisum_kv_krauq$id.mp3").joinToString(separator),
        coverType = if (coverUrl.isNullOrBlank()) CoverType.NoCover else CoverType.Url,
        cover = coverUrl.orEmpty(),
        source = TrackSource.Vk,
        id = id,
        durationMs = (durationSeconds ?: 0) * 1_000L,
        extras = buildMap {
            url?.let { put(EXTRA_URL, it) }
            accessKey?.let { put(EXTRA_ACCESS_KEY, it) }
        },
    )

    companion object {
        const val EXTRA_URL = "url"
        const val EXTRA_ACCESS_KEY = "access_key"
        private val ARTIST_SEPARATORS = Regex(",|\\sfeat\\.\\s|\\s&\\s")
    }
}

data class VkPlaylist(
    val id: String,
    val ownerId: Long?,
    val title: String,
    val description: String?,
    val count: Int?,
    val photo: String?,
    val accessKey: String?,
)

data class VkStatus(val connected: Boolean, val vkUserId: String?)

/**
 * VK Music through quark's backend (`services/vkmusic_services.dart`). The
 * backend holds the VK token — handed over by [QuarkAccount.saveVkToken] — so
 * every call here needs a signed-in quark account.
 *
 * Stream urls expire, and VK serves many of them as HLS playlists; the players
 * on both platforms read those.
 */
class VkClient(
    private val http: HttpClient,
    private val account: QuarkAccount,
    private val baseUrl: String = "$QUARK_BASE_URL/api/vk",
) {
    suspend fun status(): VkStatus {
        val response = account.authorized { token -> http.get("$baseUrl/token/status") { bearer(token) } }
        if (!response.status.isSuccess()) return VkStatus(false, null)
        val body = parse(response) ?: return VkStatus(false, null)
        return VkStatus(
            connected = (body["connected"] as? JsonPrimitive)?.booleanOrNull ?: false,
            vkUserId = (body["vk_user_id"] as? JsonPrimitive)?.contentOrNull,
        )
    }

    suspend fun disconnect() {
        val response = account.authorized { token -> http.delete("$baseUrl/token") { bearer(token) } }
        if (!response.status.isSuccess() && response.status != HttpStatusCode.NoContent) {
            throw response.failure("Could not disconnect VK")
        }
    }

    suspend fun mySongs(count: Int = 200): List<VkSong> =
        songs("$baseUrl/my/songs", mapOf("count" to count))

    suspend fun myPlaylists(count: Int = 50, offset: Int = 0): List<VkPlaylist> {
        val response = account.authorized { token ->
            http.get("$baseUrl/my/playlists") {
                bearer(token)
                parameter("count", count)
                parameter("offset", offset)
            }
        }
        if (response.status == HttpStatusCode.Forbidden) throw VkException("VK playlists are private or access is denied")
        if (!response.status.isSuccess()) throw response.failure("Could not load VK playlists")
        return (parse(response)?.get("playlists") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.toPlaylist() }
    }

    suspend fun playlistSongs(playlist: VkPlaylist, count: Int = 500): List<VkSong> =
        songs(
            "$baseUrl/my/playlist/${playlist.id}/songs",
            buildMap {
                put("count", count)
                playlist.accessKey?.let { put("access_key", it) }
            },
        )

    suspend fun search(query: String, count: Int = 30): List<VkSong> =
        songs("$baseUrl/search", mapOf("query" to query, "count" to count))

    suspend fun popular(count: Int = 50, offset: Int = 0): List<VkSong> =
        songs("$baseUrl/popular", mapOf("count" to count, "offset" to offset))

    private suspend fun songs(url: String, parameters: Map<String, Any>): List<VkSong> {
        val response = account.authorized { token ->
            http.get(url) {
                bearer(token)
                parameters.forEach { (name, value) -> parameter(name, value) }
            }
        }
        if (response.status == HttpStatusCode.Forbidden) throw VkException("VK audio is private or access is denied")
        if (!response.status.isSuccess()) throw response.failure("VK request failed")
        return (parse(response)?.get("songs") as? JsonArray).orEmpty()
            .mapNotNull { (it as? JsonObject)?.toSong() }
    }

    private suspend fun parse(response: HttpResponse): JsonObject? =
        runCatching { LenientJson.parseToJsonElement(response.bodyAsText()) as? JsonObject }.getOrNull()
}

class VkException(message: String) : RuntimeException(message)

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull

/** `id` arrives either as `owner_id` + `id` numbers or already joined as `"owner_audio"`. */
private fun JsonObject.compositeId(): String? {
    val raw = this["id"] as? JsonPrimitive ?: return null
    val owner = (this["owner_id"] as? JsonPrimitive)?.longOrNull
    val content = raw.contentOrNull ?: return null
    return if ('_' in content || owner == null) content else "${owner}_$content"
}

private fun JsonObject.toSong(): VkSong? = VkSong(
    id = compositeId() ?: return null,
    ownerId = (this["owner_id"] as? JsonPrimitive)?.longOrNull,
    title = string("title") ?: "Untitled",
    artist = string("artist"),
    durationSeconds = (this["duration"] as? JsonPrimitive)?.intOrNull,
    url = string("url"),
    coverUrl = string("cover_url") ?: string("photo"),
    album = string("album"),
    accessKey = string("access_key"),
)

private fun JsonObject.toPlaylist(): VkPlaylist? = VkPlaylist(
    id = compositeId() ?: return null,
    ownerId = (this["owner_id"] as? JsonPrimitive)?.longOrNull,
    title = string("title") ?: "Untitled",
    description = string("description"),
    count = (this["count"] as? JsonPrimitive)?.intOrNull,
    photo = string("photo") ?: string("cover_url"),
    accessKey = string("access_key"),
)
