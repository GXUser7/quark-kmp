package com.quark.app

import com.quark.platform.DeviceKind
import java.awt.Desktop
import java.awt.Frame
import java.awt.Toolkit
import java.awt.datatransfer.StringSelection
import java.net.URI

/** [Platform] on the desktop JVM: AWT dialogs and `java.awt.Desktop`. */
class DesktopPlatform(private val owner: () -> Frame?) : Platform {

    override val kind: DeviceKind = DeviceKind.Desktop

    override fun openUrl(url: String) {
        val opened = runCatching {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(URI(url))
                true
            } else {
                false
            }
        }.getOrDefault(false)
        if (!opened) {
            // Some Linux desktops expose no AWT browse action; xdg-open always works there.
            runCatching { ProcessBuilder("xdg-open", url).start() }
        }
    }

    override suspend fun pickFolder(): String? = FilePicker.pickFolder(owner())?.absolutePath

    override suspend fun pickAudioFiles(): List<String> =
        FilePicker.pickAudioFiles(owner()).map { it.absolutePath }

    override suspend fun pickFile(extensions: List<String>): String? =
        FilePicker.pickFile(owner(), "Open", extensions)?.absolutePath

    override val deviceLibrary: String? = null

    override fun copyToClipboard(text: String) {
        runCatching { Toolkit.getDefaultToolkit().systemClipboard.setContents(StringSelection(text), null) }
    }
}
