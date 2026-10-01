package com.quark.app.ui

import androidx.compose.runtime.Composable

/**
 * Whether sign-in pages can be shown inside the app — Android's WebView, as
 * the Flutter build did with `flutter_inappwebview` — rather than in the
 * system browser with the address pasted back by hand.
 */
expect val hasEmbeddedBrowser: Boolean

/** Where a service's sign-in starts, and how to tell from an address that it is done. */
class WebSignInSpec(
    val url: String,
    /** The token, code or address to hand on once an address holds it; null until then. */
    val extract: (String) -> String?,
    val userAgent: String? = null,
)

/**
 * Shows [spec]'s page full-screen inside the app and watches where it goes;
 * as soon as [WebSignInSpec.extract] finds what it waits for, the page closes
 * and [onResult] gets it. Closing the page by hand gives null.
 */
@Composable
expect fun WebSignInDialog(spec: WebSignInSpec, onResult: (String?) -> Unit)
