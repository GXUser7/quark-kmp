package com.quark.data.settings

import com.quark.core.settings.Settings
import com.quark.core.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import kotlin.io.path.deleteIfExists
import kotlin.io.path.exists
import kotlin.io.path.readText

/**
 * Settings in one json file, written atomically.
 *
 * Unknown keys are ignored and missing ones fall back to their defaults, so a
 * file written by an older or newer build still loads. A file that will not
 * parse at all is kept aside as `settings.json.broken` rather than deleted —
 * it holds the Yandex token, and silently starting from defaults would log the
 * user out with no way back.
 */
class JsonSettingsStore(
    private val file: Path,
    private val scope: CoroutineScope,
) : SettingsStore {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val _settings = MutableStateFlow(load())
    override val settings: StateFlow<Settings> = _settings.asStateFlow()

    @OptIn(FlowPreview::class)
    private val writer = scope.launch(Dispatchers.IO) {
        // Toggling a slider emits on every frame; coalesce before touching disk.
        _settings.drop(1).debounce(SAVE_DELAY_MS).collect(::save)
    }

    override fun update(transform: (Settings) -> Settings) {
        _settings.update(transform)
    }

    override suspend fun close() {
        writer.cancel()
        withContext(Dispatchers.IO) { save(_settings.value) }
    }

    private fun load(): Settings {
        if (!file.exists()) return Settings()
        return try {
            json.decodeFromString(Settings.serializer(), file.readText())
        } catch (e: Exception) {
            val broken = file.resolveSibling("${file.fileName}.broken")
            runCatching { Files.move(file, broken, StandardCopyOption.REPLACE_EXISTING) }
            Settings()
        }
    }

    private fun save(settings: Settings) {
        val temp = file.resolveSibling("${file.fileName}.tmp")
        try {
            Files.createDirectories(file.parent)
            // Files.write rather than writeString: the latter is Java 11, and
            // Android only has it from API 33.
            Files.write(temp, json.encodeToString(Settings.serializer(), settings).encodeToByteArray())
            // Replace in one step: a crash here leaves the old file intact, never a half-written one.
            Files.move(
                temp,
                file,
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.ATOMIC_MOVE,
            )
        } catch (e: Exception) {
            temp.deleteIfExists()
        }
    }

    private companion object {
        const val SAVE_DELAY_MS = 400L
    }
}
