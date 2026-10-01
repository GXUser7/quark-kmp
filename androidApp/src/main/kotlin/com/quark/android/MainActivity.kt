package com.quark.android

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.ListenableFuture
import com.quark.app.AndroidPlatform
import com.quark.app.QuarkRoot
import com.quark.app.StartupFailure

class MainActivity : ComponentActivity() {

    private lateinit var platform: AndroidPlatform
    private var controller: ListenableFuture<MediaController>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        // Registers the picker contracts, which must happen before STARTED.
        platform = AndroidPlatform(this)

        val started = quark
        setContent {
            val running = started.getOrNull()
            if (running == null) StartupFailure(started.exceptionOrNull())
            else QuarkRoot(running.app, platform)
        }
        handleOpenIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOpenIntent(intent)
    }

    /**
     * Binding a controller is what brings the playback service up; from then
     * on Media3 keeps it in the foreground for as long as something plays, and
     * the notification carries the controls.
     */
    override fun onStart() {
        super.onStart()
        if (quark.isFailure) return
        val token = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        controller = MediaController.Builder(this, token).buildAsync()
    }

    override fun onStop() {
        controller?.let(MediaController::releaseFuture)
        controller = null
        super.onStop()
    }

    /** "Open with quark" from a file manager or another app. */
    private fun handleOpenIntent(intent: Intent?) {
        if (intent?.action != Intent.ACTION_VIEW) return
        val uri: Uri = intent.data ?: return
        quark.getOrNull()?.app?.requestOpen(listOf(uri.toString()))
    }
}
