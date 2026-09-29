package com.quark.app.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ClearAll
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quark.app.browse.LikeButton
import com.quark.app.browse.TrackMenuItems
import com.quark.app.browse.TrackRow
import com.quark.app.browse.matching
import com.quark.app.i18n.strings
import com.quark.app.image.Cover
import com.quark.app.shell.shell
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.CircleButton
import com.quark.app.ui.Divider
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.Item
import com.quark.app.ui.MenuButton
import com.quark.app.ui.QIcon
import com.quark.app.ui.QText
import com.quark.app.ui.QTextField
import com.quark.app.ui.ThinSlider
import com.quark.core.model.Track
import com.quark.core.player.PlayerState
import com.quark.core.player.RepeatMode
import kotlinx.coroutines.delay
import kotlin.time.Duration

/** Width of the playlist panel, and how far it pushes the player across. */
private val PANEL_WIDTH = 400.dp

/** The speeds the speed menu offers. */
private val SPEEDS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)

/**
 * Chooses a layout for the window's size.
 *
 * The thresholds are the ones from `playlist_page_router.dart`: under 80px tall
 * the window is a control strip, up to 300px a compact card, above that the full
 * player — or, on a phone-narrow screen, `android_player.dart`'s column. The
 * panel only appears when there is room for it, as in the original's
 * `playerPadding` rule.
 */
@Composable
fun PlayerScreen(
    model: PlayerUi,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    onOpenLyrics: () -> Unit = {},
    onOpenVibe: () -> Unit = {},
    onOpenSettings: () -> Unit = {},
) {
    val state by model.state.collectAsState()
    val cover by model.cover.collectAsState()
    val status by model.status.collectAsState()
    val shell = shell
    var panelOpen by rememberSaveable { mutableStateOf(shell.app.settings.current.appearance.playlistPanelOpen) }
    var zoomed by remember { mutableStateOf(false) }
    val togglePanel: () -> Unit = {
        panelOpen = !panelOpen
        val open = panelOpen
        shell.app.settings.update { it.copy(appearance = it.appearance.copy(playlistPanelOpen = open)) }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val roomForPanel = maxWidth > 810.dp
        val actions = PlayerActions(
            onBack = onBack,
            onTogglePanel = togglePanel,
            onOpenLyrics = onOpenLyrics,
            onOpenVibe = onOpenVibe,
            onOpenSettings = onOpenSettings,
            onZoom = { zoomed = true },
        )
        when {
            maxHeight < 80.dp -> MacroPlayer(state, model)
            maxHeight <= 300.dp -> CompactPlayer(state, cover, model)
            maxWidth < 600.dp -> MobilePlayer(state, cover, status, model, panelOpen, actions)
            else -> FullPlayer(
                state = state,
                cover = cover,
                status = status,
                model = model,
                panelOpen = panelOpen && roomForPanel,
                actions = actions,
            )
        }

        AnimatedVisibility(visible = zoomed, enter = fadeIn(), exit = fadeOut()) {
            CoverZoom(cover) { zoomed = false }
        }
    }
}

/** What the player's buttons open, handed down together. */
private class PlayerActions(
    val onBack: (() -> Unit)?,
    val onTogglePanel: () -> Unit,
    val onOpenLyrics: () -> Unit,
    val onOpenVibe: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onZoom: () -> Unit,
)

@Composable
private fun FullPlayer(
    state: PlayerState,
    cover: Cover?,
    status: LibraryStatus,
    model: PlayerUi,
    panelOpen: Boolean,
    actions: PlayerActions,
) {
    Box(Modifier.fillMaxSize()) {
        NowPlaying(
            state = state,
            cover = cover,
            model = model,
            panelOpen = panelOpen,
            actions = actions,
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
                onClose = actions.onTogglePanel,
                modifier = Modifier.width(PANEL_WIDTH).fillMaxHeight().padding(12.dp),
            )
        }

        actions.onBack?.let { back ->
            CircleButton(
                Icons.Filled.Home,
                back,
                diameter = 36.dp,
                iconSize = 18.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp),
            )
        }

        // "Playlist opening area": resting the pointer on the left edge opens
        // the panel, as `playlistOpeningArea` did in main_player.dart.
        val openingArea = shell.app.settings.settings.collectAsState().value.appearance.playlistOpeningArea
        if (openingArea && !panelOpen) {
            val edge = remember { MutableInteractionSource() }
            val hovered by edge.collectIsHoveredAsState()
            LaunchedEffect(hovered) {
                if (hovered) {
                    delay(250)
                    actions.onTogglePanel()
                }
            }
            Box(Modifier.align(Alignment.CenterStart).width(14.dp).fillMaxHeight().hoverable(edge))
        }
    }
}

