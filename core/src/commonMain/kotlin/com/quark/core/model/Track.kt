package com.quark.core.model

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

/** Where the audio bytes come from once a track is about to be played. */
enum class PlaySourceType { LocalFile, Url }

/** A resolved, immediately playable location. Network urls are short-lived. */
data class PlayableInfo(val type: PlaySourceType, val path: String)

@Serializable
enum class TrackSource(val value: String) {
    Local("local"),
    YandexMusic("yandex_music"),
    YouTubeMusic("youtube"),
    Spotify("spotify"),
    SoundCloud("soundcloud"),
    Vk("vkmusic");

    companion object {
        fun parse(value: String?): TrackSource =
            entries.firstOrNull { it.value == value } ?: Local
    }
}

/**
 * A track as the player and the UI know it.
 *
 * Unlike the Dart original, resolving a playable url, album or artist is not a
 * method on the model: those reach into network services and would drag the
 * whole api layer into the domain. They live in the corresponding repository
 * instead, keyed off [source].
 */
@Serializable
sealed interface Track {
    val title: String
    val artists: List<String>
    val albums: List<String>

    /**
     * Local tracks: the real path on disk. Remote tracks: the path the file
     * would occupy in the cache directory, which is also what identifies it.
     */
    val filepath: String
    val cover: String
    val coverType: CoverType
    val source: TrackSource

    /** Zero when the source did not say; the engine reports the real length. */
    val durationMs: Long

    val artistLine: String get() = artists.joinToString(", ")
    val albumLine: String get() = albums.firstOrNull() ?: UNKNOWN_ALBUM

    companion object {
        const val UNKNOWN_TITLE = "Unknown"
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"

        val Dummy: Track = LocalTrack(
            title = UNKNOWN_TITLE,
            artists = listOf(UNKNOWN_ARTIST),
            albums = listOf(UNKNOWN_ALBUM),
            filepath = "",
            coverType = CoverType.NoCover,
        )
    }
}

@Serializable
data class LocalTrack(
    override val title: String,
    override val artists: List<String>,
    override val albums: List<String>,
    override val filepath: String,
    override val coverType: CoverType,
    override val cover: String = NO_COVER,
    override val durationMs: Long = 0,
) : Track {
    override val source: TrackSource get() = TrackSource.Local

    companion object {
        const val NO_COVER = "none"
    }
}

@Serializable
data class YandexTrack(
    override val title: String,
    override val artists: List<String>,
    override val albums: List<String>,
    override val filepath: String,
    override val coverType: CoverType,
    override val cover: String = "",
    val trackId: String,
    val albumId: Long? = null,
    val artistIds: List<Long> = emptyList(),
    override val durationMs: Long = 0,
    val available: Boolean = true,
    /**
     * The untouched api payload. The Dart version keeps it to round-trip a
     * playlist through the settings store without re-querying, and the local
     * api hands parts of it to clients, so dropping it would be a behaviour
     * change.
     */
    val raw: JsonObject? = null,
) : Track {
    override val source: TrackSource get() = TrackSource.YandexMusic
}

@Serializable
data class YtMusicTrack(
    override val title: String,
    override val artists: List<String>,
    override val albums: List<String>,
    override val filepath: String,
    override val coverType: CoverType,
    override val cover: String = "",
    val videoId: String,
    override val durationMs: Long = 0,
    /** Direct media url handed out by the backend; expires, so it is re-resolved. */
    val streamUrl: String? = null,
) : Track {
    override val source: TrackSource get() = TrackSource.YouTubeMusic
}

/**
 * A track from one of the streaming services that need nothing beyond an id to
 * find their audio again: Spotify, SoundCloud and VK.
 *
 * One class for all three rather than one each, because the only difference is
 * which resolver answers for them, and that is keyed off [source].
 */
@Serializable
data class ServiceTrack(
    override val title: String,
    override val artists: List<String>,
    override val albums: List<String>,
    override val filepath: String,
    override val coverType: CoverType,
    override val source: TrackSource,
    /** The service's own id for the track. */
    val id: String,
    override val cover: String = "",
    override val durationMs: Long = 0,
    /** What the resolver needs besides the id: a permalink, an access key, an ISRC. */
    val extras: Map<String, String> = emptyMap(),
) : Track
