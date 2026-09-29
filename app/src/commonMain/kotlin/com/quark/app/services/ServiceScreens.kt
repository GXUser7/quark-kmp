package com.quark.app.services

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileOpen
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Waves
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.quark.app.browse.CollectionSummary
import com.quark.app.browse.SectionTitle
import com.quark.app.browse.Shelf
import com.quark.app.browse.SummaryTile
import com.quark.app.browse.TopBar
import com.quark.app.i18n.strings
import com.quark.app.nav.Screen
import com.quark.app.shell.shell
import com.quark.app.theme.Glass
import com.quark.app.theme.Quark
import com.quark.app.theme.Radius
import com.quark.app.ui.CircleButton
import com.quark.app.ui.Gap
import com.quark.app.ui.GlassSurface
import com.quark.app.ui.Item
import com.quark.app.ui.Load
import com.quark.app.ui.MenuButton
import com.quark.app.ui.PillButton
import com.quark.app.ui.Placeholder
import com.quark.app.ui.QIcon
import com.quark.app.ui.QText
import com.quark.app.ui.QTextField
import com.quark.app.ui.WebSignInDialog
import com.quark.app.ui.WebSignInSpec
import com.quark.app.ui.hasEmbeddedBrowser
import com.quark.app.ui.bottomInset
import com.quark.app.ui.rememberLoad
import com.quark.app.yandex.WaveSession
import com.quark.app.yandex.WaveState
import com.quark.app.yandex.YandexState
import com.quark.app.yandex.YandexViewModel
import com.quark.network.yandex.YandexOAuth
import kotlinx.coroutines.launch

// --- Shared pieces -----------------------------------------------------------------

/** How many tiles fit across [width]. */
private fun columnsFor(width: Dp): Int = ((width - 28.dp) / 170.dp).toInt().coerceIn(2, 8)

