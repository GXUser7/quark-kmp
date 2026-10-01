package com.quark.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quark.core.model.Playlist
import com.quark.core.model.Track
import com.quark.core.model.TrackSource
import com.quark.data.db.Playlists
import com.quark.data.db.QuarkDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/** A stored playlist, without its tracks. */
data class StoredPlaylist(
    val id: Long,
    val title: String,
    val coverPath: String?,
    val coverUrl: String?,
    val description: String?,
    val isAlbum: Boolean,
    val trackCount: Long = 0,
)

class PlaylistRepository(
    private val db: QuarkDatabase,
    private val io: CoroutineDispatcher,
) {
    private val queries get() = db.playlistsQueries
    private val trackQueries get() = db.knownTracksQueries

    fun observeAll(): Flow<List<StoredPlaylist>> =
        queries.selectAll().asFlow().mapToList(io).map { rows -> rows.map(Playlists::toStored) }

    suspend fun all(): List<StoredPlaylist> = withContext(io) {
        queries.selectAll().executeAsList().map(Playlists::toStored)
    }

    suspend fun byId(id: Long): StoredPlaylist? = withContext(io) {
        queries.selectById(id).executeAsOneOrNull()?.toStored()
    }

    suspend fun tracksOf(id: Long): List<Track> = withContext(io) {
        queries.tracksOf(id).executeAsList().map { it.toTrack() }
    }

    /** The user's own playlists, newest first, with how many tracks each holds. */
    fun observeUser(): Flow<List<StoredPlaylist>> =
        queries.selectUser().asFlow().mapToList(io).map { rows -> rows.map { it.toStored() } }

    suspend fun userPlaylists(): List<StoredPlaylist> = withContext(io) {
        queries.selectUser().executeAsList().map { it.toStored() }
    }

    suspend fun userPlaylistByTitle(title: String): StoredPlaylist? = withContext(io) {
        queries.selectUserByTitle(title).executeAsOneOrNull()?.toStored()
    }

    /** A new, empty playlist of the user's; returns its id. */
    suspend fun create(title: String): Long = withContext(io) {
        queries.transactionWithResult {
            queries.createTyped(title, TYPE_PLAYLIST)
            trackQueries.lastInsertedId().executeAsOne()
        }
    }

    /** A new playlist of the user's already holding [tracks]. */
    suspend fun createWith(title: String, tracks: List<Track>, coverUrl: String? = null): Long = withContext(io) {
        queries.transactionWithResult {
            queries.createTyped(title, TYPE_PLAYLIST)
            val id = trackQueries.lastInsertedId().executeAsOne()
            coverUrl?.let { queries.setCoverUrl(it, id) }
            appendTracks(id, tracks)
            id
        }
    }

    suspend fun setDescription(id: Long, description: String?) = withContext(io) {
        queries.setDescription(description, id)
    }

    suspend fun setCoverUrl(id: Long, url: String?) = withContext(io) {
        queries.setCoverUrl(url, id)
    }

    /** Takes one track out of a playlist, wherever it sits. */
    suspend fun removeTrack(playlistId: Long, trackPath: String) = withContext(io) {
        queries.transaction {
            val trackId = trackQueries.selectByPath(trackPath).executeAsOneOrNull()?.id ?: return@transaction
            queries.removeTrack(playlistId, trackId)
        }
    }

    /** Rewrites the order of a playlist to follow [paths]. */
    suspend fun reorder(playlistId: Long, paths: List<String>) = withContext(io) {
        queries.transaction {
            paths.forEachIndexed { index, path ->
                val trackId = trackQueries.selectByPath(path).executeAsOneOrNull()?.id ?: return@forEachIndexed
                queries.setPosition(index.toLong() + 1, playlistId, trackId)
            }
        }
    }

    /** Where the first track's artwork is, to stand in as the playlist's cover. */
    suspend fun firstTrackCover(playlistId: Long): Track? = withContext(io) {
        queries.tracksOf(playlistId).executeAsList().firstOrNull()?.toTrack()
    }

    suspend fun rename(id: Long, title: String) = withContext(io) {
        queries.rename(title, id)
    }

    suspend fun setCover(id: Long, path: String) = withContext(io) {
        queries.setCover(path, id)
    }

    suspend fun delete(id: Long) = withContext(io) {
        queries.transaction {
            queries.deleteTracksOf(id)
            queries.delete(id)
        }
    }

    /**
     * Appends [tracks], recording any the library has not seen first so the
     * foreign key resolves. Positions are assigned by the insert itself.
     */
    suspend fun addTracks(playlistId: Long, tracks: List<Track>) = withContext(io) {
        if (tracks.isEmpty()) return@withContext
        queries.transaction {
            appendTracks(playlistId, tracks)
        }
    }

    /**
     * Stores a complete playlist for startup restoration. When [existingId]
     * still exists it is replaced atomically; otherwise a new row is created.
     */
    suspend fun saveSnapshot(playlist: Playlist, existingId: Long? = null): Long = withContext(io) {
        queries.transactionWithResult {
            val playlistId = existingId
                ?.takeIf { queries.selectById(it).executeAsOneOrNull() != null }
                ?: run {
                    queries.createTyped(playlist.name, TYPE_SESSION)
                    trackQueries.lastInsertedId().executeAsOne()
                }

            queries.rename(playlist.name, playlistId)
            queries.deleteTracksOf(playlistId)
            appendTracks(playlistId, playlist.tracks)
            playlistId
        }
    }

    suspend fun removeAt(playlistId: Long, position: Long) = withContext(io) {
        queries.removeTrackAt(playlistId, position)
    }

    suspend fun size(playlistId: Long): Long = withContext(io) {
        queries.countTracksOf(playlistId).executeAsOne()
    }

    private fun appendTracks(playlistId: Long, tracks: List<Track>) {
        for (track in tracks) {
            // Rescans may discover edited tags at the same path. Upsert keeps
            // the persisted snapshot in step with what the UI just read.
            trackQueries.upsert(
                path = track.filepath,
                title = track.title,
                artists = track.artists.joinArtists(),
                album = track.albumLine,
                cover_url = track.coverUrl,
                source = track.source.value,
                sourceid = track.sourceId,
                downloaded = track.source == TrackSource.Local,
            )
            val trackId = trackQueries.selectByPath(track.filepath).executeAsOneOrNull()?.id
                ?: continue
            queries.appendTrack(playlistId, trackId, playlistId)
        }
    }
}

const val TYPE_PLAYLIST = "Playlist"
const val TYPE_SESSION = "Session"

private fun com.quark.data.db.SelectUser.toStored() = StoredPlaylist(
    id = id,
    title = title,
    coverPath = cover_path,
    coverUrl = cover_url,
    description = description,
    isAlbum = type.equals("Album", ignoreCase = true),
    trackCount = track_count,
)

private fun Playlists.toStored() = StoredPlaylist(
    id = id,
    title = title,
    coverPath = cover_path,
    coverUrl = cover_url,
    description = description,
    isAlbum = type.equals("Album", ignoreCase = true),
)
