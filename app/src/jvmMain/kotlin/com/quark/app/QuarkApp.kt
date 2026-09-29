package com.quark.app

import com.quark.app.yandex.YandexSession
import com.quark.app.player.PlaybackSession
import com.quark.app.player.TrackCacheCoordinator
import com.quark.app.player.LibraryWatcher
import com.quark.app.stats.ListenLogger
import com.quark.core.settings.SettingsStore
import com.quark.data.db.DatabaseFactory
import com.quark.data.db.QuarkDatabase
import com.quark.data.images.CoverCache
import com.quark.data.local.LibraryScanner
import com.quark.data.local.DirectoryObserver
import com.quark.data.net.TrackCacher
import com.quark.data.repository.ListenStatsRepository
import com.quark.data.repository.CoverColorRepository
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
    val coverColors: CoverColorRepository,
    val yandex: YandexSession,
    private val engine: AudioEngine,
    val controller: PlayerController,
    val playback: PlaybackSession,
    private val listenLogger: ListenLogger,
    private val trackCache: TrackCacheCoordinator,
    private val libraryWatcher: LibraryWatcher,
) {
    fun shutdown() {
        runBlocking {
            libraryWatcher.close()
            playback.close()
            listenLogger.close()
            trackCache.close()
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
            val resolver = QuarkSourceResolver(yandex)
            val controller = PlayerController(engine, resolver, scope)
            val tracks = TrackRepository(database, Dispatchers.IO)
            val playlists = PlaylistRepository(database, Dispatchers.IO)
            val listenStats = ListenStatsRepository(database, Dispatchers.IO)
            val coverColors = CoverColorRepository(database, Dispatchers.IO)
            val playback = PlaybackSession(controller, settings, playlists, scope)
            val listenLogger = ListenLogger(controller, settings, listenStats, scope)
            val trackCache = TrackCacheCoordinator(
                controller = controller,
                settings = settings,
                cacher = TrackCacher(http),
                resolver = resolver,
                scope = scope,
            )
            val scanner = LibraryScanner()
            val libraryWatcher = LibraryWatcher(
                controller = controller,
                settings = settings,
                scanner = scanner,
                tracks = tracks,
                playback = playback,
                observer = DirectoryObserver(),
                scope = scope,
            )

            QuarkApp(
                scope = scope,
                settings = settings,
                database = database,
                tracks = tracks,
                playlists = playlists,
                listenStats = listenStats,
                scanner = scanner,
                covers = CoverCache(AppDirs.coverCache, http),
                coverColors = coverColors,
                yandex = yandex,
                engine = engine,
                controller = controller,
                playback = playback,
                listenLogger = listenLogger,
                trackCache = trackCache,
                libraryWatcher = libraryWatcher,
            ).also {
                listenLogger.start()
                playback.start()
                trackCache.start()
                libraryWatcher.start()
            }
        }
    }
}
