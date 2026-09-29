package com.quark.app.browse

import com.quark.app.integrations.Integrations
import com.quark.app.library.UserLibrary
import com.quark.core.model.PlaylistId
import com.quark.core.model.PlaylistSource
import com.quark.core.model.Track
import com.quark.core.settings.SettingsStore
import com.quark.data.repository.TrackRepository
import com.quark.network.soundcloud.ScPlaylist
import com.quark.network.spotify.SpotifyPlaylist
import com.quark.network.vk.VkPlaylist
import com.quark.network.ytmusic.YtPlaylist
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * The other services as collections, each the way its Dart widget listed it:
 * Spotify's playlists with Liked Songs first (`spotify_playlists_widget.dart`),
 * a SoundCloud profile's playlists (`soundcloud_playlists_widget.dart`), VK's
 * own tracks and playlists (`vkmusic_playlist_widget.dart`), and the YouTube
 * playlists behind a cookie file (`ytmusic_playlist_widget.dart`).
 */
class ServiceCatalogs(
    private val integrations: Integrations,
    private val settings: SettingsStore,
    private val known: TrackRepository,
    private val cacheRoot: String,
    private val separator: String,
) {
    // --- Spotify -----------------------------------------------------------------

    suspend fun spotifyPlaylists(): List<CollectionSummary> =
        integrations.spotify.playlists().map { playlist -> playlist.summary() }

    private fun SpotifyPlaylist.summary() = CollectionSummary(
        key = "spotify:$id",
        title = name,
        subtitle = owner,
        coverUrl = cover,
        trackCount = trackCount,
        kind = if (isLiked) CollectionKind.Liked else CollectionKind.Playlist,
        open = { spotifyPlaylist(this) },
    )

    suspend fun spotifyPlaylist(playlist: SpotifyPlaylist): TrackCollection {
        val tracks = integrations.spotify.playlistTracks(playlist).map { it.toTrack(cacheRoot, separator) }
        remember(tracks)
        return TrackCollection(
            key = "spotify:${playlist.id}",
            title = playlist.name,
            subtitle = playlist.owner,
            coverUrl = playlist.cover,
            tracks = tracks,
            playlistId = PlaylistId(0, playlist.id.hashCode().toLong(), PlaylistSource.Spotify),
            kind = if (playlist.isLiked) CollectionKind.Liked else CollectionKind.Playlist,
        )
    }

    // --- SoundCloud --------------------------------------------------------------

    /** The playlists of the profile at [profileUrl], remembered for next time. */
    suspend fun soundCloudProfile(profileUrl: String): Pair<String, List<CollectionSummary>> {
        val user = integrations.soundCloud.resolveUser(profileUrl)
            ?: throw IllegalArgumentException("Could not find a SoundCloud profile at that link")
        settings.update { it.copy(soundCloud = it.soundCloud.copy(profileUrl = profileUrl.trim())) }
        val playlists = integrations.soundCloud.userPlaylists(user.id).map { it.summary() }
        val liked = CollectionSummary(
            key = "soundcloud:likes:${user.id}",
            title = "Likes",
            subtitle = user.username,
            coverUrl = user.avatarUrl,
            kind = CollectionKind.Liked,
            open = { soundCloudTracks("soundcloud:likes:${user.id}", "${user.username} — likes", user.avatarUrl) {
                integrations.soundCloud.userLikes(user.id)
            } },
        )
        val uploads = CollectionSummary(
            key = "soundcloud:tracks:${user.id}",
            title = "Tracks",
            subtitle = user.username,
            coverUrl = user.avatarUrl,
            open = { soundCloudTracks("soundcloud:tracks:${user.id}", user.username, user.avatarUrl) {
                integrations.soundCloud.userTracks(user.id)
            } },
        )
        return user.username to (listOf(liked, uploads) + playlists)
    }

    suspend fun soundCloudSearchPlaylists(query: String): List<CollectionSummary> =
        integrations.soundCloud.searchPlaylists(query).map { it.summary() }

    private fun ScPlaylist.summary() = CollectionSummary(
        key = "soundcloud:playlist:$id",
        title = title,
        subtitle = owner,
        coverUrl = artworkUrl,
        trackCount = trackCount,
        kind = if (isAlbum) CollectionKind.Album else CollectionKind.Playlist,
        open = { soundCloudTracks("soundcloud:playlist:$id", title, artworkUrl) { integrations.soundCloud.playlistTracks(id) } },
    )

    private suspend fun soundCloudTracks(
        key: String,
        title: String,
        cover: String?,
        load: suspend () -> List<com.quark.network.soundcloud.ScTrack>,
    ): TrackCollection {
        val tracks = load().map { it.toTrack(cacheRoot, separator) }
        remember(tracks)
        return TrackCollection(
            key = key,
            title = title,
            coverUrl = cover,
            tracks = tracks,
            playlistId = PlaylistId(0, key.hashCode().toLong(), PlaylistSource.SoundCloud),
        )
    }

    // --- VK ------------------------------------------------------------------------

    suspend fun vkLibrary(): List<CollectionSummary> {
        val mine = CollectionSummary(
            key = "vk:my",
            title = "My music",
            kind = CollectionKind.Liked,
            open = { vkTracks("vk:my", "My music", null) { integrations.vk.mySongs() } },
        )
        val popular = CollectionSummary(
            key = "vk:popular",
            title = "Popular",
            kind = CollectionKind.Chart,
            open = { vkTracks("vk:popular", "Popular", null) { integrations.vk.popular() } },
        )
        val playlists = runCatching { integrations.vk.myPlaylists() }.getOrDefault(emptyList()).map { it.summary() }
        return listOf(mine, popular) + playlists
    }

    private fun VkPlaylist.summary() = CollectionSummary(
        key = "vk:playlist:$id",
        title = title,
        subtitle = description,
        coverUrl = photo,
        trackCount = count,
        open = { vkTracks("vk:playlist:$id", title, photo) { integrations.vk.playlistSongs(this) } },
    )

    private suspend fun vkTracks(
        key: String,
        title: String,
        cover: String?,
        load: suspend () -> List<com.quark.network.vk.VkSong>,
    ): TrackCollection {
        val tracks = load().filter { it.isPlayable }.map { it.toTrack(cacheRoot, separator) }
        remember(tracks)
        return TrackCollection(
            key = key,
            title = title,
            coverUrl = cover,
            tracks = tracks,
            playlistId = PlaylistId(0, key.hashCode().toLong(), PlaylistSource.Vk),
        )
    }

    // --- YouTube -------------------------------------------------------------------

    suspend fun youtubePlaylists(): List<CollectionSummary> {
        val cookies = settings.current.youtube.cookies
        require(cookies.isNotBlank()) { "Add a cookie file first" }
        return integrations.youtube.playlists(cookies).map { it.summary(cookies) }
    }

    private fun YtPlaylist.summary(cookies: String) = CollectionSummary(
        key = "youtube:$id",
        title = title,
        coverUrl = thumbnail,
        open = {
            val contents = integrations.youtube.playlist(cookies, id)
            val tracks = contents.videos.filter { it.isPlayable }.map { it.toTrack(cacheRoot, separator) }
            remember(tracks)
            TrackCollection(
                key = "youtube:$id",
                title = contents.title ?: title,
                coverUrl = thumbnail,
                tracks = tracks,
                playlistId = PlaylistId(0, id.hashCode().toLong(), PlaylistSource.YouTube),
            )
        },
    )

    private suspend fun remember(tracks: List<Track>) {
        runCatching { known.remember(tracks, downloaded = false) }
    }
}

