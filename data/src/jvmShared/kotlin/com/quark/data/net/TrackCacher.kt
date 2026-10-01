package com.quark.data.net

import com.quark.core.model.Playlist
import com.quark.core.model.Track
import com.quark.core.model.TrackSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.isSuccess
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.UUID

/**
 * Downloads remote tracks into the paths already carried by their models.
 * Work is deduplicated by destination and bounded globally, so several UI
 * features can request the same track without truncating each other's file.
 */
class TrackCacher(
    private val http: HttpClient,
    private val io: CoroutineDispatcher = Dispatchers.IO,
    maxConcurrent: Int = 8,
    private val maxAttempts: Int = 3,
    private val retryDelay: suspend (failedAttempt: Int) -> Unit = { attempt ->
        delay((1L shl attempt) * 1_000L)
    },
) : TrackDownloader {
    private val permits = Semaphore(maxConcurrent)
    private val activeLock = Mutex()
    private val active = mutableSetOf<Path>()

    override suspend fun cacheAround(
        playlist: Playlist,
        current: Track,
        source: suspend (Track) -> DownloadSource?,
    ): List<String> = cache(window(playlist.tracks, current), source)

    override suspend fun cache(
        tracks: List<Track>,
        source: suspend (Track) -> DownloadSource?,
    ): List<String> = coroutineScope {
        tracks.distinctBy(Track::filepath).map { track ->
            async { runCatching { cacheOne(track, source) }.getOrNull() }
        }.awaitAll().filterNotNull().map(Path::toString)
    }

    internal fun window(tracks: List<Track>, current: Track): List<Track> {
        if (tracks.isEmpty()) return emptyList()
        val center = tracks.indexOf(current)
        if (center < 0) return listOf(current).filterNot { it.source == TrackSource.Local }
        return (-1..1)
            .map { offset -> tracks[Math.floorMod(center + offset, tracks.size)] }
            .distinctBy(Track::filepath)
            .filterNot { it.source == TrackSource.Local }
    }

    private suspend fun cacheOne(
        track: Track,
        source: suspend (Track) -> DownloadSource?,
    ): Path? {
        if (track.source == TrackSource.Local || track.filepath.isBlank()) return null
        val target = runCatching { Paths.get(track.filepath) }.getOrNull() ?: return null
        if (Files.isRegularFile(target)) return target

        val claimed = activeLock.withLock { active.add(target) }
        if (!claimed) return null
        return try {
            permits.withPermit { downloadWithRetry(track, target, source) }
        } finally {
            activeLock.withLock { active.remove(target) }
        }
    }

    private suspend fun downloadWithRetry(
        track: Track,
        target: Path,
        source: suspend (Track) -> DownloadSource?,
    ): Path? {
        var lastFailure: Throwable? = null
        for (attempt in 1..maxAttempts) {
            try {
                val remote = source(track) ?: return null
                download(remote, target)
                return target
            } catch (failure: Throwable) {
                if (failure is kotlinx.coroutines.CancellationException) throw failure
                lastFailure = failure
                if (attempt < maxAttempts) retryDelay(attempt)
            }
        }
        throw lastFailure ?: IllegalStateException("Caching failed without an error")
    }

    override suspend fun downloadTo(source: DownloadSource, target: String) =
        permits.withPermit { download(source, Paths.get(target)) }

    private suspend fun download(source: DownloadSource, target: Path) {
        val response = http.get(source.url) {
            headers { source.headers.forEach { (name, value) -> append(name, value) } }
        }
        check(response.status.isSuccess()) { "Download failed with HTTP ${response.status.value}" }

        val temp = target.resolveSibling("${target.fileName}.part-${UUID.randomUUID()}")
        try {
            withContext(io) {
                Files.createDirectories(target.parent)
                val channel = response.bodyAsChannel()
                Files.newOutputStream(temp).use { output ->
                    val buffer = ByteArray(BUFFER_SIZE)
                    while (true) {
                        val read = channel.readAvailable(buffer)
                        if (read < 0) break
                        if (read > 0) output.write(buffer, 0, read)
                    }
                }
                check(Files.size(temp) > 0L) { "Downloaded file is empty" }
                try {
                    Files.move(
                        temp,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE,
                    )
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING)
                }
            }
        } finally {
            Files.deleteIfExists(temp)
        }
    }

    private companion object {
        const val BUFFER_SIZE = 64 * 1_024
    }
}
