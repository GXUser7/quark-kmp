package com.quark.app.image

/**
 * Blurs ARGB [pixels] in place: [passes] box blurs, which together approach a
 * Gaussian. Used where there is no GPU blur to lean on — Android cannot blur a
 * bitmap directly since RenderScript went away.
 *
 * Each pass runs along rows and writes the result transposed, so the second
 * half of a pass is the same loop again over what were columns.
 */
fun boxBlur(pixels: IntArray, width: Int, height: Int, radius: Int, passes: Int = 3) {
    if (radius < 1 || width < 1 || height < 1) return
    require(pixels.size >= width * height) { "pixel buffer too small" }
    val buffer = IntArray(width * height)
    repeat(passes) {
        blurRows(pixels, buffer, width, height, radius)
        blurRows(buffer, pixels, height, width, radius)
    }
}

/** Box-blurs each row of [source] (w×h) and writes it transposed into [target] (h×w). */
private fun blurRows(source: IntArray, target: IntArray, w: Int, h: Int, r: Int) {
    val window = r * 2 + 1
    val last = w - 1
    for (y in 0 until h) {
        val row = y * w
        var a = 0
        var red = 0
        var green = 0
        var blue = 0
        for (i in -r..r) {
            val p = source[row + i.coerceIn(0, last)]
            a += p ushr 24
            red += (p shr 16) and 0xFF
            green += (p shr 8) and 0xFF
            blue += p and 0xFF
        }
        for (x in 0 until w) {
            target[x * h + y] = ((a / window) shl 24) or
                ((red / window) shl 16) or
                ((green / window) shl 8) or
                (blue / window)
            val leaving = source[row + (x - r).coerceIn(0, last)]
            val entering = source[row + (x + r + 1).coerceIn(0, last)]
            a += (entering ushr 24) - (leaving ushr 24)
            red += ((entering shr 16) and 0xFF) - ((leaving shr 16) and 0xFF)
            green += ((entering shr 8) and 0xFF) - ((leaving shr 8) and 0xFF)
            blue += (entering and 0xFF) - (leaving and 0xFF)
        }
    }
}
