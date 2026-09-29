package com.quark.app.browse

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.ThumbDown
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quark.app.i18n.strings
import com.quark.app.image.Artwork
import com.quark.app.image.rememberPicture
import com.quark.app.image.rememberThumbnail
import com.quark.app.shell.shell
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.CircleButton
import com.quark.app.ui.Item
import com.quark.app.ui.MenuButton
import com.quark.app.ui.MenuScope
import com.quark.app.ui.QIcon
import com.quark.app.ui.QText
import com.quark.app.ui.Separator
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import kotlin.time.Duration.Companion.milliseconds

// --- Screen chrome ---------------------------------------------------------------

/** The strip at the top of every screen but the player: Back, a title, actions. */
@Composable
fun TopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        if (onBack != null) {
            CircleButton(Icons.AutoMirrored.Filled.ArrowBack, onBack, diameter = 36.dp, iconSize = 20.dp)
        }
        Column(Modifier.weight(1f)) {
            QText(title, Quark.type.heading, maxLines = 1)
            if (subtitle != null) QText(subtitle, Quark.type.label, color = Quark.colors.textMuted, maxLines = 1)
        }
        actions()
    }
}

/** A heading over a shelf or a list, with an optional count beside it. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier, count: Int? = null, trailing: @Composable RowScope.() -> Unit = {}) {
    Row(
        modifier.fillMaxWidth().padding(top = 18.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(Modifier.size(4.dp, 16.dp).clip(RoundedCornerShape(4.dp)).background(Quark.accent.primary))
        QText(text, Quark.type.panelTitle, maxLines = 1)
        if (count != null) {
            QText(
                count.toString(),
                Quark.type.label,
                color = Quark.accent.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(Quark.accent.primary.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        trailing()
    }
}

// --- Tiles ---------------------------------------------------------------------------

/**
 * A playlist, album or station as a square picture with its name under it —
 * the cards of `yandex_playlists_widget.dart` and the shelves of the artist
 * and album pages.
 */
@Composable
fun CollectionTile(
    title: String,
    subtitle: String?,
    coverUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: ImageVector = Icons.AutoMirrored.Filled.QueueMusic,
    round: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val scale by animateFloatAsState(if (hovered) 1.03f else 1f)
    val picture by rememberPicture(coverUrl)
    Column(
        modifier
            .scale(scale)
            .clip(RoundedCornerShape(Radius.card))
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Artwork(
            image = picture,
            shape = if (round) CircleShape else RoundedCornerShape(Radius.card),
            placeholder = placeholder,
            modifier = Modifier.fillMaxWidth().aspectRatio(1f),
        )
        QText(title, Quark.type.trackTitle, maxLines = 2)
        if (!subtitle.isNullOrBlank()) {
            QText(subtitle, Quark.type.label, color = Quark.colors.textSecondary, maxLines = 1)
        }
    }
}

@Composable
fun SummaryTile(summary: CollectionSummary, modifier: Modifier = Modifier) {
    val shell = shell
    val s = strings
    CollectionTile(
        title = summary.title,
        subtitle = summary.subtitle ?: summary.trackCount?.let(s::tracks),
        coverUrl = summary.coverUrl,
        onClick = { shell.open(summary) },
        placeholder = when (summary.kind) {
            CollectionKind.Album -> Icons.Filled.Album
            CollectionKind.Liked -> Icons.Filled.Favorite
            CollectionKind.Station -> Icons.Filled.Radio
            else -> Icons.AutoMirrored.Filled.QueueMusic
        },
        modifier = modifier,
    )
}

/** A horizontal shelf of tiles, as the artist and home pages have them. */
@Composable
fun Shelf(summaries: List<CollectionSummary>, modifier: Modifier = Modifier, tileWidth: Dp = 160.dp) {
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(summaries, key = { it.key }) { summary ->
            SummaryTile(summary, Modifier.width(tileWidth))
        }
    }
}

@Composable
fun ArtistShelf(artists: List<ArtistSummary>, modifier: Modifier = Modifier) {
    val shell = shell
    LazyRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(artists, key = { it.id }) { artist ->
            CollectionTile(
                title = artist.name,
                subtitle = null,
                coverUrl = artist.coverUrl,
                onClick = { shell.openArtist(artist.id, artist.name) },
                placeholder = Icons.Filled.Person,
                round = true,
                modifier = Modifier.width(130.dp),
            )
        }
    }
}