@Composable
private fun NowPlaying(
    state: PlayerState,
    cover: Cover?,
    model: PlayerUi,
    panelOpen: Boolean,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.padding(horizontal = 32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CoverArt(cover, Modifier.size(270.dp).clickable(indication = null, interactionSource = null, onClick = actions.onZoom))

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
        SecondaryRow(state, model, panelOpen, actions)
    }
}

/**
 * `android_player.dart`: everything in one column, the cover as wide as the
 * screen, and the playlist as a sheet over it rather than a panel beside it.
 */
@Composable
private fun MobilePlayer(
    state: PlayerState,
    cover: Cover?,
    status: LibraryStatus,
    model: PlayerUi,
    panelOpen: Boolean,
    actions: PlayerActions,
) {
    val s = strings
    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(horizontal = 24.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                actions.onBack?.let { back ->
                    CircleButton(Icons.Filled.KeyboardArrowDown, back, diameter = 40.dp, iconSize = 24.dp, filled = false)
                }
                QText(
                    state.playlistInfo.name,
                    Quark.type.label.copy(textAlign = TextAlign.Center),
                    color = Quark.colors.textSecondary,
                    maxLines = 1,
                    modifier = Modifier.weight(1f),
                )
                MenuButton(diameter = 40.dp, iconSize = 22.dp) {
                    TrackMenuItems(state.current)
                    Item(s.lyrics, Icons.Filled.Lyrics, onClick = actions.onOpenLyrics)
                    Item(s.visualizer, Icons.Filled.GraphicEq, onClick = actions.onOpenVibe)
                    Item(s.settings, Icons.Filled.Settings, onClick = actions.onOpenSettings)
                }
            }

            Spacer(Modifier.weight(0.6f))
            CoverArt(
                cover,
                Modifier
                    .fillMaxWidth()
                    .widthIn(max = 420.dp)
                    .aspectRatio(1f)
                    .clickable(indication = null, interactionSource = null, onClick = actions.onZoom),
            )
            Spacer(Modifier.height(28.dp))

            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    QText(state.current.title, Quark.type.heading, maxLines = 1)
                    QText(state.current.artistLine, Quark.type.body, color = Quark.colors.textSecondary, maxLines = 1)
                }
                LikeButton(state.current, diameter = 40.dp)
            }
            Spacer(Modifier.height(14.dp))
            SeekRow(state, model, Modifier.fillMaxWidth())
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleButton(Icons.Filled.Shuffle, model::toggleShuffle, diameter = 40.dp, iconSize = 20.dp, active = state.isShuffled, filled = false)
                CircleButton(Icons.Filled.SkipPrevious, model::previous, diameter = 52.dp, iconSize = 28.dp)
                CircleButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    onClick = model::playPause,
                    diameter = 68.dp,
                    iconSize = 34.dp,
                )
                CircleButton(Icons.Filled.SkipNext, model::next, diameter = 52.dp, iconSize = 28.dp)
                CircleButton(
                    if (state.repeat == RepeatMode.One) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    model::toggleRepeat,
                    diameter = 40.dp,
                    iconSize = 20.dp,
                    active = state.repeat == RepeatMode.One,
                    filled = false,
                )
            }
            Spacer(Modifier.height(14.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                CircleButton(Icons.AutoMirrored.Filled.QueueMusic, actions.onTogglePanel, diameter = 40.dp, iconSize = 20.dp, active = panelOpen)
                CircleButton(Icons.Filled.Lyrics, actions.onOpenLyrics, diameter = 40.dp, iconSize = 20.dp)
                SpeedButton(state, model, diameter = 40.dp)
            }
            Spacer(Modifier.weight(1f))
        }

        AnimatedVisibility(
            visible = panelOpen,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
        ) {
            PlaylistPanel(
                state = state,
                status = status,
                model = model,
                onClose = actions.onTogglePanel,
                modifier = Modifier.fillMaxSize().padding(8.dp),
            )
        }
    }
}

