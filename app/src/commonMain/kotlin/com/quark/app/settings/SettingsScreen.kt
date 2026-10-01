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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.quark.app.QUARK_VERSION
import com.quark.app.browse.TopBar
import com.quark.app.i18n.Language
import com.quark.app.i18n.strings
import com.quark.app.isDesktop
import com.quark.app.nav.Screen
import com.quark.app.shell.shell
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.ConfirmDialog
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.Item
import com.quark.app.ui.PillButton
import com.quark.app.ui.PopupMenu
import com.quark.app.ui.QIcon
import com.quark.app.ui.QText
import com.quark.app.ui.QTextField
import com.quark.app.ui.ThinSlider
import com.quark.app.ui.bottomInset
import com.quark.core.settings.AccountSettings
import com.quark.core.settings.Settings
import com.quark.core.settings.SoundCloudSettings
import com.quark.core.settings.SpotifySettings
import com.quark.core.settings.StreamQuality
import com.quark.core.settings.ThemeMode
import com.quark.core.settings.VkSettings
import com.quark.core.settings.YandexSettings
import com.quark.core.settings.YouTubeSettings
import com.quark.data.files.Files
import kotlinx.coroutines.withContext

/**
 * Preferences (`settings.dart`), every section of it, on the brightest of the
 * three glass recipes — the one the original reserved for this screen.
 *
 * Every row writes straight through to the store, which saves the whole
 * settings value at once. The Dart build had a listener per field and wrote each
 * on its own, so a crash between two of them left the file inconsistent.
 */
