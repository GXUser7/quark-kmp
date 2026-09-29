package com.quark.app.desktop

import com.sun.jna.Function
import com.sun.jna.Library
import com.sun.jna.Memory
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.WString
import com.sun.jna.ptr.PointerByReference
import com.sun.jna.win32.StdCallLibrary
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.UUID

/**
 * Windows' System Media Transport Controls — the media overlay, the lock
 * screen and the media keys — for a desktop window, which is what the Flutter
 * build had from `smtc_windows`.
 *
 * There is no Java binding for WinRT, so this speaks its ABI directly through
 * JNA: activation factories by class name, interfaces as tables of function
 * pointers, and one COM object of our own for the button events. Every call
 * checks its HRESULT; the caller runs it all on one thread.
 */
internal class Smtc private constructor(
    private val controls: Pointer,
    private val updater: Pointer,
    private val music: Pointer,
    private val music2: Pointer?,
) {
    enum class Button { Play, Pause, Stop, Record, FastForward, Rewind, Next, Previous, ChannelUp, ChannelDown }

    private var token: Memory? = null
    private var handler: ButtonHandler? = null

    fun setEnabled(enabled: Boolean) {
        call(controls, CONTROLS_PUT_IS_ENABLED, flag(enabled))
        call(controls, CONTROLS_PUT_IS_PLAY_ENABLED, flag(enabled))
        call(controls, CONTROLS_PUT_IS_PAUSE_ENABLED, flag(enabled))
        call(controls, CONTROLS_PUT_IS_NEXT_ENABLED, flag(enabled))
        call(controls, CONTROLS_PUT_IS_PREVIOUS_ENABLED, flag(enabled))
        call(controls, CONTROLS_PUT_IS_STOP_ENABLED, flag(enabled))
    }

    /** Playing, paused, or [closed] when nothing is loaded. */
    fun setPlaying(playing: Boolean, closed: Boolean = false) {
        val status = when {
            closed -> STATUS_CLOSED
            playing -> STATUS_PLAYING
            else -> STATUS_PAUSED
        }
        call(controls, CONTROLS_PUT_PLAYBACK_STATUS, status)
    }

    /** What the overlay shows; [thumbnailUrl] must be http(s), which is what WinRT fetches itself. */
    fun setTrack(title: String, artist: String, album: String, thumbnailUrl: String?) {
        call(updater, UPDATER_PUT_TYPE, TYPE_MUSIC)
        withString(title) { call(music, MUSIC_PUT_TITLE, it) }
        withString(artist) { call(music, MUSIC_PUT_ARTIST, it) }
        withString(artist) { call(music, MUSIC_PUT_ALBUM_ARTIST, it) }
        music2?.let { properties -> withString(album) { call(properties, MUSIC2_PUT_ALBUM_TITLE, it) } }
        val thumbnail = thumbnailUrl?.takeIf { it.startsWith("http") }?.let(::streamReference)
        try {
            call(updater, UPDATER_PUT_THUMBNAIL, thumbnail)
        } finally {
            thumbnail?.let(::release)
        }
        call(updater, UPDATER_UPDATE)
    }

    fun onButton(listener: (Button) -> Unit) {
        val handler = ButtonHandler(listener)
        val token = Memory(8)
        call(controls, CONTROLS_ADD_BUTTON_PRESSED, handler.pointer, token)
        this.handler = handler
        this.token = token
    }

    fun close() {
        runCatching {
            token?.let { call(controls, CONTROLS_REMOVE_BUTTON_PRESSED, it.getLong(0)) }
            setPlaying(playing = false, closed = true)
            setEnabled(false)
        }
        music2?.let(::release)
        release(music)
        release(updater)
        release(controls)
        handler = null
        token = null
    }

    private fun streamReference(url: String): Pointer? = runCatching {
        val uriFactory = activationFactory("Windows.Foundation.Uri", IID_URI_FACTORY)
        val uri = try {
            withString(url) { hstring -> outPointer { call(uriFactory, URI_FACTORY_CREATE, hstring, it) } }
        } finally {
            release(uriFactory)
        }
        val statics = activationFactory("Windows.Storage.Streams.RandomAccessStreamReference", IID_STREAM_REFERENCE_STATICS)
        try {
            outPointer { call(statics, STREAM_REFERENCE_CREATE_FROM_URI, uri, it) }
        } finally {
            release(statics)
            release(uri)
        }
    }.getOrNull()

    /**
     * The COM object SMTC calls with a button: a table of four functions —
     * QueryInterface, AddRef, Release and Invoke — and a pointer to it. It is
     * agile, so the calls may come on any thread.
     */
    private class ButtonHandler(private val listener: (Button) -> Unit) {
        private val queryInterface = object : QueryInterface {
            override fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int {
                val iid = readGuid(riid)
                return if (iid == IID_UNKNOWN || iid == IID_AGILE || iid == IID_BUTTON_HANDLER) {
                    out.setPointer(0, self)
                    S_OK
                } else {
                    out.setPointer(0, null)
                    E_NOINTERFACE
                }
            }
        }
        // Lifetime is ours: the object lives while this class does, so the
        // count only has to look plausible to the caller.
        private val addRef = object : RefCount {
            override fun invoke(self: Pointer): Int = 1
        }
        private val release = object : RefCount {
            override fun invoke(self: Pointer): Int = 1
        }
        private val invoke = object : Invoke {
            override fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int {
                if (args == null) return S_OK
                runCatching {
                    val button = Memory(4)
                    call(args, ARGS_GET_BUTTON, button)
                    Button.entries.getOrNull(button.getInt(0))?.let(listener)
                }
                return S_OK
            }
        }

        private val table = Memory(4L * Native.POINTER_SIZE).apply {
            setPointer(0L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(queryInterface))
            setPointer(1L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(addRef))
            setPointer(2L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(release))
            setPointer(3L * Native.POINTER_SIZE, com.sun.jna.CallbackReference.getFunctionPointer(invoke))
        }

        val pointer: Pointer = Memory(Native.POINTER_SIZE.toLong()).apply { setPointer(0, table) }
    }

    private interface QueryInterface : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, riid: Pointer, out: Pointer): Int
    }

    private interface RefCount : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer): Int
    }

    private interface Invoke : StdCallLibrary.StdCallCallback {
        fun invoke(self: Pointer, sender: Pointer?, args: Pointer?): Int
    }

    @Suppress("FunctionName")
    private interface Combase : Library {
        fun RoInitialize(type: Int): Int
        fun RoGetActivationFactory(classId: Pointer, iid: Pointer, factory: PointerByReference): Int
        fun WindowsCreateString(source: WString, length: Int, string: PointerByReference): Int
        fun WindowsDeleteString(string: Pointer?): Int
    }

    companion object {
        private val combase: Combase by lazy { Native.load("combase", Combase::class.java) }

        /**
         * Joins the calling thread to the multithreaded apartment and binds
         * SMTC to [window], an HWND. Must be called on the thread every later
         * call is made on.
         */
        fun forWindow(window: Long): Smtc {
            // Access violations in native code become exceptions, not a crash.
            Native.setProtected(true)
            val init = combase.RoInitialize(RO_INIT_MULTITHREADED)
            check(init == S_OK || init == S_FALSE || init == RPC_E_CHANGED_MODE) { hresult("RoInitialize", init) }

            val interop = activationFactory("Windows.Media.SystemMediaTransportControls", IID_INTEROP)
            val controls = try {
                outPointer { call(interop, INTEROP_GET_FOR_WINDOW, Pointer(window), guid(IID_CONTROLS), it) }
            } finally {
                release(interop)
            }
            val updater = outPointer { call(controls, CONTROLS_GET_DISPLAY_UPDATER, it) }
            val music = outPointer { call(updater, UPDATER_GET_MUSIC_PROPERTIES, it) }
            val music2 = runCatching { outPointer { call(music, QUERY_INTERFACE, guid(IID_MUSIC2), it) } }.getOrNull()
            return Smtc(controls, updater, music, music2)
        }

        private fun activationFactory(className: String, iid: UUID): Pointer = withString(className) { name ->
            val factory = PointerByReference()
            val result = combase.RoGetActivationFactory(name!!, guid(iid), factory)
            check(result == S_OK) { hresult("RoGetActivationFactory($className)", result) }
            factory.value
        }

        /** Calls method [index] of the interface at [self]; a failed HRESULT throws. */
        private fun call(self: Pointer, index: Int, vararg args: Any?) {
            val table = self.getPointer(0)
            val function = Function.getFunction(table.getPointer(index.toLong() * Native.POINTER_SIZE), Function.ALT_CONVENTION)
            val result = function.invokeInt(arrayOf<Any?>(self, *args))
            check(result >= 0) { hresult("method $index", result) }
        }

        private fun release(self: Pointer) {
            runCatching {
                val table = self.getPointer(0)
                Function.getFunction(table.getPointer(2L * Native.POINTER_SIZE), Function.ALT_CONVENTION)
                    .invokeInt(arrayOf<Any?>(self))
            }
        }

        private inline fun outPointer(block: (PointerByReference) -> Unit): Pointer {
            val out = PointerByReference()
            block(out)
            return out.value ?: error("A WinRT call returned no object")
        }

        private inline fun <T> withString(text: String, block: (Pointer?) -> T): T {
            if (text.isEmpty()) return block(null)
            val out = PointerByReference()
            val result = combase.WindowsCreateString(WString(text), text.length, out)
            check(result == S_OK) { hresult("WindowsCreateString", result) }
            try {
                return block(out.value)
            } finally {
                combase.WindowsDeleteString(out.value)
            }
        }

        private fun flag(value: Boolean): Byte = if (value) 1 else 0

        private fun hresult(what: String, result: Int) = "$what failed: 0x${result.toUInt().toString(16)}"

        /** A GUID as COM lays it out: three little-endian fields, then eight bytes as they are. */
        fun guidBytes(uuid: UUID): ByteArray {
            val buffer = ByteBuffer.allocate(16)
            val msb = uuid.mostSignificantBits
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            buffer.putInt((msb ushr 32).toInt())
            buffer.putShort((msb ushr 16).toShort())
            buffer.putShort(msb.toShort())
            buffer.order(ByteOrder.BIG_ENDIAN)
            buffer.putLong(uuid.leastSignificantBits)
            return buffer.array()
        }

        private fun guid(uuid: UUID): Memory = Memory(16).apply { write(0, guidBytes(uuid), 0, 16) }

        private fun readGuid(pointer: Pointer): UUID {
            val bytes = pointer.getByteArray(0, 16)
            val buffer = ByteBuffer.wrap(bytes)
            buffer.order(ByteOrder.LITTLE_ENDIAN)
            val data1 = buffer.getInt().toLong() and 0xffffffffL
            val data2 = buffer.getShort().toLong() and 0xffffL
            val data3 = buffer.getShort().toLong() and 0xffffL
            buffer.order(ByteOrder.BIG_ENDIAN)
            val rest = buffer.getLong()
            return UUID((data1 shl 32) or (data2 shl 16) or data3, rest)
        }

        // --- The ABI ----------------------------------------------------------

        private const val S_OK = 0
        private const val S_FALSE = 1
        private const val E_NOINTERFACE = 0x80004002.toInt()
        private const val RPC_E_CHANGED_MODE = 0x80010106.toInt()
        private const val RO_INIT_MULTITHREADED = 1

        val IID_UNKNOWN: UUID = UUID.fromString("00000000-0000-0000-c000-000000000046")
        val IID_AGILE: UUID = UUID.fromString("94ea2b94-e9cc-49e0-c0ff-ee64ca8f5b90")
        val IID_INTEROP: UUID = UUID.fromString("ddb0472d-c911-4a1f-86d9-dc3d71a95f5a")
        val IID_CONTROLS: UUID = UUID.fromString("99fa3ff4-1742-42a6-902e-087d41f965ec")
        val IID_BUTTON_ARGS: UUID = UUID.fromString("b7f47116-a56f-4dc8-9e11-92031f4a87c2")
        val IID_MUSIC2: UUID = UUID.fromString("00368462-97d3-44b9-b00f-008afcefaf18")
        val IID_URI_FACTORY: UUID = UUID.fromString("44a9796f-723e-4fdf-a218-033e75b0c084")
        val IID_STREAM_REFERENCE_STATICS: UUID = UUID.fromString("857309dc-3fbf-4e7d-986f-ef3b1a07a964")

        /** `TypedEventHandler<SystemMediaTransportControls, …ButtonPressedEventArgs>`, derived by WinRT's rules. */
        val IID_BUTTON_HANDLER: UUID = UUID.fromString("0557e996-7b23-5bae-aa81-ea0d671143a4")

        // Method indices: IUnknown's three and IInspectable's three come first.
        private const val QUERY_INTERFACE = 0
        private const val INTEROP_GET_FOR_WINDOW = 6

        private const val CONTROLS_PUT_PLAYBACK_STATUS = 7
        private const val CONTROLS_GET_DISPLAY_UPDATER = 8
        private const val CONTROLS_PUT_IS_ENABLED = 11
        private const val CONTROLS_PUT_IS_PLAY_ENABLED = 13
        private const val CONTROLS_PUT_IS_STOP_ENABLED = 15
        private const val CONTROLS_PUT_IS_PAUSE_ENABLED = 17
        private const val CONTROLS_PUT_IS_PREVIOUS_ENABLED = 25
        private const val CONTROLS_PUT_IS_NEXT_ENABLED = 27
        private const val CONTROLS_ADD_BUTTON_PRESSED = 32
        private const val CONTROLS_REMOVE_BUTTON_PRESSED = 33

        private const val UPDATER_PUT_TYPE = 7
        private const val UPDATER_PUT_THUMBNAIL = 11
        private const val UPDATER_GET_MUSIC_PROPERTIES = 12
        private const val UPDATER_UPDATE = 17

        private const val MUSIC_PUT_TITLE = 7
        private const val MUSIC_PUT_ALBUM_ARTIST = 9
        private const val MUSIC_PUT_ARTIST = 11
        private const val MUSIC2_PUT_ALBUM_TITLE = 7

        private const val ARGS_GET_BUTTON = 6
        private const val URI_FACTORY_CREATE = 6
        private const val STREAM_REFERENCE_CREATE_FROM_URI = 7

        private const val STATUS_CLOSED = 0
        private const val STATUS_PLAYING = 3
        private const val STATUS_PAUSED = 4
        private const val TYPE_MUSIC = 1
    }
}
