package com.quark.app

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.unit.Density
import com.quark.app.browse.CollectionKind
import com.quark.app.browse.TrackCollection
import com.quark.app.nav.Navigator
import com.quark.app.nav.Screen
import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Playlist
import com.quark.core.model.PlaylistId
import com.quark.core.model.Track
import com.quark.core.settings.AppearanceSettings
import com.quark.core.settings.Settings
import com.quark.core.settings.SettingsStore
import com.quark.core.settings.ThemeMode
import com.quark.data.db.DatabaseFactory
import com.quark.data.images.ImageStore
import com.quark.data.local.DesktopLibrary
import com.quark.data.local.LibraryScanner
import com.quark.data.net.TrackCacher
import com.quark.network.defaultHttpClient
import com.quark.platform.DeviceKind
import com.quark.platform.StoragePaths
import com.quark.player.AudioEngine
import com.quark.player.EngineEvent
import com.quark.player.MediaSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration

/**
 * Composes the whole interface — every screen, a phone-narrow window and the
 * light theme — against a real application graph with a silent engine and no
 * network, and writes what it drew to `build/preview`.
 *
 * What it guards is that nothing throws while composing: a missing
 * composition local, a duplicate list key, a state read in the wrong place.
 * Those fail only at run time, and this runs on every build.
 */
class AppRenderTest {

    private val tracks: List<Track> = listOf(
        track("Dogs", "Pink Floyd", "Animals"),
        track("Pigs (Three Different Ones)", "Pink Floyd", "Animals"),
        track("Sheep", "Pink Floyd", "Animals"),
        track("Heroes", "David Bowie", "\"Heroes\""),
        track("Washing Machine Heart", "Mitski", "Be the Cowboy"),
    )

    @Test
    fun every_screen_composes() {
        val settings = MemorySettings(Settings())
        val app = testApp(settings)
        val navigator = app.retain { Navigator() }
        val collection = TrackCollection(
            key = "local:test",
            title = "Road trip",
            tracks = tracks,
            playlistId = PlaylistId.Local,
            kind = CollectionKind.Playlist,
            userPlaylistId = 1,
        )

        val screens: List<Pair<String, Screen>> = listOf(
            "home" to Screen.Home,
            "player" to Screen.Player,
            "search" to Screen.Search,
            "settings" to Screen.Settings,
            "statistics" to Screen.Statistics,
            "account" to Screen.Account,
            "yandex" to Screen.Yandex,
            "spotify" to Screen.Spotify,
            "soundcloud" to Screen.SoundCloud,
            "vk" to Screen.Vk,
            "youtube" to Screen.YouTube,
            "collection" to Screen.Collection(collection.key, collection.title) { collection },
            "artist" to Screen.Artist(1, "Pink Floyd"),
        )

        render(app, 1100, 760) { frame ->
            screens.forEach { (name, screen) ->
                navigator.home()
                navigator.push(screen)
                frame("desktop-$name")
            }
            navigator.home()
            settings.update { it.copy(appearance = AppearanceSettings(theme = ThemeMode.Light)) }
            frame("desktop-home-light")
            navigator.push(Screen.Player)
            frame("desktop-player-light")
            settings.update { it.copy(appearance = AppearanceSettings(theme = ThemeMode.Dark, language = "ru")) }
            navigator.home()
            frame("desktop-home-ru")
            navigator.push(Screen.Settings)
            frame("desktop-settings-ru")
        }

        render(app, 400, 820) { frame ->
            listOf("home" to Screen.Home, "player" to Screen.Player, "collection" to screens[11].second).forEach { (name, screen) ->
                navigator.home()
                navigator.push(screen)
                frame("phone-$name")
            }
        }

        render(app, 900, 200) { frame ->
            frame("squashed")
        }

        runBlocking { app.shutdown() }
    }

