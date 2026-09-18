package com.quark.app.player

import com.quark.app.QuarkApp
import com.quark.app.image.Cover
import com.quark.app.image.CoverLoader
import com.quark.app.theme.AccentColors
import com.quark.core.model.Playlist
import com.quark.core.model.Track
import com.quark.core.player.PlayerState
import com.quark.core.player.RepeatMode
import com.quark.core.player.ShuffleMode
import com.quark.data.local.ScanResult
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File
import kotlin.time.Duration

/** What the library is doing, so the UI can say so instead of appearing stuck. */
sealed interface LibraryStatus {
    data object Idle : LibraryStatus
    data class Scanning(val found: Int, val read: Int, val current: String?) : LibraryStatus
    data class Failed(val message: String) : LibraryStatus
}

/**
 * The single state holder behind every player layout.
 *
 * The Dart build repeated this logic in four widgets — subscriptions, seek
 * maths, time formatting and database calls copied between `main_player`,
 * `mini_player`, `macro_player` and `android_player`. Here the layouts are
 * stateless and read [state].
 */
class PlayerViewModel(private val app: QuarkApp) {

    private val scope = app.scope
    private val coverLoader = CoverLoader()

    val state: StateFlow<PlayerState> = app.controller.state

    private val _cover = MutableStateFlow<Cover?>(null)
    val cover: StateFlow<Cover?> = _cover.asStateFlow()

    val accent: StateFlow<AccentColors> = MutableStateFlow(AccentColors()).also { flow ->
        _cover.map { it?.accent ?: AccentColors() }
            .distinctUntilChanged()
            .onEach(flow::value::set)
            .launchIn(scope)
    }.asStateFlow()

    private val _status = MutableStateFlow<LibraryStatus>(LibraryStatus.Idle)
    val status: StateFlow<LibraryStatus> = _status.asStateFlow()

    /** Position while the user is dragging, which must not fight the engine's updates. */
    private val _scrubbing = MutableStateFlow<Duration?>(null)
    val scrubbing: StateFlow<Duration?> = _scrubbing.asStateFlow()

    private var scanJob: Job? = null

    init {
        // The cover follows whatever is playing, whoever changed it.
        state.map { it.current }
            .distinctUntilChanged()
            .onEach { track -> _cover.value = coverLoader.load(track) }
            .launchIn(scope)

        // Remember volume across restarts, as the Dart build did.
        scope.launch {
            app.controller.setVolume(app.settings.current.playback.volume)
        }
    }

    fun open(paths: List<File>, recursive: Boolean = true) {
        scanJob?.cancel()
        scanJob = scope.launch {
            app.scanner.scanWithProgress(paths, recursive).collect { result ->
                when (result) {
                    is ScanResult.Progress -> _status.value = LibraryStatus.Scanning(
                        found = result.progress.found,
                        read = result.progress.read,
                        current = result.progress.current,
                    )

                    is ScanResult.Done -> {
                        _status.value = LibraryStatus.Idle
                        if (result.tracks.isEmpty()) return@collect
                        app.tracks.remember(result.tracks)
                        app.controller.load(
                            Playlist(name = paths.firstOrNull()?.name ?: "Library", tracks = result.tracks),
                        )
                    }
                }
            }
        }
    }

    fun play(track: Track) = scope.launch { app.controller.play(track) }

    fun playPause() = scope.launch { app.controller.playPause() }

    fun next() = scope.launch { app.controller.next() }

    fun previous() = scope.launch { app.controller.previous() }

    /** Called continuously while the handle is held; does not touch the engine. */
    fun scrub(to: Duration) {
        _scrubbing.value = to
    }

    /** Called when the handle is released; this is the one that seeks. */
    fun commitScrub() {
        val target = _scrubbing.value ?: return
        _scrubbing.value = null
        scope.launch { app.controller.seek(target) }
    }

    fun setVolume(volume: Float) = scope.launch {
        app.controller.setVolume(volume)
        app.settings.update { it.copy(playback = it.playback.copy(volume = volume)) }
    }

    fun toggleShuffle() {
        if (state.value.isShuffled) app.controller.unshuffle()
        else app.controller.shuffle(ShuffleMode.AfterCurrent)
    }

    fun toggleRepeat() {
        val next = if (state.value.repeat == RepeatMode.One) RepeatMode.Off else RepeatMode.One
        app.controller.setRepeat(next)
    }

    fun enqueue(track: Track) = app.controller.enqueueLast(track)

    fun enqueueNext(track: Track) = app.controller.enqueueNext(track)

    fun removeFromQueue(track: Track) = app.controller.removeFromQueue(track)

    fun clearQueue() = app.controller.clearQueue()
}
