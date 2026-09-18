package com.quark.app

import com.quark.core.model.PlaySourceType
import com.quark.core.model.Track
import com.quark.core.settings.SettingsStore
import com.quark.data.db.DatabaseFactory
import com.quark.data.db.QuarkDatabase
import com.quark.data.local.LibraryScanner
import com.quark.data.repository.ListenStatsRepository
import com.quark.data.repository.PlaylistRepository
import com.quark.data.repository.TrackRepository
import com.quark.data.settings.JsonSettingsStore
import com.quark.platform.AppDirs
import com.quark.player.AudioEngine
import com.quark.player.MediaSource
import com.quark.player.MpvAudioEngine
import com.quark.player.PlayerController
import com.quark.player.SourceResolver
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
         * Builds the graph. Throws only if the database cannot be opened —
         * a missing libmpv is reported as [EngineFailure] instead, because the
         * rest of the application is still usable and the user needs to be told
         * what to install rather than shown a stack trace.
         */
        fun start(): Result<QuarkApp> = runCatching {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            val settings = JsonSettingsStore(AppDirs.support.resolve("settings.json"), scope)
            val database = DatabaseFactory.open(AppDirs.database)

            val tracks = TrackRepository(database, Dispatchers.IO)
            val playlists = PlaylistRepository(database, Dispatchers.IO)
            val listenStats = ListenStatsRepository(database, Dispatchers.IO)

            val engine = MpvAudioEngine()
            val controller = PlayerController(engine, LocalSourceResolver, scope)

            QuarkApp(
                scope = scope,
                settings = settings,
                database = database,
                tracks = tracks,
                playlists = playlists,
                listenStats = listenStats,
                scanner = LibraryScanner(),
                engine = engine,
                controller = controller,
            )
        }
    }
}

/**
 * Local files only, for now. Remote sources resolve through their own api and
 * arrive with the network layer.
 */
private object LocalSourceResolver : SourceResolver {
    override suspend fun resolve(track: Track): MediaSource = when (track.playSource) {
        PlaySourceType.LocalFile -> MediaSource.LocalFile(track.filepath)
        PlaySourceType.Url -> MediaSource.Network(track.filepath)
    }
}

private val Track.playSource: PlaySourceType
    get() = if (filepath.startsWith("http://") || filepath.startsWith("https://")) {
        PlaySourceType.Url
    } else {
        PlaySourceType.LocalFile
    }
