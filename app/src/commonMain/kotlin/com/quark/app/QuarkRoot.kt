package com.quark.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quark.app.account.AccountScreen
import com.quark.app.browse.ArtistScreen
import com.quark.app.browse.CollectionScreen
import com.quark.app.export.ExportProgress
import com.quark.app.home.HomeScreen
import com.quark.app.i18n.Language
import com.quark.app.i18n.strings
import com.quark.app.image.LocalCovers
import com.quark.app.lyrics.LyricsScreen
import com.quark.app.lyrics.LyricsViewModel
import com.quark.app.nav.Navigator
import com.quark.app.nav.Screen
import com.quark.app.player.MINI_PLAYER_HEIGHT
import com.quark.app.player.MiniPlayer
import com.quark.app.player.PlayerScreen
import com.quark.app.player.PlayerViewModel
import com.quark.app.search.SearchScreen
import com.quark.app.services.SoundCloudScreen
import com.quark.app.services.SpotifyScreen
import com.quark.app.services.VkScreen
import com.quark.app.services.YandexHubScreen
import com.quark.app.services.YouTubeScreen
import com.quark.app.settings.SettingsScreen
import com.quark.app.shell.LocalShell
import com.quark.app.shell.Shell
import com.quark.app.stats.StatsScreen
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.QuarkTheme
import com.quark.app.theme.Radius
import com.quark.app.ui.Backdrop
import com.quark.app.ui.CircleButton
import com.quark.app.ui.DialogHost
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.LocalBackdrop
import com.quark.app.ui.LocalBottomInset
import com.quark.app.ui.LocalCompact
import com.quark.app.ui.LocalDialogs
import com.quark.app.ui.LocalMessages
import com.quark.app.ui.MessageLayer
import com.quark.app.ui.Messages
import com.quark.app.ui.PillButton
import com.quark.app.ui.PlatformBackHandler
import com.quark.app.ui.QIcon
import com.quark.app.ui.QText
import com.quark.app.ui.backdropBackground
import com.quark.app.vibe.VibeAnimation
import com.quark.app.yandex.YandexViewModel
import com.quark.core.settings.ThemeMode

/** What is drawn over the player besides the screens: lyrics, or the visualiser under it. */
private enum class Overlay { None, Lyrics, Vibe }

/**
 * The whole interface, the same on every platform: the desktop puts it in a
 * window, Android in an activity. Everything that differs between them comes in
 * through [platform].
 */