/**
 * The artwork, with the shadow the original gives it. Cross-fades between
 * tracks rather than popping, which is what the 650ms `AnimatedSwitcher` in
 * `main_player.dart` is doing.
 */
@Composable
private fun CoverArt(cover: Cover?, modifier: Modifier = Modifier) {
    Crossfade(targetState = cover, animationSpec = tween(650), modifier = modifier) { shown ->
        Box(
            Modifier
                .fillMaxSize()
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

/** The cover on its own, as large as the window allows (`CoverView`). */
@Composable
private fun CoverZoom(cover: Cover?, onClose: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable(indication = null, interactionSource = null, onClick = onClose)
            .padding(32.dp),
        contentAlignment = Alignment.Center,
    ) {
        val image = cover?.image
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(Radius.cover)),
            )
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

/** The volume line; the mouse wheel over it turns the volume too. */
@Composable
private fun VolumeRow(state: PlayerState, model: PlayerUi, modifier: Modifier = Modifier) {
    val volume = state.volume
    Row(
        modifier
            .fillMaxWidth()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent()
                        if (event.type == PointerEventType.Scroll) {
                            val delta = event.changes.firstOrNull()?.scrollDelta?.y ?: 0f
                            if (delta != 0f) {
                                model.setVolume((model.state.value.volume - delta * 0.05f).coerceIn(0f, 1f))
                                event.changes.forEach { it.consume() }
                            }
                        }
                    }
                }
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        QIcon(Icons.AutoMirrored.Filled.VolumeDown, Modifier.size(18.dp), Quark.colors.textMuted)
        ThinSlider(
            value = volume,
            onChange = model::setVolume,
            modifier = Modifier.weight(1f),
        )
        QIcon(Icons.AutoMirrored.Filled.VolumeUp, Modifier.size(18.dp), Quark.colors.textMuted)
    }
}

@Composable
private fun SecondaryRow(
    state: PlayerState,
    model: PlayerUi,
    panelOpen: Boolean,
    actions: PlayerActions,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
        CircleButton(
            icon = Icons.AutoMirrored.Filled.QueueMusic,
            onClick = actions.onTogglePanel,
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
        LikeButton(state.current)
        CircleButton(
            icon = Icons.Filled.Lyrics,
            onClick = actions.onOpenLyrics,
            diameter = 36.dp,
            iconSize = 18.dp,
        )
        CircleButton(
            icon = Icons.Filled.GraphicEq,
            onClick = actions.onOpenVibe,
            diameter = 36.dp,
            iconSize = 18.dp,
        )
        SpeedButton(state, model)
        MenuButton(diameter = 36.dp, iconSize = 18.dp, filled = true) {
            TrackMenuItems(state.current)
        }
        CircleButton(
            icon = Icons.Filled.Settings,
            onClick = actions.onOpenSettings,
            diameter = 36.dp,
            iconSize = 18.dp,
        )
    }
}

/** Playback speed, remembered across tracks and restarts. */
@Composable
private fun SpeedButton(state: PlayerState, model: PlayerUi, diameter: androidx.compose.ui.unit.Dp = 36.dp) {
    MenuButton(icon = Icons.Filled.Speed, diameter = diameter, iconSize = diameter / 2, filled = true) {
        SPEEDS.forEach { speed ->
            Item(
                text = "${formatSpeed(speed)}×",
                icon = if (speed == state.speed) Icons.Filled.Check else null,
            ) { model.setSpeed(speed) }
        }
    }
}

