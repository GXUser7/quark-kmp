package com.quark.data.repository

import com.quark.core.model.Track
import com.quark.data.db.QuarkDatabase
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import kotlin.time.Duration

/** One finished listen, as the player saw it. */
data class Listen(
    val trackPath: String,
    val at: Long,
    val played: Duration,
    val total: Duration,
    val skipped: Boolean,
) {
    /** How far through the track the listener got, 0..100. */
    val progressPercent: Int
        get() = if (total.inWholeSeconds <= 0) 0
        else ((played.inWholeSeconds * 100) / total.inWholeSeconds).toInt().coerceIn(0, 100)
}

data class TrackPlays(val track: Track, val plays: Long, val seconds: Long)

/** One row of the listening history, joined with its track. */
data class ListenRecord(
    val trackKey: String,
    val title: String,
    val artists: List<String>,
    val album: String,
    val source: String,
    /** Unix seconds. */
    val at: Long,
    val playedSeconds: Long,
    val totalSeconds: Long,
    val progressPercent: Int,
    val skipped: Boolean,
    val track: Track,
)

class ListenStatsRepository(
    private val db: QuarkDatabase,
    private val io: CoroutineDispatcher,
) {
    private val queries get() = db.listenStatsQueries
    private val trackQueries get() = db.knownTracksQueries

    /**
     * Records [listen], first making sure the track has a row to point at.
     * Unknown tracks are recorded from [track] rather than dropped — the Dart
     * build lost the listen when the track had not been saved yet.
     */
    suspend fun record(listen: Listen, track: Track) = withContext(io) {
        queries.transaction {
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
            val trackId = trackQueries.selectByPath(listen.trackPath).executeAsOneOrNull()?.id
                ?: return@transaction
            queries.insert(
                track = trackId,
                time = listen.at,
                played_seconds = listen.played.inWholeSeconds,
                total_track_duration = listen.total.inWholeSeconds,
                progress_percent = listen.progressPercent.toLong(),
                is_skipped = listen.skipped,
            )
        }
    }

    suspend fun topTracks(since: Long, limit: Long = 20): List<TrackPlays> = withContext(io) {
        queries.topTracks(since, limit).executeAsList().map { row ->
            TrackPlays(
                track = com.quark.data.db.Known_tracks(
                    id = row.id,
                    path = row.path,
                    title = row.title,
                    artists = row.artists,
                    album = row.album,
                    cover_url = row.cover_url,
                    cover_path = row.cover_path,
                    blur_cover_path = row.blur_cover_path,
                    md5 = row.md5,
                    source = row.source,
                    sourceid = row.sourceid,
                    downloaded = row.downloaded,
                ).toTrack(),
                plays = row.plays,
                seconds = row.seconds ?: 0L,
            )
        }
    }

    suspend fun totalSeconds(since: Long): Long = withContext(io) {
        queries.totalSecondsSince(since).executeAsOne()
    }

    /** Every listen since [since], oldest first, with the track it was of. */
    suspend fun listens(since: Long = 0): List<ListenRecord> = withContext(io) {
        queries.listensWithTracks(since).executeAsList().map { row ->
            ListenRecord(
                trackKey = row.path,
                title = row.title,
                artists = row.artists.toArtistList(),
                album = row.album,
                source = row.source,
                at = row.time,
                playedSeconds = row.played_seconds,
                totalSeconds = row.total_track_duration,
                progressPercent = row.progress_percent.toInt(),
                skipped = row.is_skipped,
                track = com.quark.data.db.Known_tracks(
                    id = row.track_id,
                    path = row.path,
                    title = row.title,
                    artists = row.artists,
                    album = row.album,
                    cover_url = row.cover_url,
                    cover_path = row.cover_path,
                    blur_cover_path = null,
                    md5 = null,
                    source = row.source,
                    sourceid = row.sourceid,
                    downloaded = false,
                ).toTrack(),
            )
        }
    }

    suspend fun clear() = withContext(io) { queries.deleteAll() }
}
