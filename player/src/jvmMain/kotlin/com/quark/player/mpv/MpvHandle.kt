package com.quark.player.mpv

import com.sun.jna.Memory
import com.sun.jna.Pointer
import java.util.concurrent.atomic.AtomicBoolean

/**
 * One mpv instance, with its event loop on a thread of its own.
 *
 * mpv's client API is not thread safe for a single handle, but every call used
 * here is; what is not allowed is running two event loops. [start] takes that
 * thread and hands events to [onEvent] until [close].
 */
internal class MpvHandle private constructor(
    private val lib: LibMpv,
    private val ctx: Pointer,
) {
    private val running = AtomicBoolean(true)
    private var eventThread: Thread? = null

    fun start(onEvent: (MpvUpdate) -> Unit) {
        check(eventThread == null) { "event loop already running" }
        eventThread = Thread({ loop(onEvent) }, "mpv-events").apply {
            isDaemon = true
            start()
        }
    }

    private fun loop(onEvent: (MpvUpdate) -> Unit) {
        while (running.get()) {
            val event = lib.mpv_wait_event(ctx, EVENT_TIMEOUT_SECONDS) ?: continue
            when (event.event_id) {
                MpvEventId.NONE -> Unit

                MpvEventId.SHUTDOWN -> {
                    running.set(false)
                    onEvent(MpvUpdate.Shutdown)
                }

                MpvEventId.START_FILE -> onEvent(MpvUpdate.FileStarted)

                MpvEventId.FILE_LOADED -> onEvent(MpvUpdate.FileLoaded)

                MpvEventId.END_FILE -> {
                    val end = event.data?.let { MpvEventEndFile(it).apply { read() } }
                    onEvent(MpvUpdate.FileEnded(end?.reason ?: MpvEndReason.EOF, end?.error ?: 0))
                }

                MpvEventId.PROPERTY_CHANGE -> {
                    val property = event.data?.let { MpvEventProperty(it).apply { read() } } ?: continue
                    val name = property.name ?: continue
                    onEvent(MpvUpdate.Property(name, readValue(property)))
                }

                MpvEventId.PLAYBACK_RESTART -> onEvent(MpvUpdate.PlaybackRestarted)
            }
        }
    }

    /**
     * A property change carries its value inline; a null payload means mpv has
     * no value for it right now, which happens between files.
     */
    private fun readValue(property: MpvEventProperty): Any? {
        val data = property.data ?: return null
        return when (property.format) {
            MpvFormat.DOUBLE -> data.getDouble(0)
            MpvFormat.FLAG -> data.getInt(0) != 0
            MpvFormat.INT64 -> data.getLong(0)
            MpvFormat.STRING -> data.getPointer(0)?.getString(0)
            else -> null
        }
    }

    fun observe(name: String, format: Int) {
        check(lib.mpv_observe_property(ctx, 0, name, format), "observe $name")
    }

    fun command(vararg args: String) {
        // mpv reads argv until the null.
        check(lib.mpv_command(ctx, arrayOf(*args, null)), "command ${args.joinToString(" ")}")
    }

    fun setOption(name: String, value: String) {
        check(lib.mpv_set_option_string(ctx, name, value), "option $name=$value")
    }

    fun setProperty(name: String, value: String) {
        check(lib.mpv_set_property_string(ctx, name, value), "property $name=$value")
    }

    fun setProperty(name: String, value: Double) = withMemory(8) { memory ->
        memory.setDouble(0, value)
        check(lib.mpv_set_property(ctx, name, MpvFormat.DOUBLE, memory), "property $name=$value")
    }

    fun setProperty(name: String, value: Boolean) = withMemory(4) { memory ->
        memory.setInt(0, if (value) 1 else 0)
        check(lib.mpv_set_property(ctx, name, MpvFormat.FLAG, memory), "property $name=$value")
    }

    fun setProperty(name: String, value: Long) = withMemory(8) { memory ->
        memory.setLong(0, value)
        check(lib.mpv_set_property(ctx, name, MpvFormat.INT64, memory), "property $name=$value")
    }

    fun getDouble(name: String): Double? = withMemory(8) { memory ->
        if (lib.mpv_get_property(ctx, name, MpvFormat.DOUBLE, memory) < 0) null
        else memory.getDouble(0)
    }

    fun getLong(name: String): Long? = withMemory(8) { memory ->
        if (lib.mpv_get_property(ctx, name, MpvFormat.INT64, memory) < 0) null
        else memory.getLong(0)
    }

    fun getBoolean(name: String): Boolean? = withMemory(4) { memory ->
        if (lib.mpv_get_property(ctx, name, MpvFormat.FLAG, memory) < 0) null
        else memory.getInt(0) != 0
    }

    fun close() {
        if (!running.compareAndSet(true, false)) return
        // Unblock the event loop so the thread sees `running` and leaves.
        lib.mpv_wakeup(ctx)
        eventThread?.join(SHUTDOWN_TIMEOUT_MS)
        lib.mpv_terminate_destroy(ctx)
    }

    private inline fun <T> withMemory(size: Long, block: (Memory) -> T): T =
        Memory(size).use { block(it) }

    private fun check(code: Int, what: String) {
        if (code < 0) throw MpvException(code, "$what failed: ${lib.mpv_error_string(code)}")
    }

    companion object {
        private const val EVENT_TIMEOUT_SECONDS = 1.0
        private const val SHUTDOWN_TIMEOUT_MS = 2000L

        /**
         * Creates a handle configured for audio-only playback. Options that
         * must be set before `mpv_initialize` are applied here; everything else
         * is a property and can change later.
         */
        fun create(lib: LibMpv, options: Map<String, String> = emptyMap()): MpvHandle {
            val ctx = lib.mpv_create() ?: error("mpv_create returned null")
            val handle = MpvHandle(lib, ctx)

            handle.setOption("vid", "no")
            handle.setOption("audio-display", "no")
            // Do not let mpv read the user's mpv.conf: this is a player, not a shell for theirs.
            handle.setOption("config", "no")
            handle.setOption("terminal", "no")
            handle.setOption("idle", "yes")
            // The point of choosing mpv.
            handle.setOption("gapless-audio", "yes")
            handle.setOption("audio-pitch-correction", "yes")
            // Do not advance on its own: the controller decides what plays next,
            // except across a gapless pair it has explicitly appended.
            handle.setOption("keep-open", "no")
            handle.setOption("prefetch-playlist", "yes")
            options.forEach { (name, value) -> handle.setOption(name, value) }

            val code = lib.mpv_initialize(ctx)
            if (code < 0) {
                lib.mpv_terminate_destroy(ctx)
                throw MpvException(code, "mpv_initialize failed: ${lib.mpv_error_string(code)}")
            }
            return handle
        }
    }
}

/** What the event loop saw, flattened to the cases this player cares about. */
internal sealed interface MpvUpdate {
    data class Property(val name: String, val value: Any?) : MpvUpdate
    data object FileStarted : MpvUpdate
    data object FileLoaded : MpvUpdate
    data class FileEnded(val reason: Int, val error: Int) : MpvUpdate
    data object PlaybackRestarted : MpvUpdate
    data object Shutdown : MpvUpdate
}
