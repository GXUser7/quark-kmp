package com.quark.android

import android.app.Application
import com.quark.app.AndroidQuark
import com.quark.app.startAndroidQuark

/**
 * Owns the one [AndroidQuark] for the process. The activity and the playback
 * service both reach it here, so closing the activity does not stop the music
 * and reopening it finds the player where it was.
 */
class QuarkApplication : Application() {

    /** Built on first use, on the main thread, which ExoPlayer requires. */
    val quark: Result<AndroidQuark> by lazy { runCatching { startAndroidQuark(this) } }
}

val android.content.Context.quark: Result<AndroidQuark>
    get() = (applicationContext as QuarkApplication).quark
