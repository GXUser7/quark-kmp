package com.quark.network.yandex

/**
 * Signing in without an embedded browser.
 *
 * Yandex's implicit flow returns the token in the url *fragment*, which never
 * reaches a server — that is why the Flutter build embedded a WebView just to
 * read the address bar, and why it fell back to pasting the token by hand on
 * Linux, where WebView2 does not exist.
 *
 * The redirect is registered against Yandex Music's own client id and points at
 * `music.yandex.ru`, so a local callback server cannot be used either. What is
 * left, and what this does, is to open the system browser and accept whatever
 * the user pastes back: the whole redirected address, or just the token.
 *
 * Embedding Chromium through JCEF would restore the automatic flow at a cost of
 * something over a hundred megabytes, which is more than the rest of the
 * application including libmpv.
 */
object YandexOAuth {

    /** Yandex Music's own client id, as the Dart build used it. */
    const val CLIENT_ID = "23cabbbdc6cd418abb4b39c32c41195d"

    val authorizeUrl: String
        get() = "https://oauth.yandex.ru/authorize?response_type=token&client_id=$CLIENT_ID"

    /**
     * Pulls the token out of whatever was pasted.
     *
     * Accepts the full redirected url, the fragment on its own, or a bare
     * token. Returns null when there is nothing token-shaped in it.
     */
    fun extractToken(pasted: String): String? {
        val text = pasted.trim()
        if (text.isEmpty()) return null

        parameter(text, "access_token")?.let { return it }

        // A bare token: Yandex issues `y0_…`, but the prefix has changed before,
        // so anything long enough and without spaces or url punctuation counts.
        val looksLikeToken = text.length >= MIN_TOKEN_LENGTH &&
            text.none { it.isWhitespace() } &&
            '/' !in text && '?' !in text && '&' !in text && '=' !in text
        return text.takeIf { looksLikeToken }
    }

    /** How long the token is good for, if the redirect said so. */
    fun extractExpiresIn(pasted: String): Long? =
        parameter(pasted.trim(), "expires_in")?.toLongOrNull()

    private fun parameter(text: String, name: String): String? {
        val marker = "$name="
        val start = text.indexOf(marker).takeIf { it >= 0 }?.plus(marker.length) ?: return null
        val rest = text.substring(start)
        val end = rest.indexOfFirst { it == '&' || it == '#' || it.isWhitespace() }
        val value = if (end >= 0) rest.substring(0, end) else rest
        return value.takeIf(String::isNotEmpty)
    }

    private const val MIN_TOKEN_LENGTH = 20
}
