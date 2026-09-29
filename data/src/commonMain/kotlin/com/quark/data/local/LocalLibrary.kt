package com.quark.data.local

import com.quark.core.model.LocalTrack
import com.quark.core.model.Track
import kotlinx.coroutines.flow.Flow

/** Progress of a scan, so a large folder can show something while it runs. */
data class ScanProgress(val found: Int, val read: Int, val current: String?)

sealed interface ScanResult {
    data class Progress(val progress: ScanProgress) : ScanResult
    data class Done(val tracks: List<LocalTrack>) : ScanResult
}

/**
 * The music on this device.
 *
 * Locations are opaque strings the platform's pickers hand out and get back
 * here: an absolute path on the desktop, a `content://` uri from the storage
 * access framework on Android. Nothing above this interface looks inside them,
 * which is what lets the same view models open a folder on both.
 */
interface LocalLibrary {

    /** Reads every audio file under [locations], reporting progress as it goes. */
    fun scan(locations: List<String>, recursive: Boolean): Flow<ScanResult>

    /** The same as [scan], for callers that only want the result. */
    suspend fun scanAll(locations: List<String>, recursive: Boolean): List<LocalTrack>

    /** Cover bytes for a local track: embedded in its tags or sitting beside it. */
    suspend fun artwork(track: Track): ByteArray?

    /** Whether [location] names a folder rather than a single file. */
    fun isFolder(location: String): Boolean

    /** A short human name for [location], used to title the playlist it becomes. */
    fun displayName(location: String): String
}
