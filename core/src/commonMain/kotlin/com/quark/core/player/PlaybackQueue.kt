package com.quark.core.player

import com.quark.core.model.Track

/**
 * Track ordering: the main playlist, the user's temporary queue on top of it,
 * and the way back into the playlist once the queue runs dry.
 *
 * This is a direct port of `_getNext` / `_getPrevious` / `playCustom` from
 * `lib/services/player/player.dart`, kept free of IO so it can be tested. Two
 * things differ from the Dart original on purpose:
 *
 *  - Tracks compare by value here. In Dart neither `PlayerTrack` nor its
 *    subclasses override `==`, so a track rebuilt from the database is never
 *    equal to the one in the playlist and `indexOf` silently returns -1.
 *  - Positions that would throw a RangeError in Dart (an empty playlist, or a
 *    queued track that is not in the playlist while repeat is on) return null
 *    instead of crashing.
 */
class PlaybackQueue {
    var playlist: List<Track> = emptyList()
        private set

    /** The user's "play next" queue. Takes precedence over [playlist]. */
    var queue: List<Track> = emptyList()
        private set

    var current: Track = Track.Dummy
        private set

    /**
     * Where to resume in [playlist] after the queue is exhausted: the track
     * that was playing when the queue was opened.
     */
    var returnPoint: Track? = null
        private set

    var repeat: RepeatMode = RepeatMode.Off

    fun setPlaylist(tracks: List<Track>) {
        playlist = tracks
    }

    fun setCurrent(track: Track) {
        current = track
    }

    /** The track [next] would pick, without consuming the return point. */
    fun peekNext(): Track? = resolveNext(consume = false)

    /** Advances to the next track, consuming the return point if it is used. */
    fun next(): Track? = resolveNext(consume = true)?.also { current = it }

    private fun resolveNext(consume: Boolean): Track? {
        if (queue.isNotEmpty()) {
            val indexInQueue = queue.indexOf(current)
            if (indexInQueue < 0) return queue.first()

            val nextInQueue = indexInQueue + 1
            if (nextInQueue < queue.size) return queue[nextInQueue]

            // End of the queue: hand control back to the playlist.
            val resumeAt = returnPoint?.let(playlist::indexOf) ?: -1
            if (resumeAt >= 0) return playlist[stepForward(resumeAt)]
            // Falls through: the Dart original indexes the playlist with the
            // queued track, which is not in it.
        } else {
            returnPoint?.let { pending ->
                if (consume) returnPoint = null
                return pending
            }
        }

        if (playlist.isEmpty()) return null
        val index = playlist.indexOf(current)
        if (index < 0) return playlist.first()
        return playlist[stepForward(index)]
    }

    fun previous(): Track? = resolvePrevious()?.also { current = it }

    private fun resolvePrevious(): Track? {
        if (queue.isNotEmpty()) {
            val indexInQueue = queue.indexOf(current)
            if (indexInQueue > 0) return queue[indexInQueue - 1]
            if (indexInQueue == 0) returnPoint?.let { return it }
        }

        if (playlist.isEmpty()) return null
        val index = playlist.indexOf(current)
        if (index <= 0) return playlist.last()
        return playlist[index - 1]
    }

    /** Repeat holds position; otherwise walk forward and wrap at the end. */
    private fun stepForward(from: Int): Int = when {
        repeat == RepeatMode.One -> from
        from == playlist.lastIndex -> 0
        else -> from + 1
    }

    /**
     * Plays [track] on demand. Picking something out of the queue drops
     * everything queued before it; picking out of the playlist makes that
     * track the point the queue returns to.
     */
    fun playNow(track: Track) {
        val target = queue.indexOf(track)
        when {
            // Jumping ahead inside the queue drops what was skipped over. The
            // current track is not itself in the queue, so a missing index means
            // "from the start".
            target >= 0 -> {
                val from = queue.indexOf(current).coerceAtLeast(0)
                if (from < target) queue = queue.take(from) + queue.drop(target)
            }

            // Leaving the queue for the playlist moves the point it returns to.
            // With no queue open there is nothing to return from.
            queue.isNotEmpty() && playlist.contains(track) -> returnPoint = track
        }
        current = track
    }

    fun enqueueNext(track: Track) = enqueueNext(listOf(track))

    fun enqueueNext(tracks: List<Track>) {
        openQueue()
        // -1 when the current track is playing from the playlist, which puts
        // the insert at the head of the queue, where it should be.
        val at = (queue.indexOf(current) + 1).coerceIn(0, queue.size)
        queue = queue.toMutableList().apply { addAll(at, tracks) }
    }

    fun enqueueLast(track: Track) = enqueueLast(listOf(track))

    fun enqueueLast(tracks: List<Track>) {
        openQueue()
        queue = queue + tracks
    }

    fun removeFromQueue(track: Track) {
        queue = queue.filterNot { it == track }
        if (queue.isEmpty()) returnPoint = null
    }

    fun clearQueue() {
        queue = emptyList()
        returnPoint = null
    }

    /** Moves the queued entry at [from] to [to], for drag-to-reorder. */
    fun moveInQueue(from: Int, to: Int) {
        if (from !in queue.indices || to !in queue.indices || from == to) return
        queue = queue.toMutableList().apply { add(to, removeAt(from)) }
    }

    /**
     * Remembers where to come back to the first time the queue is used. The
     * current track does not join the queue: it is still playing from the
     * playlist, and putting it in would make it its own successor.
     */
    private fun openQueue() {
        if (queue.isEmpty()) returnPoint = current
    }
}
