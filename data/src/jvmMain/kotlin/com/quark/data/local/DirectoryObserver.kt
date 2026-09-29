package com.quark.data.local

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.nio.file.ClosedWatchServiceException
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardWatchEventKinds.ENTRY_CREATE
import java.nio.file.StandardWatchEventKinds.ENTRY_DELETE
import java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY
import java.nio.file.StandardWatchEventKinds.OVERFLOW
import java.nio.file.WatchEvent
import java.nio.file.WatchKey

enum class DirectoryChangeKind { Created, Modified, Deleted, Overflow }

data class DirectoryChange(
    val root: Path,
    val path: Path,
    val kind: DirectoryChangeKind,
)

/** A cancellation-safe, optionally recursive facade over Java's WatchService. */
class DirectoryObserver(private val io: CoroutineDispatcher = Dispatchers.IO) {

    fun changes(roots: List<Path>, recursive: Boolean = true): Flow<DirectoryChange> =
        changesWhenReady(roots, recursive) {}

    /** Readiness hook keeps filesystem integration tests free of timing sleeps. */
    internal fun changesWhenReady(
        roots: List<Path>,
        recursive: Boolean = true,
        onReady: () -> Unit,
    ): Flow<DirectoryChange> = callbackFlow {
        val watcher = FileSystems.getDefault().newWatchService()
        val registrations = mutableMapOf<WatchKey, Registration>()
        val normalizedRoots = roots
            .map(Path::toAbsolutePath)
            .map(Path::normalize)
            .distinct()
            .filter(Files::isDirectory)
            // One recursive root already covers every child supplied with it.
            .filter { candidate -> !recursive || roots.none { other ->
                val normalizedOther = other.toAbsolutePath().normalize()
                normalizedOther != candidate && candidate.startsWith(normalizedOther)
            } }

        fun register(directory: Path, root: Path) {
            val key = directory.register(watcher, ENTRY_CREATE, ENTRY_MODIFY, ENTRY_DELETE)
            registrations[key] = Registration(root, directory)
        }

        fun registerTree(root: Path, owner: Path = root) {
            if (!recursive) {
                register(root, owner)
                return
            }
            Files.walk(root).use { paths ->
                paths.filter(Files::isDirectory).forEach { register(it, owner) }
            }
        }

        normalizedRoots.forEach { registerTree(it) }
        onReady()

        val reader = launch(io) {
            try {
                while (isActive) {
                    val key = watcher.take()
                    val registration = registrations[key]
                    if (registration == null) {
                        key.reset()
                        continue
                    }

                    for (event in key.pollEvents()) {
                        if (event.kind() == OVERFLOW) {
                            trySend(
                                DirectoryChange(
                                    registration.root,
                                    registration.directory,
                                    DirectoryChangeKind.Overflow,
                                )
                            )
                            continue
                        }

                        @Suppress("UNCHECKED_CAST")
                        val pathEvent = event as WatchEvent<Path>
                        val path = registration.directory.resolve(pathEvent.context()).normalize()
                        val kind = when (event.kind()) {
                            ENTRY_CREATE -> DirectoryChangeKind.Created
                            ENTRY_MODIFY -> DirectoryChangeKind.Modified
                            ENTRY_DELETE -> DirectoryChangeKind.Deleted
                            else -> continue
                        }

                        // Register before emitting so a caller may immediately
                        // populate a newly created directory without a race.
                        if (recursive && kind == DirectoryChangeKind.Created && Files.isDirectory(path)) {
                            runCatching { registerTree(path, registration.root) }
                        }
                        trySend(DirectoryChange(registration.root, path, kind))
                    }

                    if (!key.reset()) registrations.remove(key)
                }
            } catch (_: ClosedWatchServiceException) {
                // awaitClose uses close() to wake the blocking take().
            } catch (error: CancellationException) {
                throw error
            }
        }

        awaitClose {
            watcher.close()
            reader.cancel()
        }
    }

    private data class Registration(val root: Path, val directory: Path)
}
