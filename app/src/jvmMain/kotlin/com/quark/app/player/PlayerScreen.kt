package com.quark.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quark.app.image.Cover
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.CircleButton
import com.quark.app.ui.Divider
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.QIcon
import com.quark.app.ui.QText
import com.quark.app.ui.ThinSlider
import com.quark.core.model.Track
import com.quark.core.player.PlayerState
import com.quark.core.player.RepeatMode
import kotlin.time.Duration

/** Width of the playlist panel, and how far it pushes the player across. */
private val PANEL_WIDTH = 400.dp

/**
 * Chooses a layout for the window's size.
 *
 * The thresholds are the ones from `playlist_page_router.dart`: under 80px tall
 * the window is a control strip, up to 300px a compact card, above that the full
 * player. The panel only appears when there is room for it, as in the original's
 * `playerPadding` rule.
 */
@Composable
fun PlayerScreen(model: PlayerUi, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()
    val cover by model.cover.collectAsState()
    val status by model.status.collectAsState()
    var panelOpen by remember { mutableStateOf(true) }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomForPanel = maxWidth > 810.dp
        when {
            maxHeight < 80.dp -> MacroPlayer(state, model)
            maxHeight <= 300.dp -> CompactPlayer(state, cover, model)
            else -> FullPlayer(
                state = state,
                cover = cover,
                status = status,
                model = model,
                panelOpen = panelOpen && roomForPanel,
                onTogglePanel = { panelOpen = !panelOpen },
            )
        }
    }
}

@Composable
private fun FullPlayer(
    state: PlayerState,
    cover: Cover?,
    status: LibraryStatus,
    model: PlayerUi,
    panelOpen: Boolean,
    onTogglePanel: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        NowPlaying(
            state = state,
            cover = cover,
            model = model,
            panelOpen = panelOpen,
            onTogglePanel = onTogglePanel,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = if (panelOpen) PANEL_WIDTH else 0.dp),
        )

        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInHorizontally { -it } + fadeIn(),
            exit = slideOutHorizontally { -it } + fadeOut(),
        ) {
            PlaylistPanel(
                state = state,
                status = status,
                model = model,
                onClose = onTogglePanel,
                modifier = Modifier.width(PANEL_WIDTH).fillMaxHeight().padding(12.dp),
            )
        }
    }
}

@Composable
private fun NowPlaying(
    state: PlayerState,
    cover: Cover?,
    model: PlayerUi,
    panelOpen: Boolean,
    onTogglePanel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverArt(cover, Modifier.size(270.dp))

        Spacer(Modifier.height(20.dp))

        QText(
            text = state.current.title,
            style = Quark.type.nowPlayingTitle.copy(textAlign = TextAlign.Center),
            maxLines = 1,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        QText(
            text = state.current.albumLine,
            style = Quark.type.nowPlayingAlbum.copy(textAlign = TextAlign.Center),
            color = Quark.colors.textSecondary,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 520.dp),
        )
        QText(
            text = state.current.artistLine,
            style = Quark.type.nowPlayingArtist.copy(textAlign = TextAlign.Center),
            color = Quark.colors.textMuted,
            maxLines = 1,
            modifier = Modifier.widthIn(max = 520.dp),
        )

        Spacer(Modifier.height(18.dp))
        SeekRow(state, model, Modifier.widthIn(max = 420.dp))
        Spacer(Modifier.height(14.dp))
        TransportRow(state, model)
        Spacer(Modifier.height(16.dp))
        VolumeRow(state, model, Modifier.widthIn(max = 260.dp))
        Spacer(Modifier.height(18.dp))
        SecondaryRow(state, model, panelOpen, onTogglePanel)
    }
}

/**
 * The artwork, with the shadow the original gives it. Cross-fades between
 * tracks rather than popping, which is what the 650ms `AnimatedSwitcher` in
 * `main_player.dart` is doing.
 */
