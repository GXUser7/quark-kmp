package com.quark.app.browse

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quark.app.i18n.Strings
import com.quark.app.i18n.strings
import com.quark.app.nav.Screen
import com.quark.app.shell.Shell
import com.quark.app.shell.shell
import com.quark.app.ui.CircleButton
import com.quark.app.ui.Item
import com.quark.app.ui.Load
import com.quark.app.ui.LocalCompact
import com.quark.app.ui.MenuButton
import com.quark.app.ui.MenuScope
import com.quark.app.ui.PillButton
import com.quark.app.ui.Placeholder
import com.quark.app.ui.QTextField
import com.quark.app.ui.Separator
import com.quark.app.ui.bottomInset
import com.quark.app.ui.rememberLoad
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack

/**
 * Any list of tracks — a playlist on any service, an album, the liked tracks,
 * the chart — on one screen. The Flutter build had a widget per service for
 * this (`PlaylistInfoWidget`, `AlbumInfoWidget`, the Spotify, SoundCloud, VK
 * and YouTube playlist views); they differed in little but the source.
 */
@Composable
fun CollectionScreen(screen: Screen.Collection) {
    val shell = shell
    val s = strings
    var generation by remember(screen.key) { mutableIntStateOf(0) }
    // The user's own playlists change under this screen; everything else can
    // be kept for going back.
    val cacheable = !screen.key.startsWith("local:")
    val load by rememberLoad(screen.key, generation, if (cacheable) shell.cache else null, screen.load)
    val refresh: () -> Unit = {
        shell.cache.forget(screen.key)
        generation++
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = screen.title, onBack = { shell.navigator.pop() }) {
            CircleButton(Icons.Filled.Refresh, refresh, diameter = 36.dp, iconSize = 18.dp)
        }
        when (val current = load) {
            Load.Loading -> Placeholder(s.loading, Modifier.fillMaxSize())
            is Load.Failed -> Placeholder(
                s.playlistError(current.message),
                Modifier.fillMaxSize(),
                action = s.retry,
                onAction = refresh,
            )
            is Load.Ready -> CollectionBody(current.value, refresh)
        }
    }
}

