package com.quark.app.image

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import com.quark.app.theme.AccentColors
import com.quark.core.color.AccentPalette
import com.quark.core.model.CoverType
import com.quark.core.model.Track
import com.quark.data.images.CoverCache
import com.quark.data.local.TagReader
import com.quark.data.repository.CoverColorRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.skia.Image
import java.io.File
import java.security.MessageDigest
import java.util.Collections

/**
 * A decoded cover: the artwork itself, the blurred wash that fills the window
 * behind it, and the colours taken off it.
 */
data class Cover(
    val image: ImageBitmap,
    val thumbnail: ImageBitmap,
    val blurred: ImageBitmap,
    val accent: AccentColors,
)

/**
 * Decodes cover art and keeps a bounded number of them around.
 *
 * Covers are large and a library view scrolls past hundreds of them, so the
 * cache is an LRU with a hard cap rather than the unbounded maps the Dart build
 * kept on its singletons.
 */
class CoverLoader(
    private val remote: CoverCache? = null,
    private val palettes: CoverColorRepository? = null,
    private val capacity: Int = DEFAULT_CAPACITY,
) {

    private val cache: MutableMap<String, Cover> = Collections.synchronizedMap(
        object : LinkedHashMap<String, Cover>(16, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Cover>) =
                size > capacity
        }
    )

    /** Tracks already known to have no artwork, so the file is not reopened. */
    private val empty: MutableSet<String> = Collections.synchronizedSet(mutableSetOf<String>())

    suspend fun load(track: Track): Cover? {
        val key = track.cacheKey() ?: return null
        cache[key]?.let { return it }
        if (key in empty) return null

        val bytes = readBytes(track)
        if (bytes == null) {
            empty += key
            return null
        }

        val cover = decode(bytes)
        if (cover == null) {
            empty += key
            return null
        }

        cache[key] = cover
        return cover
    }

    private suspend fun readBytes(track: Track): ByteArray? = when (track.coverType) {
        CoverType.BuiltIn -> withContext(Dispatchers.IO) {
            TagReader.readArtwork(File(track.filepath))
        }

        CoverType.ExternalFile -> withContext(Dispatchers.IO) {
            File(track.cover).takeIf(File::isFile)?.readBytes()
        }

        // Downloaded once and kept on disk, so scrolling a remote playlist
        // twice does not fetch the same artwork twice.
        CoverType.Url -> remote?.get(track.cover)

        CoverType.NoCover -> null
    }

    private suspend fun decode(bytes: ByteArray): Cover? {
        val hash = bytes.md5()
        val stored = try {
            palettes?.get(hash)?.takeIf { it.size >= AccentPalette.ZONES }
        } catch (_: Exception) {
            null
        }

        val decoded = withContext(Dispatchers.Default) {
            try {
                val image = Image.makeFromEncoded(bytes)
                val bitmap = image.toComposeImageBitmap()
                val colors = stored ?: bitmap.accentPalette()
                DecodedCover(
                    cover = Cover(
                        image = bitmap,
                        thumbnail = CoverBlur.thumbnail(image),
                        blurred = CoverBlur.blur(image),
                        accent = colors.toAccentColors(),
                    ),
                    calculatedColors = colors.takeIf { stored == null },
                )
            } catch (_: Exception) {
                null
            }
        } ?: return null

        decoded.calculatedColors?.takeIf { it.size >= AccentPalette.ZONES }?.let { colors ->
            try {
                palettes?.put(hash, colors)
            } catch (_: Exception) {
                // A cache write must never make an otherwise valid cover vanish.
            }
        }
        return decoded.cover
    }

    private fun Track.cacheKey(): String? = when (coverType) {
        CoverType.BuiltIn -> filepath
        CoverType.ExternalFile, CoverType.Url -> cover
        CoverType.NoCover -> null
    }

    private companion object {
        const val DEFAULT_CAPACITY = 64
    }

    private data class DecodedCover(
        val cover: Cover,
        val calculatedColors: List<Int>?,
    )
}

/**
 * Reads the bitmap's pixels once and hands them to [AccentPalette]. Sampling a
 * downscaled copy would be cheaper, but covers are at most a few megapixels and
 * this runs once per track, not per frame.
 */
private fun ImageBitmap.accentPalette(): List<Int> {
    val pixels = IntArray(width * height)
    readPixels(pixels)
    return AccentPalette.extract(pixels, width, height)
}

private fun List<Int>.toAccentColors(): AccentColors {
    if (size < AccentPalette.ZONES) return AccentColors()

    return AccentColors(
        primary = Color(this[0]),
        secondary = Color(this[1]),
        tertiary = Color(this[2]),
    )
}

private fun ByteArray.md5(): String {
    val digest = MessageDigest.getInstance("MD5").digest(this)
    val alphabet = "0123456789abcdef"
    return buildString(digest.size * 2) {
        for (byte in digest) {
            val value = byte.toInt() and 0xFF
            append(alphabet[value ushr 4])
            append(alphabet[value and 0x0F])
        }
    }
}
