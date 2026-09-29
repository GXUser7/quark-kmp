package com.quark.app.vibe

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ShaderBrush

/**
 * AGSL is Android's name for SkSL, so the desktop shader runs unchanged on
 * Android 13 and later. Older phones get [VibeFallback].
 */
@Composable
actual fun VibeSurface(frame: () -> VibeUniforms, modifier: Modifier) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        val shader = remember { createShader() }
        if (shader != null) {
            ShaderCanvas(shader, frame, modifier)
            return
        }
    }
    VibeFallback(frame, modifier)
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private fun createShader(): RuntimeShader? = runCatching { RuntimeShader(VIBE_SKSL) }.getOrNull()

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun ShaderCanvas(shader: RuntimeShader, frame: () -> VibeUniforms, modifier: Modifier) {
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        val uniforms = frame()
        shader.setFloatUniform("uScreenSize", size.width, size.height)
        shader.setFloatUniform("uTime", uniforms.time)
        shader.setFloatUniform("uScale", uniforms.scale)
        shader.setFloatUniform("uBgBrightness", uniforms.backgroundBrightness)
        uniforms.colors.forEachIndexed { index, colour ->
            shader.setFloatUniform("uColor$index", colour.red, colour.green, colour.blue)
        }
        uniforms.rotations.forEachIndexed { index, (x, y, z) ->
            shader.setFloatUniform("uRotation$index", x, y, z)
        }
        drawRect(brush)
    }
}
