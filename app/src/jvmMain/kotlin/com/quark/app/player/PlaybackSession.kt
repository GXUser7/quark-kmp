package com.quark.app.player

import com.quark.core.model.LocalTrack
import com.quark.core.model.Playlist
import com.quark.core.model.PlaylistId
import com.quark.core.model.PlaylistSource
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import com.quark.core.model.YtMusicTrack
import com.quark.core.settings.PlaybackMemory
import com.quark.core.settings.SettingsStore
import com.quark.data.repository.PlaylistRepository
import com.quark.player.PlayerController
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.time.Duration.Companion.seconds

/**
 * Persists the active playlist and playback cursor, then restores both on the
 * next start. A SQLite snapshot is kept even for remote playlists so startup
 * restoration does not depend on the network being available.
 */
class PlaybackSession(
    private val controller: PlayerController,
    private val settings: SettingsStore,
    private val playlists: PlaylistRepository,
    private val scope: CoroutineScope,
    private val nowMillis: () -> Long = System::currentTimeMillis,
) {
    private val ready = CompletableDeferred<Unit>()
    private var startup: Job? = null
    private var tracker: Job? = null

    fun start() {
        if (startup != null) return
        startup = scope.launch {
            try {
                controller.setVolume(settings.current.playback.volume)
                restore()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // A stale file or an unavailable remote track must not prevent
                // the player window from opening. The saved snapshot is kept.
            } finally {
                beginTracking()
                ready.complete(Unit)
            }
        }
    }

    /** Saves [playlist] before handing it to the engine. */
    suspend fun open(playlist: Playlist, startAt: Track? = playlist.tracks.firstOrNull()) {
        ready.await()
        if (playlist.tracks.isEmpty()) return

        val memory = settings.current.memory
        val samePlaylist = when (playlist.source) {
            PlaylistSource.Local -> memory.lastPlaylist?.source == PlaylistSource.Local &&
                memory.lastPlaylistName == playlist.name
            else -> memory.lastPlaylist == playlist.id
        }
        val storageId = playlists.saveSnapshot(
            playlist = playlist,
            existingId = memory.lastPlaylistStorageId.takeIf { samePlaylist },
        )
        val storedPlaylist = if (playlist.source == PlaylistSource.Local) {
            playlist.copy(id = PlaylistId(kind = storageId, source = PlaylistSource.Local))
        } else {
            playlist
        }
        val first = startAt?.let { candidate ->
            storedPlaylist.tracks.firstOrNull { it.filepath == candidate.filepath }
        } ?: storedPlaylist.tracks.first()

        remember(storedPlaylist, storageId, first.filepath, positionSeconds = 0)
        controller.load(storedPlaylist, first)
    }

    /** Persists a watcher rescan without interrupting the track in the engine. */
    suspend fun refreshLocal(playlist: Playlist) {
        ready.await()
        if (controller.state.value.playlistInfo.id.source != PlaylistSource.Local) return

        val memory = settings.current.memory
        val storageId = playlists.saveSnapshot(
            playlist = playlist,
            existingId = memory.lastPlaylistStorageId
                .takeIf { memory.lastPlaylist?.source == PlaylistSource.Local },
        )
        val stored = playlist.copy(
            id = PlaylistId(kind = storageId, source = PlaylistSource.Local),
        )
        val rememberedPath = memory.lastTrackPath
            ?.takeIf { path -> stored.tracks.any { it.filepath == path } }
            ?: stored.tracks.firstOrNull()?.filepath
        val keptPosition = rememberedPath != null && rememberedPath == memory.lastTrackPath

        settings.update { current ->
            current.copy(
                memory = current.memory.copy(
                    lastTrackPath = rememberedPath,
                    lastPositionSeconds = current.memory.lastPositionSeconds.takeIf { keptPosition } ?: 0,
                    lastPlaylist = stored.id,
                    lastPlaylistName = stored.name,
                    lastPlaylistStorageId = storageId,
                )
            )
        }
        controller.updatePlaylist(stored)
    }

    internal suspend fun restore() {
        val memory = settings.current.memory
        val storageId = memory.lastPlaylistStorageId ?: return
        val stored = playlists.byId(storageId) ?: return
        val tracks = playlists.tracksOf(storageId)
        if (tracks.isEmpty()) return

        val playlist = Playlist(
            id = memory.lastPlaylist ?: PlaylistId(kind = storageId, source = PlaylistSource.Local),
            name = memory.lastPlaylistName ?: stored.title,
            tracks = tracks,
        )
        val remembered = memory.lastTrackPath?.let { path ->
            tracks.firstOrNull { it.filepath == path }
        }
        val startAt = remembered ?: tracks.first()

        controller.load(playlist, startAt)
        val seconds = memory.lastPositionSeconds
        val duration = startAt.durationSeconds()
        if (remembered != null && seconds > 0 && (duration <= 0 || seconds < duration)) {
            controller.seek(seconds.seconds)
        }
    }

    suspend fun close() {
        if (startup == null) return
        ready.await()
        tracker?.cancelAndJoin()
        saveCurrentPosition()
    }

    private fun beginTracking() {
        if (tracker != null) return
        tracker = scope.launch {
            var trackPath = controller.state.value.current.takeIf { controller.state.value.hasTrack }?.filepath
            var lastCountedSecond = controller.state.value.position.inWholeSeconds
            var playedSinceSave = 0L
            var lastSavedAt = nowMillis()

            controller.state.collect { state ->
                if (!state.hasTrack) return@collect
                if (state.current.filepath != trackPath) {
                    trackPath = state.current.filepath
                    lastCountedSecond = state.position.inWholeSeconds
                    playedSinceSave = 0
                    lastSavedAt = nowMillis()
                    settings.update { current ->
                        current.copy(
                            memory = current.memory.copy(
                                lastTrackPath = state.current.filepath,
                                lastPositionSeconds = 0,
                            )
                        )
                    }
                    return@collect
                }

                val second = state.position.inWholeSeconds
                val difference = second - lastCountedSecond
                if (difference in 1..SEEK_THRESHOLD_SECONDS) playedSinceSave += difference
                if (difference != 0L) lastCountedSecond = second

                val now = nowMillis()
                if (playedSinceSave >= SAVE_EVERY_PLAYED_SECONDS && now - lastSavedAt >= SAVE_THROTTLE_MS) {
                    saveCurrentPosition()
                    playedSinceSave = 0
                    lastSavedAt = now
                }
            }
        }
    }

    private fun saveCurrentPosition() {
        val state = controller.state.value
        if (!state.hasTrack || state.playlist.isEmpty()) return
        settings.update { current ->
            current.copy(
                memory = current.memory.copy(
                    lastTrackPath = state.current.filepath,
                    lastPositionSeconds = state.position.inWholeSeconds.coerceAtLeast(0).toInt(),
                )
            )
        }
    }

    private fun remember(playlist: Playlist, storageId: Long, trackPath: String, positionSeconds: Int) {
        settings.update { current ->
            current.copy(
                memory = PlaybackMemory(
                    lastTrackPath = trackPath,
                    lastPositionSeconds = positionSeconds,
                    lastPlaylist = playlist.id,
                    lastPlaylistName = playlist.name,
                    lastPlaylistStorageId = storageId,
                )
            )
        }
    }

    private fun Track.durationSeconds(): Long = when (this) {
        is LocalTrack -> durationMs / 1_000
        is YandexTrack -> durationMs / 1_000
        is YtMusicTrack -> durationMs / 1_000
    }

    private companion object {
        const val SEEK_THRESHOLD_SECONDS = 2L
        const val SAVE_EVERY_PLAYED_SECONDS = 15L
        const val SAVE_THROTTLE_MS = 2_000L
    }
}
