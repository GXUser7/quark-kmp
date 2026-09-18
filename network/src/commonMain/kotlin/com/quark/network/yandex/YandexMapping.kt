package com.quark.network.yandex

import com.quark.core.model.CoverType
import com.quark.core.model.Playlist
import com.quark.core.model.PlaylistId
import com.quark.core.model.PlaylistSource
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import com.quark.network.yandex.dto.PlaylistDto
import com.quark.network.yandex.dto.TrackDto

/**
 * Where a cached Yandex track lives on disk.
 *
 * The odd name is `quark_yandex_music` written backwards, which is what the
 * Dart build used (`getTrackPath`, `objects/track.dart:342`). Keeping it means
 * files already downloaded by the Flutter version are found rather than
 * fetched again.
 */
fun yandexCachePath(cacheRoot: String, trackId: String, separator: String = "/"): String =
    listOf(cacheRoot, "audio_cache", "yandex_music", "cisum_xednay_krauq$trackId.flac")
        .joinToString(separator)

fun TrackDto.toTrack(cacheRoot: String, separator: String = "/"): YandexTrack = YandexTrack(
    title = title.ifBlank { Track.UNKNOWN_TITLE },
    artists = artists.map { it.name }.filter(String::isNotBlank)
        .ifEmpty { listOf(Track.UNKNOWN_ARTIST) },
    albums = albums.map { it.title }.filter(String::isNotBlank)
        .ifEmpty { listOf(Track.UNKNOWN_ALBUM) },
    filepath = yandexCachePath(cacheRoot, id, separator),
    coverType = CoverType.Url,
    cover = coverUrl().orEmpty(),
    trackId = id,
    albumId = albums.firstOrNull()?.id,
    artistIds = artists.map { it.id },
    durationMs = durationMs,
    available = available,
)

fun PlaylistDto.toPlaylist(cacheRoot: String, separator: String = "/"): Playlist = Playlist(
    id = PlaylistId(ownerUid = owner.uid, kind = kind, source = PlaylistSource.YandexMusic),
    name = title,
    // Entries arrive either expanded or as bare ids; the bare ones are filled
    // in by a follow-up batch lookup rather than shown as blanks.
    tracks = tracks.mapNotNull { it.track?.toTrack(cacheRoot, separator) },
)

/** Ids of entries the playlist response did not expand. */
fun PlaylistDto.unresolvedTrackIds(): List<String> =
    tracks.filter { it.track == null }.map { it.id }.filter(String::isNotEmpty)