    /** Draws a scene, handing [block] a way to render and save the current frame. */
    private fun render(app: QuarkApp, width: Int, height: Int, block: ((String) -> Unit) -> Unit) {
        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            QuarkRoot(app, TestPlatform)
        }
        var time = 0L
        try {
            block { name ->
                // A few frames, so enter animations and effects have run.
                repeat(4) {
                    time += 250_000_000L
                    scene.render(time)
                }
                val image = scene.render(time)
                val out = File("build/preview/$name.png")
                out.parentFile.mkdirs()
                out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
                assertTrue(out.length() > 0, "$name was not drawn")
                // A small copy, quicker to page through.
                val small = File("build/preview/small/$name.jpg")
                small.parentFile.mkdirs()
                small.writeBytes(shrink(image, 0.5f))
            }
        } finally {
            scene.close()
        }
    }

    private fun shrink(image: Image, scale: Float): ByteArray {
        val width = (image.width * scale).toInt().coerceAtLeast(1)
        val height = (image.height * scale).toInt().coerceAtLeast(1)
        val surface = Surface.makeRasterN32Premul(width, height)
        surface.canvas.drawImageRect(
            image,
            Rect.makeWH(image.width.toFloat(), image.height.toFloat()),
            Rect.makeWH(width.toFloat(), height.toFloat()),
            SamplingMode.LINEAR,
            null,
            true,
        )
        val bytes = surface.makeImageSnapshot().encodeToData(EncodedImageFormat.JPEG, 70)!!.bytes
        surface.close()
        return bytes
    }

    private fun testApp(settings: SettingsStore): QuarkApp {
        val dir = Files.createTempDirectory("quark-ui").toFile()
        val http = defaultHttpClient()
        val host = QuarkHost(
            kind = DeviceKind.Desktop,
            paths = StoragePaths(
                support = dir.resolve("support").path,
                cache = dir.resolve("cache").path,
                separator = File.separator,
                exports = dir.resolve("exports").path,
            ),
            settings = settings,
            database = DatabaseFactory.inMemory(),
            http = http,
            library = DesktopLibrary(LibraryScanner()),
            images = ImageStore { null },
            engine = SilentEngine(),
            downloader = TrackCacher(http),
            io = Dispatchers.Unconfined,
            main = Dispatchers.Unconfined,
        )
        return QuarkApp(host).also { app ->
            app.start()
            runBlocking {
                app.userLibrary.create("Road trip", tracks)
                app.userLibrary.create("Empty")
                app.playback.open(Playlist(name = "Animals", tracks = tracks), tracks[1])
                app.controller.enqueueLast(tracks[3])
            }
        }
    }
}

private fun track(title: String, artist: String, album: String): Track = LocalTrack(
    title = title,
    artists = listOf(artist),
    albums = listOf(album),
    filepath = "/music/${title.lowercase().replace(' ', '-')}.flac",
    coverType = CoverType.BuiltIn,
    durationMs = 245_000,
)

private object TestPlatform : Platform {
    override val kind: DeviceKind = DeviceKind.Desktop
    override fun openUrl(url: String) = Unit
    override suspend fun pickFolder(): String? = null
    override suspend fun pickAudioFiles(): List<String> = emptyList()
    override suspend fun pickFile(extensions: List<String>): String? = null
    override val deviceLibrary: String? = null
}

private class SilentEngine : AudioEngine {
    private val mutableEvents = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 8)
    override val events: SharedFlow<EngineEvent> = mutableEvents.asSharedFlow()
    override suspend fun play(source: MediaSource) = Unit
    override suspend fun preload(source: MediaSource) = Unit
    override suspend fun prepare(source: MediaSource) = Unit
    override suspend fun pause() = Unit
    override suspend fun resume() = Unit
    override suspend fun stop() = Unit
    override suspend fun seek(to: Duration) = Unit
    override suspend fun setVolume(volume: Float) = Unit
    override suspend fun setSpeed(speed: Float) = Unit
    override suspend fun release() = Unit
}

private class MemorySettings(initial: Settings) : SettingsStore {
    private val mutable = MutableStateFlow(initial)
    override val settings = mutable.asStateFlow()
    override fun update(transform: (Settings) -> Settings) = mutable.update(transform)
    override suspend fun close() = Unit
}
