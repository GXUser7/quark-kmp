package com.quark.network.yandex

import com.quark.network.yandex.dto.AccountStatusDto
import com.quark.network.yandex.dto.AlbumDto
import com.quark.network.yandex.dto.ArtistDto
import com.quark.network.yandex.dto.DownloadInfoDto
import com.quark.network.yandex.dto.LyricsDto
import com.quark.network.yandex.dto.PlaylistDto
import com.quark.network.yandex.dto.RotorBatchDto
import com.quark.network.yandex.dto.RotorSessionDto
import com.quark.network.yandex.dto.SearchResultDto
import com.quark.network.yandex.dto.TrackDto
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.put
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** Requested audio quality, in the api's vocabulary. */
enum class YandexQuality(val value: String) {
    Normal("nq"),
    High("hq"),
    Lossless("lossless"),
}

enum class LyricsFormat(val value: String) {
    PlainText("TEXT_PLAIN"),
    Synced("LRC"),
}

/**
 * Everything the player asks of Yandex Music.
 *
 * One class rather than the ten subclasses of the Dart library: the calls share
 * a client and nothing else, and splitting them bought only indirection.
 */
@OptIn(ExperimentalTime::class)
class YandexMusic(private val client: YandexClient) {

    val token: String get() = client.token

    /** Signs in and remembers the account id every other call needs. */
    suspend fun authorise(): AccountStatusDto {
        val status = decode(client.get("/account/status"), AccountStatusDto.serializer())
        client.userId = status.account.uid
        return status
    }

    suspend fun accountStatus(): AccountStatusDto =
        decode(client.get("/account/status"), AccountStatusDto.serializer())

    // --- Playlists -----------------------------------------------------------

    suspend fun userPlaylists(userId: Long = client.userId): List<PlaylistDto> =
        decodeList(client.get("/users/$userId/playlists/list"), PlaylistDto.serializer())

    suspend fun playlist(kind: Long, userId: Long = client.userId): PlaylistDto =
        decode(client.get("/users/$userId/playlists/$kind"), PlaylistDto.serializer())

    /** The "Liked" pseudo-playlist, which is a plain track id list. */
    suspend fun likedTrackIds(userId: Long = client.userId): List<String> {
        val library = client.get("/users/$userId/likes/tracks") as? JsonObject
            ?: throw YandexException.Unexpected("likes/tracks: not an object")
        return library["library"]?.let { it as? JsonObject }
            ?.get("tracks")?.jsonArray
            ?.mapNotNull { (it as? JsonObject)?.get("id")?.asString() }
            .orEmpty()
    }

    // --- Tracks --------------------------------------------------------------

    /** Batch lookup; the api takes up to a few hundred ids at a time. */
    suspend fun tracks(ids: List<String>): List<TrackDto> {
        if (ids.isEmpty()) return emptyList()
        return decodeList(
            client.post("/tracks", parameters = mapOf("track-ids" to ids.joinToString(","))),
            TrackDto.serializer(),
        )
    }

    suspend fun track(id: String): TrackDto? = tracks(listOf(id)).firstOrNull()

    suspend fun similarTracks(id: String): List<TrackDto> {
        val result = client.get("/tracks/$id/similar") as? JsonObject ?: return emptyList()
        return result["similarTracks"]?.let { decodeList(it, TrackDto.serializer()) }.orEmpty()
    }

    /**
     * A direct, signed and short-lived url for the audio.
     *
     * The signature covers the timestamp, so the url has to be fetched close to
     * when it is used; the caller caches it for minutes, not hours.
     */
    suspend fun downloadUrl(trackId: String, quality: YandexQuality): String {
        val timestamp = Clock.System.now().epochSeconds
        val signature = YandexSignatures.fileInfo(
            trackId = trackId,
            quality = quality.value,
            codecs = CODECS,
            transport = TRANSPORT,
            timestamp = timestamp,
        )

        val response = client.get(
            path = "/get-file-info",
            parameters = mapOf(
                "ts" to timestamp,
                "trackId" to trackId,
                "quality" to quality.value,
                // Signed without separators, sent with commas. Not a typo.
                "codecs" to CODECS.joinToString(","),
                "transports" to TRANSPORT,
                "sign" to signature,
            ),
        )

        return decode(response, DownloadInfoDto.serializer()).info.best
            ?: throw YandexException.Unexpected("get-file-info returned no url for $trackId")
    }

    suspend fun lyrics(trackId: String, format: LyricsFormat = LyricsFormat.Synced): String? {
        val signature = YandexSignatures.lyrics(trackId, Clock.System.now().epochSeconds)
        val response = client.get(
            path = "/tracks/$trackId/lyrics",
            parameters = mapOf(
                "format" to format.value,
                "timeStamp" to signature.timestamp,
                "sign" to signature.signature,
            ),
            headers = YandexClient.MOBILE_HEADERS,
        )

        val url = decode(response, LyricsDto.serializer()).downloadUrl
        return url.takeIf(String::isNotEmpty)?.let { client.getRaw(it) }
    }

    // --- Albums and artists --------------------------------------------------

    suspend fun album(id: Long, withTracks: Boolean = true): AlbumDto = decode(
        client.get(if (withTracks) "/albums/$id/with-tracks" else "/albums/$id"),
        AlbumDto.serializer(),
    )

    suspend fun artist(id: Long): JsonElement = client.get("/artists/$id/brief-info")

