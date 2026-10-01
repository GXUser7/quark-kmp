package com.quark.app.player

import com.quark.app.AppService
import com.quark.core.model.Playlist
import com.quark.core.model.PlaylistSource
import com.quark.core.settings.SettingsStore
import com.quark.data.local.DirectoryObserver
import com.quark.data.local.LibraryScanner
import com.quark.data.repository.TrackRepository
import com.quark.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Path

/** Keeps the currently open local folder playlist in sync with the filesystem. */
class LibraryWatcher(
    private val controller: PlayerController,
    private val settings: SettingsStore,
    private val scanner: LibraryScanner,
    private val tracks: TrackRepository,
    private val playback: PlaybackSession,
    private val observer: DirectoryObserver,
    private val scope: CoroutineScope,
) : AppService {
    private var job: Job? = null

    @OptIn(FlowPreview::class)
    override fun start() {
        if (job != null) return
        job = scope.launch {
            settings.settings
                .map { value ->
                    WatchConfig(
                        enabled = value.library.watchFolders,
                        roots = value.library.watchedFolderPaths.map(Path::of),
                        recursive = value.library.recursiveFolderAdding,
                    )
                }
                .distinctUntilChanged()
                .collectLatest { config ->
                    if (!config.enabled || config.roots.isEmpty()) return@collectLatest
                    observer.changes(config.roots, config.recursive)
                        // Copying an album can produce hundreds of events. One
                        // scan after the burst is both faster and deterministic.
                        .debounce(REFRESH_DELAY_MS)
                        .collect {
                            try {
                                refresh(config)
                            } catch (error: CancellationException) {
                                throw error
                            } catch (_: Exception) {
                                // A directory can disappear between the event
                                // and scan. The next event retries naturally.
                            }
                        }
                }
        }
    }

    override suspend fun close() {
        job?.cancelAndJoin()
        job = null
    }

    private suspend fun refresh(config: WatchConfig) {
        if (controller.state.value.playlistInfo.id.source != PlaylistSource.Local) return
        val roots = config.roots.map(Path::toFile)
        val refreshed = scanner.scan(roots, config.recursive)
        tracks.remember(refreshed)
        playback.refreshLocal(
            Playlist(
                name = roots.firstOrNull()?.name?.ifBlank { "Library" } ?: "Library",
                tracks = refreshed,
            )
        )
    }

    private data class WatchConfig(
        val enabled: Boolean,
        val roots: List<Path>,
        val recursive: Boolean,
    )

    private companion object {
        const val REFRESH_DELAY_MS = 500L
    }
}
