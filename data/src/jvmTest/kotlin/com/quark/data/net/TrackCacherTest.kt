package com.quark.data.net

import com.quark.core.model.CoverType
import com.quark.core.model.YandexTrack
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.readBytes
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class TrackCacherTest {

    @Test
    fun retries_twice_then_atomically_keeps_the_download() = runTest {
        val directory = Files.createTempDirectory("quark-track-cache-test")
        try {
            var requests = 0
            val expected = "audio bytes".encodeToByteArray()
            val http = HttpClient(MockEngine {
                requests += 1
                if (requests < 3) respond("temporary", HttpStatusCode.ServiceUnavailable)
                else respond(expected)
            })
            val track = remote("dogs", directory.resolve("dogs.flac"))
            val cacher = TrackCacher(
                http = http,
                io = Dispatchers.Unconfined,
                retryDelay = {},
            )

            val cached = cacher.cache(listOf(track)) { DownloadSource("https://cdn.test/dogs") }

            assertEquals(3, requests)
            assertEquals(listOf(track.filepath), cached)
            assertContentEquals(expected, Path.of(track.filepath).readBytes())
            assertEquals(0, Files.list(directory).use { files ->
                files.filter { it.fileName.toString().contains(".part-") }.count()
            })
        } finally {
            directory.toFile().deleteRecursively()
        }
    }

    @Test
    fun window_wraps_previous_current_and_next() {
        val directory = Path.of("cache")
        val tracks = listOf("a", "b", "c", "d").map { remote(it, directory.resolve("$it.flac")) }
        val cacher = TrackCacher(HttpClient(MockEngine { respond("unused") }))

        assertEquals(listOf("d", "a", "b"), cacher.window(tracks, tracks.first()).map { it.title })
    }
}

private fun remote(name: String, path: Path) = YandexTrack(
    title = name,
    artists = listOf("Pink Floyd"),
    albums = listOf("Animals"),
    filepath = path.toString(),
    coverType = CoverType.Url,
    trackId = name,
)