    suspend fun artistTracks(id: Long, page: Int = 0, pageSize: Int = 50): List<TrackDto> {
        val result = client.get(
            "/artists/$id/tracks",
            mapOf("page" to page, "page-size" to pageSize),
        ) as? JsonObject ?: return emptyList()
        return result["tracks"]?.let { decodeList(it, TrackDto.serializer()) }.orEmpty()
    }

    suspend fun artistAlbums(id: Long, page: Int = 0, pageSize: Int = 50): List<AlbumDto> {
        val result = client.get(
            "/artists/$id/direct-albums",
            mapOf("page" to page, "page-size" to pageSize),
        ) as? JsonObject ?: return emptyList()
        return result["albums"]?.let { decodeList(it, AlbumDto.serializer()) }.orEmpty()
    }

    // --- Search --------------------------------------------------------------

    suspend fun search(
        text: String,
        type: String = "all",
        page: Int = 0,
        correct: Boolean = true,
    ): SearchResultDto = decode(
        client.get(
            "/search",
            mapOf("text" to text, "page" to page, "type" to type, "nocorrect" to !correct),
        ),
        SearchResultDto.serializer(),
    )

    // --- My Vibe -------------------------------------------------------------

    suspend fun waveSettings(): JsonElement = client.get("/rotor/wave/settings")

    /** Opens a station. [seeds] are things like `user:onyourwave` or `genre:rock`. */
    suspend fun startWave(seeds: List<String>): RotorSessionDto = decode(
        client.post(
            "/rotor/session/new",
            body = buildJsonObject {
                put("seeds", buildJsonArray { seeds.forEach { add(JsonPrimitive(it)) } })
                put("includeTracksInResponse", true)
                put("includeWaveModel", true)
                put("interactive", true)
            },
        ),
        RotorSessionDto.serializer(),
    )

    /** Asks the station for the next batch once the current one runs low. */
    suspend fun waveTracks(sessionId: String, queue: List<String>): RotorBatchDto = decode(
        client.post(
            "/rotor/session/$sessionId/tracks",
            body = buildJsonObject {
                put("queue", buildJsonArray { queue.forEach { add(JsonPrimitive(it)) } })
            },
        ),
        RotorBatchDto.serializer(),
    )

    /**
     * Tells the station what the listener did. It shapes what comes next, and
     * skipping it makes the wave repeat itself.
     */
    suspend fun sendWaveFeedback(sessionId: String, feedback: WaveFeedback) {
        client.post(
            "/rotor/session/$sessionId/feedback",
            parameters = mapOf("batch-id" to feedback.batchId),
            body = feedback.toJson(),
        )
    }

    // --- Plumbing ------------------------------------------------------------

    private fun <T> decode(element: JsonElement, serializer: kotlinx.serialization.KSerializer<T>): T =
        client.json.decodeFromJsonElement(serializer, element)

    private fun <T> decodeList(
        element: JsonElement,
        serializer: kotlinx.serialization.KSerializer<T>,
    ): List<T> = when (element) {
        is JsonArray -> element.map { decode(it, serializer) }
        else -> throw YandexException.Unexpected("expected an array, got ${element::class.simpleName}")
    }

    private fun JsonElement.asString(): String? = (this as? JsonPrimitive)?.content

    private companion object {
        /** Signed in this order, joined with nothing; sent joined with commas. */
        val CODECS = listOf("flac", "aac", "he-aac", "mp3", "flac-mp4", "aac-mp4", "he-aac-mp4")
        const val TRANSPORT = "raw"
    }
}

/** An event the wave wants to hear about. */
sealed class WaveFeedback(val type: String, val batchId: String?) {
    class RadioStarted(batchId: String?) : WaveFeedback("radioStarted", batchId)

    class TrackStarted(batchId: String?, val trackId: String, val albumId: Long?) :
        WaveFeedback("trackStarted", batchId)

    class TrackFinished(
        batchId: String?,
        val trackId: String,
        val albumId: Long?,
        val playedSeconds: Double,
        val totalSeconds: Double,
    ) : WaveFeedback("trackFinished", batchId)

    class Skipped(
        batchId: String?,
        val trackId: String,
        val albumId: Long?,
        val playedSeconds: Double,
    ) : WaveFeedback("skip", batchId)

    /** Yandex identifies a track inside a station as `trackId:albumId`. */
    protected fun qualifiedId(trackId: String, albumId: Long?): String =
        if (albumId != null) "$trackId:$albumId" else trackId

    @OptIn(ExperimentalTime::class)
    internal fun toJson(): JsonObject = buildJsonObject {
        put("event", buildJsonObject {
            put("type", type)
            put("timestamp", Clock.System.now().toString())
            put("from", FROM)
            when (this@WaveFeedback) {
                is TrackStarted -> put("trackId", qualifiedId(trackId, albumId))

                is TrackFinished -> {
                    put("trackId", qualifiedId(trackId, albumId))
                    put("totalPlayedSeconds", playedSeconds)
                    put("trackLengthSeconds", totalSeconds)
                }

                is Skipped -> {
                    put("trackId", qualifiedId(trackId, albumId))
                    put("totalPlayedSeconds", playedSeconds)
                }

                is RadioStarted -> Unit
            }
        })
        batchId?.let { put("batchId", it) }
    }

    private companion object {
        /** The station this client claims to be; the api records it verbatim. */
        const val FROM = "web-home-rup_main-radio-default"
    }
}
