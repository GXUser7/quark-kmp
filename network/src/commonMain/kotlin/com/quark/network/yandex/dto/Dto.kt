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
)

@Serializable
data class AlbumDto(
    val id: Long = 0,
    val title: String = "",
    val year: Int? = null,
    val coverUri: String? = null,
    val trackCount: Int = 0,
    val artists: List<ArtistDto> = emptyList(),
    val volumes: List<List<TrackDto>> = emptyList(),
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
    val available: Boolean = true,
    val durationMs: Long = 0,
    val coverUri: String? = null,
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
    val title: String = "",
    val description: String? = null,
    val trackCount: Int = 0,
    val revision: Int = 0,
    val owner: OwnerDto = OwnerDto(),
    val cover: CoverDto? = null,
    val tracks: List<PlaylistTrackDto> = emptyList(),
) {
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
