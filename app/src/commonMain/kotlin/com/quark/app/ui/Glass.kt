package com.quark.app.ui

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * The blurred cover filling the window, and the size it is drawn at.
 *
 * Glass surfaces sample this, offset by their own position, which is how they
 * show what is behind them. Compose has no `BackdropFilter`: `Modifier.blur`
 * blurs a composable's own content, not the backdrop. Since the backdrop here is
 * one known image, sampling it directly gives the same result and costs nothing.
 */
@Immutable
class Backdrop(val image: ImageBitmap?, val windowSize: Size)

val LocalBackdrop = compositionLocalOf { Backdrop(null, Size.Zero) }

/**
 * Draws the backdrop as it appears at [bounds] — the same picture the window
 * shows there, so a panel lines up with what it covers.
 */
private fun DrawScope.drawBackdrop(backdrop: Backdrop, bounds: Rect) {
    val image = backdrop.image ?: return
    val window = backdrop.windowSize
    if (window.width <= 0f || window.height <= 0f) return

    val destination = coverRect(
        content = Size(image.width.toFloat(), image.height.toFloat()),
        container = window,
    )

    translate(left = -bounds.left, top = -bounds.top) {
        drawImage(
            image = image,
            dstOffset = IntOffset(destination.left.roundToInt(), destination.top.roundToInt()),
            dstSize = IntSize(destination.width.roundToInt(), destination.height.roundToInt()),
        )
    }
}

/** `BoxFit.cover`: fill the container, overflow on the long axis, stay centred. */
fun coverRect(content: Size, container: Size): Rect {
    if (content.width <= 0f || content.height <= 0f) return Rect(0f, 0f, container.width, container.height)
    val scale = max(container.width / content.width, container.height / content.height)
    val width = content.width * scale
    val height = content.height * scale
    return Rect(
        left = (container.width - width) / 2f,
        top = (container.height - height) / 2f,
        right = (container.width - width) / 2f + width,
        bottom = (container.height - height) / 2f + height,
    )
}

/**
 * A translucent surface over the blurred cover: the player's one recurring
 * shape. [glass] picks which of the original's three recipes to use.
 */
@Composable
fun GlassSurface(
    shape: Shape,
    modifier: Modifier = Modifier,
    glass: Glass = Glass.Card,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    val fallback = Quark.colors.background
    val recipe = if (Quark.colors.isLight) remember(glass) { glass.forLight() } else glass
    var bounds by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .clip(shape)
            .drawBehind {
                // With no current cover there is nothing opaque to sample. If
                // we only draw the translucent tint, controls below a modal
                // sheet remain sharp and readable through it. The original's
                // BackdropFilter always supplied an opaque backdrop.
                if (backdrop.image == null) drawRect(fallback)
                else drawBackdrop(backdrop, bounds)
                drawRect(recipe.tint)
                recipe.brush?.let { drawRect(it) }
            }
            .border(1.dp, recipe.border, shape),
        content = content,
    )
}

/**
 * The window's own background: the blurred cover, darkened.
 *
 * Drawn once behind everything, and sampled again by every [GlassSurface].
 */
fun Modifier.backdropBackground(backdrop: Backdrop, scrim: Color, fallback: Color): Modifier =
    drawBehind {
        val image = backdrop.image
        if (image == null) {
            drawRect(fallback)
            return@drawBehind
        }
        val destination = coverRect(
            content = Size(image.width.toFloat(), image.height.toFloat()),
            container = size,
        )
        drawImage(
            image = image,
            dstOffset = IntOffset(destination.left.roundToInt(), destination.top.roundToInt()),
            dstSize = IntSize(destination.width.roundToInt(), destination.height.roundToInt()),
        )
        drawRect(scrim)
    }
