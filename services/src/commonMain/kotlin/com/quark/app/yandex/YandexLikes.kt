package com.quark.app.yandex

import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Which Yandex tracks the user liked, kept in one place so every heart in the
 * interface agrees — the Dart build moved this into `YandexMusicSingleton` for
 * the same reason (`likedTracksNotifier`).
 *
 * Toggling is optimistic: the heart changes at once and changes back if Yandex
 * refuses.
 */
class YandexLikes(
    private val session: YandexSession,
    private val scope: CoroutineScope,
) {
    private val _liked = MutableStateFlow<Set<String>>(emptySet())
    val liked: StateFlow<Set<String>> = _liked.asStateFlow()

    private val _disliked = MutableStateFlow<Set<String>>(emptySet())
    val disliked: StateFlow<Set<String>> = _disliked.asStateFlow()

    init {
        scope.launch {
            session.apiFlow.collect { api ->
                if (api == null) {
                    _liked.value = emptySet()
                    _disliked.value = emptySet()
                } else {
                    refresh()
                }
            }
        }
    }

    suspend fun refresh() {
        val api = session.api ?: return
        runCatching { _liked.value = api.likedTrackIds().toSet() }
        runCatching { _disliked.value = api.dislikedTracks().map { it.id }.toSet() }
    }

    fun isLiked(track: Track): Boolean = track is YandexTrack && track.trackId in _liked.value

    fun toggle(track: Track) {
        if (track !is YandexTrack) return
        val api = session.api ?: return
        val id = track.trackId
        val wasLiked = id in _liked.value
        _liked.update { if (wasLiked) it - id else it + id }
        scope.launch {
            val ok = runCatching { if (wasLiked) api.unlike(listOf(id)) else api.like(listOf(id)) }.isSuccess
            if (!ok) _liked.update { if (wasLiked) it + id else it - id }
        }
    }

    /** "Not interested": keeps the track out of My Vibe and recommendations. */
    fun dislike(track: Track) {
        if (track !is YandexTrack) return
        val api = session.api ?: return
        val id = track.trackId
        _disliked.update { it + id }
        _liked.update { it - id }
        scope.launch {
            if (runCatching { api.dislike(id) }.isFailure) _disliked.update { it - id }
        }
    }
}
