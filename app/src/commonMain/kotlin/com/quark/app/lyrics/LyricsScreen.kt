package com.quark.app.lyrics

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.quark.app.i18n.strings
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.CircleButton
import com.quark.app.ui.Divider
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.QText
import com.quark.core.lyrics.LyricLine

/**
 * The words, following the music.
 *
 * The active line is brighter and larger, and the list scrolls to keep it in the
 * middle — which is the whole point of synced lyrics and the only reason to show
 * them in a player rather than a browser.
 */
@Composable
fun LyricsScreen(
    state: LyricsState,
    activeLine: Int,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val active = activeLine

    GlassSurface(
        shape = RoundedCornerShape(Radius.panel),
        glass = Glass.Panel,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QText(strings.lyrics, Quark.type.panelTitle, Modifier.weight(1f))
                CircleButton(
                    icon = Icons.Filled.Close,
                    onClick = onClose,
                    diameter = 28.dp,
                    iconSize = 18.dp,
                    filled = false,
                )
            }
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            when (val current = state) {
                is LyricsState.Ready -> LyricsBody(current, active)
                LyricsState.Loading -> Message(strings.lyricsLoading)
                is LyricsState.Unavailable -> Message(current.reason)
                LyricsState.Idle -> Message(strings.nothingPlaying)
            }
        }
    }
}

@Composable
private fun LyricsBody(state: LyricsState.Ready, active: Int) {
    val listState = rememberLazyListState()
    val lines = state.lyrics.lines

    if (lines.isEmpty()) {
        Message("No lyrics for this track.")
        return
    }

    // Keep the current line around a third of the way down, which reads better
    // than the very top and leaves the next lines visible.
    LaunchedEffect(active) {
        if (active >= 0) listState.animateScrollToItem(active, scrollOffset = -160)
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        state = listState,
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 24.dp),
    ) {
        itemsIndexed(lines) { index, line ->
            LyricRow(line = line, active = index == active, synced = state.lyrics.synced)
        }
    }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, synced: Boolean) {
    val colors = Quark.colors

    // Unsynced words have no "current" line, so they are all shown equally.
    val target = when {
        !synced -> colors.textSecondary
        active -> colors.text
        else -> colors.textMuted
    }
    val color by animateColorAsState(target, tween(250))
    val scale by animateFloatAsState(if (active && synced) 1f else 0.94f, tween(250))

    QText(
        text = line.text,
        style = Quark.type.body.copy(
            fontSize = 22.sp * scale,
            fontWeight = if (active && synced) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Start,
        ),
        color = color,
        modifier = Modifier.fillMaxWidth().alpha(if (line.text.isEmpty()) 0f else 1f),
    )
}

@Composable
private fun Message(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        QText(text, Quark.type.body, color = Quark.colors.textSecondary)
    }
}
