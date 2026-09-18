package com.quark.app

import com.quark.app.yandex.YandexSession
import com.quark.core.settings.SettingsStore
import com.quark.data.db.DatabaseFactory
import com.quark.data.db.QuarkDatabase
import com.quark.data.images.CoverCache
import com.quark.data.local.LibraryScanner
import com.quark.data.repository.ListenStatsRepository
import com.quark.data.repository.PlaylistRepository
import com.quark.data.repository.TrackRepository
import com.quark.data.settings.JsonSettingsStore
import com.quark.network.yandex.YandexClient
import com.quark.platform.AppDirs
import com.quark.player.AudioEngine
import com.quark.player.MpvAudioEngine
import com.quark.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking

/**
 * Everything the application owns, built once at startup.
 *
 * Explicit wiring rather than a DI container: the graph is a dozen objects deep
 * and reading the constructor tells you the whole of it, which is the opposite
 * of the twelve hidden singletons the Dart build reached for from anywhere.
 */
class QuarkApp private constructor(
    val scope: CoroutineScope,
    val settings: SettingsStore,
    private val database: QuarkDatabase,
    val tracks: TrackRepository,
    val playlists: PlaylistRepository,
    val listenStats: ListenStatsRepository,
    val scanner: LibraryScanner,
    val covers: CoverCache,
    val yandex: YandexSession,
    private val engine: AudioEngine,
    val controller: PlayerController,
) {
    fun shutdown() {
        runBlocking {
            controller.release()
            settings.close()
        }
        scope.cancel()
    }

    companion object {
        /**
         * Builds the graph. A missing libmpv fails here, and the window shows
         * the message rather than a stack trace, because it names what to
         * install.
         */
        fun start(): Result<QuarkApp> = runCatching {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val settings = JsonSettingsStore(AppDirs.support.resolve("settings.json"), scope)
            val database = DatabaseFactory.open(AppDirs.database)
            val http = YandexClient.defaultHttpClient()

            val yandex = YandexSession(settings, scope, http)
            val engine = MpvAudioEngine()
            val controller = PlayerController(engine, QuarkSourceResolver(yandex), scope)

            QuarkApp(
                scope = scope,
                settings = settings,
                database = database,
                tracks = TrackRepository(database, Dispatchers.IO),
                playlists = PlaylistRepository(database, Dispatchers.IO),
                listenStats = ListenStatsRepository(database, Dispatchers.IO),
                scanner = LibraryScanner(),
                covers = CoverCache(AppDirs.coverCache, http),
                yandex = yandex,
                engine = engine,
                controller = controller,
            )
        }
    }
}
