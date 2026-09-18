package com.quark.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class PlaylistSource(val value: String) {
    Local("local"),
    YandexMusic("yandex_music"),
    Spotify("spotify"),
    YouTube("youtube");

    companion object {
        fun parse(value: String?): PlaylistSource =
            entries.firstOrNull { it.value == value } ?: Local
    }
}

/** Identifies a playlist at its source; [kind] and [ownerUid] are Yandex's pair. */
@Serializable
data class PlaylistId(
    val ownerUid: Long = 0,
    val kind: Long = 0,
    val source: PlaylistSource = PlaylistSource.Local,
) {
    companion object {
        val Local = PlaylistId()
    }
}

/** A playlist with its tracks loaded. Restored from disk on startup. */
@Serializable
data class Playlist(
    val id: PlaylistId = PlaylistId.Local,
    val name: String = "Local",
    val tracks: List<Track> = emptyList(),
) {
    val source: PlaylistSource get() = id.source
    val size: Int get() = tracks.size
}

/** Playlist metadata without the tracks, for lists and the now-playing header. */
@Serializable
data class PlaylistInfo(
    val id: PlaylistId = PlaylistId.Local,
    val name: String = "Local",
) {
    companion object {
        fun of(playlist: Playlist) = PlaylistInfo(playlist.id, playlist.name)
    }
}
