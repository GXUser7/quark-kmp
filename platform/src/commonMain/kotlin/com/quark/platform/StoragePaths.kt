package com.quark.platform

/**
 * Where the application keeps things, as plain strings so shared code can pass
 * them around. Built by [AppDirs] on the desktop and from the `Context` on
 * Android.
 */
data class StoragePaths(
    /** Settings and the library database: survives a cache wipe. */
    val support: String,
    /** Covers, downloaded tracks: disposable. */
    val cache: String,
    /** What joins path segments on this system. */
    val separator: String,
    /** Where exported tracks go by default: the user's music folder. */
    val exports: String,
) {
    val settingsFile: String get() = join(support, "settings.json")
    val database: String get() = join(support, "quark.db")
    val coverCache: String get() = join(cache, "cached_images")
    val audioCache: String get() = join(cache, "audio_cache")

    fun join(vararg parts: String): String = parts.joinToString(separator)
}

/** Which kind of device the shared code is running on, for the few places that differ. */
enum class DeviceKind { Desktop, Android }