/**
 * Search across every service the user has, each in parallel and each allowed
 * to fail on its own (`multi_search.dart`). Only services that are signed in
 * and switched on in the settings take part.
 */
class MultiSearch(
    private val integrations: Integrations,
    private val yandex: YandexCatalog,
    private val library: UserLibrary,
    private val settings: SettingsStore,
    private val yandexSignedIn: () -> Boolean,
    private val cacheRoot: String,
    private val separator: String,
) {
    fun available(): List<SearchService> {
        val current = settings.current
        return buildList {
            add(SearchService.Local)
            if (yandexSignedIn() && current.yandex.searchEnabled) add(SearchService.Yandex)
            if (current.youtube.searchEnabled) add(SearchService.YouTube)
            if (current.soundCloud.searchEnabled) add(SearchService.SoundCloud)
            if (current.spotify.searchEnabled) add(SearchService.Spotify)
            if (current.account.isSignedIn && current.vk.isConnected && current.vk.searchEnabled) add(SearchService.Vk)
        }
    }

    suspend fun search(query: String, services: List<SearchService> = available()): List<SearchSection> = coroutineScope {
        services.map { service -> async { one(service, query) } }.map { it.await() }
    }

    suspend fun one(service: SearchService, query: String): SearchSection = try {
        when (service) {
            SearchService.Local -> SearchSection(service, tracks = library.search(query))
            SearchService.Yandex -> yandex.search(query)
            SearchService.YouTube -> SearchSection(
                service,
                tracks = integrations.youtube.search(query).map { it.toTrack(cacheRoot, separator) },
            )
            SearchService.SoundCloud -> SearchSection(
                service,
                tracks = integrations.soundCloud.searchTracks(query).map { it.toTrack(cacheRoot, separator) },
            )
            SearchService.Spotify -> SearchSection(
                service,
                tracks = integrations.spotify.search(query).map { it.toTrack(cacheRoot, separator) },
            )
            SearchService.Vk -> SearchSection(
                service,
                tracks = integrations.vk.search(query).filter { it.isPlayable }.map { it.toTrack(cacheRoot, separator) },
            )
        }
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        SearchSection(service, error = e.message ?: "Search failed")
    }
}
