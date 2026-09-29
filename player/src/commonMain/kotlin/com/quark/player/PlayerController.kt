package com.quark.player

import com.quark.core.model.Playlist
import com.quark.core.model.PlaylistInfo
import com.quark.core.model.Track
import com.quark.core.player.ChangeReason
import com.quark.core.player.PlaybackQueue
import com.quark.core.player.PlayerState
import com.quark.core.player.RepeatMode
import com.quark.core.player.ShuffleMode
import com.quark.core.player.Shuffles
import com.quark.core.player.TrackChange
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.time.Duration

/**
 * Playback state and the commands that change it — the half of the Dart
 * `Player` god-object that is not the engine itself.
 *
 * Everything the UI reads comes from one [state] flow rather than a dozen
 * notifiers, so a track change repaints once. Ordering lives in
 * [PlaybackQueue]; this class owns the engine, the preloading and the
 * bookkeeping around them.
 *
 * Preloading is what makes gapless work, and it is why finishing a track is not
 * the same as starting one: by the time the engine reports
 * [EngineEvent.Completed], the next track is already sounding, so the
 * controller only catches its state up.
 */
class PlayerController(
    private val engine: AudioEngine,
    private val resolver: SourceResolver,
    private val scope: CoroutineScope,
) {
    private val queue = PlaybackQueue()
    private var unshuffled: List<Track> = emptyList()

    private val _state = MutableStateFlow(PlayerState())
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val _trackChanges = MutableSharedFlow<TrackChange>(extraBufferCapacity = 8)

    /** Track changes with their cause, for the scrobbler and the OS controls. */
    val trackChanges: SharedFlow<TrackChange> = _trackChanges.asSharedFlow()

    /**
     * Completes once the engine's events are actually being collected. Starting
     * playback before that would drop the first state change, and the transport
     * button would show the wrong thing until the next one came along.
     */
    private val listening = CompletableDeferred<Unit>()

    init {
        scope.launch {
            engine.events.onSubscription { listening.complete(Unit) }.collect { event ->
                when (event) {
                    is EngineEvent.Position -> _state.update { it.copy(position = event.position) }
                    is EngineEvent.TotalDuration -> _state.update { it.copy(duration = event.duration) }
                    is EngineEvent.PlayingChanged -> _state.update { it.copy(isPlaying = event.isPlaying) }
                    EngineEvent.Completed -> onEngineAdvanced()
                    // Nothing was waiting in the engine, so the next track has
                    // to be opened rather than merely caught up with.
                    EngineEvent.Ended -> skipForward(ChangeReason.Completed)
                    // A track that will not open is not worth stalling on.
                    is EngineEvent.Failed -> skipForward(ChangeReason.Completed)
                }
            }
        }
    }

    suspend fun load(playlist: Playlist, startAt: Track? = playlist.tracks.firstOrNull()) {
        queue.setPlaylist(playlist.tracks)
        queue.clearQueue()
        unshuffled = playlist.tracks
        _state.update {
            it.copy(
                playlist = playlist.tracks,
                playlistInfo = PlaylistInfo.of(playlist),
                queue = emptyList(),
                isShuffled = false,
            )
        }
        startAt?.let { play(it) }
    }

    /**
     * Replaces a live playlist after its directory changed without reopening
     * the audio that is already playing or losing the user's temporary queue.
     */
    fun updatePlaylist(playlist: Playlist) {
        val old = _state.value
        val replacementCurrent = playlist.tracks.firstOrNull { it.filepath == old.current.filepath }
        val current = replacementCurrent ?: old.current
        unshuffled = playlist.tracks

        val ordered = if (old.isShuffled) {
            val byPath = playlist.tracks.associateBy(Track::filepath)
            val retained = old.playlist.mapNotNull { byPath[it.filepath] }
            val retainedPaths = retained.mapTo(mutableSetOf(), Track::filepath)
            retained + playlist.tracks.filterNot { it.filepath in retainedPaths }
        } else {
            playlist.tracks
        }

        queue.setPlaylist(ordered)
        queue.setCurrent(current)
        _state.update {
            it.copy(
                current = current,
                playlist = ordered,
                playlistInfo = PlaylistInfo.of(playlist),
            )
        }
        repreload()
    }

    suspend fun play(track: Track) {
        queue.playNow(track)
        start(track, ChangeReason.External)
    }

    suspend fun next() = skipForward(ChangeReason.External)

    suspend fun previous() {
        val track = queue.previous() ?: return
        start(track, ChangeReason.External)
    }

    private suspend fun skipForward(reason: ChangeReason) {
        val track = queue.next() ?: return
        start(track, reason)
    }

    /** Opens [track] in the engine and takes the state with it. */
    private suspend fun start(track: Track, reason: ChangeReason) {
        listening.await()
        publish(track, reason)
        engine.play(resolver.resolve(track))
        preloadNext()
    }

    /**
     * The engine crossed into the track it had been given ahead of time.
     * Nothing to open — only the state and the next preload are behind.
     */
    private suspend fun onEngineAdvanced() {
        val track = queue.next() ?: return
        publish(track, ChangeReason.Completed)
        preloadNext()
    }

    private suspend fun publish(track: Track, reason: ChangeReason) {
        _state.update {
            it.copy(
                current = track,
                lastChangeReason = reason,
                position = Duration.ZERO,
                duration = Duration.ZERO,
                queue = queue.queue,
            )
        }
        _trackChanges.emit(TrackChange(track, reason))
    }

    /**
     * Hands the engine whatever comes next. Called after every change to what
     * that would be, because the engine holds a copy and would otherwise cross
     * into a track the queue no longer points at.
     */
    private suspend fun preloadNext() {
        val upcoming = queue.peekNext() ?: return
        runCatching { engine.preload(resolver.resolve(upcoming)) }
    }

    private fun repreload() {
        scope.launch { preloadNext() }
    }

    suspend fun playPause() {
        if (_state.value.isPlaying) engine.pause() else engine.resume()
    }

    suspend fun seek(to: Duration) {
        engine.seek(to)
        _state.update { it.copy(position = to) }
    }

    suspend fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        engine.setVolume(clamped)
        _state.update { it.copy(volume = clamped) }
    }

    suspend fun setSpeed(speed: Float) {
        engine.setSpeed(speed)
        _state.update { it.copy(speed = speed) }
    }

    fun setRepeat(mode: RepeatMode) {
        queue.repeat = mode
        _state.update { it.copy(repeat = mode) }
        repreload()
    }

    fun enqueueNext(track: Track) {
        queue.enqueueNext(track)
        publishQueue()
    }

    fun enqueueLast(track: Track) {
        queue.enqueueLast(track)
        publishQueue()
    }

    fun enqueueNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        queue.enqueueNext(tracks)
        publishQueue()
    }

    fun enqueueLast(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        queue.enqueueLast(tracks)
        publishQueue()
    }

    fun moveInQueue(from: Int, to: Int) {
        queue.moveInQueue(from, to)
        publishQueue()
    }

    /** Reorders the playlist itself; the current track keeps playing. */
    fun moveInPlaylist(from: Int, to: Int) {
        val list = queue.playlist
        if (from !in list.indices || to !in list.indices || from == to) return
        val reordered = list.toMutableList().apply { add(to, removeAt(from)) }
        queue.setPlaylist(reordered)
        if (!_state.value.isShuffled) unshuffled = reordered
        _state.update { it.copy(playlist = reordered) }
        repreload()
    }

    /** Takes [track] out of the playlist; if it is playing, it plays on to its end. */
    fun removeFromPlaylist(track: Track) {
        val reordered = queue.playlist.filterNot { it == track }
        queue.setPlaylist(reordered)
        unshuffled = unshuffled.filterNot { it == track }
        _state.update { it.copy(playlist = reordered) }
        repreload()
    }

    /** Adds [tracks] to the playlist, after the current track or at the end. */
    fun addToPlaylist(tracks: List<Track>, afterCurrent: Boolean = false) {
        if (tracks.isEmpty()) return
        val list = queue.playlist
        val at = if (afterCurrent) (list.indexOf(_state.value.current) + 1).coerceIn(0, list.size) else list.size
        val extended = list.toMutableList().apply { addAll(at, tracks) }
        queue.setPlaylist(extended)
        unshuffled = if (_state.value.isShuffled) unshuffled + tracks else extended
        _state.update { it.copy(playlist = extended) }
        repreload()
    }

    suspend fun stop() {
        engine.stop()
        queue.clearQueue()
        _state.update { PlayerState(volume = it.volume, speed = it.speed) }
    }

    fun removeFromQueue(track: Track) {
        queue.removeFromQueue(track)
        publishQueue()
    }

    fun clearQueue() {
        queue.clearQueue()
        publishQueue()
    }

    private fun publishQueue() {
        _state.update { it.copy(queue = queue.queue) }
        repreload()
    }

    fun shuffle(mode: ShuffleMode = ShuffleMode.AfterCurrent) {
        val current = _state.value.current
        if (!_state.value.isShuffled) unshuffled = queue.playlist
        val reordered = Shuffles.apply(mode, queue.playlist, current)
        queue.setPlaylist(reordered)
        _state.update { it.copy(playlist = reordered, isShuffled = true) }
        repreload()
    }

    fun unshuffle() {
        queue.setPlaylist(unshuffled)
        queue.clearQueue()
        _state.update { it.copy(playlist = unshuffled, queue = emptyList(), isShuffled = false) }
        repreload()
    }

    suspend fun release() = engine.release()
}
