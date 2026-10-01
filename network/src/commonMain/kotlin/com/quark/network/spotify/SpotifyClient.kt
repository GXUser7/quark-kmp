package com.quark.network.spotify

import com.quark.core.model.CoverType
import com.quark.core.model.ServiceTrack
import com.quark.core.model.Track
import com.quark.core.model.TrackSource
import com.quark.network.LenientJson
import com.quark.network.base64
import com.quark.network.base64Decode
import com.quark.network.hmacSha1
import com.quark.network.md5Hex
import com.quark.network.urlEncode
import com.quark.network.ytmusic.YtMusicClient
import io.ktor.client.HttpClient
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.Parameters
import io.ktor.http.content.TextContent
import io.ktor.http.isSuccess
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

/** The user's Spotify authorisation, as the settings keep it. */
@Serializable
data class SpotifyTokens(val access: String, val refresh: String)

interface SpotifyTokenStore {
    fun load(): SpotifyTokens?
    fun save(tokens: SpotifyTokens?)
}

data class SpotifyPlaylist(
    val id: String,
    val name: String,
    val cover: String?,
    val trackCount: Int,
    val owner: String,
) {
    val isLiked: Boolean get() = id == LIKED_ID

    companion object {
        /** The "Liked Songs" collection, which the api does not model as a playlist. */
        const val LIKED_ID = "liked_songs_synthetic_id"
    }
}

/** Stream quality as the settings name it, and the bitrate GDStudio understands. */
enum class SpotifyQuality(val bitrate: String) {
    HiRes("1400"),
    Lossless("999"),
    High("320"),
}

/**
 * Spotify, which never hands out audio: its catalogue is searched and a
 * playable copy of each track is found elsewhere (`services/spotify_services.dart`).
 *
 *  - Search runs on the web player's anonymous session: a token obtained with
 *    the player's TOTP, and the partner GraphQL api it queries.
 *  - The user's own playlists come from the public Web API after an OAuth
 *    sign-in, pasting the code back as the Dart build did.
 *  - Audio is found by ISRC: Spotify's metadata gives the ISRC, Tidal's public
 *    api the Tidal id, and GDStudio a stream for it. When that fails, the track
 *    is looked up on YouTube Music by artist and title.
 */
