package com.quark.app.player

import com.quark.app.AppService
import com.quark.app.QuarkSourceResolver
import com.quark.core.model.Playlist
import com.quark.core.settings.SettingsStore
import com.quark.data.net.TrackDownloader
import com.quark.player.PlayerController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/** Keeps the previous, current and next remote tracks available offline. */
class TrackCacheCoordinator(
    private val controller: PlayerController,
    private val settings: SettingsStore,
    private val cacher: TrackDownloader,
    private val resolver: QuarkSourceResolver,
    private val scope: CoroutineScope,
) : AppService {
    private var job: Job? = null

    override fun start() {
        if (job != null) return
        job = scope.launch {
            combine(
                settings.settings.map { it.playback.cacheRemoteTracks }.distinctUntilChanged(),
                controller.state.map { state ->
                    CacheTarget(Playlist(state.playlistInfo.id, state.playlistInfo.name, state.playlist), state.current)
                }.distinctUntilChanged(),
            ) { enabled, target -> enabled to target }
                .collectLatest { (enabled, target) ->
                    if (enabled && target.playlist.tracks.isNotEmpty()) {
                        cacher.cacheAround(target.playlist, target.current, resolver::downloadSource)
                    }
                }
        }
    }

    override suspend fun close() {
        job?.cancelAndJoin()
        job = null
    }

    private data class CacheTarget(val playlist: Playlist, val current: com.quark.core.model.Track)
}
