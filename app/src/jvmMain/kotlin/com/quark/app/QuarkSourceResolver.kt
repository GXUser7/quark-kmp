package com.quark.app

import com.quark.app.yandex.YandexSession
import com.quark.core.model.LocalTrack
import com.quark.core.model.Track
import com.quark.core.model.YandexTrack
import com.quark.core.model.YtMusicTrack
import com.quark.core.util.TimedCache
import com.quark.data.net.DownloadSource
import com.quark.player.MediaSource
import com.quark.player.SourceResolver
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.time.Duration.Companion.minutes

/**
 * Turns a track into something the engine can open.
 *
 * A remote track that has already been downloaded plays from disk; otherwise a
 * signed url is fetched. Those urls expire, so they are cached for less time
 * than they last — the Dart build used thirty minutes
 * (`objects/track.dart:325`), and twenty-five leaves room for a track that
 * starts just before the entry would have gone stale.
 */
class QuarkSourceResolver(
    private val yandex: YandexSession,
) : SourceResolver {

    private val urls = TimedCache<String, String>(URL_LIFETIME)
    private val lock = Mutex()

    override suspend fun resolve(track: Track): MediaSource = when (track) {
        is LocalTrack -> MediaSource.LocalFile(track.filepath)
        else -> downloadedFile(track) ?: downloadSource(track)?.let {
            MediaSource.Network(it.url, it.headers)
        } ?: error("No remote source for ${track.filepath}")
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
        is YtMusicTrack -> {
            val url = track.streamUrl ?: error("No stream url for ${track.videoId}")
            // The backend hands out urls that only work for a client claiming
            // to be the Android app, as in the Dart build.
            DownloadSource(url, mapOf("User-Agent" to YOUTUBE_USER_AGENT))
        }
    }

    /** A remote track whose file is already cached plays from there. */
    private fun downloadedFile(track: Track): MediaSource.LocalFile? {
        if (track.filepath.isEmpty()) return null
        val path = runCatching { Paths.get(track.filepath) }.getOrNull() ?: return null
        return if (Files.isRegularFile(path)) MediaSource.LocalFile(track.filepath) else null
    }

    private companion object {
        val URL_LIFETIME = 25.minutes
        const val YOUTUBE_USER_AGENT =
            "com.google.android.youtube/17.31.35 (Linux; U; Android 13; en_US) gzip"
    }
}
