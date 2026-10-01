package com.quark.network.localapi

import com.quark.core.model.LocalTrack
import com.quark.core.model.Track
import com.quark.core.player.PlayerState
import com.quark.core.player.RepeatMode
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.cio.CIO
import io.ktor.server.engine.EmbeddedServer
import io.ktor.server.engine.embeddedServer
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.RoutingCall
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.websocket.WebSockets
import io.ktor.server.websocket.webSocket
import io.ktor.websocket.Frame
import io.ktor.websocket.close
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.file.Files
import java.nio.file.Path

/** What the local api can read and do; the application adapts its player to it. */
interface LocalApiBackend {
    val state: StateFlow<PlayerState>
    suspend fun pause()
    suspend fun resume()
    suspend fun next()
    suspend fun previous()
    suspend fun setVolume(volume: Float)
    fun setRepeat(enabled: Boolean)
    fun setShuffle(enabled: Boolean)
    suspend fun seek(seconds: Long)
    suspend fun playIndex(index: Int)
}

/**
 * The local control api, version 0 — the contract in the Flutter build's
 * `lib/services/local_api/README.md`, which external clients already speak.
 * Nothing here may change shape: paths, parameter names, status codes and
 * field names are the Dart server's, down to its quirks (see [trackJson]).
 *
 * HTTP and WebSocket on one random port, written to [portFiles] so clients can
 * find it. The Dart server listened on every interface; this one keeps to the
 * loopback address the documentation promises unless [lan] is set.
 */
