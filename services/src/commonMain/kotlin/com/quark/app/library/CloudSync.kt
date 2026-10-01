package com.quark.app.library

import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.ServiceTrack
import com.quark.core.model.Track
import com.quark.core.model.TrackSource
import com.quark.core.model.YandexTrack
import com.quark.core.model.YtMusicTrack
import com.quark.core.settings.SettingsStore
import com.quark.network.quark.CloudPlaylist
import com.quark.network.quark.CloudTrack
import com.quark.network.quark.PlaylistSync
import com.quark.network.quark.QuarkAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed interface SyncState {
    data object Idle : SyncState
    data object Running : SyncState
    data class Done(val downloaded: Int, val uploaded: Int) : SyncState
    data class Failed(val message: String) : SyncState
}

/**
 * Keeps the user's playlists in the quark cloud (`PlaylistSyncService`): first
 * the cloud's playlists that are missing here are created locally, then local
 * ones the cloud has not got are uploaded. Matching is by title, as in the
 * Dart build, so a playlist is never duplicated on either side.
 */
class CloudSync(
    private val account: QuarkAccount,
    private val sync: PlaylistSync,
    private val library: UserLibrary,
    private val settings: SettingsStore,
) {
    private val lock = Mutex()
    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    suspend fun run() = lock.withLock {
        if (!account.isLoggedIn || !settings.current.account.syncPlaylists) return@withLock
        _state.value = SyncState.Running
        _state.value = try {
            val cloud = sync.fetchAll()
            val localTitles = library.all.value.map { it.title }.toSet()
            var downloaded = 0
            for (stored in cloud) {
                val playlist = stored.playlist
                if (playlist.title in localTitles) continue
                library.create(playlist.title, playlist.tracks.sortedBy(CloudTrack::position).map { it.toTrack() }, playlist.coverUrl)
                downloaded++
            }

            val cloudTitles = cloud.map { it.playlist.title }.toSet()
            var uploaded = 0
            for (local in library.all.value) {
                if (local.title in cloudTitles) continue
                val tracks = library.tracksOf(local.id)
                sync.upload(
                    CloudPlaylist(
                        title = local.title,
                        coverUrl = local.coverUrl,
                        description = local.description,
                        type = if (local.isAlbum) "Album" else "Playlist",
                        tracks = tracks.mapIndexed { index, track -> track.toCloud(index) },
                    )
                )
                uploaded++
            }
            SyncState.Done(downloaded, uploaded)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            SyncState.Failed(e.message ?: "Sync failed")
        }
    }
}

private fun Track.toCloud(position: Int) = CloudTrack(
    path = filepath,
    title = title,
    artists = artists.joinToString(","),
    album = albumLine,
    coverUrl = cover.takeIf { coverType == CoverType.Url && it.isNotEmpty() },
    source = source.value,
    sourceId = when (this) {
        is YandexTrack -> trackId
        is YtMusicTrack -> videoId
        is ServiceTrack -> id
        is LocalTrack -> null
    },
    position = position,
)

private fun CloudTrack.toTrack(): Track {
    val artistList = artists.split(',').map(String::trim).filter(String::isNotEmpty)
        .ifEmpty { listOf(Track.UNKNOWN_ARTIST) }
    val albums = listOf(album.ifBlank { Track.UNKNOWN_ALBUM })
    val coverType = if (coverUrl.isNullOrBlank()) CoverType.BuiltIn else CoverType.Url
    return when (val kind = TrackSource.parse(source)) {
        TrackSource.YandexMusic -> YandexTrack(
            title, artistList, albums, path, CoverType.Url, coverUrl.orEmpty(), trackId = sourceId.orEmpty(),
        )
        TrackSource.YouTubeMusic -> YtMusicTrack(
            title, artistList, albums, path, CoverType.Url, coverUrl.orEmpty(), videoId = sourceId.orEmpty(),
        )
        TrackSource.Spotify, TrackSource.SoundCloud, TrackSource.Vk -> ServiceTrack(
            title, artistList, albums, path, CoverType.Url, kind, id = sourceId.orEmpty(), cover = coverUrl.orEmpty(),
        )
        TrackSource.Local -> LocalTrack(title, artistList, albums, path, coverType, coverUrl ?: path)
    }
}