private fun formatSpeed(speed: Float): String {
    val hundredths = (speed * 100).toInt()
    return if (hundredths % 100 == 0) (hundredths / 100).toString()
    else (hundredths / 100).toString() + "." + (hundredths % 100).toString().padStart(2, '0').trimEnd('0')
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
        var query by remember { mutableStateOf("") }
        var category by remember(state.playlistInfo) { mutableStateOf<Category?>(null) }
        val shown = remember(state.playlist, query, category) {
            state.playlist.matching(query).filter { track -> category?.matches(track) ?: true }
        }
        val showCategories by shell.app.settings.settings.collectAsState()

        Column(Modifier.fillMaxSize().padding(horizontal = 14.dp, vertical = 12.dp)) {
            PanelHeader(state, status, onClose)
            Spacer(Modifier.height(8.dp))
            QTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = strings.search,
                leading = Icons.Filled.Search,
                modifier = Modifier.fillMaxWidth(),
            )
            if (showCategories.library.categories) {
                Spacer(Modifier.height(8.dp))
                Categories(state.playlist, category) { category = it }
            }
            Spacer(Modifier.height(8.dp))
            Divider()
            Spacer(Modifier.height(6.dp))
            TrackList(
                state = state,
                shown = shown,
                model = model,
                showQueue = query.isEmpty() && category == null,
                reorderable = query.isEmpty() && category == null,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun PanelHeader(
    state: PlayerState,
    status: LibraryStatus,
    onClose: () -> Unit,
) {
    val s = strings
    Row(verticalAlignment = Alignment.CenterVertically) {
        Spacer(Modifier.size(20.dp))
        Column(
            Modifier.weight(1f),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            QText(state.playlistInfo.name, Quark.type.panelTitle, maxLines = 1)
            QText(
                text = when (status) {
                    is LibraryStatus.Scanning -> s.reading(status.read, status.found)
                    is LibraryStatus.Failed -> status.message
                    LibraryStatus.Idle -> s.tracks(state.playlist.size)
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

/** A filter of the playlist panel: one album or one artist (`getCategories`). */
private sealed interface Category {
    val label: String
    fun matches(track: Track): Boolean

    data class Album(override val label: String) : Category {
        override fun matches(track: Track) = label in track.albums
    }

    data class Artist(override val label: String) : Category {
        override fun matches(track: Track) = label in track.artists
    }
}

/** "All", then every album and every artist of the playlist, as chips. */
@Composable
private fun Categories(playlist: List<Track>, chosen: Category?, onChoose: (Category?) -> Unit) {
    val categories = remember(playlist) {
        val albums = playlist.flatMap { it.albums }.filter { it.isNotBlank() && it != Track.UNKNOWN_ALBUM }.distinct()
        val artists = playlist.flatMap { it.artists }.filter { it.isNotBlank() && it != Track.UNKNOWN_ARTIST }.distinct()
        // A playlist of one album by one artist has nothing to sort by.
        if (albums.size + artists.size <= 2) emptyList()
        else albums.map { Category.Album(it) } + artists.map { Category.Artist(it) }
    }
    if (categories.isEmpty()) return
    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        item(key = "all") { CategoryChip(strings.allTracks, chosen == null) { onChoose(null) } }
        items(categories, key = { it.toString() }) { category ->
            CategoryChip(category.label, category == chosen) { onChoose(if (category == chosen) null else category) }
        }
    }
}

@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Quark.colors
    QText(
        label,
        Quark.type.label,
        color = if (selected) colors.text else colors.textSecondary,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Quark.accent.primary.copy(alpha = 0.45f) else colors.control)
            .clickable(indication = null, interactionSource = null, onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
    )
}

/**
 * Dragging a track by its handle to a new place in the playlist, as the
 * Flutter build's `ReorderableListView` did. The rows move under the finger
 * as it goes; the playlist itself changes once, when it is let go.
 */
private class Reorder {
    var order by mutableStateOf<List<Track>?>(null)
    var draggedKey by mutableStateOf<String?>(null)
    var offset by mutableFloatStateOf(0f)
    var from = -1
}

@Composable
private fun TrackList(
    state: PlayerState,
    shown: List<Track>,
    model: PlayerUi,
    showQueue: Boolean,
    reorderable: Boolean,
    modifier: Modifier = Modifier,
) {
    val shell = shell
    val s = strings
    val listState = rememberLazyListState()
    val queue = if (showQueue) state.queue else emptyList()
    val offset = if (queue.isEmpty()) 0 else queue.size + 1
    val currentIndex = remember(shown, state.current) { shown.indexOf(state.current) }
    val reorder = remember { Reorder() }
    val rows = reorder.order ?: shown
    val keys = remember(rows) { stableKeys(rows) }

    // Follow the music: when it moves on by itself, bring it into view.
    LaunchedEffect(currentIndex) {
        if (currentIndex >= 0 && reorder.order == null) listState.animateScrollToItem(currentIndex + offset)
    }

    fun dragBy(delta: Float) {
        val order = reorder.order ?: return
        val key = reorder.draggedKey ?: return
        reorder.offset += delta
        val visible = listState.layoutInfo.visibleItemsInfo
        val dragged = visible.firstOrNull { it.key == key } ?: return
        val middle = dragged.offset + reorder.offset + dragged.size / 2f
        val target = visible.firstOrNull { item ->
            item.key != key && item.key is String && (item.key as String) in keys &&
                middle >= item.offset && middle <= item.offset + item.size
        } ?: return
        val fromIndex = keys.indexOf(key)
        val toIndex = keys.indexOf(target.key as String)
        if (fromIndex < 0 || toIndex < 0) return
        reorder.order = order.toMutableList().apply { add(toIndex, removeAt(fromIndex)) }
        // The row now sits where the target was; keep it under the pointer.
        reorder.offset -= (target.offset - dragged.offset)
    }

    fun endDrag() {
        val order = reorder.order
        val key = reorder.draggedKey
        if (order != null && key != null) {
            val to = stableKeys(order).indexOf(key)
            if (reorder.from >= 0 && to >= 0 && to != reorder.from) shell.app.controller.moveInPlaylist(reorder.from, to)
        }
        reorder.order = null
        reorder.draggedKey = null
        reorder.offset = 0f
        reorder.from = -1
    }

    LazyColumn(modifier, state = listState, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        if (queue.isNotEmpty()) {
            item(key = "queue-title") {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    QText(s.queue, Quark.type.trackTitle, Modifier.weight(1f), color = Quark.accent.primary)
                    CircleButton(Icons.Filled.ClearAll, model::clearQueue, diameter = 28.dp, iconSize = 16.dp, filled = false)
                }
            }
            itemsIndexed(queue, key = { index, track -> "queue:${track.filepath}#$index" }) { _, track ->
                TrackRow(
                    track = track,
                    onClick = { model.play(track) },
                    menu = { TrackMenuItems(track, inQueue = true) },
                )
            }
        }
        itemsIndexed(rows, key = { index, _ -> keys[index] }) { index, track ->
            val key = keys[index]
            val dragging = reorder.draggedKey == key
            TrackRow(
                track = track,
                onClick = { model.play(track) },
                playing = track == state.current,
                modifier = Modifier
                    .zIndex(if (dragging) 1f else 0f)
                    .graphicsLayer { translationY = if (dragging) reorder.offset else 0f },
                menu = {
                    TrackMenuItems(
                        track,
                        onRemove = { shell.app.controller.removeFromPlaylist(track) }.takeIf { state.playlist.size > 1 },
                    )
                },
                leading = if (!reorderable) null else {
                    {
                        QIcon(
                            Icons.Filled.DragIndicator,
                            Modifier
                                .size(20.dp)
                                .pointerInput(key) {
                                    detectDragGestures(
                                        onDragStart = {
                                            reorder.order = shown
                                            reorder.draggedKey = key
                                            reorder.from = keys.indexOf(key)
                                            reorder.offset = 0f
                                        },
                                        onDragEnd = { endDrag() },
                                        onDragCancel = { endDrag() },
                                    ) { change, amount ->
                                        change.consume()
                                        dragBy(amount.y)
                                    }
                                },
                            Quark.colors.textMuted,
                        )
                    }
                },
            )
        }
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

/**
 * Keys for rows of a playlist that stay with the track when it moves: its path,
 * and a count for the second and later copies of the same track.
 */
private fun stableKeys(tracks: List<Track>): List<String> {
    val seen = HashMap<String, Int>()
    return tracks.map { track ->
        val copy = seen.merge(track.filepath, 1, Int::plus) ?: 1
        if (copy == 1) track.filepath else "${track.filepath}#$copy"
    }
}