@OptIn(ExperimentalTime::class)
class SpotifyClient(
    private val http: HttpClient,
    private val tokens: SpotifyTokenStore,
    private val youtube: YtMusicClient? = null,
    private val quality: () -> SpotifyQuality = { SpotifyQuality.High },
) {
    // --- Anonymous web-player session ------------------------------------------

    private val sessionLock = Mutex()
    private var accessToken: String? = null
    private var clientToken: String? = null
    private var clientVersion: String = "1.2.3.4"
    private var sessionExpiresAt = 0L
    private val cookies = mutableMapOf<String, String>()

    private suspend fun session(): Pair<String, String> = sessionLock.withLock {
        val now = Clock.System.now().toEpochMilliseconds()
        val access = accessToken
        val client = clientToken
        if (access != null && client != null && now < sessionExpiresAt) return access to client

        cookies.clear()
        val page = http.get("https://open.spotify.com") { header("User-Agent", BROWSER) }
        remember(page)
        APP_CONFIG.find(page.bodyAsText())?.groupValues?.get(1)?.let { encoded ->
            runCatching {
                val config = LenientJson.parseToJsonElement(base64Decode(encoded).decodeToString()) as? JsonObject
                config?.string("clientVersion")?.let { clientVersion = it }
            }
        }

        val totp = totp(TOTP_SECRET, now)
        val tokenResponse = http.get("https://open.spotify.com/api/token") {
            header("User-Agent", BROWSER)
            cookieHeader()?.let { header("Cookie", it) }
            parameter("reason", "init")
            parameter("productType", "web-player")
            parameter("totp", totp)
            parameter("totpVer", TOTP_VERSION)
            parameter("totpServer", totp)
        }
        remember(tokenResponse)
        val token = parse(tokenResponse) as? JsonObject ?: throw SpotifyException("No web-player token")
        val newAccess = token.string("accessToken") ?: throw SpotifyException("No web-player token")
        val clientId = token.string("clientId").orEmpty()

        val clientTokenResponse = http.post("https://clienttoken.spotify.com/v1/clienttoken") {
            header("User-Agent", BROWSER)
            header("Accept", "application/json")
            setBody(
                TextContent(
                    buildJsonObject {
                        putJsonObject("client_data") {
                            put("client_version", clientVersion)
                            put("client_id", clientId)
                            putJsonObject("js_sdk_data") {
                                put("device_brand", "unknown")
                                put("device_model", "unknown")
                                put("os", "windows")
                                put("os_version", "NT 10.0")
                                put("device_id", cookies["sp_t"].orEmpty())
                                put("device_type", "computer")
                            }
                        }
                    }.toString(),
                    ContentType.Application.Json,
                )
            )
        }
        val granted = ((parse(clientTokenResponse) as? JsonObject)?.get("granted_token") as? JsonObject)
            ?.string("token") ?: throw SpotifyException("No client token")

        accessToken = newAccess
        clientToken = granted
        sessionExpiresAt = now + SESSION_LIFETIME_MS
        newAccess to granted
    }

    private fun forgetSession() {
        accessToken = null
        clientToken = null
    }

    /** Tracks matching [query], from the same search the web player runs. */
    suspend fun search(query: String, limit: Int = 20, offset: Int = 0): List<SpotifyHit> {
        val (access, client) = session()
        val response = http.post("https://api-partner.spotify.com/pathfinder/v2/query") {
            partnerHeaders(access, client)
            setBody(
                TextContent(
                    buildJsonObject {
                        putJsonObject("variables") {
                            put("searchTerm", query)
                            put("offset", offset)
                            put("limit", limit)
                            put("numberOfTopResults", 5)
                            put("includeAudiobooks", false)
                            put("includeArtistHasConcertsField", false)
                            put("includePreReleases", true)
                            put("includeAuthors", false)
                        }
                        put("operationName", "searchDesktop")
                        putJsonObject("extensions") {
                            putJsonObject("persistedQuery") {
                                put("version", 1)
                                put("sha256Hash", SEARCH_HASH)
                            }
                        }
                    }.toString(),
                    ContentType.Application.Json,
                )
            )
        }
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            forgetSession()
            return emptyList()
        }
        val search = (((parse(response) as? JsonObject)?.get("data") as? JsonObject)?.get("searchV2") as? JsonObject)
            ?: return emptyList()
        val items = ((search["tracksV2"] as? JsonObject)?.get("items") as? JsonArray)
            ?: ((search["tracks"] as? JsonObject)?.get("items") as? JsonArray)
            ?: return emptyList()
        return items.mapNotNull { item ->
            val data = ((item as? JsonObject)?.get("item") as? JsonObject)?.get("data") as? JsonObject
                ?: return@mapNotNull null
            val album = data["albumOfTrack"] as? JsonObject
            val sources = ((album?.get("coverArt") as? JsonObject)?.get("sources") as? JsonArray)
            val artists = ((data["artists"] as? JsonObject)?.get("items") as? JsonArray).orEmpty()
                .mapNotNull { ((it as? JsonObject)?.get("profile") as? JsonObject)?.string("name") }
            SpotifyHit(
                id = data.string("id") ?: return@mapNotNull null,
                title = data.string("name") ?: "Unknown",
                artists = artists.ifEmpty { listOf(Track.UNKNOWN_ARTIST) },
                album = album?.string("name").orEmpty(),
                cover = (sources?.firstOrNull() as? JsonObject)?.string("url"),
                durationMs = ((data["duration"] as? JsonObject)?.get("totalMilliseconds") as? JsonPrimitive)?.longOrNull
                    ?: ((data["duration"] as? JsonObject)?.get("milliseconds") as? JsonPrimitive)?.longOrNull
                    ?: 0L,
            )
        }
    }

    // --- Finding the audio ------------------------------------------------------

    /** A playable url for [track], or null when every route failed. */
    suspend fun streamUrl(track: ServiceTrack): String? {
        val isrc = runCatching { isrc(track.id) }.getOrNull()
        if (!isrc.isNullOrBlank()) {
            val tidalId = runCatching { tidalIdByIsrc(isrc) }.getOrNull()
            if (tidalId != null) {
                runCatching { gdStudio(tidalId) }.getOrNull()?.let { return it }
            }
        }
        // Tidal did not have it: fall back to YouTube by name.
        val youtube = youtube ?: return null
        val query = "${track.artists.joinToString(", ")} - ${track.title}"
        val found = runCatching { youtube.search(query, limit = 3) }.getOrDefault(emptyList())
        for (candidate in found) {
            val url = runCatching { youtube.song(candidate.id).streamUrl }.getOrNull()
            if (!url.isNullOrBlank()) return url
        }
        return null
    }

    suspend fun isrc(spotifyId: String): String? {
        val (access, client) = session()
        val response = http.get("https://spclient.wg.spotify.com/metadata/4/track/${gid(spotifyId)}") {
            parameter("market", "from_token")
            header("Authorization", "Bearer $access")
            header("Client-Token", client)
            header("Accept", "application/json")
            header("User-Agent", BROWSER)
        }
        if (response.status == HttpStatusCode.Unauthorized || response.status == HttpStatusCode.Forbidden) {
            forgetSession()
            return null
        }
        val ids = ((parse(response) as? JsonObject)?.get("external_id") as? JsonArray).orEmpty()
        return ids.mapNotNull { it as? JsonObject }
            .firstOrNull { it.string("type").equals("isrc", ignoreCase = true) }
            ?.string("id")?.uppercase()
    }

    private suspend fun tidalIdByIsrc(isrc: String): Long? {
        val response = http.get("https://listen.tidal.com/v1/tracks") {
            parameter("isrc", isrc)
            parameter("countryCode", "US")
            header("User-Agent", BROWSER)
            header("X-Tidal-Token", TIDAL_TOKEN)
        }
        val items = ((parse(response) as? JsonObject)?.get("items") as? JsonArray).orEmpty()
        return ((items.firstOrNull() as? JsonObject)?.get("id") as? JsonPrimitive)?.longOrNull
    }

    /** Asks GDStudio for a stream of [tidalId], best bitrate first, on each mirror in turn. */
    private suspend fun gdStudio(tidalId: Long): String? {
        val preferred = quality().bitrate
        val bitrates = listOf(preferred) + listOf("1400", "999", "740", "320").filter { it != preferred }
        for (host in GD_STUDIO_HOSTS) {
            val time = runCatching {
                http.get("https://$host/time") { header("User-Agent", "Mozilla/5.0") }.bodyAsText().trim()
            }.getOrNull()?.takeIf { it.all(Char::isDigit) && it.isNotEmpty() }
                ?: (Clock.System.now().epochSeconds).toString()
            val ts9 = time.take(9)
            val signature = gdSignature(host, ts9, tidalId.toString())
            for (bitrate in bitrates) {
                val url = runCatching {
                    val response = http.post("https://$host/api.php") {
                        header("Origin", "https://$host")
                        header("Referer", "https://$host/")
                        header("User-Agent", BROWSER)
                        setBody(
                            FormDataContent(
                                Parameters.build {
                                    append("types", "url")
                                    append("id", tidalId.toString())
                                    append("source", "tidal")
                                    append("br", bitrate)
                                    append("s", signature)
                                }
                            )
                        )
                    }
                    (parse(response) as? JsonObject)?.string("url")
                }.getOrNull()
                if (!url.isNullOrBlank()) return url
            }
        }
        return null
    }

    // --- The user's library (Web API) --------------------------------------------

    val isSignedIn: Boolean get() = tokens.load() != null

    val authorizeUrl: String
        get() = "https://accounts.spotify.com/authorize?client_id=$CLIENT_ID&response_type=code" +
            "&redirect_uri=${urlEncode(REDIRECT_URI)}" +
            "&scope=${urlEncode(SCOPES)}&show_dialog=true"

    /** Accepts the redirected address or the bare code, as pasted. */
    suspend fun signIn(pasted: String): Boolean {
        val text = pasted.trim()
        val code = Regex("[?&]code=([^&#\\s]+)").find(text)?.groupValues?.get(1) ?: text
        if (code.isBlank() || ' ' in code) return false
        val response = http.post("https://accounts.spotify.com/api/token") {
            header("Authorization", "Basic ${base64("$CLIENT_ID:$CLIENT_SECRET".encodeToByteArray())}")
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "authorization_code")
                        append("code", code)
                        append("redirect_uri", REDIRECT_URI)
                    }
                )
            )
        }
        if (!response.status.isSuccess()) return false
        val body = parse(response) as? JsonObject ?: return false
        val access = body.string("access_token") ?: return false
        val refresh = body.string("refresh_token") ?: return false
        tokens.save(SpotifyTokens(access, refresh))
        return true
    }

    fun signOut() = tokens.save(null)

    private suspend fun refreshUserToken(): Boolean {
        val current = tokens.load() ?: return false
        val response = http.post("https://accounts.spotify.com/api/token") {
            header("Authorization", "Basic ${base64("$CLIENT_ID:$CLIENT_SECRET".encodeToByteArray())}")
            setBody(
                FormDataContent(
                    Parameters.build {
                        append("grant_type", "refresh_token")
                        append("refresh_token", current.refresh)
                    }
                )
            )
        }
        if (!response.status.isSuccess()) return false
        val body = parse(response) as? JsonObject ?: return false
        val access = body.string("access_token") ?: return false
        tokens.save(SpotifyTokens(access, body.string("refresh_token") ?: current.refresh))
        return true
    }

    /** A Web API call with the user's token, refreshed once on 401. */
    private suspend fun userGet(url: String): JsonObject? {
        var token = tokens.load()?.access ?: return null
        var response = http.get(url) { header("Authorization", "Bearer $token") }
        if (response.status == HttpStatusCode.Unauthorized && refreshUserToken()) {
            token = tokens.load()?.access ?: return null
            response = http.get(url) { header("Authorization", "Bearer $token") }
        }
        if (!response.status.isSuccess()) throw SpotifyException("Spotify answered ${response.status.value}")
        return parse(response) as? JsonObject
    }

    suspend fun playlists(): List<SpotifyPlaylist> {
        val result = mutableListOf<SpotifyPlaylist>()
        runCatching {
            val liked = userGet("https://api.spotify.com/v1/me/tracks?limit=1")
            val total = (liked?.get("total") as? JsonPrimitive)?.intOrNull ?: 0
            if (total > 0) {
                result += SpotifyPlaylist(SpotifyPlaylist.LIKED_ID, "Liked Songs", LIKED_COVER, total, "Me")
            }
        }
        var next: String? = "https://api.spotify.com/v1/me/playlists?limit=50"
        while (next != null) {
            val page = userGet(next) ?: break
            (page["items"] as? JsonArray).orEmpty().mapNotNull { it as? JsonObject }.forEach { item ->
                val images = item["images"] as? JsonArray
                result += SpotifyPlaylist(
                    id = item.string("id") ?: return@forEach,
                    name = item.string("name") ?: "Untitled playlist",
                    cover = (images?.firstOrNull() as? JsonObject)?.string("url"),
                    trackCount = ((item["tracks"] as? JsonObject)?.get("total") as? JsonPrimitive)?.intOrNull ?: 0,
                    owner = (item["owner"] as? JsonObject)?.string("display_name") ?: "Unknown",
                )
            }
            next = page.string("next")
        }
        return result
    }

    suspend fun playlistTracks(playlist: SpotifyPlaylist, limit: Int = 1000): List<SpotifyHit> {
        var next: String? = if (playlist.isLiked) "https://api.spotify.com/v1/me/tracks?limit=50"
        else "https://api.spotify.com/v1/playlists/${playlist.id}/tracks?limit=100"
        val result = mutableListOf<SpotifyHit>()
        while (next != null && result.size < limit) {
            val page = userGet(next) ?: break
            (page["items"] as? JsonArray).orEmpty().forEach { entry ->
                val track = (entry as? JsonObject)?.get("track") as? JsonObject ?: return@forEach
                val album = track["album"] as? JsonObject
                val images = album?.get("images") as? JsonArray
                result += SpotifyHit(
                    id = track.string("id") ?: return@forEach,
                    title = track.string("name") ?: "Unknown",
                    artists = (track["artists"] as? JsonArray).orEmpty()
                        .mapNotNull { (it as? JsonObject)?.string("name") }
                        .ifEmpty { listOf(Track.UNKNOWN_ARTIST) },
                    album = album?.string("name").orEmpty(),
                    cover = (images?.firstOrNull() as? JsonObject)?.string("url"),
                    durationMs = (track["duration_ms"] as? JsonPrimitive)?.longOrNull ?: 0L,
                )
            }
            next = page.string("next")
        }
        return result
    }

    // --- Plumbing ------------------------------------------------------------------

    private fun io.ktor.client.request.HttpRequestBuilder.partnerHeaders(access: String, client: String) {
        header("Authorization", "Bearer $access")
        header("Client-Token", client)
        header("Spotify-App-Version", clientVersion)
        header("User-Agent", BROWSER)
    }

    private fun remember(response: HttpResponse) {
        response.headers.getAll("Set-Cookie").orEmpty().forEach { cookie ->
            val pair = cookie.substringBefore(';')
            val name = pair.substringBefore('=').trim()
            if (name.isNotEmpty() && '=' in pair) cookies[name] = pair.substringAfter('=').trim()
        }
    }

    private fun cookieHeader(): String? =
        cookies.takeIf { it.isNotEmpty() }?.entries?.joinToString("; ") { "${it.key}=${it.value}" }

    private suspend fun parse(response: HttpResponse): JsonElement? =
        runCatching { LenientJson.parseToJsonElement(response.bodyAsText()) }.getOrNull()

    companion object {
        const val CLIENT_ID = "598a5a932ba2414fbc203483596f2391"
        private const val CLIENT_SECRET = "dd2c1d9558154e758a6609c5aae4d98d"
        const val REDIRECT_URI = "https://oauth.pstmn.io/v1/browser-callback"
        private const val SCOPES =
            "playlist-read-private playlist-read-collaborative user-library-read user-read-private user-read-email"

        private const val BROWSER =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/145.0.0.0 Safari/537.36"
        private const val TOTP_SECRET =
            "GM3TMMJTGYZTQNZVGM4DINJZHA4TGOBYGMZTCMRTGEYDSMJRHE4TEOBUG4YTCMRUGQ4DQOJUGQYTAMRRGA2TCMJSHE3TCMBY"
        private const val TOTP_VERSION = "61"
        private const val SESSION_LIFETIME_MS = 55 * 60 * 1000L
        private const val SEARCH_HASH = "fcad5a3e0d5af727fb76966f06971c19cfa2275e6ff7671196753e008611873c"
        private const val TIDAL_TOKEN = "CzET4vdadNUFQ5JU"
        private const val LIKED_COVER = "https://t.scdn.co/images/30782914-b35a-4df2-ab14-c6c339594e57.png"
        private val GD_STUDIO_HOSTS = listOf("music.gdstudio.xyz", "music.gdstudio.org")
        private val APP_CONFIG = Regex("<script id=\"appServerConfig\" type=\"text/plain\">([^<]+)</script>")

        /** RFC 6238 over a base32 secret: what the web player computes to get its token. */
        fun totp(secret: String, timeMillis: Long, digits: Int = 6): String {
            val counter = timeMillis / 1000 / 30
            val message = ByteArray(8) { index -> (counter ushr (8 * (7 - index))).toByte() }
            val hash = hmacSha1(base32(secret), message)
            val offset = hash.last().toInt() and 0x0f
            val binary = ((hash[offset].toInt() and 0x7f) shl 24) or
                ((hash[offset + 1].toInt() and 0xff) shl 16) or
                ((hash[offset + 2].toInt() and 0xff) shl 8) or
                (hash[offset + 3].toInt() and 0xff)
            var modulus = 1
            repeat(digits) { modulus *= 10 }
            return (binary % modulus).toString().padStart(digits, '0')
        }

        fun base32(secret: String): ByteArray {
            val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
            val clean = secret.trimEnd('=').uppercase()
            val output = ArrayList<Byte>(clean.length * 5 / 8)
            var buffer = 0
            var bits = 0
            for (char in clean) {
                val value = alphabet.indexOf(char)
                if (value < 0) continue
                buffer = (buffer shl 5) or value
                bits += 5
                if (bits >= 8) {
                    output += ((buffer ushr (bits - 8)) and 0xff).toByte()
                    bits -= 8
                }
            }
            return output.toByteArray()
        }

        /** A track's base62 id as the 32-hex-digit gid the metadata api wants. */
        fun gid(base62: String): String {
            val alphabet = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
            val bytes = IntArray(17)
            for (char in base62) {
                val digit = alphabet.indexOf(char)
                require(digit >= 0) { "Not a base62 id: $base62" }
                var carry = digit
                for (index in bytes.indices.reversed()) {
                    val value = bytes[index] * 62 + carry
                    bytes[index] = value and 0xff
                    carry = value ushr 8
                }
            }
            return bytes.drop(1).joinToString("") { it.toString(16).padStart(2, '0') }
        }

        /** GDStudio signs a request with the tail of an md5 over the host, a date and the id. */
        fun gdSignature(host: String, ts9: String, trackId: String): String {
            val digest = md5Hex("$host|20260510|$ts9|${urlEncode(trackId)}".encodeToByteArray()).lowercase()
            return digest.takeLast(8).uppercase()
        }
    }
}

/** A track as Spotify describes it, before it becomes one the player can queue. */
data class SpotifyHit(
    val id: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val cover: String?,
    val durationMs: Long,
) {
    fun toTrack(cacheRoot: String, separator: String = "/"): ServiceTrack = ServiceTrack(
        title = title,
        artists = artists,
        albums = listOf(album.ifBlank { Track.UNKNOWN_ALBUM }),
        filepath = listOf(cacheRoot, "audio_cache", "spotify", "cisum_yfitops_krauq$id.flac").joinToString(separator),
        coverType = if (cover.isNullOrBlank()) CoverType.NoCover else CoverType.Url,
        cover = cover.orEmpty(),
        source = TrackSource.Spotify,
        id = id,
        durationMs = durationMs,
    )
}

class SpotifyException(message: String) : RuntimeException(message)

private fun JsonObject.string(name: String): String? = (this[name] as? JsonPrimitive)?.contentOrNull