// --- Track rows ------------------------------------------------------------------------

/**
 * One track in a list: its cover, title and artists, how long it is, and the
 * menu of what can be done with it. [playing] marks the one in the player.
 */
@Composable
fun TrackRow(
    track: Track,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    index: Int? = null,
    menu: (@Composable MenuScope.() -> Unit)? = { TrackMenuItems(track) },
    leading: (@Composable () -> Unit)? = null,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = Quark.colors
    val thumbnail by rememberThumbnail(track)
    val background by animateColorAsState(
        when {
            playing -> colors.rowSelected
            hovered -> colors.rowHover
            else -> Color.Transparent
        }
    )
    val liked by shell.app.likes.liked.collectAsState()

    Row(
        modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        leading?.invoke()
        if (index != null) {
            QText(
                (index + 1).toString(),
                Quark.type.label,
                color = if (playing) Quark.accent.primary else colors.textMuted,
                modifier = Modifier.width(26.dp),
            )
        }
        Artwork(thumbnail, RoundedCornerShape(Radius.thumbnail), Modifier.size(45.dp))
        Column(Modifier.weight(1f)) {
            QText(track.title, Quark.type.trackTitle, maxLines = 1, color = if (playing) Quark.accent.primary.brighter() else colors.text)
            QText(track.artistLine, Quark.type.trackSubtitle, color = colors.textSecondary, maxLines = 1)
        }
        if (track is YandexTrack && track.trackId in liked) {
            QIcon(Icons.Filled.Favorite, Modifier.size(14.dp), colors.textMuted)
        }
        trailing()
        if (track.durationMs > 0) {
            QText(track.durationMs.milliseconds.clock(), Quark.type.label, color = colors.textMuted)
        }
        if (menu != null) MenuButton(items = menu)
    }
}

/**
 * Everything that can be done with a track, as `PlaylistTileWidget` offered
 * it: queueing, playlists, and for Yandex tracks the likes, the album, the
 * artist and the stations built from it. [onRemove] adds "Remove from
 * playlist" for lists the user can edit.
 */
@Composable
fun MenuScope.TrackMenuItems(
    track: Track,
    onRemove: (() -> Unit)? = null,
    inQueue: Boolean = false,
) {
    val shell = shell
    val s = strings
    val liked by shell.app.likes.liked.collectAsState()
    val signedIn = shell.app.yandex.api != null

    Item(s.playNext, Icons.Filled.SkipNext) { shell.playNext(listOf(track)) }
    Item(s.addToQueue, Icons.AutoMirrored.Filled.QueueMusic) { shell.enqueue(listOf(track)) }
    Item(s.addToPlaylist, Icons.AutoMirrored.Filled.PlaylistAdd) { shell.addToPlaylist(listOf(track)) }
    if (inQueue) Item(s.removeFromQueue, Icons.Filled.RemoveCircleOutline) { shell.app.controller.removeFromQueue(track) }
    if (onRemove != null) Item(s.removeFromPlaylist, Icons.Filled.Delete, danger = true) { onRemove() }

    if (track is YandexTrack && signedIn) {
        Separator()
        val isLiked = track.trackId in liked
        Item(if (isLiked) s.unlike else s.like, if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder) {
            shell.toggleLike(track)
        }
        Item(s.addToYandexPlaylist, Icons.Filled.LibraryAdd) { shell.addToYandexPlaylist(listOf(track)) }
        if (track.albumId != null) Item(s.goToAlbum, Icons.Filled.Album) { shell.openAlbum(track) }
        if (track.artistIds.isNotEmpty()) Item(s.goToArtist, Icons.Filled.Person) { shell.openArtist(track) }
        Item(s.findSimilar, Icons.Filled.Search) { shell.findSimilar(track) }
        Item(s.startStation, Icons.Filled.Radio) { shell.startStation(track) }
        Item(s.dislike, Icons.Filled.ThumbDown) { shell.dislike(track) }
    }
}

