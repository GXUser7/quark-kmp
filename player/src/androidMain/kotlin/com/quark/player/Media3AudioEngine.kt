package com.quark.player

import android.content.Context
import android.net.Uri
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * [AudioEngine] on Media3's ExoPlayer, the Android counterpart of the libmpv
 * engine.
 *
 * Gapless works the same way: the next track is appended to ExoPlayer's own
 * playlist while the current one plays, and ExoPlayer crosses into it without a
 * gap. The crossing arrives as a media item transition with reason AUTO, which
 * is reported as [EngineEvent.Completed] — the next track is already sounding,
 * so the controller only catches its state up.
 *
 * ExoPlayer is single-threaded: every call is made on the main looper, and the
 * engine must be built there too. The [player] is exposed so the playback
 * service can put a media session in front of it.
 */
@OptIn(UnstableApi::class)
class Media3AudioEngine(context: Context) : AudioEngine {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val main = Dispatchers.Main.immediate

    /** Request headers per url; a Yandex CDN url must not carry YouTube's agent. */
    private val headers = ConcurrentHashMap<String, Map<String, String>>()

    private val _events = MutableSharedFlow<EngineEvent>(
        replay = 0,
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    override val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    /** The source in the current slot, for error reports. */
    @Volatile private var current: MediaSource? = null

    /** What was appended behind the current item, so a crossing can promote it. */
    @Volatile private var pending: MediaSource? = null
    private var ticker: Job? = null
    private var itemCounter = 0L

    private val listener = object : Player.Listener {
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
            emit(EngineEvent.PlayingChanged(playWhenReady && player.playbackState != Player.STATE_ENDED))
            updateTicker()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            updateTicker()
        }

        override fun onPlaybackStateChanged(state: Int) {
            when (state) {
                Player.STATE_READY -> emitDuration()
                Player.STATE_ENDED -> {
                    emit(EngineEvent.PlayingChanged(false))
                    emit(EngineEvent.Ended)
                }
                else -> Unit
            }
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                // ExoPlayer walked onto the item we appended: the previous
                // track finished and this one is already playing.
                current = pending
                pending = null
                emit(EngineEvent.Completed)
                emit(EngineEvent.Position(Duration.ZERO))
            }
            emitDuration()
        }

        override fun onPlayerError(error: PlaybackException) {
            val source = current ?: return
            emit(EngineEvent.PlayingChanged(false))
            emit(EngineEvent.Failed(source, error))
        }
    }

    val player: ExoPlayer

    init {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(30_000)
        val upstream = DefaultDataSource.Factory(context.applicationContext, http)
        val withHeaders = ResolvingDataSource.Factory(upstream) { spec ->
            val extra = headers[spec.uri.toString()]
            if (extra.isNullOrEmpty()) spec else spec.withAdditionalHeaders(extra)
        }

        player = ExoPlayer.Builder(context.applicationContext)
            .setMediaSourceFactory(DefaultMediaSourceFactory(withHeaders))
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()
        player.addListener(listener)
    }

    override suspend fun play(source: MediaSource) = withContext(main) {
        current = source
        pending = null
        player.setMediaItem(source.toMediaItem())
        player.prepare()
        player.playWhenReady = true
        emit(EngineEvent.PlayingChanged(true))
    }

    override suspend fun preload(source: MediaSource) = withContext(main) {
        dropQueuedItems()
        pending = source
        player.addMediaItem(source.toMediaItem())
    }

    override suspend fun prepare(source: MediaSource) = withContext(main) {
        current = source
        pending = null
        player.setMediaItem(source.toMediaItem())
        player.prepare()
        player.playWhenReady = false
    }

    private fun dropQueuedItems() {
        val next = player.currentMediaItemIndex + 1
        if (next < player.mediaItemCount) player.removeMediaItems(next, player.mediaItemCount)
    }

    override suspend fun pause() = withContext(main) {
        player.playWhenReady = false
    }

    override suspend fun resume() = withContext(main) {
        if (player.playbackState == Player.STATE_IDLE) player.prepare()
        if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
        player.playWhenReady = true
    }

    override suspend fun stop() = withContext(main) {
        current = null
        pending = null
        player.stop()
        player.clearMediaItems()
        emit(EngineEvent.PlayingChanged(false))
    }

    override suspend fun seek(to: Duration) = withContext(main) {
        player.seekTo(to.inWholeMilliseconds.coerceAtLeast(0))
        emit(EngineEvent.Position(to))
    }

    override suspend fun setVolume(volume: Float) = withContext(main) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    override suspend fun setSpeed(speed: Float) = withContext(main) {
        // PlaybackParameters keeps the pitch at 1 unless asked otherwise, which
        // is the "tempo without chipmunks" the desktop engine gets from mpv.
        player.playbackParameters = PlaybackParameters(speed.coerceIn(MIN_SPEED, MAX_SPEED))
    }

    override suspend fun release() = withContext(main) {
        ticker?.cancel()
        player.removeListener(listener)
        player.release()
    }

    private fun updateTicker() {
        val running = ticker?.isActive == true
        if (player.isPlaying && !running) {
            ticker = scope.launch {
                while (isActive) {
                    emit(EngineEvent.Position(player.currentPosition.milliseconds))
                    delay(TICK_MS)
                }
            }
        } else if (!player.isPlaying && running) {
            ticker?.cancel()
            ticker = null
            emit(EngineEvent.Position(player.currentPosition.milliseconds))
        }
    }

    private fun emitDuration() {
        val duration = player.duration
        if (duration != C.TIME_UNSET && duration > 0) {
            emit(EngineEvent.TotalDuration(duration.milliseconds))
        }
    }

    private fun emit(event: EngineEvent) {
        _events.tryEmit(event)
    }

    private fun MediaSource.toMediaItem(): MediaItem {
        val uri = when (this) {
            is MediaSource.LocalFile ->
                if (path.startsWith("content://") || path.startsWith("file://")) Uri.parse(path)
                else Uri.fromFile(File(path))
            is MediaSource.Network -> Uri.parse(url)
        }
        if (this is MediaSource.Network) {
            val withAgent = if (headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) headers
            else headers + ("User-Agent" to DEFAULT_USER_AGENT)
            this@Media3AudioEngine.headers[url] = withAgent
        }

        val builder = MediaItem.Builder()
            .setUri(uri)
            // Unique per load: the same track queued twice must still count as
            // a transition when ExoPlayer crosses into the second copy.
            .setMediaId("${uri}#${itemCounter++}")
        info?.let { meta ->
            builder.setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(meta.title)
                    .setArtist(meta.artist)
                    .setAlbumTitle(meta.album)
                    .setArtworkUri(meta.artworkUrl?.takeIf(String::isNotBlank)?.let(Uri::parse))
                    .build()
            )
        }
        return builder.build()
    }

    private companion object {
        const val TICK_MS = 250L
        const val MIN_SPEED = 0.25f
        const val MAX_SPEED = 4f
        const val DEFAULT_USER_AGENT = "quark"
    }
}
