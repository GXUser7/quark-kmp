package com.quark.app.yandex

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.CircleButton
import com.quark.app.ui.Divider
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.PillButton
import com.quark.app.ui.QIcon
import com.quark.app.ui.QText
import com.quark.network.yandex.YandexOAuth

/**
 * Signing in and picking a playlist.
 *
 * Shown as a glass sheet over the player, the way the Flutter build showed its
 * login and playlist views.
 */
@Composable
fun YandexScreen(model: YandexViewModel, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val state by model.state.collectAsState()

    GlassSurface(
        shape = RoundedCornerShape(Radius.panel),
        glass = Glass.Panel,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QText("Yandex Music", Quark.type.panelTitle, Modifier.weight(1f))
                if (state is YandexState.SignedIn) {
                    PillButton("Sign out", model::signOut)
                    Spacer(Modifier.width(8.dp))
                }
                CircleButton(Icons.Filled.Close, onClose, diameter = 28.dp, iconSize = 18.dp, filled = false)
            }
            Spacer(Modifier.height(12.dp))
            Divider()
            Spacer(Modifier.height(12.dp))

            when (val current = state) {
                is YandexState.SignedIn -> PlaylistList(model, current)
                YandexState.SigningIn -> Centered { QText("Signing in…", Quark.type.body) }
                is YandexState.Failed -> SignIn(model, error = current.message)
                YandexState.SignedOut -> SignIn(model, error = null)
            }
        }
    }
}

@Composable
private fun SignIn(model: YandexViewModel, error: String?) {
    var pasted by remember { mutableStateOf("") }
    val token = YandexOAuth.extractToken(pasted)

    Centered {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(max = 520.dp),
        ) {
            QText(
                text = "Open the authorisation page, allow access, then paste the address you " +
                    "are sent to — or the token itself.",
                style = Quark.type.body.copy(textAlign = TextAlign.Center),
                color = Quark.colors.textSecondary,
            )
            Spacer(Modifier.height(16.dp))
            PillButton(
                text = "Open authorisation page",
                onClick = { model.openAuthorizePage(YandexOAuth.authorizeUrl) },
                modifier = Modifier.width(320.dp),
            )
            Spacer(Modifier.height(16.dp))

            TokenField(
                value = pasted,
                onValueChange = { pasted = it },
                modifier = Modifier.fillMaxWidth(),
            )

            if (error != null) {
                Spacer(Modifier.height(10.dp))
                QText(error, Quark.type.label, color = Quark.colors.textMuted)
            }

            Spacer(Modifier.height(16.dp))
            PillButton(
                text = if (token != null) "Sign in" else "Paste the address or token",
                onClick = { token?.let(model::signIn) },
                modifier = Modifier.width(320.dp),
            )
        }
    }
}

@Composable
private fun TokenField(value: String, onValueChange: (String) -> Unit, modifier: Modifier = Modifier) {
    val colors = Quark.colors
    Box(
        modifier
            .clip(RoundedCornerShape(Radius.control))
            .background(colors.control)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        if (value.isEmpty()) {
            QText("https://music.yandex.ru/#access_token=…", Quark.type.body, color = colors.textMuted)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = Quark.type.body.copy(color = colors.text),
            cursorBrush = SolidColor(colors.text),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun PlaylistList(model: YandexViewModel, state: YandexState.SignedIn) {
    val playlists by model.playlists.collectAsState()
    val load by model.load.collectAsState()

    Column(Modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            QText(state.displayName, Quark.type.body, Modifier.weight(1f), colors().textSecondary)
            QText(
                text = when (val current = load) {
                    YandexLoad.Loading -> "Loading…"
                    is YandexLoad.Failed -> current.message
                    YandexLoad.Idle -> "${playlists.size} playlists"
                },
                style = Quark.type.label,
                color = colors().textMuted,
            )
        }
        Spacer(Modifier.height(10.dp))

        if (playlists.isEmpty() && load != YandexLoad.Loading) {
            Centered {
                PillButton("Load playlists", model::refreshPlaylists, Modifier.width(280.dp))
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                items(playlists) { playlist ->
                    PlaylistRow(playlist) { model.openPlaylist(playlist) }
                }
            }
        }
    }
}

@Composable
private fun PlaylistRow(playlist: YandexPlaylistSummary, onOpen: () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = colors()

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered) colors.rowHover else Color.Transparent)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onOpen)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(Radius.thumbnail))
                .background(colors.control),
            contentAlignment = Alignment.Center,
        ) {
            QIcon(Icons.Filled.LibraryMusic, Modifier.size(18.dp), colors.textMuted)
        }
        Column(Modifier.weight(1f)) {
            QText(playlist.title, Quark.type.trackTitle, maxLines = 1)
            QText(
                "${playlist.trackCount} tracks",
                Quark.type.trackSubtitle,
                color = colors.textSecondary,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun colors() = Quark.colors

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}
