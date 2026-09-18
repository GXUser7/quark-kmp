package com.quark.app.image

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.quark.app.theme.AccentColors
import com.quark.core.color.AccentPalette
import com.quark.core.model.CoverType
import com.quark.core.model.Track
import com.quark.data.local.TagReader
import androidx.compose.ui.graphics.Color
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import java.util.Collections

/** A decoded cover and the colours taken from it. */
data class Cover(val image: ImageBitmap, val accent: AccentColors)

/**
 * Decodes cover art and keeps a bounded number of them around.
 *
 * Covers are large and a library view scrolls past hundreds of them, so the
 * cache is an LRU with a hard cap rather than the unbounded maps the Dart build
 * kept on its singletons.
 */
class CoverLoader(private val capacity: Int = DEFAULT_CAPACITY) {

    private val cache: MutableMap<String, Cover> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Cover>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cover>) =
                size > capacity
        }
    )

    suspend fun load(track: Track): Cover? {
        val key = track.cacheKey() ?: return null
        cache[key]?.let { return it }

        val bytes = withContext(Dispatchers.IO) { readBytes(track) } ?: return null
        val cover = withContext(Dispatchers.Default) { decode(bytes) } ?: return null

        cache[key] = cover
        return cover
    }

    private fun readBytes(track: Track): ByteArray? = when (track.coverType) {
        CoverType.BuiltIn -> TagReader.readArtwork(File(track.filepath))

        CoverType.ExternalFile -> File(track.cover).takeIf(File::isFile)?.readBytes()

        // Remote covers are fetched and cached by the network layer, which
        // writes them to disk; nothing to do here until then.
        CoverType.Url, CoverType.NoCover -> null
    }

    private fun decode(bytes: ByteArray): Cover? = try {
        val image = Image.makeFromEncoded(bytes)
        val bitmap = image.toComposeImageBitmap()
        Cover(bitmap, bitmap.accentColors())
    } catch (e: Exception) {
        null
    }

    private fun Track.cacheKey(): String? = when (coverType) {
        CoverType.BuiltIn -> filepath
        CoverType.ExternalFile, CoverType.Url -> cover
        CoverType.NoCover -> null
    }

    private companion object {
        const val DEFAULT_CAPACITY = 64
    }
}

/**
 * Reads the bitmap's pixels once and hands them to [AccentPalette]. Sampling a
 * downscaled copy would be cheaper, but covers are at most a few megapixels and
 * this runs once per track, not per frame.
 */
private fun ImageBitmap.accentColors(): AccentColors {
    val pixels = IntArray(width * height)
    readPixels(pixels)

    val extracted = AccentPalette.extract(pixels, width, height)
    if (extracted.size < AccentPalette.ZONES) return AccentColors()

    return AccentColors(
        primary = Color(extracted[0]),
        secondary = Color(extracted[1]),
        tertiary = Color(extracted[2]),
    )
}