@Composable
private fun CollectionBody(collection: TrackCollection, refresh: () -> Unit) {
    val shell = shell
    val s = strings
    val compact = LocalCompact.current
    val state by shell.app.controller.state.collectAsState()
    var filter by remember(collection.key) { mutableStateOf("") }
    val shown = remember(collection.tracks, filter) { collection.tracks.matching(filter) }
    val details = remember(collection.tracks, s) {
        val total = collection.tracks.sumOf { it.durationMs } / 60_000
        listOfNotNull(
            s.tracks(collection.tracks.size),
            if (total > 0) s.hoursMinutes(total / 60, total % 60) else null,
        ).joinToString(" · ")
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset + 16.dp)) {
        item(key = "header") {
            CollectionHeader(
                title = collection.title,
                coverUrl = collection.coverUrl,
                compact = compact,
                kindLabel = collection.kind.label(s),
                subtitle = collection.subtitle,
                description = collection.description,
                details = details,
                placeholder = when (collection.kind) {
                    CollectionKind.Album -> Icons.Filled.Album
                    CollectionKind.Liked -> Icons.Filled.Favorite
                    CollectionKind.Station -> Icons.Filled.Radio
                    CollectionKind.Artist -> Icons.Filled.Person
                    else -> Icons.AutoMirrored.Filled.QueueMusic
                },
            ) {
                PillButton(
                    s.play,
                    { shell.play(collection) },
                    icon = Icons.Filled.PlayArrow,
                    accent = true,
                    enabled = collection.tracks.isNotEmpty(),
                )
                PillButton(
                    s.shuffle,
                    { shell.play(collection, shuffled = true) },
                    icon = Icons.Filled.Shuffle,
                    enabled = collection.tracks.size > 1,
                )
                MenuButton(diameter = 45.dp, iconSize = 20.dp, filled = true) {
                    CollectionMenuItems(shell, s, collection, refresh)
                }
            }
        }

        if (collection.artistIds.isNotEmpty()) {
            item(key = "artists") {
                LazyRow(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(collection.artistIds, key = { it.first }) { (id, name) ->
                        PillButton(name, { shell.openArtist(id, name) }, icon = Icons.Filled.Person, height = 36.dp)
                    }
                }
            }
        }

        if (collection.tracks.size > FILTER_FROM) {
            item(key = "filter") {
                QTextField(
                    value = filter,
                    onValueChange = { filter = it },
                    placeholder = s.filterTracks,
                    leading = Icons.Filled.FilterList,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
        }

        if (shown.isEmpty()) {
            item(key = "empty") { Placeholder(s.emptyCollection) }
        }

        itemsIndexed(shown, key = { index, track -> "${track.filepath}#$index" }) { index, track ->
            val remove = removal(shell, collection, track, refresh)
            TrackRow(
                track = track,
                onClick = { shell.play(collection, start = track) },
                playing = state.hasTrack && state.current.filepath == track.filepath,
                index = if (collection.kind == CollectionKind.Album || collection.kind == CollectionKind.Chart) index else null,
                menu = { TrackMenuItems(track, onRemove = remove) },
                modifier = Modifier.padding(horizontal = 12.dp),
            )
        }
    }
}

/** How a track leaves [collection], if the user may take it out. */
private fun removal(shell: Shell, collection: TrackCollection, track: Track, refresh: () -> Unit): (() -> Unit)? {
    val own = collection.userPlaylistId
    val yandexKind = collection.yandexKind
    return when {
        own != null -> {
            { shell.attempt { shell.app.userLibrary.remove(own, track); refresh() } }
        }
        yandexKind != null && track is YandexTrack -> {
            { shell.attempt { shell.app.yandexCatalog.removeFromPlaylist(yandexKind, track); refresh() } }
        }
        else -> null
    }
}

@Composable
private fun MenuScope.CollectionMenuItems(
    shell: Shell,
    s: Strings,
    collection: TrackCollection,
    refresh: () -> Unit,
) {
    val tracks = collection.tracks
    Item(s.playNext, Icons.Filled.SkipNext, enabled = tracks.isNotEmpty()) { shell.playNext(tracks) }
    Item(s.addToQueue, Icons.AutoMirrored.Filled.QueueMusic, enabled = tracks.isNotEmpty()) { shell.enqueue(tracks) }
    Item(s.addToPlaylist, Icons.AutoMirrored.Filled.PlaylistAdd, enabled = tracks.isNotEmpty()) { shell.addToPlaylist(tracks) }
    if (collection.userPlaylistId == null) {
        Item(s.saveToLibrary, Icons.Filled.LibraryAdd, enabled = tracks.isNotEmpty()) { shell.saveToLibrary(collection) }
    }
    val yandexTracks = tracks.filterIsInstance<YandexTrack>()
    if (yandexTracks.isNotEmpty() && collection.yandexKind == null && shell.app.yandex.api != null) {
        Item(s.addToYandexPlaylist, Icons.Filled.LibraryAdd) { shell.addToYandexPlaylist(yandexTracks) }
    }
    if (tracks.any { it !is com.quark.core.model.LocalTrack }) {
        Item(s.downloadOffline, Icons.Filled.CloudDownload) { shell.downloadForOffline(collection) }
    }
    Item(s.export, Icons.Filled.Download, enabled = tracks.isNotEmpty()) { shell.export(collection) }
    collection.link?.let { link -> Item(s.copyLink, Icons.Filled.Link) { shell.copy(link) } }

    val own = collection.userPlaylistId
    if (own != null) {
        Separator()
        Item(s.rename, Icons.Filled.Edit) { shell.renameUserPlaylist(own, collection.title, refresh) }
        Item(s.editDescription, Icons.Filled.Description) { shell.describeUserPlaylist(own, collection.description, refresh) }
        Item(s.deletePlaylist, Icons.Filled.Delete, danger = true) {
            shell.deleteUserPlaylist(own, collection.title) { shell.navigator.pop() }
        }
    }

    val kind = collection.yandexKind
    if (kind != null) {
        Separator()
        Item(s.rename, Icons.Filled.Edit) { shell.renameYandexPlaylist(kind, collection.title, refresh) }
        Item(s.uploadTracks, Icons.Filled.Upload) { shell.uploadToYandex(kind, refresh) }
        collection.isPublic?.let { public ->
            Item(if (public) s.makePrivate else s.makePublic, if (public) Icons.Filled.Lock else Icons.Filled.Public) {
                shell.attempt {
                    shell.app.yandexCatalog.setVisibility(kind, !public)
                    refresh()
                }
            }
        }
        Item(s.deletePlaylist, Icons.Filled.Delete, danger = true) {
            shell.deleteYandexPlaylist(kind, collection.title) {
                shell.cache.forgetAll("yandex:")
                shell.navigator.pop()
            }
        }
    }
}

private fun CollectionKind.label(s: Strings): String = when (this) {
    CollectionKind.Playlist -> s.playlist
    CollectionKind.Album -> s.album
    CollectionKind.Liked -> s.liked
    CollectionKind.Chart -> s.chart
    CollectionKind.Station -> s.station
    CollectionKind.Search -> s.search
    CollectionKind.Artist -> s.artist
}

/** Title, artist or album contains every word typed, in any order. */
fun List<Track>.matching(query: String): List<Track> {
    val words = query.trim().lowercase().split(' ').filter(String::isNotEmpty)
    if (words.isEmpty()) return this
    return filter { track ->
        val haystack = "${track.title} ${track.artistLine} ${track.albumLine}".lowercase()
        words.all { it in haystack }
    }
}

private const val FILTER_FROM = 12
