package com.quark.app.i18n

import java.util.Locale

actual fun systemLanguageCode(): String = Locale.getDefault().language
