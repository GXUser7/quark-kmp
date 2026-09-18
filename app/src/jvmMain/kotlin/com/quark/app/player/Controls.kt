package com.quark.app.player

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.quark.core.player.PlayerState
import com.quark.core.player.RepeatMode
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Position, with the handle owned by the user while they hold it.
 *
 * Seeking on every drag event would have the engine fighting the finger: mpv
 * answers each seek with a new position, which snaps the handle back. So the
 * drag is local and the seek happens once, on release.
 */
@Composable
fun SeekBar(state: PlayerState, model: PlayerViewModel) {
    val scrubbing by model.scrubbing.collectAsState()
    val shown = scrubbing ?: state.position
    val total = state.duration.inWholeSeconds.coerceAtLeast(1).toFloat()

    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = shown.inWholeSeconds.toFloat().coerceIn(0f, total),
            onValueChange = { model.scrub(it.toLong().seconds) },
            onValueChangeFinished = model::commitScrub,
            valueRange = 0f..total,
            enabled = state.hasTrack && state.duration > Duration.ZERO,
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Label(shown.display())
            Label(state.duration.display())
        }
    }
}

@Composable
fun TransportControls(state: PlayerState, model: PlayerViewModel, large: Boolean) {
    val size = if (large) 56.dp else 40.dp

    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = model::toggleShuffle) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Shuffle",
                tint = if (state.isShuffled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        IconButton(onClick = model::previous) {
            // One arrow glyph, mirrored, rather than two icon dependencies.
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = "Previous",
                modifier = Modifier.graphicsLayer(scaleX = -1f),
            )
        }

        FilledIconButton(onClick = model::playPause, modifier = Modifier.size(size)) {
            Text(
                text = if (state.isPlaying) "II" else "▶",
                style = MaterialTheme.typography.titleMedium,
            )
        }

        IconButton(onClick = model::next) {
            Icon(Icons.Filled.PlayArrow, contentDescription = "Next")
        }

        IconButton(onClick = model::toggleRepeat) {
            Icon(
                Icons.Filled.Refresh,
                contentDescription = "Repeat",
                tint = if (state.repeat == RepeatMode.One) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun VolumeBar(state: PlayerState, model: PlayerViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Label("Vol")
        Slider(
            value = state.volume,
            onValueChange = { model.setVolume(it) },
            modifier = Modifier.width(160.dp),
        )
        Label("${(state.volume * 100).toInt()}%")
    }
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}
