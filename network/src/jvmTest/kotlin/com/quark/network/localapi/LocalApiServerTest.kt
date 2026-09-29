package com.quark.network.localapi

import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.PlaylistInfo
import com.quark.core.player.PlayerState
import com.quark.core.player.RepeatMode
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlin.io.path.createTempDirectory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration.Companion.seconds

class LocalApiServerTest {

    private val dogs = track("Dogs")
    private val sheep = track("Sheep")

    private val backend = RecordingBackend(
        PlayerState(
            current = dogs,
            playlist = listOf(dogs, sheep),
            playlistInfo = PlaylistInfo(name = "Animals"),
            position = 42.seconds,
            duration = 1024.seconds,
            isPlaying = true,
            volume = 0.5f,
        )
    )
    private val server = LocalApiServer(backend, portFiles = listOf(createTempDirectory().resolve("api.port")))

    @Test
    fun answers_the_v0_contract() = testApplication {
        application { with(server) { module() } }

        assertEquals("0", client.get("/get-api-version").bodyAsText())
        assertEquals("0.5", client.get("/get-volume").bodyAsText())
        assertEquals("42", client.get("/get-position").bodyAsText())
        assertEquals("false", client.get("/get-repeat").bodyAsText())

        val quick = Json.parseToJsonElement(client.get("/get-quick-parameters").bodyAsText()).jsonObject
        assertEquals("false", quick["paused"]!!.jsonPrimitive.content)

        val now = Json.parseToJsonElement(client.get("/get-now-playing-track").bodyAsText()).jsonObject
        assertEquals("Dogs", now["title"]!!.jsonPrimitive.content)
        assertEquals("0", now["index"]!!.jsonPrimitive.content)
        assertEquals("builtIn", now["coverType"]!!.jsonPrimitive.content)

        val playlist = Json.parseToJsonElement(client.get("/get-now-playlist").bodyAsText()).jsonObject
        assertEquals("Animals", playlist["name"]!!.jsonPrimitive.content)
        assertEquals(2, playlist["tracks"]!!.jsonArray.size)
    }

    @Test
    fun validates_parameters_like_the_dart_server() = testApplication {
        application { with(server) { module() } }

        assertEquals(HttpStatusCode.BadRequest, client.get("/set-volume").status)
        assertEquals(HttpStatusCode.BadRequest, client.get("/set-volume?value=1.5").status)
        assertEquals(HttpStatusCode.NoContent, client.get("/set-volume?value=0.25").status)
        assertEquals(0.25f, backend.volume)

        assertEquals(HttpStatusCode.BadRequest, client.get("/seek?value=9999").status)
        assertEquals(HttpStatusCode.NoContent, client.get("/seek-delta-seconds?value=10").status)
        assertEquals(52L, backend.sought)

        assertEquals(HttpStatusCode.NoContent, client.get("/set-now-playing-track?value=1").status)
        assertEquals(1, backend.played)
    }

    private class RecordingBackend(initial: PlayerState) : LocalApiBackend {
        override val state = MutableStateFlow(initial)
        var volume = -1f
        var sought = -1L
        var played = -1

        override suspend fun pause() = state.update { it.copy(isPlaying = false) }
        override suspend fun resume() = state.update { it.copy(isPlaying = true) }
        override suspend fun next() = Unit
        override suspend fun previous() = Unit
        override suspend fun setVolume(volume: Float) { this.volume = volume }
        override fun setRepeat(enabled: Boolean) = state.update {
            it.copy(repeat = if (enabled) RepeatMode.One else RepeatMode.Off)
        }
        override fun setShuffle(enabled: Boolean) = state.update { it.copy(isShuffled = enabled) }
        override suspend fun seek(seconds: Long) { sought = seconds }
        override suspend fun playIndex(index: Int) { played = index }
    }

    private fun track(title: String) = LocalTrack(
        title = title,
        artists = listOf("Pink Floyd"),
        albums = listOf("Animals"),
        filepath = "/music/$title.flac",
        coverType = CoverType.BuiltIn,
    )
}
