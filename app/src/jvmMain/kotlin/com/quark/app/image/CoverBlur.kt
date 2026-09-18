package com.quark.app.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
import org.jetbrains.skia.SamplingMode
import org.jetbrains.skia.Surface
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The window background: the cover, shrunk and blurred into a wash of colour.
 *
 * The recipe is the Dart build's — downscale to 150 px wide, then blur hard
 * (`cached_images.dart:158-176`). Shrinking first is what makes it cheap and is
 * most of the effect: by the time the result is stretched across the window it
 * is pure gradient, and no detail of the artwork survives to distract from the
 * text over it.
 */
object CoverBlur {

    /** Width the cover is reduced to before blurring, as in the original. */
    private const val WORKING_WIDTH = 150

    /**
     * Skia takes a Gaussian sigma where the Dart `image` package took a kernel
     * radius of 25. Sigma is roughly a third of the radius for a comparable
     * spread, which on a 150 px image is already most of the frame.
     */
    private const val SIGMA = 8f

    /** A small copy for list rows, where the full cover is wasted work. */
    fun thumbnail(source: Image, size: Int = 96): ImageBitmap {
        val scale = size.toFloat() / max(max(source.width, source.height), 1)
        if (scale >= 1f) return source.toComposeImageBitmap()

        val width = max((source.width * scale).roundToInt(), 1)
        val height = max((source.height * scale).roundToInt(), 1)
        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            surface.canvas.drawImageRect(
                image = source,
                src = Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
                dst = Rect.makeWH(width.toFloat(), height.toFloat()),
                samplingMode = SamplingMode.LINEAR,
                paint = null,
                strict = true,
            )
            return surface.makeImageSnapshot().toComposeImageBitmap()
        } finally {
            surface.close()
        }
    }

    fun blur(source: Image): ImageBitmap {
        val scale = WORKING_WIDTH.toFloat() / max(source.width, 1)
        val width = WORKING_WIDTH.coerceAtMost(source.width)
        val height = max((source.height * scale).roundToInt(), 1)

        val surface = Surface.makeRasterN32Premul(width, height)
        try {
            val paint = Paint().apply {
                imageFilter = ImageFilter.makeBlur(SIGMA, SIGMA, FilterTileMode.CLAMP)
            }
            surface.canvas.drawImageRect(
                image = source,
                src = Rect.makeWH(source.width.toFloat(), source.height.toFloat()),
                dst = Rect.makeWH(width.toFloat(), height.toFloat()),
                samplingMode = SamplingMode.LINEAR,
                paint = paint,
                strict = true,
            )
            return surface.makeImageSnapshot().toComposeImageBitmap()
        } finally {
            surface.close()
        }
    }
}
