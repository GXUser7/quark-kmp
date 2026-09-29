package com.quark.app

import com.quark.app.yandex.YandexSession
import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import com.quark.core.model.YtMusicTrack
import com.quark.core.util.TimedCache
import com.quark.data.files.Files
import com.quark.data.net.DownloadSource
import com.quark.player.MediaInfo
import com.quark.player.MediaSource
import com.quark.player.SourceResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.minutes

/**
 * Turns a track into something the engine can open.
 *
 * A remote track that has already been downloaded plays from disk; otherwise a
 * signed url is fetched. Those urls expire, so they are cached for less time
 * than they last — the Dart build used thirty minutes
 * (`objects/track.dart:325`), and twenty-five leaves room for a track that
 * starts just before the entry would have gone stale.
 *
 * Sources other than the built-in ones register a [RemoteSource]; the resolver
 * asks each in turn, which is how Spotify, SoundCloud and VK plug in without
 * this class knowing about them.
 */
class QuarkSourceResolver(
    private val yandex: YandexSession,
) : SourceResolver {

    /** A streaming service that can say where a track's audio is right now. */
    fun interface RemoteSource {
        /** Null when the track is not this source's; throws when it is and fails. */
        suspend fun resolve(track: Track): DownloadSource?
    }

    private val urls = TimedCache<String, String>(URL_LIFETIME)
    private val lock = Mutex()
    private val extra = mutableListOf<RemoteSource>()

    fun register(source: RemoteSource) {
        extra += source
    }

    override suspend fun resolve(track: Track): MediaSource {
        val info = track.mediaInfo()
        if (track is LocalTrack) return MediaSource.LocalFile(track.filepath, info)
        downloadedFile(track)?.let { return MediaSource.LocalFile(it, info) }
        val remote = downloadSource(track) ?: error("No remote source for ${track.filepath}")
        return MediaSource.Network(remote.url, remote.headers, info)
    }

    /** Resolves a fresh network request even when a cached file already exists. */
    suspend fun downloadSource(track: Track): DownloadSource? = when (track) {
        is LocalTrack -> null
        is YandexTrack -> {
            val api = yandex.api ?: error("Not signed in to Yandex Music")
            val url = lock.withLock {
                urls.getOrPut(track.trackId) { api.downloadUrl(it, yandex.quality) }
            }
            DownloadSource(url)
        }
        // A fresh url from the backend first — the one a playlist came with
        // expires within hours — and that one only when the backend is out of
        // reach. Its urls work for a client claiming to be the Android app.
        is YtMusicTrack -> runCatching { fromRegistered(track) }.getOrNull()
            ?: track.streamUrl?.let { DownloadSource(it, mapOf("User-Agent" to YOUTUBE_USER_AGENT)) }
        else -> fromRegistered(track)
    }

    private suspend fun fromRegistered(track: Track): DownloadSource? {
        var failure: Throwable? = null
        for (source in extra) {
            try {
                source.resolve(track)?.let { return it }
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
            }
        }
        failure?.let { throw it }
        return null
    }

    /** A remote track whose file is already cached plays from there. */
    private fun downloadedFile(track: Track): String? =
        track.filepath.takeIf { it.isNotEmpty() && Files.exists(it) }

    private companion object {
        val URL_LIFETIME = 25.minutes
        const val YOUTUBE_USER_AGENT =
            "com.google.android.youtube/17.31.35 (Linux; U; Android 13; en_US) gzip"
    }
}

/** What the system media controls show for [this]. */
fun Track.mediaInfo(): MediaInfo = MediaInfo(
    title = title,
    artist = artistLine,
    album = albumLine,
    artworkUrl = cover.takeIf { coverType == CoverType.Url && it.startsWith("http") },
    durationMs = durationMs,
)