@Composable
fun SettingsScreen() {
    val shell = shell
    val s = strings
    val app = shell.app
    val store = app.settings
    val settings by store.settings.collectAsState()
    val desktop = shell.platform.isDesktop

    Column(Modifier.fillMaxSize()) {
        TopBar(s.preferences, onBack = { shell.navigator.pop() })
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            GlassSurface(
                shape = RoundedCornerShape(Radius.panel),
                glass = Glass.Panel,
                modifier = Modifier
                    .widthIn(max = 820.dp)
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 12.dp, bottom = bottomInset + 8.dp),
            ) {
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 18.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    // --- Account ---
                    Section(s.quarkAccount)
                    ActionRow(
                        title = if (settings.account.isSignedIn) s.signedInAs(settings.account.username.ifBlank { settings.account.email }) else s.quarkAccount,
                        subtitle = s.accountHint,
                        action = if (settings.account.isSignedIn) s.profile else s.login,
                    ) { shell.navigator.push(Screen.Account) }
                    if (settings.account.isSignedIn) {
                        Toggle(s.syncPlaylists, s.syncPlaylistsHint, settings.account.syncPlaylists) { on ->
                            store.update { it.copy(account = it.account.copy(syncPlaylists = on)) }
                        }
                    }

                    // --- Playback ---
                    Section(s.playback)
                    Toggle(s.keepStreamed, s.keepStreamedHint, settings.playback.cacheRemoteTracks) { on ->
                        store.update { it.copy(playback = it.playback.copy(cacheRemoteTracks = on)) }
                    }
                    Toggle(s.prefetchNext, s.prefetchNextHint, settings.playback.prefetchNext) { on ->
                        store.update { it.copy(playback = it.playback.copy(prefetchNext = on)) }
                    }

                    // --- Library ---
                    Section(s.library)
                    Toggle(s.recursiveFiles, s.recursiveFilesHint, settings.library.recursiveFolderAdding) { on ->
                        store.update { it.copy(library = it.library.copy(recursiveFolderAdding = on)) }
                    }
                    if (desktop) {
                        Toggle(s.watchFolders, s.watchFoldersHint, settings.library.watchFolders) { on ->
                            store.update { it.copy(library = it.library.copy(watchFolders = on)) }
                        }
                    }
                    Toggle(s.logListens, s.logListensHint, settings.library.logListens) { on ->
                        store.update { it.copy(library = it.library.copy(logListens = on)) }
                    }
                    ActionRow(s.deleteAllPlaylists, s.deleteAllPlaylistsDesc, s.deleteAll, danger = true) {
                        shell.deleteAllUserPlaylists()
                    }

                    // --- Appearance ---
                    Section(s.appearance)
                    val language = settings.appearance.language?.let { Language.of(it) }
                    DropdownRow(
                        title = s.language,
                        current = language?.nativeName ?: s.systemLanguage,
                        options = listOf(s.systemLanguage to (language == null)) +
                            Language.entries.map { it.nativeName to (it == language) },
                    ) { index ->
                        val code = if (index == 0) null else Language.entries[index - 1].code
                        store.update { it.copy(appearance = it.appearance.copy(language = code)) }
                    }
                    ChoiceRow(
                        title = s.theme,
                        subtitle = null,
                        options = listOf(
                            s.themeSystem to ThemeMode.System,
                            s.themeDark to ThemeMode.Dark,
                            s.themeLight to ThemeMode.Light,
                        ),
                        selected = settings.appearance.theme,
                    ) { mode -> store.update { it.copy(appearance = it.appearance.copy(theme = mode)) } }
                    Toggle(s.dynamicWindowColor, s.dynamicWindowColorHint, settings.appearance.dynamicWindowColor) { on ->
                        store.update { it.copy(appearance = it.appearance.copy(dynamicWindowColor = on)) }
                    }
                    if (desktop) {
                        Toggle(s.playlistArea, s.playlistAreaHint, settings.appearance.playlistOpeningArea) { on ->
                            store.update { it.copy(appearance = it.appearance.copy(playlistOpeningArea = on)) }
                        }
                    }
                    Toggle(s.originalCoverSize, s.originalCoverSizeHint, settings.appearance.originalSizeCovers) { on ->
                        store.update { it.copy(appearance = it.appearance.copy(originalSizeCovers = on)) }
                    }
                    SliderRow(
                        title = s.transitionSpeed,
                        subtitle = s.transitionSpeedHint,
                        value = settings.appearance.transitionSpeed,
                        range = 0.25f..2f,
                        display = { "${(it * 100).toInt()}%" },
                    ) { value -> store.update { it.copy(appearance = it.appearance.copy(transitionSpeed = value)) } }

                    // --- Integrations ---
                    if (desktop) {
                        Section(s.integrations)
                        // Android always has its media notification; the toggle is SMTC's.
                        Toggle(s.nativeControls, s.nativeControlsHint, settings.integrations.nativeControls) { on ->
                            store.update { it.copy(integrations = it.integrations.copy(nativeControls = on)) }
                        }
                        Toggle(s.discordRpc, s.discordRpcHint, settings.integrations.discordRpc) { on ->
                            store.update { it.copy(integrations = it.integrations.copy(discordRpc = on)) }
                        }
                        Toggle(s.localApi, s.localApiHint, settings.integrations.localApi) { on ->
                            store.update { it.copy(integrations = it.integrations.copy(localApi = on)) }
                        }
                        if (settings.integrations.localApi) {
                            Toggle(s.localApiLan, s.localApiLanHint, settings.integrations.localApiLan) { on ->
                                store.update { it.copy(integrations = it.integrations.copy(localApiLan = on)) }
                            }
                        }
                    }

                    // --- Yandex Music ---
                    Section(s.yandexMusic)
                    ChoiceRow(
                        title = s.quality,
                        subtitle = s.qualityHint,
                        options = listOf(
                            s.qualityLossless to StreamQuality.Lossless,
                            s.qualityNormal to StreamQuality.Normal,
                            s.qualityLow to StreamQuality.Low,
                        ),
                        selected = settings.yandex.quality.takeIf { it != StreamQuality.High } ?: StreamQuality.Normal,
                    ) { quality -> store.update { it.copy(yandex = it.yandex.copy(quality = quality)) } }
                    Toggle(s.searchInYandex, s.searchInYandexHint, settings.yandex.searchEnabled) { on ->
                        store.update { it.copy(yandex = it.yandex.copy(searchEnabled = on)) }
                    }
                    Toggle(s.yandexPreload, s.yandexPreloadHint, settings.yandex.preloadPlaylists) { on ->
                        store.update { it.copy(yandex = it.yandex.copy(preloadPlaylists = on)) }
                    }
                    if (settings.yandex.isAuthorised) {
                        ActionRow(s.token, s.tokenHint, s.signOut, danger = true) {
                            app.yandex.signOut()
                            shell.cache.forgetAll("yandex:")
                        }
                    }

                    // --- Spotify ---
                    Section(s.spotify)
                    ChoiceRow(
                        title = s.spotifyQuality,
                        subtitle = s.spotifyQualityHint,
                        options = listOf(
                            s.qualityHiRes to "hires",
                            s.qualityLosslessCd to "lossless",
                            s.qualityHigh to "high",
                        ),
                        selected = settings.spotify.quality,
                    ) { quality -> store.update { it.copy(spotify = it.spotify.copy(quality = quality)) } }
                    Toggle(s.searchInService, s.searchInServiceHint, settings.spotify.searchEnabled) { on ->
                        store.update { it.copy(spotify = it.spotify.copy(searchEnabled = on)) }
                    }

                    // --- SoundCloud ---
                    Section(s.soundCloud)
                    TextRow(s.soundCloudToken, s.soundCloudTokenHint, settings.soundCloud.oauthToken) { token ->
                        store.update { it.copy(soundCloud = it.soundCloud.copy(oauthToken = token.trim())) }
                    }
                    Toggle(s.searchInService, s.searchInServiceHint, settings.soundCloud.searchEnabled) { on ->
                        store.update { it.copy(soundCloud = it.soundCloud.copy(searchEnabled = on)) }
                    }

                    // --- VK ---
                    Section(s.vkMusic)
                    Toggle(s.searchInService, s.searchInServiceHint, settings.vk.searchEnabled) { on ->
                        store.update { it.copy(vk = it.vk.copy(searchEnabled = on)) }
                    }

                    // --- YouTube ---
                    Section(s.youtubeMusic)
                    Toggle(s.searchInService, s.searchInServiceHint, settings.youtube.searchEnabled) { on ->
                        store.update { it.copy(youtube = it.youtube.copy(searchEnabled = on)) }
                    }
                    if (settings.youtube.cookies.isNotBlank()) {
                        ActionRow(s.forgetCookies, null, s.clearCache, danger = true) {
                            store.update { it.copy(youtube = it.youtube.copy(cookies = "")) }
                        }
                    }

                    // --- Storage ---
                    Section(s.storage)
                    StorageRow(s.cacheSize, app.paths.audioCache)
                    StorageRow(s.coverCacheSize, app.paths.coverCache)
                    InfoRow(s.exportFolder, app.paths.exports)

                    // --- About ---
                    Section(s.about)
                    InfoRow(s.version, QUARK_VERSION)
                    ActionRow(s.sourceCode, "github.com/z3nsh0w/quark", s.openAction) {
                        shell.platform.openUrl("https://github.com/z3nsh0w/quark")
                    }
                    ActionRow(s.restoreDefaults, s.restoreDefaultsHint, s.restore, danger = true) {
                        shell.dialogs.show { dismiss ->
                            ConfirmDialog(s.restoreDefaults, s.restoreDefaultsHint, s.restore, dismiss, danger = true) {
                                store.update(::defaultsKeepingAccounts)
                                shell.messages.show(s.settingsRestored)
                            }
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                }
            }
        }
    }
}

