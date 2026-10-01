package com.quark.app

import com.quark.app.desktop.DiscordPresence
import com.quark.app.desktop.LocalApiService
import com.quark.app.export.TagEditor
import com.quark.app.player.LibraryWatcher
import com.quark.data.local.AudioTags
import com.quark.data.local.TagWriter
import java.io.File
import com.quark.data.db.DatabaseFactory
import com.quark.data.images.CoverCache
import com.quark.data.local.DesktopLibrary
import com.quark.data.local.DirectoryObserver
import com.quark.data.local.LibraryScanner
import com.quark.data.net.TrackCacher
import com.quark.data.settings.JsonSettingsStore
import com.quark.network.defaultHttpClient
import com.quark.platform.AppDirs
import com.quark.platform.DeviceKind
import com.quark.player.MpvAudioEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.swing.Swing

/**
 * Builds the application on the desktop JVM: libmpv, a JDBC SQLite file the
 * Flutter build may already have written, and the folder watcher.
 */
object DesktopQuark {

    /**
     * A missing libmpv fails here, and the window shows the message rather than
     * a stack trace, because it names what to install.
     */
    fun start(): Result<QuarkApp> = runCatching {
        val io = Dispatchers.IO
        val settings = JsonSettingsStore(
            AppDirs.support.resolve("settings.json"),
            CoroutineScope(SupervisorJob() + io),
        )
        val http = defaultHttpClient()
        val scanner = LibraryScanner()
        val host = QuarkHost(
            kind = DeviceKind.Desktop,
            paths = AppDirs.paths,
            settings = settings,
            database = DatabaseFactory.open(AppDirs.database),
            http = http,
            library = DesktopLibrary(scanner),
            images = CoverCache(AppDirs.coverCache, http),
            engine = MpvAudioEngine(),
            downloader = TrackCacher(http),
            io = io,
            main = Dispatchers.Swing,
            tagEditor = TagEditor { path, tags ->
                TagWriter.write(
                    File(path),
                    AudioTags(
                        title = tags.title,
                        artist = tags.artist,
                        album = tags.album,
                        trackNumber = tags.trackNumber,
                        trackTotal = tags.trackTotal,
                        coverData = tags.cover,
                    ),
                )
            },
        )

        QuarkApp(host).also { app ->
            app.attach(
                LibraryWatcher(
                    controller = app.controller,
                    settings = settings,
                    scanner = scanner,
                    tracks = app.tracks,
                    playback = app.playback,
                    observer = DirectoryObserver(),
                    scope = app.scope,
                )
            )
            app.attach(DiscordPresence(app.controller, settings, app.scope))
            app.attach(LocalApiService(app.controller, settings, app.scope))
            app.start()
        }
    }
}
