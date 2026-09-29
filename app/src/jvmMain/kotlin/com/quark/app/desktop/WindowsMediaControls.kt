package com.quark.app.desktop

import com.quark.app.AppService
import com.quark.core.model.CoverType
import com.quark.core.model.Track
import com.quark.core.settings.SettingsStore
import com.quark.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors

/**
 * Keeps Windows' media overlay in step with the player and sends its buttons
 * — and the keyboard's media keys, which go through it — back, for as long as
 * the "System media controls" setting is on.
 *
 * WinRT wants its calls from a thread in the multithreaded apartment, so they
 * all go through one thread of their own. Anything that fails there turns the
 * integration off for the session rather than troubling the player.
 */
class WindowsMediaControls(
    private val controller: PlayerController,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
    /** Embedded covers of local files, which SMTC is given as a file. */
    private val artwork: suspend (Track) -> ByteArray?,
    /** Where those covers are written; two files taken in turn, as SMTC keeps the last one open. */
    private val coverFolder: File,
    private val windowHandle: () -> Long,
) : AppService {

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "quark-smtc").apply { isDaemon = true }
    }
    private val thread = executor.asCoroutineDispatcher()
    private var smtc: Smtc? = null
    private var broken = false
    private var job: Job? = null

    override fun start() {
        if (!isWindows) return
        job = scope.launch {
            settings.settings.map { it.integrations.nativeControls }.distinctUntilChanged().collect { enabled ->
                if (enabled) open() else shut()
            }
        }
        scope.launch {
            combine(
                controller.state.map { it.current to it.hasTrack }.distinctUntilChanged(),
                controller.state.map { it.isPlaying }.distinctUntilChanged(),
            ) { (track, hasTrack), playing -> Triple(track, hasTrack, playing) }
                .collect { (track, hasTrack, playing) ->
                    val cover = if (hasTrack) coverFile(track) else null
                    onThread { controls ->
                        controls.setPlaying(playing, closed = !hasTrack)
                        if (hasTrack) {
                            controls.setTrack(
                                title = track.title,
                                artist = track.artistLine,
                                album = track.albumLine,
                                thumbnailUrl = track.cover.takeIf { track.coverType == CoverType.Url },
                                thumbnailFile = cover,
                            )
                        }
                    }
                }
        }
    }

    private suspend fun open() {
        if (broken || smtc != null) return
        val handle = windowHandle()
        if (handle == 0L) return
        withContext(thread) {
            try {
                smtc = Smtc.forWindow(handle).also { controls ->
                    controls.setEnabled(true)
                    controls.onButton(::pressed)
                }
            } catch (e: Throwable) {
                broken = true
                System.err.println("quark: system media controls unavailable: ${e.message}")
            }
        }
        val state = controller.state.value
        val cover = if (state.hasTrack) coverFile(state.current) else null
        onThread { controls ->
            controls.setPlaying(state.isPlaying, closed = !state.hasTrack)
            if (state.hasTrack) {
                controls.setTrack(
                    state.current.title,
                    state.current.artistLine,
                    state.current.albumLine,
                    state.current.cover.takeIf { state.current.coverType == CoverType.Url },
                    cover,
                )
            }
        }
    }

    private var coverTurn = 0

    /** The embedded cover of a local track, written out for SMTC; null for streamed ones. */
    private suspend fun coverFile(track: Track): String? {
        if (track.coverType == CoverType.Url || track.coverType == CoverType.NoCover) return null
        val bytes = runCatching { artwork(track) }.getOrNull() ?: return null
        return withContext(Dispatchers.IO) {
            runCatching {
                coverFolder.mkdirs()
                coverTurn = 1 - coverTurn
                val file = File(coverFolder, "smtc-cover-$coverTurn")
                file.writeBytes(bytes)
                file.absolutePath
            }.getOrNull()
        }
    }

    private suspend fun shut() {
        withContext(thread) {
            smtc?.close()
            smtc = null
        }
    }

    private fun pressed(button: Smtc.Button) {
        scope.launch {
            when (button) {
                Smtc.Button.Play, Smtc.Button.Pause -> controller.playPause()
                Smtc.Button.Next -> controller.next()
                Smtc.Button.Previous -> controller.previous()
                Smtc.Button.Stop -> if (controller.state.value.isPlaying) controller.playPause()
                else -> Unit
            }
        }
    }

    /** Runs [block] on the SMTC thread when there is SMTC to call. */
    private suspend fun onThread(block: (Smtc) -> Unit) {
        withContext(thread) {
            val controls = smtc ?: return@withContext
            try {
                block(controls)
            } catch (e: Throwable) {
                System.err.println("quark: system media controls failed: ${e.message}")
            }
        }
    }

    override suspend fun close() {
        job?.cancel()
        runCatching { shut() }
        executor.shutdown()
    }

    companion object {
        val isWindows: Boolean = System.getProperty("os.name").orEmpty().startsWith("Windows", ignoreCase = true)
    }
}
