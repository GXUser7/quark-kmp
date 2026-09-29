package com.quark.app.ui

import androidx.compose.runtime.Composable

@Composable
actual fun PlatformBackHandler(enabled: Boolean, onBack: () -> Unit) {
    // The desktop has no back gesture; sheets close from their own buttons.
}
