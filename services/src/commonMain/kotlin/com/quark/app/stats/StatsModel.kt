package com.quark.app.stats

import com.quark.core.stats.Listen
import com.quark.core.stats.ListeningAnalytics
import com.quark.core.stats.ListeningReport
import com.quark.data.repository.ListenStatsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.time.Clock

/** Which stretch of history the statistics cover. */
enum class StatsPeriod(val days: Int?) { Week(7), Month(30), Year(365), All(null) }

sealed interface StatsState {
    data object Loading : StatsState
    data class Ready(val period: StatsPeriod, val report: ListeningReport) : StatsState
    data class Failed(val message: String) : StatsState
}

/** The listening statistics screen's data: the log, analysed for a period. */
class StatsModel(
    private val repository: ListenStatsRepository,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<StatsState>(StatsState.Loading)
    val state: StateFlow<StatsState> = _state.asStateFlow()

    fun load(period: StatsPeriod = StatsPeriod.Month) {
        _state.value = StatsState.Loading
        scope.launch {
            _state.value = try {
                val now = Clock.System.now().epochSeconds
                val since = period.days?.let { now - it * 86_400L } ?: 0L
                val listens = repository.listens(since).map { record ->
                    Listen(
                        key = record.trackKey,
                        title = record.title,
                        artists = record.artists,
                        album = record.album,
                        source = record.source,
                        at = record.at,
                        playedSeconds = record.playedSeconds,
                        totalSeconds = record.totalSeconds,
                        skipped = record.skipped,
                    )
                }
                StatsState.Ready(period, ListeningAnalytics.analyze(listens, now))
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                StatsState.Failed(e.message ?: "Could not read the listening history")
            }
        }
    }

    fun clear() {
        scope.launch {
            runCatching { repository.clear() }
            load((state.value as? StatsState.Ready)?.period ?: StatsPeriod.Month)
        }
    }
}
