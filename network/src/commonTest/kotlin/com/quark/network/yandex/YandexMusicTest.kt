package com.quark.network.yandex

import com.quark.core.model.YandexTrack
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.respondError
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.request.HttpRequestData
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

private fun clientReturning(
    body: String,
    status: HttpStatusCode = HttpStatusCode.OK,
    onRequest: (HttpRequestData) -> Unit = {},
): YandexMusic {
    val engine = MockEngine { request ->
        onRequest(request)
        if (status.value !in 200..299) {
            respondError(status)
        } else {
            respond(
                content = body,
                status = status,
                headers = headersOf("Content-Type", ContentType.Application.Json.toString()),
            )
        }
    }
    val http = HttpClient(engine) {
        install(ContentNegotiation) { json(Json { ignoreUnknownKeys = true }) }
        expectSuccess = false
    }
    return YandexMusic(YandexClient(http, token = "test-token", userId = 42))
}

class YandexMusicTest {

    @Test
    fun authorising_remembers_the_account_id() = runTest {
        val api = clientReturning(
            """{"result":{"account":{"uid":12345,"login":"zenar","displayName":"Zenar"}}}"""
        )

        val status = api.authorise()

        assertEquals(12345L, status.account.uid)
        assertEquals("zenar", status.account.login)
    }

    @Test
    fun every_request_carries_the_token_and_the_web_client_headers() = runTest {
        var seen: HttpRequestData? = null
        val api = clientReturning("""{"result":{"account":{"uid":1}}}""") { seen = it }

        api.accountStatus()

        val headers = seen!!.headers
        assertEquals("OAuth test-token", headers["Authorization"])
        assertEquals("YandexMusicWebNext/1.0.0", headers["x-yandex-music-client"])
        assertEquals("42", headers["x-yandex-music-multi-auth-user-id"])
    }

    @Test
    fun the_download_url_call_is_signed_and_sends_codecs_comma_separated() = runTest {
        var seen: HttpRequestData? = null
        val api = clientReturning(
            """{"result":{"downloadInfo":{"url":"https://cdn/track.flac","urls":["https://cdn/alt.flac"],"codec":"flac"}}}"""
        ) { seen = it }

        val url = api.downloadUrl("47127", YandexQuality.Lossless)

        assertEquals("https://cdn/track.flac", url)
        val query = seen!!.url.parameters
        assertEquals("47127", query["trackId"])
        assertEquals("lossless", query["quality"])
        assertEquals("flac,aac,he-aac,mp3,flac-mp4,aac-mp4,he-aac-mp4", query["codecs"])
        assertEquals("raw", query["transports"])
        // The signature is base64 with its last character dropped, so it never
        // ends in padding.
        val signature = query["sign"]!!
        assertTrue(signature.isNotEmpty())
        assertTrue(!signature.endsWith("="))
    }

    @Test
    fun a_missing_url_is_an_error_rather_than_a_silent_empty_string() = runTest {
        val api = clientReturning("""{"result":{"downloadInfo":{}}}""")

        val failure = assertFailsWith<YandexException.Unexpected> {
            api.downloadUrl("47127", YandexQuality.High)
        }
        assertContains(failure.message!!, "47127")
    }

    @Test
    fun a_rejected_token_surfaces_as_unauthorised() = runTest {
        val api = clientReturning("", status = HttpStatusCode.Unauthorized)

        assertFailsWith<YandexException.Unauthorized> { api.accountStatus() }
    }

    @Test
    fun lyrics_go_out_with_the_mobile_client_headers() = runTest {
        var seen: HttpRequestData? = null
        val api = clientReturning("""{"result":{"downloadUrl":""}}""") { seen = it }

        api.lyrics("47127")

        assertEquals("YandexMusicAndroid/24023621", seen!!.headers["X-Yandex-Music-Client"])
        assertEquals("LRC", seen!!.url.parameters["format"])
    }

    @Test
    fun a_playlist_becomes_domain_tracks_with_cache_paths() = runTest {
        val api = clientReturning(
            """
            {"result":{"kind":1003,"title":"Мне нравится","owner":{"uid":42,"login":"zenar"},
             "tracks":[{"id":"47127","track":{"id":"47127","title":"Heroes","durationMs":371000,
             "coverUri":"avatars.yandex.net/get-music-content/x/%%",
             "artists":[{"id":9,"name":"David Bowie"}],
             "albums":[{"id":5,"title":"Heroes"}]}}]}}
            """.trimIndent()
        )

        val playlist = api.playlist(kind = 1003).toPlaylist(cacheRoot = "/cache")

        assertEquals("Мне нравится", playlist.name)
        val track = playlist.tracks.single() as YandexTrack
        assertEquals("Heroes", track.title)
        assertEquals(listOf("David Bowie"), track.artists)
        assertEquals(5L, track.albumId)
        assertEquals("/cache/audio_cache/yandex_music/cisum_xednay_krauq47127.flac", track.filepath)
        assertEquals("https://avatars.yandex.net/get-music-content/x/300x300", track.cover)
    }

    @Test
    fun wave_feedback_names_the_track_the_way_the_station_expects() = runTest {
        var seen: HttpRequestData? = null
        val api = clientReturning("""{"result":{}}""") { seen = it }

        api.sendWaveFeedback(
            sessionId = "s1",
            feedback = WaveFeedback.TrackFinished(
                batchId = "b1",
                trackId = "47127",
                albumId = 5,
                playedSeconds = 300.0,
                totalSeconds = 371.0,
            ),
        )

        assertContains(seen!!.url.encodedPath, "/rotor/session/s1/feedback")
        assertEquals("b1", seen!!.url.parameters["batch-id"])
    }
}
