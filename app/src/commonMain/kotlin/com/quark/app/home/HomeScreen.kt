package com.quark.app.home

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Login
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.quark.app.browse.ServiceCard
import com.quark.app.i18n.strings
import com.quark.app.image.Artwork
import com.quark.app.image.rememberPicture
import com.quark.app.image.rememberThumbnail
import com.quark.app.nav.Screen
import com.quark.app.player.PlayerViewModel
import com.quark.app.shell.shell
import com.quark.app.theme.Quark
import com.quark.app.ui.CircleButton
import com.quark.app.ui.Gap
import com.quark.app.ui.LocalCompact
import com.quark.app.ui.QIcon
import com.quark.app.ui.QText
import com.quark.app.ui.QTextField
import com.quark.app.ui.bottomInset
import com.quark.app.yandex.YandexState
import com.quark.core.model.Track
import com.quark.core.settings.ThemeMode
import com.quark.data.repository.StoredPlaylist
import kotlinx.coroutines.launch

/**
 * The start page of the slop branch's `MainPage`: the header with the theme,
 * account and settings buttons, the card to go back to what was playing, the
 * grid of places music comes from, and the user's own playlists.
 */
@Composable
fun HomeScreen(player: PlayerViewModel, light: Boolean) {
    val shell = shell
    val s = strings
    val app = shell.app
    val platform = shell.platform
    val scope = rememberCoroutineScope()
    val state by app.controller.state.collectAsState()
    val settings by app.settings.settings.collectAsState()
    val yandexState by app.yandex.state.collectAsState()
    val playlists by app.userLibrary.all.collectAsState()
    var query by remember { mutableStateOf("") }
    val filtered = remember(playlists, query) {
        val q = query.trim().lowercase()
        if (q.isEmpty()) playlists else playlists.filter { q in it.title.lowercase() }
    }
    val compact = LocalCompact.current
    val columns = if (compact) 1 else 3

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 16.dp, bottom = bottomInset + 24.dp),
    ) {
        item(key = "header") {
            Header(
                light = light,
                signedIn = settings.account.isSignedIn,
                onTheme = {
                    app.settings.update {
                        it.copy(appearance = it.appearance.copy(theme = if (light) ThemeMode.Dark else ThemeMode.Light))
                    }
                },
                onAccount = { shell.navigator.push(Screen.Account) },
                onSettings = { shell.navigator.push(Screen.Settings) },
            )
        }

        item(key = "resume") {
            Gap(8)
            if (state.hasTrack) {
                ResumeCard(
                    title = state.playlistInfo.name,
                    subtitle = "${state.current.title} — ${state.current.artistLine}",
                    label = if (state.isPlaying) s.nowPlaying else s.resumeListen,
                    count = s.tracks(state.playlist.size),
                    onClick = shell::openPlayer,
                )
            } else {
                WelcomeHero(s.readyToListen, s.selectFolderHint)
            }
            Gap(24)
        }

        item(key = "services") {
            QText(s.musicServices, Quark.type.panelTitle)
            Gap(12)
            val cards = buildList<ServiceEntry> {
                add(ServiceEntry(s.pickFolder, Icons.Filled.FolderOpen, Color(0xFF448AFF)) {
                    scope.launch {
                        platform.pickFolder()?.let { folder -> player.open(listOf(folder)) { shell.navigator.push(Screen.Player) } }
                    }
                })
                add(ServiceEntry(s.addFiles, Icons.Filled.AudioFile, Color(0xFF7C4DFF)) {
                    scope.launch {
                        val files = platform.pickAudioFiles()
                        if (files.isNotEmpty()) player.open(files) { shell.navigator.push(Screen.Player) }
                    }
                })
                platform.deviceLibrary?.let { device ->
                    add(ServiceEntry(s.deviceMusic, Icons.Filled.PhoneAndroid, Color(0xFF00BFA5)) {
                        scope.launch {
                            if (platform.requestLibraryAccess()) {
                                player.open(listOf(device), name = s.deviceMusic) { shell.navigator.push(Screen.Player) }
                            }
                        }
                    })
                }
                add(ServiceEntry(s.yandexMusic, Icons.Filled.Waves, Color(0xFFFFCC00), badge = s.active.takeIf { yandexState is YandexState.SignedIn }) {
                    shell.navigator.push(Screen.Yandex)
                })
                add(ServiceEntry(s.youtubeMusic, Icons.Filled.SmartDisplay, Color(0xFFFF0000), badge = s.active.takeIf { settings.youtube.cookies.isNotBlank() }) {
                    shell.navigator.push(Screen.YouTube)
                })
                add(ServiceEntry(s.soundCloud, Icons.Filled.Cloud, Color(0xFFFF5500), badge = s.active.takeIf { settings.soundCloud.profileUrl.isNotBlank() }) {
                    shell.navigator.push(Screen.SoundCloud)
                })
                add(ServiceEntry(s.spotify, Icons.Filled.Headphones, Color(0xFF1DB954), badge = s.active.takeIf { settings.spotify.isSignedIn }) {
                    shell.navigator.push(Screen.Spotify)
                })
                add(ServiceEntry(s.vkMusic, Icons.Filled.GraphicEq, Color(0xFF0077FF), badge = s.active.takeIf { settings.vk.isConnected }) {
                    shell.navigator.push(Screen.Vk)
                })
                add(ServiceEntry(s.search, Icons.Filled.Search, Color(0xFF64FFDA)) { shell.navigator.push(Screen.Search) })
                add(ServiceEntry(s.statistics, Icons.Filled.BarChart, Color(0xFFB388FF)) { shell.navigator.push(Screen.Statistics) })
                add(ServiceEntry(s.onGitHub, Icons.Filled.Code, Color(0xFFF0F6FC)) { platform.openUrl(GITHUB) })
            }
            ServiceGrid(cards, if (compact) 2 else 3)
            Gap(28)
        }

        item(key = "playlists-title") {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                QIcon(Icons.Filled.LibraryMusic, Modifier.size(22.dp), Quark.accent.primary)
                QText(s.yourPlaylists, Quark.type.panelTitle, Modifier.weight(1f))
                CircleButton(Icons.Filled.Add, { shell.createPlaylist() }, diameter = 32.dp, iconSize = 18.dp)
            }
            Gap(12)
            if (playlists.size > 4) {
                QTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = s.searchPlaylists,
                    leading = Icons.Filled.Search,
                    modifier = Modifier.fillMaxWidth(),
                )
                Gap(12)
            }
            when {
                playlists.isEmpty() -> QText(s.noPlaylistsYet, Quark.type.body, color = Quark.colors.textMuted)
                filtered.isEmpty() -> QText(s.noPlaylistsMatch(query), Quark.type.body, color = Quark.colors.textMuted)
            }
        }

        items(filtered.chunked(columns), key = { row -> "playlists:" + row.joinToString(",") { it.id.toString() } }) { row ->
            Row(Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { playlist ->
                    PlaylistChip(playlist, Modifier.weight(1f)) { shell.openUserPlaylist(playlist) }
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

private class ServiceEntry(
    val label: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val color: Color,
    val badge: String? = null,
    val onClick: () -> Unit,
)

/** The cards two or three to a row, as `_buildServicesGrid` laid them out. */
@Composable
private fun ServiceGrid(cards: List<ServiceEntry>, columns: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        cards.chunked(columns).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                row.forEach { card ->
                    ServiceCard(card.label, card.icon, card.color, card.onClick, Modifier.weight(1f), card.badge)
                }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@Composable
private fun Header(
    light: Boolean,
    signedIn: Boolean,
    onTheme: () -> Unit,
    onAccount: () -> Unit,
    onSettings: () -> Unit,
) {
    val s = strings
    Row(
        Modifier.fillMaxWidth().padding(bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        QuarkLogo(Modifier.size(44.dp))
        Column(Modifier.weight(1f)) {
            QText(s.appName, Quark.type.heading, maxLines = 1)
            QText(s.tagline, Quark.type.label, color = Quark.colors.textMuted, maxLines = 1)
        }
        CircleButton(if (light) Icons.Filled.DarkMode else Icons.Filled.LightMode, onTheme, diameter = 40.dp, iconSize = 20.dp)
        CircleButton(if (signedIn) Icons.Filled.Person else Icons.AutoMirrored.Filled.Login, onAccount, diameter = 40.dp, iconSize = 20.dp)
        CircleButton(Icons.Filled.Settings, onSettings, diameter = 40.dp, iconSize = 20.dp)
    }
}

/**
 * The atom of the app icon, drawn rather than shipped as a bitmap: three
 * orbits and the nucleus, in the cover's colour.
 */
@Composable
fun QuarkLogo(modifier: Modifier = Modifier) {
    val color = Quark.accent.primary
    val text = Quark.colors.text
    Box(modifier.clip(CircleShape).background(color.copy(alpha = 0.15f)), contentAlignment = Alignment.Center) {
        listOf(0f, 60f, 120f).forEach { angle ->
            Canvas(Modifier.fillMaxSize().padding(6.dp).rotate(angle)) {
                val w = size.width
                val h = size.height * 0.38f
                drawOval(
                    color = text.copy(alpha = 0.85f),
                    topLeft = Offset(0f, (size.height - h) / 2f),
                    size = Size(w, h),
                    style = Stroke(width = size.width * 0.06f),
                )
            }
        }
        Canvas(Modifier.size(8.dp)) { drawCircle(color = color) }
    }
}

@Composable
private fun ResumeCard(title: String, subtitle: String, label: String, count: String, onClick: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val primary = Quark.accent.primary
    val border by animateColorAsState(primary.copy(alpha = if (hovered) 0.45f else 0.15f))
    val shape = RoundedCornerShape(24.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(primary.copy(alpha = 0.22f), primary.copy(alpha = 0.05f))))
            .border(1.5.dp, border, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Box(
            Modifier.size(52.dp).clip(CircleShape).background(primary.copy(alpha = 0.18f)).border(1.dp, primary.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            QIcon(Icons.Filled.PlayArrow, Modifier.size(28.dp), Quark.colors.text)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            QText(label, Quark.type.label, color = Quark.colors.textSecondary)
            QText(title, Quark.type.panelTitle, maxLines = 1)
            QText(subtitle, Quark.type.trackSubtitle, color = Quark.colors.textSecondary, maxLines = 1)
            QText(count, Quark.type.label, color = Quark.colors.textMuted)
        }
        QIcon(Icons.Filled.ChevronRight, Modifier.size(24.dp), Quark.colors.textMuted)
    }
}

@Composable
private fun WelcomeHero(title: String, text: String) {
    val colors = Quark.colors
    val shape = RoundedCornerShape(24.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(colors.text.copy(alpha = 0.05f), colors.text.copy(alpha = 0.01f))))
            .border(1.dp, colors.divider, shape)
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QText(title, Quark.type.heading)
        QText(text, Quark.type.body, color = colors.textSecondary)
    }
}

/** One of the user's playlists: its cover — or its first track's — and its size. */
@Composable
private fun PlaylistChip(playlist: StoredPlaylist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shell = shell
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = Quark.colors
    val background by animateColorAsState(if (hovered) colors.controlHover else colors.control)
    val border by animateColorAsState(if (hovered) Quark.accent.primary.copy(alpha = 0.35f) else colors.divider)
    val shape = RoundedCornerShape(16.dp)
    val stand by produceState<Track?>(null, playlist.id, playlist.trackCount) {
        if (playlist.coverUrl == null) value = runCatching { shell.app.userLibrary.coverTrack(playlist.id) }.getOrNull()
    }
    val picture by rememberPicture(playlist.coverUrl, 160)

    Row(
        modifier
            .height(68.dp)
            .clip(shape)
            .background(background)
            .border(1.dp, border, shape)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val track = stand
        if (playlist.coverUrl == null && track != null) {
            val thumbnail by rememberThumbnail(track)
            Artwork(thumbnail, RoundedCornerShape(10.dp), Modifier.size(50.dp), Icons.AutoMirrored.Filled.QueueMusic)
        } else {
            Artwork(picture, RoundedCornerShape(10.dp), Modifier.size(50.dp), Icons.AutoMirrored.Filled.QueueMusic)
        }
        Column(Modifier.weight(1f).widthIn(min = 0.dp)) {
            QText(playlist.title, Quark.type.trackTitle, maxLines = 1)
            QText(strings.tracks(playlist.trackCount.toInt()), Quark.type.label, color = colors.textMuted, maxLines = 1)
        }
    }
}

private const val GITHUB = "https://github.com/z3nsh0w/quark/"
