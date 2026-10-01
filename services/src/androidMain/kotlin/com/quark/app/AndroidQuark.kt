package com.quark.app

import android.content.Context
import com.quark.data.db.AndroidDatabaseFactory
import com.quark.data.images.CoverCache
import com.quark.data.local.AndroidLibrary
import com.quark.data.net.TrackCacher
import com.quark.data.settings.JsonSettingsStore
import com.quark.network.defaultHttpClient
import com.quark.platform.AndroidDirs
import com.quark.platform.DeviceKind
import com.quark.player.Media3AudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import java.io.File

/** The running application and the ExoPlayer the playback service wraps. */
class AndroidQuark(val app: QuarkApp, val engine: Media3AudioEngine)

/**
 * Builds the application on Android. Must run on the main thread: ExoPlayer is
 * bound to the looper it is created on.
 */
fun startAndroidQuark(context: Context): AndroidQuark {
    val appContext = context.applicationContext
    val paths = AndroidDirs.paths(appContext)
    val io = Dispatchers.IO
    val settings = JsonSettingsStore(
        File(paths.settingsFile).toPath(),
        CoroutineScope(SupervisorJob() + io),
    )
    val http = defaultHttpClient()
    val engine = Media3AudioEngine(appContext)
    val host = QuarkHost(
        kind = DeviceKind.Android,
        paths = paths,
        settings = settings,
        database = AndroidDatabaseFactory.open(appContext),
        http = http,
        library = AndroidLibrary(appContext),
        images = CoverCache(File(paths.coverCache).toPath(), http),
        engine = engine,
        downloader = TrackCacher(http),
        io = io,
        main = Dispatchers.Main,
    )
    val app = QuarkApp(host)
    app.start()
    return AndroidQuark(app, engine)
}