/**
 * Factory settings, minus what would sign the user out of everything: the
 * accounts and tokens, and where playback was.
 */
private fun defaultsKeepingAccounts(current: Settings): Settings = Settings(
    memory = current.memory,
    account = AccountSettings(
        accessToken = current.account.accessToken,
        refreshToken = current.account.refreshToken,
        username = current.account.username,
        email = current.account.email,
    ),
    yandex = YandexSettings(
        token = current.yandex.token,
        tokenExpiresAt = current.yandex.tokenExpiresAt,
        uid = current.yandex.uid,
        login = current.yandex.login,
        displayName = current.yandex.displayName,
        fullName = current.yandex.fullName,
        email = current.yandex.email,
    ),
    spotify = SpotifySettings(accessToken = current.spotify.accessToken, refreshToken = current.spotify.refreshToken),
    soundCloud = SoundCloudSettings(oauthToken = current.soundCloud.oauthToken, profileUrl = current.soundCloud.profileUrl),
    vk = VkSettings(userId = current.vk.userId),
    youtube = YouTubeSettings(cookies = current.youtube.cookies),
)

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(18.dp))
    QText(title.uppercase(), Quark.type.label, color = Quark.colors.textMuted)
    Spacer(Modifier.height(6.dp))
}

/** A row that can be clicked as a whole, with an optional hint under the title. */
@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)?,
    trailing: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val colors = Quark.colors
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(if (hovered && onClick != null) colors.rowHover else androidx.compose.ui.graphics.Color.Transparent)
            .hoverable(interaction, enabled = onClick != null)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = interaction, indication = null, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Column(Modifier.weight(1f)) {
            QText(title, Quark.type.body)
            if (!subtitle.isNullOrBlank()) QText(subtitle, Quark.type.label, color = Quark.colors.textMuted)
        }
        trailing()
    }
}

