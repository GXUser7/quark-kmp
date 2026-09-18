package com.quark.app

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.quark.app.player.PlayerScreen
import com.quark.app.player.PlayerViewModel
import com.quark.app.theme.QuarkTheme
import com.quark.core.player.PlayerState
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

        QuarkTheme(accent = accent) {
            Surface(Modifier.fillMaxSize()) {
                Column(Modifier.fillMaxSize()) {
                    Toolbar(model, window)
                    PlayerScreen(model, Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun Toolbar(model: PlayerViewModel, owner: Frame?) {
    val state: PlayerState by model.state.collectAsState()

    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = { model.open(FilePicker.pickAudioFiles(owner)) }) { Text("Add files") }
        OutlinedButton(onClick = {
            FilePicker.pickFolder(owner)?.let { model.open(listOf(it)) }
        }) { Text("Add folder") }

        Box(Modifier.weight(1f))

        if (state.queue.isNotEmpty()) {
            OutlinedButton(onClick = model::clearQueue) { Text("Clear queue (${state.queue.size})") }
        }
    }
}

/**
 * Shown when the application cannot start at all. The common cause by far is a
 * missing libmpv, and [com.quark.player.mpv.MpvNotFoundException] already says
 * what to install, so the message is passed through rather than replaced.
 */
@Composable
private fun StartupFailure(cause: Throwable?) {
    Surface(Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().padding(48.dp), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 560.dp),
            ) {
                Text("quark could not start", style = MaterialTheme.typography.headlineSmall)
                Text(
                    text = cause?.message ?: "Unknown error.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
