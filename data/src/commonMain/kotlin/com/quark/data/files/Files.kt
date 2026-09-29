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
}