/** A heart that follows the one list of liked tracks. */
@Composable
fun LikeButton(track: Track, diameter: Dp = 36.dp) {
    val shell = shell
    if (track !is YandexTrack || shell.app.yandex.api == null) return
    val liked by shell.app.likes.liked.collectAsState()
    val isLiked = track.trackId in liked
    CircleButton(
        icon = if (isLiked) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder,
        onClick = { shell.toggleLike(track) },
        diameter = diameter,
        iconSize = diameter * 0.5f,
        active = isLiked,
    )
}

/** A tile for a service or an action on the start page (`ExpressiveServiceCard`). */
@Composable
fun ServiceCard(
    label: String,
    icon: ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    badge: String? = null,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = Quark.colors
    val scale by animateFloatAsState(if (hovered) 1.03f else 1f)
    val base = colors.text.copy(alpha = if (colors.isLight) 0.06f else 0.10f)
    val border by animateColorAsState(if (hovered) color.copy(alpha = 0.4f) else colors.divider)
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier
            .scale(scale)
            .height(112.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    if (hovered) listOf(color.copy(alpha = 0.18f), color.copy(alpha = 0.06f))
                    else listOf(base, base.copy(alpha = 0.03f))
                )
            )
            .border(1.2.dp, border, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(16.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Box(
                Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (hovered) color.copy(alpha = 0.15f) else colors.text.copy(alpha = 0.05f))
                    .padding(8.dp),
            ) {
                QIcon(icon, Modifier.size(22.dp), if (hovered) color else colors.text.copy(alpha = 0.8f))
            }
            if (badge != null) {
                QText(
                    badge,
                    Quark.type.label,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(color.copy(alpha = 0.2f))
                        .border(1.dp, color.copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        }
        QText(label, Quark.type.trackTitle, maxLines = 1)
    }
}

/** A cover with its details beside it or, on a narrow screen, under it. */
@Composable
fun CollectionHeader(
    title: String,
    coverUrl: String?,
    compact: Boolean,
    modifier: Modifier = Modifier,
    kindLabel: String? = null,
    subtitle: String? = null,
    description: String? = null,
    details: String? = null,
    placeholder: ImageVector = Icons.Filled.MusicNote,
    round: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val picture by rememberPicture(coverUrl, 600)
    val coverSize = if (compact) 180.dp else 220.dp
    val info: @Composable (Alignment.Horizontal) -> Unit = { align ->
        Column(horizontalAlignment = align, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            if (kindLabel != null) {
                QText(kindLabel.uppercase(), Quark.type.label, color = Quark.accent.primary.brighter())
            }
            QText(title, Quark.type.nowPlayingTitle, maxLines = 2)
            if (!subtitle.isNullOrBlank()) QText(subtitle, Quark.type.body, color = Quark.colors.textSecondary, maxLines = 2)
            if (!details.isNullOrBlank()) QText(details, Quark.type.label, color = Quark.colors.textMuted, maxLines = 1)
            if (!description.isNullOrBlank()) {
                QText(description, Quark.type.trackSubtitle, color = Quark.colors.textSecondary, maxLines = 4)
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                actions()
            }
        }
    }
    if (compact) {
        Column(modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Artwork(picture, if (round) CircleShape else RoundedCornerShape(Radius.cover), Modifier.size(coverSize), placeholder)
            Spacer(Modifier.height(16.dp))
            info(Alignment.CenterHorizontally)
        }
    } else {
        Row(
            modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Artwork(picture, if (round) CircleShape else RoundedCornerShape(Radius.cover), Modifier.size(coverSize), placeholder)
            Box(Modifier.weight(1f)) { info(Alignment.Start) }
        }
    }
}

/** A lighter shade of an accent colour, readable as text on the dark glass. */
fun Color.brighter(): Color = Color(
    red = red + (1f - red) * 0.35f,
    green = green + (1f - green) * 0.35f,
    blue = blue + (1f - blue) * 0.35f,
    alpha = alpha,
)

/** `m:ss`, or `h:mm:ss` for the long ones. */
fun kotlin.time.Duration.clock(): String {
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

/** Fills the rest of a Column; the screens' lists stop above the mini player. */
@Composable
fun ColumnFill(modifier: Modifier = Modifier) = Spacer(modifier.fillMaxSize())
