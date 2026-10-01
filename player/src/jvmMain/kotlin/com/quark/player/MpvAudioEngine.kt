package com.quark.player

import com.quark.player.mpv.LibMpv
import com.quark.player.mpv.MpvEndReason
import com.quark.player.mpv.MpvFormat
import com.quark.player.mpv.MpvHandle
import com.quark.player.mpv.MpvLibraryLoader
import com.quark.player.mpv.MpvUpdate
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * [AudioEngine] on libmpv.
 *
 * Gapless works by handing mpv the next file while the current one is still
 * playing: `loadfile … append` puts it in mpv's own playlist, and mpv crosses
 * the boundary without reopening the output. The engine notices the crossing by
 * watching `playlist-pos` climb on its own, and reports it as
 * [EngineEvent.Completed] — by which point the next track is already sounding,
 * so the controller updates its state rather than starting anything.
 */
class MpvAudioEngine internal constructor(
    private val io: CoroutineDispatcher,
    libraryLoader: () -> LibMpv,
) : AudioEngine {

    constructor(io: CoroutineDispatcher = Dispatchers.IO) : this(io, { MpvLibraryLoader.load() })

    private val lib = libraryLoader()
    private val handle = MpvHandle.create(lib)

    private val _events = MutableSharedFlow<EngineEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    /** Last `playlist-pos` seen, to tell a natural advance from one we caused. */
    @Volatile private var playlistPosition = 0L

    @Volatile private var current: MediaSource? = null


    init {
        handle.observe("time-pos", MpvFormat.DOUBLE)
        handle.observe("duration", MpvFormat.DOUBLE)
        handle.observe("pause", MpvFormat.FLAG)
        handle.observe("playlist-pos", MpvFormat.INT64)
        handle.start(::onUpdate)
    }

    private fun onUpdate(update: MpvUpdate) {
        when (update) {
            is MpvUpdate.Property -> onProperty(update.name, update.value)

            is MpvUpdate.FileEnded -> when (update.reason) {
                MpvEndReason.ERROR -> current?.let { source ->
                    emitPlaying(false)
                    emit(
                        EngineEvent.Failed(
                            source,
                            MpvPlaybackException(lib.mpv_error_string(update.error)),
                        )
                    )
                }
                // EOF with nothing queued: playlist-pos will not move, so report
                // the end here instead.
                MpvEndReason.EOF -> if (isLastEntry()) {
                    emitPlaying(false)
                    emit(EngineEvent.Ended)
                }
                else -> Unit
            }

            // Fires when a file starts, and again after every seek. Either way
            // the truth about playback is whatever `pause` says right now.
            MpvUpdate.PlaybackRestarted -> emitPlaying(handle.getBoolean("pause") != true)

            MpvUpdate.Shutdown -> emitPlaying(false)

            else -> Unit
        }
    }

    private fun onProperty(name: String, value: Any?) {
        when (name) {
            "time-pos" -> (value as? Double)?.let { emit(EngineEvent.Position(it.seconds)) }

            "duration" -> (value as? Double)?.let { emit(EngineEvent.TotalDuration(it.seconds)) }

            "pause" -> (value as? Boolean)?.let { emitPlaying(!it) }

            "playlist-pos" -> {
                val position = (value as? Long) ?: return
                if (position > playlistPosition) {
                    // mpv walked onto the entry we appended: the previous track
                    // finished and this one is already playing.
                    playlistPosition = position
                    emit(EngineEvent.Completed)
                } else {
                    playlistPosition = position
                }
            }
        }
    }

    private fun isLastEntry(): Boolean {
        val count = handle.getLong("playlist-count") ?: return true
        return playlistPosition >= count - 1
    }

    override suspend fun play(source: MediaSource) = withContext(io) {
        applyHeaders(source)
        current = source
        playlistPosition = 0
        handle.command("loadfile", source.url, "replace")
        handle.setProperty("pause", false)
        // Say so directly. mpv only notifies when `pause` actually flips, and it
        // is already false here, so waiting for the property would report nothing.
        emitPlaying(true)
    }

    override suspend fun preload(source: MediaSource) = withContext(io) {
        // Replace whatever was queued behind the current entry: the queue may
        // have changed since the last preload.
        dropQueuedEntries()
        applyHeaders(source)
        handle.command("loadfile", source.url, "append")
    }

    override suspend fun prepare(source: MediaSource) = withContext(io) {
        applyHeaders(source)
        current = source
        playlistPosition = 0
        handle.command("loadfile", source.url, "replace")
        handle.setProperty("pause", true)
    }

    private fun dropQueuedEntries() {
        val count = handle.getLong("playlist-count") ?: return
        val position = handle.getLong("playlist-pos") ?: playlistPosition
        for (index in count - 1 downTo position + 1) {
            runCatching { handle.command("playlist-remove", index.toString()) }
        }
    }

    /**
     * mpv takes request headers as global options, so they are set immediately
     * before the load they belong to. Clearing them first matters: a Yandex
     * CDN url must not go out carrying the YouTube user agent.
     */
    private fun applyHeaders(source: MediaSource) {
        val headers = (source as? MediaSource.Network)?.headers.orEmpty()
        val userAgent = headers.entries
            .firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }
            ?.value

        handle.setProperty("user-agent", userAgent ?: DEFAULT_USER_AGENT)
        handle.setProperty(
            "http-header-fields",
            headers.filterKeys { !it.equals("User-Agent", ignoreCase = true) }
                .entries
                .joinToString(",") { "${it.key}: ${it.value}" },
        )
    }

    override suspend fun pause() = withContext(io) {
        handle.setProperty("pause", true)
        emitPlaying(false)
    }

    override suspend fun resume() = withContext(io) {
        handle.setProperty("pause", false)
        emitPlaying(true)
    }

    override suspend fun stop() = withContext(io) {
        current = null
        playlistPosition = 0
        handle.command("stop")
        emitPlaying(false)
    }

    override suspend fun seek(to: Duration) = withContext(io) {
        handle.command("seek", to.inWholeMilliseconds.toDouble().div(1000).toString(), "absolute")
    }

    override suspend fun setVolume(volume: Float) = withContext(io) {
        // mpv takes 0..100; the rest of the app works in 0..1.
        handle.setProperty("volume", (volume.coerceIn(0f, 1f) * 100.0))
    }

    override suspend fun setSpeed(speed: Float) = withContext(io) {
        handle.setProperty("speed", speed.coerceIn(MIN_SPEED, MAX_SPEED).toDouble())
    }

    override suspend fun release() = withContext(io) { handle.close() }

    private fun emit(event: EngineEvent) {
        _events.tryEmit(event)
    }

    /**
     * Reports the playing state without filtering repeats.
     *
     * Deduplicating here looks tempting and is wrong: `mpv_observe_property`
     * delivers the property's current value the moment it is observed, before
     * anyone is listening, so the filter would remember a state nobody was told
     * about and swallow the real one later. Downstream is a StateFlow, which
     * conflates equal values anyway.
     */
    private fun emitPlaying(isPlaying: Boolean) {
        emit(EngineEvent.PlayingChanged(isPlaying))
    }

    private val MediaSource.url: String
        get() = when (this) {
            is MediaSource.LocalFile -> path
            is MediaSource.Network -> url
        }

    private companion object {
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
        const val DEFAULT_USER_AGENT = "quark"
    }
}

class MpvPlaybackException(message: String) : RuntimeException(message)
