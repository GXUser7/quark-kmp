package com.quark.app.shell

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import com.quark.app.Platform
import com.quark.app.QuarkApp
import com.quark.app.browse.CatalogLabels
import com.quark.app.browse.CollectionKind
import com.quark.app.browse.CollectionSummary
import com.quark.app.browse.EditablePlaylist
import com.quark.app.browse.TrackCollection
import com.quark.app.i18n.Strings
import com.quark.app.nav.Navigator
import com.quark.app.nav.Screen
import com.quark.app.ui.ChoiceDialog
import com.quark.app.ui.ConfirmDialog
import com.quark.app.ui.DialogHost
import com.quark.app.ui.LoadCache
import com.quark.app.ui.Messages
import com.quark.app.ui.TextInputDialog
import com.quark.app.yandex.WaveSession
import com.quark.core.model.Playlist
import com.quark.core.model.PlaylistId
import com.quark.core.model.PlaylistSource
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import com.quark.core.player.ShuffleMode
import com.quark.data.repository.StoredPlaylist
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * What any screen can ask of the application: play this, queue that, add it to
 * a playlist, show that album. The Flutter build had these as callbacks threaded
 * through every widget and as calls on singletons from inside menus; here the
 * screens share one object and stay free of the plumbing.
 */
class Shell(
    val app: QuarkApp,
    val platform: Platform,
    val navigator: Navigator,
    val messages: Messages,
    val dialogs: DialogHost,
    private val scope: CoroutineScope,
) {
    /** Set by the root on every language change; messages are built from it. */
    var strings: Strings = Strings()
        set(value) {
            if (field === value) return
            field = value
            val labels = CatalogLabels(
                liked = value.liked,
                chart = value.chart,
                likes = value.liked,
                tracks = value.tracksTitle,
                myMusic = value.myMusic,
                popular = value.popular,
            )
            app.yandexCatalog.labels = labels
            app.catalogs.labels = labels
            // Titles already fetched were named in the old language.
            cache.forgetAll("")
        }

    /** What the screens fetched, kept for going back to them. */
    val cache = LoadCache()

    // --- Playing -------------------------------------------------------------------

    /** Plays [collection] from [start], or from the top; [shuffled] shuffles it first. */
    fun play(collection: TrackCollection, start: Track? = null, shuffled: Boolean = false) {
        playTracks(collection.title, collection.tracks, start, collection.playlistId, shuffled)
    }

    fun playTracks(
        title: String,
        tracks: List<Track>,
        start: Track? = null,
        id: PlaylistId = PlaylistId(0, title.hashCode().toLong(), PlaylistSource.Local),
        shuffled: Boolean = false,
    ) {
        if (tracks.isEmpty()) return
        attempt {
            val first = start ?: if (shuffled) tracks.random() else tracks.first()
            app.playback.open(Playlist(id = id, name = title, tracks = tracks), first)
            val state = app.controller.state.value
            if (shuffled) app.controller.shuffle(ShuffleMode.NowOnTop)
            else if (state.isShuffled) app.controller.unshuffle()
        }
    }

    /** Plays [tracks] after the current one, or starts them if nothing is loaded. */
    fun playNext(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (!app.controller.state.value.hasTrack) return playTracks(strings.queue, tracks)
        app.controller.enqueueNext(tracks)
        messages.show(strings.willPlayNext)
    }

    fun enqueue(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        if (!app.controller.state.value.hasTrack) return playTracks(strings.queue, tracks)
        app.controller.enqueueLast(tracks)
        messages.show(strings.addedToQueue)
    }

    fun startMyVibe() {
        app.wave.start()
    }

    fun startStation(track: YandexTrack) {
        app.wave.start(listOf("track:${track.trackId}"), strings.stationOn(track.title))
    }

    // --- Playlists -------------------------------------------------------------------

    /** Asks which of the user's playlists [tracks] go into, a new one included. */
    fun addToPlaylist(tracks: List<Track>) {
        if (tracks.isEmpty()) return
        dialogs.show { dismiss ->
            val playlists by app.userLibrary.all.collectAsState()
            val s = strings
            ChoiceDialog(
                title = s.choosePlaylist,
                choices = listOf(s.newPlaylistEllipsis to { createPlaylist(tracks) }) +
                    playlists.map { playlist -> playlist.title to { addTo(playlist, tracks) } },
                dismiss = dismiss,
            )
        }
    }

    private fun addTo(playlist: StoredPlaylist, tracks: List<Track>) = attempt {
        app.userLibrary.add(playlist.id, tracks)
        messages.show(strings.addedTo(playlist.title))
    }

    /** A new playlist, holding [tracks] from the start. */
    fun createPlaylist(tracks: List<Track> = emptyList(), coverUrl: String? = null, name: String = "") {
        dialogs.show { dismiss ->
            val s = strings
            TextInputDialog(
                title = s.newPlaylist,
                initial = name,
                placeholder = s.playlistName,
                confirm = s.create,
                dismiss = dismiss,
            ) { title ->
                attempt {
                    app.userLibrary.create(title, tracks, coverUrl)
                    if (tracks.isNotEmpty()) messages.show(strings.addedTo(title))
                }
            }
        }
    }

    /** Asks which of the user's Yandex playlists [tracks] go into. */
    fun addToYandexPlaylist(tracks: List<YandexTrack>) {
        if (tracks.isEmpty()) return
        dialogs.show { dismiss ->
            var playlists by remember { mutableStateOf<List<EditablePlaylist>?>(null) }
            LaunchedEffect(Unit) {
                playlists = runCatching { app.yandexCatalog.editablePlaylists() }.getOrDefault(emptyList())
            }
            ChoiceDialog(
                title = strings.addToYandexPlaylist,
                choices = playlists.orEmpty().map { playlist ->
                    playlist.title to {
                        attempt {
                            app.yandexCatalog.addToPlaylist(playlist.kind, tracks)
                            messages.show(strings.addedTo(playlist.title))
                        }
                    }
                },
                dismiss = dismiss,
                loading = playlists == null,
            )
        }
    }

    /** Keeps a copy of [collection] among the user's playlists. */
    fun saveToLibrary(collection: TrackCollection) = attempt {
        app.userLibrary.create(collection.title, collection.tracks, collection.coverUrl)
        messages.show(strings.savedToLibrary)
    }

    fun renameUserPlaylist(id: Long, current: String, then: () -> Unit = {}) {
        dialogs.show { dismiss ->
            val s = strings
            TextInputDialog(s.rename, current, s.playlistName, s.save, dismiss) { title ->
                attempt {
                    app.userLibrary.rename(id, title)
                    then()
                }
            }
        }
    }

    fun describeUserPlaylist(id: Long, current: String?, then: () -> Unit = {}) {
        dialogs.show { dismiss ->
            val s = strings
            TextInputDialog(s.editDescription, current.orEmpty(), s.description, s.save, dismiss, singleLine = false) { text ->
                attempt {
                    app.userLibrary.setDescription(id, text)
                    then()
                }
            }
        }
    }

    fun deleteUserPlaylist(id: Long, title: String, then: () -> Unit = {}) {
        dialogs.show { dismiss ->
            val s = strings
            ConfirmDialog(s.deletePlaylist, s.deletePlaylistConfirm(title), s.delete, dismiss, danger = true) {
                attempt {
                    app.userLibrary.delete(id)
                    then()
                }
            }
        }
    }

    fun deleteAllUserPlaylists() {
        dialogs.show { dismiss ->
            val s = strings
            ConfirmDialog(s.deleteAllPlaylists, s.deleteAllPlaylistsDesc, s.delete, dismiss, danger = true) {
                attempt { app.userLibrary.deleteAll() }
            }
        }
    }

    fun renameYandexPlaylist(kind: Long, current: String, then: () -> Unit = {}) {
        dialogs.show { dismiss ->
            val s = strings
            TextInputDialog(s.rename, current, s.playlistName, s.save, dismiss) { title ->
                attempt {
                    app.yandexCatalog.renamePlaylist(kind, title)
                    then()
                }
            }
        }
    }

    fun deleteYandexPlaylist(kind: Long, title: String, then: () -> Unit = {}) {
        dialogs.show { dismiss ->
            val s = strings
            ConfirmDialog(s.deletePlaylist, s.deletePlaylistConfirm(title), s.delete, dismiss, danger = true) {
                attempt {
                    app.yandexCatalog.deletePlaylist(kind)
                    then()
                }
            }
        }
    }

    fun createYandexPlaylist(then: () -> Unit = {}) {
        dialogs.show { dismiss ->
            val s = strings
            TextInputDialog(s.newPlaylist, "", s.playlistName, s.create, dismiss) { title ->
                attempt {
                    app.yandexCatalog.createPlaylist(title)
                    then()
                }
            }
        }
    }

    // --- Going places ------------------------------------------------------------------

    fun open(summary: CollectionSummary) {
        navigator.push(Screen.Collection(summary.key, summary.title, summary.open))
    }

    fun openUserPlaylist(playlist: StoredPlaylist) {
        navigator.push(Screen.Collection("local:${playlist.id}", playlist.title) { userCollection(playlist.id) })
    }

    /** One of the user's playlists as a collection, read fresh each time. */
    suspend fun userCollection(id: Long): TrackCollection {
        val stored = app.userLibrary.all.value.firstOrNull { it.id == id }
        val tracks = app.userLibrary.tracksOf(id)
        val cover = stored?.coverUrl ?: app.userLibrary.coverTrack(id)?.cover?.takeIf { it.startsWith("http") }
        return TrackCollection(
            key = "local:$id",
            title = stored?.title ?: strings.playlist,
            description = stored?.description,
            coverUrl = cover,
            tracks = tracks,
            playlistId = PlaylistId(0, id, PlaylistSource.Local),
            kind = if (stored?.isAlbum == true) CollectionKind.Album else CollectionKind.Playlist,
            userPlaylistId = id,
        )
    }

    fun openYandexAlbum(id: Long, title: String) {
        navigator.push(Screen.Collection("yandex:album:$id", title) { app.yandexCatalog.album(id) })
    }

    fun openAlbum(track: YandexTrack) {
        val id = track.albumId ?: return
        openYandexAlbum(id, track.albumLine)
    }

    fun openArtist(track: YandexTrack) {
        val id = track.artistIds.firstOrNull() ?: return
        navigator.push(Screen.Artist(id, track.artists.firstOrNull().orEmpty()))
    }

    fun openArtist(id: Long, name: String) {
        navigator.push(Screen.Artist(id, name))
    }

    fun openArtistTracks(id: Long, name: String) {
        navigator.push(Screen.Collection("yandex:artist-tracks:$id", name) { app.yandexCatalog.artistTracks(id, name) })
    }

    fun findSimilar(track: YandexTrack) {
        val title = strings.similarTo(track.title)
        navigator.push(
            Screen.Collection("yandex:similar:${track.trackId}", title) {
                TrackCollection(
                    key = "yandex:similar:${track.trackId}",
                    title = title,
                    tracks = app.yandexCatalog.similar(track),
                    playlistId = PlaylistId(0, track.trackId.hashCode().toLong(), PlaylistSource.YandexMusic),
                    kind = CollectionKind.Station,
                )
            }
        )
    }

    fun openLiked() {
        navigator.push(Screen.Collection("yandex:liked", strings.likedTracks) { app.yandexCatalog.liked() })
    }

    fun openChart() {
        navigator.push(Screen.Collection("yandex:chart", strings.chart) { app.yandexCatalog.chart() })
    }

    fun openPlayer() {
        if (app.controller.state.value.hasTrack) navigator.push(Screen.Player)
    }

    // --- Everything else -------------------------------------------------------------------

    fun export(collection: TrackCollection) {
        app.exporter.export(collection.title, collection.tracks)
    }

    /**
     * Keeps every track of [collection] in the cache, so it plays without a
     * connection ("Caching playlist…" in the Dart build).
     */
    fun downloadForOffline(collection: TrackCollection) = attempt {
        messages.show(strings.caching)
        val saved = app.downloader.cache(collection.tracks) { track -> app.resolver.downloadSource(track) }
        messages.show(strings.cached(saved.size))
    }

    /** Uploads files the user picks into their Yandex playlist [kind] (`uploadTracks`). */
    fun uploadToYandex(kind: Long, then: () -> Unit = {}) = attempt {
        val files = platform.pickAudioFiles()
        if (files.isEmpty()) return@attempt
        messages.show(strings.uploading)
        var uploaded = 0
        for (file in files) {
            val bytes = platform.readBytes(file) ?: continue
            val name = app.library.displayName(file)
            runCatching { app.yandexCatalog.uploadTrack(kind, name, bytes) }.onSuccess { uploaded++ }
        }
        messages.show(strings.uploaded(uploaded, files.size), error = uploaded < files.size)
        then()
    }

    fun copy(text: String) {
        platform.copyToClipboard(text)
        messages.show(strings.copiedToClipboard)
    }

    fun toggleLike(track: Track) = app.likes.toggle(track)

    fun dislike(track: Track) = app.likes.dislike(track)

    /** Whether the wave's playlist is what is playing. */
    val isWavePlaying: Boolean
        get() = app.controller.state.value.playlistInfo.id == WaveSession.WAVE_ID

    /**
     * Runs [block] off the interface's back, turning a failure into a message
     * instead of a crash: the network fails, a playlist was deleted elsewhere.
     */
    fun attempt(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                messages.show(strings.failed(e.message ?: strings.somethingWentWrong), error = true)
            }
        }
    }
}

val LocalShell = staticCompositionLocalOf<Shell> { error("No shell") }

/** The shell of the screen being composed. */
val shell: Shell @Composable get() = LocalShell.current
