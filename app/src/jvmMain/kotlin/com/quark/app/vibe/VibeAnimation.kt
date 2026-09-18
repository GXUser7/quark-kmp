package com.quark.app.vibe

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.withFrameMillis
import com.quark.app.theme.AccentColors
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.random.Random

/**
 * My Vibe's visualiser: three blobs of colour turning over each other.
 *
 * Drives [VIBE_SKSL] from a frame clock, as the Flutter version drove it from an
 * endless `AnimationController`. Time wraps at a day so the float stays small
 * enough to keep its precision — the original wrapped at 86400 for the same
 * reason.
 *
 * Colours come from the current cover rather than the fixed hue the Dart build
 * started from: the visualiser sits behind the artwork, and taking its palette
 * makes the two agree.
 */
@Composable
fun VibeAnimation(
    accent: AccentColors,
    modifier: Modifier = Modifier,
    scale: Float = 0.38f,
    speed: Float = 1f,
    backgroundBrightness: Float = 0f,
) {
    val effect = remember { runCatching { RuntimeEffect.makeForShader(VIBE_SKSL) }.getOrNull() }
    val builder = remember(effect) { effect?.let(::RuntimeShaderBuilder) }

    // Fixed per composition: the original seeds three random axes once and lets
    // them turn, so the motion is the same shape every time but never aligned.
    val rotations = remember {
        val random = Random(SEED)
        List(3) {
            Triple(
                random.nextFloat() * 2f - 1f,
                random.nextFloat() * 2f - 1f,
                random.nextFloat() * 0.4f + 0.1f,
            )
        }
    }

    var time by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(speed) {
        var previous = 0L
        while (true) {
            withFrameMillis { now ->
                if (previous != 0L) {
                    time = (time + speed * (now - previous) / 1000f) % DAY_SECONDS
                }
                previous = now
            }
        }
    }

    if (builder == null) {
        // No shader support: the screen still works, it just does not glow.
        return
    }

    val palette = accent.asSixColours()

    Canvas(modifier) {
        builder.uniform("uScreenSize", size.width, size.height)
        builder.uniform("uTime", time)
        builder.uniform("uScale", scale)
        builder.uniform("uBgBrightness", backgroundBrightness)

        palette.forEachIndexed { index, colour ->
            builder.uniform("uColor$index", colour.red, colour.green, colour.blue)
        }
        rotations.forEachIndexed { index, (x, y, z) ->
            builder.uniform("uRotation$index", x, y, z)
        }

        val paint = Paint().apply { shader = builder.makeShader() }
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.drawRect(
                org.jetbrains.skia.Rect.makeWH(size.width, size.height),
                paint,
            )
        }
        paint.close()
    }
}

/**
 * The shader wants six colours: three for the inner edge of each blob and three
 * for the outer. The cover gives three, so each is paired with a darker version
 * of itself, which is what the original's HSL ramp amounted to.
 */
private fun AccentColors.asSixColours(): List<Color> = listOf(
    primary,
    secondary,
    tertiary,
    primary.darken(),
    secondary.darken(),
    tertiary.darken(),
)

private fun Color.darken(amount: Float = 0.45f) =
    Color(red * (1f - amount), green * (1f - amount), blue * (1f - amount), alpha)

private const val DAY_SECONDS = 86_400f

/** Fixed so the motion is reproducible; nothing here wants true randomness. */
private const val SEED = 20260918
