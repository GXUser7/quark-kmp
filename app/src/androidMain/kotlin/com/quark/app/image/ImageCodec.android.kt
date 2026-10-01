package com.quark.app.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * `android.graphics` does the decoding and scaling; the blur is [boxBlur] on
 * the pixels, since a phone has no bitmap blur to call any more.
 *
 * Decoding samples down by powers of two first: a 3000 px cover decoded at full
 * size is 36 MB of pixels, which is how music players on phones run out of
 * memory.
 */
actual object ImageCodec {

    private const val WORKING_WIDTH = 150
    private const val BLUR_RADIUS = 8

    actual fun decode(bytes: ByteArray, maxSize: Int): ImageBitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSize) sample *= 2
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options) ?: return null
        return fit(bitmap, maxSize).asImageBitmap()
    }

    actual fun thumbnail(image: ImageBitmap, size: Int): ImageBitmap {
        val bitmap = image.asAndroidBitmap()
        if (max(bitmap.width, bitmap.height) <= size) return image
        return fit(bitmap, size).asImageBitmap()
    }

    actual fun blur(image: ImageBitmap): ImageBitmap {
        val bitmap = image.asAndroidBitmap()
        val width = min(WORKING_WIDTH, bitmap.width).coerceAtLeast(1)
        val height = max((bitmap.height * width.toFloat() / max(bitmap.width, 1)).roundToInt(), 1)
        val small = Bitmap.createScaledBitmap(bitmap, width, height, true)
            .let { if (it.isMutable && it.config == Bitmap.Config.ARGB_8888) it else it.copy(Bitmap.Config.ARGB_8888, true) }
        val pixels = IntArray(width * height)
        small.getPixels(pixels, 0, width, 0, 0, width, height)
        boxBlur(pixels, width, height, BLUR_RADIUS)
        small.setPixels(pixels, 0, width, 0, 0, width, height)
        return small.asImageBitmap()
    }

    private fun fit(bitmap: Bitmap, maxSize: Int): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxSize) return bitmap
        val scale = maxSize.toFloat() / longest
        return Bitmap.createScaledBitmap(
            bitmap,
            max((bitmap.width * scale).roundToInt(), 1),
            max((bitmap.height * scale).roundToInt(), 1),
            true,
        )
    }
}