class LocalApiServer(
    private val backend: LocalApiBackend,
    private val portFiles: List<Path>,
    private val lan: Boolean = false,
) {
    private var server: EmbeddedServer<*, *>? = null

    /** The port bound, once [start] has run. */
    var port: Int? = null
        private set

    suspend fun start(): Int {
        port?.let { return it }
        val engine = embeddedServer(CIO, port = 0, host = if (lan) "0.0.0.0" else "127.0.0.1") {
            module()
        }
        engine.startSuspend(wait = false)
        val bound = engine.engine.resolvedConnectors().first().port
        server = engine
        port = bound
        portFiles.forEach { file ->
            runCatching {
                Files.createDirectories(file.parent)
                Files.write(file, bound.toString().encodeToByteArray())
            }
        }
        return bound
    }

    suspend fun stop() {
        server?.stopSuspend(gracePeriodMillis = 200, timeoutMillis = 1_000)
        server = null
        port = null
        portFiles.forEach { runCatching { Files.deleteIfExists(it) } }
    }

    internal fun Application.module() {
        install(WebSockets)
        routing {
            get("/get-api-version") { call.plain(API_VERSION.toString()) }

            get("/get-quick-parameters") {
                val state = backend.state.value
                call.json(buildJsonObject {
                    put("repeat", state.repeat == RepeatMode.One)
                    put("shuffle", state.isShuffled)
                    put("volume", state.volume.toDouble())
                    put("paused", !state.isPlaying)
                })
            }

            get("/pause") { backend.pause(); call.noContent() }
            get("/resume") { backend.resume(); call.noContent() }
            get("/play-next") { backend.next(); call.noContent() }
            get("/play-previous") { backend.previous(); call.noContent() }

            get("/get-volume") { call.plain(backend.state.value.volume.toDouble().toString()) }
            get("/set-volume") {
                val raw = call.request.queryParameters["value"]
                    ?: return@get call.bad("Failed to get volume value")
                val volume = raw.toDoubleOrNull()?.takeIf { it in 0.0..1.0 }
                    ?: return@get call.bad("Incorrect volume value")
                backend.setVolume(volume.toFloat())
                call.noContent()
            }

            get("/get-repeat") { call.plain((backend.state.value.repeat == RepeatMode.One).toString()) }
            get("/set-repeat") {
                val raw = call.request.queryParameters["value"]
                    ?: return@get call.bad("Failed to get repeat value")
                val enabled = raw.toBooleanStrictOrNull() ?: return@get call.bad("Incorrect repeat value")
                backend.setRepeat(enabled)
                call.noContent()
            }

            get("/get-shuffle") { call.plain(backend.state.value.isShuffled.toString()) }
            get("/set-shuffle") {
                // The Dart server answered with the repeat wording here too.
                val raw = call.request.queryParameters["value"]
                    ?: return@get call.bad("Failed to get repeat value")
                val enabled = raw.toBooleanStrictOrNull() ?: return@get call.bad("Incorrect repeat value")
                backend.setShuffle(enabled)
                call.noContent()
            }

            get("/get-position") { call.plain(backend.state.value.position.inWholeSeconds.toString()) }
            get("/get-duration") { call.plain(backend.state.value.duration.inWholeSeconds.toString()) }

            get("/seek") {
                val raw = call.request.queryParameters["value"]
                    ?: return@get call.bad("Failed to get seek timing")
                val duration = backend.state.value.duration.inWholeSeconds
                val seconds = raw.toLongOrNull()?.takeIf { it in 0..duration }
                    ?: return@get call.bad("Incorrect seek timing")
                backend.seek(seconds)
                call.noContent()
            }
            get("/seek-delta-seconds") {
                val raw = call.request.queryParameters["value"]
                    ?: return@get call.bad("Failed to get seek timing")
                val state = backend.state.value
                val delta = raw.toLongOrNull()?.takeIf { it in 0..state.duration.inWholeSeconds }
                    ?: return@get call.bad("Incorrect seek timing")
                backend.seek(state.position.inWholeSeconds + delta)
                call.noContent()
            }

            get("/get-now-playing-track") {
                call.json(nowPlayingJson(backend.state.value))
            }
            get("/set-now-playing-track") {
                val raw = call.request.queryParameters["value"]
                    ?: return@get call.bad("Failed to get track index")
                val state = backend.state.value
                val index = raw.toIntOrNull()?.takeIf { it in 0 until state.playlist.size }
                    ?: return@get call.bad("Incorrect track index")
                backend.playIndex(index)
                call.noContent()
            }

            get("/get-now-playlist") { call.json(playlistJson(backend.state.value)) }

            webSocket("/subscribe") {
                val requested = call.request.queryParameters["subscriptions"]
                    ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty)
                if (requested.isNullOrEmpty() || !TOPICS.containsAll(requested)) {
                    close(io.ktor.websocket.CloseReason(io.ktor.websocket.CloseReason.Codes.CANNOT_ACCEPT, "Failed to get subscription list"))
                    return@webSocket
                }
                val topics = requested.toSet().filter { it != "position" } // disabled in v0: too chatty
                val updates = topics.map { topic ->
                    backend.state.map { topic to valueOf(topic, it) }.distinctUntilChanged().drop(1)
                }
                merge(*updates.toTypedArray()).collect { (topic, value) ->
                    send(Frame.Text(buildJsonObject {
                        put("event", "$topic-update")
                        put("new", value)
                    }.toString()))
                }
            }
        }
    }

    private fun valueOf(topic: String, state: PlayerState): JsonElement = when (topic) {
        "repeat" -> JsonPrimitive(state.repeat == RepeatMode.One)
        "shuffle" -> JsonPrimitive(state.isShuffled)
        "now-playing-track" -> nowPlayingJson(state)
        "playlist" -> playlistJson(state)
        "play-pause" -> JsonPrimitive(state.isPlaying)
        "duration" -> JsonPrimitive(state.duration.inWholeSeconds)
        "position" -> JsonPrimitive(state.position.inWholeSeconds)
        "volume" -> JsonPrimitive(state.volume.toDouble())
        else -> JsonPrimitive(null as String?)
    }

    private fun nowPlayingJson(state: PlayerState): JsonObject =
        trackJson(state.current, state, withPosition = true)

    private fun playlistJson(state: PlayerState): JsonObject = buildJsonObject {
        put("length", state.playlist.size)
        put("source", state.playlistInfo.id.source.value)
        put("name", state.playlistInfo.name)
        put("unqueued-track-index", -1)
        put("tracks", buildJsonArray {
            (state.playlist + state.queue).forEach { add(trackJson(it, state, withPosition = false)) }
        })
    }

    /**
     * A track as `serializedLocalTrack` plus the player fields the Dart server
     * added. Two quirks are kept on purpose because clients were written
     * against them: `paused` carries whether the player is *playing*, and
     * `duration` is the current track's length on every row.
     */
    internal fun trackJson(track: Track, state: PlayerState, withPosition: Boolean): JsonObject = buildJsonObject {
        put("title", track.title)
        put("artists", buildJsonArray { track.artists.forEach { add(JsonPrimitive(it)) } })
        put("albums", buildJsonArray { track.albums.forEach { add(JsonPrimitive(it)) } })
        put("filepath", track.filepath)
        put("cover", track.cover)
        put("coverType", track.coverType.value)
        if (withPosition) put("position", state.position.inWholeSeconds)
        put("duration", state.duration.inWholeSeconds)
        put("paused", state.isPlaying)
        put("local", track is LocalTrack)
        put("from-queue", track in state.queue)
        put("index", state.playlist.indexOf(track))
        put("queue-index", state.queue.indexOf(track))
    }

    private suspend fun RoutingCall.plain(text: String) =
        respondText(text, ContentType.Application.Json)

    private suspend fun RoutingCall.json(body: JsonObject) =
        respondText(body.toString(), ContentType.Application.Json)

    private suspend fun RoutingCall.noContent() = respond(HttpStatusCode.NoContent)

    private suspend fun RoutingCall.bad(message: String) =
        respondText(message, ContentType.Application.Json, HttpStatusCode.BadRequest)

    companion object {
        const val API_VERSION = 0

        val TOPICS = setOf(
            "repeat", "shuffle", "now-playing-track", "playlist", "play-pause", "duration", "position", "volume",
        )
    }
}
