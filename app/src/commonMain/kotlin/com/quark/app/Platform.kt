package com.quark.app

import com.quark.platform.DeviceKind

/**
 * What the shared interface needs from the machine it runs on and cannot say in
 * common Kotlin: dialogs, the browser, the few things only one platform has.
 *
 * Implemented by `DesktopPlatform` (AWT dialogs, `java.awt.Desktop`) and
 * `AndroidPlatform` (activity result contracts, intents).
 */
interface Platform {
    val kind: DeviceKind

    /** Opens [url] in the system browser. */
    fun openUrl(url: String)

    /**
     * Asks for a folder of music. The result is a location the library
     * understands — a path, or a tree uri on Android — or null if cancelled.
     */
    suspend fun pickFolder(): String?

    /** Asks for one or more audio files. Empty if cancelled. */
    suspend fun pickAudioFiles(): List<String>

    /** Asks for a single file of the given kind, such as a cookie export. */
    suspend fun pickFile(extensions: List<String>): String?

    /**
     * A location standing for all the music the system already indexed, where
     * there is such a thing: the media store on Android. Null on the desktop.
     */
    val deviceLibrary: String?

    /** Asks for whatever permission reading [deviceLibrary] needs; true if granted. */
    suspend fun requestLibraryAccess(): Boolean = true

    /** Shows a short message the way the platform does, or does nothing. */
    fun toast(message: String) {}

    /** Puts [text] on the clipboard. */
    fun copyToClipboard(text: String) {}
}

val Platform.isDesktop: Boolean get() = kind == DeviceKind.Desktop
val Platform.isAndroid: Boolean get() = kind == DeviceKind.Android

/** The [Platform] the interface is running on, for composables that need it directly. */
val LocalPlatform = androidx.compose.runtime.staticCompositionLocalOf<Platform> {
    error("LocalPlatform is provided by QuarkRoot")
}
