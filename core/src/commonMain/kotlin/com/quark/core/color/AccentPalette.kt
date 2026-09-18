package com.quark.core.color

/**
 * Picks the three colours a cover is tinted with, for the window chrome and
 * background gradients.
 *
 * Ported from `AccentColorService._getColors` in
 * `lib/services/dynamic_window_color_linux.dart`: skip the top band, split the
 * rest into three vertical zones, average each. It is not a clustering
 * algorithm and does not try to be — averaging left, middle and right gives a
 * gradient that follows the artwork's own left-to-right composition, which is
 * what a wide window header wants.
 */
object AccentPalette {

    /** Covers often have a title or a logo across the top; it is not the mood. */
    private const val TOP_SKIP = 0.15f

    const val ZONES = 3

    /**
     * [pixels] is row-major ARGB, [width] pixels per row.
     * Returns [ZONES] packed ARGB colours, left to right.
     */
    fun extract(pixels: IntArray, width: Int, height: Int): List<Int> {
        if (width <= 0 || height <= 0 || pixels.size < width * height) return emptyList()

        val firstRow = (height * TOP_SKIP).toInt().coerceIn(0, height - 1)
        val zoneWidth = width / ZONES
        if (zoneWidth == 0) return emptyList()

        return List(ZONES) { zone ->
            val from = zone * zoneWidth
            val to = if (zone == ZONES - 1) width else from + zoneWidth
            averageOf(pixels, width, firstRow, height, from, to)
        }
    }

    private fun averageOf(
        pixels: IntArray,
        width: Int,
        firstRow: Int,
        height: Int,
        fromColumn: Int,
        toColumn: Int,
    ): Int {
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0L

        for (y in firstRow until height) {
            val rowStart = y * width
            for (x in fromColumn until toColumn) {
                val pixel = pixels[rowStart + x]
                val alpha = (pixel ushr 24) and 0xFF
                // Fully transparent pixels carry no colour worth averaging.
                if (alpha == 0) continue
                red += (pixel ushr 16) and 0xFF
                green += (pixel ushr 8) and 0xFF
                blue += pixel and 0xFF
                count++
            }
        }

        if (count == 0L) return OPAQUE_BLACK
        return pack((red / count).toInt(), (green / count).toInt(), (blue / count).toInt())
    }

    private fun pack(red: Int, green: Int, blue: Int): Int =
        (0xFF shl 24) or (red shl 16) or (green shl 8) or blue

    private const val OPAQUE_BLACK = 0xFF000000.toInt()
}
