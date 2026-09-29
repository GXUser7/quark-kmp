package com.quark.app.desktop

import com.quark.app.AppService
import com.quark.core.model.CoverType
import com.quark.core.player.PlayerState
import com.quark.core.settings.SettingsStore
import com.quark.platform.discord.DiscordActivity
import com.quark.platform.discord.DiscordRpc
import com.quark.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.time.Clock

/**
 * Shows what is playing on the user's Discord profile while the setting is on
 * (`discord_rpc.dart`). Discord may start after quark or restart under it, so
 * a lost connection is retried every so often rather than given up on.
 */
class DiscordPresence(
    private val controller: PlayerController,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) : AppService {

    private val rpc = DiscordRpc()
    private var job: Job? = null

    override fun start() {
        if (job != null) return
        job = scope.launch(Dispatchers.IO) {
            settings.settings.map { it.integrations.discordRpc }.distinctUntilChanged().collectLatest { enabled ->
                if (!enabled) {
                    runCatching { rpc.close() }
                    return@collectLatest
                }
                controller.state
                    .map { Snapshot.of(it) }
                    .distinctUntilChanged()
                    .collectLatest { snapshot ->
                        while (true) {
                            if (rpc.isConnected || rpc.connect()) {
                                if (rpc.setActivity(snapshot.activity())) break
                            }
                            delay(RETRY_MS)
                        }
                    }
            }
        }
    }

    override suspend fun close() {
        job?.cancelAndJoin()
        job = null
        withContext(Dispatchers.IO) { runCatching { rpc.close() } }
    }

    /** The parts of the player state the card shows; the position only matters on seeks. */
    private data class Snapshot(
        val title: String?,
        val artist: String,
        val album: String,
        val cover: String?,
        val playing: Boolean,
        val positionBucket: Long,
        val durationMs: Long,
    ) {
        fun activity(): DiscordActivity? {
            if (title == null) return null
            val now = Clock.System.now().toEpochMilliseconds()
            val start = now - positionBucket * BUCKET_MS
            return DiscordActivity(
                details = title,
                state = artist,
                largeImage = cover,
                largeText = album,
                startMs = if (playing) start else null,
                endMs = if (playing && durationMs > 0) start + durationMs else null,
            )
        }

        companion object {
            fun of(state: PlayerState) = Snapshot(
                title = state.current.title.takeIf { state.hasTrack },
                artist = state.current.artistLine,
                album = state.current.albumLine,
                cover = state.current.cover.takeIf { state.current.coverType == CoverType.Url && it.startsWith("http") },
                playing = state.isPlaying,
                // Coarse on purpose: Discord only needs a new start time when the
                // user seeks, not on every tick of the position.
                positionBucket = state.position.inWholeMilliseconds / BUCKET_MS,
                durationMs = state.duration.inWholeMilliseconds,
            )
        }
    }

    private companion object {
        const val RETRY_MS = 15_000L
        const val BUCKET_MS = 15_000L
    }
}
