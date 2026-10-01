package com.quark.app.image

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.quark.app.theme.Quark
import com.quark.app.ui.QIcon
import com.quark.core.model.Track

/** The one cover loader, so every list shares its cache. */
val LocalCovers = staticCompositionLocalOf<CoverLoader?> { null }

/** A playlist's, album's or artist's picture by address, loaded once and cached. */
@Composable
fun rememberPicture(url: String?, size: Int = 360): State<ImageBitmap?> {
    val loader = LocalCovers.current
    val image = remember(url, size) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(url, size, loader) {
        if (loader != null && !url.isNullOrBlank()) image.value = loader.picture(url, size)
    }
    return image
}

/** The small cover of a track for a list row. */
@Composable
fun rememberThumbnail(track: Track): State<ImageBitmap?> {
    val loader = LocalCovers.current
    val image = remember(track.filepath, track.cover) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(track.filepath, track.cover, loader) {
        if (loader != null) image.value = loader.thumbnail(track)
    }
    return image
}

/** A picture clipped to [shape], or a glyph on a plain tile while there is none. */
@Composable
fun Artwork(
    image: ImageBitmap?,
    shape: Shape,
    modifier: Modifier = Modifier,
    placeholder: ImageVector = Icons.Filled.MusicNote,
) {
    Box(
        modifier.clip(shape).background(Quark.colors.control),
        contentAlignment = Alignment.Center,
    ) {
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            QIcon(placeholder, Modifier.fillMaxWidth(0.4f).size(40.dp), Quark.colors.textMuted.copy(alpha = 0.5f))
        }
    }
}
