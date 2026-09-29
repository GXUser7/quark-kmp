package com.quark.data.local

import com.quark.core.model.LocalTrack
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Turns folders and files into tracks.
 *
 * Reading tags is IO-bound and independent per file, so files are read in
 * parallel; the order the caller gets back is the order the files were
 * discovered in, which for a music folder is the album order.
 */
class LibraryScanner(private val io: CoroutineDispatcher = Dispatchers.IO) {

    /** Every audio file under [roots], in discovery order. */
    suspend fun collectFiles(roots: List<File>, recursive: Boolean = true): List<File> =
        withContext(io) {
            val found = mutableListOf<File>()
            for (root in roots) {
                currentCoroutineContext().ensureActive()
                when {
                    root.isFile && root.isAudio() -> found += root
                    root.isDirectory -> found += walk(root, recursive)
                }
            }
            found
        }

    private fun walk(root: File, recursive: Boolean): List<File> {
        val walker = if (recursive) root.walkTopDown() else root.walkTopDown().maxDepth(1)
        return walker
            .filter { it.isFile && it.isAudio() }
            // A music folder is meaningless in filesystem order; sorting by path
            // puts discs and track numbers back where the user expects them.
            .sortedBy { it.absolutePath.lowercase() }
            .toList()
    }

    suspend fun scan(roots: List<File>, recursive: Boolean = true): List<LocalTrack> {
        val files = collectFiles(roots, recursive)
        return readAll(files)
    }

    /** Reads [files] in parallel, keeping their order. */
    suspend fun readAll(files: List<File>): List<LocalTrack> = coroutineScope {
        files.map { file -> async(io) { TagReader.read(file) } }.map { it.await() }
    }

    /**
     * Same as [scan], but reports progress as it goes. Emits a first value with
     * the file count before any tag is read, so the UI can show a total.
     */
    fun scanWithProgress(
        roots: List<File>,
        recursive: Boolean = true,
        batch: Int = PROGRESS_BATCH,
    ): Flow<ScanResult> = flow {
        val files = collectFiles(roots, recursive)
        emit(ScanResult.Progress(ScanProgress(files.size, 0, null)))

        val tracks = ArrayList<LocalTrack>(files.size)
        for ((index, file) in files.withIndex()) {
            currentCoroutineContext().ensureActive()
            tracks += TagReader.read(file)
            if ((index + 1) % batch == 0) {
                emit(ScanResult.Progress(ScanProgress(files.size, index + 1, file.name)))
            }
        }
        emit(ScanResult.Done(tracks))
    }.flowOn(io)

    private fun File.isAudio(): Boolean = extension.lowercase() in AUDIO_EXTENSIONS

    private companion object {
        const val PROGRESS_BATCH = 25
    }
}
