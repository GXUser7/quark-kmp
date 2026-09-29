package com.quark.app

import com.quark.app.player.PlaybackSession
import com.quark.app.player.TrackCacheCoordinator
import com.quark.app.stats.ListenLogger
import com.quark.app.yandex.YandexSession
import com.quark.core.settings.SettingsStore
import com.quark.data.db.QuarkDatabase
import com.quark.data.images.ImageStore
import com.quark.data.local.LocalLibrary
import com.quark.data.net.TrackDownloader
import com.quark.data.repository.CoverColorRepository
import com.quark.data.repository.ListenStatsRepository
import com.quark.data.repository.PlaylistRepository
import com.quark.data.repository.TrackRepository
import com.quark.platform.DeviceKind
import com.quark.platform.StoragePaths
import com.quark.player.AudioEngine
import com.quark.player.PlayerController
import io.ktor.client.HttpClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow

/**
 * The pieces only the platform can make: files, the database driver, the audio
 * backend. `DesktopQuark` and `AndroidQuark` build one of these and hand it to
 * [QuarkApp], which wires everything above them the same way on both.
 */
class QuarkHost(
    val kind: DeviceKind,
    val paths: StoragePaths,
    val settings: SettingsStore,
    val database: QuarkDatabase,
    val http: HttpClient,
    val library: LocalLibrary,
    val images: ImageStore,
    val engine: AudioEngine,
    val downloader: TrackDownloader,
    val io: CoroutineDispatcher,
    val main: CoroutineDispatcher,
)

/** Something that runs alongside the player for as long as the app does. */
interface AppService {
    fun start()
    suspend fun close()
}

/**
 * Everything the application owns, built once at startup.
 *
 * Explicit wiring rather than a DI container: the graph is a dozen objects deep
 * and reading this constructor tells you the whole of it, which is the opposite
 * of the twelve hidden singletons the Dart build reached for from anywhere.
 */
class QuarkApp(host: QuarkHost) {

    val kind: DeviceKind = host.kind
    val paths: StoragePaths = host.paths
    val io: CoroutineDispatcher = host.io
    val main: CoroutineDispatcher = host.main
    val scope: CoroutineScope = CoroutineScope(SupervisorJob() + host.io)

    val settings: SettingsStore = host.settings
    val database: QuarkDatabase = host.database
    val http: HttpClient = host.http
    val library: LocalLibrary = host.library
    val images: ImageStore = host.images
    val downloader: TrackDownloader = host.downloader

    val tracks = TrackRepository(database, io)
    val playlists = PlaylistRepository(database, io)
    val listenStats = ListenStatsRepository(database, io)
    val coverColors = CoverColorRepository(database, io)

    val yandex = YandexSession(settings, scope, http)
    val resolver = QuarkSourceResolver(yandex)

    private val engine: AudioEngine = host.engine
    val controller = PlayerController(engine, resolver, scope)
    val playback = PlaybackSession(controller, settings, playlists, scope)

    private val services = mutableListOf<AppService>(
        ListenLogger(controller, settings, listenStats, scope),
        TrackCacheCoordinator(controller, settings, downloader, resolver, scope),
    )

    private val _openRequests = Channel<List<String>>(Channel.BUFFERED)

    /**
     * Files the system asked the app to open: "Open with" on Android, command
     * line arguments and drag-and-drop on the desktop. A channel rather than a
     * replaying flow: a request made before the interface is up waits for it,
     * and one already handled is not handed to the next screen that subscribes.
     */
    val openRequests: Flow<List<String>> = _openRequests.receiveAsFlow()

    fun requestOpen(locations: List<String>) {
        if (locations.isNotEmpty()) _openRequests.trySend(locations)
    }

    private val retained = mutableMapOf<kotlin.reflect.KClass<*>, Any>()

    /**
     * One instance of [T] for the life of the app. View models go here so an
     * Android activity that is torn down and rebuilt finds the ones it had,
     * instead of stacking a second set of collectors on the app scope.
     * Called from composition only, so on one thread.
     */
    @Suppress("UNCHECKED_CAST")
    inline fun <reified T : Any> retain(noinline create: () -> T): T = retainAs(T::class, create)

    @Suppress("UNCHECKED_CAST")
    fun <T : Any> retainAs(type: kotlin.reflect.KClass<T>, create: () -> T): T =
        retained.getOrPut(type) { create() } as T

    private var started = false

    /** Adds a platform service; started now if the app already is. */
    fun attach(service: AppService) {
        services += service
        if (started) service.start()
    }

    fun start() {
        if (started) return
        started = true
        playback.start()
        services.forEach(AppService::start)
    }

    suspend fun shutdown() {
        services.asReversed().forEach { runCatching { it.close() } }
        runCatching { playback.close() }
        runCatching { controller.release() }
        runCatching { settings.close() }
        scope.cancel()
    }
}
