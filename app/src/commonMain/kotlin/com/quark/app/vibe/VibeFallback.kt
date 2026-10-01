package com.quark.app.vibe

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * The visualiser without a runtime shader: three soft discs in the cover's
 * colours, circling on the same axes the shader's blobs turn on. Not the real
 * thing, but it moves with the music's colours and costs three gradients.
 */
@Composable
fun VibeFallback(frame: () -> VibeUniforms, modifier: Modifier) {
    Canvas(modifier) {
        val uniforms = frame()
        val centre = Offset(size.width / 2f, size.height / 2f)
        val base = min(size.width, size.height) * uniforms.scale * 1.4f
        uniforms.rotations.forEachIndexed { index, (x, y, speed) ->
            val angle = uniforms.time * speed * 2f + index * 2.1f
            val orbit = base * 0.25f
            val position = Offset(
                centre.x + (x * orbit) * cos(angle) - (y * orbit) * sin(angle),
                centre.y + (x * orbit) * sin(angle) + (y * orbit) * cos(angle),
            )
            val radius = base * (1f - index * 0.18f)
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        uniforms.colors[index].copy(alpha = 0.55f),
                        uniforms.colors[index + 3].copy(alpha = 0.25f),
                        Color.Transparent,
                    ),
                    center = position,
                    radius = radius,
                ),
                radius = radius,
                center = position,
            )
        }
    }
}
