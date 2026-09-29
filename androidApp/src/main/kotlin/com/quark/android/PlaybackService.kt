package com.quark.android

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.quark.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps playback alive outside the activity and gives the system its media
 * controls: the notification, the lock screen, headset buttons, Bluetooth.
 *
 * The session sits in front of the engine's ExoPlayer, but skipping must go
 * through [PlayerController] — it owns the queue, shuffle and repeat, and the
 * ExoPlayer playlist only ever holds the current track and the one preloaded
 * after it. So next and previous are rerouted, and advertised as always
 * available even when ExoPlayer's own playlist has nothing behind the current
 * item.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        val running = quark.getOrNull() ?: return
        val player = QueueAwarePlayer(running.engine.player, running.app.controller, scope)
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        session = MediaSession.Builder(this, player)
            .setSessionActivity(openApp)
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    /** Swiping the app away stops the service only if nothing is playing. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        val player = session?.player
        if (player == null || !player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        // The engine owns the player and outlives the service; only the
        // session goes.
        session?.release()
        session = null
        scope.cancel()
        super.onDestroy()
    }
}

@OptIn(UnstableApi::class)
private class QueueAwarePlayer(
    player: Player,
    private val controller: PlayerController,
    private val scope: CoroutineScope,
) : ForwardingPlayer(player) {

    private val skipCommands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
        )
        .build()

    override fun getAvailableCommands(): Player.Commands =
        super.getAvailableCommands().buildUpon().addAll(skipCommands).build()

    override fun isCommandAvailable(command: Int): Boolean =
        skipCommands.contains(command) || super.isCommandAvailable(command)

    override fun seekToNext() {
        scope.launch { controller.next() }
    }

    override fun seekToNextMediaItem() {
        scope.launch { controller.next() }
    }

    override fun seekToPrevious() {
        // As in every player: early in a track, go back; later, restart it.
        if (currentPosition > RESTART_THRESHOLD_MS) seekTo(0) else scope.launch { controller.previous() }
    }

    override fun seekToPreviousMediaItem() {
        scope.launch { controller.previous() }
    }

    private companion object {
        const val RESTART_THRESHOLD_MS = 3_000L
    }
}
