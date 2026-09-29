package com.quark.app.library

import com.quark.core.model.Track
import com.quark.data.repository.PlaylistRepository
import com.quark.data.repository.StoredPlaylist
import com.quark.data.repository.TrackRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.stateIn

/**
 * The user's own playlists: the "Your Playlists" shelf of the home screen and
 * every "Add to playlist" menu (`LocalPlaylistsSection`, `AddingTracks` on
 * slop). Tracks of any source can go in one; they are recorded in
 * `known_tracks` first, as the Dart build did.
 */
class UserLibrary(
    private val playlists: PlaylistRepository,
    private val tracks: TrackRepository,
    scope: CoroutineScope,
) {
    val all: StateFlow<List<StoredPlaylist>> = playlists.observeUser()
        .catch { emit(emptyList()) }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    suspend fun tracksOf(id: Long): List<Track> = playlists.tracksOf(id)

    suspend fun create(title: String, tracks: List<Track> = emptyList(), coverUrl: String? = null): Long {
        val name = title.trim().ifEmpty { "Playlist" }
        return playlists.createWith(name, tracks, coverUrl)
    }

    suspend fun add(playlistId: Long, tracks: List<Track>) = playlists.addTracks(playlistId, tracks)

    suspend fun remove(playlistId: Long, track: Track) = playlists.removeTrack(playlistId, track.filepath)

    suspend fun reorder(playlistId: Long, tracks: List<Track>) = playlists.reorder(playlistId, tracks.map(Track::filepath))

    suspend fun rename(playlistId: Long, title: String) = playlists.rename(playlistId, title.trim())

    suspend fun setDescription(playlistId: Long, description: String?) =
        playlists.setDescription(playlistId, description?.trim()?.ifEmpty { null })

    suspend fun delete(playlistId: Long) = playlists.delete(playlistId)

    suspend fun deleteAll() = all.value.forEach { playlists.delete(it.id) }

    /** A track to borrow a cover from, for playlists that have none of their own. */
    suspend fun coverTrack(playlistId: Long): Track? = playlists.firstTrackCover(playlistId)

    /** Tracks the library has seen that match [query], whatever their source. */
    suspend fun search(query: String): List<Track> = tracks.search(query)
}
