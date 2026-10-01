package com.quark.player

import kotlinx.coroutines.flow.SharedFlow
import kotlin.time.Duration

/**
 * The low-level playback contract, replacing `_PlayerEngine` from
 * `lib/services/player/player.dart`.
 *
 * The Dart version fans out to three Flutter packages behind this same shape.
 * On the JVM there is one implementation per native backend instead, and
 * [preload] is what makes gapless work: the engine is told what comes next
 * while the current track is still playing.
 */
interface AudioEngine {

    /**
     * Hot, and without replay: an event describes a moment, not a state, so a
     * late subscriber must not be told that a track finished an hour ago. Use
     * `onSubscription` when it matters that nothing is missed.
     */
    val events: SharedFlow<EngineEvent>

    suspend fun play(source: MediaSource)

    /** Queues [source] to start the instant the current track ends. */
    suspend fun preload(source: MediaSource)

    /** Opens [source] without starting it, so the first frame is ready. */
    suspend fun prepare(source: MediaSource)

    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    suspend fun seek(to: Duration)
    suspend fun setVolume(volume: Float)

    /** Changes tempo without shifting pitch, where the backend supports it. */
    suspend fun setSpeed(speed: Float)

    suspend fun release()
}

/**
 * What the operating system's media controls show while a source plays: the
 * Android notification and lock screen, SMTC on Windows. Engines that have no
 * such surface ignore it.
 */
data class MediaInfo(
    val title: String,
    val artist: String,
    val album: String,
    /** An http(s) or content url the platform can load artwork from itself. */
    val artworkUrl: String? = null,
    val durationMs: Long = 0,
)

sealed interface MediaSource {
    val info: MediaInfo?

    /** A path on disk, or on Android a `content://` uri. */
    data class LocalFile(val path: String, override val info: MediaInfo? = null) : MediaSource

    data class Network(
        val url: String,
        val headers: Map<String, String> = emptyMap(),
        override val info: MediaInfo? = null,
    ) : MediaSource
}

sealed interface EngineEvent {
    data class Position(val position: Duration) : EngineEvent
    data class TotalDuration(val duration: Duration) : EngineEvent
    data class PlayingChanged(val isPlaying: Boolean) : EngineEvent

    /** The current source ran to its end and the preloaded one took over. */
    data object Completed : EngineEvent

    /**
     * The current source ran to its end with nothing preloaded behind it —
     * the queue had nothing, or the next track could not be resolved in time.
     * Unlike [Completed], nothing is playing now.
     */
    data object Ended : EngineEvent

    data class Failed(val source: MediaSource, val cause: Throwable) : EngineEvent
}

/** Turns a track into something the engine can open. */
fun interface SourceResolver {
    suspend fun resolve(track: com.quark.core.model.Track): MediaSource
}
