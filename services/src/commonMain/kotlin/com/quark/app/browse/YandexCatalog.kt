package com.quark.app.browse

import com.quark.app.yandex.YandexSession
import com.quark.core.model.PlaylistId
import com.quark.core.model.PlaylistSource
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import com.quark.data.repository.TrackRepository
import com.quark.network.yandex.YandexMusic
import com.quark.network.yandex.dto.AlbumDto
import com.quark.network.yandex.dto.ArtistDto
import com.quark.network.yandex.dto.PlaylistDto
import com.quark.network.yandex.dto.TrackDto
import com.quark.network.yandex.toTrack

/**
 * Yandex Music as collections: the user's playlists with Liked first, albums,
 * artists, the chart, new releases and search (`yandex_widgets.dart`,
 * `media_cards/` on main). Every track that comes through is recorded in the
 * library, so it can be added to the user's playlists and counted in the
 * statistics like any other.
 */
class YandexCatalog(
    private val session: YandexSession,
    private val known: TrackRepository,
) {
    private val api: YandexMusic get() = session.api ?: throw IllegalStateException("Sign in to Yandex Music first")

    /** Set by the interface, so Liked and Chart are named in its language. */
    var labels: CatalogLabels = CatalogLabels()

    private fun TrackDto.track(): YandexTrack = toTrack(session.cacheRoot, session.separator)

    suspend fun playlists(): List<CollectionSummary> =
        api.playlistsWithLikes().map { it.summary() }

    suspend fun playlist(ownerUid: Long, kind: Long): TrackCollection {
        val dto = api.playlist(kind = kind, userId = ownerUid)
        val expanded = dto.tracks.mapNotNull { it.track }.associateBy(TrackDto::id)
        val missing = dto.tracks.filter { it.track == null }.map { it.id }.filter(String::isNotEmpty)
        val fetched = missing.chunked(BATCH).flatMap { api.tracks(it) }.associateBy(TrackDto::id)
        val tracks = dto.tracks.mapNotNull { entry -> (expanded[entry.id] ?: fetched[entry.id])?.track() }
            .filter(YandexTrack::available)
        remember(tracks)
        return TrackCollection(
            key = "yandex:playlist:$ownerUid:$kind",
            title = if (dto.isLikes) labels.liked else dto.title,
            subtitle = dto.owner.name.ifBlank { dto.owner.login }.ifBlank { null },
            description = dto.description,
            coverUrl = dto.coverUrl(COVER),
            tracks = tracks,
            playlistId = PlaylistId(ownerUid, kind, PlaylistSource.YandexMusic),
            kind = if (dto.isLikes) CollectionKind.Liked else CollectionKind.Playlist,
            yandexKind = kind.takeIf { ownerUid == account() && !dto.isLikes },
            yandexRevision = dto.revision,
            link = "https://music.yandex.ru/users/${dto.owner.login.ifBlank { ownerUid.toString() }}/playlists/$kind",
            isPublic = dto.visibility?.let { it == "public" }?.takeIf { ownerUid == account() && !dto.isLikes },
        )
    }

    suspend fun liked(): TrackCollection = playlist(account(), PlaylistDto.LIKES_KIND)

    suspend fun album(id: Long): TrackCollection {
        val album = api.album(id, withTracks = true)
        val tracks = album.volumes.flatten().map { it.track() }.filter(YandexTrack::available)
        remember(tracks)
        return TrackCollection(
            key = "yandex:album:$id",
            title = album.title,
            subtitle = listOfNotNull(album.artistLine.ifBlank { null }, album.year?.toString()).joinToString(" · "),
            coverUrl = album.coverUrl(COVER),
            tracks = tracks,
            playlistId = PlaylistId(0, id, PlaylistSource.YandexMusic),
            kind = CollectionKind.Album,
            artistIds = album.artists.map { it.id to it.name },
            link = "https://music.yandex.ru/album/$id",
        )
    }

    suspend fun artist(id: Long): ArtistPage {
        val brief = api.artistBrief(id)
        val popular = brief.popularTracks.map { it.track() }.filter(YandexTrack::available)
        remember(popular)
        return ArtistPage(
            id = id,
            name = brief.artist.name,
            coverUrl = brief.artist.coverUrl(COVER),
            genres = brief.artist.genres,
            popular = popular,
            albums = brief.albums.map { it.summary() },
            alsoAlbums = brief.alsoAlbums.map { it.summary() },
            similar = brief.similarArtists.map { it.summary() },
            playlists = brief.playlists.map { it.summary() },
            likes = brief.artist.likesCount,
            monthlyListeners = brief.stats?.lastMonthListeners?.takeIf { it > 0 },
            link = "https://music.yandex.ru/artist/$id",
        )
    }

    suspend fun artistTracks(id: Long, name: String): TrackCollection {
        val tracks = api.artistTracks(id, pageSize = 100).map { it.track() }.filter(YandexTrack::available)
        remember(tracks)
        return TrackCollection(
            key = "yandex:artist:$id",
            title = name,
            tracks = tracks,
            playlistId = PlaylistId(0, id, PlaylistSource.YandexMusic),
            kind = CollectionKind.Artist,
        )
    }

    suspend fun chart(): TrackCollection {
        val tracks = api.chart().map { it.track() }.filter(YandexTrack::available)
        remember(tracks)
        return TrackCollection(
            key = "yandex:chart",
            title = labels.chart,
            tracks = tracks,
            playlistId = PlaylistId(0, CHART_KIND, PlaylistSource.YandexMusic),
            kind = CollectionKind.Chart,
        )
    }

    suspend fun newReleases(): List<CollectionSummary> = api.newReleases().map { it.summary() }

    suspend fun similar(track: YandexTrack): List<Track> =
        api.similarTracks(track.trackId).map { it.track() }.filter(YandexTrack::available)

    suspend fun search(query: String): SearchSection {
        val result = api.search(query)
        val tracks = result.tracks?.results.orEmpty().map { it.track() }.filter(YandexTrack::available)
        remember(tracks)
        return SearchSection(
            service = SearchService.Yandex,
            tracks = tracks,
            albums = result.albums?.results.orEmpty().map { it.summary() },
            playlists = result.playlists?.results.orEmpty().map { it.summary() },
            artists = result.artists?.results.orEmpty().map { it.summary() },
        )
    }

    /** The user's own playlists, which tracks can be added to; Liked is not one. */
    suspend fun editablePlaylists(): List<EditablePlaylist> {
        val me = account()
        return api.playlistsWithLikes()
            .filter { !it.isLikes && it.ownerUid == me }
            .map { EditablePlaylist(it.kind, it.title) }
    }

    /**
     * Adds [tracks] to the user's Yandex playlist [kind], at its top as the app
     * does. The playlist is read first for its revision: edits against a stale
     * one are refused.
     */
    suspend fun addToPlaylist(kind: Long, tracks: List<YandexTrack>) {
        val revision = api.playlist(kind = kind, userId = account()).revision
        api.insertTracks(kind, revision, tracks.map { it.trackId to it.albumId })
    }

    /** Removes the entry of [track] from the user's playlist [kind]. */
    suspend fun removeFromPlaylist(kind: Long, track: YandexTrack) {
        val dto = api.playlist(kind = kind, userId = account())
        val index = dto.tracks.indexOfFirst { it.id == track.trackId || it.id.substringBefore(':') == track.trackId }
        if (index < 0) return
        api.deleteTracks(kind, dto.revision, index, index + 1)
    }

    suspend fun createPlaylist(title: String): Long = api.createPlaylist(title).kind

    /** Uploads one of the user's own files into their playlist [kind] (`uploadUGCTrack`). */
    suspend fun uploadTrack(kind: Long, fileName: String, bytes: ByteArray): String =
        api.uploadTrack(kind, fileName, bytes)

    suspend fun renamePlaylist(kind: Long, title: String) {
        api.renamePlaylist(kind, title)
    }

    suspend fun deletePlaylist(kind: Long) = api.deletePlaylist(kind)

    suspend fun setVisibility(kind: Long, public: Boolean) {
        api.setVisibility(kind, public)
    }

    private fun account(): Long = session.api?.accountId ?: 0L

    private suspend fun remember(tracks: List<Track>) {
        runCatching { known.remember(tracks, downloaded = false) }
    }

    private fun PlaylistDto.summary(): CollectionSummary {
        val owner = ownerUid
        val kind = kind
        return CollectionSummary(
            key = "yandex:playlist:$owner:$kind",
            title = if (isLikes) labels.liked else title,
            subtitle = owner.takeIf { it != 0L }?.let { this.owner.name.ifBlank { this.owner.login }.ifBlank { null } },
            coverUrl = coverUrl(COVER),
            trackCount = trackCount,
            kind = if (isLikes) CollectionKind.Liked else CollectionKind.Playlist,
            open = { playlist(owner, kind) },
        )
    }

    private fun AlbumDto.summary(): CollectionSummary {
        val id = id
        return CollectionSummary(
            key = "yandex:album:$id",
            title = title,
            subtitle = listOfNotNull(artistLine.ifBlank { null }, year?.toString()).joinToString(" · "),
            coverUrl = coverUrl(COVER),
            trackCount = trackCount,
            kind = CollectionKind.Album,
            open = { album(id) },
        )
    }

    private fun ArtistDto.summary() = ArtistSummary(id, name, coverUrl(COVER))

    companion object {
        const val CHART_KIND = -2L
        private const val COVER = "400x400"
        private const val BATCH = 250
    }
}
