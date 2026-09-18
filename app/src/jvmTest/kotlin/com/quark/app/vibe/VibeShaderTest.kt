package com.quark.app.vibe

import org.jetbrains.skia.RuntimeEffect
import org.jetbrains.skia.RuntimeShaderBuilder
import kotlin.test.Test
import kotlin.test.assertNotNull

/**
 * SkSL is compiled by Skia at runtime, and a mistake in it does not fail the
 * build — the effect simply comes back null and the visualiser renders nothing.
 * This is the only place that would notice.
 */
class VibeShaderTest {

    @Test
    fun the_shader_compiles() {
        val effect = RuntimeEffect.makeForShader(VIBE_SKSL)
        assertNotNull(effect)
        effect.close()
    }

    @Test
    fun every_uniform_the_renderer_sets_exists() {
        val effect = RuntimeEffect.makeForShader(VIBE_SKSL)
        val builder = RuntimeShaderBuilder(effect)

        // Setting an unknown uniform throws, so this fails if a name drifts
        // between the shader and the code that feeds it.
        builder.uniform("uScreenSize", 1920f, 1080f)
        builder.uniform("uTime", 12.5f)
        builder.uniform("uScale", 0.38f)
        builder.uniform("uBgBrightness", 0f)
        repeat(6) { index -> builder.uniform("uColor$index", 0.5f, 0.4f, 0.6f) }
        repeat(3) { index -> builder.uniform("uRotation$index", 0.2f, -0.3f, 0.15f) }

        val shader = builder.makeShader()
        assertNotNull(shader)
        shader.close()
        effect.close()
    }
}
