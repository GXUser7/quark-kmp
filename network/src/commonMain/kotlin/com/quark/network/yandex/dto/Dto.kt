package com.quark.network.yandex.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Only the fields the player reads. The api sends a great deal more, and the
 * client is configured to ignore what is not declared here, so new fields on
 * their side do not break parsing on ours.
 */

@Serializable
data class AccountStatusDto(
    val account: AccountDto = AccountDto(),
    val plus: PlusDto? = null,
)

@Serializable
data class AccountDto(
    val uid: Long = 0,
    val login: String = "",
    val fullName: String = "",
    val displayName: String = "",
    @SerialName("defaultEmail") val email: String = "",
)

@Serializable
data class PlusDto(val hasPlus: Boolean = false)

@Serializable
data class ArtistDto(
    val id: Long = 0,
    val name: String = "",
    val cover: CoverDto? = null,
    val genres: List<String> = emptyList(),
    val counts: ArtistCountsDto? = null,
    val likesCount: Int? = null,
) {
    fun coverUrl(size: String = "300x300"): String? =
        cover?.uri?.let { "https://${it.replace("%%", size)}" }
}

@Serializable
data class ArtistCountsDto(
    val tracks: Int = 0,
    val directAlbums: Int = 0,
    val alsoAlbums: Int = 0,
)

@Serializable
data class AlbumDto(
    val id: Long = 0,
    val title: String = "",
    val year: Int? = null,
    val coverUri: String? = null,
    val trackCount: Int = 0,
    val genre: String? = null,
    val type: String? = null,
    val releaseDate: String? = null,
    val likesCount: Int = 0,
    val artists: List<ArtistDto> = emptyList(),
    val labels: List<LabelDto> = emptyList(),
    val volumes: List<List<TrackDto>> = emptyList(),
) {
    fun coverUrl(size: String = "300x300"): String? =
        coverUri?.let { "https://${it.replace("%%", size)}" }

    val artistLine: String get() = artists.joinToString(", ") { it.name }
}

@Serializable
data class LabelDto(val id: Long = 0, val name: String = "")

/** A track as the library lists it: just the ids, and when it was added. */
@Serializable
data class TrackRefDto(
    val id: String = "",
    val albumId: String? = null,
    val timestamp: String? = null,
)

@Serializable
data class LibraryDto(val library: LibraryTracksDto = LibraryTracksDto())

@Serializable
data class LibraryTracksDto(
    val uid: Long = 0,
    val revision: Int = 0,
    val tracks: List<TrackRefDto> = emptyList(),
)

/** What `/artists/{id}/brief-info` returns, trimmed to what the artist page shows. */
@Serializable
data class ArtistBriefDto(
    val artist: ArtistDto = ArtistDto(),
    val albums: List<AlbumDto> = emptyList(),
    val alsoAlbums: List<AlbumDto> = emptyList(),
    val popularTracks: List<TrackDto> = emptyList(),
    val similarArtists: List<ArtistDto> = emptyList(),
    val lastReleases: List<AlbumDto> = emptyList(),
    val playlists: List<PlaylistDto> = emptyList(),
    val stats: ArtistStatsDto? = null,
)

@Serializable
data class ArtistStatsDto(
    val lastMonthListeners: Int = 0,
)

@Serializable
data class UploadTargetDto(
    @SerialName("post-target") val postTarget: String = "",
    @SerialName("ugc-track-id") val trackId: String = "",
)

@Serializable
data class ChartItemDto(
    val track: TrackDto? = null,
    val chart: ChartPositionDto? = null,
)

@Serializable
data class ChartPositionDto(
    val position: Int = 0,
    val progress: String? = null,
    val listeners: Int = 0,
)

@Serializable
data class CoverDto(
    val uri: String? = null,
    val type: String? = null,
    val itemsUri: List<String> = emptyList(),
)

@Serializable
data class TrackDto(
    val id: String = "",
    val title: String = "",
    val version: String? = null,
    val available: Boolean = true,
    val durationMs: Long = 0,
    val coverUri: String? = null,
    val explicit: Boolean? = null,
    val lyricsAvailable: Boolean? = null,
    val artists: List<ArtistDto> = emptyList(),
    val albums: List<AlbumDto> = emptyList(),
) {
    /**
     * Cover uris come with a `%%` where the size belongs, e.g.
     * `avatars.yandex.net/get-music-content/…/%%`.
     */
    fun coverUrl(size: String = "300x300"): String? =
        coverUri?.let { "https://${it.replace("%%", size)}" }
}

/** A playlist entry: the track, or just its id when the payload is trimmed. */
@Serializable
data class PlaylistTrackDto(
    val id: String = "",
    val albumId: String? = null,
    val track: TrackDto? = null,
)

@Serializable
data class OwnerDto(
    val uid: Long = 0,
    val login: String = "",
    val name: String = "",
)

@Serializable
data class PlaylistDto(
    val kind: Long = 0,
    val uid: Long = 0,
    val title: String = "",
    val description: String? = null,
    val trackCount: Int = 0,
    val revision: Int = 0,
    val visibility: String? = null,
    val playlistUuid: String? = null,
    val owner: OwnerDto = OwnerDto(),
    val cover: CoverDto? = null,
    val ogImage: String? = null,
    val tracks: List<PlaylistTrackDto> = emptyList(),
) {
    /** The owner's id, wherever this response put it. */
    val ownerUid: Long get() = owner.uid.takeIf { it != 0L } ?: uid

    /** The "Liked" playlist, which Yandex keeps as kind 3 on every account. */
    val isLikes: Boolean get() = kind == LIKES_KIND

    companion object {
        const val LIKES_KIND = 3L
    }

    fun coverUrl(size: String = "300x300"): String? =
        cover?.uri?.let { "https://${it.replace("%%", size)}" }
            ?: cover?.itemsUri?.firstOrNull()?.let { "https://${it.replace("%%", size)}" }
}

@Serializable
data class DownloadInfoDto(
    @SerialName("downloadInfo") val info: DownloadUrlsDto = DownloadUrlsDto(),
)

@Serializable
data class DownloadUrlsDto(
    val url: String? = null,
    val urls: List<String> = emptyList(),
    val quality: String? = null,
    val codec: String? = null,
    val bitrate: Int = 0,
) {
    /** `url` first, then the alternates, as the Dart build ordered them. */
    val best: String? get() = url ?: urls.firstOrNull()
}

@Serializable
data class LyricsDto(
    val downloadUrl: String = "",
    val lyricId: String? = null,
    val writers: List<String> = emptyList(),
)

@Serializable
data class SearchResultDto(
    val text: String = "",
    val tracks: SearchSectionDto<TrackDto>? = null,
    val albums: SearchSectionDto<AlbumDto>? = null,
    val artists: SearchSectionDto<ArtistDto>? = null,
    val playlists: SearchSectionDto<PlaylistDto>? = null,
)

@Serializable
data class SearchSectionDto<T>(
    val total: Int = 0,
    val results: List<T> = emptyList(),
)

@Serializable
data class RotorSessionDto(
    @SerialName("radioSessionId") val sessionId: String = "",
    val batchId: String? = null,
    val sequence: List<RotorSequenceItemDto> = emptyList(),
)

@Serializable
data class RotorSequenceItemDto(
    val type: String = "track",
    val track: TrackDto? = null,
    val liked: Boolean = false,
)

@Serializable
data class RotorBatchDto(
    val batchId: String? = null,
    val sequence: List<RotorSequenceItemDto> = emptyList(),
)
