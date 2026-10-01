package com.quark.app.browse

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.quark.app.i18n.strings
import com.quark.app.nav.Screen
import com.quark.app.shell.shell
import com.quark.app.ui.CircleButton
import com.quark.app.ui.Item
import com.quark.app.ui.Load
import com.quark.app.ui.LocalCompact
import com.quark.app.ui.MenuButton
import com.quark.app.ui.PillButton
import com.quark.app.ui.Placeholder
import com.quark.app.ui.bottomInset
import com.quark.app.ui.rememberLoad
import com.quark.core.model.PlaylistId
import com.quark.core.model.PlaylistSource

/**
 * A Yandex Music artist (`ArtistInfoWidget`): their most played tracks, their
 * albums and the ones they appear on, playlists about them and who else sounds
 * like them.
 */
@Composable
fun ArtistScreen(screen: Screen.Artist) {
    val shell = shell
    val s = strings
    var generation by remember(screen.id) { mutableIntStateOf(0) }
    val key = "yandex:artist:${screen.id}"
    val load by rememberLoad(key, generation, shell.cache) { shell.app.yandexCatalog.artist(screen.id) }

    Column(Modifier.fillMaxSize()) {
        TopBar(title = screen.name, onBack = { shell.navigator.pop() }) {
            CircleButton(Icons.Filled.Refresh, { shell.cache.forget(key); generation++ }, diameter = 36.dp, iconSize = 18.dp)
        }
        when (val current = load) {
            Load.Loading -> Placeholder(s.loading, Modifier.fillMaxSize())
            is Load.Failed -> Placeholder(current.message, Modifier.fillMaxSize(), s.retry) { generation++ }
            is Load.Ready -> ArtistBody(current.value)
        }
    }
}

@Composable
private fun ArtistBody(artist: ArtistPage) {
    val shell = shell
    val s = strings
    val state by shell.app.controller.state.collectAsState()
    val playlistId = PlaylistId(0, artist.id, PlaylistSource.YandexMusic)
    val stats = listOfNotNull(
        artist.likes?.let(s::likesCount),
        artist.monthlyListeners?.let(s::monthlyListeners),
    ).joinToString(" · ")

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset + 16.dp)) {
        item(key = "header") {
            CollectionHeader(
                title = artist.name,
                coverUrl = artist.coverUrl,
                compact = LocalCompact.current,
                kindLabel = s.artist,
                subtitle = artist.genres.joinToString(", ").ifBlank { null },
                details = stats.ifBlank { null },
                placeholder = Icons.Filled.Person,
                round = true,
            ) {
                PillButton(
                    s.play,
                    { shell.playTracks(artist.name, artist.popular, id = playlistId) },
                    icon = Icons.Filled.PlayArrow,
                    accent = true,
                    enabled = artist.popular.isNotEmpty(),
                )
                PillButton(s.startStation, { shell.app.wave.start(listOf("artist:${artist.id}"), s.stationOn(artist.name)) }, icon = Icons.Filled.Radio)
                MenuButton(diameter = 45.dp, iconSize = 20.dp, filled = true) {
                    Item(s.allTracks, Icons.AutoMirrored.Filled.QueueMusic) { shell.openArtistTracks(artist.id, artist.name) }
                    artist.link?.let { link -> Item(s.copyLink, Icons.Filled.Link) { shell.copy(link) } }
                }
            }
        }

        if (artist.popular.isNotEmpty()) {
            item(key = "popular-title") {
                SectionTitle(s.popularTracks, Modifier.padding(horizontal = 20.dp)) {
                    PillButton(s.allTracks, { shell.openArtistTracks(artist.id, artist.name) }, height = 32.dp)
                }
            }
            itemsIndexed(artist.popular.take(POPULAR), key = { index, track -> "popular:${track.filepath}#$index" }) { index, track ->
                TrackRow(
                    track = track,
                    onClick = { shell.playTracks(artist.name, artist.popular, start = track, id = playlistId) },
                    playing = state.hasTrack && state.current.filepath == track.filepath,
                    index = index,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }

        shelf("albums", s.albums, artist.albums)
        shelf("also", s.appearsOn, artist.alsoAlbums)
        shelf("playlists", s.playlistsTitle, artist.playlists)

        if (artist.similar.isNotEmpty()) {
            item(key = "similar") {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    SectionTitle(s.similarArtists)
                    ArtistShelf(artist.similar)
                }
            }
        }
    }
}

private fun androidx.compose.foundation.lazy.LazyListScope.shelf(
    key: String,
    title: String,
    summaries: List<CollectionSummary>,
) {
    if (summaries.isEmpty()) return
    item(key = key) {
        Column(Modifier.padding(horizontal = 20.dp)) {
            SectionTitle(title, count = summaries.size)
            Shelf(summaries)
        }
    }
}

private const val POPULAR = 10
