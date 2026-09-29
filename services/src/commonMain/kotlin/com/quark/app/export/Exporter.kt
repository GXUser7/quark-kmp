package com.quark.app.export

import com.quark.app.QuarkSourceResolver
import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Track
import com.quark.data.files.Files
import com.quark.data.files.safeFileName
import com.quark.data.files.sniffAudioExtension
import com.quark.data.images.ImageStore
import com.quark.data.net.TrackDownloader
import com.quark.platform.StoragePaths
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** The tags an exported file gets. */
data class ExportTags(
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: Int?,
    val trackTotal: Int?,
    val cover: ByteArray?,
)

/** Writes tags into a finished file; only the desktop has one (jaudiotagger). */
fun interface TagEditor {
    fun write(path: String, tags: ExportTags)
}

data class ExportProgress(
    val name: String,
    val folder: String,
    val total: Int,
    val done: Int = 0,
    val failed: Int = 0,
    val current: String? = null,
    val finished: Boolean = false,
)

/**
 * Saves streamed tracks as ordinary files, one folder per playlist or album —
 * the Dart build's "export" (`exportPlaytlist`, `exportAlbum`, `exportTracks`
 * in `yandex_music_singleton.dart`): the audio at the quality chosen for
 * streaming, the cover next to it as `cover.jpg`, and tags written in so
 * other players see the same titles.
 */
class Exporter(
    private val resolver: QuarkSourceResolver,
    private val downloader: TrackDownloader,
    private val images: ImageStore,
    private val paths: StoragePaths,
    private val tagEditor: TagEditor?,
    private val scope: CoroutineScope,
) {
    private val _progress = MutableStateFlow<ExportProgress?>(null)
    val progress: StateFlow<ExportProgress?> = _progress.asStateFlow()

    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    fun export(name: String, tracks: List<Track>) {
        if (isRunning) return
        val remote = tracks.filterNot { it is LocalTrack }
        val folder = paths.join(paths.exports, safeFileName(name))
        _progress.value = ExportProgress(name, folder, remote.size)
        job = scope.launch {
            Files.createDirectories(folder)
            var coverWritten = false
            remote.forEachIndexed { index, track ->
                _progress.value = _progress.value?.copy(current = track.title)
                val ok = runCatching {
                    val source = resolver.downloadSource(track) ?: error("No source")
                    val base = paths.join(folder, safeFileName("${(index + 1).toString().padStart(2, '0')}. ${track.artistLine} - ${track.title}"))
                    val partial = "$base.part"
                    downloader.downloadTo(source, partial)
                    val target = "$base.${sniffAudioExtension(Files.head(partial, 16))}"
                    Files.move(partial, target)

                    val cover = track.cover.takeIf { track.coverType == CoverType.Url && it.isNotEmpty() }
                        ?.let { images.get(it) }
                    if (cover != null && !coverWritten) {
                        Files.writeBytes(paths.join(folder, "cover.jpg"), cover)
                        coverWritten = true
                    }
                    tagEditor?.let { editor ->
                        runCatching {
                            editor.write(
                                target,
                                ExportTags(
                                    title = track.title,
                                    artist = track.artistLine,
                                    album = track.albumLine,
                                    trackNumber = index + 1,
                                    trackTotal = remote.size,
                                    cover = cover,
                                ),
                            )
                        }
                    }
                }.isSuccess
                _progress.value = _progress.value?.let {
                    if (ok) it.copy(done = it.done + 1) else it.copy(failed = it.failed + 1)
                }
            }
            _progress.value = _progress.value?.copy(current = null, finished = true)
        }
    }

    fun cancel() {
        job?.cancel()
        _progress.value = _progress.value?.copy(current = null, finished = true)
    }

    fun dismiss() {
        if (!isRunning) _progress.value = null
    }
}
