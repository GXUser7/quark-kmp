package com.quark.app

import androidx.compose.runtime.staticCompositionLocalOf

/** The [Platform] the interface is running on, for composables that need it directly. */
val LocalPlatform = staticCompositionLocalOf<Platform> {
    error("LocalPlatform is provided by QuarkRoot")
}
