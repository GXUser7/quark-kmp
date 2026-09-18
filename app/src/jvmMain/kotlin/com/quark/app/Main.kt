package com.quark.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.quark.app.player.PlayerScreen
import com.quark.app.player.PlayerViewModel
import com.quark.app.theme.Quark
import com.quark.app.theme.QuarkTheme
import com.quark.app.ui.Backdrop
import com.quark.app.ui.LocalBackdrop
import com.quark.app.ui.PillButton
import com.quark.app.ui.QText
import com.quark.app.ui.backdropBackground
import java.awt.Frame

fun main() = application {
    val started = remember { QuarkApp.start() }

    Window(
        onCloseRequest = {
            started.getOrNull()?.shutdown()
            exitApplication()
        },
        title = "quark",
        state = rememberWindowState(width = 1100.dp, height = 760.dp),
    ) {
        val app = started.getOrNull()
        if (app == null) {
            QuarkTheme { StartupFailure(started.exceptionOrNull()) }
            return@Window
        }

        val model = remember { PlayerViewModel(app) }
        val accent by model.accent.collectAsState()
        val cover by model.cover.collectAsState()
        val state by model.state.collectAsState()
        val windowSize = LocalWindowInfo.current.containerSize

        QuarkTheme(accent = accent) {
            val colors = Quark.colors
            val backdrop = Backdrop(
                image = cover?.blurred,
                windowSize = Size(windowSize.width.toFloat(), windowSize.height.toFloat()),
            )

            CompositionLocalProvider(LocalBackdrop provides backdrop) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .backdropBackground(backdrop, colors.backgroundScrim, colors.background)
                ) {
                    if (state.playlist.isEmpty()) {
                        StartScreen(model, window)
                    } else {
                        PlayerScreen(model)
                    }
                }
            }
        }
    }
}

/**
 * What the window shows before there is anything to play, matching the
 * original's opening screen: a line about the player and the ways in.
 */
@Composable
private fun StartScreen(model: PlayerViewModel, owner: Frame?) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
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
            PillButton(
                text = "Add folder",
                onClick = { FilePicker.pickFolder(owner)?.let { model.open(listOf(it)) } },
                modifier = Modifier.width(350.dp),
            )
            Spacer(Modifier.height(12.dp))
            PillButton(
                text = "Add files",
                onClick = { model.open(FilePicker.pickAudioFiles(owner)) },
                modifier = Modifier.width(350.dp),
            )
        }
    }
}

/**
 * Shown when the application cannot start at all. The common cause by far is a
 * missing libmpv, and `MpvNotFoundException` already says what to install, so
 * the message is passed through rather than replaced.
 */
@Composable
private fun StartupFailure(cause: Throwable?) {
    Box(
        Modifier.fillMaxSize().padding(48.dp),
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
