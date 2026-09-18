package com.quark.network.yandex

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class YandexOAuthTest {

    private val token = "y0_AgAAAABxxxxxxAAG8XgAAAADq1234567890abcdefgh"

    @Test
    fun reads_the_token_out_of_a_pasted_redirect() {
        val url = "https://music.yandex.ru/#access_token=$token&token_type=bearer&expires_in=31536000"

        assertEquals(token, YandexOAuth.extractToken(url))
        assertEquals(31_536_000L, YandexOAuth.extractExpiresIn(url))
    }

    @Test
    fun reads_the_token_out_of_the_fragment_alone() {
        assertEquals(token, YandexOAuth.extractToken("#access_token=$token&token_type=bearer"))
    }

    @Test
    fun accepts_a_bare_token() {
        assertEquals(token, YandexOAuth.extractToken("  $token  "))
    }

    @Test
    fun rejects_things_that_are_not_tokens() {
        assertNull(YandexOAuth.extractToken(""))
        assertNull(YandexOAuth.extractToken("short"))
        assertNull(YandexOAuth.extractToken("https://music.yandex.ru/home"))
    }

    @Test
    fun the_order_of_the_fragment_does_not_matter() {
        val url = "https://music.yandex.ru/#token_type=bearer&access_token=$token"

        assertEquals(token, YandexOAuth.extractToken(url))
        assertNull(YandexOAuth.extractExpiresIn(url))
    }

    @Test
    fun the_authorize_url_asks_for_an_implicit_token() {
        val url = YandexOAuth.authorizeUrl

        assertEquals(
            "https://oauth.yandex.ru/authorize?response_type=token&client_id=${YandexOAuth.CLIENT_ID}",
            url,
        )
    }
}
