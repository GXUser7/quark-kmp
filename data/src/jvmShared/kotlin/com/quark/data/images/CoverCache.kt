package com.quark.data.images

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsBytes
import io.ktor.http.isSuccess
import io.ktor.client.statement.HttpResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.exists
import kotlin.io.path.readBytes

/**
 * Remote cover art, kept on disk.
 *
 * Files are named by the md5 of their url, as the Dart build named them
 * (`cached_images.dart:293`), so a cache written by the Flutter version is still
 * read here. Downloads for the same url are collapsed: a playlist view asks for
 * the same album cover once per track, and without this each row would fetch it.
 */
class CoverCache(
    private val directory: Path,
    private val http: HttpClient,
) : ImageStore {
    private val inFlight = mutableMapOf<String, Mutex>()
    private val guard = Mutex()

    override suspend fun get(url: String): ByteArray? {
        if (url.isEmpty()) return null
        val file = fileFor(url)

        readIfPresent(file)?.let { return it }

        val lock = guard.withLock { inFlight.getOrPut(url) { Mutex() } }
        return lock.withLock {
            // Another caller may have finished while this one waited.
            readIfPresent(file)?.let { return@withLock it }

            val bytes = download(url) ?: return@withLock null
            withContext(Dispatchers.IO) {
                runCatching {
                    Files.createDirectories(file.parent)
                    Files.write(file, bytes)
                }
            }
            bytes
        }.also {
            guard.withLock { inFlight.remove(url) }
        }
    }

    /** The path a url's bytes would be cached at, whether or not they are. */
    fun fileFor(url: String): Path = directory.resolve(md5(url))

    suspend fun isCached(url: String): Boolean =
        withContext(Dispatchers.IO) { fileFor(url).exists() }

    private suspend fun readIfPresent(file: Path): ByteArray? = withContext(Dispatchers.IO) {
        if (file.exists()) runCatching { file.readBytes() }.getOrNull() else null
    }

    private suspend fun download(url: String): ByteArray? = try {
        val response: HttpResponse = http.get(url)
        if (response.status.isSuccess()) response.bodyAsBytes() else null
    } catch (e: Exception) {
        null
    }

    private fun md5(value: String): String = MessageDigest.getInstance("MD5")
        .digest(value.encodeToByteArray())
        .joinToString("") { byte -> (byte.toInt() and 0xff).toString(16).padStart(2, '0') }
}
