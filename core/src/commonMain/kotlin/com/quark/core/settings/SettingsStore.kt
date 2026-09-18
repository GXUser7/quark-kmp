package com.quark.core.settings

import kotlinx.coroutines.flow.StateFlow

/**
 * Holds the current [Settings] and persists every change.
 *
 * Reads are synchronous off [settings]; writes go through [update], which
 * applies the change in memory and schedules the save. Callers never wait on
 * the disk to flip a switch.
 */
interface SettingsStore {
    val settings: StateFlow<Settings>

    val current: Settings get() = settings.value

    fun update(transform: (Settings) -> Settings)

    /** Writes any pending change and releases the store. */
    suspend fun close()
}
