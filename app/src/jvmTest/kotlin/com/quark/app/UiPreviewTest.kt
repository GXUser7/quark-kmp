package com.quark.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.Density
import com.quark.app.image.Cover
import com.quark.app.image.CoverBlur
import com.quark.app.player.LibraryStatus
import com.quark.app.player.PlayerScreen
import com.quark.app.player.PlayerUi
import com.quark.app.theme.AccentColors
import com.quark.app.theme.Quark
import com.quark.app.theme.QuarkTheme
import com.quark.app.ui.Backdrop
import com.quark.app.ui.LocalBackdrop
import com.quark.app.ui.backdropBackground
import com.quark.core.color.AccentPalette
import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.PlaylistInfo
import com.quark.core.model.Track
import com.quark.core.player.PlayerState
import com.quark.core.player.RepeatMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Color4f
import org.jetbrains.skia.EncodedImageFormat
import org.jetbrains.skia.Image
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.Surface
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Renders the player off-screen and writes a png.
 *
 * Not an assertion about pixels — it is a way to look at the interface without
 * opening a window, which is how the layout and the glass get checked while they
 * are being built. The file lands in `build/preview` and nothing depends on it.
 */
class UiPreviewTest {

    @Test
    fun renders_the_player() {
        val width = 1100
        val height = 760
        val cover = syntheticCover()
        val ui = PreviewPlayerUi(cover)

        val scene = ImageComposeScene(width = width, height = height, density = Density(1f)) {
            val backdrop = Backdrop(cover.blurred, Size(width.toFloat(), height.toFloat()))
            QuarkTheme(accent = cover.accent) {
                CompositionLocalProvider(LocalBackdrop provides backdrop) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .backdropBackground(
                                backdrop,
                                Quark.colors.backgroundScrim,
                                Quark.colors.background,
                            )
                    ) {
                        PlayerScreen(ui)
                    }
                }
            }
        }

        val image = scene.render()
        val out = File("build/preview/player.png")
        out.parentFile.mkdirs()
        out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()

        assertTrue(out.length() > 0, "preview was not written")
        println("preview: ${out.absolutePath}")
    }

    /** Three colour bands, so the accent extraction has something to find. */
    private fun syntheticCover(): Cover {
        val size = 600
        val surface = Surface.makeRasterN32Premul(size, size)
        val bands = listOf(
            Color4f(0.16f, 0.11f, 0.32f, 1f),
            Color4f(0.36f, 0.19f, 0.44f, 1f),
            Color4f(0.64f, 0.33f, 0.39f, 1f),
        )
        bands.forEachIndexed { index, colour ->
            surface.canvas.drawRect(
                Rect.makeXYWH(index * size / 3f, 0f, size / 3f, size.toFloat()),
                Paint().apply { color4f = colour },
            )
        }
        val image: Image = surface.makeImageSnapshot()
        surface.close()

        val bitmap = image.toComposeImageBitmap()
        val pixels = IntArray(bitmap.width * bitmap.height).also(bitmap::readPixels)
        val palette = AccentPalette.extract(pixels, bitmap.width, bitmap.height)

        return Cover(
            image = bitmap,
            thumbnail = CoverBlur.thumbnail(image),
            blurred = CoverBlur.blur(image),
            accent = if (palette.size == AccentPalette.ZONES) {
                AccentColors(
                    primary = androidx.compose.ui.graphics.Color(palette[0]),
                    secondary = androidx.compose.ui.graphics.Color(palette[1]),
                    tertiary = androidx.compose.ui.graphics.Color(palette[2]),
                )
            } else {
                AccentColors()
            },
        )
    }
}

/** A [PlayerUi] with fixed state and no engine behind it. */
private class PreviewPlayerUi(cover: Cover) : PlayerUi {

    private val tracks = listOf(
        track("Dogs", "Pink Floyd", "Animals"),
        track("Pigs (Three Different Ones)", "Pink Floyd", "Animals"),
        track("Sheep", "Pink Floyd", "Animals"),
        track("Heroes", "David Bowie", "\"Heroes\""),
        track("Aria Math", "bxkq, Hardx", "Unknown"),
        track("Washing Machine Heart", "Mitski", "Be the Cowboy"),
        track("Du Hast", "Rammstein", "Sehnsucht"),
        track("The Man Who Made a Monster", "Dance With the Dead", "Loved to Death"),
    )

    override val state: StateFlow<PlayerState> = MutableStateFlow(
        PlayerState(
            current = tracks[4],
            playlist = tracks,
            playlistInfo = PlaylistInfo(name = "Local"),
            queue = listOf(tracks[1]),
            position = 6.seconds,
            duration = 245.seconds,
            isPlaying = true,
            repeat = RepeatMode.Off,
            volume = 0.62f,
        )
    )

    override val cover: StateFlow<Cover?> = MutableStateFlow(cover)
    override val status: StateFlow<LibraryStatus> = MutableStateFlow(LibraryStatus.Idle)
    override val scrubbing: StateFlow<Duration?> = MutableStateFlow(null)

    private val thumbnail = cover.thumbnail

    @Composable
    override fun rememberThumbnail(track: Track): State<ImageBitmap?> =
        remember(track.filepath) { mutableStateOf<ImageBitmap?>(thumbnail) }

    override fun play(track: Track) = Unit
    override fun playPause() = Unit
    override fun next() = Unit
    override fun previous() = Unit
    override fun scrub(to: Duration) = Unit
    override fun commitScrub() = Unit
    override fun setVolume(volume: Float) = Unit
    override fun toggleShuffle() = Unit
    override fun toggleRepeat() = Unit
    override fun enqueue(track: Track) = Unit
    override fun clearQueue() = Unit
}

private fun track(title: String, artist: String, album: String): Track = LocalTrack(
    title = title,
    artists = listOf(artist),
    albums = listOf(album),
    filepath = "/music/${title.lowercase().replace(' ', '-')}.flac",
    coverType = CoverType.BuiltIn,
)
