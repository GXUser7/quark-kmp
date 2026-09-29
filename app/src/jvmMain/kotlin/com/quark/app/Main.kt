package com.quark.app

import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.runBlocking
import org.jetbrains.skia.Image

fun main() = application {
    val started = remember { DesktopQuark.start() }
    val icon = remember { windowIcon() }

    Window(
        onCloseRequest = {
            started.getOrNull()?.let { app -> runBlocking { app.shutdown() } }
            exitApplication()
        },
        title = "quark",
        icon = icon,
        state = rememberWindowState(width = 1100.dp, height = 760.dp),
        onPreviewKeyEvent = { event -> started.getOrNull()?.let { quarkShortcut(it, event) } ?: false },
    ) {
        val app = started.getOrNull()
        if (app == null) {
            StartupFailure(started.exceptionOrNull())
            return@Window
        }
        val platform = remember { DesktopPlatform { window } }
        QuarkRoot(app, platform)
    }
}

/** The atom from the Flutter build's `assets/icon512.png`, for the title bar and taskbar. */
private fun windowIcon(): Painter? = runCatching {
    val bytes = Thread.currentThread().contextClassLoader
        ?.getResourceAsStream("quark.png")
        ?.use { it.readBytes() }
        ?: return@runCatching null
    BitmapPainter(Image.makeFromEncoded(bytes).toComposeImageBitmap())
}.getOrNull()
