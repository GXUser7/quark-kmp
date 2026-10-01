package com.quark.data.repository

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import com.quark.core.model.Track
import com.quark.data.db.QuarkDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

/**
 * The `known_tracks` table: every track the player has seen, whatever its source.
 *
 * Rows are keyed by [Track.filepath], which for remote tracks is the path the
 * cached file would occupy. That is how the Dart build identified them too, so
 * the two agree on what is already known.
 */
class TrackRepository(
    private val db: QuarkDatabase,
    private val io: CoroutineDispatcher,
) {
    private val queries get() = db.knownTracksQueries

    fun observeAll(): Flow<List<Track>> =
        queries.selectAll().asFlow().mapToList(io).map { rows -> rows.map { it.toTrack() } }

    suspend fun all(): List<Track> = withContext(io) {
        queries.selectAll().executeAsList().map { it.toTrack() }
    }

    suspend fun byPath(path: String): Track? = withContext(io) {
        queries.selectByPath(path).executeAsOneOrNull()?.toTrack()
    }

    /** Tracks whose title, artists or album contain [query], for the local side of search. */
    suspend fun search(query: String, limit: Long = 100): List<Track> = withContext(io) {
        if (query.isBlank()) return@withContext emptyList()
        queries.search(query.trim(), limit).executeAsList().map { it.toTrack() }
    }

    suspend fun idOf(path: String): Long? = withContext(io) {
        queries.selectByPath(path).executeAsOneOrNull()?.id
    }

    /**
     * Records [tracks], leaving rows that are already there untouched. Used by
     * the scanner and whenever a playlist is loaded, so it runs against
     * thousands of rows and goes in one transaction.
     */
    suspend fun remember(tracks: List<Track>, downloaded: Boolean = true) = withContext(io) {
        if (tracks.isEmpty()) return@withContext
        queries.transaction {
            for (track in tracks) {
                queries.insertOrIgnore(
                    path = track.filepath,
                    title = track.title,
                    artists = track.artists.joinArtists(),
                    album = track.albumLine,
                    cover_url = track.coverUrl,
                    source = track.source.value,
                    sourceid = track.sourceId,
                    downloaded = downloaded,
                )
            }
        }
    }

    /** Writes [track] even if the path is already known, and returns its id. */
    suspend fun upsert(track: Track, downloaded: Boolean = true): Long = withContext(io) {
        queries.transactionWithResult {
            queries.upsert(
                path = track.filepath,
                title = track.title,
                artists = track.artists.joinArtists(),
                album = track.albumLine,
                cover_url = track.coverUrl,
                source = track.source.value,
                sourceid = track.sourceId,
                downloaded = downloaded,
            )
            queries.lastInsertedId().executeAsOne()
        }
    }

    /** Records where the cover art for [trackId] was cached. */
    suspend fun setCover(
        trackId: Long,
        coverPath: String?,
        blurPath: String?,
        md5: String?,
    ) = withContext(io) {
        queries.setCoverPaths(coverPath, blurPath, md5, trackId)
    }

    suspend fun forget(path: String) = withContext(io) {
        queries.deleteByPath(path)
    }

    /** Paths already in the table, for skipping them during a rescan. */
    suspend fun knownPaths(): Set<String> = withContext(io) {
        queries.selectPaths().executeAsList().toSet()
    }
}
