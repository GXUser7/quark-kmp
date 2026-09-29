package com.quark.app.desktop

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import com.quark.app.QuarkApp
import com.quark.app.player.PlayerViewModel
import com.quark.core.settings.ThemeMode
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer

/**
 * The title bar on Windows, as the Flutter build set it up: dark to match the
 * dark theme (`DwmSetWindowAttribute`, immersive dark mode), and on Windows 11
 * tinted with the cover's colour while "Dynamic window color" is on. Older
 * systems refuse the attributes they do not know, which is harmless.
 */
@Composable
fun WindowChrome(window: ComposeWindow, app: QuarkApp) {
    if (!WindowsMediaControls.isWindows) return
    val model = remember(app) { app.retain { PlayerViewModel(app) } }
    val accent by model.accent.collectAsState()
    val settings by app.settings.settings.collectAsState()
    val light = when (settings.appearance.theme) {
        ThemeMode.System -> !isSystemInDarkTheme()
        ThemeMode.Dark -> false
        ThemeMode.Light -> true
    }
    val dynamic = settings.appearance.dynamicWindowColor
    LaunchedEffect(light, accent, dynamic) {
        val background = if (light) Color(0xFFF1F1F4) else Color(0xFF0E0E11)
        val caption = if (dynamic) lerp(background, accent.primary, if (light) 0.25f else 0.35f) else background
        Dwm.apply(window.windowHandle, dark = !light, caption = caption, text = if (light) Color(0xFF141418) else Color.White)
    }
}

private object Dwm {
    @Suppress("FunctionName")
    private interface DwmApi : Library {
        fun DwmSetWindowAttribute(window: Pointer, attribute: Int, value: Pointer, size: Int): Int
    }

    private val api: DwmApi? by lazy { runCatching { Native.load("dwmapi", DwmApi::class.java) }.getOrNull() }

    fun apply(handle: Long, dark: Boolean, caption: Color, text: Color) {
        val dwm = api ?: return
        if (handle == 0L) return
        val window = Pointer(handle)
        runCatching {
            val flag = Memory(4).apply { setInt(0, if (dark) 1 else 0) }
            // 20 on Windows 10 20H1 and later, 19 on the builds before it.
            if (dwm.DwmSetWindowAttribute(window, IMMERSIVE_DARK_MODE, flag, 4) != 0) {
                dwm.DwmSetWindowAttribute(window, IMMERSIVE_DARK_MODE_OLD, flag, 4)
            }
            dwm.DwmSetWindowAttribute(window, CAPTION_COLOR, colorRef(caption), 4)
            dwm.DwmSetWindowAttribute(window, TEXT_COLOR, colorRef(text), 4)
        }
    }

    /** COLORREF: 0x00BBGGRR. */
    private fun colorRef(color: Color): Memory {
        val r = (color.red * 255).toInt() and 0xff
        val g = (color.green * 255).toInt() and 0xff
        val b = (color.blue * 255).toInt() and 0xff
        return Memory(4).apply { setInt(0, (b shl 16) or (g shl 8) or r) }
    }

    private const val IMMERSIVE_DARK_MODE = 20
    private const val IMMERSIVE_DARK_MODE_OLD = 19
    private const val CAPTION_COLOR = 35
    private const val TEXT_COLOR = 36
}
