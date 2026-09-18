package com.quark.app.yandex

import com.quark.app.QuarkApp
import com.quark.core.model.Playlist
import com.quark.core.model.PlaylistId
import com.quark.core.model.PlaylistSource
import com.quark.core.model.Track
import com.quark.network.yandex.dto.PlaylistDto
import com.quark.network.yandex.toPlaylist
import com.quark.network.yandex.toTrack
import com.quark.platform.AppDirs
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.awt.Desktop
import java.io.File
import java.net.URI

/** A playlist as the sidebar shows it, before its tracks are fetched. */
data class YandexPlaylistSummary(
    val kind: Long,
    val ownerUid: Long,
    val title: String,
    val trackCount: Int,
    val coverUrl: String?,
)

sealed interface YandexLoad {
    data object Idle : YandexLoad
    data object Loading : YandexLoad
    data class Failed(val message: String) : YandexLoad
}

/**
 * The Yandex Music side of the library: signing in, listing playlists, and
 * handing one to the player.
 */
class YandexViewModel(private val app: QuarkApp) {

    private val scope = app.scope
    private val session = app.yandex

    val state: StateFlow<YandexState> = session.state

    private val _playlists = MutableStateFlow<List<YandexPlaylistSummary>>(emptyList())
    val playlists: StateFlow<List<YandexPlaylistSummary>> = _playlists.asStateFlow()

    private val _load = MutableStateFlow<YandexLoad>(YandexLoad.Idle)
    val load: StateFlow<YandexLoad> = _load.asStateFlow()

    private var job: Job? = null

    /** Where a cached Yandex track would live; the mapper builds paths from it. */
    private val cacheRoot: String get() = AppDirs.cache.toString()

    fun openAuthorizePage(url: String) {
        runCatching {
            if (Desktop.isDesktopSupported()) Desktop.getDesktop().browse(URI(url))
        }
    }

    fun signIn(token: String) {
        job?.cancel()
        job = scope.launch {
            val result = session.signIn(token)
            if (result is YandexState.SignedIn) refreshPlaylists()
        }
    }

    fun signOut() {
        session.signOut()
        _playlists.value = emptyList()
    }

    fun refreshPlaylists() {
        job?.cancel()
        job = scope.launch {
            val api = session.api ?: return@launch
            _load.value = YandexLoad.Loading
            try {
                _playlists.value = api.userPlaylists().map(PlaylistDto::toSummary)
                _load.value = YandexLoad.Idle
            } catch (e: Exception) {
                _load.value = YandexLoad.Failed(e.message ?: "Could not load playlists")
            }
        }
    }

    /**
     * Fetches a playlist and starts it.
     *
     * Entries the api left unexpanded are filled in with one batch lookup
     * rather than one request each, and unavailable tracks are dropped: the
     * player would only fail to open them.
     */
    fun openPlaylist(summary: YandexPlaylistSummary) {
        job?.cancel()
        job = scope.launch {
            val api = session.api ?: return@launch
            _load.value = YandexLoad.Loading
            try {
                val dto = api.playlist(kind = summary.kind, userId = summary.ownerUid)
                val known = dto.toPlaylist(cacheRoot, File.separator)

                val missing = dto.tracks.filter { it.track == null }.map { it.id }
                val fetched = if (missing.isEmpty()) {
                    emptyList()
                } else {
                    api.tracks(missing).map { it.toTrack(cacheRoot, File.separator) }
                }

                val tracks: List<Track> = (known.tracks + fetched).filter { track ->
                    track !is com.quark.core.model.YandexTrack || track.available
                }

                app.tracks.remember(tracks, downloaded = false)
                app.controller.load(
                    Playlist(
                        id = PlaylistId(summary.ownerUid, summary.kind, PlaylistSource.YandexMusic),
                        name = summary.title,
                        tracks = tracks,
                    )
                )
                _load.value = YandexLoad.Idle
            } catch (e: Exception) {
                _load.value = YandexLoad.Failed(e.message ?: "Could not open the playlist")
            }
        }
    }
}

private fun PlaylistDto.toSummary() = YandexPlaylistSummary(
    kind = kind,
    ownerUid = owner.uid,
    title = title,
    trackCount = trackCount,
    coverUrl = coverUrl(),
)
