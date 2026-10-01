package com.quark.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.produceState
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Something fetched for a screen: not yet, there, or not to be had. */
sealed interface Load<out T> {
    data object Loading : Load<Nothing>
    data class Ready<T>(val value: T) : Load<T>
    data class Failed(val message: String) : Load<Nothing>
}

/**
 * Keeps what screens fetched while they are in the back stack and a while
 * after, so going back to a playlist does not fetch it again.
 */
class LoadCache(private val capacity: Int = 48) {
    private val entries = LinkedHashMap<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> get(key: String): T? = entries[key] as T?

    fun put(key: String, value: Any?) {
        entries.remove(key)
        entries[key] = value
        while (entries.size > capacity) entries.remove(entries.keys.first())
    }

    fun forget(key: String) {
        entries.remove(key)
    }

    fun forgetAll(prefix: String) {
        entries.keys.filter { it.startsWith(prefix) }.forEach(entries::remove)
    }
}

/**
 * Runs [load] for [key] — again whenever [generation] changes — and says how
 * it went. With a [cache], a result already fetched is shown at once.
 */
@Composable
fun <T> rememberLoad(
    key: String,
    generation: Int = 0,
    cache: LoadCache? = null,
    load: suspend () -> T,
): State<Load<T>> {
    val cached = if (generation == 0) cache?.get<T>(key) else null
    return produceState<Load<T>>(if (cached != null) Load.Ready(cached) else Load.Loading, key, generation) {
        if (cached != null) return@produceState
        if (value !is Load.Ready) value = Load.Loading
        value = try {
            val result = load()
            cache?.put(key, result)
            Load.Ready(result)
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            Load.Failed(e.message ?: e::class.simpleName ?: "Error")
        }
    }
}

/** Whether the window is phone-narrow, which switches several layouts. */
val LocalCompact = compositionLocalOf { false }

/** How much of the bottom edge the mini player covers, for lists to scroll past. */
val LocalBottomInset = compositionLocalOf { 0.dp }

/** Shorthand for the bottom padding a scrolling screen needs. */
val bottomInset: Dp @Composable get() = LocalBottomInset.current