/** Tiles in rows of [columns], for grids inside a lazy list. */
private fun LazyListScope.tileGrid(keyPrefix: String, summaries: List<CollectionSummary>, columns: Int) {
    itemsIndexed(summaries.chunked(columns), key = { index, _ -> "$keyPrefix:$index" }) { _, row ->
        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            row.forEach { summary -> SummaryTile(summary, Modifier.weight(1f)) }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}

/** The collections of a service in a grid, with its loading and its failures. */
private fun LazyListScope.loadedGrid(
    keyPrefix: String,
    load: Load<List<CollectionSummary>>,
    columns: Int,
    retry: () -> Unit,
    emptyText: String,
    loadingText: String,
    retryText: String,
) {
    when (load) {
        Load.Loading -> item(key = "$keyPrefix:loading") { Placeholder(loadingText) }
        is Load.Failed -> item(key = "$keyPrefix:failed") { Placeholder(load.message, action = retryText, onAction = retry) }
        is Load.Ready -> if (load.value.isEmpty()) {
            item(key = "$keyPrefix:empty") { Placeholder(emptyText) }
        } else {
            tileGrid(keyPrefix, load.value, columns)
        }
    }
}

/**
 * The sign-in card every service has in some form: what to do, a button that
 * opens the page to do it on, and a field for what that page hands back.
 */
@Composable
private fun SignInCard(
    title: String,
    hint: String,
    openLabel: String?,
    onOpen: () -> Unit,
    placeholder: String,
    submitLabel: String,
    error: String?,
    busy: Boolean,
    onSubmit: (String) -> Unit,
    modifier: Modifier = Modifier,
    web: WebSignInSpec? = null,
    extra: @Composable () -> Unit = {},
) {
    val s = strings
    var pasted by remember { mutableStateOf("") }
    var browsing by remember { mutableStateOf(false) }
    // On Android the page opens inside the app and hands the result back by
    // itself; elsewhere it opens in the browser and the address is pasted.
    val inApp = web != null && hasEmbeddedBrowser
    if (browsing && web != null) {
        WebSignInDialog(web) { result ->
            browsing = false
            if (result != null) onSubmit(result)
        }
    }
    Box(modifier.fillMaxWidth().padding(20.dp), contentAlignment = Alignment.TopCenter) {
        GlassSurface(RoundedCornerShape(Radius.panel), Modifier.widthIn(max = 560.dp).fillMaxWidth(), Glass.Card) {
            Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                QText(title, Quark.type.panelTitle)
                QText(hint, Quark.type.body, color = Quark.colors.textSecondary)
                if (openLabel != null) {
                    PillButton(
                        openLabel,
                        { if (inApp) browsing = true else onOpen() },
                        icon = Icons.Filled.OpenInBrowser,
                        accent = inApp,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                QTextField(
                    value = pasted,
                    onValueChange = { pasted = it },
                    placeholder = placeholder,
                    onSubmit = { if (pasted.isNotBlank()) onSubmit(pasted) },
                    modifier = Modifier.fillMaxWidth(),
                )
                if (error != null) QText(error, Quark.type.label, color = Quark.colors.danger)
                PillButton(
                    if (busy) s.loading else submitLabel,
                    { onSubmit(pasted) },
                    enabled = pasted.isNotBlank() && !busy,
                    accent = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                extra()
            }
        }
    }
}

// --- Yandex Music ----------------------------------------------------------------------

/**
 * Yandex Music: signing in (`yandex_login.dart`), then the hub — My Vibe, the
 * liked tracks and the chart, the user's playlists (`yandex_playlists_widget`)
 * and the new releases.
 */
@Composable
fun YandexHubScreen(model: YandexViewModel) {
    val shell = shell
    val s = strings
    val state by model.state.collectAsState()

    Column(Modifier.fillMaxSize()) {
        TopBar(
            title = s.yandexMusic,
            subtitle = (state as? YandexState.SignedIn)?.let { s.signedInAs(it.displayName) },
            onBack = { shell.navigator.pop() },
        ) {
            if (state is YandexState.SignedIn) {
                CircleButton(Icons.Filled.Search, { shell.navigator.push(Screen.Search) }, diameter = 36.dp, iconSize = 18.dp)
                MenuButton(diameter = 36.dp, iconSize = 18.dp, filled = true) {
                    Item(s.signOut, Icons.AutoMirrored.Filled.Logout, danger = true) {
                        shell.cache.forgetAll("yandex:")
                        model.signOut()
                    }
                }
            }
        }
        when (val current = state) {
            is YandexState.SignedIn -> YandexHome()
            YandexState.SigningIn -> Placeholder(s.signingIn, Modifier.fillMaxSize())
            is YandexState.Failed -> YandexSignIn(model, current.message)
            YandexState.SignedOut -> YandexSignIn(model, null)
        }
    }
}

@Composable
private fun YandexSignIn(model: YandexViewModel, error: String?) {
    val shell = shell
    val s = strings
    var problem by remember(error) { mutableStateOf(error) }
    SignInCard(
        title = s.signIn,
        hint = s.yandexSignInHint,
        openLabel = s.openAuthPage,
        onOpen = { shell.platform.openUrl(YandexOAuth.authorizeUrl) },
        placeholder = "https://music.yandex.ru/#access_token=…",
        submitLabel = s.signIn,
        error = problem,
        busy = false,
        onSubmit = { pasted ->
            val token = YandexOAuth.extractToken(pasted)
            if (token == null) problem = s.pasteAddressOrToken else model.signIn(token)
        },
        web = WebSignInSpec(YandexOAuth.authorizeUrl, extract = { address ->
            address.takeIf { "access_token=" in it }?.let(YandexOAuth::extractToken)
        }),
    )
}

@Composable
private fun YandexHome() {
    val shell = shell
    val s = strings
    val app = shell.app
    var generation by remember { mutableIntStateOf(0) }
    val playlists by rememberLoad("yandex:playlists", generation, shell.cache) { app.yandexCatalog.playlists() }
    val releases by rememberLoad("yandex:new-releases", generation, shell.cache) { app.yandexCatalog.newReleases() }
    val refresh: () -> Unit = {
        shell.cache.forget("yandex:playlists")
        shell.cache.forget("yandex:new-releases")
        generation++
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        val columns = columnsFor(maxWidth)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset + 16.dp)) {
            item(key = "vibe") { MyVibeCard(Modifier.padding(horizontal = 20.dp, vertical = 8.dp)) }
            item(key = "links") {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    PillButton(s.likedTracks, shell::openLiked, icon = Icons.Filled.Favorite, modifier = Modifier.weight(1f))
                    PillButton(s.chart, shell::openChart, icon = Icons.Filled.Insights, modifier = Modifier.weight(1f))
                }
            }
            item(key = "playlists-title") {
                SectionTitle(
                    s.yourPlaylists,
                    Modifier.padding(horizontal = 20.dp),
                    count = (playlists as? Load.Ready)?.value?.size,
                ) {
                    CircleButton(Icons.Filled.Refresh, refresh, diameter = 30.dp, iconSize = 16.dp)
                    CircleButton(Icons.Filled.Add, { shell.createYandexPlaylist(refresh) }, diameter = 30.dp, iconSize = 16.dp)
                }
            }
            loadedGrid("yandex-playlists", playlists, columns, refresh, s.emptyCollection, s.loading, s.retry)

            val fresh = (releases as? Load.Ready)?.value.orEmpty()
            if (fresh.isNotEmpty()) {
                item(key = "releases") {
                    Column(Modifier.padding(horizontal = 20.dp)) {
                        SectionTitle(s.newReleases)
                        Shelf(fresh)
                    }
                }
            }
        }
    }
}

