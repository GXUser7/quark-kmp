package com.quark.app.player

import com.quark.app.stats.ListenLogger
import com.quark.core.model.CoverType
import com.quark.core.model.LocalTrack
import com.quark.core.model.Playlist
import com.quark.core.model.PlaylistId
import com.quark.core.settings.LibrarySettings
import com.quark.core.settings.PlaybackMemory
import com.quark.core.settings.Settings
import com.quark.core.settings.SettingsStore
import com.quark.data.db.DatabaseFactory
import com.quark.data.repository.ListenStatsRepository
import com.quark.data.repository.PlaylistRepository
import com.quark.player.AudioEngine
import com.quark.player.EngineEvent
import com.quark.player.MediaSource
import com.quark.player.PlayerController
import com.quark.player.SourceResolver
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.TestScope
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class PlaybackServicesTest {

    @Test
    fun restores_the_saved_playlist_track_and_position() = runTest {
        val database = DatabaseFactory.inMemory()
        val playlists = PlaylistRepository(database, Dispatchers.Unconfined)
        val animals = Playlist(name = "Animals", tracks = listOf(track("dogs"), track("sheep")))
        val storageId = playlists.saveSnapshot(animals)
        val settings = MemorySettings(
            Settings(
                memory = PlaybackMemory(
                    lastTrackPath = "/music/sheep.flac",
                    lastPositionSeconds = 42,
                    lastPlaylist = PlaylistId.Local,
                    lastPlaylistName = animals.name,
                    lastPlaylistStorageId = storageId,
                )
            )
        )
        val engine = RecordingEngine()
        val serviceScope = TestScope(testScheduler)
        val controller = PlayerController(engine, resolver, serviceScope)
        val session = PlaybackSession(controller, settings, playlists, serviceScope)

        session.restore()
        advanceUntilIdle()

        assertEquals("sheep", controller.state.value.current.title)
        assertEquals<List<MediaSource>>(listOf(MediaSource.LocalFile("/music/sheep.flac")), engine.played)
        assertEquals(listOf(42.seconds), engine.seeks)
    }

    @Test
    fun records_more_than_ten_seconds_when_a_track_completes() = runTest {
        val database = DatabaseFactory.inMemory()
        val repository = ListenStatsRepository(database, Dispatchers.Unconfined)
        val settings = MemorySettings(Settings(library = LibrarySettings(logListens = true)))
        val engine = RecordingEngine()
        val serviceScope = TestScope(testScheduler)
        val controller = PlayerController(engine, resolver, serviceScope)
        val logger = ListenLogger(
            controller = controller,
            settings = settings,
            repository = repository,
            scope = serviceScope,
            nowEpochSeconds = { 1_726_000_000L },
        )
        logger.start()
        advanceUntilIdle()
        controller.load(Playlist(name = "Demo", tracks = listOf(track("dogs"), track("sheep"))))
        advanceUntilIdle()
        engine.emit(EngineEvent.TotalDuration(100.seconds))
        advanceUntilIdle()
        for (second in 1L..12L) {
            engine.emit(EngineEvent.Position(second.seconds))
            advanceUntilIdle()
        }
        assertEquals(12.seconds, controller.state.value.position)

        engine.emit(EngineEvent.Completed)
        advanceUntilIdle()

        assertEquals(12L, repository.totalSeconds(since = 0))
        assertEquals(listOf("dogs"), repository.topTracks(since = 0).map { it.track.title })
        logger.close()
    }

    @Test
    fun a_folder_refresh_updates_the_snapshot_without_restarting_audio() = runTest {
        val database = DatabaseFactory.inMemory()
        val playlists = PlaylistRepository(database, Dispatchers.Unconfined)
        val settings = MemorySettings(Settings())
        val engine = RecordingEngine()
        val serviceScope = TestScope(testScheduler)
        val controller = PlayerController(engine, resolver, serviceScope)
        val session = PlaybackSession(controller, settings, playlists, serviceScope)
        session.start()
        advanceUntilIdle()
        session.open(Playlist(name = "Animals", tracks = listOf(track("dogs"), track("sheep"))))
        advanceUntilIdle()
        engine.emit(EngineEvent.Position(23.seconds))
        advanceUntilIdle()

        val retagged = track("dogs").copy(title = "Dogs (2018 Remaster)")
        session.refreshLocal(
            Playlist(name = "Animals", tracks = listOf(retagged, track("sheep"), track("pigs"))),
        )
        advanceUntilIdle()

        assertEquals(1, engine.played.size)
        assertEquals(23.seconds, controller.state.value.position)
        assertEquals("Dogs (2018 Remaster)", controller.state.value.current.title)
        val storageId = settings.current.memory.lastPlaylistStorageId!!
        assertEquals(
            listOf("Dogs (2018 Remaster)", "sheep", "pigs"),
            playlists.tracksOf(storageId).map { it.title },
        )
    }
}

private fun track(name: String) = LocalTrack(
    title = name,
    artists = listOf("Pink Floyd"),
    albums = listOf("Animals"),
    filepath = "/music/$name.flac",
    coverType = CoverType.NoCover,
    durationMs = 100_000,
)

private val resolver = SourceResolver { MediaSource.LocalFile(it.filepath) }

private class RecordingEngine : AudioEngine {
    private val mutableEvents = MutableSharedFlow<EngineEvent>(extraBufferCapacity = 32)
    override val events: SharedFlow<EngineEvent> = mutableEvents.asSharedFlow()
    val played = mutableListOf<MediaSource>()
    val seeks = mutableListOf<Duration>()

    suspend fun emit(event: EngineEvent) = mutableEvents.emit(event)
    override suspend fun play(source: MediaSource) { played += source }
    override suspend fun preload(source: MediaSource) = Unit
    override suspend fun prepare(source: MediaSource) = Unit
    override suspend fun pause() = Unit
    override suspend fun resume() = Unit
    override suspend fun stop() = Unit
    override suspend fun seek(to: Duration) { seeks += to }
    override suspend fun setVolume(volume: Float) = Unit
    override suspend fun setSpeed(speed: Float) = Unit
    override suspend fun release() = Unit
}

private class MemorySettings(initial: Settings) : SettingsStore {
    private val mutable = MutableStateFlow(initial)
    override val settings = mutable.asStateFlow()
    override fun update(transform: (Settings) -> Settings) = mutable.update(transform)
    override suspend fun close() = Unit
}
