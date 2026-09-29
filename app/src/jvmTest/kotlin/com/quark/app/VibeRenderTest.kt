package com.quark.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import com.quark.app.theme.AccentColors
import com.quark.app.theme.QuarkTheme
import com.quark.app.ui.CometLoader
import com.quark.app.vibe.VibeAnimation
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Draws the two animated surfaces.
 *
 * Both talk to Skia directly — one builds a runtime shader per frame, the other
 * paints gradients — and a mistake there surfaces at draw time, where a window
 * would only show it in an error dialog. Rendering them here turns that into a
 * failing test.
 */
class VibeRenderTest {

    @Test
    fun the_visualiser_draws() {
        render("vibe.png") {
            QuarkTheme {
                Box(Modifier.fillMaxSize()) {
                    VibeAnimation(
                        accent = AccentColors(),
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
        }
    }

    @Test
    fun the_comet_loader_draws() {
        render("comets.png") {
            QuarkTheme {
                Box(Modifier.fillMaxSize()) {
                    CometLoader(Modifier.fillMaxSize())
                }
            }
        }
    }

    private fun render(name: String, content: @androidx.compose.runtime.Composable () -> Unit) {
        val scene = ImageComposeScene(width = 700, height = 500, density = Density(1f), content = content)
        // Two frames: the animation clocks off the frame callback, so the first
        // one only starts it.
        scene.render()
        val image = scene.render(2_000_000L)
        val out = File("build/preview/" + name)
        out.parentFile.mkdirs()
        out.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        scene.close()
        assertTrue(out.length() > 0)
    }
}
