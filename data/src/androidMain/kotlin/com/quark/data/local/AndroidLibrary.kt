package com.quark.data.local

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * [LocalLibrary] on Android.
 *
 * Three kinds of location arrive here:
 *  - [DEVICE_LIBRARY], the whole media store — what "Music on this device"
 *    opens, and the fastest by far because Android has already read the tags;
 *  - storage-access trees and documents (`content://`) from the folder and file
 *    pickers, which are walked and read one file at a time;
 *  - plain paths, for files handed over by another app.
 *
 * A track's `filepath` is whatever the player can open again later: the media
 * store or document uri, or the path. ExoPlayer and the metadata retriever take
 * all three.
 */
class AndroidLibrary(
    context: Context,
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : LocalLibrary {

    private val context = context.applicationContext
    private val resolver get() = context.contentResolver

    override fun scan(locations: List<String>, recursive: Boolean): Flow<ScanResult> = flow {
        val entries = collect(locations, recursive)
        emit(ScanResult.Progress(ScanProgress(entries.size, 0, null)))

        val tracks = ArrayList<LocalTrack>(entries.size)
        for ((index, batch) in entries.chunked(BATCH).withIndex()) {
            currentCoroutineContext().ensureActive()
            tracks += readBatch(batch)
            emit(
                ScanResult.Progress(
                    ScanProgress(entries.size, minOf(entries.size, (index + 1) * BATCH), batch.last().name)
                )
            )
        }
        emit(ScanResult.Done(tracks))
    }.flowOn(io)

    override suspend fun scanAll(locations: List<String>, recursive: Boolean): List<LocalTrack> =
        withContext(io) { readBatch(collect(locations, recursive)) }

    override suspend fun artwork(track: Track): ByteArray? = withContext(io) {
        when (track.coverType) {
            CoverType.BuiltIn -> embeddedPicture(track.filepath)
            CoverType.ExternalFile -> readBytes(track.cover)
            else -> null
        }
    }

    override fun isFolder(location: String): Boolean = when {
        location == DEVICE_LIBRARY -> true
        location.startsWith("content://") ->
            runCatching { DocumentsContract.isTreeUri(Uri.parse(location)) }.getOrDefault(false)
        else -> File(location).isDirectory
    }

    override fun displayName(location: String): String = when {
        location == DEVICE_LIBRARY -> "Device"
        location.startsWith("content://") -> {
            val uri = Uri.parse(location)
            val document = if (DocumentsContract.isTreeUri(uri)) DocumentFile.fromTreeUri(context, uri)
            else DocumentFile.fromSingleUri(context, uri)
            document?.name ?: uri.lastPathSegment ?: location
        }
        else -> File(location).name
    }

    // --- Discovery ------------------------------------------------------------

    /** A file found but not read yet; media store rows arrive already read. */
    private sealed interface Entry {
        val name: String

        data class Document(val uri: String, override val name: String, val cover: String?) : Entry
        data class Known(val track: LocalTrack) : Entry {
            override val name: String get() = track.title
        }
    }

    private fun collect(locations: List<String>, recursive: Boolean): List<Entry> {
        val found = mutableListOf<Entry>()
        for (location in locations) {
            when {
                location == DEVICE_LIBRARY -> found += mediaStore()
                location.startsWith("content://") -> {
                    val uri = Uri.parse(location)
                    if (DocumentsContract.isTreeUri(uri)) {
                        DocumentFile.fromTreeUri(context, uri)?.let { walk(it, recursive, found) }
                    } else {
                        val name = DocumentFile.fromSingleUri(context, uri)?.name ?: uri.toString()
                        found += Entry.Document(location, name, cover = null)
                    }
                }
                else -> {
                    val file = File(location)
                    if (file.isDirectory) walk(file, recursive, found)
                    else if (file.isFile) found += Entry.Document(file.absolutePath, file.name, coverBeside(file))
                }
            }
        }
        return found
    }

    private fun walk(folder: DocumentFile, recursive: Boolean, into: MutableList<Entry>) {
        val children = folder.listFiles().sortedBy { it.name?.lowercase().orEmpty() }
        val cover = children.firstOrNull { child ->
            val name = child.name?.lowercase() ?: return@firstOrNull false
            child.isFile && COVER_NAMES.any { base -> COVER_EXTENSIONS.any { name == "$base.$it" } }
        }?.uri?.toString()

        for (child in children) {
            if (child.isDirectory) {
                if (recursive) walk(child, true, into)
            } else if (child.isFile && isAudio(child.name, child.type)) {
                into += Entry.Document(child.uri.toString(), child.name ?: child.uri.toString(), cover)
            }
        }
    }

    private fun walk(folder: File, recursive: Boolean, into: MutableList<Entry>) {
        val children = folder.listFiles()?.sortedBy { it.name.lowercase() } ?: return
        for (child in children) {
            when {
                child.isDirectory -> if (recursive) walk(child, true, into)
                isAudio(child.name, null) -> into += Entry.Document(child.absolutePath, child.name, coverBeside(child))
            }
        }
    }

    private fun mediaStore(): List<Entry> {
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val entries = mutableListOf<Entry>()
        resolver.query(
            collection,
            projection,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0",
            null,
            "${MediaStore.Audio.Media.ARTIST} COLLATE NOCASE, ${MediaStore.Audio.Media.ALBUM} COLLATE NOCASE, ${MediaStore.Audio.Media.TRACK}",
        )?.use { cursor ->
            val id = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val title = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artist = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val album = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val duration = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val displayName = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME)
            while (cursor.moveToNext()) {
                val uri = ContentUris.withAppendedId(collection, cursor.getLong(id)).toString()
                val name = cursor.getString(displayName).orEmpty()
                entries += Entry.Known(
                    LocalTrack(
                        title = cursor.getString(title)?.takeIf { it.isNotBlank() && it != UNKNOWN }
                            ?: name.substringBeforeLast('.').ifBlank { Track.UNKNOWN_TITLE },
                        artists = splitArtists(cursor.getString(artist)),
                        albums = listOf(
                            cursor.getString(album)?.takeIf { it.isNotBlank() && it != UNKNOWN }
                                ?: Track.UNKNOWN_ALBUM
                        ),
                        filepath = uri,
                        coverType = CoverType.BuiltIn,
                        cover = uri,
                        durationMs = cursor.getLong(duration),
                    )
                )
            }
        }
        return entries
    }

    // --- Reading --------------------------------------------------------------

    private suspend fun readBatch(entries: List<Entry>): List<LocalTrack> = coroutineScope {
        entries.chunked(PARALLEL_CHUNK).flatMap { chunk ->
            chunk.map { entry ->
                async(io) {
                    when (entry) {
                        is Entry.Known -> entry.track
                        is Entry.Document -> read(entry)
                    }
                }
            }.awaitAll()
        }
    }

    private fun read(entry: Entry.Document): LocalTrack {
        val fallbackTitle = entry.name.substringBeforeLast('.').ifBlank { entry.name }
        val retriever = MediaMetadataRetriever()
        return try {
            setSource(retriever, entry.uri)
            val embedded = retriever.embeddedPicture != null
            LocalTrack(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?.takeIf(String::isNotBlank) ?: fallbackTitle,
                artists = splitArtists(retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)),
                albums = listOf(
                    retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM)
                        ?.takeIf(String::isNotBlank) ?: Track.UNKNOWN_ALBUM
                ),
                filepath = entry.uri,
                coverType = when {
                    embedded -> CoverType.BuiltIn
                    entry.cover != null -> CoverType.ExternalFile
                    else -> CoverType.NoCover
                },
                cover = if (!embedded && entry.cover != null) entry.cover else entry.uri,
                durationMs = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull() ?: 0L,
            )
        } catch (_: Exception) {
            // Unreadable tags do not make the file unplayable; list it by name,
            // as the desktop reader does.
            LocalTrack(
                title = fallbackTitle,
                artists = listOf(Track.UNKNOWN_ARTIST),
                albums = listOf(Track.UNKNOWN_ALBUM),
                filepath = entry.uri,
                coverType = if (entry.cover != null) CoverType.ExternalFile else CoverType.NoCover,
                cover = entry.cover ?: entry.uri,
            )
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun embeddedPicture(location: String): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            setSource(retriever, location)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun setSource(retriever: MediaMetadataRetriever, location: String) {
        if (location.startsWith("content://") || location.startsWith("file://")) {
            retriever.setDataSource(context, Uri.parse(location))
        } else {
            retriever.setDataSource(location)
        }
    }

    private fun readBytes(location: String): ByteArray? = runCatching {
        if (location.startsWith("content://")) {
            resolver.openInputStream(Uri.parse(location))?.use { it.readBytes() }
        } else {
            File(location).takeIf(File::isFile)?.readBytes()
        }
    }.getOrNull()

    private fun coverBeside(file: File): String? {
        val folder = file.parentFile ?: return null
        for (name in COVER_NAMES) for (extension in COVER_EXTENSIONS) {
            val candidate = File(folder, "$name.$extension")
            if (candidate.isFile) return candidate.absolutePath
        }
        return null
    }

    private fun isAudio(name: String?, mime: String?): Boolean {
        if (mime != null && mime.startsWith("audio/")) return true
        val extension = name?.substringAfterLast('.', "")?.lowercase() ?: return false
        return extension in AUDIO_EXTENSIONS
    }

    private fun splitArtists(value: String?): List<String> {
        if (value.isNullOrBlank() || value == UNKNOWN) return listOf(Track.UNKNOWN_ARTIST)
        return value.split(ARTIST_SEPARATORS).map(String::trim).filter(String::isNotEmpty)
            .ifEmpty { listOf(Track.UNKNOWN_ARTIST) }
    }

    companion object {
        /** The location standing for everything the media store knows about. */
        const val DEVICE_LIBRARY = "quark://device-library"

        private const val UNKNOWN = "<unknown>"
        private const val BATCH = 25
        private const val PARALLEL_CHUNK = 4

        private val ARTIST_SEPARATORS = Regex("[,;/]|\\sfeat\\.\\s|\\s&\\s")
        private val COVER_NAMES = listOf("cover", "folder", "front", "album", "artwork")
        private val COVER_EXTENSIONS = listOf("jpg", "jpeg", "png", "webp")

        val AUDIO_EXTENSIONS: Set<String> = setOf(
            "flac", "mp3", "m4a", "aac", "alac", "ogg", "oga", "opus", "wav", "wv",
            "aiff", "aif", "ape", "mpc", "dsf", "dff", "wma", "mka", "webm",
        )
    }
}