/**
 * My Vibe (`yandex_music_my_vibe.dart`): the endless station, started from
 * here and marked while it plays.
 */
@Composable
private fun MyVibeCard(modifier: Modifier = Modifier) {
    val shell = shell
    val s = strings
    val wave by shell.app.wave.state.collectAsState()
    val player by shell.app.controller.state.collectAsState()
    val playing = player.playlistInfo.id == WaveSession.WAVE_ID
    val accent = Quark.accent
    val shape = RoundedCornerShape(24.dp)
    Row(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Brush.linearGradient(listOf(accent.primary.copy(alpha = 0.35f), accent.tertiary.copy(alpha = 0.2f))))
            .border(1.dp, accent.primary.copy(alpha = 0.35f), shape)
            .clickable(indication = null, interactionSource = null) {
                if (playing) shell.openPlayer() else shell.startMyVibe()
            }
            .padding(22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        QIcon(Icons.Filled.Waves, Modifier.size(44.dp), Quark.colors.text)
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            QText(s.myVibe, Quark.type.heading)
            QText(
                when (val current = wave) {
                    is WaveState.Failed -> current.message
                    WaveState.Starting -> s.loading
                    else -> s.myVibeHint
                },
                Quark.type.body,
                color = if (wave is WaveState.Failed) Quark.colors.danger else Quark.colors.textSecondary,
            )
        }
        CircleButton(
            icon = if (playing && player.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            onClick = {
                when {
                    playing -> shell.attempt { shell.app.controller.playPause() }
                    else -> shell.startMyVibe()
                }
            },
            diameter = 52.dp,
            iconSize = 26.dp,
        )
    }
}

// --- Spotify --------------------------------------------------------------------------------

