package com.quark.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.quark.app.theme.Quark

/** What a menu's entries can do: close the menu they are in. */
class MenuScope(val close: () -> Unit)

/**
 * The three-dots button and the menu it opens, which is how the Flutter build
 * offered everything one could do with a track (`PlaylistTileWidget`).
 */
@Composable
fun MenuButton(
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.MoreVert,
    diameter: Dp = 28.dp,
    iconSize: Dp = 18.dp,
    filled: Boolean = false,
    items: @Composable MenuScope.() -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        CircleButton(icon, { open = true }, diameter = diameter, iconSize = iconSize, filled = filled)
        if (open) PopupMenu(onDismiss = { open = false }, items = items)
    }
}

/** A menu anchored to its parent, flipped above it when there is no room below. */
@Composable
fun PopupMenu(onDismiss: () -> Unit, items: @Composable MenuScope.() -> Unit) {
    val scope = remember(onDismiss) { MenuScope(onDismiss) }
    Popup(
        popupPositionProvider = remember { AnchoredMenuPosition() },
        onDismissRequest = onDismiss,
        properties = PopupProperties(focusable = true),
    ) {
        MenuSurface { scope.items() }
    }
}

@Composable
fun MenuSurface(content: @Composable ColumnScope.() -> Unit) {
    val colors = Quark.colors
    val shape = RoundedCornerShape(12.dp)
    Column(
        Modifier
            .widthIn(min = 200.dp, max = 320.dp)
            .heightIn(max = 480.dp)
            .shadow(16.dp, shape)
            .clip(shape)
            .background(colors.menu)
            .border(1.dp, colors.divider, shape)
            .verticalScroll(rememberScrollState())
            .padding(6.dp),
        content = content,
    )
}

/** An entry of a [PopupMenu]; choosing it closes the menu. */
@Composable
fun MenuScope.Item(
    text: String,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    ListRow(
        text = text,
        icon = icon,
        enabled = enabled,
        danger = danger,
        onClick = {
            close()
            onClick()
        },
    )
}

@Composable
fun MenuScope.Separator() {
    Divider(Modifier.padding(vertical = 4.dp))
}

private class AnchoredMenuPosition : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val margin = 8
        val maxX = (windowSize.width - popupContentSize.width - margin).coerceAtLeast(margin)
        val maxY = (windowSize.height - popupContentSize.height - margin).coerceAtLeast(margin)
        val x = (anchorBounds.right - popupContentSize.width).coerceIn(margin, maxX)
        val below = anchorBounds.bottom
        val y = if (below + popupContentSize.height <= windowSize.height - margin) {
            below
        } else {
            anchorBounds.top - popupContentSize.height
        }
        return IntOffset(x, y.coerceIn(margin, maxY))
    }
}

/** Keeps a fixed-width column of menu-like content from stretching. */
fun Modifier.menuWidth(): Modifier = width(260.dp)
