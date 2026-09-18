package com.quark.app.lyrics

import com.quark.app.QuarkApp
import com.quark.core.lyrics.LrcParser
import com.quark.core.lyrics.Lyrics
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import com.quark.network.yandex.LyricsFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** What the lyrics view has to show. */
sealed interface LyricsState {
    data object Idle : LyricsState
    data object Loading : LyricsState
    data class Ready(val lyrics: Lyrics) : LyricsState

    /** No words for this track, or the source would not give them up. */
    data class Unavailable(val reason: String) : LyricsState
}

/**
 * Song words for whatever is playing.
 *
 * Only Yandex tracks have them: the api is the only source the player has, and
 * it answers by track id. Local files fall through to [LyricsState.Unavailable]
 * rather than being looked up by title, which would guess.
 */
class LyricsViewModel(private val app: QuarkApp) {

    private val scope = app.scope

    private val _state = MutableStateFlow<LyricsState>(LyricsState.Idle)
    val state: StateFlow<LyricsState> = _state.asStateFlow()

    /** Index of the line to light, recomputed as the track plays. */
    val activeLine: StateFlow<Int> = combine(
        _state,
        app.controller.state.map { it.position },
    ) { state, position ->
        (state as? LyricsState.Ready)?.lyrics?.lineAt(position) ?: -1
    }
        .distinctUntilChanged()
        .stateIn(scope, SharingStarted.Eagerly, -1)

    init {
        app.controller.state
            .map { it.current }
            .distinctUntilChanged()
            .onEach(::load)
            .launchIn(scope)
    }

    private suspend fun load(track: Track) {
        if (track !is YandexTrack) {
            _state.value = LyricsState.Unavailable("Lyrics are available for Yandex Music tracks.")
            return
        }
        val api = app.yandex.api
        if (api == null) {
            _state.value = LyricsState.Unavailable("Sign in to Yandex Music to see lyrics.")
            return
        }

        _state.value = LyricsState.Loading
        _state.value = try {
            // Ask for synced words; the api falls back to plain text itself when
            // a song has no timings, and the parser reports which arrived.
            val text = api.lyrics(track.trackId, LyricsFormat.Synced)
            when {
                text.isNullOrBlank() -> LyricsState.Unavailable("No lyrics for this track.")
                else -> LyricsState.Ready(LrcParser.parse(text))
            }
        } catch (e: Exception) {
            LyricsState.Unavailable(e.message ?: "Could not load lyrics.")
        }
    }
}
