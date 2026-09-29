package com.quark.data.local

import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class DirectoryObserverTest {

    @Test
    fun reports_a_file_created_in_the_root() = runBlocking {
        val root = Files.createTempDirectory("quark-watch-root")
        try {
            val ready = CompletableDeferred<Unit>()
            val event = async {
                DirectoryObserver().changesWhenReady(listOf(root), onReady = { ready.complete(Unit) })
                    .first { it.path.fileName.toString() == "new.mp3" }
            }
            ready.await()

            Files.writeString(root.resolve("new.mp3"), "audio")

            val change = withTimeout(5_000) { event.await() }
            assertEquals(root, change.root)
            assertEquals(DirectoryChangeKind.Created, change.kind)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun recursively_registers_a_directory_created_after_startup() = runBlocking {
        val root = Files.createTempDirectory("quark-watch-nested")
        try {
            val events = Channel<DirectoryChange>(Channel.UNLIMITED)
            val ready = CompletableDeferred<Unit>()
            val collector = launch {
                DirectoryObserver().changesWhenReady(
                    listOf(root),
                    recursive = true,
                    onReady = { ready.complete(Unit) },
                ).collect(events::send)
            }
            ready.await()
            val nested = Files.createDirectory(root.resolve("disc-two"))
            withTimeout(5_000) {
                while (events.receive().path.fileName.toString() != "disc-two") Unit
            }

            Files.writeString(nested.resolve("track.flac"), "audio")
            val change = withTimeout(5_000) {
                var next = events.receive()
                while (next.path.fileName.toString() != "track.flac") next = events.receive()
                next
            }
            assertEquals(DirectoryChangeKind.Created, change.kind)
            collector.cancelAndJoin()
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun non_recursive_watch_does_not_report_nested_files() = runBlocking {
        val root = Files.createTempDirectory("quark-watch-flat")
        try {
            val nested = Files.createDirectory(root.resolve("nested"))
            val ready = CompletableDeferred<Unit>()
            val rootEvent = async {
                DirectoryObserver().changesWhenReady(
                    listOf(root),
                    recursive = false,
                    onReady = { ready.complete(Unit) },
                )
                    .first { it.path.fileName.toString() == "visible.mp3" }
            }
            ready.await()

            Files.writeString(nested.resolve("hidden.mp3"), "audio")
            Files.writeString(root.resolve("visible.mp3"), "audio")

            assertEquals("visible.mp3", withTimeout(5_000) { rootEvent.await() }.path.fileName.toString())
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}
