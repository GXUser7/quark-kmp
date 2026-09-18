package com.quark.core.player

import kotlin.random.Random

/** The three orderings behind [ShuffleMode]. Pure, so they are testable. */
object Shuffles {

    fun <T> apply(
        mode: ShuffleMode,
        tracks: List<T>,
        pivot: T,
        random: Random = Random.Default,
    ): List<T> = when (mode) {
        ShuffleMode.Full -> full(tracks, random)
        ShuffleMode.AfterCurrent -> afterCurrent(tracks, pivot, random)
        ShuffleMode.NowOnTop -> nowOnTop(tracks, pivot, random)
    }

    fun <T> full(tracks: List<T>, random: Random = Random.Default): List<T> =
        tracks.shuffled(random)

    /** Keeps everything up to and including [pivot], shuffles the rest. */
    fun <T> afterCurrent(tracks: List<T>, pivot: T, random: Random = Random.Default): List<T> {
        val at = tracks.indexOf(pivot)
        if (at < 0) return full(tracks, random)
        return tracks.take(at + 1) + tracks.drop(at + 1).shuffled(random)
    }

    /** Shuffles everything, then lifts [pivot] to the front. */
    fun <T> nowOnTop(tracks: List<T>, pivot: T, random: Random = Random.Default): List<T> {
        val rest = tracks.filterNot { it == pivot }
        if (rest.size == tracks.size) return full(tracks, random)
        return listOf(pivot) + rest.shuffled(random)
    }
}