@Composable
private fun CoverArt(cover: Cover?, modifier: Modifier = Modifier) {
    Crossfade(targetState = cover, animationSpec = tween(650)) { shown ->
        Box(
            modifier
                .shadow(20.dp, RoundedCornerShape(Radius.cover), clip = false)
                .clip(RoundedCornerShape(Radius.cover))
                .background(Color(0x33000000)),
            contentAlignment = Alignment.Center,
        ) {
            val image = shown?.image
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                QIcon(
                    Icons.Filled.MusicNote,
                    Modifier.size(64.dp),
                    Quark.colors.textMuted.copy(alpha = 0.4f),
                )
            }
        }
    }
}

@Composable
private fun SeekRow(state: PlayerState, model: PlayerUi, modifier: Modifier = Modifier) {
    val scrubbing by model.scrubbing.collectAsState()
    val shown = scrubbing ?: state.position
    val total = state.duration

    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QText(shown.display(), Quark.type.label, color = Quark.colors.textSecondary)
        ThinSlider(
            value = if (total > Duration.ZERO) {
                (shown.inWholeMilliseconds.toFloat() / total.inWholeMilliseconds)
            } else {
                0f
            },
            onChange = { fraction -> model.scrub(total * fraction.toDouble()) },
            onCommit = { model.commitScrub() },
            enabled = state.hasTrack && total > Duration.ZERO,
            modifier = Modifier.weight(1f),
        )
        QText(total.display(), Quark.type.label, color = Quark.colors.textSecondary)
    }
}

@Composable
private fun TransportRow(state: PlayerState, model: PlayerUi) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(Icons.Filled.SkipPrevious, model::previous, diameter = 44.dp)
        CircleButton(
            icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            onClick = model::playPause,
            diameter = 52.dp,
            iconSize = 26.dp,
        )
        CircleButton(Icons.Filled.SkipNext, model::next, diameter = 44.dp)
    }
}

@Composable
private fun VolumeRow(state: PlayerState, model: PlayerUi, modifier: Modifier = Modifier) {
    Row(
        modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QIcon(Icons.Filled.VolumeDown, Modifier.size(18.dp), Quark.colors.textMuted)
        ThinSlider(
            value = state.volume,
            onChange = model::setVolume,
            modifier = Modifier.weight(1f),
        )
        QIcon(Icons.Filled.VolumeUp, Modifier.size(18.dp), Quark.colors.textMuted)
    }
}

@Composable
private fun SecondaryRow(
    state: PlayerState,
    model: PlayerUi,
    panelOpen: Boolean,
    onTogglePanel: () -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        CircleButton(
            icon = Icons.Filled.QueueMusic,
            onClick = onTogglePanel,
            diameter = 36.dp,
            iconSize = 18.dp,
            active = panelOpen,
        )
        CircleButton(
            icon = Icons.Filled.Shuffle,
            onClick = model::toggleShuffle,
            diameter = 36.dp,
            iconSize = 18.dp,
            active = state.isShuffled,
        )
        CircleButton(
            icon = if (state.repeat == RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
            onClick = model::toggleRepeat,
            diameter = 36.dp,
            iconSize = 18.dp,
            active = state.repeat == RepeatMode.One,
        )
    }
}

// --- Playlist panel ----------------------------------------------------------

@Composable
private fun PlaylistPanel(
    state: PlayerState,
    status: LibraryStatus,
    model: PlayerUi,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    GlassSurface(
        shape = RoundedCornerShape(Radius.panel),
        glass = Glass.Card,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            PanelHeader(state, status, onClose)
            Spacer(Modifier.height(10.dp))
            Divider()
            Spacer(Modifier.height(6.dp))
            TrackList(state, model, Modifier.weight(1f))
        }
    }
}

