package com.quark.data.repository

import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Track
import com.quark.core.model.TrackSource
import com.quark.core.model.YandexTrack
import com.quark.core.model.YtMusicTrack
import com.quark.data.db.Known_tracks

/**
 * `known_tracks` stores artists as one string, the way Drift wrote it. The
 * separators are the ones the Dart build split on, not just the comma it joined
 * with, because tags in the wild use all of them.
 */
private val ARTIST_SEPARATORS = Regex("[,&;/]")

internal fun String.toArtistList(): List<String> =
    split(ARTIST_SEPARATORS)
        .map(String::trim)
        .filter(String::isNotEmpty)
        .ifEmpty { listOf(Track.UNKNOWN_ARTIST) }

internal fun List<String>.joinArtists(): String = joinToString(",")

fun Known_tracks.toTrack(): Track {
    val artistList = artists.toArtistList()
    val albumList = listOf(album.ifBlank { Track.UNKNOWN_ALBUM })

    return when (TrackSource.parse(source)) {
        TrackSource.YandexMusic -> YandexTrack(
            title = title,
            artists = artistList,
            albums = albumList,
            filepath = path,
            coverType = if (cover_path != null) CoverType.ExternalFile else CoverType.Url,
            cover = cover_path ?: cover_url.orEmpty(),
            trackId = sourceid.orEmpty(),
        )

        TrackSource.YouTubeMusic -> YtMusicTrack(
            title = title,
            artists = artistList,
            albums = albumList,
            filepath = path,
            coverType = if (cover_path != null) CoverType.ExternalFile else CoverType.Url,
            cover = cover_path ?: cover_url.orEmpty(),
            videoId = sourceid.orEmpty(),
        )

        // Spotify tracks are never written by the player; treat anything else as local.
        else -> LocalTrack(
            title = title,
            artists = artistList,
            albums = albumList,
            filepath = path,
            coverType = coverTypeOf(cover_path, cover_url),
            cover = cover_path ?: cover_url ?: path,
        )
    }
}

/**
 * A local track with no cover file recorded is assumed to carry its artwork in
 * its tags, which is what [CoverType.BuiltIn] means and what the scanner sets.
 */
private fun coverTypeOf(coverPath: String?, coverUrl: String?): CoverType = when {
    coverPath != null -> CoverType.ExternalFile
    coverUrl != null -> CoverType.Url
    else -> CoverType.BuiltIn
}

internal val Track.sourceId: String?
    get() = when (this) {
        is YandexTrack -> trackId
        is YtMusicTrack -> videoId
        else -> null
    }

internal val Track.coverUrl: String?
    get() = cover.takeIf { coverType == CoverType.Url && it.isNotEmpty() }
