package com.quark.app.desktop

import com.sun.jna.Native
import com.sun.jna.Pointer
import java.security.MessageDigest
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.swing.JFrame
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals

class SmtcTest {

    @Test
    fun guids_are_laid_out_as_com_expects() {
        val bytes = Smtc.guidBytes(UUID.fromString("0557e996-7b23-5bae-aa81-ea0d671143a4"))
        val expected = intArrayOf(
            0x96, 0xe9, 0x57, 0x05, 0x23, 0x7b, 0xae, 0x5b,
            0xaa, 0x81, 0xea, 0x0d, 0x67, 0x11, 0x43, 0xa4,
        ).map { it.toByte() }.toByteArray()
        assertContentEquals(expected, bytes)
    }

    /**
     * The button handler's IID is not in any header: WinRT derives it from the
     * generic delegate and its two type arguments. Deriving it here checks the
     * constant and the three interface IIDs it is made from.
     */
    @Test
    fun the_button_handler_iid_follows_winrt_rules() {
        val signature = "pinterface({9de1c534-6ae1-11e0-84e1-18a905bcc53f};" +
            "rc(Windows.Media.SystemMediaTransportControls;{${Smtc.IID_CONTROLS}});" +
            "rc(Windows.Media.SystemMediaTransportControlsButtonPressedEventArgs;{${Smtc.IID_BUTTON_ARGS}}))"
        val namespace = uuidBytes(UUID.fromString("11f47ad5-7b73-42c0-abae-878b1e16adee"))
        val hash = MessageDigest.getInstance("SHA-1").digest(namespace + signature.toByteArray()).copyOf(16)
        hash[6] = ((hash[6].toInt() and 0x0f) or 0x50).toByte()
        hash[8] = ((hash[8].toInt() and 0x3f) or 0x80).toByte()
        assertEquals(Smtc.IID_BUTTON_HANDLER, uuidFrom(hash))
    }

    /** On Windows only: binds to a real window and drives every call the player makes. */
    @Test
    fun drives_the_controls_of_a_window() {
        if (!WindowsMediaControls.isWindows) return
        val frame = JFrame("quark").apply {
            setSize(320, 200)
            isVisible = true
        }
        val executor = Executors.newSingleThreadExecutor()
        try {
            val handle = Pointer.nativeValue(Native.getWindowPointer(frame))
            executor.submit {
                val smtc = Smtc.forWindow(handle)
                smtc.setEnabled(true)
                smtc.onButton { }
                smtc.setPlaying(true)
                smtc.setTrack("Heroes", "David Bowie", "\"Heroes\"", "https://avatars.yandex.net/get-music-content/49876/b0f7f4d1.a.2185786-1/400x400")
                smtc.setTrack("Untitled", "", "", null)
                val cover = java.io.File.createTempFile("quark-cover", ".png").apply {
                    // A 1×1 PNG is enough for SMTC to take a stream over a file.
                    writeBytes(java.util.Base64.getDecoder().decode(
                        "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
                    ))
                    deleteOnExit()
                }
                smtc.setTrack("Local", "Artist", "Album", null, cover.absolutePath)
                check(smtc.canReadCoverFile(cover.absolutePath)) { "no stream over the cover file" }
                smtc.setPlaying(false)
                smtc.close()
            }.get(60, TimeUnit.SECONDS)
        } finally {
            executor.shutdown()
            frame.dispose()
        }
    }

    private fun uuidBytes(uuid: UUID): ByteArray =
        java.nio.ByteBuffer.allocate(16).putLong(uuid.mostSignificantBits).putLong(uuid.leastSignificantBits).array()

    private fun uuidFrom(bytes: ByteArray): UUID {
        val buffer = java.nio.ByteBuffer.wrap(bytes)
        return UUID(buffer.getLong(), buffer.getLong())
    }
}