@Composable
private fun Toggle(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(title, subtitle, onClick = { onChange(!checked) }) { Switch(checked, onChange) }
}

@Composable
private fun ActionRow(
    title: String,
    subtitle: String?,
    action: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    SettingRow(title, subtitle, onClick = null) { PillButton(action, onClick, danger = danger, height = 36.dp) }
}

@Composable
private fun InfoRow(title: String, value: String) {
    SettingRow(title, value, onClick = null) {}
}

/** A few options side by side, one of them chosen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> ChoiceRow(
    title: String,
    subtitle: String?,
    options: List<Pair<String, T>>,
    selected: T,
    onPick: (T) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QText(title, Quark.type.body)
        if (!subtitle.isNullOrBlank()) QText(subtitle, Quark.type.label, color = Quark.colors.textMuted)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            options.forEach { (label, value) ->
                Choice(label, value == selected) { onPick(value) }
            }
        }
    }
}

/** Many options behind one button: the language list. */
@Composable
private fun DropdownRow(
    title: String,
    current: String,
    options: List<Pair<String, Boolean>>,
    onPick: (Int) -> Unit,
) {
    var open by remember { mutableStateOf(false) }
    SettingRow(title, null, onClick = { open = true }) {
        Box {
            PillButton(current, { open = true }, icon = Icons.Filled.ExpandMore, height = 36.dp)
            if (open) {
                PopupMenu(onDismiss = { open = false }) {
                    options.forEachIndexed { index, (label, chosen) ->
                        Item(label, if (chosen) Icons.Filled.Check else null) { onPick(index) }
                    }
                }
            }
        }
    }
}

/** A setting that is a line of text, saved when the field is submitted or left. */
@Composable
private fun TextRow(title: String, subtitle: String, value: String, onSave: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        QText(title, Quark.type.body)
        QText(subtitle, Quark.type.label, color = Quark.colors.textMuted)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            QTextField(text, { text = it }, Modifier.weight(1f), password = true, onSubmit = { onSave(text) })
            PillButton(strings.save, { onSave(text) }, enabled = text != value, height = 40.dp)
        }
    }
}

/** How much a cache folder holds, and a button to empty it. */
@Composable
private fun StorageRow(title: String, folder: String) {
    val shell = shell
    val s = strings
    var generation by remember { mutableIntStateOf(0) }
    var size by remember { mutableStateOf<Long?>(null) }
    LaunchedEffect(folder, generation) {
        size = withContext(shell.app.io) { runCatching { Files.directorySize(folder) }.getOrNull() }
    }
    SettingRow(title, size?.let(::megabytes) ?: s.loading, onClick = null) {
        PillButton(s.clearCache, {
            shell.attempt {
                withContext(shell.app.io) { Files.clearDirectory(folder) }
                generation++
                shell.messages.show(s.cacheCleared)
            }
        }, height = 36.dp, enabled = (size ?: 0L) > 0L)
    }
}

private fun megabytes(bytes: Long): String {
    val tenths = bytes * 10 / (1024 * 1024)
    return "${tenths / 10}.${tenths % 10} MB"
}

/** A small switch in the player's own idiom rather than Material's. */
@Composable
private fun Switch(checked: Boolean, onChange: (Boolean) -> Unit) {
    val colors = Quark.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val track by animateColorAsState(
        when {
            checked -> Quark.accent.primary.copy(alpha = 0.9f)
            hovered -> colors.controlHover
            else -> colors.control
        }
    )
    val knob by animateColorAsState(if (checked) colors.text else colors.textSecondary)

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
private fun SliderRow(
    title: String,
    subtitle: String?,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    display: (Float) -> String,
    onChange: (Float) -> Unit,
) {
    SettingRow(title, subtitle, onClick = null) {
        ThinSlider(
            value = (value - range.start) / (range.endInclusive - range.start),
            onChange = { fraction ->
                onChange(range.start + fraction * (range.endInclusive - range.start))
            },
            modifier = Modifier.width(160.dp),
        )
        QText(display(value), Quark.type.label, color = Quark.colors.textMuted)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = Quark.colors
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val background by animateColorAsState(
        when {
            selected -> Quark.accent.primary.copy(alpha = 0.55f)
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
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        QText(
            text = label,
            style = Quark.type.label,
            color = if (selected) colors.text else colors.textSecondary,
        )
    }
}
