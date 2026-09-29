package com.quark.data.files

/**
 * The handful of file operations shared code needs. Both platforms are JVMs, so
 * the one implementation lives in `jvmShared`; the paths here are always real
 * files (the app's own cache and export folders), never storage-access uris.
 */
expect object Files {
    fun exists(path: String): Boolean
    fun size(path: String): Long
    fun delete(path: String): Boolean
    fun readBytes(path: String): ByteArray?
    fun writeBytes(path: String, bytes: ByteArray)
    fun list(directory: String): List<String>
    fun createDirectories(path: String)
    fun copy(from: String, to: String)
    fun parent(path: String): String?
    fun name(path: String): String
    fun join(parent: String, child: String): String
    /** Total size of everything under [directory], in bytes. */
    fun directorySize(directory: String): Long
    /** Deletes everything under [directory] and returns how many bytes that freed. */
    fun clearDirectory(directory: String): Long
    /** The first [count] bytes of a file, fewer if it is shorter. */
    fun head(path: String, count: Int): ByteArray
    fun move(from: String, to: String)
}

/** A file name with the characters Windows, Android and the rest forbid replaced. */
fun safeFileName(name: String, limit: Int = 120): String =
    name.map { if (it in "\\/:*?\"<>|" || it.code < 32) '_' else it }
        .joinToString("")
        .trim()
        .trimEnd('.')
        .take(limit)
        .ifEmpty { "untitled" }

/** Guesses an audio file's extension from its first bytes. */
fun sniffAudioExtension(head: ByteArray): String {
    fun at(offset: Int, text: String) =
        head.size >= offset + text.length && text.indices.all { head[offset + it] == text[it].code.toByte() }
    return when {
        at(0, "fLaC") -> "flac"
        at(0, "OggS") -> "ogg"
        at(4, "ftyp") -> "m4a"
        at(0, "RIFF") -> "wav"
        at(0, "ID3") -> "mp3"
        head.size >= 2 && head[0] == 0xFF.toByte() && (head[1].toInt() and 0xE0) == 0xE0 ->
            if ((head[1].toInt() and 0x06) == 0) "aac" else "mp3"
        else -> "mp3"
    }
}
