package com.quark.app.stats

import com.quark.app.AppService
import com.quark.core.model.Track
import com.quark.core.player.ChangeReason
import com.quark.core.player.PlayerState
import com.quark.core.settings.SettingsStore
import com.quark.data.repository.Listen
import com.quark.data.repository.ListenStatsRepository
import com.quark.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO
import kotlin.time.Duration.Companion.seconds

/** Records actual time heard; jumps caused by seeking are deliberately ignored. */
class ListenLogger(
    private val controller: PlayerController,
    private val settings: SettingsStore,
    private val repository: ListenStatsRepository,
    private val scope: CoroutineScope,
    private val nowEpochSeconds: () -> Long = { Clock.System.now().epochSeconds },
) : AppService {
    private var collector: Job? = null
    private var active: Active? = null

    override fun start() {
        if (collector != null) return
        active = controller.state.value.toActiveOrNull()
        collector = scope.launch {
            controller.state.collect { state ->
                val current = active
                when {
                    !state.hasTrack -> Unit
                    current == null -> active = state.toActiveOrNull()
                    current.track != state.current -> {
                        current.save(state.lastChangeReason)
                        active = Active(state.current).also { it.observe(state) }
                    }
                    else -> current.observe(state)
                }
            }
        }
    }

    override suspend fun close() {
        val running = collector ?: return
        running.cancelAndJoin()
        active?.save(ChangeReason.External)
        active = null
        collector = null
    }

    private suspend fun Active.save(reason: ChangeReason) {
        if (!settings.current.library.logListens || playedSeconds <= MINIMUM_LISTEN_SECONDS) return
        repository.record(
            listen = Listen(
                trackPath = track.filepath,
                at = nowEpochSeconds(),
                played = playedSeconds.seconds,
                total = duration,
                skipped = reason != ChangeReason.Completed,
            ),
            track = track,
        )
    }

    private class Active(val track: Track) {
        var playedSeconds: Long = 0
        var duration: Duration = ZERO
        private var lastCountedSecond: Long = 0

        fun observe(state: PlayerState) {
            if (state.current != track) return
            if (state.duration > ZERO) duration = state.duration

            val second = state.position.inWholeSeconds
            val difference = second - lastCountedSecond
            if (difference in 1..SEEK_THRESHOLD_SECONDS) playedSeconds += difference
            if (difference != 0L) lastCountedSecond = second
        }
    }

    private fun PlayerState.toActiveOrNull(): Active? =
        current.takeIf { hasTrack }?.let(::Active)?.also { it.observe(this) }

    private companion object {
        const val SEEK_THRESHOLD_SECONDS = 2L
        const val MINIMUM_LISTEN_SECONDS = 10L
    }
}
