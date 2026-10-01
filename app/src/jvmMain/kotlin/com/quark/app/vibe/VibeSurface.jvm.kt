package com.quark.app.vibe

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder

/**
 * Skia's runtime effect. Uniforms are set by name through
 * [RuntimeShaderBuilder] rather than packed into a buffer, because a wrong
 * `float3` alignment shows up only as the wrong colours.
 */
@Composable
actual fun VibeSurface(frame: () -> VibeUniforms, modifier: Modifier) {
    val effect = remember { runCatching { RuntimeEffect.makeForShader(VIBE_SKSL) }.getOrNull() }
    val builder = remember(effect) { effect?.let(::RuntimeShaderBuilder) }

    if (builder == null) {
        // No shader support: the screen still works, it just does not glow.
        VibeFallback(frame, modifier)
        return
    }

    Canvas(modifier) {
        val uniforms = frame()
        builder.uniform("uScreenSize", size.width, size.height)
        builder.uniform("uTime", uniforms.time)
        builder.uniform("uScale", uniforms.scale)
        builder.uniform("uBgBrightness", uniforms.backgroundBrightness)
        uniforms.colors.forEachIndexed { index, colour ->
            builder.uniform("uColor$index", colour.red, colour.green, colour.blue)
        }
        uniforms.rotations.forEachIndexed { index, (x, y, z) ->
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
