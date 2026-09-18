package com.quark.player.mpv

import com.sun.jna.Library
import com.sun.jna.Pointer
import com.sun.jna.Structure

/**
 * mpv's client API, as much of it as a music player needs.
 *
 * Mirrors `client.h`. Only the calls used here are declared; the header is
 * stable across mpv 0.3x, and anything added later can be appended without
 * disturbing what is already mapped.
 */
internal interface LibMpv : Library {

    fun mpv_create(): Pointer?

    fun mpv_initialize(ctx: Pointer): Int

    fun mpv_terminate_destroy(ctx: Pointer)

    fun mpv_set_option_string(ctx: Pointer, name: String, data: String): Int

    /** Null-terminated argv. Preferred over `mpv_command_string`: no quoting. */
    fun mpv_command(ctx: Pointer, args: Array<String?>): Int

    fun mpv_set_property_string(ctx: Pointer, name: String, data: String): Int

    fun mpv_set_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int

    fun mpv_get_property(ctx: Pointer, name: String, format: Int, data: Pointer): Int

    fun mpv_observe_property(ctx: Pointer, replyUserdata: Long, name: String, format: Int): Int

    /** Blocks up to [timeout] seconds. The returned event is valid until the next call. */
    fun mpv_wait_event(ctx: Pointer, timeout: Double): MpvEvent?

    /** Makes a blocked [mpv_wait_event] return immediately. */
    fun mpv_wakeup(ctx: Pointer)

    fun mpv_error_string(error: Int): String

    fun mpv_client_api_version(): Long
}

/** `mpv_format` */
internal object MpvFormat {
    const val NONE = 0
    const val STRING = 1
    const val FLAG = 3
    const val INT64 = 4
    const val DOUBLE = 5
}

/** `mpv_event_id`, the subset this player reacts to. */
internal object MpvEventId {
    const val NONE = 0
    const val SHUTDOWN = 1
    const val LOG_MESSAGE = 2
    const val START_FILE = 6
    const val END_FILE = 7
    const val FILE_LOADED = 8
    const val PLAYBACK_RESTART = 21
    const val PROPERTY_CHANGE = 22
}

/** `mpv_end_file_reason` */
internal object MpvEndReason {
    const val EOF = 0
    const val STOP = 2
    const val QUIT = 3
    const val ERROR = 4
    const val REDIRECT = 5
}

@Structure.FieldOrder("event_id", "error", "reply_userdata", "data")
internal open class MpvEvent(
    @JvmField var event_id: Int = 0,
    @JvmField var error: Int = 0,
    @JvmField var reply_userdata: Long = 0,
    @JvmField var data: Pointer? = null,
) : Structure(), Structure.ByReference

@Structure.FieldOrder("name", "format", "data")
internal open class MpvEventProperty(
    pointer: Pointer? = null,
) : Structure(pointer) {
    @JvmField var name: String? = null
    @JvmField var format: Int = 0
    @JvmField var data: Pointer? = null
}

@Structure.FieldOrder("reason", "error", "playlist_entry_id", "playlist_insert_id", "playlist_insert_num_entries")
internal open class MpvEventEndFile(
    pointer: Pointer? = null,
) : Structure(pointer) {
    @JvmField var reason: Int = 0
    @JvmField var error: Int = 0
    @JvmField var playlist_entry_id: Long = 0
    @JvmField var playlist_insert_id: Int = 0
    @JvmField var playlist_insert_num_entries: Int = 0
}

/** Raised when an mpv call returns a negative error code. */
internal class MpvException(val code: Int, message: String) : RuntimeException(message)
