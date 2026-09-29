package com.quark.app.desktop

import com.quark.app.AppService
import com.quark.core.player.PlayerState
import com.quark.core.player.RepeatMode
import com.quark.core.player.ShuffleMode
import com.quark.core.settings.SettingsStore
import com.quark.network.localapi.LocalApiBackend
import com.quark.network.localapi.LocalApiServer
import com.quark.network.localapi.ServiceBroadcast
import com.quark.platform.AppDirs
import com.quark.platform.Os
import com.quark.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.nio.file.Path
import java.nio.file.Paths
import kotlin.time.Duration.Companion.seconds

/**
 * Runs the local control api while the setting is on. The port goes to the
 * cache folder and to the place the v0 documentation names
 * (`~/.cache/com.quark.quark/api.port`, `%LOCALAPPDATA%\com.quark.quark\api.port`),
 * which is where existing clients look.
 */
class LocalApiService(
    private val controller: PlayerController,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) : AppService {

    private var job: Job? = null
    private var server: LocalApiServer? = null
    private var broadcast: ServiceBroadcast? = null

    override fun start() {
        if (job != null) return
        job = scope.launch {
            settings.settings
                .map { it.integrations.localApi to it.integrations.localApiLan }
                .distinctUntilChanged()
                .collectLatest { (enabled, lan) ->
                    shutdown()
                    if (!enabled) return@collectLatest
                    runCatching {
                        val api = LocalApiServer(Backend(controller), portFiles(), lan)
                        val port = api.start()
                        server = api
                        if (lan) broadcast = ServiceBroadcast(port).also { it.start() }
                    }
                }
        }
    }

    override suspend fun close() {
        job?.cancelAndJoin()
        job = null
        shutdown()
    }

    private suspend fun shutdown() {
        broadcast?.stop()
        broadcast = null
        server?.let { runCatching { it.stop() } }
        server = null
    }

    private fun portFiles(): List<Path> {
        val home = Paths.get(System.getProperty("user.home"))
        val documented = when (AppDirs.os) {
            Os.Windows -> System.getenv("LOCALAPPDATA")?.let { Paths.get(it, "com.quark.quark", "api.port") }
            Os.MacOs -> home.resolve("Library/Caches/com.quark.quark/api.port")
            Os.Linux -> (System.getenv("XDG_CACHE_HOME")?.let(Paths::get) ?: home.resolve(".cache"))
                .resolve("com.quark.quark").resolve("api.port")
        }
        return listOfNotNull(AppDirs.portFile, documented).distinct()
    }

    private class Backend(private val controller: PlayerController) : LocalApiBackend {
        override val state: StateFlow<PlayerState> = controller.state
        override suspend fun pause() { if (controller.state.value.isPlaying) controller.playPause() }
        override suspend fun resume() { if (!controller.state.value.isPlaying) controller.playPause() }
        override suspend fun next() = controller.next()
        override suspend fun previous() = controller.previous()
        override suspend fun setVolume(volume: Float) = controller.setVolume(volume)
        override fun setRepeat(enabled: Boolean) = controller.setRepeat(if (enabled) RepeatMode.One else RepeatMode.Off)
        override fun setShuffle(enabled: Boolean) {
            // The Dart server shuffled with "now on top" when asked over the api.
            if (enabled) controller.shuffle(ShuffleMode.NowOnTop) else controller.unshuffle()
        }
        override suspend fun seek(seconds: Long) = controller.seek(seconds.seconds)
        override suspend fun playIndex(index: Int) {
            controller.state.value.playlist.getOrNull(index)?.let { controller.play(it) }
        }
    }
}
