package com.quark.network.quark

import com.quark.network.LenientJson
import io.ktor.client.HttpClient
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.statement.bodyAsText
import io.ktor.http.isSuccess
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

/** A playlist entry as the cloud keeps it: the `known_tracks` row, flattened. */
@Serializable
data class CloudTrack(
    val path: String = "",
    val title: String = "",
    /** Comma-joined, as in the local database. */
    val artists: String = "",
    val album: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val source: String = "local",
    @SerialName("source_id") val sourceId: String? = null,
    val position: Int = 0,
)

@Serializable
data class CloudPlaylist(
    val title: String = "",
    @SerialName("cover_url") val coverUrl: String? = null,
    val description: String? = null,
    val type: String = "Playlist",
    val tracks: List<CloudTrack> = emptyList(),
)

/** A cloud playlist with the id the backend gave it. */
data class StoredCloudPlaylist(val cloudId: String?, val playlist: CloudPlaylist)

/**
 * The user's own playlists kept on `quarkaudio.ru/api/sync`, so they follow
 * the account between devices. A port of `playlist_sync_services.dart`: the
 * sync is by title — a playlist that exists on both sides is left alone.
 */
class PlaylistSync(
    private val http: HttpClient,
    private val account: QuarkAccount,
    private val baseUrl: String = QUARK_BASE_URL,
) {
    suspend fun fetchAll(): List<StoredCloudPlaylist> {
        val response = account.authorized { token -> http.get("$baseUrl/api/sync") { bearer(token) } }
        if (!response.status.isSuccess()) throw response.failure("Could not load cloud playlists")
        val items = LenientJson.parseToJsonElement(response.bodyAsText()) as? JsonArray ?: return emptyList()
        return items.mapNotNull { item ->
            val body = item as? JsonObject ?: return@mapNotNull null
            StoredCloudPlaylist(
                cloudId = (body["id"] as? JsonPrimitive)?.contentOrNull,
                playlist = LenientJson.decodeFromJsonElement(CloudPlaylist.serializer(), body),
            )
        }
    }

    /** Uploads [playlist] and returns the id the backend assigned. */
    suspend fun upload(playlist: CloudPlaylist): String? {
        val response = account.authorized { token ->
            http.post("$baseUrl/api/sync") {
                bearer(token)
                json(LenientJson.encodeToJsonElement(CloudPlaylist.serializer(), playlist))
            }
        }
        if (!response.status.isSuccess()) throw response.failure("Could not upload the playlist")
        return (LenientJson.parseToJsonElement(response.bodyAsText()) as? JsonObject)
            ?.get("id")?.jsonPrimitive?.contentOrNull
    }

    suspend fun delete(cloudId: String): Boolean = runCatching {
        account.authorized { token -> http.delete("$baseUrl/api/sync/$cloudId") { bearer(token) } }
            .status.isSuccess()
    }.getOrDefault(false)

    internal companion object {
        val listSerializer = ListSerializer(CloudPlaylist.serializer())
    }
}
