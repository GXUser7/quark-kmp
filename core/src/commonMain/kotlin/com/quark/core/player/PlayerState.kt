package com.quark.core.player

import com.quark.core.model.PlaylistInfo
import com.quark.core.model.Track
import kotlin.time.Duration
import kotlin.time.Duration.Companion.ZERO

/** How [PlayerController.shuffle] rearranges the playlist. */
enum class ShuffleMode {
    /** Randomise everything. */
    Full,

    /** Keep what has already been played, shuffle only what comes after. */
    AfterCurrent,

    /** Shuffle everything, then move the current track to the top. */
    NowOnTop,
}

enum class RepeatMode { Off, One }

/** Why the current track changed, so listeners can tell a skip from a natural end. */
enum class ChangeReason { Completed, External }

data class TrackChange(val track: Track, val reason: ChangeReason)

/**
 * Everything the UI needs to draw the player, in one immutable snapshot.
 *
 * The Dart version spreads this over a dozen ValueNotifiers, which is why a
 * single repaint can arrive as a dozen separate rebuilds; collapsing them into
 * one StateFlow is deliberate.
 */
data class PlayerState(
    val current: Track = Track.Dummy,
    /** Cause of the most recent [current] change, updated in the same snapshot. */
    val lastChangeReason: ChangeReason = ChangeReason.External,
    val playlist: List<Track> = emptyList(),
    val playlistInfo: PlaylistInfo = PlaylistInfo(),
    val queue: List<Track> = emptyList(),
    val position: Duration = ZERO,
    val duration: Duration = ZERO,
    val isPlaying: Boolean = false,
    val repeat: RepeatMode = RepeatMode.Off,
    val isShuffled: Boolean = false,
    val volume: Float = DEFAULT_VOLUME,
    val speed: Float = 1f,
) {
    val hasTrack: Boolean get() = current != Track.Dummy

    /** Index of [current] in [playlist], or -1 when it is playing off-queue. */
    val currentIndex: Int get() = playlist.indexOf(current)

    val progress: Float
        get() = if (duration <= ZERO) 0f
        else (position / duration).toFloat().coerceIn(0f, 1f)

    companion object {
        const val DEFAULT_VOLUME = 0.7f
    }
}