/** Spotify (`spotify_login.dart`, `spotify_playlists_widget.dart`). */
@Composable
fun SpotifyScreen() {
    val shell = shell
    val s = strings
    val app = shell.app
    val settings by app.settings.settings.collectAsState()
    val signedIn = settings.spotify.isSignedIn
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TopBar(s.spotify, onBack = { shell.navigator.pop() }) {
            if (signedIn) {
                CircleButton(Icons.Filled.Refresh, { shell.cache.forget("spotify:playlists"); generation++ }, diameter = 36.dp, iconSize = 18.dp)
                MenuButton(diameter = 36.dp, iconSize = 18.dp, filled = true) {
                    Item(s.signOut, Icons.AutoMirrored.Filled.Logout, danger = true) {
                        app.integrations.spotify.signOut()
                        shell.cache.forgetAll("spotify:")
                    }
                }
            }
        }
        if (!signedIn) {
            SignInCard(
                title = s.spotify,
                hint = s.spotifySignInHint,
                openLabel = s.openSpotifySignIn,
                onOpen = { shell.platform.openUrl(app.integrations.spotify.authorizeUrl) },
                placeholder = "https://oauth.pstmn.io/v1/browser-callback?code=…",
                submitLabel = s.signIn,
                error = error,
                busy = busy,
                onSubmit = { pasted ->
                    busy = true
                    error = null
                    scope.launch {
                        val ok = runCatching { app.integrations.spotify.signIn(pasted) }
                        busy = false
                        error = when {
                            ok.isFailure -> ok.exceptionOrNull()?.message ?: s.somethingWentWrong
                            ok.getOrDefault(false) -> null
                            else -> s.pasteAddressOrCode
                        }
                    }
                },
                web = WebSignInSpec(app.integrations.spotify.authorizeUrl, extract = { address ->
                    address.takeIf { it.startsWith(SPOTIFY_REDIRECT) && "code=" in it }
                }),
            )
        } else {
            val playlists by rememberLoad("spotify:playlists", generation, shell.cache) { app.catalogs.spotifyPlaylists() }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = columnsFor(maxWidth)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset + 16.dp)) {
                    item(key = "title") { SectionTitle(s.playlistsTitle, Modifier.padding(horizontal = 20.dp), count = (playlists as? Load.Ready)?.value?.size) }
                    loadedGrid("spotify", playlists, columns, { generation++ }, s.emptyCollection, s.loading, s.retry)
                }
            }
        }
    }
}

// --- SoundCloud -----------------------------------------------------------------------------

/**
 * SoundCloud (`soundcloud_playlists_widget.dart`): a profile's playlists,
 * likes and tracks by its link, and playlists found by searching.
 */
@Composable
fun SoundCloudScreen() {
    val shell = shell
    val s = strings
    val app = shell.app
    val settings by app.settings.settings.collectAsState()
    var link by remember { mutableStateOf(settings.soundCloud.profileUrl) }
    var opened by remember { mutableStateOf(settings.soundCloud.profileUrl.takeIf(String::isNotBlank)) }
    var query by remember { mutableStateOf("") }
    var searched by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    Column(Modifier.fillMaxSize()) {
        TopBar(s.soundCloud, onBack = { shell.navigator.pop() })
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val columns = columnsFor(maxWidth)
            val profile = opened?.let { url ->
                rememberLoad("soundcloud:profile:$url", generation, shell.cache) { app.catalogs.soundCloudProfile(url) }.value
            }
            val found = searched?.let { q ->
                rememberLoad("soundcloud:search:$q", generation, shell.cache) { app.catalogs.soundCloudSearchPlaylists(q) }.value
            }
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset + 16.dp)) {
                item(key = "inputs") {
                    Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        QText(s.soundCloudProfileHint, Quark.type.body, color = Quark.colors.textSecondary)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            QTextField(
                                value = link,
                                onValueChange = { link = it },
                                placeholder = "https://soundcloud.com/…",
                                onSubmit = { if (link.isNotBlank()) opened = link.trim() },
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(s.openAction, { if (link.isNotBlank()) opened = link.trim() }, enabled = link.isNotBlank())
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            QTextField(
                                value = query,
                                onValueChange = { query = it },
                                placeholder = s.searchPlaylistsOnline,
                                leading = Icons.Filled.Search,
                                onSubmit = { if (query.isNotBlank()) searched = query.trim() },
                                modifier = Modifier.weight(1f),
                            )
                            PillButton(s.search, { if (query.isNotBlank()) searched = query.trim() }, enabled = query.isNotBlank())
                        }
                    }
                }
                if (profile != null) {
                    item(key = "profile-title") {
                        SectionTitle(
                            (profile as? Load.Ready)?.value?.first ?: s.profile,
                            Modifier.padding(horizontal = 20.dp),
                        )
                    }
                    val mapped: Load<List<CollectionSummary>> = when (profile) {
                        is Load.Ready -> Load.Ready(profile.value.second)
                        is Load.Failed -> profile
                        Load.Loading -> Load.Loading
                    }
                    loadedGrid("soundcloud-profile", mapped, columns, { generation++ }, s.emptyCollection, s.loading, s.retry)
                }
                if (found != null) {
                    item(key = "search-title") { SectionTitle(s.playlistsTitle, Modifier.padding(horizontal = 20.dp)) }
                    loadedGrid("soundcloud-search", found, columns, { generation++ }, s.nothingFound, s.loading, s.retry)
                }
            }
        }
    }
}

