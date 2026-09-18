package com.quark.player

import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Playlist
import com.quark.core.model.Track
import com.quark.core.player.ChangeReason
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds

private fun track(name: String) = LocalTrack(
    title = name,
    artists = listOf("artist"),
    albums = listOf("album"),
    filepath = "/music/$name.flac",
    coverType = CoverType.NoCover,
)

/** Records what the controller asked of an engine, and replays engine events. */
private class RecordingEngine : AudioEngine {
    private val _events = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<EngineEvent> = _events.asSharedFlow()

    val played = mutableListOf<MediaSource>()
    val preloaded = mutableListOf<MediaSource>()
    var paused = false
    var volume = 1f
    var released = false

    suspend fun emit(event: EngineEvent) = _events.emit(event)

    override suspend fun play(source: MediaSource) {
        played += source
        paused = false
    }

    override suspend fun preload(source: MediaSource) {
        preloaded += source
    }

    override suspend fun prepare(source: MediaSource) = Unit
    override suspend fun pause() { paused = true }
    override suspend fun resume() { paused = false }
    override suspend fun stop() { paused = true }
    override suspend fun seek(to: kotlin.time.Duration) = Unit
    override suspend fun setVolume(volume: Float) { this.volume = volume }
    override suspend fun setSpeed(speed: Float) = Unit
    override suspend fun release() { released = true }
}

private val resolver = SourceResolver { MediaSource.LocalFile(it.filepath) }

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerControllerTest {

    private val a = track("a")
    private val b = track("b")
    private val c = track("c")
    private val playlist = Playlist(name = "Demo", tracks = listOf(a, b, c))

    @Test
    fun loading_a_playlist_starts_the_first_track_and_queues_the_second() = runTest {
        val engine = RecordingEngine()
        val controller = PlayerController(engine, resolver, TestScope(testScheduler))

        controller.load(playlist)
        advanceUntilIdle()

        assertEquals<List<MediaSource>>(listOf(MediaSource.LocalFile(a.filepath)), engine.played)
        assertEquals<List<MediaSource>>(listOf(MediaSource.LocalFile(b.filepath)), engine.preloaded)
        assertEquals(a, controller.state.value.current)
    }

    @Test
    fun a_natural_advance_does_not_reopen_the_track_that_is_already_playing() = runTest {
        val engine = RecordingEngine()
        val controller = PlayerController(engine, resolver, TestScope(testScheduler))
        controller.load(playlist)
        advanceUntilIdle()

        engine.emit(EngineEvent.Completed)
        advanceUntilIdle()

        // State moved on, but nothing new was opened: mpv is already on it.
        assertEquals(b, controller.state.value.current)
        assertEquals(1, engine.played.size)
        // And the one after is now queued.
        assertEquals(MediaSource.LocalFile(c.filepath), engine.preloaded.last())
    }

    @Test
    fun skipping_by_hand_does_open_the_next_track() = runTest {
        val engine = RecordingEngine()
        val controller = PlayerController(engine, resolver, TestScope(testScheduler))
        controller.load(playlist)
        advanceUntilIdle()

        controller.next()
        advanceUntilIdle()

        assertEquals(listOf(a.filepath, b.filepath), engine.played.map { (it as MediaSource.LocalFile).path })
    }

    @Test
    fun a_track_that_fails_to_open_is_skipped() = runTest {
        val engine = RecordingEngine()
        val controller = PlayerController(engine, resolver, TestScope(testScheduler))
        controller.load(playlist)
        advanceUntilIdle()

        engine.emit(EngineEvent.Failed(MediaSource.LocalFile(a.filepath), RuntimeException("no codec")))
        advanceUntilIdle()

        assertEquals(b, controller.state.value.current)
    }

    @Test
    fun changing_the_queue_replaces_what_was_preloaded() = runTest {
        val engine = RecordingEngine()
        val controller = PlayerController(engine, resolver, TestScope(testScheduler))
        controller.load(playlist)
        advanceUntilIdle()
        assertEquals(MediaSource.LocalFile(b.filepath), engine.preloaded.last())

        controller.enqueueLast(c)
        advanceUntilIdle()

        // c now comes next, so that is what the engine must be holding.
        assertEquals(MediaSource.LocalFile(c.filepath), engine.preloaded.last())
    }

    @Test
    fun engine_events_drive_the_state() = runTest {
        val engine = RecordingEngine()
        val controller = PlayerController(engine, resolver, TestScope(testScheduler))
        controller.load(playlist)
        advanceUntilIdle()

        engine.emit(EngineEvent.TotalDuration(200.seconds))
        engine.emit(EngineEvent.Position(50.seconds))
        engine.emit(EngineEvent.PlayingChanged(true))
        advanceUntilIdle()

        val state = controller.state.value
        assertEquals(200.seconds, state.duration)
        assertEquals(50.seconds, state.position)
        assertTrue(state.isPlaying)
        assertEquals(0.25f, state.progress)
    }

    @Test
    fun track_changes_carry_why_they_happened() = runTest {
        val engine = RecordingEngine()
        val controller = PlayerController(engine, resolver, TestScope(testScheduler))
        val seen = mutableListOf<ChangeReason>()
        val collector = launch { controller.trackChanges.collect { seen += it.reason } }
        advanceUntilIdle()

        controller.load(playlist)
        advanceUntilIdle()
        engine.emit(EngineEvent.Completed)
        advanceUntilIdle()
        controller.next()
        advanceUntilIdle()

        assertEquals(
            listOf(ChangeReason.External, ChangeReason.Completed, ChangeReason.External),
            seen,
        )
        collector.cancel()
    }

    @Test
    fun volume_is_clamped_before_it_reaches_the_engine() = runTest {
        val engine = RecordingEngine()
        val controller = PlayerController(engine, resolver, TestScope(testScheduler))

        controller.setVolume(1.7f)
        assertEquals(1f, engine.volume)

        controller.setVolume(-0.2f)
        assertEquals(0f, engine.volume)
        assertEquals(0f, controller.state.value.volume)
    }
}
