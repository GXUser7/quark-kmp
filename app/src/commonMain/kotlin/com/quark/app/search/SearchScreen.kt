package com.quark.app.search

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import com.quark.app.browse.ArtistShelf
import com.quark.app.browse.CollectionKind
import com.quark.app.browse.SearchSection
import com.quark.app.browse.SearchService
import com.quark.app.browse.SectionTitle
import com.quark.app.browse.Shelf
import com.quark.app.browse.TopBar
import com.quark.app.browse.TrackCollection
import com.quark.app.browse.TrackRow
import com.quark.app.i18n.Strings
import com.quark.app.i18n.strings
import com.quark.app.nav.Screen
import com.quark.app.shell.shell
import com.quark.app.theme.Quark
import com.quark.app.ui.Load
import com.quark.app.ui.PillButton
import com.quark.app.ui.Placeholder
import com.quark.app.ui.QText
import com.quark.app.ui.QTextField
import com.quark.app.ui.bottomInset
import com.quark.app.ui.rememberLoad
import com.quark.core.model.PlaylistId
import com.quark.core.model.PlaylistSource
import com.quark.core.model.Track
import kotlinx.coroutines.delay

/**
 * Search everywhere at once (`multi_search.dart`): the library and every
 * service that is signed in and has search switched on, each answering on its
 * own so one slow or failing service holds nothing up.
 */
@Composable
fun SearchScreen() {
    val shell = shell
    val s = strings
    val app = shell.app
    var query by rememberSaveable { mutableStateOf("") }
    var submitted by rememberSaveable { mutableStateOf("") }
    val available = remember { app.search.available() }
    var chosen by remember { mutableStateOf(available.toSet()) }
    val focus = remember { FocusRequester() }

    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    // Typing pauses are searches too, as in the Dart build's debounce.
    LaunchedEffect(query) {
        if (query.isBlank()) return@LaunchedEffect
        delay(700)
        submitted = query.trim()
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(s.search, onBack = { shell.navigator.pop() })
        QTextField(
            value = query,
            onValueChange = { query = it },
            placeholder = s.searchHint,
            leading = Icons.Filled.Search,
            onSubmit = { submitted = query.trim() },
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).focusRequester(focus),
        )
        LazyRow(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(available, key = { it.name }) { service ->
                val on = service in chosen
                PillButton(
                    service.label(s),
                    { chosen = if (on) chosen - service else chosen + service },
                    icon = if (on) Icons.Filled.Check else null,
                    accent = on,
                    height = 34.dp,
                )
            }
        }

        if (submitted.isBlank()) {
            Placeholder(s.searchHint, Modifier.fillMaxSize())
        } else {
            val services = available.filter { it in chosen }
            SearchResults(submitted, services)
        }
    }
}

@Composable
private fun SearchResults(submitted: String, services: List<SearchService>) {
    val shell = shell
    val s = strings
    val key = "search:$submitted:" + services.joinToString(",") { it.name }
    val results by rememberLoad(key, cache = shell.cache) { shell.app.search.search(submitted, services) }
    when (val current = results) {
        Load.Loading -> Placeholder(s.loading, Modifier.fillMaxSize())
        is Load.Failed -> Placeholder(current.message, Modifier.fillMaxSize())
        is Load.Ready -> Results(submitted, current.value)
    }
}

@Composable
private fun Results(query: String, sections: List<SearchSection>) {
    val shell = shell
    val s = strings
    val state by shell.app.controller.state.collectAsState()
    val nothing = sections.all { it.tracks.isEmpty() && it.albums.isEmpty() && it.playlists.isEmpty() && it.artists.isEmpty() && it.error == null }
    if (nothing) {
        Placeholder(s.nothingFound, Modifier.fillMaxSize())
        return
    }

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset + 16.dp)) {
        sections.forEach { section ->
            val name = section.service.label(s)
            val error = section.error
            if (error != null) {
                item(key = "${section.service}:error") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        SectionTitle(name)
                        QText(error, Quark.type.label, color = Quark.colors.danger)
                    }
                }
                return@forEach
            }
            if (section.tracks.isEmpty() && section.albums.isEmpty() && section.playlists.isEmpty() && section.artists.isEmpty()) {
                return@forEach
            }
            val collection = TrackCollection(
                key = "search:${section.service}:$query",
                title = "$name: $query",
                tracks = section.tracks,
                playlistId = PlaylistId(0, "$query:${section.service}".hashCode().toLong(), PlaylistSource.Local),
                kind = CollectionKind.Search,
            )
            item(key = "${section.service}:title") {
                SectionTitle(name, Modifier.padding(horizontal = 20.dp), count = section.tracks.size.takeIf { it > 0 }) {
                    if (section.tracks.isNotEmpty()) {
                        PillButton(s.addAll, { shell.addToPlaylist(section.tracks) }, icon = Icons.AutoMirrored.Filled.PlaylistAdd, height = 32.dp)
                        if (section.tracks.size > SHOWN) {
                            PillButton(s.allTracks, {
                                shell.navigator.push(Screen.Collection(collection.key, collection.title) { collection })
                            }, height = 32.dp)
                        }
                    }
                }
            }
            itemsIndexed(section.tracks.take(SHOWN), key = { index, track -> "${section.service}:${track.filepath}#$index" }) { _, track ->
                TrackRow(
                    track = track,
                    onClick = { shell.play(collection, start = track) },
                    playing = state.hasTrack && state.current.filepath == track.filepath,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
            if (section.artists.isNotEmpty()) {
                item(key = "${section.service}:artists") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        QText(s.artists, Quark.type.trackTitle, color = Quark.colors.textSecondary)
                        ArtistShelf(section.artists)
                    }
                }
            }
            if (section.albums.isNotEmpty()) {
                item(key = "${section.service}:albums") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        QText(s.albums, Quark.type.trackTitle, color = Quark.colors.textSecondary)
                        Shelf(section.albums)
                    }
                }
            }
            if (section.playlists.isNotEmpty()) {
                item(key = "${section.service}:playlists") {
                    Column(Modifier.padding(horizontal = 20.dp, vertical = 6.dp)) {
                        QText(s.playlistsTitle, Quark.type.trackTitle, color = Quark.colors.textSecondary)
                        Shelf(section.playlists)
                    }
                }
            }
        }
    }
}

fun SearchService.label(s: Strings): String = when (this) {
    SearchService.Local -> s.local
    SearchService.Yandex -> s.yandexMusic
    SearchService.YouTube -> s.youtubeMusic
    SearchService.SoundCloud -> s.soundCloud
    SearchService.Spotify -> s.spotify
    SearchService.Vk -> s.vkMusic
}

private const val SHOWN = 10
