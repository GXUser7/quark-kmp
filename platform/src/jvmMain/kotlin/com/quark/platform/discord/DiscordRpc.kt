package com.quark.platform.discord

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.io.Closeable
import java.io.File
import java.io.RandomAccessFile
import java.net.StandardProtocolFamily
import java.net.UnixDomainSocketAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.SocketChannel
import java.util.UUID

/** What the profile card shows while something plays. */
data class DiscordActivity(
    val details: String,
    val state: String,
    val largeImage: String? = null,
    val largeText: String? = null,
    val smallImage: String? = null,
    val smallText: String? = null,
    /** Epoch milliseconds the track started and will end, for the progress bar. */
    val startMs: Long? = null,
    val endMs: Long? = null,
)

/**
 * Discord Rich Presence over Discord's local IPC, replacing `flutter_discord_rpc`.
 *
 * The protocol is small: frames of a little-endian opcode, a length and json,
 * over a named pipe on Windows (`\\.\pipe\discord-ipc-N`) and a unix socket
 * elsewhere. A handshake with the application id, then `SET_ACTIVITY` whenever
 * the track changes. The activity is of the "listening" type, as the Dart build
 * set it, with the same "Join quark" button.
 */
class DiscordRpc(private val applicationId: String = APPLICATION_ID) : Closeable {

    private var pipe: Pipe? = null

    val isConnected: Boolean get() = pipe != null

    /** Connects to the first Discord instance found; false when none is running. */
    @Synchronized
    fun connect(): Boolean {
        if (pipe != null) return true
        for (index in 0 until 10) {
            val candidate = openPipe(index) ?: continue
            try {
                candidate.write(OP_HANDSHAKE, buildJsonObject {
                    put("v", 1)
                    put("client_id", applicationId)
                })
                // Discord answers the handshake with READY; anything else means
                // it rejected us, and the pipe is useless.
                val (op, _) = candidate.read()
                if (op == OP_FRAME) {
                    pipe = candidate
                    return true
                }
            } catch (_: Exception) {
                // Try the next index: several Discord builds can be running.
            }
            runCatching { candidate.close() }
        }
        return false
    }

    /** Shows [activity], or clears the card when it is null. False if Discord went away. */
    @Synchronized
    fun setActivity(activity: DiscordActivity?): Boolean {
        val current = pipe ?: return false
        val payload = buildJsonObject {
            put("cmd", "SET_ACTIVITY")
            put("nonce", UUID.randomUUID().toString())
            putJsonObject("args") {
                put("pid", ProcessHandle.current().pid())
                if (activity == null) put("activity", kotlinx.serialization.json.JsonNull)
                else put("activity", activity.toJson())
            }
        }
        return try {
            current.write(OP_FRAME, payload)
            current.read()
            true
        } catch (_: Exception) {
            closeQuietly()
            false
        }
    }

    @Synchronized
    override fun close() {
        pipe?.let { runCatching { setActivity(null) } }
        closeQuietly()
    }

    private fun closeQuietly() {
        runCatching { pipe?.close() }
        pipe = null
    }

    private fun DiscordActivity.toJson(): JsonObject = buildJsonObject {
        put("type", ACTIVITY_LISTENING)
        put("details", details.take(MAX_TEXT))
        put("state", state.take(MAX_TEXT))
        if (startMs != null || endMs != null) {
            putJsonObject("timestamps") {
                startMs?.let { put("start", it) }
                endMs?.let { put("end", it) }
            }
        }
        putJsonObject("assets") {
            largeImage?.let { put("large_image", it) }
            largeText?.takeIf(String::isNotBlank)?.let { put("large_text", it.take(MAX_TEXT)) }
            smallImage?.let { put("small_image", it) }
            smallText?.takeIf(String::isNotBlank)?.let { put("small_text", it.take(MAX_TEXT)) }
        }
        put("buttons", buildJsonArray {
            add(buildJsonObject {
                put("label", "Join quark")
                put("url", "https://quarkaudio.ru")
            })
        })
    }

    // --- Transport -------------------------------------------------------------

    private interface Pipe : Closeable {
        fun writeBytes(bytes: ByteArray)
        fun readBytes(count: Int): ByteArray

        fun write(op: Int, payload: JsonObject) {
            val body = payload.toString().encodeToByteArray()
            val frame = ByteBuffer.allocate(8 + body.size).order(ByteOrder.LITTLE_ENDIAN)
            frame.putInt(op).putInt(body.size).put(body)
            writeBytes(frame.array())
        }

        fun read(): Pair<Int, String> {
            val header = ByteBuffer.wrap(readBytes(8)).order(ByteOrder.LITTLE_ENDIAN)
            val op = header.int
            val length = header.int
            require(length in 0..MAX_FRAME) { "Unreasonable frame length $length" }
            return op to readBytes(length).decodeToString()
        }
    }

    private class WindowsPipe(path: String) : Pipe {
        private val file = RandomAccessFile(path, "rw")
        override fun writeBytes(bytes: ByteArray) = file.write(bytes)
        override fun readBytes(count: Int): ByteArray = ByteArray(count).also { file.readFully(it) }
        override fun close() = file.close()
    }

    private class UnixPipe(path: String) : Pipe {
        private val channel = SocketChannel.open(StandardProtocolFamily.UNIX).apply {
            connect(UnixDomainSocketAddress.of(path))
        }

        override fun writeBytes(bytes: ByteArray) {
            val buffer = ByteBuffer.wrap(bytes)
            while (buffer.hasRemaining()) channel.write(buffer)
        }

        override fun readBytes(count: Int): ByteArray {
            val buffer = ByteBuffer.allocate(count)
            while (buffer.hasRemaining()) {
                if (channel.read(buffer) < 0) throw java.io.EOFException("Discord closed the socket")
            }
            return buffer.array()
        }

        override fun close() = channel.close()
    }

    private fun openPipe(index: Int): Pipe? {
        val name = "discord-ipc-$index"
        if (System.getProperty("os.name").lowercase().contains("win")) {
            return runCatching { WindowsPipe("\\\\.\\pipe\\$name") }.getOrNull()
        }
        val roots = listOf("XDG_RUNTIME_DIR", "TMPDIR", "TMP", "TEMP")
            .mapNotNull { System.getenv(it)?.takeIf(String::isNotBlank) } + "/tmp"
        // Flatpak and Snap builds of Discord put the socket one level down.
        val folders = roots.flatMap { root ->
            listOf(root, "$root/app/com.discordapp.Discord", "$root/snap.discord", "$root/.flatpak/dev.vencord.Vesktop/xdg-run")
        }
        for (folder in folders) {
            val socket = File(folder, name)
            if (!socket.exists()) continue
            runCatching { return UnixPipe(socket.absolutePath) }
        }
        return null
    }

    companion object {
        /** The application registered for quark, from `discord_rpc.dart`. */
        const val APPLICATION_ID = "1520321415595954247"

        private const val OP_HANDSHAKE = 0
        private const val OP_FRAME = 1
        private const val ACTIVITY_LISTENING = 2
        private const val MAX_TEXT = 127
        private const val MAX_FRAME = 1 shl 20
    }
}
