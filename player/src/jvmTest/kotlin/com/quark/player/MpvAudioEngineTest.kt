package com.quark.player

import com.quark.player.mpv.MpvNotFoundException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files
import kotlin.math.PI
import kotlin.math.sin
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

/**
 * Exercises the real libmpv binding.
 *
 * Output goes to the null device, so this runs without a sound card and makes
 * no noise; what is being checked is that the JNA mapping, the event loop and
 * the property plumbing actually work against the library, which no amount of
 * mocking can tell us.
 *
 * Skipped when libmpv is not present rather than failed: it is fetched by
 * `:app:fetchMpv` and a checkout that has not run it should not look broken.
 */
class MpvAudioEngineTest {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var engine: MpvAudioEngine? = null

    @AfterTest
    fun tearDown() {
        runBlocking { engine?.release() }
        scope.cancel()
    }

    private fun engineOrSkip(): MpvAudioEngine? = try {
        MpvAudioEngine(io = Dispatchers.IO).also { engine = it }
    } catch (e: MpvNotFoundException) {
        println("skipping: ${e.message?.lineSequence()?.firstOrNull()}")
        null
    } catch (e: UnsatisfiedLinkError) {
        println("skipping: ${e.message}")
        null
    }

    @Test
    fun plays_a_file_and_reports_its_duration() {
        val engine = engineOrSkip() ?: return
        val file = writeTone(seconds = 2)

        runBlocking {
            val events = mutableListOf<EngineEvent>()
            engine.events.onEach { events += it }.launchIn(scope)

            engine.setVolume(0f)
            engine.play(MediaSource.LocalFile(file.absolutePath))

            val duration = withTimeoutOrNull(10.seconds) {
                engine.events.first { it is EngineEvent.TotalDuration } as EngineEvent.TotalDuration
            }

            assertTrue(duration != null, "mpv never reported a duration")
            assertTrue(
                duration.duration.inWholeMilliseconds in 1_500..2_500,
                "expected about 2s, got ${duration.duration}",
            )
        }
    }

    @Test
    fun reports_position_while_playing() {
        val engine = engineOrSkip() ?: return
        val file = writeTone(seconds = 3)

        runBlocking {
            engine.setVolume(0f)
            engine.play(MediaSource.LocalFile(file.absolutePath))

            val moved = withTimeoutOrNull(10.seconds) {
                engine.events.first { it is EngineEvent.Position && it.position.inWholeMilliseconds > 0 }
            }
            assertTrue(moved != null, "position never advanced")
        }
    }

    @Test
    fun crossing_into_a_preloaded_file_is_reported_as_completion() {
        val engine = engineOrSkip() ?: return
        val first = writeTone(seconds = 1, frequency = 440.0)
        val second = writeTone(seconds = 1, frequency = 660.0)

        runBlocking {
            engine.setVolume(0f)
            engine.play(MediaSource.LocalFile(first.absolutePath))
            engine.preload(MediaSource.LocalFile(second.absolutePath))

            val completed = withTimeoutOrNull(15.seconds) {
                engine.events.first { it is EngineEvent.Completed }
            }
            assertTrue(completed != null, "mpv never crossed into the preloaded file")
        }
    }

    @Test
    fun pausing_and_resuming_is_reflected_back() {
        val engine = engineOrSkip() ?: return
        val file = writeTone(seconds = 5)

        runBlocking {
            // Subscribe before anything happens: the event flow does not replay,
            // and mpv reports the first state change almost immediately.
            val playing = mutableListOf<Boolean>()
            val collecting = CompletableDeferred<Unit>()
            engine.events
                .onSubscription { collecting.complete(Unit) }
                .onEach { if (it is EngineEvent.PlayingChanged) playing += it.isPlaying }
                .launchIn(scope)
            // onSubscription, not onStart: the latter runs before the
            // subscription is registered, which is exactly when events go missing.
            collecting.await()

            engine.setVolume(0f)
            engine.play(MediaSource.LocalFile(file.absolutePath))
            awaitUntil { playing.contains(true) }
            assertTrue(playing.contains(true), "playback start was not reported")

            engine.pause()
            awaitUntil { playing.contains(false) }
            assertTrue(playing.contains(false), "pause was not reported back")

            engine.resume()
            awaitUntil { playing.lastOrNull() == true }
            assertTrue(playing.lastOrNull() == true, "resume was not reported back")
        }
    }

    /** Polls [condition] until it holds or the budget runs out. */
    private suspend fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < deadline && !condition()) {
            kotlinx.coroutines.delay(25)
        }
    }
}

/**
 * A sine tone as a 16-bit mono wav. Writing the file here keeps a binary
 * fixture out of the repository, and the header is short enough to spell out.
 */
internal fun writeTone(seconds: Int, frequency: Double = 440.0, sampleRate: Int = 44_100): File {
    val samples = sampleRate * seconds
    val dataBytes = samples * 2
    val buffer = ByteBuffer.allocate(44 + dataBytes).order(ByteOrder.LITTLE_ENDIAN)

    buffer.put("RIFF".toByteArray())
    buffer.putInt(36 + dataBytes)
    buffer.put("WAVE".toByteArray())
    buffer.put("fmt ".toByteArray())
    buffer.putInt(16)          // PCM header size
    buffer.putShort(1)         // PCM, uncompressed
    buffer.putShort(1)         // mono
    buffer.putInt(sampleRate)
    buffer.putInt(sampleRate * 2)
    buffer.putShort(2)         // block align
    buffer.putShort(16)        // bits per sample
    buffer.put("data".toByteArray())
    buffer.putInt(dataBytes)

    for (index in 0 until samples) {
        val value = sin(2.0 * PI * frequency * index / sampleRate) * Short.MAX_VALUE * 0.2
        buffer.putShort(value.toInt().toShort())
    }

    val file = Files.createTempFile("quark-tone-", ".wav").toFile()
    file.deleteOnExit()
    file.writeBytes(buffer.array())
    return file
}