// --- VK -------------------------------------------------------------------------------------

/**
 * VK Music (`vkmusic_playlist_widget.dart`, `vk_login.dart`), which the quark
 * backend fronts: connecting takes a quark account and a token from Kate
 * Mobile's OAuth page, or a login and password.
 */
@Composable
fun VkScreen() {
    val shell = shell
    val s = strings
    val app = shell.app
    val settings by app.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var generation by remember { mutableIntStateOf(0) }

    val connect: (suspend () -> String?) -> Unit = { attempt ->
        busy = true
        error = null
        scope.launch {
            try {
                val userId = attempt() ?: app.integrations.vk.status().vkUserId
                app.settings.update { it.copy(vk = it.vk.copy(userId = userId?.ifBlank { null } ?: "connected")) }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                error = e.message ?: s.somethingWentWrong
            } finally {
                busy = false
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(s.vkMusic, onBack = { shell.navigator.pop() }) {
            if (settings.vk.isConnected) {
                CircleButton(Icons.Filled.Refresh, { shell.cache.forget("vk:library"); generation++ }, diameter = 36.dp, iconSize = 18.dp)
                MenuButton(diameter = 36.dp, iconSize = 18.dp, filled = true) {
                    Item(s.disconnect, Icons.AutoMirrored.Filled.Logout, danger = true) {
                        shell.attempt {
                            runCatching { app.integrations.vk.disconnect() }
                            app.settings.update { it.copy(vk = it.vk.copy(userId = "")) }
                            shell.cache.forgetAll("vk:")
                        }
                    }
                }
            }
        }
        when {
            !settings.account.isSignedIn -> Placeholder(
                s.vkNeedsAccount,
                Modifier.fillMaxSize(),
                action = s.login,
                onAction = { shell.navigator.push(Screen.Account) },
            )
            !settings.vk.isConnected -> {
                var login by remember { mutableStateOf("") }
                var password by remember { mutableStateOf("") }
                SignInCard(
                    title = s.connect,
                    hint = s.vkConnectHint,
                    openLabel = s.openVkSignIn,
                    onOpen = { shell.platform.openUrl(VK_AUTH_URL) },
                    placeholder = "https://oauth.vk.com/blank.html#access_token=…",
                    submitLabel = s.connect,
                    error = error,
                    busy = busy,
                    onSubmit = { pasted ->
                        val token = vkToken(pasted)
                        if (token == null) error = s.pasteAddressOrToken
                        else connect { app.integrations.account.saveVkToken(token); null }
                    },
                    web = WebSignInSpec(
                        VK_AUTH_URL.replace("display=page", "display=mobile"),
                        extract = { address -> address.takeIf { "blank.html" in it && "access_token=" in it } },
                        userAgent = "Mozilla/5.0 (iPhone; CPU iPhone OS 16_0 like Mac OS X) AppleWebKit/605.1.15 Mobile/15E148",
                    ),
                ) {
                    QText(s.orSignInWithPassword, Quark.type.label, color = Quark.colors.textMuted)
                    QTextField(login, { login = it }, placeholder = s.emailOrUsername, modifier = Modifier.fillMaxWidth())
                    QTextField(password, { password = it }, placeholder = s.password, password = true, modifier = Modifier.fillMaxWidth())
                    PillButton(
                        s.signIn,
                        { connect { app.integrations.account.loginVk(login.trim(), password) } },
                        enabled = login.isNotBlank() && password.isNotBlank() && !busy,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
            else -> {
                val library by rememberLoad("vk:library", generation, shell.cache) { app.catalogs.vkLibrary() }
                BoxWithConstraints(Modifier.fillMaxSize()) {
                    val columns = columnsFor(maxWidth)
                    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset + 16.dp)) {
                        loadedGrid("vk", library, columns, { generation++ }, s.emptyCollection, s.loading, s.retry)
                    }
                }
            }
        }
    }
}

/** The token out of the address Kate's OAuth page ends on, or the token itself. */
private fun vkToken(pasted: String): String? {
    val text = pasted.trim()
    val fromUrl = Regex("access_token=([^&#\\s]+)").find(text)?.groupValues?.get(1)
    if (fromUrl != null) return fromUrl
    return text.takeIf { it.length >= 40 && it.none(Char::isWhitespace) && !it.contains('/') }
}

private val SPOTIFY_REDIRECT = com.quark.network.spotify.SpotifyClient.REDIRECT_URI

private const val VK_AUTH_URL = "https://oauth.vk.com/authorize?client_id=2685278&scope=audio,offline" +
    "&redirect_uri=https://oauth.vk.com/blank.html&display=page&response_type=token&revoke=1"

// --- YouTube Music ------------------------------------------------------------------------------

/**
 * YouTube Music (`ytmusic_playlist_widget.dart` and the cookie drop zone):
 * the playlists of the account whose browser cookies were given.
 */
@Composable
fun YouTubeScreen() {
    val shell = shell
    val s = strings
    val app = shell.app
    val settings by app.settings.settings.collectAsState()
    val scope = rememberCoroutineScope()
    var generation by remember { mutableIntStateOf(0) }
    val hasCookies = settings.youtube.cookies.isNotBlank()

    val pickCookies: () -> Unit = {
        scope.launch {
            val location = shell.platform.pickFile(listOf("txt")) ?: return@launch
            val text = shell.platform.readText(location)
            if (text.isNullOrBlank()) {
                shell.messages.show(s.somethingWentWrong, error = true)
            } else {
                app.settings.update { it.copy(youtube = it.youtube.copy(cookies = text)) }
                shell.cache.forgetAll("youtube:")
                generation++
                shell.messages.show(s.cookiesSaved)
            }
        }
    }

    Column(Modifier.fillMaxSize()) {
        TopBar(s.youtubeMusic, onBack = { shell.navigator.pop() }) {
            CircleButton(Icons.Filled.Search, { shell.navigator.push(Screen.Search) }, diameter = 36.dp, iconSize = 18.dp)
            if (hasCookies) {
                MenuButton(diameter = 36.dp, iconSize = 18.dp, filled = true) {
                    Item(s.chooseCookieFile, Icons.Filled.FileOpen, onClick = pickCookies)
                    Item(s.forgetCookies, Icons.AutoMirrored.Filled.Logout, danger = true) {
                        app.settings.update { it.copy(youtube = it.youtube.copy(cookies = "")) }
                        shell.cache.forgetAll("youtube:")
                    }
                }
            }
        }
        if (!hasCookies) {
            Placeholder(
                s.youtubeCookiesHint,
                Modifier.fillMaxSize(),
                action = s.chooseCookieFile,
                onAction = pickCookies,
            )
        } else {
            val playlists by rememberLoad("youtube:playlists", generation, shell.cache) { app.catalogs.youtubePlaylists() }
            BoxWithConstraints(Modifier.fillMaxSize()) {
                val columns = columnsFor(maxWidth)
                LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottomInset + 16.dp)) {
                    item(key = "title") { SectionTitle(s.playlistsTitle, Modifier.padding(horizontal = 20.dp), count = (playlists as? Load.Ready)?.value?.size) }
                    loadedGrid("youtube", playlists, columns, { shell.cache.forget("youtube:playlists"); generation++ }, s.emptyCollection, s.loading, s.retry)
                }
            }
        }
    }
}
