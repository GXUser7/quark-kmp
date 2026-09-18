package com.quark.app.player

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.quark.core.model.Track
import com.quark.core.player.PlayerState
import kotlin.time.Duration

/**
 * Picks the layout for the window's size.
 *
 * The thresholds are the ones from `playlist_page_router.dart`: below 80px tall
 * the window is a control strip, up to 300px it is a compact player, and above
 * that the full one.
 */
@Composable
fun PlayerScreen(model: PlayerViewModel, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val cover by model.cover.collectAsState()
    val status by model.status.collectAsState()

    BoxWithConstraints(modifier.fillMaxSize()) {
        val height = maxHeight
        when {
            height < 80.dp -> MacroPlayer(state, model)
            height <= 300.dp -> CompactPlayer(state, cover, model)
            else -> FullPlayer(state, cover, status, model)
        }
    }
}

@Composable
private fun FullPlayer(
    state: PlayerState,
    cover: com.quark.app.image.Cover?,
    status: LibraryStatus,
    model: PlayerViewModel,
) {
    val accent by model.accent.collectAsState()

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(
                        accent.primary.copy(alpha = 0.22f),
                        MaterialTheme.colorScheme.background,
                    )
                )
            )
    ) {
        Row(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).padding(32.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                CoverArt(cover, Modifier.weight(1f, fill = false).fillMaxWidth())
                NowPlayingText(state.current)
                SeekBar(state, model)
                TransportControls(state, model, large = true)
                VolumeBar(state, model)
            }

            Column(
                Modifier
                    .width(360.dp)
                    .fillMaxHeight()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                LibraryHeader(state, status, model)
                TrackList(state, model, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun CompactPlayer(
    state: PlayerState,
    cover: com.quark.app.image.Cover?,
    model: PlayerViewModel,
) {
    Row(
        Modifier.fillMaxSize().padding(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CoverArt(cover, Modifier.size(96.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            NowPlayingText(state.current, compact = true)
            SeekBar(state, model)
            TransportControls(state, model, large = false)
        }
    }
}

/** The strip the window becomes when it is squashed to nothing. */
@Composable
private fun MacroPlayer(state: PlayerState, model: PlayerViewModel) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TransportControls(state, model, large = false)
        Text(
            text = state.current.title,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = "${state.position.display()} / ${state.duration.display()}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CoverArt(cover: com.quark.app.image.Cover?, modifier: Modifier = Modifier) {
    Box(
        modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val image = cover?.image
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Filled.PlayArrow,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                modifier = Modifier.size(48.dp),
            )
        }
    }
}

@Composable
private fun NowPlayingText(track: Track, compact: Boolean = false) {
    Column {
        Text(
            text = track.title,
            style = if (compact) MaterialTheme.typography.titleMedium
            else MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = track.artistLine,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LibraryHeader(state: PlayerState, status: LibraryStatus, model: PlayerViewModel) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(state.playlistInfo.name, style = MaterialTheme.typography.titleSmall)
            Text(
                text = when (status) {
                    is LibraryStatus.Scanning -> "Reading ${status.read} of ${status.found}"
                    is LibraryStatus.Failed -> status.message
                    LibraryStatus.Idle -> "${state.playlist.size} tracks"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (status is LibraryStatus.Scanning) {
            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
        }
    }
}

@Composable
private fun TrackList(state: PlayerState, model: PlayerViewModel, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val currentIndex = state.currentIndex

    // Follow the music: when it moves on by itself, bring it into view.
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.scrollToItem(currentIndex)
    }

    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(state.playlist) { track ->
            TrackRow(
                track = track,
                playing = track == state.current,
                queued = track in state.queue,
                onPlay = { model.play(track) },
                onQueue = { model.enqueue(track) },
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    playing: Boolean,
    queued: Boolean,
    onPlay: () -> Unit,
    onQueue: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (playing) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent
            )
            .clickable(onClick = onPlay)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                track.title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                track.artistLine,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (queued) {
            Box(
                Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
            )
        }
        IconButton(onClick = onQueue, modifier = Modifier.size(28.dp)) {
            Text("+", style = MaterialTheme.typography.titleMedium)
        }
    }
}

/** `m:ss`, and `h:mm:ss` once a track is long enough to need it. */
internal fun Duration.display(): String {
    val total = inWholeSeconds
    val seconds = total % 60
    val minutes = (total / 60) % 60
    val hours = total / 3600
    return if (hours > 0) {
        "$hours:${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
    } else {
        "$minutes:${seconds.toString().padStart(2, '0')}"
    }
}
