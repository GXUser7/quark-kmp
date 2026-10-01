package com.quark.app.integrations

import com.quark.app.QuarkSourceResolver
import com.quark.core.model.ServiceTrack
import com.quark.core.model.Track
import com.quark.core.model.TrackSource
import com.quark.core.model.YtMusicTrack
import com.quark.core.settings.SettingsStore
import com.quark.core.util.TimedCache
import com.quark.data.net.DownloadSource
import com.quark.network.quark.PlaylistSync
import com.quark.network.quark.QuarkAccount
import com.quark.network.quark.QuarkTokenStore
import com.quark.network.quark.QuarkTokens
import com.quark.network.soundcloud.SoundCloudClient
import com.quark.network.spotify.SpotifyClient
import com.quark.network.spotify.SpotifyQuality
import com.quark.network.spotify.SpotifyTokenStore
import com.quark.network.spotify.SpotifyTokens
import com.quark.network.vk.VkClient
import com.quark.network.vk.VkSong
import com.quark.network.ytmusic.YtMusicClient
import io.ktor.client.HttpClient
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes

/**
 * The streaming services besides Yandex, and the quark account, each with its
 * credentials kept in the settings file.
 *
 * Every one of them was a singleton in the Dart build, reachable from any
 * widget; here they are built once, handed out from [com.quark.app.QuarkApp],
 * and plugged into the source resolver so tracks of any service play the same
 * way.
 */
class Integrations(
    private val settings: SettingsStore,
    http: HttpClient,
) {
    val account = QuarkAccount(http, AccountTokens(settings))
    val sync = PlaylistSync(http, account)
    val youtube = YtMusicClient()
    val vk = VkClient(http, account)
    val soundCloud = SoundCloudClient(http) { settings.current.soundCloud.oauthToken }
    val spotify = SpotifyClient(http, SpotifyTokenStorage(settings), youtube) {
        when (settings.current.spotify.quality) {
            "hires" -> SpotifyQuality.HiRes
            "lossless" -> SpotifyQuality.Lossless
            else -> SpotifyQuality.High
        }
    }

    /** Stream urls expire; they are kept for less time than they last, as Yandex's are. */
    private val streams = TimedCache<String, String>(25.minutes)
    private val streamLock = Mutex()

    /** Teaches [resolver] where each service's audio is. */
    fun register(resolver: QuarkSourceResolver) {
        resolver.register { track -> resolve(track) }
    }

    private suspend fun resolve(track: Track): DownloadSource? = when (track) {
        is YtMusicTrack -> DownloadSource(
            cached("youtube:${track.videoId}") {
                youtube.song(track.videoId).streamUrl ?: error("No stream for ${track.videoId}")
            },
            mapOf("User-Agent" to YOUTUBE_USER_AGENT),
        )

        is ServiceTrack -> when (track.source) {
            TrackSource.Spotify -> DownloadSource(
                cached("spotify:${track.id}") {
                    spotify.streamUrl(track) ?: error("No playable copy of ${track.title} was found")
                }
            )

            TrackSource.SoundCloud -> DownloadSource(
                cached("soundcloud:${track.id}") {
                    soundCloud.streamUrl(track.id.toLong()) ?: error("SoundCloud has no stream for ${track.title}")
                }
            )

            // The backend hands out the url with the song; there is no way to
            // ask for it again short of reloading the list it came in.
            TrackSource.Vk -> track.extras[VkSong.EXTRA_URL]?.let { DownloadSource(it) }
                ?: error("VK gave no url for ${track.title}")

            else -> null
        }

        else -> null
    }

    private suspend fun cached(key: String, fetch: suspend () -> String): String =
        streamLock.withLock { streams.getOrPut(key) { fetch() } }

    private class AccountTokens(private val settings: SettingsStore) : QuarkTokenStore {
        override fun load(): QuarkTokens? = settings.current.account.let {
            if (it.accessToken.isEmpty()) null else QuarkTokens(it.accessToken, it.refreshToken)
        }

        override fun save(tokens: QuarkTokens?) = settings.update {
            it.copy(
                account = it.account.copy(
                    accessToken = tokens?.access.orEmpty(),
                    refreshToken = tokens?.refresh.orEmpty(),
                    username = if (tokens == null) "" else it.account.username,
                    email = if (tokens == null) "" else it.account.email,
                )
            )
        }
    }

    private class SpotifyTokenStorage(private val settings: SettingsStore) : SpotifyTokenStore {
        override fun load(): SpotifyTokens? = settings.current.spotify.let {
            if (it.refreshToken.isEmpty()) null else SpotifyTokens(it.accessToken, it.refreshToken)
        }

        override fun save(tokens: SpotifyTokens?) = settings.update {
            it.copy(
                spotify = it.spotify.copy(
                    accessToken = tokens?.access.orEmpty(),
                    refreshToken = tokens?.refresh.orEmpty(),
                )
            )
        }
    }

    private companion object {
        const val YOUTUBE_USER_AGENT =
            "com.google.android.youtube/17.31.35 (Linux; U; Android 13; en_US) gzip"
    }
}
