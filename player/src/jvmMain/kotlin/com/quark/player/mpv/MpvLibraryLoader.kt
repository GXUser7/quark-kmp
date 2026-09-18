package com.quark.player.mpv

import com.sun.jna.Native
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Finds libmpv and loads it.
 *
 * Three places, in order: a path the user pinned with `-Dquark.mpv.path`, the
 * `mpv/` folder shipped next to the application, and finally the system loader.
 * The bundled copy comes first so a distribution is self-contained and does not
 * pick up whatever mpv the machine happens to have; on Linux, where mpv is
 * normally a package, the system copy is the one that gets used.
 */
internal object MpvLibraryLoader {

    private const val PROPERTY = "quark.mpv.path"

    val isWindows: Boolean = System.getProperty("os.name").lowercase().contains("win")
    val isMac: Boolean = System.getProperty("os.name").lowercase().let {
        it.contains("mac") || it.contains("darwin")
    }

    /** File names libmpv ships under, most specific first. */
    private val candidates: List<String> = when {
        isWindows -> listOf("libmpv-2.dll", "mpv-2.dll", "mpv-1.dll")
        isMac -> listOf("libmpv.2.dylib", "libmpv.dylib")
        else -> listOf("libmpv.so.2", "libmpv.so.1", "libmpv.so")
    }

    fun load(): LibMpv {
        val failures = mutableListOf<String>()

        for (path in searchPaths()) {
            try {
                return Native.load(path.toAbsolutePath().toString(), LibMpv::class.java)
            } catch (e: UnsatisfiedLinkError) {
                failures += "$path: ${e.message}"
            }
        }

        // Nothing bundled: let the platform loader look on its own paths.
        for (name in listOf("mpv") + candidates) {
            try {
                return Native.load(name, LibMpv::class.java)
            } catch (e: UnsatisfiedLinkError) {
                failures += "$name: ${e.message}"
            }
        }

        throw MpvNotFoundException(buildMessage(failures))
    }

    private fun searchPaths(): List<Path> {
        val pinned = System.getProperty(PROPERTY)?.let(Paths::get)
        if (pinned != null) {
            // A directory means "look inside"; a file means "use exactly this".
            return if (Files.isDirectory(pinned)) candidates.map(pinned::resolve)
            else listOf(pinned)
        }

        val appDir = appDirectory() ?: return emptyList()
        return listOf(appDir, appDir.resolve("mpv"), appDir.resolve("runtime"))
            .filter(Files::isDirectory)
            .flatMap { dir -> candidates.map(dir::resolve) }
            .filter(Files::isRegularFile)
    }

    /** Where the application was installed, as jpackage lays it out. */
    private fun appDirectory(): Path? =
        System.getProperty("compose.application.resources.dir")?.let(Paths::get)
            ?: System.getProperty("user.dir")?.let(Paths::get)

    private fun buildMessage(failures: List<String>) = buildString {
        append("libmpv could not be loaded. ")
        append(
            when {
                isWindows -> "Put libmpv-2.dll next to the application, or point -D$PROPERTY at it."
                isMac -> "Install it with `brew install mpv`, or point -D$PROPERTY at libmpv.dylib."
                else -> "Install the libmpv package (libmpv2 / libmpv.so.2), or point -D$PROPERTY at it."
            }
        )
        if (failures.isNotEmpty()) {
            append("\nTried:\n")
            failures.forEach { append("  ").append(it).append('\n') }
        }
    }
}

class MpvNotFoundException(message: String) : RuntimeException(message)
