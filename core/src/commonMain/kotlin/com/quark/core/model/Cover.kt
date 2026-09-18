package com.quark.core.model

import kotlinx.serialization.Serializable

/** How the cover of a track has to be fetched. */
@Serializable
enum class CoverType(val value: String) {
    /** [Track.cover] is an http(s) url. */
    Url("url"),

    /** The artwork is embedded in the audio file at [Track.filepath]. */
    BuiltIn("builtIn"),

    /** [Track.cover] is a path to an image file next to the track. */
    ExternalFile("external"),

    NoCover("noCover");

    companion object {
        fun parse(value: String?): CoverType =
            entries.firstOrNull { it.value == value } ?: BuiltIn
    }
}

/**
 * Requested artwork size. Remote sources encode the size in the url, so the
 * quality is resolved at fetch time rather than stored with the track.
 */
enum class CoverQuality(val pixels: Int) {
    Low(100),
    Medium(300),
    High(1000),

    /** Whatever the source considers lossless. */
    Original(0),
}

enum class CoverSource(val value: String) {
    YouTube("youtube"),
    Local("local"),
    YandexMusic("yandex_music"),
    Vk("vk"),
    Spotify("spotify"),
}
