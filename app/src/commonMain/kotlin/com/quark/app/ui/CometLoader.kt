package com.quark.app.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import kotlin.random.Random

/**
 * The loading animation: streaks crossing the frame, fading out behind them.
 *
 * A port of `widgets/comets.dart`. Forty of them, each with its own speed,
 * length and weight, respawning on the left when they leave on the right. The
 * trail comes from a gradient along the streak rather than from painting over
 * the previous frame, which is how the Dart version faked it and which forces a
 * full-surface redraw every frame.
 */
@Composable
fun CometLoader(
    modifier: Modifier = Modifier,
    count: Int = 40,
    colour: Color = Color.White,
) {
    val comets = remember(count) { Comets(count) }
    var frame by remember { mutableIntStateOf(0) }

    LaunchedEffect(comets) {
        var previous = 0L
        while (true) {
            withFrameMillis { now ->
                val delta = if (previous == 0L) 0f else (now - previous) / 1000f
                previous = now
                comets.advance(delta)
                frame++
            }
        }
    }

    Canvas(modifier) {
        // Read so the canvas redraws with the clock.
        @Suppress("UNUSED_EXPRESSION") frame

        comets.layOut(size.width, size.height)
        for (comet in comets.all) {
            val start = Offset(comet.x - comet.length, comet.y)
            val end = Offset(comet.x, comet.y)
            drawLine(
                brush = Brush.linearGradient(
                    colors = listOf(colour.copy(alpha = 0f), colour.copy(alpha = comet.opacity)),
                    start = start,
                    end = end,
                ),
                start = start,
                end = end,
                strokeWidth = comet.width,
                cap = StrokeCap.Round,
            )
        }
    }
}

internal class Comet(
    var x: Float,
    var y: Float,
    var speed: Float,
    var length: Float,
    var opacity: Float,
    var width: Float,
)

/**
 * The flock. Positions are in pixels, so the first frame lays them out once the
 * canvas size is known — before that the surface has no width to spread across.
 */
internal class Comets(private val count: Int, seed: Int = 0) {

    private val random = if (seed == 0) Random.Default else Random(seed)
    private var laidOut = false
    private var width = 0f
    private var height = 0f

    val all: List<Comet> = List(count) {
        Comet(x = 0f, y = 0f, speed = 0f, length = 0f, opacity = 0f, width = 0f)
    }

    fun layOut(width: Float, height: Float) {
        if (laidOut && width == this.width && height == this.height) return
        this.width = width
        this.height = height
        laidOut = true
        // Spread the first generation across the frame instead of starting them
        // all at the left edge, which would look like one pulse.
        all.forEach { comet ->
            respawn(comet)
            comet.x = random.nextFloat() * width
        }
    }

    fun advance(seconds: Float) {
        if (!laidOut || seconds <= 0f) return
        for (comet in all) {
            comet.x += comet.speed * seconds
            if (comet.x - comet.length > width) respawn(comet)
        }
    }

    private fun respawn(comet: Comet) {
        comet.length = 40f + random.nextFloat() * 120f
        comet.x = -comet.length
        comet.y = random.nextFloat() * height
        comet.speed = 100f + random.nextFloat() * 400f
        comet.opacity = 0.4f + random.nextFloat() * 0.6f
        comet.width = 0.5f + random.nextFloat() * 1.5f
    }
}
