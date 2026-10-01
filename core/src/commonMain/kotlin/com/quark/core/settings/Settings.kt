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
    /** 64 kbps AAC, for metered connections. */
    Low("lq"),
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
    val spotify: SpotifySettings = SpotifySettings(),
    val soundCloud: SoundCloudSettings = SoundCloudSettings(),
    val vk: VkSettings = VkSettings(),
    val youtube: YouTubeSettings = YouTubeSettings(),
    val account: AccountSettings = AccountSettings(),
    val memory: PlaybackMemory = PlaybackMemory(),
)

/** Light or dark interface; [System] follows the operating system. */
@Serializable
enum class ThemeMode { System, Dark, Light }

@Serializable
data class PlaybackSettings(
    val volume: Float = 0.7f,
    val backend: AudioBackend = AudioBackend.Mpv,

    /** Fetch the next track's stream url before it is needed. */
    val prefetchNext: Boolean = false,

    /** Keep played remote tracks on disk so replays are instant. */
    val cacheRemoteTracks: Boolean = false,

    /** Playback speed, remembered across tracks and restarts. */
    val speed: Float = 1f,
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

    /** Interface language code, or null to follow the system. */
    val language: String? = null,

    val theme: ThemeMode = ThemeMode.Dark,
)

@Serializable
data class IntegrationSettings(
    val discordRpc: Boolean = false,

    /** The local HTTP/WebSocket control api. */
    val localApi: Boolean = false,

    /**
     * Let the local api answer the whole network and announce it over mDNS,
     * rather than only this machine. Off by default: the api has no password.
     */
    val localApiLan: Boolean = false,

    /** The system media controls: SMTC on Windows, media keys, the Android notification. */
    val nativeControls: Boolean = true,
)

/** The quark account at quarkaudio.ru, which syncs playlists and fronts VK. */
@Serializable
data class AccountSettings(
    val accessToken: String = "",
    val refreshToken: String = "",
    val username: String = "",
    val email: String = "",
    /** Upload and download playlists on sign-in and at startup. */
    val syncPlaylists: Boolean = true,
) {
    val isSignedIn: Boolean get() = accessToken.isNotEmpty()
}

@Serializable
data class SpotifySettings(
    val accessToken: String = "",
    val refreshToken: String = "",
    /** `hires`, `lossless` or `high`, as the Dart build stored it. */
    val quality: String = "high",
    val searchEnabled: Boolean = true,
) {
    val isSignedIn: Boolean get() = refreshToken.isNotEmpty()
}

@Serializable
data class SoundCloudSettings(
    /** The user's OAuth token, for full-length Go+ tracks. Optional. */
    val oauthToken: String = "",
    /** The profile last opened, so the playlists are one click away. */
    val profileUrl: String = "",
    val searchEnabled: Boolean = true,
)

@Serializable
data class VkSettings(
    val userId: String = "",
    val searchEnabled: Boolean = true,
) {
    val isConnected: Boolean get() = userId.isNotEmpty()
}

@Serializable
data class YouTubeSettings(
    /** The Netscape cookie file exported from a signed-in browser, verbatim. */
    val cookies: String = "",
    val searchEnabled: Boolean = true,
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
