package com.quark.app.yandex

import com.quark.app.player.PlaybackSession
import com.quark.core.model.Playlist
import com.quark.core.model.PlaylistId
import com.quark.core.model.PlaylistSource
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import com.quark.core.player.ChangeReason
import com.quark.network.yandex.WaveFeedback
import com.quark.network.yandex.dto.RotorSequenceItemDto
import com.quark.network.yandex.toTrack
import com.quark.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

/** What the My Vibe controls show. */
sealed interface WaveState {
    data object Off : WaveState
    data object Starting : WaveState
    data class Playing(val station: String) : WaveState
    data class Failed(val message: String) : WaveState
}

/**
 * My Vibe: an endless station from Yandex's rotor, played as a playlist that
 * grows as it is listened to (`yandex_music_my_vibe.dart`,
 * `my_wave_playlist_extension.dart`).
 *
 * The station learns from feedback — a track started, finished or skipped, and
 * for how long it played. Without it the wave keeps serving the same handful of
 * tracks, so every change of track is reported, and more are fetched while two
 * are still left to play.
 */
class WaveSession(
    private val session: YandexSession,
    private val controller: PlayerController,
    private val playback: PlaybackSession,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<WaveState>(WaveState.Off)
    val state: StateFlow<WaveState> = _state.asStateFlow()

    private val lock = Mutex()
    private var sessionId: String? = null
    private var batchId: String? = null
    private var stationName: String = MY_VIBE
    private var tracks: List<Track> = emptyList()
    private var listening: Job? = null
    private var current: YandexTrack? = null
    private var currentPosition: Duration = Duration.ZERO
    private var fetching = false

    /**
     * Starts a station. [seeds] name it — `user:onyourwave` for My Vibe,
     * `track:ID` for a station built around one track, `genre:rock` and so on.
     */
    fun start(seeds: List<String> = listOf("user:onyourwave"), name: String = MY_VIBE) {
        scope.launch {
            val api = session.api ?: run {
                _state.value = WaveState.Failed("Sign in to Yandex Music first")
                return@launch
            }
            _state.value = WaveState.Starting
            try {
                val started = api.startWave(seeds)
                val first = started.sequence.toTracks()
                if (first.isEmpty()) {
                    _state.value = WaveState.Failed("The station returned no tracks")
                    return@launch
                }
                lock.withLock {
                    sessionId = started.sessionId
                    batchId = started.batchId
                    stationName = name
                    tracks = first
                }
                runCatching { api.sendWaveFeedback(started.sessionId, WaveFeedback.RadioStarted(started.batchId)) }
                playback.open(Playlist(id = WAVE_ID, name = name, tracks = first))
                _state.value = WaveState.Playing(name)
                follow()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                _state.value = WaveState.Failed(e.message ?: "Could not start the station")
            }
        }
    }

    fun stop() {
        listening?.cancel()
        listening = null
        sessionId = null
        _state.value = WaveState.Off
    }

    /** Reports each change of track and keeps the queue topped up. */
    private fun follow() {
        listening?.cancel()
        listening = scope.launch {
            launch {
                controller.state.collect { state ->
                    if (state.current == current) currentPosition = state.position
                    // Leaving the wave for another playlist ends the station.
                    if (state.playlistInfo.id != WAVE_ID && state.playlist.isNotEmpty() && sessionId != null) {
                        stop()
                    }
                }
            }
            controller.trackChanges.collect { change ->
                val id = sessionId ?: return@collect
                val api = session.api ?: return@collect
                val previous = current
                val played = currentPosition
                if (previous != null) {
                    val feedback = if (change.reason == ChangeReason.Completed) {
                        WaveFeedback.TrackFinished(
                            batchId, previous.trackId, previous.albumId,
                            playedSeconds = played.inWholeMilliseconds / 1000.0,
                            totalSeconds = previous.durationMs / 1000.0,
                        )
                    } else {
                        WaveFeedback.Skipped(
                            batchId, previous.trackId, previous.albumId,
                            playedSeconds = played.inWholeMilliseconds / 1000.0,
                        )
                    }
                    runCatching { api.sendWaveFeedback(id, feedback) }
                }
                val next = change.track as? YandexTrack
                current = next
                currentPosition = Duration.ZERO
                if (next != null) {
                    runCatching { api.sendWaveFeedback(id, WaveFeedback.TrackStarted(batchId, next.trackId, next.albumId)) }
                }
                topUp()
            }
        }
    }

    private suspend fun topUp() {
        val state = controller.state.value
        if (state.playlistInfo.id != WAVE_ID) return
        val remaining = state.playlist.size - 1 - state.currentIndex
        if (remaining > PREFETCH_WHEN_LEFT || fetching) return
        val id = sessionId ?: return
        val api = session.api ?: return
        fetching = true
        try {
            val heard = state.playlist.filterIsInstance<YandexTrack>().map { track ->
                track.albumId?.let { "${track.trackId}:$it" } ?: track.trackId
            }
            val batch = api.waveTracks(id, heard)
            val more = batch.sequence.toTracks().filter { candidate -> state.playlist.none { it.filepath == candidate.filepath } }
            if (more.isEmpty()) return
            lock.withLock {
                batchId = batch.batchId ?: batchId
                tracks = state.playlist + more
            }
            controller.updatePlaylist(Playlist(id = WAVE_ID, name = stationName, tracks = state.playlist + more))
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            // The next track change tries again.
        } finally {
            fetching = false
        }
    }

    private fun List<RotorSequenceItemDto>.toTracks(): List<Track> =
        mapNotNull { it.track?.toTrack(session.cacheRoot, session.separator) }.filter { it.available }

    companion object {
        const val MY_VIBE = "My Vibe"

        /** Marks the wave's playlist, so other code can tell it from a saved one. */
        val WAVE_ID = PlaylistId(ownerUid = 0, kind = -1, source = PlaylistSource.YandexMusic)

        private const val PREFETCH_WHEN_LEFT = 2
    }
}
