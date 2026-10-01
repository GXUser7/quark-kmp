package com.quark.app.image

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import com.quark.app.theme.AccentColors
import com.quark.core.color.AccentPalette
import com.quark.core.model.CoverType
import com.quark.core.model.Track
import com.quark.data.images.ImageStore
import com.quark.data.local.LocalLibrary
import com.quark.data.repository.CoverColorRepository
import com.quark.network.md5Hex
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

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
 * Decoding, shrinking and blurring, which each platform does with its own
 * graphics library: Skia on the desktop, `android.graphics` on Android.
 */
expect object ImageCodec {
    /** Decodes [bytes], no larger than [maxSize] on the long side; null when they are not an image. */
    fun decode(bytes: ByteArray, maxSize: Int = 2048): ImageBitmap?

    /** A copy no larger than [size] on its long side. */
    fun thumbnail(image: ImageBitmap, size: Int = 96): ImageBitmap

    /** The window wash: the cover shrunk to 150 px wide and blurred hard. */
    fun blur(image: ImageBitmap): ImageBitmap
}

/**
 * Decodes cover art and keeps a bounded number of them around.
 *
 * Covers are large and a library view scrolls past hundreds of them, so the
 * cache is an LRU with a hard cap rather than the unbounded maps the Dart build
 * kept on its singletons.
 */
class CoverLoader(
    private val library: LocalLibrary,
    private val remote: ImageStore? = null,
    private val palettes: CoverColorRepository? = null,
    private val capacity: Int = DEFAULT_CAPACITY,
    private val decoding: CoroutineDispatcher = Dispatchers.Default,
) {
    private val lock = Mutex()
    private val cache = LinkedHashMap<String, Cover>()
    private val thumbnails = LinkedHashMap<String, ImageBitmap>()
    private val pictures = LinkedHashMap<String, ImageBitmap>()

    /** Tracks already known to have no artwork, so the file is not reopened. */
    private val empty = mutableSetOf<String>()

    suspend fun load(track: Track): Cover? {
        val key = track.coverKey() ?: return null
        lock.withLock {
            cache.remove(key)?.let { hit ->
                cache[key] = hit
                return hit
            }
            if (key in empty) return null
        }

        val bytes = bytesOf(track)
        val cover = bytes?.let { decode(it) }
        lock.withLock {
            if (cover == null) {
                empty += key
            } else {
                cache[key] = cover
                trim(cache, capacity)
            }
        }
        return cover
    }

    /**
     * Only the small copy, for list rows. Cheaper than [load] because it skips
     * the blur and the palette, and a list asks for hundreds of these.
     */
    suspend fun thumbnail(track: Track): ImageBitmap? {
        val key = track.coverKey() ?: return null
        lock.withLock {
            cache[key]?.let { return it.thumbnail }
            thumbnails.remove(key)?.let { hit ->
                thumbnails[key] = hit
                return hit
            }
            if (key in empty) return null
        }
        val bytes = bytesOf(track)
        val image = bytes?.let {
            withContext(decoding) {
                runCatching { ImageCodec.decode(it, THUMBNAIL_DECODE)?.let { full -> ImageCodec.thumbnail(full) } }
                    .getOrNull()
            }
        }
        lock.withLock {
            if (image == null) empty += key
            else {
                thumbnails[key] = image
                trim(thumbnails, THUMBNAIL_CAPACITY)
            }
        }
        return image
    }

    /**
     * A picture that is not a track's cover — a playlist's, an album's, an
     * artist's — fetched once through the image store and decoded at [size].
     */
    suspend fun picture(url: String, size: Int = PICTURE_SIZE): ImageBitmap? {
        if (url.isBlank()) return null
        val key = "$size:$url"
        lock.withLock {
            pictures.remove(key)?.let { hit ->
                pictures[key] = hit
                return hit
            }
            if (key in empty) return null
        }
        val bytes = try {
            remote?.get(url)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            null
        }
        val image = bytes?.let {
            withContext(decoding) { runCatching { ImageCodec.decode(it, size) }.getOrNull() }
        }
        lock.withLock {
            if (image == null) empty += key
            else {
                pictures[key] = image
                trim(pictures, PICTURE_CAPACITY)
            }
        }
        return image
    }

    private suspend fun bytesOf(track: Track): ByteArray? = try {
        when (track.coverType) {
            CoverType.BuiltIn, CoverType.ExternalFile -> library.artwork(track)
            // Downloaded once and kept on disk, so scrolling a remote playlist
            // twice does not fetch the same artwork twice.
            CoverType.Url -> remote?.get(track.cover)
            CoverType.NoCover -> null
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        null
    }

    private suspend fun decode(bytes: ByteArray): Cover? {
        val hash = md5Hex(bytes)
        val stored = try {
            palettes?.get(hash)?.takeIf { it.size >= AccentPalette.ZONES }
        } catch (_: Exception) {
            null
        }

        val decoded = withContext(decoding) {
            try {
                val bitmap = ImageCodec.decode(bytes) ?: return@withContext null
                val colors = stored ?: bitmap.accentPalette()
                DecodedCover(
                    cover = Cover(
                        image = bitmap,
                        thumbnail = ImageCodec.thumbnail(bitmap),
                        blurred = ImageCodec.blur(bitmap),
                        accent = colors.toAccentColors(),
                    ),
                    calculatedColors = colors.takeIf { stored == null },
                )
            } catch (_: Exception) {
                null
            } catch (_: OutOfMemoryError) {
                // A 6000 px scan on a phone: better no cover than no app.
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

    private fun Track.coverKey(): String? = when (coverType) {
        CoverType.BuiltIn -> filepath
        CoverType.ExternalFile, CoverType.Url -> cover.takeIf(String::isNotEmpty)
        CoverType.NoCover -> null
    }

    private fun <V> trim(map: LinkedHashMap<String, V>, limit: Int) {
        while (map.size > limit) {
            val eldest = map.keys.firstOrNull() ?: return
            map.remove(eldest)
        }
    }

    private companion object {
        const val DEFAULT_CAPACITY = 32
        const val THUMBNAIL_CAPACITY = 400
        const val THUMBNAIL_DECODE = 256
        const val PICTURE_SIZE = 360
        const val PICTURE_CAPACITY = 160
    }

    private data class DecodedCover(
        val cover: Cover,
        val calculatedColors: List<Int>?,
    )
}

/**
 * Reads the bitmap's pixels once and hands them to [AccentPalette]. Covers are
 * decoded at most 2048 px on a side, and this runs once per track, not per frame.
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
