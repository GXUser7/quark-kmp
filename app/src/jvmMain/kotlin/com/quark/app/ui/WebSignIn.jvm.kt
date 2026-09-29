package com.quark.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect

/** The desktop has no web view to embed; sign-in goes through the browser. */
actual val hasEmbeddedBrowser: Boolean = false

@Composable
actual fun WebSignInDialog(spec: WebSignInSpec, onResult: (String?) -> Unit) {
    LaunchedEffect(spec) { onResult(null) }
}