@Composable
private fun PanelHeader(state: PlayerState, status: LibraryStatus, onClose: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        QIcon(Icons.Filled.Search, Modifier.size(20.dp), Quark.colors.textSecondary)
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QText(state.playlistInfo.name, Quark.type.panelTitle, maxLines = 1)
            QText(
                text = when (status) {
                    is LibraryStatus.Scanning -> "Reading ${status.read} of ${status.found}"
                    is LibraryStatus.Failed -> status.message
                    LibraryStatus.Idle -> "${state.playlist.size} tracks"
                },
                style = Quark.type.label,
                color = Quark.colors.textMuted,
                maxLines = 1,
            )
        }
        CircleButton(
            icon = Icons.Filled.Close,
            onClick = onClose,
            diameter = 28.dp,
            iconSize = 18.dp,
            filled = false,
        )
    }
}

@Composable
private fun TrackList(state: PlayerState, model: PlayerUi, modifier: Modifier = Modifier) {
    val listState = rememberLazyListState()
    val currentIndex = state.currentIndex

    // Follow the music: when it moves on by itself, bring it into view.
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0) listState.animateScrollToItem(currentIndex)
    }

    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        items(state.playlist) { track ->
            TrackRow(
                track = track,
                model = model,
                playing = track == state.current,
                queued = track in state.queue,
            )
        }
    }
}

@Composable
private fun TrackRow(
    track: Track,
    model: PlayerUi,
    playing: Boolean,
    queued: Boolean,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = Quark.colors
    val thumbnail by model.rememberThumbnail(track)

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(
                when {
                    playing -> colors.rowSelected
                    hovered -> colors.rowHover
                    else -> Color.Transparent
                }
            )
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { model.play(track) }
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(45.dp)
                .shadow(10.dp, RoundedCornerShape(Radius.thumbnail), clip = false)
                .clip(RoundedCornerShape(Radius.thumbnail))
                .background(Color(0xFF4D4D4D)),
            contentAlignment = Alignment.Center,
        ) {
            val image = thumbnail
            if (image != null) {
                Image(
                    bitmap = image,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                QIcon(Icons.Filled.MusicNote, Modifier.size(20.dp), colors.textMuted)
            }
        }

        Column(Modifier.weight(1f)) {
            QText(track.title, Quark.type.trackTitle, maxLines = 1)
            QText(
                track.artistLine,
                Quark.type.trackSubtitle,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }

        if (queued) {
            QIcon(Icons.Filled.QueueMusic, Modifier.size(14.dp), colors.textMuted)
        }
        CircleButton(
            icon = Icons.Filled.MoreVert,
            onClick = { model.enqueue(track) },
            diameter = 28.dp,
            iconSize = 18.dp,
            filled = false,
        )
    }
}

// --- Small layouts -----------------------------------------------------------

@Composable
private fun CompactPlayer(state: PlayerState, cover: Cover?, model: PlayerUi) {
    GlassSurface(
        shape = RoundedCornerShape(Radius.card),
        glass = Glass.Card,
        modifier = Modifier.fillMaxSize().padding(12.dp),
    ) {
        Row(
            Modifier.fillMaxSize().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CoverArt(cover, Modifier.size(96.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                QText(state.current.title, Quark.type.panelTitle, maxLines = 1)
                QText(
                    state.current.artistLine,
                    Quark.type.trackSubtitle,
                    color = Quark.colors.textSecondary,
                    maxLines = 1,
                )
                SeekRow(state, model)
                TransportRow(state, model)
            }
        }
    }
}

/** What the window becomes when it is squashed to a strip. */
@Composable
private fun MacroPlayer(state: PlayerState, model: PlayerUi) {
    Row(
        Modifier.fillMaxSize().padding(horizontal = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleButton(Icons.Filled.SkipPrevious, model::previous, diameter = 32.dp, iconSize = 16.dp)
        CircleButton(
            icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            onClick = model::playPause,
            diameter = 36.dp,
            iconSize = 18.dp,
        )
        CircleButton(Icons.Filled.SkipNext, model::next, diameter = 32.dp, iconSize = 16.dp)
        QText(
            text = state.current.title,
            style = Quark.type.trackTitle,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        QText(
            text = "${state.position.display()} / ${state.duration.display()}",
            style = Quark.type.label,
            color = Quark.colors.textMuted,
        )
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
