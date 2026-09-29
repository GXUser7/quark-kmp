package com.quark.core.settings

import com.quark.core.model.PlaylistId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Which native backend plays the audio. */
@Serializable
enum class AudioBackend {
    /** libmpv. What the Flutter build used in its "Just Audio MK" mode. */
    Mpv,

    /** libvlc, kept as the fallback if mpv misbehaves on a given machine. */
    Vlc,
}

/** Preferred stream quality for Yandex Music, in the api's own vocabulary. */
@Serializable
enum class StreamQuality(val apiValue: String) {
    Normal("nq"),
    High("hq"),
    Lossless("lossless");

    companion object {
        fun parse(value: String?): StreamQuality =
            entries.firstOrNull { it.apiValue == value } ?: Normal
    }
}

/**
 * Everything the Flutter build kept in 38 separate `ValueNotifier`s on
 * `DatabaseStreamerService`, as one value.
 *
 * Saving is all-or-nothing, which is the point: the Dart version attached a
 * listener per field and wrote each one on its own, so a crash mid-change could
 * leave the settings half-updated.
 */
@Serializable
data class Settings(
    val playback: PlaybackSettings = PlaybackSettings(),
    val library: LibrarySettings = LibrarySettings(),
    val appearance: AppearanceSettings = AppearanceSettings(),
    val integrations: IntegrationSettings = IntegrationSettings(),
    val yandex: YandexSettings = YandexSettings(),
    val memory: PlaybackMemory = PlaybackMemory(),
)

@Serializable
data class PlaybackSettings(
    val volume: Float = 0.7f,
    val backend: AudioBackend = AudioBackend.Mpv,

    /** Fetch the next track's stream url before it is needed. */
    val prefetchNext: Boolean = false,

    /** Keep played remote tracks on disk so replays are instant. */
    val cacheRemoteTracks: Boolean = false,
)

@Serializable
data class LibrarySettings(
    /** Descend into subfolders when a folder is added. */
    val recursiveFolderAdding: Boolean = true,

    /** Watch added folders and pick up files appearing in them. */
    val watchFolders: Boolean = true,

    /** Roots selected through "Add folder", retained so watching survives restart. */
    val watchedFolderPaths: List<String> = emptyList(),

    val logListens: Boolean = false,

    /** Group playlists into categories in the sidebar. */
    val categories: Boolean = true,

    /** Switching category also switches what is playing. */
    val categorySwitchesPlaylist: Boolean = false,
)

@Serializable
data class AppearanceSettings(
    /** Tint the window chrome with colours taken from the cover. */
    val dynamicWindowColor: Boolean = true,

    val gradientBackground: Boolean = false,

    /** Multiplies every animation's duration; 1.0 is as designed. */
    val transitionSpeed: Float = 1f,

    /** Show the small playback-state indicator. */
    val stateIndicator: Boolean = true,

    /** Open covers at their original size instead of a downscaled copy. */
    val originalSizeCovers: Boolean = false,

    /** Keep the playlist panel open across restarts. */
    val playlistPanelOpen: Boolean = false,

    /** Hovering the edge of the window opens the playlist panel. */
    val playlistOpeningArea: Boolean = false,
)

@Serializable
data class IntegrationSettings(
    val discordRpc: Boolean = false,

    /** The local HTTP/WebSocket control api, and its mDNS announcement. */
    val localApi: Boolean = false,
)

@Serializable
data class YandexSettings(
    val token: String = "",
    val tokenExpiresAt: Long = 0,
    val uid: Long? = null,
    val login: String = "",
    val displayName: String = "",
    val fullName: String = "",
    val email: String = "",
    val quality: StreamQuality = StreamQuality.Normal,

    /** Offer Yandex results alongside local ones when searching. */
    val searchEnabled: Boolean = true,

    /** Load the user's playlists at startup rather than on first open. */
    val preloadPlaylists: Boolean = true,

    /** Re-read a playlist from the api instead of trusting the cached copy. */
    val alwaysRefreshPlaylists: Boolean = false,
) {
    val isAuthorised: Boolean get() = token.isNotEmpty()
}

/** What to restore on the next start. */
@Serializable
data class PlaybackMemory(
    @SerialName("last_track_path")
    val lastTrackPath: String? = null,
    val lastPositionSeconds: Int = 0,
    val lastPlaylist: PlaylistId? = null,
    val lastPlaylistName: String? = null,
    /** SQLite snapshot used to restore any source, including while offline. */
    val lastPlaylistStorageId: Long? = null,
)
