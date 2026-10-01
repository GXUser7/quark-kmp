package com.quark.data.local

import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Track
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

/** [LocalLibrary] over the file system: locations are plain paths. */
class DesktopLibrary(
    private val scanner: LibraryScanner = LibraryScanner(),
    private val io: CoroutineDispatcher = Dispatchers.IO,
) : LocalLibrary {

    override fun scan(locations: List<String>, recursive: Boolean): Flow<ScanResult> =
        scanner.scanWithProgress(locations.map(::File), recursive)

    override suspend fun scanAll(locations: List<String>, recursive: Boolean): List<LocalTrack> =
        scanner.scan(locations.map(::File), recursive)

    override suspend fun artwork(track: Track): ByteArray? = withContext(io) {
        when (track.coverType) {
            CoverType.BuiltIn -> TagReader.readArtwork(File(track.filepath))
            CoverType.ExternalFile -> File(track.cover).takeIf(File::isFile)?.readBytes()
            else -> null
        }
    }

    override fun isFolder(location: String): Boolean = File(location).isDirectory

    override fun displayName(location: String): String =
        File(location).name.ifBlank { location }
}
