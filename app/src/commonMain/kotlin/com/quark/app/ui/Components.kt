package com.quark.app.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import kotlin.math.roundToInt

/**
 * A glyph, tinted.
 *
 * The shapes come from the Material icon set because the original uses
 * `material_symbols_icons` for the same job; what is left behind is Material's
 * `Icon`, which drags its theme along with it.
 */
@Composable
fun QIcon(
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = Quark.colors.text,
) {
    Image(
        painter = rememberVectorPainter(icon),
        contentDescription = null,
        modifier = modifier,
        colorFilter = ColorFilter.tint(tint),
    )
}

@Composable
fun QText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    color: Color = Quark.colors.text,
    maxLines: Int = Int.MAX_VALUE,
) {
    BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color),
        maxLines = maxLines,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * The transport control: a translucent circle that lightens under the pointer.
 *
 * [active] is for the toggles — shuffle and repeat — which the original marks by
 * brightening the glyph rather than tinting the circle.
 */
@Composable
fun CircleButton(
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    diameter: Dp = 44.dp,
    iconSize: Dp = 20.dp,
    active: Boolean = false,
    filled: Boolean = true,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val pressed by interaction.collectIsPressedAsState()
    val colors = Quark.colors

    val background by animateColorAsState(
        when {
            !filled -> Color.Transparent
            hovered || pressed -> colors.controlHover
            else -> colors.control
        }
    )
    val tint by animateColorAsState(
        when {
            !enabled -> colors.textMuted.copy(alpha = 0.3f)
            active || hovered -> colors.text
            else -> colors.textSecondary
        }
    )

    Box(
        modifier
            .size(diameter)
            .clip(CircleShape)
            .background(background)
            .hoverable(interaction, enabled = enabled)
            .clickable(
                enabled = enabled,
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        QIcon(icon, Modifier.size(iconSize), tint)
    }
}

/**
 * The wide rounded buttons on the start screen and in dialogs. [accent] fills
 * it with the cover's colour for the one action a screen is about, [danger]
 * tints it for the ones that delete.
 */
@Composable
fun PillButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    accent: Boolean = false,
    danger: Boolean = false,
    height: Dp = 45.dp,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = Quark.colors
    val accentColor = Quark.accent.primary
    val background by animateColorAsState(
        when {
            accent -> accentColor.copy(alpha = if (hovered) 0.55f else 0.4f)
            danger -> colors.danger.copy(alpha = if (hovered) 0.3f else 0.18f)
            hovered && enabled -> colors.controlHover
            else -> colors.control
        }
    )
    val content = when {
        !enabled -> colors.textMuted.copy(alpha = 0.4f)
        danger -> colors.danger
        else -> colors.text
    }

    Row(
        modifier
            .height(height)
            .clip(RoundedCornerShape(Radius.control))
            .background(background)
            .hoverable(interaction, enabled)
            .clickable(enabled = enabled, interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        if (icon != null) QIcon(icon, Modifier.size(18.dp), content)
        QText(text, Quark.type.button, color = content, maxLines = 1)
    }
}

/**
 * The thin progress and volume line.
 *
 * Written rather than taken from Material because the shape is the point: a
 * hairline track, a filled portion, and a handle that only appears under the
 * pointer. Dragging reports continuously through [onChange] and once more
 * through [onCommit] on release, so the caller can keep the engine out of it
 * until the user lets go — seeking on every drag event would have mpv answering
 * with a position that snaps the handle back.
 */
@Composable
fun ThinSlider(
    value: Float,
    onChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    onCommit: (Float) -> Unit = {},
    enabled: Boolean = true,
    trackHeight: Dp = 4.dp,
    fill: Color = Quark.colors.text,
) {
    val colors = Quark.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var trackWidth by remember { mutableFloatStateOf(1f) }
    var dragging by remember { mutableStateOf(false) }
    val density = LocalDensity.current

    val fraction = value.coerceIn(0f, 1f)
    val handleSize by animateDpAsState(if ((hovered || dragging) && enabled) 12.dp else 0.dp)

    Box(
        modifier
            .height(20.dp)
            .hoverable(interaction, enabled = enabled)
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                detectTapGestures { offset ->
                    val target = (offset.x / size.width).coerceIn(0f, 1f)
                    onChange(target)
                    onCommit(target)
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                var position = fraction
                detectHorizontalDragGestures(
                    onDragStart = { start ->
                        dragging = true
                        position = (start.x / size.width).coerceIn(0f, 1f)
                        onChange(position)
                    },
                    onDragEnd = {
                        dragging = false
                        onCommit(position)
                    },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    position = (change.position.x / size.width).coerceIn(0f, 1f)
                    onChange(position)
                }
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Canvas(Modifier.fillMaxWidth().height(trackHeight)) {
            trackWidth = size.width
            val y = size.height / 2
            drawLine(
                color = colors.track,
                start = Offset(0f, y),
                end = Offset(size.width, y),
                strokeWidth = size.height,
                cap = StrokeCap.Round,
            )
            if (fraction > 0f) {
                drawLine(
                    color = fill,
                    start = Offset(0f, y),
                    end = Offset(size.width * fraction, y),
                    strokeWidth = size.height,
                    cap = StrokeCap.Round,
                )
            }
        }

        if (handleSize > 0.dp) {
            val radiusPx = with(density) { handleSize.toPx() / 2f }
            Box(
                Modifier
                    .offset { IntOffset((trackWidth * fraction - radiusPx).roundToInt(), 0) }
                    .size(handleSize)
                    .clip(CircleShape)
                    .background(colors.text)
            )
        }
    }
}

/** A hairline rule in the divider colour. */
@Composable
fun Divider(modifier: Modifier = Modifier, vertical: Boolean = false) {
    val color = Quark.colors.divider
    Box(
        modifier
            .then(if (vertical) Modifier.width(1.dp) else Modifier.fillMaxWidth().height(1.dp))
            .background(color)
    )
}
