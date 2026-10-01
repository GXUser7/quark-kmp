package com.quark.app.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.quark.app.browse.LikeButton
import com.quark.app.image.Artwork
import com.quark.app.image.rememberThumbnail
import com.quark.app.shell.shell
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.CircleButton
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.LocalCompact
import com.quark.app.ui.QText

/** Height of the bar, which the screens above it leave room for. */
val MINI_PLAYER_HEIGHT = 72.dp

/**
 * What is playing, at the bottom of every screen but the player — the
 * slop branch's `MacroPlayer` strip in the Yandex pages, made universal.
 * Tapping it opens the player.
 */
@Composable
fun MiniPlayer(model: PlayerViewModel, modifier: Modifier = Modifier) {
    val shell = shell
    val state by model.state.collectAsState()
    if (!state.hasTrack) return
    val compact = LocalCompact.current
    val thumbnail by rememberThumbnail(state.current)
    val colors = Quark.colors

    GlassSurface(
        shape = RoundedCornerShape(Radius.card),
        glass = Glass.Card,
        modifier = modifier.fillMaxWidth().height(MINI_PLAYER_HEIGHT - 12.dp),
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight()) {
            // The progress, as a hairline along the top edge.
            Box(Modifier.fillMaxWidth().height(2.dp).background(colors.track)) {
                Box(
                    Modifier
                        .fillMaxWidth(state.progress.coerceIn(0f, 1f))
                        .height(2.dp)
                        .background(Quark.accent.primary)
                )
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clickable(indication = null, interactionSource = null) { shell.openPlayer() }
                    .padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Artwork(thumbnail, RoundedCornerShape(Radius.thumbnail), Modifier.size(42.dp))
                Column(Modifier.weight(1f)) {
                    QText(state.current.title, Quark.type.trackTitle, maxLines = 1)
                    QText(state.current.artistLine, Quark.type.trackSubtitle, color = colors.textSecondary, maxLines = 1)
                }
                LikeButton(state.current, diameter = 34.dp)
                if (!compact) CircleButton(Icons.Filled.SkipPrevious, model::previous, diameter = 36.dp, iconSize = 18.dp, filled = false)
                CircleButton(
                    icon = if (state.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
                    onClick = model::playPause,
                    diameter = 40.dp,
                    iconSize = 22.dp,
                )
                CircleButton(Icons.Filled.SkipNext, model::next, diameter = 36.dp, iconSize = 18.dp, filled = false)
            }
        }
    }
}
