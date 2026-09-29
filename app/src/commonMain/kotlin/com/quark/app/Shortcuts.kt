package com.quark.app

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import com.quark.app.nav.Navigator
import com.quark.app.nav.Screen
import com.quark.app.ui.DialogHost
import kotlinx.coroutines.launch

/**
 * The keyboard, for the window that has one: Escape goes back or closes the
 * dialog, Ctrl+F searches, Ctrl+arrows skip and turn the volume, and the
 * media keys do what they say. Returns whether the key was used.
 */
fun quarkShortcut(app: QuarkApp, event: KeyEvent): Boolean {
    if (event.type != KeyEventType.KeyDown) return false
    val navigator = app.retain { Navigator() }
    val dialogs = app.retain { DialogHost() }
    val controller = app.controller
    val ctrl = event.isCtrlPressed || event.isMetaPressed
    fun run(block: suspend () -> Unit): Boolean {
        app.scope.launch { block() }
        return true
    }
    return when {
        event.key == Key.Escape -> when {
            dialogs.isOpen -> { dialogs.dismiss(); true }
            else -> navigator.pop()
        }
        ctrl && event.key == Key.F -> { navigator.push(Screen.Search); true }
        ctrl && event.key == Key.DirectionRight -> run { controller.next() }
        ctrl && event.key == Key.DirectionLeft -> run { controller.previous() }
        ctrl && event.key == Key.DirectionUp -> run { changeVolume(app, +0.05f) }
        ctrl && event.key == Key.DirectionDown -> run { changeVolume(app, -0.05f) }
        event.key == Key.MediaPlayPause -> run { controller.playPause() }
        event.key == Key.MediaNext -> run { controller.next() }
        event.key == Key.MediaPrevious -> run { controller.previous() }
        else -> false
    }
}

private suspend fun changeVolume(app: QuarkApp, by: Float) {
    val volume = (app.controller.state.value.volume + by).coerceIn(0f, 1f)
    app.controller.setVolume(volume)
    app.settings.update { it.copy(playback = it.playback.copy(volume = volume)) }
}
