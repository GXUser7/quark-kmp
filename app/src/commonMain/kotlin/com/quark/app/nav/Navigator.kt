package com.quark.app.nav

import androidx.compose.runtime.mutableStateListOf
import com.quark.app.browse.TrackCollection

/**
 * The places the interface can be. The Flutter build pushed a route for each
 * and layered its service views over the start page as overlays; here they are
 * all one back stack, so Back — the key, the gesture, the button — always
 * means the same thing.
 */
sealed interface Screen {
    data object Home : Screen
    data object Player : Screen
    data object Search : Screen
    data object Settings : Screen
    data object Statistics : Screen
    data object Account : Screen
    data object Yandex : Screen
    data object Spotify : Screen
    data object SoundCloud : Screen
    data object Vk : Screen
    data object YouTube : Screen

    /** Any list of tracks; [load] fetches it, from the network if need be. */
    class Collection(
        val key: String,
        val title: String,
        val load: suspend () -> TrackCollection,
    ) : Screen {
        override fun equals(other: Any?) = other is Collection && other.key == key
        override fun hashCode() = key.hashCode()
    }

    data class Artist(val id: Long, val name: String) : Screen
}

class Navigator(start: Screen = Screen.Home) {
    private val stack = mutableStateListOf(start)

    val current: Screen get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** Opens [screen]; opening the one already shown does nothing. */
    fun push(screen: Screen) {
        if (stack.last() == screen) return
        // Going to a screen already further down pops back to it rather than
        // growing the stack with a second copy.
        val existing = stack.indexOf(screen)
        if (existing >= 0 && (screen == Screen.Player || screen == Screen.Home)) {
            while (stack.size > existing + 1) stack.removeAt(stack.lastIndex)
            return
        }
        stack.add(screen)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun home() {
        while (stack.size > 1) stack.removeAt(stack.lastIndex)
    }
}
