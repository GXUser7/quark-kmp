package com.quark.app.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.quark.app.theme.Quark

actual val hasEmbeddedBrowser: Boolean = true

@SuppressLint("SetJavaScriptEnabled")
@Composable
actual fun WebSignInDialog(spec: WebSignInSpec, onResult: (String?) -> Unit) {
    val finish = rememberUpdatedState(onResult)
    // Pages report the same address more than once (started, history,
    // override); only the first finding counts.
    val done = remember(spec) { booleanArrayOf(false) }
    val check: (String) -> Boolean = { address ->
        val found = spec.extract(address)
        if (found != null && !done[0]) {
            done[0] = true
            finish.value(found)
        }
        found != null
    }

    Dialog(
        onDismissRequest = { if (!done[0]) finish.value(null) },
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Column(Modifier.fillMaxSize().background(Quark.colors.background).safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                CircleButton(Icons.Filled.Close, { if (!done[0]) finish.value(null) }, diameter = 36.dp, iconSize = 18.dp)
            }
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { context ->
                    WebView(context).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        spec.userAgent?.let { settings.userAgentString = it }
                        CookieManager.getInstance().setAcceptCookie(true)
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean =
                                check(request.url.toString())

                            override fun onPageStarted(view: WebView, url: String, favicon: Bitmap?) {
                                if (check(url)) view.stopLoading()
                            }

                            // Tokens come back in the fragment, which changes
                            // the address without loading a page.
                            override fun doUpdateVisitedHistory(view: WebView, url: String, isReload: Boolean) {
                                check(url)
                            }
                        }
                        loadUrl(spec.url)
                    }
                },
                onRelease = { it.destroy() },
            )
        }
    }
}