@Composable
fun QuarkRoot(app: QuarkApp, platform: Platform) {
    val model = remember(app) { app.retain { PlayerViewModel(app) } }
    val yandex = remember(app) { app.retain { YandexViewModel(app) } }
    val lyrics = remember(app) { app.retain { LyricsViewModel(app) } }
    val scope = rememberCoroutineScope()
    val navigator = remember(app) { app.retain { Navigator() } }
    val messages = remember(scope) { Messages(scope) }
    val dialogs = remember { DialogHost() }
    val shell = remember(app, platform, navigator) { Shell(app, platform, navigator, messages, dialogs, scope) }
    val stateHolder = rememberSaveableStateHolder()

    var overlay by remember { mutableStateOf(Overlay.None) }
    val accent by model.accent.collectAsState()
    val cover by model.cover.collectAsState()
    val state by model.state.collectAsState()
    val settings by app.settings.settings.collectAsState()

    val light = when (settings.appearance.theme) {
        ThemeMode.System -> !isSystemInDarkTheme()
        ThemeMode.Dark -> false
        ThemeMode.Light -> true
    }
    val language = Language.resolve(settings.appearance.language)
    val strings = remember(language) { language.strings() }
    shell.strings = strings

    val screen = navigator.current
    PlatformBackHandler(enabled = dialogs.isOpen || overlay != Overlay.None || navigator.canGoBack) {
        when {
            dialogs.isOpen -> dialogs.dismiss()
            overlay != Overlay.None -> overlay = Overlay.None
            else -> navigator.pop()
        }
    }

    LaunchedEffect(app) {
        app.openRequests.collect { locations ->
            model.open(locations, recursive = true) { navigator.push(Screen.Player) }
        }
    }
    // Leaving the player puts the lyrics and the visualiser away.
    LaunchedEffect(screen) { if (screen != Screen.Player) overlay = Overlay.None }

    QuarkTheme(accent = accent, light = light, strings = strings) {
        val colors = Quark.colors
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val backdrop = Backdrop(
                image = cover?.blurred?.takeIf { settings.appearance.dynamicWindowColor },
                windowSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
            )
            val compact = maxWidth < COMPACT_WIDTH
            // A window squashed to a strip or a card is the mini player, whatever was open.
            val squashed = maxHeight <= SQUASHED_HEIGHT && state.hasTrack
            val showMini = state.hasTrack && screen != Screen.Player && !squashed

            CompositionLocalProvider(
                LocalBackdrop provides backdrop,
                LocalPlatform provides platform,
                LocalShell provides shell,
                LocalMessages provides messages,
                LocalDialogs provides dialogs,
                LocalCovers provides model.coverLoader,
                LocalCompact provides compact,
                LocalBottomInset provides if (showMini) MINI_PLAYER_HEIGHT else 0.dp,
            ) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .backdropBackground(backdrop, colors.backgroundScrim, colors.background)
                ) {
                    // The visualiser sits under the interface, not over it.
                    AnimatedVisibility(
                        visible = overlay == Overlay.Vibe && screen == Screen.Player,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        VibeAnimation(accent, Modifier.fillMaxSize())
                    }

                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        if (squashed) {
                            PlayerScreen(model = model)
                        } else {
                            Crossfade(targetState = screen, animationSpec = tween(220)) { shown ->
                                stateHolder.SaveableStateProvider(shown.saveKey()) {
                                    ScreenContent(
                                        screen = shown,
                                        model = model,
                                        yandex = yandex,
                                        light = light,
                                        onOpenLyrics = { overlay = Overlay.Lyrics },
                                        onToggleVibe = {
                                            overlay = if (overlay == Overlay.Vibe) Overlay.None else Overlay.Vibe
                                        },
                                    )
                                }
                            }
                        }

                        if (showMini) {
                            Box(Modifier.fillMaxSize().padding(horizontal = 8.dp, vertical = 6.dp), contentAlignment = Alignment.BottomCenter) {
                                MiniPlayer(model, Modifier.widthIn(max = 960.dp))
                            }
                        }

                        val export by app.exporter.progress.collectAsState()
                        export?.let { progress ->
                            ExportBanner(
                                progress = progress,
                                onStop = app.exporter::cancel,
                                onDismiss = app.exporter::dismiss,
                                onOpenFolder = { platform.openUrl(folderUrl(progress.folder)) },
                                modifier = Modifier.align(Alignment.TopEnd).padding(12.dp),
                            )
                        }

                        val sheet = Modifier.fillMaxSize().padding(if (compact) 8.dp else 48.dp)
                        ModalSheet(overlay == Overlay.Lyrics) {
                            val lyricsState by lyrics.state.collectAsState()
                            val activeLine by lyrics.activeLine.collectAsState()
                            LyricsScreen(lyricsState, activeLine, { overlay = Overlay.None }, sheet)
                        }

                        dialogs.Layer()
                        MessageLayer(messages, Modifier.padding(bottom = if (showMini) MINI_PLAYER_HEIGHT else 0.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun ScreenContent(
    screen: Screen,
    model: PlayerViewModel,
    yandex: YandexViewModel,
    light: Boolean,
    onOpenLyrics: () -> Unit,
    onToggleVibe: () -> Unit,
) {
    val shell = com.quark.app.shell.shell
    when (screen) {
        Screen.Home -> HomeScreen(model, light)
        Screen.Player -> PlayerScreen(
            model = model,
            onBack = { shell.navigator.pop() },
            onOpenLyrics = onOpenLyrics,
            onOpenVibe = onToggleVibe,
            onOpenSettings = { shell.navigator.push(Screen.Settings) },
        )
        Screen.Search -> SearchScreen()
        Screen.Settings -> SettingsScreen()
        Screen.Statistics -> StatsScreen()
        Screen.Account -> AccountScreen()
        Screen.Yandex -> YandexHubScreen(yandex)
        Screen.Spotify -> SpotifyScreen()
        Screen.SoundCloud -> SoundCloudScreen()
        Screen.Vk -> VkScreen()
        Screen.YouTube -> YouTubeScreen()
        is Screen.Collection -> CollectionScreen(screen)
        is Screen.Artist -> ArtistScreen(screen)
    }
}

/** A key for the state a screen keeps while it is in the back stack. */
private fun Screen.saveKey(): String = when (this) {
    is Screen.Collection -> "collection:$key"
    is Screen.Artist -> "artist:$id"
    else -> this::class.simpleName ?: toString()
}

/** A `file:` address for a folder, which both platforms know how to open. */
private fun folderUrl(folder: String): String =
    if (folder.startsWith("/")) "file://$folder" else "file:///" + folder.replace('\\', '/')

/** How far an export has got, in the corner until it is dismissed. */
@Composable
private fun ExportBanner(
    progress: ExportProgress,
    onStop: () -> Unit,
    onDismiss: () -> Unit,
    onOpenFolder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val s = strings
    GlassSurface(RoundedCornerShape(Radius.card), modifier.widthIn(max = 360.dp), Glass.Card) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QText(progress.name, Quark.type.trackTitle, Modifier.weight(1f), maxLines = 1)
                if (progress.finished) CircleButton(Icons.Filled.Close, onDismiss, diameter = 26.dp, iconSize = 16.dp, filled = false)
            }
            QText(
                if (progress.finished) s.exported(progress.done, progress.failed, progress.folder)
                else s.exporting(progress.done + progress.failed, progress.total),
                Quark.type.label,
                color = Quark.colors.textSecondary,
                maxLines = 3,
            )
            progress.current?.let { QText(it, Quark.type.label, color = Quark.colors.textMuted, maxLines = 1) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!progress.finished) PillButton(s.cancelExport, onStop, height = 32.dp)
                else PillButton(s.openAction, onOpenFolder, icon = Icons.Filled.FolderOpen, height = 32.dp)
            }
        }
    }
}

private val COMPACT_WIDTH = 600.dp
private val SQUASHED_HEIGHT = 300.dp

/**
 * The Flutter sheets dim everything below them before drawing their glass
 * surface (`main_player.dart:1329-1333`). Keeping that scrim in one host also
 * prevents new sheets from accidentally getting a transparent modal backdrop.
 */
@Composable
private fun ModalSheet(visible: Boolean, content: @Composable () -> Unit) {
    AnimatedVisibility(visible = visible, enter = fadeIn(), exit = fadeOut()) {
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f))) {
            content()
        }
    }
}

/**
 * Shown when the application cannot start at all. On the desktop the common
 * cause by far is a missing libmpv, and its exception already says what to
 * install, so the message is passed through rather than replaced.
 */
@Composable
fun StartupFailure(cause: Throwable?) {
    QuarkTheme {
        Box(
            Modifier.fillMaxSize().background(Quark.colors.background).padding(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                QText("quark could not start", Quark.type.heading)
                QText(
                    text = cause?.message ?: strings.unknownError,
                    style = Quark.type.body.copy(textAlign = TextAlign.Center),
                    color = Quark.colors.textSecondary,
                )
            }
        }
    }
}
