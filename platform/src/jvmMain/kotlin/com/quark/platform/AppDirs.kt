package com.quark.platform

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

enum class Os { Windows, Linux, MacOs }

/**
 * Where quark keeps its data, replacing `path_provider` plus
 * `ApplicationDirectories` from `lib/services/files.dart`.
 *
 * The Flutter build derived these paths from platform metadata rather than a
 * constant, so they differ per OS: Windows from the exe's CompanyName and
 * ProductName ("quark" / "quarkaudio"), Linux from APPLICATION_ID
 * ("com.quark.quark"). [legacySupport] and [legacyCache] point at those, so an
 * existing library and settings file can be found and adopted instead of the
 * user starting from an empty database.
 */
object AppDirs {

    val os: Os = System.getProperty("os.name").lowercase().let { name ->
        when {
            name.contains("win") -> Os.Windows
            name.contains("mac") || name.contains("darwin") -> Os.MacOs
            else -> Os.Linux
        }
    }

    private val home: Path = Paths.get(System.getProperty("user.home"))

    private fun env(name: String): Path? =
        System.getenv(name)?.takeIf { it.isNotBlank() }?.let(Paths::get)

    /** Database and settings: survives a cache wipe. */
    val support: Path by lazy {
        when (os) {
            Os.Windows -> (env("APPDATA") ?: home.resolve("AppData/Roaming")).resolve(APP)
            Os.MacOs -> home.resolve("Library/Application Support").resolve(APP)
            Os.Linux -> (env("XDG_DATA_HOME") ?: home.resolve(".local/share")).resolve(APP)
        }.created()
    }

    /** Cover cache, downloaded tracks, the local api port file: disposable. */
    val cache: Path by lazy {
        when (os) {
            Os.Windows -> (env("LOCALAPPDATA") ?: home.resolve("AppData/Local")).resolve(APP)
            Os.MacOs -> home.resolve("Library/Caches").resolve(APP)
            Os.Linux -> (env("XDG_CACHE_HOME") ?: home.resolve(".cache")).resolve(APP)
        }.created()
    }

    val coverCache: Path by lazy { cache.resolve("cached_images").created() }
    val audioCache: Path by lazy { cache.resolve("audio_cache").created() }
    val database: Path by lazy { support.resolve("quark.db") }

    /** The port the local api bound to, as the Dart version published it. */
    val portFile: Path by lazy { cache.resolve("api.port") }

    /** Where the Flutter build kept the same data, if it is still there. */
    val legacySupport: Path? by lazy {
        when (os) {
            Os.Windows -> env("APPDATA")?.resolve("quark/quarkaudio")
            Os.MacOs -> home.resolve("Library/Application Support/com.quark.quark")
            Os.Linux -> (env("XDG_DATA_HOME") ?: home.resolve(".local/share"))
                .resolve("com.quark.quark")
        }?.takeIf { Files.isDirectory(it) }
    }

    val legacyCache: Path? by lazy {
        when (os) {
            Os.Windows -> env("LOCALAPPDATA")?.resolve("quark/quarkaudio")
            Os.MacOs -> home.resolve("Library/Caches/com.quark.quark")
            Os.Linux -> (env("XDG_CACHE_HOME") ?: home.resolve(".cache"))
                .resolve("com.quark.quark")
        }?.takeIf { Files.isDirectory(it) }
    }

    private const val APP = "quark"

    private fun Path.created(): Path = also { Files.createDirectories(it) }
}
