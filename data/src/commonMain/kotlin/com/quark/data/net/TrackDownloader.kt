package com.quark.data.net

import com.quark.core.model.Playlist
import com.quark.core.model.Track

/** Where a remote track's bytes can be fetched from right now. */
data class DownloadSource(val url: String, val headers: Map<String, String> = emptyMap())

/**
 * Puts remote tracks on disk at the paths their models already carry.
 *
 * Downloads for the same destination are collapsed, so several features can ask
 * for the same track without truncating each other's file.
 */
interface TrackDownloader {

    /** The previous, current and next remote tracks around [current]. */
    suspend fun cacheAround(
        playlist: Playlist,
        current: Track,
        source: suspend (Track) -> DownloadSource?,
    ): List<String>

    /** Every remote track in [tracks]; returns the paths that ended up on disk. */
    suspend fun cache(
        tracks: List<Track>,
        source: suspend (Track) -> DownloadSource?,
    ): List<String>
}
