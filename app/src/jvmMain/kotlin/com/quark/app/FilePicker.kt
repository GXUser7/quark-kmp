package com.quark.app

import com.quark.data.local.AUDIO_EXTENSIONS
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import javax.swing.JFileChooser
import javax.swing.UIManager

/**
 * Native file and folder dialogs, replacing `file_picker`.
 *
 * Files go through AWT's [FileDialog], which is the real system dialog on
 * Windows and macOS. Folders go through Swing's chooser, because [FileDialog]
 * cannot select a directory on Windows at all.
 */
object FilePicker {

    fun pickAudioFiles(owner: Frame?): List<File> {
        val dialog = FileDialog(owner, "Add music", FileDialog.LOAD).apply {
            isMultipleMode = true
            setFilenameFilter { _, name -> name.substringAfterLast('.', "").lowercase() in AUDIO_EXTENSIONS }
        }
        dialog.isVisible = true
        return dialog.files?.toList().orEmpty()
    }

    fun pickFolder(owner: Frame?): File? {
        useSystemLookAndFeel()
        val chooser = JFileChooser().apply {
            fileSelectionMode = JFileChooser.DIRECTORIES_ONLY
            dialogTitle = "Add folder"
        }
        return if (chooser.showOpenDialog(owner) == JFileChooser.APPROVE_OPTION) {
            chooser.selectedFile
        } else {
            null
        }
    }

    /** Swing defaults to Metal, which looks nothing like the host system. */
    private fun useSystemLookAndFeel() {
        runCatching { UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName()) }
    }
}
