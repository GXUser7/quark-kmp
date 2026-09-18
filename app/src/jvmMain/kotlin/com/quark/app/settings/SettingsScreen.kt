package com.quark.app.settings

import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.CircleButton
import com.quark.app.ui.Divider
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.QText
import com.quark.app.ui.ThinSlider
import com.quark.core.settings.Settings
import com.quark.core.settings.SettingsStore
import com.quark.core.settings.StreamQuality

/**
 * Preferences, on the brightest of the three glass recipes — the one the
 * original reserved for this screen (blur 75, white at a fifth).
 *
 * Every row writes straight through to the store, which saves the whole
 * settings value at once. The Dart build had a listener per field and wrote each
 * on its own, so a crash between two of them left the file inconsistent.
 */
@Composable
fun SettingsScreen(store: SettingsStore, onClose: () -> Unit, modifier: Modifier = Modifier) {
    val settings by store.settings.collectAsState()

    GlassSurface(
        shape = RoundedCornerShape(Radius.panel),
        glass = Glass.Panel,
        modifier = modifier,
    ) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                QText("Preferences", Quark.type.panelTitle, Modifier.weight(1f))
                CircleButton(
                    icon = Icons.Filled.Close,
                    onClick = onClose,
                    diameter = 28.dp,
                    iconSize = 18.dp,
                    filled = false,
                )
            }
            Spacer(Modifier.height(14.dp))
            Divider()

            Column(
                Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Section("Playback")
                Toggle(
                    title = "Keep streamed tracks",
                    subtitle = "Save what you play to disk so replays are instant",
                    checked = settings.playback.cacheRemoteTracks,
                ) { on ->
                    store.update { it.copy(playback = it.playback.copy(cacheRemoteTracks = on)) }
                }
                Toggle(
                    title = "Prefetch the next track",
                    subtitle = "Fetch its address before it is needed",
                    checked = settings.playback.prefetchNext,
                ) { on ->
                    store.update { it.copy(playback = it.playback.copy(prefetchNext = on)) }
                }

                Section("Library")
                Toggle(
                    title = "Include subfolders",
                    subtitle = "Descend into folders when one is added",
                    checked = settings.library.recursiveFolderAdding,
                ) { on ->
                    store.update { it.copy(library = it.library.copy(recursiveFolderAdding = on)) }
                }
                Toggle(
                    title = "Watch folders",
                    subtitle = "Pick up files that appear in folders you added",
                    checked = settings.library.watchFolders,
                ) { on ->
                    store.update { it.copy(library = it.library.copy(watchFolders = on)) }
                }
                Toggle(
                    title = "Log what you listen to",
                    subtitle = "Keeps the play counts and the statistics screen",
                    checked = settings.library.logListens,
                ) { on ->
                    store.update { it.copy(library = it.library.copy(logListens = on)) }
                }

                Section("Appearance")
                Toggle(
                    title = "Tint the window",
                    subtitle = "Take the chrome's colour from the cover",
                    checked = settings.appearance.dynamicWindowColor,
                ) { on ->
                    store.update { it.copy(appearance = it.appearance.copy(dynamicWindowColor = on)) }
                }
                Toggle(
                    title = "Full-size covers",
                    subtitle = "Open artwork at its original size",
                    checked = settings.appearance.originalSizeCovers,
                ) { on ->
                    store.update { it.copy(appearance = it.appearance.copy(originalSizeCovers = on)) }
                }
                SliderRow(
                    title = "Animation speed",
                    value = settings.appearance.transitionSpeed,
                    range = 0.25f..2f,
                    display = { "${(it * 100).toInt()}%" },
                ) { value ->
                    store.update { it.copy(appearance = it.appearance.copy(transitionSpeed = value)) }
                }

                Section("Yandex Music")
                QualityRow(settings) { quality ->
                    store.update { it.copy(yandex = it.yandex.copy(quality = quality)) }
                }
                Toggle(
                    title = "Search Yandex too",
                    subtitle = "Offer its results alongside your own library",
                    checked = settings.yandex.searchEnabled,
                ) { on ->
                    store.update { it.copy(yandex = it.yandex.copy(searchEnabled = on)) }
                }

                Section("Integrations")
                Toggle(
                    title = "Discord presence",
                    subtitle = "Show what you are listening to",
                    checked = settings.integrations.discordRpc,
                ) { on ->
                    store.update { it.copy(integrations = it.integrations.copy(discordRpc = on)) }
                }
                Toggle(
                    title = "Local control API",
                    subtitle = "Let programs on this machine drive the player",
                    checked = settings.integrations.localApi,
                ) { on ->
                    store.update { it.copy(integrations = it.integrations.copy(localApi = on)) }
                }

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(18.dp))
    QText(title, Quark.type.label, color = Quark.colors.textMuted)
    Spacer(Modifier.height(6.dp))
}

@Composable
private fun Toggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable { onChange(!checked) }
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            QText(title, Quark.type.body)
            QText(subtitle, Quark.type.label, color = Quark.colors.textMuted)
        }
        Switch(checked, onChange)
    }
}

/** A small switch in the player's own idiom rather than Material's. */
@Composable
private fun Switch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Quark.colors
    val interaction = remember2()
    val hovered by interaction.collectIsHoveredAsState()

    val track by animateColorAsState(
        when {
            checked -> colors.text.copy(alpha = 0.85f)
            hovered -> colors.controlHover
            else -> colors.control
        }
    )
    val knob by animateColorAsState(if (checked) colors.background else colors.textSecondary)

    Box(
        Modifier
            .size(width = 42.dp, height = 24.dp)
            .clip(CircleShape)
            .background(track)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null) { onChange(!checked) },
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .padding(horizontal = 3.dp)
                .size(18.dp)
                .clip(CircleShape)
                .background(knob)
        )
    }
}

@Composable
private fun remember2() = androidx.compose.runtime.remember { MutableInteractionSource() }

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        QText(title, Quark.type.body, Modifier.weight(1f))
        ThinSlider(
            value = (value - range.start) / (range.endInclusive - range.start),
            onChange = { fraction ->
                onChange(range.start + fraction * (range.endInclusive - range.start))
            },
            modifier = Modifier.width(160.dp),
        )
        Spacer(Modifier.width(10.dp))
        QText(display(value), Quark.type.label, color = Quark.colors.textMuted)
    }
}

@Composable
private fun QualityRow(settings: Settings, onChange: (StreamQuality) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            QText("Stream quality", Quark.type.body)
            QText(
                "Lossless needs a subscription that allows it",
                Quark.type.label,
                color = Quark.colors.textMuted,
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            StreamQuality.entries.forEach { quality ->
                Choice(
                    label = quality.name,
                    selected = settings.yandex.quality == quality,
                    onClick = { onChange(quality) },
                )
            }
        }
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Quark.colors
    val interaction = remember2()
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> colors.text.copy(alpha = 0.85f)
            hovered -> colors.controlHover
            else -> colors.control
        }
    )

    Box(
        Modifier
            .clip(RoundedCornerShape(Radius.control))
            .background(background)
            .hoverable(interaction)
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    ) {
        QText(
            text = label,
            style = Quark.type.label,
            color = if (selected) colors.background else colors.textSecondary,
        )
    }
}
