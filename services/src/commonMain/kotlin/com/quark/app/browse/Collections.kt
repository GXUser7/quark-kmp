package com.quark.app.browse

import com.quark.core.model.PlaylistId
import com.quark.core.model.PlaylistSource
import com.quark.core.model.Track

/** What kind of list a collection is, for its header and its menu. */
enum class CollectionKind { Playlist, Album, Liked, Chart, Station, Search, Artist }

/**
 * A list of tracks the interface can show and play — a playlist on any
 * service, an album, the liked tracks, the chart. One shape for all of them is
 * what lets one screen stand in for the half a dozen near-identical playlist
 * widgets of the Dart build.
 */
data class TrackCollection(
    val key: String,
    val title: String,
    val tracks: List<Track>,
    val playlistId: PlaylistId,
    val subtitle: String? = null,
    val description: String? = null,
    val coverUrl: String? = null,
    val kind: CollectionKind = CollectionKind.Playlist,
    /** Set for the user's own playlists, which can be edited here. */
    val userPlaylistId: Long? = null,
    /** Set for the user's own Yandex playlists, which can be edited on Yandex. */
    val yandexKind: Long? = null,
    val yandexRevision: Int? = null,
    /** An album's artists, which the header links to. */
    val artistIds: List<Pair<Long, String>> = emptyList(),
) {
    val source: PlaylistSource get() = playlistId.source
    val isEditable: Boolean get() = userPlaylistId != null
}

/**
 * A collection before it is opened: enough for a grid tile. [open] fetches the
 * tracks, which for a remote playlist is a network call.
 */
data class CollectionSummary(
    val key: String,
    val title: String,
    val subtitle: String? = null,
    val coverUrl: String? = null,
    val trackCount: Int? = null,
    val kind: CollectionKind = CollectionKind.Playlist,
    val open: suspend () -> TrackCollection,
)

/** An artist's page: who, their best tracks, their records. */
data class ArtistPage(
    val id: Long,
    val name: String,
    val coverUrl: String?,
    val genres: List<String>,
    val popular: List<Track>,
    val albums: List<CollectionSummary>,
    val alsoAlbums: List<CollectionSummary>,
    val similar: List<ArtistSummary>,
)

data class ArtistSummary(val id: Long, val name: String, val coverUrl: String?)

/** Results from one service for one query. */
data class SearchSection(
    val service: SearchService,
    val tracks: List<Track> = emptyList(),
    val albums: List<CollectionSummary> = emptyList(),
    val playlists: List<CollectionSummary> = emptyList(),
    val artists: List<ArtistSummary> = emptyList(),
    val error: String? = null,
)

enum class SearchService { Local, Yandex, YouTube, SoundCloud, Spotify, Vk }
