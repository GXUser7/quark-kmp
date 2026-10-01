package com.quark.app.ui

import androidx.compose.runtime.Composable

/**
 * The system back gesture, where there is one. On Android it closes whatever
 * sheet is open before it leaves the app; the desktop has no such gesture and
 * relies on the close buttons.
 */
@Composable
expect fun PlatformBackHandler(enabled: Boolean = true, onBack: () -> Unit)
