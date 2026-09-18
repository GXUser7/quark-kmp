package com.quark.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quark.core.model.Track
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

    suspend fun create(title: String): Long = withContext(io) {
        queries.transactionWithResult {
            queries.create(title)
            trackQueries.lastInsertedId().executeAsOne()
        }
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
            for (track in tracks) {
                trackQueries.insertOrIgnore(
                    path = track.filepath,
                    title = track.title,
                    artists = track.artists.joinArtists(),
                    album = track.albumLine,
                    cover_url = track.coverUrl,
                    source = track.source.value,
                    sourceid = track.sourceId,
                    downloaded = true,
                )
                val trackId = trackQueries.selectByPath(track.filepath).executeAsOneOrNull()?.id
                    ?: return@transaction
                queries.appendTrack(playlistId, trackId, playlistId)
            }
        }
    }

    suspend fun removeAt(playlistId: Long, position: Long) = withContext(io) {
        queries.removeTrackAt(playlistId, position)
    }

    suspend fun size(playlistId: Long): Long = withContext(io) {
        queries.countTracksOf(playlistId).executeAsOne()
    }
}

private fun Playlists.toStored() = StoredPlaylist(
    id = id,
    title = title,
    coverPath = cover_path,
    coverUrl = cover_url,
    description = description,
    isAlbum = type.equals("Album", ignoreCase = true),
)
