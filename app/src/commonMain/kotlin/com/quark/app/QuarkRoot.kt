package com.quark.app

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quark.app.lyrics.LyricsScreen
import com.quark.app.lyrics.LyricsViewModel
import com.quark.app.player.PlayerScreen
import com.quark.app.player.PlayerViewModel
import com.quark.app.settings.SettingsScreen
import com.quark.app.theme.Quark
import com.quark.app.theme.QuarkTheme
import com.quark.app.ui.Backdrop
import com.quark.app.ui.LocalBackdrop
import com.quark.app.ui.PillButton
import com.quark.app.ui.PlatformBackHandler
import com.quark.app.ui.QText
import com.quark.app.ui.backdropBackground
import com.quark.app.vibe.VibeAnimation
import com.quark.app.yandex.YandexScreen
import com.quark.app.yandex.YandexViewModel
import kotlinx.coroutines.launch

/** Which sheet, if any, is over the player. Only one at a time. */
private enum class Overlay { None, Yandex, Lyrics, Settings, Vibe }

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
    var overlay by remember { mutableStateOf(Overlay.None) }
    val accent by model.accent.collectAsState()
    val cover by model.cover.collectAsState()
    val state by model.state.collectAsState()

    PlatformBackHandler(enabled = overlay != Overlay.None) { overlay = Overlay.None }

    LaunchedEffect(app) {
        app.openRequests.collect { locations -> model.open(locations, recursive = true) }
    }

    QuarkTheme(accent = accent) {
        val colors = Quark.colors
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val backdrop = Backdrop(
                image = cover?.blurred,
                windowSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat()),
            )
            val compact = maxWidth < COMPACT_WIDTH

            CompositionLocalProvider(LocalBackdrop provides backdrop, LocalPlatform provides platform) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .backdropBackground(backdrop, colors.backgroundScrim, colors.background)
                ) {
                    // The visualiser sits under the interface, not over it.
                    AnimatedVisibility(
                        visible = overlay == Overlay.Vibe,
                        enter = fadeIn(),
                        exit = fadeOut(),
                    ) {
                        VibeAnimation(accent, Modifier.fillMaxSize())
                    }

                    Box(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                        if (state.playlist.isEmpty()) {
                            StartScreen(
                                model = model,
                                platform = platform,
                                onYandex = { overlay = Overlay.Yandex },
                                onSettings = { overlay = Overlay.Settings },
                            )
                        } else {
                            PlayerScreen(
                                model = model,
                                onOpenLyrics = { overlay = Overlay.Lyrics },
                                onOpenVibe = {
                                    overlay = if (overlay == Overlay.Vibe) Overlay.None else Overlay.Vibe
                                },
                                onOpenSettings = { overlay = Overlay.Settings },
                            )
                        }

                        val sheet = Modifier.fillMaxSize().padding(if (compact) 8.dp else 48.dp)
                        ModalSheet(overlay == Overlay.Yandex) {
                            YandexScreen(yandex, { overlay = Overlay.None }, sheet)
                        }
                        ModalSheet(overlay == Overlay.Lyrics) {
                            val lyricsState by lyrics.state.collectAsState()
                            val activeLine by lyrics.activeLine.collectAsState()
                            LyricsScreen(lyricsState, activeLine, { overlay = Overlay.None }, sheet)
                        }
                        ModalSheet(overlay == Overlay.Settings) {
                            SettingsScreen(app.settings, { overlay = Overlay.None }, sheet)
                        }
                    }
                }
            }
        }
    }
}

private val COMPACT_WIDTH = 600.dp

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
 * What the window shows before there is anything to play, matching the
 * original's opening screen: a line about the player and the ways in.
 */
@Composable
private fun StartScreen(
    model: PlayerViewModel,
    platform: Platform,
    onYandex: () -> Unit,
    onSettings: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.widthIn(max = 350.dp).fillMaxWidth(),
        ) {
            QText("quark: where sound begins", Quark.type.heading)
            Spacer(Modifier.height(14.dp))
            QText(
                text = "Select the folder with tracks.",
                style = Quark.type.body.copy(textAlign = TextAlign.Center),
                color = Quark.colors.textSecondary,
            )
            QText(
                text = "You can also link your streaming account to use it.",
                style = Quark.type.body.copy(textAlign = TextAlign.Center),
                color = Quark.colors.textSecondary,
            )

            Spacer(Modifier.height(28.dp))
            platform.deviceLibrary?.let { device ->
                PillButton(
                    text = "Music on this device",
                    onClick = {
                        scope.launch {
                            if (platform.requestLibraryAccess()) model.open(listOf(device), name = "Device")
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
            }
            PillButton(
                text = "Add folder",
                onClick = { scope.launch { platform.pickFolder()?.let { model.open(listOf(it)) } } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = "Add files",
                onClick = { scope.launch { model.open(platform.pickAudioFiles()) } },
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = "Yandex Music",
                onClick = onYandex,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = "Preferences",
                onClick = onSettings,
                modifier = Modifier.fillMaxWidth(),
            )
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
                    text = cause?.message ?: "Unknown error.",
                    style = Quark.type.body.copy(textAlign = TextAlign.Center),
                    color = Quark.colors.textSecondary,
                )
            }
        }
    }
}
