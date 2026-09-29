package com.quark.app.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.ImageBitmap
import com.quark.app.image.Cover
import com.quark.core.model.Track
import com.quark.core.player.PlayerState
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

/**
 * Everything the player layouts read and call.
 *
 * The screens take this rather than the view model itself, so they can be drawn
 * against made-up state — by the preview renderer, and by anything else that
 * wants the interface without an audio backend behind it.
 */
interface PlayerUi {
    val state: StateFlow<PlayerState>
    val cover: StateFlow<Cover?>
    val status: StateFlow<LibraryStatus>

    /** Where the seek handle is while the user is holding it, if they are. */
    val scrubbing: StateFlow<Duration?>

    @Composable
    fun rememberThumbnail(track: Track): State<ImageBitmap?>

    fun play(track: Track)
    fun playPause()
    fun next()
    fun previous()
    fun scrub(to: Duration)
    fun commitScrub()
    fun setVolume(volume: Float)
    fun toggleShuffle()
    fun toggleRepeat()
    fun enqueue(track: Track)
    fun clearQueue()
}
