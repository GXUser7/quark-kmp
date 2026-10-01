package com.quark.app.vibe

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.quark.app.theme.AccentColors
import kotlin.random.Random

/** One frame's worth of the shader's inputs. */
class VibeUniforms(
    val time: Float,
    val scale: Float,
    val backgroundBrightness: Float,
    /** Inner edge of each blob, then outer edge of each: six in all. */
    val colors: List<Color>,
    /** An axis and a speed per blob. */
    val rotations: List<Triple<Float, Float, Float>>,
)

/**
 * Draws [VIBE_SKSL] with the platform's runtime shaders: Skia's on the desktop,
 * AGSL on Android 13 and later, and a plainer gradient where neither exists.
 * [frame] is read inside the draw phase, so the animation redraws without
 * recomposing anything.
 */
@Composable
expect fun VibeSurface(frame: () -> VibeUniforms, modifier: Modifier)

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
    val palette = remember(accent) { accent.asSixColours() }

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

    VibeSurface(
        frame = { VibeUniforms(time, scale, backgroundBrightness, palette, rotations) },
        modifier = modifier,
    )
}

/**
 * The shader wants six colours: three for the inner edge of each blob and three
 * for the outer. The cover gives three, so each is paired with a darker version
 * of itself, which is what the original's HSL ramp amounted to.
 */
fun AccentColors.asSixColours(): List<Color> = listOf(
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
